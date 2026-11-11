/* eslint-disable no-unused-expressions -- Native process restart with a disposable browser profile. */
async (page) => {
  const ui='https://127.0.0.1:18444',fixture='@FIXTURE_CONTROL@',stage='@RESTART_STAGE@',ctx=page.context();
  const recent=[],pageerrors=[];
  page.on('response',r=>{const u=new URL(r.url());if(u.pathname.startsWith('/api/')){recent.push({path:u.pathname,status:r.status()});if(recent.length>16)recent.shift();}});
  page.on('pageerror',e=>pageerrors.push(String(e.message).slice(0,500)));
  const assert=(ok,label)=>{if(!ok)throw Error(label);};
  const control=async(command)=>{const r=await ctx.request.post(ui+'/__fixture/'+command,{headers:{'X-FitCore-Fixture':fixture},data:'/api/browser/login'});assert(r.status()===204,'fixture control');};
  try {
  await page.bringToFront();
  await page.goto(ui+'/login');await page.waitForLoadState('networkidle');
  if(stage==='before'){
    await control('hold-body');await page.getByLabel('Email',{exact:true}).fill('owner1@fixture.invalid');await page.getByLabel('Password',{exact:true}).fill('Browser-Spike!2026');await page.getByRole('button',{name:'Login',exact:true}).click();
    for(let n=0;n<120;n++){
      const response=await ctx.request.get(ui+'/__fixture/state',{headers:{'X-FitCore-Fixture':fixture}});
      if((await response.json()).some(g=>g.waiting>0)&&(await ctx.cookies(ui)).some(c=>c.name==='__Host-fitcore_session'))break;
      await new Promise(resolve=>setTimeout(resolve,50));
    }
    assert((await ctx.cookies(ui)).some(c=>c.name==='__Host-fitcore_session'),'headers actually delivered before process closure');
    assert(await page.evaluate(()=>!!localStorage.getItem('fitcore:cookie-transition')),'native profile has pending marker');
    return {prepared:true,browserType:ctx.browser().browserType().name(),browserVersion:ctx.browser().version(),production:'BLOCKED'};
  }
  assert(stage==='after','restart stage');
  await control('release');
  await page.getByRole('button',{name:'Check sign-in status',exact:true}).waitFor();
  assert(await page.evaluate(()=>!!localStorage.getItem('fitcore:cookie-transition')),'marker survived actual browser process restart');
  const response=await ctx.request.get(ui+'/api/browser/csrf',{headers:{'Sec-Fetch-Site':'same-origin'}}),metadata=await response.json();
  const cookies=await ctx.cookies(ui),cookiePresence={auth:cookies.some(c=>c.name==='__Host-fitcore_session'),preauth:cookies.some(c=>c.name==='__Host-fitcore_preauth')};
  const marker=await page.evaluate(()=>JSON.parse(localStorage.getItem('fitcore:cookie-transition')));
  const received=await page.evaluate(async evidence=>{const {restartProof}=await import('/__fixture/restart-proof.js');return restartProof(evidence);},{status:response.status(),metadata,marker,cookiePresence});
  await page.getByRole('button',{name:'Check sign-in status',exact:true}).click();
  if(received){
    await page.waitForURL(ui+'/owner');await page.getByRole('heading',{name:'Synthetic Gym 1',exact:true}).waitFor();
    assert(await page.evaluate(()=>!localStorage.getItem('fitcore:cookie-transition')),'proof reconciled');
  }else{
    await page.getByRole('alert').filter({hasText:/This original context remains blocked/i}).waitFor();
    assert(await page.evaluate(()=>!!localStorage.getItem('fitcore:cookie-transition')),'no proof stays held');
    assert(await page.getByRole('button',{name:'Login',exact:true}).isDisabled(),'unproven restart cannot login');
  }
  assert(await page.evaluate(()=>!localStorage.getItem('token')&&!sessionStorage.getItem('token')),'no bearer fallback');
  return {suite:'fitcore-cookie-native-restart-v1',passed:2,checks:['native-browser-restart-retains-fence','native-browser-restart-bound-proof-policy'],failures:[],receivedCookieProof:received,restartPolicyOutcome:received?'received-cookie':'missing-cookie',boundCandidate:received,cookiePresence,browserType:ctx.browser().browserType().name(),browserVersion:ctx.browser().version(),browser:await page.evaluate(()=>navigator.userAgent),production:'BLOCKED'};
  }catch(error){
    throw Error(JSON.stringify({message:String(error.message||error).slice(0,1400),url:page.url(),body:(await page.locator('body').innerText().catch(()=>'<unavailable>')).replace(/[A-Za-z0-9_-]{32,}/g,'[redacted]').slice(0,1800),recent,pageerrors}));
  }
}
