/* eslint-disable no-unused-expressions -- Disposable CLI runner. */
async (page) => {
  const ui='https://127.0.0.1:18444', password='Browser-Spike!2026', checks=[], failures=[];
  const ctx=page.context(), fixture='@FIXTURE_CONTROL@';
  const assert=(ok,label)=>{if(!ok)throw Error(label);};
  const check=(name,ok)=>{assert(ok,name);checks.push(name);};
  const user=(gym,role)=>`30000000-0000-0000-0000-${String(gym*10+role).padStart(12,'0')}`;
  const owner=gym=>`20000000-0000-0000-0000-${String(gym).padStart(12,'0')}`;
  const raw=async(p,method,path,body,csrf)=>p.evaluate(async({method,path,body,csrf})=>{
    const headers={};if(body!==undefined)headers['Content-Type']='application/json';if(csrf)headers['X-CSRF-TOKEN']=csrf;
    const response=await fetch('/api'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),credentials:'same-origin',cache:'no-store',redirect:'error'});
    const text=await response.text();let data=null;try{data=JSON.parse(text);}catch{/*204*/}return {status:response.status,data,text,cache:response.headers.get('cache-control'),cookie:response.headers.get('set-cookie')};
  },{method,path,body,csrf});
  const api=async(p,method,path,body)=>{let csrf;if(!['GET','HEAD'].includes(method)){const token=await raw(p,'GET','/browser/csrf');csrf=token.data?.csrfToken;}return raw(p,method,path,body,csrf);};
  const transition=async(p,email)=>{
    const login=await api(p,'POST','/browser/login',{email,password});assert(login.status===200,`login ${email}: ${login.status} ${login.text}`);
    const ack=await raw(p,'POST','/browser/ack',{},login.data.csrfToken);assert(ack.status===200,`ACK ${ack.status}`);
    return (await api(p,'GET','/auth/me')).data;
  };
  const control=async(command,path)=>{const r=await ctx.request.post(ui+'/__fixture/'+command,{headers:{'X-FitCore-Fixture':fixture},data:path});assert(r.status()===204,'fixture control');};
  const pending=async(path)=>{for(let n=0;n<120;n++){const r=await ctx.request.get(ui+'/__fixture/state',{headers:{'X-FitCore-Fixture':fixture}});if((await r.json()).some(g=>g.target===path&&g.waiting>0))return;await new Promise(resolve=>setTimeout(resolve,50));}throw Error('No held backend response');};
  const lifecycleEvidence={events:[],backNavigation:null,focusEmulationDisabled:true};
  // Playwright enables focus emulation on every Chromium page. Disable it only
  // in this fixture so native tab activation can emit real focus/visibility events.
  const focusSessions=[];
  const nativeFocus=async p=>{const cdp=await ctx.newCDPSession(p);focusSessions.push(cdp);await cdp.send('Emulation.setFocusEmulationEnabled',{enabled:false});};
  await ctx.addInitScript(()=>{
    const record=event=>{const events=JSON.parse(sessionStorage.getItem('__lifecycleEvents')||'[]');events.push({type:event.type,persisted:event.persisted===true,trusted:event.isTrusted,visibility:document.visibilityState});sessionStorage.setItem('__lifecycleEvents',JSON.stringify(events));};
    window.addEventListener('focus',record);window.addEventListener('pageshow',record);document.addEventListener('visibilitychange',record);
  });
  const uiIdentity=async(p,gym)=>{try {await p.getByText(`Synthetic Gym ${gym} · Hello`,{exact:false}).waitFor();await p.getByRole('heading',{name:`Synthetic Gym ${gym}`,exact:true}).waitFor();}catch(error){throw Error(JSON.stringify({error:error.message,url:p.url(),cookieIdentity:lifecycleEvidence.cookieIdentityBeforeForeground,beforeForeground:lifecycleEvidence.beforeForeground,body:await p.locator('body').innerText(),events:await p.evaluate(()=>JSON.parse(sessionStorage.getItem('__lifecycleEvents')||'[]'))}));}};
  // APIRequestContext changes cookies without executing app code, focus or coordinator notifications.
  const backgroundTransition=async(email)=>{
    const csrf=await (await ctx.request.get(ui+'/api/browser/csrf',{headers:{'Sec-Fetch-Site':'same-origin'}})).json();
    const login=await ctx.request.post(ui+'/api/browser/login',{headers:{Origin:ui,'Sec-Fetch-Site':'same-origin','X-CSRF-TOKEN':csrf.csrfToken},data:{email,password}});assert(login.status()===200,`background login ${login.status()}: ${await login.text()}`);
    const result=await login.json();assert((await ctx.request.post(ui+'/api/browser/ack',{headers:{Origin:ui,'Sec-Fetch-Site':'same-origin','X-CSRF-TOKEN':result.csrfToken},data:{}})).status()===200,'background ACK');
  };
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(ui+'/login');
  // UI proves the real cookie transport build; every API request uses the same-origin BFF.
  await page.getByLabel('Email',{exact:true}).fill('owner1@fixture.invalid');await page.getByLabel('Password',{exact:true}).fill(password);
  await page.getByRole('button',{name:'Login',exact:true}).click();await page.waitForURL(ui+'/owner');
  await uiIdentity(page,1);await page.waitForLoadState('networkidle');
  const background=await ctx.newPage();await background.goto('about:blank');
  await nativeFocus(page);await nativeFocus(background);await background.bringToFront();await background.mouse.click(10,10);
  const revision=await page.evaluate(()=>{localStorage.setItem('workoutPlan:old-tenant:old-user','private training');sessionStorage.setItem('workoutPlan:old-tenant:old-user','private training');return localStorage.getItem('fitcore:cookie-revision');});
  await backgroundTransition('owner2@fixture.invalid');
  lifecycleEvidence.cookieIdentityBeforeForeground=await (await ctx.request.get(ui+'/api/auth/me',{headers:{'Sec-Fetch-Site':'same-origin'}})).json();
  assert(lifecycleEvidence.cookieIdentityBeforeForeground.id===owner(2),'cookie switched to owner 2 before foreground');
  assert(await page.evaluate(revision=>localStorage.getItem('fitcore:cookie-revision')===revision&&!localStorage.getItem('fitcore:cookie-transition'),revision),'raw switch must not notify coordinator');
  lifecycleEvidence.beforeForeground=await page.evaluate(()=>({focused:document.hasFocus(),visibility:document.visibilityState,events:JSON.parse(sessionStorage.getItem('__lifecycleEvents')||'[]')}));
  await page.bringToFront();await page.mouse.click(10,10);await uiIdentity(page,2);
  lifecycleEvidence.foregroundEvents=await page.evaluate(()=>JSON.parse(sessionStorage.getItem('__lifecycleEvents')||'[]'));
  assert(lifecycleEvidence.foregroundEvents.some(event=>event.trusted&&(event.type==='focus'||(event.type==='visibilitychange'&&event.visibility==='visible'))),'native foreground event required');
  check('foreground-session-revalidation',await page.getByText('Synthetic Gym 1 · Hello',{exact:false}).count()===0&&await page.evaluate(()=>!localStorage.getItem('workoutPlan:old-tenant:old-user')&&!sessionStorage.getItem('workoutPlan:old-tenant:old-user')));
  await page.waitForLoadState('networkidle');await backgroundTransition('owner1@fixture.invalid');
  await page.evaluate(()=>window.dispatchEvent(new PageTransitionEvent('pageshow',{persisted:true})));
  await uiIdentity(page,1);check('persisted-pageshow-revalidation',await page.getByText('Synthetic Gym 2 · Hello',{exact:false}).count()===0);
  await background.close();
  const cookies=await ctx.cookies(ui), auth=cookies.find(c=>c.name==='__Host-fitcore_session');
  check('secure-http-only-host-cookie',auth?.secure&&auth.httpOnly&&auth.path==='/'&&auth.sameSite==='Lax'&&auth.domain==='127.0.0.1'&&auth.value.length===43);
  check('no-browser-auth-secrets',await page.evaluate(()=>!document.cookie.includes('fitcore_session')&&!localStorage.getItem('token')&&!sessionStorage.getItem('token')));
  check('same-origin-no-store', (await api(page,'GET','/auth/me')).cache==='no-store');
  check('legacy-bearer-route-blocked',(await raw(page,'POST','/auth/login',{email:'owner1@fixture.invalid',password})).status===404);
  check('unsafe-without-csrf-blocked',(await raw(page,'POST','/browser/logout',{})).status===403);
  check('no-state-changing-get',(await raw(page,'GET','/browser/logout')).status===405);
  const cross=await ctx.request.post(ui+'/api/browser/logout',{headers:{Origin:'https://evil.invalid','Sec-Fetch-Site':'cross-site'},data:{}});
  check('cross-origin-blocked',cross.status()===403);
  check('client-tenant-selector-blocked',(await api(page,'GET','/admin/users?gymId=foreign')).status===400);
  for(const gym of [1,2]){
    const foreign=gym===1?2:1;
    for(const [role,index] of [['owner',null],['admin',0],['trainer',1],['member',2]]){
      const me=await transition(page,`${role}${gym}@fixture.invalid`);
      assert(me.id===(index===null?owner(gym):user(gym,index))&&me.role===role.toUpperCase(),'authoritative role');
      assert((await api(page,'GET','/users/'+user(foreign,2))).status===404,'foreign user');
      const admin=await api(page,'GET','/admin/users');assert(admin.status===(['owner','admin'].includes(role)?200:403),'role permission');
      if(admin.status===200)assert(!admin.text.includes(`member${foreign}@fixture.invalid`),'admin listing leak');
      const nested=await api(page,'POST','/workouts/logs/'+me.id,{userId:me.id,exercises:[{exercise:{id:foreign*100,name:'Foreign Bench',category:'Strength',difficulty:'BEGINNER'},setsCompleted:1,repsCompleted:'1'}],durationMinutes:10});
      assert(nested.status===404,'nested exercise');
    }
    check(`gym-${gym}-all-role-isolation`,true);
    const member=user(gym,2);await transition(page,`member${gym}@fixture.invalid`);
    let sub=await api(page,'POST',`/memberships/subscribe/${member}/${gym*100}`,{});assert(sub.status===200,'subscribe');
    const original=sub.data;
    let renew=await api(page,'POST',`/memberships/renew/${member}/${original.id}`,{expectedEndDate:original.endDate});assert(renew.status===200&&renew.data.id===original.id&&renew.data.endDate>original.endDate,'renewal');
    assert((await api(page,'POST',`/memberships/renew/${member}/${original.id}`,{expectedEndDate:original.endDate})).status===409,'stale renewal');
    const upgraded=await api(page,'POST',`/memberships/upgrade/${member}/${gym*100+1}`,{membershipId:original.id,expectedEndDate:renew.data.endDate});assert(upgraded.status===200,'upgrade');
    const history=await api(page,'GET',`/memberships/user/${member}`);assert(history.data.length===2&&history.data.some(m=>m.id===original.id&&m.status==='CANCELLED'),'membership history');
    check(`gym-${gym}-membership-rules`,true);
    const tomorrow=new Date(Date.now()+86400000).toISOString().slice(0,10);
    const booking={member:{id:member,fullName:'Synthetic member',email:`member${gym}@fixture.invalid`,role:'MEMBER'},trainer:{id:gym*100,specialization:'Strength',experienceYears:4},sessionDate:tomorrow,startTime:'10:00:00',endTime:'11:00:00'};
    assert((await api(page,'POST',`/trainers/book/${member}/${gym*100}`,booking)).status===200,'booking');
    assert((await api(page,'POST',`/trainers/book/${member}/${gym*100}`,booking)).status===409,'booking conflict');
    check(`gym-${gym}-booking-conflict`,true);
    const logged=await api(page,'POST','/workouts/logs/'+member,{userId:member,exercises:[{exercise:{id:gym*100,name:'Historical Bench',category:'Strength',difficulty:'BEGINNER'},setsCompleted:3,repsCompleted:'8'}],durationMinutes:30});assert(logged.status===200,'workout log');
    await transition(page,`owner${gym}@fixture.invalid`);
    const ex={name:'Historical Bench',category:'Strength',difficulty:'BEGINNER'};
    assert((await api(page,'PUT',`/admin/catalog/exercises/${gym*100}`,{...ex,name:'Illegal rename'})).status===409,'referenced history rename');
    let workout=await api(page,'POST','/admin/catalog/workouts',{level:'Spike '+gym,duration:'30 min',schedule:['Monday'],exerciseIds:[gym*100],status:'DRAFT'});assert(workout.status===201,'catalog draft');
    assert((await api(page,'POST',`/admin/catalog/workouts/${workout.data.id}/publish`,{})).status===200,'catalog publish');
    assert((await api(page,'POST',`/admin/catalog/workouts/${workout.data.id}/archive`,{})).status===200,'catalog archive');
    assert((await api(page,'POST',`/admin/catalog/exercises/${gym*100}/archive`,{})).status===200,'exercise archive');
    const logs=await api(page,'GET','/workouts/logs/'+member);assert(logs.text.includes('Historical Bench'),'historical names');
    check(`gym-${gym}-catalog-history`,true);
  }
  // Two real tabs, delayed HTTPS headers after the backend transaction has committed.
  const tab=await ctx.newPage();await tab.goto(ui+'/login');
  await control('hold','/api/browser/login');
  const delayed=api(page,'POST','/browser/login',{email:'owner1@fixture.invalid',password});await pending('/api/browser/login');
  check('pending-blocks-business',(await api(tab,'GET','/admin/users')).status===423);
  check('pending-blocks-second-login',(await api(tab,'POST','/browser/login',{email:'owner2@fixture.invalid',password})).status===423);
  check('pending-blocks-logout',(await api(tab,'POST','/browser/logout',{})).status===423);
  await control('release','/api/browser/login');const first=await delayed;assert(first.status===200,'held login');
  assert((await raw(page,'POST','/browser/ack',{},first.data.csrfToken)).status===200,'delivered ACK');
  const b=await transition(tab,'owner2@fixture.invalid');
  check('two-tab-gym-change',b.id===owner(2)&&(await api(page,'GET','/auth/me')).data.id===owner(2));
  await control('hold','/api/browser/logout');
  const logout=api(tab,'POST','/browser/logout',{});await pending('/api/browser/logout');
  check('logout-pending-blocks-login',(await api(page,'POST','/browser/login',{email:'owner1@fixture.invalid',password})).status===423);
  await control('release','/api/browser/logout');const out=await logout;
  assert((await raw(tab,'POST','/browser/ack',{},out.data.csrfToken)).status===200,'logout ACK');
  check('logout-expires-auth-cookie',!(await ctx.cookies(ui)).some(c=>c.name==='__Host-fitcore_session'));
  check('logout-no-old-session',(await api(page,'GET','/auth/me')).status===401);
  await transition(page,'owner1@fixture.invalid');await page.reload();await uiIdentity(page,1);
  await page.goto(ui+'/favicon.svg');await page.goBack();await uiIdentity(page,1);
  lifecycleEvidence.events=await page.evaluate(()=>JSON.parse(sessionStorage.getItem('__lifecycleEvents')||'[]'));
  lifecycleEvidence.backNavigation={url:page.url(),persistedObserved:lifecycleEvidence.events.some(event=>event.type==='pageshow'&&event.persisted&&event.trusted)};
  check('refresh-back-session-restore',page.url()===ui+'/owner'&&await page.getByText('Synthetic Gym 2 · Hello',{exact:false}).count()===0);
  await tab.close();
  // Exercise the actual adapter with real Web Locks, backend ACK and HTTPS delivery.
  const coordinated=await ctx.browser().newContext({ignoreHTTPSErrors:true});
  const left=await coordinated.newPage(),right=await coordinated.newPage();
  for(const p of [left,right]){
    await p.goto(ui+'/login');
    await p.evaluate(async()=>{
      const {createCookieSession}=await import('/__fixture/browser-session.js');
      window.__fixtureClient=createCookieSession({storage:localStorage,locks:navigator.locks,fetch:(...args)=>fetch(...args)});
    });
  }
  await left.evaluate(({password})=>window.__fixtureClient.browserLogin('owner1@fixture.invalid',password),{password});
  await right.evaluate(()=>window.__fixtureClient.browserMe());
  await control('hold','/api/browser/login');
  await left.evaluate(({password})=>{window.__fixtureLogin=window.__fixtureClient.browserLogin('owner1@fixture.invalid',password);},{password});
  await pending('/api/browser/login');
  await right.evaluate(({password})=>{window.__fixtureLogin=window.__fixtureClient.browserLogin('owner2@fixture.invalid',password);},{password});
  await control('release','/api/browser/login');
  const firstUser=await left.evaluate(()=>window.__fixtureLogin),secondUser=await right.evaluate(()=>window.__fixtureLogin);
  check('live-web-lock-serialization',firstUser.id===owner(1)&&secondUser.id===owner(2)&&(await api(left,'GET','/auth/me')).data.id===owner(2));
  await left.evaluate(()=>window.__fixtureClient.browserMe());
  // Queue a login ahead of an old view's PATCH while another live lock is held.
  const beforeName=(await api(left,'GET','/gym')).data.name;
  await left.evaluate(async()=>{
    window.__fixtureLockReady=new Promise(ready=>navigator.locks.request('fitcore:cookie-session',async()=>{ready();await new Promise(release=>{window.__fixtureRelease=release;});}));
    await window.__fixtureLockReady;
  });
  await left.evaluate(({password})=>{
    window.__fixtureLogin=window.__fixtureClient.browserLogin('owner1@fixture.invalid',password);
    window.__fixtureStale=window.__fixtureClient.cookieApiRequest({url:'/gym',method:'PATCH',data:{name:'STALE_PAYLOAD_MUST_NOT_WRITE'}}).then(()=>({sent:true}),e=>({sent:false,code:e.code}));
    window.__fixtureRelease();
  },{password});
  await left.evaluate(()=>window.__fixtureLogin);
  const stale=await left.evaluate(()=>window.__fixtureStale);
  check('queued-old-view-write-blocked',!stale.sent&&stale.code==='ERR_CANCELED'&&(await api(left,'GET','/gym')).data.name!== 'STALE_PAYLOAD_MUST_NOT_WRITE'&&beforeName!=='STALE_PAYLOAD_MUST_NOT_WRITE');
  await coordinated.close();
  // New isolated contexts: interruption before ACK must survive tab death and refresh.
  const isolated=await ctx.browser().newContext({ignoreHTTPSErrors:true});
  const interrupted=await isolated.newPage();await interrupted.goto(ui+'/login');
  await control('hold-body','/api/browser/login');
  await interrupted.getByLabel('Email',{exact:true}).fill('owner1@fixture.invalid');await interrupted.getByLabel('Password',{exact:true}).fill(password);
  await interrupted.getByRole('button',{name:'Login',exact:true}).click();await pending('/api/browser/login');
  for(let n=0;n<100 && !(await isolated.cookies(ui)).some(c=>c.name==='__Host-fitcore_session');n++)await new Promise(resolve=>setTimeout(resolve,50));
  assert((await isolated.cookies(ui)).some(c=>c.name==='__Host-fitcore_session'),'SetCookie headers not actually delivered');
  await interrupted.close();await control('release','/api/browser/login');
  const survivor=await isolated.newPage();await survivor.goto(ui+'/login');
  assert(await survivor.evaluate(()=>!!localStorage.getItem('fitcore:cookie-transition')),'uncertainty marker lost on tab close');
  assert((await api(survivor,'GET','/auth/me')).status===423,'unacknowledged cookie authorized business');
  await survivor.reload();
  check('tab-close-uncertainty',await survivor.evaluate(()=>!!localStorage.getItem('fitcore:cookie-transition'))&&(await api(survivor,'GET','/auth/me')).status===423);
  await isolated.close();
  const timed=await ctx.browser().newContext({ignoreHTTPSErrors:true}), timedPage=await timed.newPage();await timedPage.goto(ui+'/login');
  await control('hold','/api/browser/login');
  await timedPage.getByLabel('Email',{exact:true}).fill('owner2@fixture.invalid');await timedPage.getByLabel('Password',{exact:true}).fill(password);
  await timedPage.getByRole('button',{name:'Login',exact:true}).click();await pending('/api/browser/login');
  await timedPage.getByText(/cookie.*delivery|uncertain|interrupted|timed out/i).first().waitFor({timeout:30000});
  assert(await timedPage.evaluate(()=>!!localStorage.getItem('fitcore:cookie-transition')),'timeout marker lost');
  await control('release','/api/browser/login');await timedPage.reload();
  check('timeout-uncertainty',(await api(timedPage,'GET','/auth/me')).status===423&&await timedPage.evaluate(()=>!!localStorage.getItem('fitcore:cookie-transition')));
  await timed.close();
  check('no-uncaught-errors',errors.length===0);
  return {suite:'fitcore-browser-spike-v1',passed:checks.length,checks,failures,browser:await page.evaluate(()=>navigator.userAgent),webLocks:await page.evaluate(()=>!!navigator.locks),lifecycleEvidence,production:'BLOCKED'};
}
