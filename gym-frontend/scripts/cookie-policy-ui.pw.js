/* eslint-disable no-unused-expressions -- Owned HTTPS UI stages; SQL is performed only by the harness shell. */
async (page) => {
  const ui='https://127.0.0.1:18444',stage='@POLICY_STAGE@',oldPassword='Browser-Spike!2026',newPassword='Browser-Policy-New!2026';
  const parsed=/^gym-([12])-(expired|password|role|disabled)-(prepare|verify)$/.exec(stage);
  if(!parsed)throw Error('Unknown policy stage');
  const [,gym,policy,phase]=parsed,ctx=page.context(),browser=ctx.browser(),checks=[],failures=[],recent=[],errors=[];
  const email=`${policy==='role'?'trainer':'member'}${gym}@fixture.invalid`;
  const assert=(ok,message)=>{if(!ok)throw Error(message);};
  const button=name=>page.getByRole('button',{name,exact:true});
  const metadata=async()=>{const r=await ctx.request.get(ui+'/api/browser/csrf',{headers:{'Sec-Fetch-Site':'same-origin'}});return {status:r.status(),data:await r.json()};};
  const request=(method,path)=>page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname===path);
  const marker=()=>page.evaluate(()=>localStorage.getItem('fitcore:cookie-transition'));
  const login=async(secret,denied=false)=>{
    await page.bringToFront();await page.getByText('Secure browser session',{exact:true}).waitFor();
    await page.getByLabel('Email',{exact:true}).fill(email);await page.getByLabel('Password',{exact:true}).fill(secret);
    const result=request('POST','/api/browser/login');await button('Login').click();
    const response=await result;
    if(denied){assert(response.status()===401,'fresh credentials must be denied with 401');await page.getByRole('alert').filter({hasText:'Email or password is incorrect.'}).waitFor();assert(!await marker()&&await button('Login').isEnabled(),'complete denial permits only explicit retry');return;}
    assert(response.status()===200,'explicit login must succeed');await page.waitForURL(ui+'/dashboard');
    await page.getByRole('heading',{name:'Dashboard Overview',exact:true}).waitFor();await button('Sign Out').waitFor();
    const me=await page.evaluate(async()=>{const r=await fetch('/api/auth/me',{credentials:'same-origin',cache:'no-store'});return {status:r.status,data:await r.json()};});
    assert(me.status===200&&me.data.gymId===`10000000-0000-0000-0000-00000000000${gym}`,'fresh identity belongs to intended gym');
    assert(me.data.role===(policy==='role'&&phase==='prepare'?'TRAINER':'MEMBER'),'fresh identity has expected current role');
    assert(!await marker(),'successful login settled own marker');
  };
  page.on('response',r=>{const u=new URL(r.url());if(u.pathname.startsWith('/api/')){recent.push({path:u.pathname,status:r.status()});if(recent.length>16)recent.shift();}});
  page.on('pageerror',e=>errors.push(String(e.message).slice(0,500)));
  try {
    await page.bringToFront();
    if(phase==='prepare'){
      await page.goto(ui+'/login');await login(policy==='disabled'?newPassword:oldPassword);
      const current=await metadata();assert(current.status===200&&current.data.authenticated&&current.data.currentContext&&current.data.transitionState==='IDLE','prepared current live session');
      return {prepared:true,stage,policyFamilyId:current.data.familyId,browserType:browser.browserType().name(),browserVersion:browser.version(),production:'BLOCKED'};
    }
    // A real navigation revalidates the mutated current account before any UI data.
    await page.reload();await page.waitForURL(ui+'/login');await button('Sign out previous session').waitFor();
    assert(await page.getByRole('heading',{name:'Dashboard Overview',exact:true}).count()===0,'revoked/expired dashboard is absent');
    assert(await button('Login').isDisabled(),'current control handle must explicitly sign out first');
    const denied=await page.evaluate(async()=>{const r=await fetch('/api/gym',{credentials:'same-origin',cache:'no-store'});return r.status;});
    assert(denied===401,'current expired or revoked handle cannot authenticate gym data');
    const before=await metadata();
    assert(before.status===200&&before.data.reauthenticationRequired===true&&before.data.authenticated===false&&before.data.currentContext===true&&before.data.contextKind==='AUTH'&&before.data.transitionState==='IDLE','current handle supplies control proof only');
    const logout=request('POST','/api/browser/logout'),ack=request('POST','/api/browser/ack');
    await page.bringToFront();await button('Sign out previous session').click();
    assert((await logout).status()===200&&(await ack).status()===200,'actual explicit logout and ACK succeed');
    await button('Sign out previous session').waitFor({state:'hidden'});await button('Login').waitFor();
    assert(await button('Login').isEnabled()&&!await marker(),'explicit logout restores usable credential form');
    const after=await metadata();
    assert(after.status===200&&after.data.contextKind==='PREAUTH'&&!after.data.authenticated&&!after.data.reauthenticationRequired&&after.data.transitionState==='IDLE'&&after.data.familyId===before.data.familyId&&after.data.contextRevision===String(BigInt(before.data.contextRevision)+1n)&&after.data.familyRevision===after.data.contextRevision,'logout rotated only same-family next revision to anonymous PREAUTH');
    assert(!(await ctx.cookies(ui)).some(c=>c.name==='__Host-fitcore_session'),'old AUTH cookie is absent after explicit logout');
    if(policy==='password'){await login(oldPassword,true);await login(newPassword);}
    else if(policy==='disabled')await login(newPassword,true);
    else await login(oldPassword);
    assert(errors.length===0,'no page errors');
    assert(await page.evaluate(()=>!localStorage.getItem('token')&&!sessionStorage.getItem('token')),'no bearer fallback');
    const cases={expired:'expired-current-explicit-logout-reauthentication',password:'password-change-explicit-logout-reauthentication',role:'role-revocation-explicit-logout-reauthentication',disabled:'disabled-account-explicit-logout-denied'};
    checks.push(`gym-${gym}.${cases[policy]}`);
  }catch(error){
    failures.push({id:stage,message:String(error.message||error).slice(0,1400),url:page.url(),body:(await page.locator('body').innerText().catch(()=>'<unavailable>')).replace(/[A-Za-z0-9_-]{32,}/g,'[redacted]').slice(0,1800),recent,errors});
  }
  return {suite:'fitcore-cookie-policy-ui-v1',passed:checks.length,checks,failures,browserType:browser.browserType().name(),browserVersion:browser.version(),production:'BLOCKED'};
}
