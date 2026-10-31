/* eslint-disable no-unused-expressions -- Executed by the disposable Playwright CLI harness. */
async (page) => {
  const ui = 'http://127.0.0.1:5175', api = 'http://127.0.0.1:18082';
  const aMember = '22222222-2222-2222-2222-222222222222';
  const bMember = 'a3000000-0000-0000-0000-000000000002';
  const checks = [], errors = [], external = [];
  const inspect = request => { const u = new URL(request.url()); if (!['data:', 'blob:', 'about:'].includes(u.protocol) && ![ui, api].includes(u.origin)) external.push(u.origin); };
  const onError = e => errors.push(e.message);
  page.context().on('request', inspect); page.on('pageerror', onError);
  const check = (ok, message) => { if (!ok) throw Error(message); };
  const login = async (email, password) => {
    await page.goto(ui + '/login');
    await page.evaluate(() => { localStorage.clear(); sessionStorage.clear(); }); await page.reload();
    await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Login', exact: true }).click(); await page.waitForURL(ui + '/dashboard');
    return page.evaluate(() => localStorage.getItem('token'));
  };
  const get = (path, token) => page.request.get(api + '/api' + path, { headers: { Authorization: `Bearer ${token}` } });
  const report = async (token, local, foreign) => {
    const dashboard = await get('/admin/dashboard', token); check(dashboard.status() === 200, 'Dashboard denied');
    const data = await dashboard.json(); check(data.attendanceHistory.length === 1 && data.attendanceHistory[0].userId === local, 'Attendance aggregate crossed gyms');
    const attendance = await get('/admin/attendance', token); check(attendance.status() === 200 && (await attendance.json()).every(row => row.userId === local), 'Attendance endpoint crossed gyms');
    await page.goto(ui + '/admin/attendance'); await page.getByRole('button', { name: 'Generate Reports', exact: true }).waitFor();
    await page.evaluate(() => { const original = URL.createObjectURL; URL.createObjectURL = function(blob) { window.__fitcoreReport = blob; return original.call(URL, blob); }; });
    await page.getByRole('button', { name: 'Generate Reports', exact: true }).click();
    await page.getByText('Attendance report downloaded', { exact: true }).waitFor();
    const csv = await page.evaluate(() => window.__fitcoreReport.text());
    check(csv.includes(local) && !csv.includes(foreign) && csv.split('\r\n').length === 2, 'CSV crossed gyms');
  };
  try {
    let token = await login('phase2-member@e2e.test', 'Fitcore@123');
    check((await get('/users/' + bMember, token)).status() === 404, 'A member read B');
    const plans = await get('/memberships/plans', token); check(plans.status() === 200 && (await plans.json()).every(p => p.id !== 4), 'A catalog leaked B');
    checks.push('A member session and catalog reject B');
    token = await login('phase3-admin@example.invalid', 'Phase3-Local-Only!Verify26');
    await report(token, aMember, bMember); checks.push('A administrator report and real CSV contain A only');
    token = await login('tenant-b-admin@fixture.invalid', 'Phase3-Local-Only!Verify26');
    check((await get('/users/' + aMember, token)).status() === 404, 'B administrator read A');
    await report(token, bMember, aMember); checks.push('B administrator report and real CSV contain B only');
    check(errors.length === 0 && external.length === 0, 'Runtime or external-network error');
    return { passed: checks.length, checks, uncaughtErrors: errors.length, externalRequests: external.length };
  } finally { page.context().off('request', inspect); page.off('pageerror', onError); }
}
