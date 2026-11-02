/* eslint-disable no-unused-expressions -- Executed only by the owned disposable UI harness. */
async(page)=>{
 const ui='http://127.0.0.1:5175',api='http://127.0.0.1:18082/api';const checks=[];
 const assert=(value,message)=>{if(!value)throw Error(message);};
 await page.goto(ui+'/login');await page.evaluate(()=>{localStorage.clear();sessionStorage.clear();});
 await page.goto(ui+'/verify-email#token='+'a'.repeat(43));await page.getByLabel('Account email',{exact:true}).waitFor();assert(!page.url().includes('#'),'verification fragment retained');
 await page.goto(ui+'/verify-email#token='+'b'.repeat(43));await page.waitForFunction(()=>!location.hash,null,{timeout:5000});
 await page.getByLabel('Account email',{exact:true}).fill('synthetic@fixture.invalid');await page.getByRole('button',{name:'Verify email',exact:true}).click();await page.getByRole('alert').waitFor();checks.push('Verification fragments scrubbed including same-document replacement; disabled gate rejects');
 await page.goto(ui+'/reset-password#token='+'c'.repeat(43));await page.getByLabel('Account email',{exact:true}).fill('synthetic@fixture.invalid');await page.getByLabel('New password',{exact:true}).fill('Changed-Password!2026');await page.getByLabel('Confirm password',{exact:true}).fill('Mismatch-Password!2026');await page.getByRole('button',{name:'Change password',exact:true}).click();await page.getByText('Passwords do not match.',{exact:true}).waitFor();await page.getByLabel('Confirm password',{exact:true}).fill('Changed-Password!2026');await page.getByRole('button',{name:'Change password',exact:true}).click();await page.getByRole('alert').filter({hasText:/HTTP 403|unavailable/}).waitFor();assert(!page.url().includes('#'),'reset fragment retained');checks.push('Reset password validation and disabled-gate feedback');
 await page.goto(ui+'/secure/create-gym');for(const [label,value] of Object.entries({'Full name':'Secure Owner','Email':'secure-owner@fixture.invalid','Password':'Secure-Password!2026','Confirm password':'Secure-Password!2026','Gym name':'Secure Gym','Gym slug':'secure-gym'}))await page.getByLabel(label,{exact:true}).fill(value);await page.getByRole('button',{name:'Create gym workspace',exact:true}).click();await page.getByRole('alert').waitFor();assert(!(await page.evaluate(()=>localStorage.getItem('token'))),'disabled pending signup created session');checks.push('Secure registration remains production-disabled');
 // Isolated race regression: only verification response is paused/stubbed; both identities/me calls are real PostgreSQL-backed sessions.
 const response=await page.request.post(api+'/auth/login',{data:{email:'phase2-member@e2e.test',password:'Fitcore@123'}});assert(response.ok(),'member fixture login');const tokenA=(await response.json()).token;
 let release,started;const waiting=new Promise(resolve=>{release=resolve;});const sent=new Promise(resolve=>{started=resolve;});
 const routeHandler=async route=>{started();await waiting;await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({token:tokenA})});};
 await page.route('**/api/auth/verify-email',routeHandler);let other;
 try{
  await page.goto(ui+'/verify-email#token='+'d'.repeat(43));await page.getByLabel('Account email',{exact:true}).fill('phase2-member@e2e.test');await page.getByRole('button',{name:'Verify email',exact:true}).click();await sent;
  other=await page.context().newPage();await other.goto(ui+'/login');await other.getByLabel('Email',{exact:true}).fill('tenant-b-admin@fixture.invalid');await other.getByLabel('Password',{exact:true}).fill('Phase3-Local-Only!Verify26');await other.getByRole('button',{name:'Login',exact:true}).click();await other.waitForURL(ui+'/dashboard');
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem('user')||'{}').email==='tenant-b-admin@fixture.invalid');release();
  await page.waitForFunction(()=>!!document.querySelector('[role="alert"]')||location.pathname!=='/verify-email',null,{timeout:8000});
  assert(await page.evaluate(()=>JSON.parse(localStorage.getItem('user')||'{}').email)==='tenant-b-admin@fixture.invalid','late verification overwrote newer gym session');checks.push('Delayed verification cannot overwrite a newer cross-tab session');
 }finally{release();if(other)await other.close();await page.unroute('**/api/auth/verify-email',routeHandler);}
 return {passed:checks.length,checks};
}
