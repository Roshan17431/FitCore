/* eslint-disable no-unused-expressions -- Executed by playwright-cli run-code. */
async (page) => {
  // Only the owned disposable PostgreSQL harness uses these public local fixture credentials.
  const ui = 'http://127.0.0.1:5175', api = 'http://127.0.0.1:18082';
  const output = '../output/playwright/phase3-2026-10-21';
  const passed = [], errors = [], external = [];
  page.on('pageerror', e => errors.push(e.message));
  page.context().on('request', request => { const u=new URL(request.url()); if(!['data:','blob:','about:'].includes(u.protocol) && ![ui,api].includes(u.origin)) external.push(u.origin); });
  const check = (ok, message) => { if(!ok)throw Error(message); };
  const visit = async path => { await page.goto(ui+path); await page.locator('main h1').waitFor(); await page.waitForFunction(()=>!document.querySelector('[role="status"]')?.textContent?.startsWith('Loading')); };
  const login = async (email,password,role) => {
    await page.goto(ui+'/login'); await page.evaluate(()=>{localStorage.removeItem('token');localStorage.removeItem('user');sessionStorage.clear();}); await page.reload(); await page.getByLabel('Email',{exact:true}).fill(email); await page.getByLabel('Password',{exact:true}).fill(password);
    await page.getByRole('button',{name:'Login',exact:true}).click();
    await page.waitForFunction(expected=>JSON.parse(localStorage.getItem('user')||'null')?.role===expected,role);
    await visit('/dashboard');
    await page.getByText('Weight Progress Chart',{exact:true}).waitFor();
    check(await page.evaluate(()=>JSON.parse(localStorage.getItem('user')).role)===role,`Wrong role after real ${role} login`);
    passed.push(`real ${role} login and dashboard`);
  };
  const logout = async () => { await page.getByRole('button',{name:'Sign Out',exact:true}).click(); await page.waitForURL(ui+'/login'); };
  await page.setViewportSize({width:1440,height:1000});
  page.setDefaultTimeout(7000);
  await login('phase2-member@e2e.test','Fitcore@123','MEMBER');
  await visit('/nutrition/food-logger');
  await page.getByLabel('Food name',{exact:true}).fill('Local verification oats');
  await page.getByLabel('Calories',{exact:true}).fill('320');
  await page.getByRole('button',{name:'Add Food',exact:true}).click();
  await page.getByText('Local verification oats',{exact:true}).first().waitFor();
  await page.reload(); await page.getByText('Local verification oats',{exact:true}).first().waitFor(); passed.push('nutrition write persists through real API and reload');
  await visit('/progress/weight');
  await page.getByLabel('Weight kg',{exact:true}).fill('73.5');
  await page.getByRole('button',{name:'Add Weight',exact:true}).click();
  await page.locator('table').getByText('73.5 kg',{exact:true}).waitFor(); passed.push('progress write appears in real history');
  await visit('/attendance/check-in');
  await page.getByRole('button',{name:'Check-In',exact:true}).click(); await page.getByText('Checked In',{exact:true}).waitFor();
  await page.getByRole('button',{name:'Check-Out',exact:true}).click(); await page.getByText('Ready',{exact:true}).waitFor();
  await visit('/attendance/history'); check(await page.locator('tbody tr').count()===1,'Attendance visit did not persist'); passed.push('attendance check-in/out and persisted history');
  await page.getByLabel('Search pages').fill('Progress'); await page.keyboard.press('ArrowDown'); await page.keyboard.press('Enter');
  await page.waitForURL(ui+'/progress/weight'); passed.push('header search keyboard navigation');
  await visit('/admin/users'); await page.waitForURL(ui+'/dashboard'); passed.push('member admin route remains restricted');
  await logout();
  await login('phase2-alpha@e2e.test','Fitcore@123','TRAINER');
  for(const path of ['/trainers/profiles','/membership/plans','/workouts/library','/nutrition/dashboard','/progress/analytics','/attendance/statistics']) {await visit(path);check(await page.getByRole('alert').count()===0,`Trainer read error ${path}`);}
  await page.screenshot({path:output+'/integrated-trainer-dark.png',fullPage:true}); passed.push('trainer protected module reads use real API');
  await logout();
  await login('phase3-admin@example.invalid','Phase3-Local-Only!Verify26','ADMIN');
  for(const path of ['/admin/dashboard','/admin/users','/admin/trainers','/admin/memberships','/admin/attendance']) { await visit(path);check(await page.getByRole('alert').count()===0,`Admin read error ${path}`); }
  await visit('/admin/users');
  const row=page.locator('tbody tr').filter({hasText:'phase2-other@e2e.test'}); await row.getByRole('button',{name:'Edit',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'Edit User'}); await dialog.getByLabel('Full name',{exact:true}).fill('Phase 3 Other Member');
  await dialog.getByRole('button',{name:'Save User',exact:true}).click(); await page.getByText('User updated',{exact:true}).waitFor();
  await page.reload(); await page.getByText('Phase 3 Other Member',{exact:true}).waitFor(); passed.push('administrator modal edit persists through authorized write API');
  await page.screenshot({path:output+'/integrated-admin-users.png',fullPage:true});
  check(errors.length===0,errors.join(';')); check(external.length===0,`External requests: ${external.join(',')}`);
  return {passed:passed.length,checks:passed,uncaughtErrors:errors.length,externalRequests:external.length};
}
