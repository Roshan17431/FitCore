/* eslint-disable no-unused-expressions -- Owned disposable HTTPS browser harness. */
async (page) => {
  const ui='https://127.0.0.1:18444',password='Browser-Spike!2026',fixture='@FIXTURE_CONTROL@';
  const browser=page.context().browser(),checks=[],failures=[];
  const assert=(ok,label)=>{if(!ok)throw Error(label);};
  const check=(id,ok)=>{assert(ok,id);checks.push(id);};
  const contexts=[],recent=[],pageerrors=[];
  const fresh=async()=>{const c=await browser.newContext({ignoreHTTPSErrors:true});contexts.push(c);c.on('response',r=>{const u=new URL(r.url());if(u.pathname.startsWith('/api/')){recent.push({path:u.pathname,status:r.status()});if(recent.length>24)recent.shift();}});const p=await c.newPage();p.on('pageerror',e=>pageerrors.push(String(e.message).slice(0,500)));await p.bringToFront();await p.goto(ui+'/login');await p.getByRole('button',{name:'Login',exact:true}).waitFor();await p.waitForLoadState('networkidle');return [c,p];};
  const fill=async(p,email,secret=password)=>{await p.bringToFront();await p.getByLabel('Email',{exact:true}).fill(email);await p.getByLabel('Password',{exact:true}).fill(secret);await p.getByRole('button',{name:'Login',exact:true}).click();};
  const control=async(c,command,path)=>{const r=await c.request.post(ui+'/__fixture/'+command,{headers:{'X-FitCore-Fixture':fixture},data:path});assert(r.status()===204,'fixture control '+command);};
  const held=async(c,path)=>{for(let n=0;n<120;n++){const r=await c.request.get(ui+'/__fixture/state',{headers:{'X-FitCore-Fixture':fixture}});if((await r.json()).some(g=>g.target===path&&g.waiting>0))return;await new Promise(resolve=>setTimeout(resolve,50));}throw Error('No held response '+path);};
  const state=async c=>{const r=await c.request.get(ui+'/api/browser/csrf',{headers:{'Sec-Fetch-Site':'same-origin'}});return {status:r.status(),data:await r.json()};};
  const marker=p=>p.evaluate(()=>localStorage.getItem('fitcore:cookie-transition'));
  const identity=async(p,gym)=>{await p.waitForURL(ui+'/owner');await p.getByRole('heading',{name:`Synthetic Gym ${gym}`,exact:true}).waitFor();};
  try {
    for(const gym of [1,2]){
      const [c,p]=await fresh();
      await fill(p,`owner${gym}@fixture.invalid`,'incorrect-synthetic-password');
      await p.getByRole('alert').filter({hasText:'Email or password is incorrect.'}).waitFor();
      check(`gym-${gym}-incorrect-password-retry`,!await marker(p)&&await p.getByRole('button',{name:'Login',exact:true}).isEnabled()&&(await state(c)).data.transitionState==='IDLE');
      await fill(p,`owner${gym}@fixture.invalid`);await identity(p,gym);
      check(`gym-${gym}-explicit-login-after-denial`,!await marker(p)&&await p.evaluate(()=>!localStorage.getItem('token')&&!sessionStorage.getItem('token')));
      await c.close();
    }
    // Native tab closure after headers, before LOGIN body/ACK. Only bound candidate may recover.
    {
      const [c,p]=await fresh();await control(c,'hold-body','/api/browser/login');
      await fill(p,'owner1@fixture.invalid');await held(c,'/api/browser/login');
      for(let n=0;n<100&&!(await c.cookies(ui)).some(v=>v.name==='__Host-fitcore_session');n++)await new Promise(resolve=>setTimeout(resolve,50));
      assert((await c.cookies(ui)).some(v=>v.name==='__Host-fitcore_session'),'candidate headers received');
      const before=JSON.parse(await marker(p));assert(before.preFamilyId&&before.preServerRevision,'bound marker');
      await p.close();await control(c,'release','/api/browser/login');
      const survivor=await c.newPage();await survivor.bringToFront();await survivor.goto(ui+'/login');await survivor.getByRole('button',{name:'Check sign-in status',exact:true}).waitFor();
      assert((await state(c)).data.transitionState==='PENDING','not autoacknowledged');
      await survivor.getByRole('button',{name:'Check sign-in status',exact:true}).click();await identity(survivor,1);
      check('tab-close-received-cookie-recovery',!await marker(survivor)&&(await state(c)).data.transitionState==='IDLE');await c.close();
    }
    // ACK committed but its receipt was lost. IDLE exact N+1 is still a valid proof.
    {
      const [c,p]=await fresh();await control(c,'hold-body','/api/browser/ack');
      await fill(p,'owner2@fixture.invalid');await held(c,'/api/browser/ack');
      assert((await state(c)).data.transitionState==='IDLE','ACK really committed');
      await p.close();await control(c,'release','/api/browser/ack');
      const survivor=await c.newPage();await survivor.bringToFront();await survivor.goto(ui+'/login');await survivor.getByRole('button',{name:'Check sign-in status',exact:true}).click();await identity(survivor,2);
      check('lost-ack-receipt-recovery',!await marker(survivor));await c.close();
    }
    // Received logout cookie can settle after tab death; no business cookie is restored.
    {
      const [c,p]=await fresh();await fill(p,'owner1@fixture.invalid');await identity(p,1);
      await p.evaluate(async()=>{const {createCookieSession}=await import('/__fixture/browser-session.js');window.__client=createCookieSession({storage:localStorage,locks:navigator.locks,fetch:(...args)=>fetch(...args)});await window.__client.browserMe();});
      await control(c,'hold-body','/api/browser/logout');
      await p.evaluate(()=>{window.__out=window.__client.browserLogout().catch(()=>null);});await held(c,'/api/browser/logout');
      for(let n=0;n<100&&(await c.cookies(ui)).some(v=>v.name==='__Host-fitcore_session');n++)await new Promise(resolve=>setTimeout(resolve,50));
      assert(!(await c.cookies(ui)).some(v=>v.name==='__Host-fitcore_session'),'logout cookie actually received');
      await p.close();await control(c,'release','/api/browser/logout');const survivor=await c.newPage();await survivor.bringToFront();await survivor.goto(ui+'/login');
      await survivor.getByRole('button',{name:'Check sign-in status',exact:true}).click();
      await survivor.getByRole('button',{name:'Login',exact:true}).waitFor();
      for(let n=0;n<100&&await marker(survivor);n++)await new Promise(resolve=>setTimeout(resolve,50));
      check('lost-logout-receipt-recovery',!await marker(survivor)&&!(await c.cookies(ui)).some(v=>v.name==='__Host-fitcore_session')&&(await state(c)).data.contextKind==='PREAUTH');
      await fill(survivor,'owner2@fixture.invalid');await identity(survivor,2);await c.close();
    }
    // No received headers: a still-old cookie cannot settle a possibly late writer.
    {
      const [c,p]=await fresh();await control(c,'hold','/api/browser/login');await fill(p,'owner1@fixture.invalid');await held(c,'/api/browser/login');
      await p.close();const survivor=await c.newPage();await survivor.bringToFront();await survivor.goto(ui+'/login');await survivor.getByRole('button',{name:'Check sign-in status',exact:true}).click();
      await survivor.getByRole('alert').filter({hasText:/This original context remains blocked/i}).waitFor();
      check('old-cookie-recovery-remains-held',!!await marker(survivor)&&await survivor.getByRole('button',{name:'Login',exact:true}).isDisabled()&&(await state(c)).data.currentContext===false);
      await control(c,'release','/api/browser/login');await c.close();
    }
    // Simulated session-cookie loss preserves non-secret uncertainty; never bootstrap through it.
    {
      const [c,p]=await fresh();await control(c,'hold-body','/api/browser/login');await fill(p,'owner1@fixture.invalid');await held(c,'/api/browser/login');
      await p.close();await c.clearCookies();await control(c,'release','/api/browser/login');
      const survivor=await c.newPage();await survivor.bringToFront();await survivor.goto(ui+'/login');await survivor.getByRole('button',{name:'Check sign-in status',exact:true}).click();
      await survivor.getByRole('alert').filter({hasText:/This original context remains blocked/i}).waitFor();
      check('missing-cookie-recovery-remains-held',!!await marker(survivor)&&(await c.cookies(ui)).length===0&&await survivor.getByRole('button',{name:'Login',exact:true}).isDisabled());
      const guidance=survivor.getByRole('alert').filter({hasText:'This original context remains blocked'});
      check('fresh-context-accessible-instructions',(await guidance.innerText()).includes('separate browser profile')&&(await guidance.innerText()).includes('does not repair'));
      const originalMarker=await marker(survivor),[isolated,newPage]=await fresh();await fill(newPage,'owner2@fixture.invalid');await identity(newPage,2);
      await survivor.bringToFront();await survivor.reload();
      assert(await marker(survivor)===originalMarker && await survivor.getByRole('button',{name:'Login',exact:true}).isDisabled(),'isolated context cannot release original fence');
      await survivor.getByRole('button',{name:'Check sign-in status',exact:true}).click();await survivor.getByRole('alert').filter({hasText:'This original context remains blocked'}).waitFor();
      check('fresh-isolated-context-does-not-repair-original',await marker(survivor)===originalMarker&&(await state(c)).status===401);
      await isolated.close();await c.close();
    }
    for(const gym of [1,2]) {
      // The test wrapper has not forwarded this genuine LOGIN at all: row/family
      // revision N is still current IDLE. It cannot prove a delayed writer ended.
      {
        const [c,p]=await fresh();await control(c,'hold-before','/api/browser/login');
        await fill(p,`owner${gym}@fixture.invalid`);await held(c,'/api/browser/login');
        const owned=await marker(p),bound=JSON.parse(owned),before=await state(c);
        assert(before.status===200&&before.data.transitionState==='IDLE'&&before.data.currentContext===true&&before.data.familyId===bound.preFamilyId&&before.data.contextRevision===bound.preServerRevision&&before.data.familyRevision===bound.preServerRevision,'actual pre-mutation same-N proof');
        await p.close();const survivor=await c.newPage();await survivor.bringToFront();await survivor.goto(ui+'/login');
        await survivor.getByRole('button',{name:'Check sign-in status',exact:true}).click();
        await survivor.getByRole('alert').filter({hasText:/This original context remains blocked/i}).waitFor();
        const after=await state(c);
        check(`gym-${gym}-same-revision-pre-mutation-remains-held`,await marker(survivor)===owned&&await survivor.getByRole('button',{name:'Login',exact:true}).isDisabled()&&after.status===200&&after.data.currentContext===true&&after.data.transitionState==='IDLE'&&after.data.contextRevision===bound.preServerRevision&&after.data.familyRevision===bound.preServerRevision);
        await control(c,'release','/api/browser/login');await c.close();
      }
      // Switch to a different, independently authenticated family while the
      // original received-cookie writer marker stays intact in local storage.
      {
        const [donor,other]=await fresh();await fill(other,`owner${gym===1?2:1}@fixture.invalid`);await identity(other,gym===1?2:1);
        const foreign=await state(donor),foreignCookies=await donor.cookies(ui);
        const [c,p]=await fresh();await control(c,'hold-body','/api/browser/login');
        await fill(p,`owner${gym}@fixture.invalid`);await held(c,'/api/browser/login');
        const owned=await marker(p),bound=JSON.parse(owned);
        assert(foreign.status===200&&foreign.data.authenticated&&foreign.data.familyId!==bound.preFamilyId,'independent real cookie family');
        await p.close();await control(c,'release','/api/browser/login');
        await c.clearCookies();await c.addCookies(foreignCookies);
        const survivor=await c.newPage();let businessRequests=0;
        survivor.on('request',r=>{if(new URL(r.url()).pathname==='/api/gym')businessRequests++;});
        await survivor.bringToFront();await survivor.goto(ui+'/login');
        await survivor.getByRole('button',{name:'Check sign-in status',exact:true}).click();
        await survivor.getByRole('alert').filter({hasText:/This original context remains blocked/i}).waitFor();
        const switched=await state(c);
        check(`gym-${gym}-raw-different-family-recovery-remains-held`,await marker(survivor)===owned&&await survivor.getByRole('button',{name:'Login',exact:true}).isDisabled()&&switched.status===200&&switched.data.familyId===foreign.data.familyId&&businessRequests===0);
        await c.close();await donor.close();
      }
    }
    // Exact pre-handler rate denial is definitive; it never replays automatically.
    {
      const remaining=300-(Math.floor(Date.now()/1000)%300);
      if(remaining<15)await new Promise(resolve=>setTimeout(resolve,remaining*1000+100));
      const [c,p]=await fresh();const before=await state(c);let exhausted=false;
      for(let n=0;n<51;n++){
        const r=await c.request.post(ui+'/api/browser/login',{headers:{Origin:ui,'Sec-Fetch-Site':'same-origin','X-CSRF-TOKEN':before.data.csrfToken},data:{email:'owner1@fixture.invalid',password:'incorrect-synthetic-password'}});
        if(r.status()===429){exhausted=true;break;}assert(r.status()===401,'generic priming denial');
      }
      assert(exhausted,'shared limiter really exhausted');let submitted=0;
      p.on('request',r=>{if(r.method()==='POST'&&new URL(r.url()).pathname==='/api/browser/login')submitted++;});
      for(let attempt=0;attempt<2;attempt++){
        const observed=p.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/browser/login');
        await fill(p,'owner1@fixture.invalid');assert((await observed).status()===429,'explicit action rate denied');
        await p.getByRole('alert').filter({hasText:'HTTP 429: RATE_LIMITED'}).waitFor();
        for(let n=0;n<100&&await marker(p);n++)await new Promise(resolve=>setTimeout(resolve,50));
        assert(!await marker(p)&&await p.getByRole('button',{name:'Login',exact:true}).isEnabled(),'proven rate denial releases owned marker');
      }
      check('controlled-rate-denial-explicit-retry',submitted===2&&(await state(c)).data.transitionState==='IDLE');await c.close();
    }
  } catch(error) {
    const diagnostics=[];
    for(const c of contexts)for(const p of c.pages())if(!p.isClosed())diagnostics.push({url:p.url(),body:(await p.locator('body').innerText().catch(()=>'<unavailable>')).replace(/[A-Za-z0-9_-]{32,}/g,'[redacted]').slice(0,1800)});
    failures.push({id:'cookie-recovery.execution',message:String(error.message||error).slice(0,1400),diagnostics,recent,pageerrors});
  } finally {for(const c of contexts)await c.close().catch(()=>{});}
    return {suite:'fitcore-cookie-recovery-v1',passed:checks.length,checks,failures,browserType:browser.browserType().name(),browserVersion:browser.version(),browser:await page.evaluate(()=>navigator.userAgent),production:'BLOCKED'};
}
