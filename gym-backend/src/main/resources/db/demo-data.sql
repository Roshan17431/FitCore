-- Opt-in demo fixtures for isolated databases; never initialize production with this file.
-- Public password: Fitcore@123. Administrators must rotate it before authentication is allowed.

INSERT INTO users (id, full_name, email, password_hash, role, fitness_goal, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', 'Maya Singh', 'maya.admin@fitcore.test', '$2y$10$NaDwZvIufqUN0Thv1R/iqeQDZRLSAxZ9cFITenn8WFkNwa424QDgW', 'ADMIN', 'Operations', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('22222222-2222-2222-2222-222222222222', 'Alex Carter', 'alex.member@fitcore.test', '$2y$10$NaDwZvIufqUN0Thv1R/iqeQDZRLSAxZ9cFITenn8WFkNwa424QDgW', 'MEMBER', 'Muscle Gain', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('33333333-3333-3333-3333-333333333333', 'Priya Nair', 'priya.member@fitcore.test', '$2y$10$NaDwZvIufqUN0Thv1R/iqeQDZRLSAxZ9cFITenn8WFkNwa424QDgW', 'MEMBER', 'Weight Loss', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('44444444-4444-4444-4444-444444444444', 'Jordan Lee', 'jordan.member@fitcore.test', '$2y$10$NaDwZvIufqUN0Thv1R/iqeQDZRLSAxZ9cFITenn8WFkNwa424QDgW', 'MEMBER', 'Maintenance', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('55555555-5555-5555-5555-555555555555', 'Ryan Brooks', 'ryan.trainer@fitcore.test', '$2y$10$NaDwZvIufqUN0Thv1R/iqeQDZRLSAxZ9cFITenn8WFkNwa424QDgW', 'TRAINER', 'Strength Coaching', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('66666666-6666-6666-6666-666666666666', 'Elena Torres', 'elena.trainer@fitcore.test', '$2y$10$NaDwZvIufqUN0Thv1R/iqeQDZRLSAxZ9cFITenn8WFkNwa424QDgW', 'TRAINER', 'Mobility Coaching', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('77777777-7777-7777-7777-777777777777', 'Noah Patel', 'noah.trainer@fitcore.test', '$2y$10$NaDwZvIufqUN0Thv1R/iqeQDZRLSAxZ9cFITenn8WFkNwa424QDgW', 'TRAINER', 'Fat Loss Coaching', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
full_name = EXCLUDED.full_name,
email = EXCLUDED.email,
password_hash = EXCLUDED.password_hash,
role = EXCLUDED.role,
fitness_goal = EXCLUDED.fitness_goal,
updated_at = CURRENT_TIMESTAMP;

INSERT INTO membership_plans (id, name, price, duration_months, recommended) VALUES
(1, 'Basic', 29.00, 1, FALSE),
(2, 'Premium', 79.00, 3, TRUE),
(3, 'Elite', 149.00, 6, FALSE)
ON CONFLICT (id) DO UPDATE SET
name = EXCLUDED.name,
price = EXCLUDED.price,
duration_months = EXCLUDED.duration_months,
recommended = EXCLUDED.recommended;

DELETE FROM membership_plan_features WHERE plan_id IN (1, 2, 3);

INSERT INTO membership_plan_features (plan_id, feature) VALUES
(1, 'Gym floor access'),
(1, 'Locker room'),
(1, 'Workout library'),
(1, 'Monthly body check'),
(2, 'Everything in Basic'),
(2, 'Trainer consultation'),
(2, 'Nutrition dashboard'),
(2, 'Progress analytics'),
(3, 'Everything in Premium'),
(3, 'Personal trainer sessions'),
(3, 'Priority booking'),
(3, 'Recovery assessment');

INSERT INTO active_memberships (id, user_id, plan_id, start_date, end_date, status, created_at, updated_at) VALUES
(1, '22222222-2222-2222-2222-222222222222', 2, '2026-05-01', '2026-08-01', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, '33333333-3333-3333-3333-333333333333', 1, '2026-06-10', '2026-07-10', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, '44444444-4444-4444-4444-444444444444', 2, '2026-02-01', '2026-05-01', 'EXPIRED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
user_id = EXCLUDED.user_id,
plan_id = EXCLUDED.plan_id,
start_date = EXCLUDED.start_date,
end_date = EXCLUDED.end_date,
status = EXCLUDED.status,
updated_at = CURRENT_TIMESTAMP;

INSERT INTO exercises (id, name, category, difficulty, recommended_sets, recommended_reps, description, image_url) VALUES
(1, 'Barbell Bench Press', 'Chest', 'INTERMEDIATE', 4, '8-10', 'Primary compound press for chest, triceps, and anterior shoulders.', 'https://images.unsplash.com/photo-1583454110551-21f2fa2afe61?auto=format&fit=crop&w=800&q=80'),
(2, 'Incline Dumbbell Press', 'Chest', 'BEGINNER', 3, '10-12', 'Upper chest builder with balanced shoulder control.', 'https://images.unsplash.com/photo-1598971639058-fab3c3109a00?auto=format&fit=crop&w=800&q=80'),
(3, 'Lat Pulldown', 'Back', 'BEGINNER', 4, '10-12', 'Vertical pull pattern for lats and upper back strength.', 'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=800&q=80'),
(4, 'Single Arm Row', 'Back', 'INTERMEDIATE', 3, '12 each', 'Unilateral row to improve back thickness and posture.', 'https://images.unsplash.com/photo-1581009146145-b5ef050c2e1e?auto=format&fit=crop&w=800&q=80'),
(5, 'Dumbbell Shoulder Press', 'Shoulders', 'INTERMEDIATE', 4, '8-10', 'Overhead press pattern for delts and trunk stability.', 'https://images.unsplash.com/photo-1571019613914-85f342c6a11e?auto=format&fit=crop&w=800&q=80'),
(6, 'Lateral Raise', 'Shoulders', 'BEGINNER', 3, '12-15', 'Isolation movement for medial deltoid development.', 'https://images.unsplash.com/photo-1599058917765-a780eda07a3e?auto=format&fit=crop&w=800&q=80'),
(7, 'Back Squat', 'Legs', 'ADVANCED', 4, '6-8', 'Full lower-body compound lift for strength and coordination.', 'https://images.unsplash.com/photo-1593079831268-3381b0db4a77?auto=format&fit=crop&w=800&q=80'),
(8, 'Romanian Deadlift', 'Legs', 'INTERMEDIATE', 3, '8-10', 'Posterior chain hinge pattern for hamstrings and glutes.', 'https://images.unsplash.com/photo-1605296867424-35fc25c9212a?auto=format&fit=crop&w=800&q=80'),
(9, 'Cable Triceps Pressdown', 'Arms', 'BEGINNER', 3, '12-15', 'Elbow extension movement for triceps volume.', 'https://images.unsplash.com/photo-1584863231364-2edc166de576?auto=format&fit=crop&w=800&q=80'),
(10, 'EZ Bar Curl', 'Arms', 'BEGINNER', 3, '10-12', 'Biceps curl variation with wrist-friendly grip.', 'https://images.unsplash.com/photo-1584466977773-e625c37cdd50?auto=format&fit=crop&w=800&q=80'),
(11, 'Cable Wood Chop', 'Core', 'INTERMEDIATE', 3, '12 each', 'Rotational core exercise for anti-rotation control.', 'https://images.unsplash.com/photo-1594737625785-a6cbdabd333c?auto=format&fit=crop&w=800&q=80'),
(12, 'Plank', 'Core', 'BEGINNER', 3, '45 sec', 'Core bracing drill for trunk endurance.', 'https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=800&q=80')
ON CONFLICT (id) DO UPDATE SET
name = EXCLUDED.name,
category = EXCLUDED.category,
difficulty = EXCLUDED.difficulty,
recommended_sets = EXCLUDED.recommended_sets,
recommended_reps = EXCLUDED.recommended_reps,
description = EXCLUDED.description,
image_url = EXCLUDED.image_url;

INSERT INTO workout_logs (id, user_id, date, duration_minutes, notes, created_at) VALUES
(1, '22222222-2222-2222-2222-222222222222', '2026-06-29', 46, 'Push strength session completed.', CURRENT_TIMESTAMP),
(2, '22222222-2222-2222-2222-222222222222', '2026-06-27', 58, 'Pull session with added rowing volume.', CURRENT_TIMESTAMP),
(3, '33333333-3333-3333-3333-333333333333', '2026-06-28', 39, 'Conditioning and core circuit.', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
user_id = EXCLUDED.user_id,
date = EXCLUDED.date,
duration_minutes = EXCLUDED.duration_minutes,
notes = EXCLUDED.notes;

INSERT INTO workout_exercise_logs (id, workout_log_id, exercise_id, sets_completed, reps_completed, weight_used) VALUES
(1, 1, 1, 4, '10,10,8,8', 82.5),
(2, 1, 2, 3, '12,11,10', 30.0),
(3, 1, 9, 3, '15,13,12', 32.5),
(4, 2, 3, 4, '12,12,10,10', 70.0),
(5, 2, 4, 3, '12 each', 34.0),
(6, 3, 12, 3, '45 sec', NULL)
ON CONFLICT (id) DO UPDATE SET
workout_log_id = EXCLUDED.workout_log_id,
exercise_id = EXCLUDED.exercise_id,
sets_completed = EXCLUDED.sets_completed,
reps_completed = EXCLUDED.reps_completed,
weight_used = EXCLUDED.weight_used;

INSERT INTO nutrition_goals (id, user_id, target_calories, target_protein, target_carbs, target_fat, updated_at) VALUES
(1, '22222222-2222-2222-2222-222222222222', 2500, 180, 260, 72, CURRENT_TIMESTAMP),
(2, '33333333-3333-3333-3333-333333333333', 1850, 155, 170, 55, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
user_id = EXCLUDED.user_id,
target_calories = EXCLUDED.target_calories,
target_protein = EXCLUDED.target_protein,
target_carbs = EXCLUDED.target_carbs,
target_fat = EXCLUDED.target_fat,
updated_at = CURRENT_TIMESTAMP;

INSERT INTO food_logs (id, user_id, meal_name, meal_type, calories, protein, carbs, fat, log_date, log_time, created_at) VALUES
(1, '22222222-2222-2222-2222-222222222222', 'Oats and Eggs', 'BREAKFAST', 520, 32, 54, 18, '2026-06-30', '08:00:00', CURRENT_TIMESTAMP),
(2, '22222222-2222-2222-2222-222222222222', 'Chicken Rice Bowl', 'LUNCH', 680, 52, 76, 14, '2026-06-30', '13:00:00', CURRENT_TIMESTAMP),
(3, '22222222-2222-2222-2222-222222222222', 'Greek Yogurt', 'SNACK', 146, 20, 8, 4, '2026-06-30', '16:30:00', CURRENT_TIMESTAMP),
(4, '33333333-3333-3333-3333-333333333333', 'Grilled Chicken Salad', 'LUNCH', 430, 44, 24, 15, '2026-06-30', '13:15:00', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
user_id = EXCLUDED.user_id,
meal_name = EXCLUDED.meal_name,
meal_type = EXCLUDED.meal_type,
calories = EXCLUDED.calories,
protein = EXCLUDED.protein,
carbs = EXCLUDED.carbs,
fat = EXCLUDED.fat,
log_date = EXCLUDED.log_date,
log_time = EXCLUDED.log_time;

INSERT INTO trainer_profiles (id, user_id, specialization, experience_years, bio) VALUES
(1, '55555555-5555-5555-5555-555555555555', 'Strength and Hypertrophy', 8, 'Strength coach focused on progressive overload, technique audits, and sustainable muscle gain.'),
(2, '66666666-6666-6666-6666-666666666666', 'Mobility and Conditioning', 6, 'Builds conditioning plans for busy members while improving joint range and movement quality.'),
(3, '77777777-7777-7777-7777-777777777777', 'Fat Loss and Core Training', 5, 'Combines habit coaching, metabolic conditioning, and core stability progressions.')
ON CONFLICT (id) DO UPDATE SET
user_id = EXCLUDED.user_id,
specialization = EXCLUDED.specialization,
experience_years = EXCLUDED.experience_years,
bio = EXCLUDED.bio;

INSERT INTO trainer_sessions (id, member_id, trainer_id, session_date, start_time, end_time, status, created_at, updated_at) VALUES
(1, '22222222-2222-2222-2222-222222222222', 1, '2026-07-01', '07:30:00', '08:30:00', 'SCHEDULED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, '22222222-2222-2222-2222-222222222222', 2, '2026-06-24', '18:00:00', '19:00:00', 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, '33333333-3333-3333-3333-333333333333', 3, '2026-06-28', '09:00:00', '10:00:00', 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
member_id = EXCLUDED.member_id,
trainer_id = EXCLUDED.trainer_id,
session_date = EXCLUDED.session_date,
start_time = EXCLUDED.start_time,
end_time = EXCLUDED.end_time,
status = EXCLUDED.status,
updated_at = CURRENT_TIMESTAMP;

INSERT INTO attendance (id, user_id, check_in_date, check_in_time, check_out_time) VALUES
(1, '22222222-2222-2222-2222-222222222222', '2026-06-29', '2026-06-29 18:18:00', '2026-06-29 19:29:00'),
(2, '22222222-2222-2222-2222-222222222222', '2026-06-27', '2026-06-27 07:04:00', '2026-06-27 08:08:00'),
(3, '22222222-2222-2222-2222-222222222222', '2026-06-25', '2026-06-25 18:42:00', '2026-06-25 19:36:00'),
(4, '22222222-2222-2222-2222-222222222222', '2026-06-23', '2026-06-23 07:15:00', '2026-06-23 08:02:00'),
(5, '33333333-3333-3333-3333-333333333333', '2026-06-29', '2026-06-29 06:58:00', '2026-06-29 07:51:00'),
(6, '33333333-3333-3333-3333-333333333333', '2026-06-26', '2026-06-26 18:05:00', '2026-06-26 19:01:00')
ON CONFLICT (id) DO UPDATE SET
user_id = EXCLUDED.user_id,
check_in_date = EXCLUDED.check_in_date,
check_in_time = EXCLUDED.check_in_time,
check_out_time = EXCLUDED.check_out_time;

INSERT INTO progress_metrics (id, user_id, log_date, weight, body_fat_percentage, chest, waist, arms, legs, created_at) VALUES
(1, '22222222-2222-2222-2222-222222222222', '2026-01-31', 86.4, 24.5, 104.0, 90.0, 35.0, 58.0, CURRENT_TIMESTAMP),
(2, '22222222-2222-2222-2222-222222222222', '2026-02-28', 84.9, 23.8, 103.5, 88.8, 35.2, 58.4, CURRENT_TIMESTAMP),
(3, '22222222-2222-2222-2222-222222222222', '2026-03-31', 83.8, 22.9, 103.0, 87.5, 35.5, 58.7, CURRENT_TIMESTAMP),
(4, '22222222-2222-2222-2222-222222222222', '2026-04-30', 82.4, 21.7, 102.6, 86.2, 35.8, 59.0, CURRENT_TIMESTAMP),
(5, '22222222-2222-2222-2222-222222222222', '2026-05-31', 81.1, 20.8, 102.2, 85.0, 36.0, 59.0, CURRENT_TIMESTAMP),
(6, '22222222-2222-2222-2222-222222222222', '2026-06-30', 79.8, 19.9, 102.0, 84.0, 36.0, 59.0, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
user_id = EXCLUDED.user_id,
log_date = EXCLUDED.log_date,
weight = EXCLUDED.weight,
body_fat_percentage = EXCLUDED.body_fat_percentage,
chest = EXCLUDED.chest,
waist = EXCLUDED.waist,
arms = EXCLUDED.arms,
legs = EXCLUDED.legs;

SELECT setval(pg_get_serial_sequence('membership_plans', 'id'), COALESCE((SELECT MAX(id) FROM membership_plans), 1), true);
SELECT setval(pg_get_serial_sequence('active_memberships', 'id'), COALESCE((SELECT MAX(id) FROM active_memberships), 1), true);
SELECT setval(pg_get_serial_sequence('exercises', 'id'), COALESCE((SELECT MAX(id) FROM exercises), 1), true);
SELECT setval(pg_get_serial_sequence('workout_logs', 'id'), COALESCE((SELECT MAX(id) FROM workout_logs), 1), true);
SELECT setval(pg_get_serial_sequence('workout_exercise_logs', 'id'), COALESCE((SELECT MAX(id) FROM workout_exercise_logs), 1), true);
SELECT setval(pg_get_serial_sequence('nutrition_goals', 'id'), COALESCE((SELECT MAX(id) FROM nutrition_goals), 1), true);
SELECT setval(pg_get_serial_sequence('food_logs', 'id'), COALESCE((SELECT MAX(id) FROM food_logs), 1), true);
SELECT setval(pg_get_serial_sequence('trainer_profiles', 'id'), COALESCE((SELECT MAX(id) FROM trainer_profiles), 1), true);
SELECT setval(pg_get_serial_sequence('trainer_sessions', 'id'), COALESCE((SELECT MAX(id) FROM trainer_sessions), 1), true);
SELECT setval(pg_get_serial_sequence('attendance', 'id'), COALESCE((SELECT MAX(id) FROM attendance), 1), true);
SELECT setval(pg_get_serial_sequence('progress_metrics', 'id'), COALESCE((SELECT MAX(id) FROM progress_metrics), 1), true);
