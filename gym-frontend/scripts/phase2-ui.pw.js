/* eslint-disable no-unused-expressions -- The Playwright CLI evaluates this file as a function expression. */
async (page) => {
  // playwright-cli run-code --filename=scripts/phase2-ui.pw.js; no real API requests.
  const origin = 'http://127.0.0.1:5174';
  const plans = [
    { id: 1, name: 'Basic', price: 100, durationMonths: 1, features: ['Gym access'] },
    { id: 2, name: 'Economy', price: 90, durationMonths: 1, features: ['Gym access'] },
    { id: 3, name: 'Equivalent', price: 100, durationMonths: 3, features: ['Gym access'] },
    { id: 4, name: 'Premium', price: 150, durationMonths: 12, features: ['Gym access', 'Classes'] },
  ];
  const current = { id: 41, plan: plans[0], startDate: '2030-01-01', endDate: '2030-02-01', status: 'ACTIVE', remainingDays: 22, serverDate: '2030-01-10', timeZone: 'UTC' };
  const trainers = [{ id: 3, user: { id: 33, fullName: 'Taylor Coach', enabled: true }, specialization: 'Strength', experienceYears: 5, bio: 'Personal training', certifications: ['CPT'], availableSlots: ['09:00-18:00'], photoUrl: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg"/>' }];
  const failures = [];
  const passes = [];
  const check = (condition, message) => { if (!condition) throw new Error(message); };
  const run = async (name, action) => {
    try { await action(); passes.push(name); } catch (error) { failures.push(`${name}: ${error.message}`); }
    finally { await page.clock.resume(); }
  };
  let active = { ...current };
  let history = [current];
  let posts = [];
  let reads = 0;
  let sessionReads = 0;
  let conflict = false;
  let sessionConflict = false;
  await page.clock.install({ time: new Date('2030-01-10T23:30:00Z') });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'local-regression-token');
    localStorage.setItem('user', JSON.stringify({ id: 7, fullName: 'Local Member', role: 'MEMBER' }));
  });
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (request.method() === 'OPTIONS') return route.fulfill({ status: 204 });
    if (request.method() === 'POST') {
      posts.push({ path, body: request.postData() ? request.postDataJSON() : null });
      if (path.includes('/memberships/') && conflict) {
        conflict = false;
        active = { ...current, endDate: '2030-03-01', remainingDays: 50 };
        return route.fulfill({ status: 409, json: { message: 'Membership changed. Reload before trying again.' } });
      }
      if (path.includes('/trainers/') && sessionConflict) {
        sessionConflict = false;
        return route.fulfill({ status: 409, json: { message: 'This trainer time is already booked.' } });
      }
      return route.fulfill({ status: 200, json: { id: 90 } });
    }
    if (path === '/api/memberships/plans') return route.fulfill({ json: plans });
    if (path === '/api/memberships/user/7/active') { reads++; return route.fulfill(active ? { json: active } : { status: 204 }); }
    if (path === '/api/memberships/user/7') return route.fulfill({ json: history });
    if (path === '/api/trainers') return route.fulfill({ json: trainers });
    if (path === '/api/trainers/sessions/7') { sessionReads++; return route.fulfill({ json: [] }); }
    return route.fulfill({ status: 404, json: { message: `Unexpected local fixture request ${path}` } });
  });
  const open = async (path) => {
    await page.goto(`${origin}${path}`);
    await page.getByText(path.startsWith('/membership') ? 'Basic Plan' : 'Session Booking', { exact: true }).waitFor();
  };
  await run('active eligibility uses total package price', async () => {
    await open('/membership/plans');
    check(await page.getByRole('button', { name: 'Purchase Plan', exact: true }).count() === 0, 'purchase offered while active');
    check(await page.getByRole('button', { name: 'Renew Plan', exact: true }).count() === 1, 'renewal offered for other plans');
    check(await page.getByRole('button', { name: 'Upgrade Plan', exact: true }).count() === 1, 'equal/lower package prices can upgrade');
  });
  await run('renew conflict retains UI, reloads expiry, locks duplicate clicks', async () => {
    active = { ...current }; posts = []; conflict = true;
    await open('/membership/plans');
    const beforeReads = reads;
    await page.getByRole('button', { name: 'Renew Plan', exact: true }).first().evaluate((button) => { button.click(); button.click(); });
    await page.getByRole('alert').waitFor({ timeout: 3000 });
    check(posts.length === 1, `expected one action, received ${posts.length}`);
    check(posts[0].path === '/api/memberships/renew/7/41', `wrong renewal endpoint ${posts[0].path}`);
    check(posts[0].body?.expectedEndDate === '2030-02-01', 'renew omitted original expected expiry');
    check(await page.getByText('Basic Plan', { exact: true }).isVisible(), 'conflict removed cards');
    await page.waitForFunction(() => document.querySelector('button[aria-busy="true"]') === null);
    check(reads > beforeReads, 'conflict did not reload membership');
    await page.getByRole('button', { name: 'Renew Plan', exact: true }).first().click();
    await page.getByText('Basic plan renewed', { exact: true }).waitFor({ timeout: 3000 });
    check(posts[1]?.body?.expectedEndDate === '2030-03-01', 'retry used stale expiry');
    // Pause after the real lazy-loaded UI has rendered, while its success is still visible.
    await page.clock.pauseAt(new Date(await page.evaluate(() => Date.now() + 1000)));
    conflict = true;
    await page.getByRole('button', { name: 'Renew Plan', exact: true }).first().click();
    await page.getByRole('alert').waitFor({ timeout: 3000 });
    check(await page.getByText('Basic plan renewed', { exact: true }).count() === 0, 'failed membership action retains the previous success toast');
  });
  await run('upgrade preserves expiry and sends concurrency identity', async () => {
    active = { ...current }; posts = [];
    await open('/membership/plans');
    check((await page.locator('main').innerText()).match(/expiry.*retain|retain.*expiry/i), 'expiry behavior absent');
    check((await page.locator('main').innerText()).match(/no (payment|charge)|not (charge|process)/i), 'payment behavior absent');
    await page.getByRole('button', { name: 'Upgrade Plan', exact: true }).last().click();
    await page.getByText('Premium plan upgraded', { exact: true }).waitFor({ timeout: 3000 });
    check(posts[0]?.path === '/api/memberships/upgrade/7/4', 'upgrade used wrong endpoint');
    check(posts[0]?.body?.membershipId === 41 && posts[0]?.body?.expectedEndDate === '2030-02-01', 'upgrade omitted concurrency identity');
  });
  await run('a member without history can purchase through the new-purchase endpoint', async () => {
    active = null; history = []; posts = [];
    await open('/membership/plans');
    check(await page.getByRole('button', { name: 'Renew Plan', exact: true }).count() === 0, 'renew offered without history');
    check(await page.getByRole('button', { name: 'Upgrade Plan', exact: true }).count() === 0, 'upgrade offered without active');
    await page.getByRole('button', { name: 'Purchase Plan', exact: true }).first().click();
    await page.getByText('Basic plan purchased', { exact: true }).waitFor({ timeout: 3000 });
    check(posts[0]?.path === '/api/memberships/subscribe/7/1', 'purchase used wrong endpoint');
  });
  await run('a future entitlement blocks new purchases and expired renewal', async () => {
    active = null;
    history = [{ ...current, startDate: '2030-01-11', endDate: '2030-02-11', effectiveStatus: 'UPCOMING' }, { ...current, id: 20, endDate: '2030-01-01', status: 'EXPIRED' }];
    await open('/membership/plans');
    check(await page.getByRole('button', { name: 'Purchase Plan', exact: true }).count() === 0, 'future entitlement allows new purchase');
    check(await page.getByRole('button', { name: 'Renew Plan', exact: true }).count() === 0, 'future entitlement allows expired renewal');
    check((await page.locator('main').innerText()).includes('upcoming membership'), 'upcoming entitlement is unexplained');
  });
  await run('only latest expired plan renews using history ID', async () => {
    active = null;
    history = [
      { ...current, id: 15, plan: plans[3], startDate: '2029-12-01', endDate: '2030-01-08', status: 'EXPIRED' },
      { ...current, id: 10, startDate: '2029-11-01', endDate: '2030-01-09', status: 'EXPIRED' },
      { ...current, id: 16, endDate: '2030-01-09', status: 'CANCELLED' },
    ];
    posts = [];
    await open('/membership/plans');
    check(await page.getByRole('button', { name: 'Purchase Plan', exact: true }).count() === 4, 'purchase unavailable without active');
    check(await page.getByRole('button', { name: 'Renew Plan', exact: true }).count() === 1, 'ambiguous expired renew target');
    await Promise.all([
      page.waitForResponse((response) => response.url().includes('/memberships/renew/7/')),
      page.getByRole('button', { name: 'Renew Plan', exact: true }).click(),
    ]);
    check(posts[0]?.path === '/api/memberships/renew/7/15', 'expired renew used wrong row');
    check(posts[0]?.body?.expectedEndDate === '2030-01-08', 'expired renew omitted expected expiry');
    await page.getByText('Premium plan renewed', { exact: true }).waitFor({ timeout: 3000 });
  });
  await run('booking UTC defaults, conflict values, success refresh, duplicate lock', async () => {
    posts = []; sessionConflict = true;
    await open('/trainers/booking');
    check((await page.locator('main').innerText()).includes('UTC'), 'timezone missing');
    check(await page.getByLabel('Select Date').inputValue() === '2030-01-10', 'default not UTC today');
    check(await page.getByLabel('Select Date').getAttribute('min') === '2030-01-10', 'minimum not UTC today');
    await page.getByLabel('Select Date').fill('2030-01-11');
    await page.getByLabel('Select Time').fill('10:00');
    await page.getByRole('button', { name: 'Book Session', exact: true }).click();
    await page.getByRole('alert').waitFor({ timeout: 3000 });
    check(await page.getByLabel('Select Date').inputValue() === '2030-01-11', 'conflict removed date');
    check(await page.getByLabel('Select Time').inputValue() === '10:00', 'conflict removed time');
    check(posts[0]?.body?.startTime === '10:00' && posts[0]?.body?.endTime === '11:00', 'wrong interval');
    const beforeReads = sessionReads;
    await page.locator('form').evaluate((form) => { form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
    await page.getByText('Session booked with Taylor Coach', { exact: true }).waitFor({ timeout: 3000 });
    check(posts.length === 2, `double submit created ${posts.length - 1} requests`);
    check(sessionReads > beforeReads, 'sessions not refreshed');
    await page.clock.pauseAt(new Date(await page.evaluate(() => Date.now() + 1000)));
    sessionConflict = true;
    await page.getByRole('button', { name: 'Book Session', exact: true }).click();
    await page.getByRole('alert').waitFor({ timeout: 3000 });
    check(await page.getByText('Session booked with Taylor Coach', { exact: true }).count() === 0, 'failed booking retains the previous success toast');
  });
  await run('past UTC starts and overnight rollover never post', async () => {
    await open('/trainers/booking'); posts = [];
    await page.getByLabel('Select Date').fill('2030-01-10');
    await page.getByLabel('Select Time').fill('10:00');
    await page.locator('form').evaluate((form) => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    await page.getByRole('alert').waitFor({ timeout: 3000 });
    check(posts.length === 0, 'past start reached API');
    await page.getByLabel('Select Date').fill('2030-01-11');
    await page.getByLabel('Select Time').fill('23:30');
    await page.locator('form').evaluate((form) => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    check(posts.length === 0, 'overnight interval reached API');
    check((await page.getByRole('alert').innerText()).match(/same day|overnight/i), 'overnight validation absent');
  });
  if (failures.length) throw new Error(`Phase 2 UI failures (${failures.length}):\n${failures.join('\n')}\nPassed: ${passes.join('; ')}`);
  return `Passed ${passes.length} Phase 2 UI regressions: ${passes.join('; ')}`;
}
