/* eslint-disable no-unused-expressions -- Executed by the disposable Playwright CLI harness. */
async(page)=>{
  const ui='http://127.0.0.1:5175';const checks=[];
  await page.goto(ui+'/login');await page.evaluate(()=>{localStorage.clear();sessionStorage.clear();});
  const submit=async(owner)=>{
    await page.getByLabel('Full name',{exact:true}).fill('Gate Fixture');
    await page.getByLabel(owner?'Email':'Invited email',{exact:true}).fill('disabled@fixture.invalid');
    await page.getByLabel('Password',{exact:true}).fill('Disabled-Password!2026');await page.getByLabel('Confirm password',{exact:true}).fill('Disabled-Password!2026');
    if(owner){await page.getByLabel('Gym name',{exact:true}).fill('Disabled Gym');await page.getByLabel('Gym slug',{exact:true}).fill('disabled-gym');}
    const response=page.waitForResponse(r=>r.url().endsWith(owner?'/auth/register-owner':'/auth/register')&&r.request().method()==='POST');
    await page.getByRole('button',{name:owner?'Create gym workspace':'Accept invitation',exact:true}).click();
    if((await response).status()!==403)throw Error('Disabled gate did not reject registration');
    await page.getByRole('alert').filter({hasText:'Public onboarding is unavailable'}).waitFor();
    if(await page.evaluate(()=>localStorage.getItem('token')||sessionStorage.getItem('token')))throw Error('Disabled gate established a session');
  };
  await page.goto(ui+'/create-gym');await submit(true);checks.push('Gate-off owner signup displays unavailable and establishes no session');
  await page.goto(ui+'/register#invitationToken='+'x'.repeat(43));await submit(false);if(page.url().includes('invitationToken'))throw Error('Fragment retained');checks.push('Gate-off invitation acceptance is rejected and fragment scrubbed');
  await page.screenshot({path:'../output/playwright/phase5-onboarding-disabled.png',fullPage:true});
  await page.goto(ui+'/login');await page.getByLabel('Email',{exact:true}).fill('browser-owner@fixture.invalid');await page.getByLabel('Password',{exact:true}).fill('Phase3-Local-Only!Verify26');await page.getByRole('button',{name:'Login',exact:true}).click();await page.waitForURL(ui+'/owner');
  await page.evaluate(()=>localStorage.setItem('fitcore_theme','dark'));await page.setViewportSize({width:390,height:844});await page.goto(ui+'/gym/invitations');await page.getByText('No invitations yet. Your first invitation will appear here.',{exact:true}).waitFor();
  await page.screenshot({path:'../output/playwright/phase5-invitations-mobile.png',fullPage:true});checks.push('Gate-off existing owner can restore session and view invitation history');
  return {passed:checks.length,checks};
}
