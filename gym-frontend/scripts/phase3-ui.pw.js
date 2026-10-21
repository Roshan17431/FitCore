/* eslint-disable no-unused-expressions -- Executed by playwright-cli run-code. */
async (page) => {
  const baseline = new URL(page.url()).searchParams.has('baseline');
  const quick = new URL(page.url()).searchParams.has('quick');
  const selection = new URL(page.url()).searchParams;
  const selectedRole = selection.get('role');
  const selectedWidth = Number(selection.get('width')) || null;
  const selectedTheme = selection.get('theme');
  let matrixPageVisits = 0;
  page.setDefaultTimeout(15000);
  const origin = 'http://127.0.0.1:5174';
  const out = '/Users/roshan/Desktop/FitCore/output/playwright/phase3-2026-10-21';
  const check = (ok, message) => { if (!ok) throw new Error(message); };
  const results = [], failures = [], errors = [], assets = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', message => { if(message.type()==='error') errors.push(message.text()); });
  page.on('requestfailed', request => { if(!request.url().includes('/api/')) assets.push(new URL(request.url()).pathname); });
  page.on('response', r => { if (r.status() >= 400 && !r.url().includes('/api/')) assets.push(r.url()); });
  const run = async (name, fn) => { try { await fn(); results.push(name); } catch(e) { failures.push(`${name} at ${new URL(page.url()).pathname}: ${e.message}`); } };
  const plans = [{ id: 1, name: 'Essential', price: 29, durationMonths: 1, features: ['Gym access', 'Progress tracking'] }, { id: 2, name: 'Performance', price: 79, durationMonths: 3, recommended: true, features: ['Gym access', 'Group classes', 'Training support'] }, { id: 3, name: 'Complete', price: 149, durationMonths: 6, features: ['Gym access', 'Group classes', 'Training support'] }];
  const membership = { id: 41, plan: plans[0], startDate: '2026-10-01', endDate: '2026-11-01', status: 'ACTIVE', remainingDays: 11, serverDate: '2026-10-21', timeZone: 'UTC' };
  const trainers = [{ id: 3, user: { id: 33, fullName: 'Taylor Coach', enabled: true }, specialization: 'Strength & conditioning', experienceYears: 5, bio: 'A structured approach to strength, movement and sustainable progress.', certifications: ['CPT'], availableSlots: ['09:00–18:00'], photoUrl: '' }, { id: 4, user: { id: 34, fullName: 'Jordan Trainer', enabled: true }, specialization: 'Mobility & recovery', experienceYears: 7, bio: 'Move with confidence and build consistency.', certifications: ['CPT'], photoUrl: '/fixture-missing-portrait.svg' }];
  const exercises = ['Incline Dumbbell Press', 'Lat Pulldown', 'Single Arm Row', 'Barbell Bench Press', 'Unverified Exercise'].map((name,i) => ({ id: i+1, name, category: 'Strength', description: 'Follow your assigned sets and repetitions with controlled movement.', difficulty: 'BEGINNER', recommendedSets: 3, recommendedReps: '10–12', imageUrl: 'https://images.unsplash.com/incorrect-exercise' }));
  const series = [{ label: 'Mon', weight: 74, bodyFat: 20, completed: 60, visits: 1, members: 30, revenue: 300 }, { label: 'Tue', weight: 73.8, bodyFat: 19.8, completed: 80, visits: 3, members: 34, revenue: 350 }, { label: 'Wed', weight: 73.5, bodyFat: 19.6, completed: 100, visits: 2, members: 38, revenue: 390 }];
  const notifications = [{ id: 1, title: 'Your membership is active. Keep your training consistent.', type: 'Membership' }];
  const overview = { dashboardCards: [{ title: 'Membership', value: 'Essential', detail: '11 days remaining', tone: 'emerald' }, { title: 'Workouts', value: '12', detail: 'Completed this month', tone: 'sky' }, { title: 'Calories', value: '1,840', detail: 'Logged today', tone: 'amber' }, { title: 'Visits', value: '16', detail: 'This month', tone: 'violet' }, { title: 'Weight', value: '73.5 kg', detail: 'Latest entry', tone: 'emerald' }], weightProgress: series, workoutCompletion: series, attendanceChart: series, notifications, upcomingSessions: [{ id: 5, trainer: 'Taylor Coach', focus: 'Strength training', date: '2026-11-01', time: '10:00' }], recentActivities: [{ id: 1, title: 'Workout completed', meta: 'Strength session', time: 'Today' }] };
  const users = [{ id: 7, fullName: 'Local Member', email: 'member@example.invalid', role: 'MEMBER', enabled: true, status: 'Active', membership: 'Essential' }];
  let role = 'MEMBER', theme = 'dark';
  await page.addInitScript(() => {
    if (window.name === 'phase3-auth') {
      localStorage.setItem('token', 'synthetic-local-token');
      localStorage.setItem('user', JSON.stringify({ id: 7, fullName: 'Local Member', role: sessionStorage.getItem('fixtureRole') || 'MEMBER' }));
    } else { localStorage.removeItem('token'); localStorage.removeItem('user'); }
  });
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url()), p = url.pathname;
    if (url.origin !== origin && !p.startsWith('/api/')) return route.fulfill({ status: 200, body: '', contentType: 'text/plain' });
    if (p === '/fixture-missing-portrait.svg') return route.fulfill({status:200,body:'invalid image',contentType:'image/svg+xml'});
    if (!p.startsWith('/api/')) return route.continue();
    if(req.method()==='OPTIONS') return route.fulfill({status:204});
    let data;
    if(p.startsWith('/api/dashboard/')) data = overview;
    else if(p.startsWith('/api/notifications/')) data = notifications;
    else if(p==='/api/memberships/plans') data = plans;
    else if(p.endsWith('/active')) data = membership;
    else if(p.startsWith('/api/memberships/user/')) data = [membership];
    else if(p==='/api/trainers') data = trainers;
    else if(p.startsWith('/api/trainers/sessions/')) data = [];
    else if(p==='/api/workouts/exercises') data = exercises;
    else if(p==='/api/workouts/plans') data = [{id:1,level:'Beginner',duration:'4 weeks',schedule:['Mon: Strength','Wed: Mobility'], exercises:[]}];
    else if(p.startsWith('/api/workouts/logs/')) data = [];
    else if(p.startsWith('/api/tracking/progress/')) data = [{id:1,logDate:'2026-10-20',weight:74,bodyFatPercentage:20,chest:95,waist:80,arms:32,legs:55}];
    else if(p.startsWith('/api/tracking/attendance/')) data = [];
    else if(p.startsWith('/api/nutrition/dashboard/')) data = {nutritionCards:[{title:'Calories',value:'1,840',detail:'Logged today',tone:'emerald'}],calorieTracker:{dailyGoal:2200}};
    else if(p==='/api/nutrition/plans') data = [{category:'Balanced',calories:2200,protein:140,carbs:250,fats:65,meals:['Breakfast','Lunch','Dinner']}];
    else if(p.startsWith('/api/nutrition/logs/')) data = [];
    else if(p==='/api/admin/dashboard') data = {users,trainers,membershipPlans:plans,attendanceHistory:[],adminCards:[{title:'Members',value:'1',detail:'Local fixture',tone:'emerald'}],membershipGrowth:series,revenueAnalytics:series,adminAttendanceAnalytics:series};
    else if(p==='/api/auth/forgot-password') data = {message:'Contact your gym administrator.'};
    else return route.fulfill({status:500,json:{message:`Unmapped local fixture ${p}`}});
    return route.fulfill({json:data});
  });
  const setup = async (auth = true) => {
    await page.goto(`${origin}/login`);
    await page.evaluate(({auth,role,theme}) => {window.name=auth?'phase3-auth':'';sessionStorage.setItem('fixtureRole',role);localStorage.setItem('fitcore_theme',theme);}, {auth,role,theme});
  };
  const open = async path => { await page.goto(origin+path); await page.locator('main').waitFor(); await page.waitForFunction(() => !document.querySelector('[role="status"]')?.textContent?.startsWith('Loading')); await page.waitForTimeout(180); };
  if(baseline) {
    for(const width of [390,768,1440]) for(const mode of ['light','dark']) {
      theme=mode; await page.setViewportSize({width,height:1000}); await page.emulateMedia({colorScheme:'light'}); await setup(false); await open('/login');
      await page.screenshot({path:`${out}/before-login-${width}-${mode}.png`,fullPage:true});
      await setup(); await open('/dashboard'); await page.getByText('Weight Progress Chart',{exact:true}).waitFor();
      await page.screenshot({path:`${out}/before-dashboard-${width}-${mode}.png`,fullPage:true});
    }
    return 'Captured 12 baseline screenshots using synthetic local fixtures.';
  }
  await page.setViewportSize({width:1440,height:1000}); await page.emulateMedia({colorScheme:'light'}); await setup();
  await run('manual dark theme overrides light OS and persists',async()=>{
    await open('/dashboard'); await page.getByText('Weight Progress Chart',{exact:true}).waitFor();
    const colors=await page.locator('main section').first().evaluate(el=>({bg:getComputedStyle(el).backgroundColor,fg:getComputedStyle(el).color}));
    check(colors.bg!=='rgb(255, 255, 255)' && !colors.bg.includes('255, 255, 255'),'dark card is white under light OS');
    await page.getByLabel('Color theme').selectOption('light'); await page.reload();
    check(await page.getByLabel('Color theme').inputValue()==='light','theme did not persist');
    check(await page.locator('html').evaluate(el=>!el.classList.contains('dark')),'light ignored');
  });
  await run('system follows OS changes',async()=>{
    await page.getByLabel('Color theme').selectOption('system'); await page.emulateMedia({colorScheme:'dark'});
    await page.waitForFunction(()=>document.documentElement.classList.contains('dark'));
    await page.emulateMedia({colorScheme:'light'}); await page.waitForFunction(()=>!document.documentElement.classList.contains('dark'));
  });
  await run('modal Escape traps and restores keyboard focus',async()=>{
    await open('/dashboard'); const trigger=page.getByRole('button',{name:'Notifications',exact:true}); await trigger.click();
    const dialog=page.getByRole('dialog',{name:'Notifications',exact:true}); await dialog.waitFor();
    check(await dialog.evaluate(el=>el.contains(document.activeElement)),'initial focus outside modal');
    await page.keyboard.press('Shift+Tab'); check(await dialog.evaluate(el=>el.contains(document.activeElement)),'reverse Tab escaped modal');
    await page.keyboard.press('Tab'); check(await dialog.evaluate(el=>el.contains(document.activeElement)),'Tab escaped modal');
    await page.keyboard.press('Escape'); check(await dialog.count()===0,'Escape did not close');
    check(await trigger.evaluate(el=>el===document.activeElement),'focus not restored');
  });
  await run('trainer missing and failed photos fall back to initials',async()=>{
    await open('/trainers/list'); await page.getByText('Taylor Coach',{exact:true}).waitFor();
    check(await page.getByRole('img',{name:'Taylor Coach — initials avatar',exact:true}).count()===1,'missing avatar has no accessible fallback');
    await page.getByRole('img',{name:'Jordan Trainer — initials avatar',exact:true}).waitFor();
    check(await page.locator('main img').evaluateAll(imgs=>imgs.every(i=>i.complete&&i.naturalWidth>0)),'broken trainer image rendered');
  });
  await run('exercise visuals never present unverified stock as an exercise',async()=>{
    await open('/workouts/library'); await page.getByText('Incline Dumbbell Press',{exact:true}).first().waitFor();
    check(await page.locator('main img[src*="unsplash"]').count()===0,'unverified exercise stock rendered');
    check(await page.getByText('Visual guide unavailable',{exact:true}).count()>=1,'unverified exercise has no labelled fallback');
    check(await page.locator('[data-exercise-visual]').count()===5,'missing standardized exercise visuals');
  });
  await run('mobile navigation hides, traps focus, closes and restores focus',async()=>{
    await page.setViewportSize({width:390,height:844}); await open('/membership/active');
    const trigger=page.getByRole('button',{name:'Open navigation',exact:true}); await trigger.click();
    const dialog=page.getByRole('dialog',{name:'Main navigation'}); await dialog.waitFor();
    await page.keyboard.press('Shift+Tab'); check(await dialog.evaluate(el=>el.contains(document.activeElement)),'navigation focus escaped');
    await page.keyboard.press('Escape'); check(await dialog.count()===0,'Escape did not close navigation');
    check(await trigger.evaluate(el=>el===document.activeElement),'nav focus not restored');
  });
  const contrast = async (locator) => locator.evaluate((el) => {
    const ctx=document.createElement('canvas').getContext('2d');
    const rgba=(color)=>{ctx.clearRect(0,0,1,1);ctx.fillStyle=color;ctx.fillRect(0,0,1,1);return [...ctx.getImageData(0,0,1,1).data].map((v,i)=>i===3?v/255:v);};
    const over=(fg,bg)=>[0,1,2].map(i=>fg[i]*fg[3]+bg[i]*(1-fg[3]));
    const ancestors=[];for(let n=el;n;n=n.parentElement)ancestors.push(n);
    let bg=[255,255,255];for(const n of ancestors.reverse())bg=over(rgba(getComputedStyle(n).backgroundColor),bg);
    const fg=over(rgba(getComputedStyle(el).color),bg);
    const lum=c=>c.map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((a,v,i)=>a+v*[.2126,.7152,.0722][i],0);
    const a=lum(fg),b=lum(bg);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);
  });
  await run('status badges and recovery success meet AA in both themes',async()=>{
    await page.setViewportSize({width:1440,height:1000});
    for(const mode of ['light','dark']) {
      theme=mode;await setup();await open('/membership/active');
      const ratio=await contrast(page.getByText('Active',{exact:true}).last());
      check(ratio>=4.5,`${mode} active badge contrast ${ratio.toFixed(2)} < 4.5`);
      await setup(false);await open('/forgot-password');await page.getByLabel('Email',{exact:true}).fill('local@example.invalid');await page.getByRole('button',{name:'Request Reset',exact:true}).click();
      const message=page.getByRole('status');await message.waitFor();
      const successRatio=await contrast(message);check(successRatio>=4.5,`${mode} recovery success contrast ${successRatio.toFixed(2)} < 4.5`);
    }
  });
  await run('recommended plan label never overlaps the plan heading at 1024px',async()=>{
    theme='light';await page.setViewportSize({width:1024,height:1000});await setup();await open('/membership/plans');
    const badge=page.getByText('Recommended',{exact:true}); const title=page.getByRole('heading',{name:'Performance Plan',exact:true});
    const a=await badge.boundingBox(),b=await title.boundingBox();
    check(a&&b&&!(a.x<b.x+b.width&&a.x+a.width>b.x&&a.y<b.y+b.height&&a.y+a.height>b.y),'recommended badge overlaps plan name');
  });
  // Matrix covers every existing page branch for each authenticated role and both themes.
  const common=['/dashboard','/membership/plans','/membership/active','/membership/history','/workouts/library','/workouts/plans','/workouts/tracker','/workouts/history','/nutrition/dashboard','/nutrition/diet-plans','/nutrition/food-logger','/nutrition/calorie-tracker','/progress/weight','/progress/measurements','/progress/bmi','/progress/analytics','/trainers/list','/trainers/profiles','/trainers/booking','/trainers/history','/attendance/check-in','/attendance/history','/attendance/statistics'];
  const admin=['/admin/dashboard','/admin/users','/admin/trainers','/admin/memberships','/admin/attendance'];
  if(!failures.length && !quick) for(const r of ['MEMBER','TRAINER','ADMIN']) for(const width of [390,768,1440]) for(const mode of ['light','dark']) {
    if(selectedRole && selectedRole!==r || selectedWidth && selectedWidth!==width || selectedTheme && selectedTheme!==mode)continue;
    role=r;theme=mode;await page.setViewportSize({width,height:1000});await setup();
    await run(`${r} ${width}px ${mode} all page branches`,async()=>{
      for(const path of [...common,...(r==='ADMIN'?admin:[])]) {
        await open(path); await page.locator('main h1').waitFor();
        check((await page.locator('main').innerText()).includes('FitCore'),'page failed to render');
        check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`horizontal overflow ${path}`);
        check(await page.getByRole('alert').count()===0,`unexpected API/render error ${path}`);
        matrixPageVisits++;
        if(r==='MEMBER'&&['/dashboard','/trainers/list','/workouts/library','/membership/plans'].includes(path)||r==='ADMIN'&&path==='/admin/users')
          await page.screenshot({path:`${out}/after-${r.toLowerCase()}-${path.slice(1).replaceAll('/','-')}-${width}-${mode}.png`,fullPage:true});
      }
    });
  }
  await run('no uncaught browser errors or failed assets',async()=>{check(!errors.length,errors.join('; '));check(!assets.length,assets.join('; '));});
  if(failures.length) throw new Error(`Phase 3 failed (${failures.length}): ${failures.join('\n')}\nPassed: ${results.join('; ')}`);
  return {passed:results.length,checks:results,matrixPageVisits,consoleErrors:errors,failedAssets:assets};
}
