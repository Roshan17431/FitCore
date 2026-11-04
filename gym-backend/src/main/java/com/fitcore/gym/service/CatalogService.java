package com.fitcore.gym.service;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.dto.catalog.CatalogDtos.*;
import com.fitcore.gym.entity.*;
import com.fitcore.gym.exception.*;
import com.fitcore.gym.repository.*;
import com.fitcore.gym.security.TenantAccess;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gym-derived ownership only. No deletes; immutable referenced names preserve historical labels. */
@Service @Transactional @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class CatalogService {
    private final TenantAccess access;
    private final GymRepository gyms;
    private final ExerciseRepository exercises;
    private final WorkoutPlanRepository workouts;
    private final NutritionPlanRepository nutrition;
    private final WorkoutExerciseLogRepository history;
    private final CatalogImagePolicy images;

    private UUID gym(){return access.manager().gymId();}
    private UUID writing(){UUID gym=gym();gyms.findByIdForUpdate(gym).orElseThrow(()->new ResourceNotFoundException("Gym not found"));return gym;}
    private ResourceNotFoundException missing(){return new ResourceNotFoundException("Catalog record not found");}
    private String text(String value){
        if(value==null)return null;
        if(value.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw new BadRequestException("Catalog text contains invalid control characters");
        return value.trim();
    }
    private List<String> lines(List<String> values){return values==null?new ArrayList<>():new ArrayList<>(values.stream().map(this::text).toList());}
    private <T>T save(Supplier<T> action){try{return action.get();}catch(DataIntegrityViolationException conflict){throw new DuplicateResourceException("Catalog name already exists or the record changed; reload and try again");}}
    private Exercise lockedExercise(Long id,UUID gym){return exercises.lockForManagement(id,gym).orElseThrow(this::missing);}
    private WorkoutPlan workout(Long id,UUID gym){return workouts.findByIdAndGymId(id,gym).orElseThrow(this::missing);}
    private NutritionPlan diet(Long id,UUID gym){return nutrition.findByIdAndGymId(id,gym).orElseThrow(this::missing);}

    @Transactional(readOnly=true) public List<ExerciseView> exercises(){return exercises.findAllByGymId(gym()).stream().sorted(Comparator.comparing(Exercise::getId)).map(this::view).toList();}
    @Transactional(readOnly=true) public ExerciseView exercise(Long id){return view(exercises.findByIdAndGymId(id,gym()).orElseThrow(this::missing));}
    public ExerciseView createExercise(ExerciseInput input){UUID gym=writing();var e=new Exercise();e.setGymId(gym);return saveExercise(e,input);}
    public ExerciseView updateExercise(Long id,ExerciseInput input){UUID gym=writing();return saveExercise(lockedExercise(id,gym),input);}
    private ExerciseView saveExercise(Exercise e,ExerciseInput input){
        String name=Objects.equals(e.getName(),input.name())?e.getName():text(input.name());
        if(e.getId()!=null&&!Objects.equals(name,e.getName())&&(history.existsByGymIdAndExerciseId(e.getGymId(),e.getId())||workouts.countExerciseReferences(e.getGymId(),e.getName())>0))
            throw new DuplicateResourceException("Referenced exercise names cannot change; create a replacement exercise");
        if(exercises.existsByGymIdAndNameAndIdNot(e.getGymId(),name,e.getId()==null?0L:e.getId()))throw new DuplicateResourceException("Exercise name already exists in your gym");
        String image=images.validateChange(input.imageUrl(),e.getImageUrl());
        e.setName(name);e.setCategory(text(input.category()));e.setDifficulty(input.difficulty());e.setRecommendedSets(input.recommendedSets());e.setRecommendedReps(text(input.recommendedReps()));e.setDescription(text(input.description()));e.setImageUrl(image);
        return view(save(()->exercises.saveAndFlush(e)));
    }
    public ExerciseView archiveExercise(Long id){
        UUID gym=writing();Exercise e=lockedExercise(id,gym);
        if(!e.isArchived()&&workouts.countPublishedExerciseReferences(gym,e.getName())>0)throw new DuplicateResourceException("Edit or archive published workout plans before archiving this exercise");
        e.setArchived(true);return view(save(()->exercises.saveAndFlush(e)));
    }
    private ExerciseView view(Exercise e){return new ExerciseView(e.getId(),e.getName(),e.getCategory(),e.getDifficulty(),e.getRecommendedSets(),e.getRecommendedReps(),e.getDescription(),e.getImageUrl(),images.approved(e.getImageUrl()),e.isArchived());}

    @Transactional(readOnly=true) public List<WorkoutView> workouts(){UUID gym=gym();return workouts.findAllByGymId(gym).stream().sorted(Comparator.comparing(WorkoutPlan::getId)).map(this::view).toList();}
    @Transactional(readOnly=true) public WorkoutView workout(Long id){return view(workout(id,gym()));}
    public WorkoutView createWorkout(WorkoutInput input){UUID gym=writing();var p=new WorkoutPlan();p.setGymId(gym);p.setStatus(WorkoutPlanStatus.DRAFT);return saveWorkout(p,input);}
    public WorkoutView updateWorkout(Long id,WorkoutInput input){UUID gym=writing();return saveWorkout(workout(id,gym),input);}
    private List<Exercise> selected(List<Long> ids,UUID gym){
        if(new HashSet<>(ids).size()!=ids.size())throw new BadRequestException("Choose each exercise once");
        var locked=new HashMap<Long,Exercise>();
        for(Long id:ids.stream().sorted().toList())locked.put(id,lockedExercise(id,gym));
        return ids.stream().map(locked::get).toList();
    }
    private void publishable(WorkoutPlan p){
        var names=p.getExercises()==null?List.<String>of():p.getExercises();
        if(names.isEmpty())throw new BadRequestException("Add at least one active exercise before publishing");
        Map<String,Exercise> local=new HashMap<>();for(Exercise e:exercises.findAllByGymId(p.getGymId()))local.put(e.getName(),e);
        var ids=new ArrayList<Long>();for(String name:names){Exercise e=local.get(name);if(e==null)throw new BadRequestException("Replace unresolved exercise names before publishing");ids.add(e.getId());}
        for(Exercise e:selected(ids,p.getGymId()))if(e.isArchived())throw new DuplicateResourceException("Archived exercises cannot be published in a workout plan");
    }
    private WorkoutView saveWorkout(WorkoutPlan p,WorkoutInput input){
        String level=text(input.level());
        if(workouts.existsByGymIdAndLevelAndIdNot(p.getGymId(),level,p.getId()==null?0L:p.getId()))throw new DuplicateResourceException("Workout plan level already exists in your gym");
        List<String> names=null;
        if(input.exerciseIds()!=null){
            var chosen=selected(input.exerciseIds(),p.getGymId());
            // An explicit replacement cannot introduce archived exercises, even in a new draft.
            for(Exercise e:chosen)if(e.isArchived())throw new DuplicateResourceException("Choose active exercises for new selections");
            names=new ArrayList<>(chosen.stream().map(Exercise::getName).toList());
        }
        boolean created=p.getId()==null;
        WorkoutPlanStatus status=input.status()==null?p.getStatus():input.status();
        if(input.exerciseIds()!=null&&input.exerciseIds().isEmpty()&&status!=WorkoutPlanStatus.DRAFT)
            throw new BadRequestException("An empty exercise selection is allowed only for a draft");
        boolean publishing=status==WorkoutPlanStatus.PUBLISHED&&(created||p.getStatus()!=WorkoutPlanStatus.PUBLISHED||input.exerciseIds()!=null);
        p.setLevel(level);p.setDuration(text(input.duration()));
        if(input.schedule()!=null||created)p.setSchedule(lines(input.schedule()));
        if(names!=null)p.setExercises(names);else if(created)p.setExercises(new ArrayList<>());
        p.setStatus(status);if(publishing)publishable(p);
        return view(save(()->workouts.saveAndFlush(p)));
    }
    public WorkoutView publishWorkout(Long id){UUID gym=writing();var p=workout(id,gym);publishable(p);p.setStatus(WorkoutPlanStatus.PUBLISHED);return view(save(()->workouts.saveAndFlush(p)));}
    public WorkoutView archiveWorkout(Long id){UUID gym=writing();var p=workout(id,gym);p.setStatus(WorkoutPlanStatus.ARCHIVED);return view(save(()->workouts.saveAndFlush(p)));}
    private WorkoutView view(WorkoutPlan p){
        Map<String,Long> local=new HashMap<>();for(Exercise e:exercises.findAllByGymId(p.getGymId()))local.put(e.getName(),e.getId());
        List<String> names=p.getExercises()==null?List.of():new ArrayList<>(p.getExercises());var ids=new ArrayList<Long>();var unresolved=new ArrayList<String>();
        for(String name:names){Long id=local.get(name);if(id==null)unresolved.add(name);else ids.add(id);}
        return new WorkoutView(p.getId(),p.getLevel(),p.getDuration(),p.getSchedule()==null?List.of():new ArrayList<>(p.getSchedule()),names,ids,unresolved,p.getStatus());
    }

    @Transactional(readOnly=true) public List<NutritionView> nutrition(){return nutrition.findAllByGymId(gym()).stream().sorted(Comparator.comparing(NutritionPlan::getId)).map(this::view).toList();}
    @Transactional(readOnly=true) public NutritionView nutrition(Long id){return view(diet(id,gym()));}
    public NutritionView createNutrition(NutritionInput input){UUID gym=writing();var p=new NutritionPlan();p.setGymId(gym);return saveNutrition(p,input);}
    public NutritionView updateNutrition(Long id,NutritionInput input){UUID gym=writing();return saveNutrition(diet(id,gym),input);}
    private NutritionView saveNutrition(NutritionPlan p,NutritionInput input){
        String category=text(input.category());if(nutrition.existsByGymIdAndCategoryAndIdNot(p.getGymId(),category,p.getId()==null?0L:p.getId()))throw new DuplicateResourceException("Nutrition category already exists in your gym");
        p.setCategory(category);p.setCalories(input.calories());p.setProtein(input.protein());p.setCarbs(input.carbs());p.setFats(input.fats());p.setMeals(lines(input.meals()));
        return view(save(()->nutrition.saveAndFlush(p)));
    }
    public NutritionView archiveNutrition(Long id){UUID gym=writing();var p=diet(id,gym);p.setArchived(true);return view(save(()->nutrition.saveAndFlush(p)));}
    private NutritionView view(NutritionPlan p){return new NutritionView(p.getId(),p.getCategory(),p.getCalories(),p.getProtein(),p.getCarbs(),p.getFats(),p.getMeals()==null?List.of():new ArrayList<>(p.getMeals()),p.isArchived());}
}
