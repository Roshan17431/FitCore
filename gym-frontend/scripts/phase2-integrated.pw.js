/* eslint-disable no-unused-expressions -- The Playwright CLI evaluates this file as a function expression. */
async (page) => {
  // Destructive to DISPOSABLE LOCAL fixtures only. Reset fixtures before rerunning.
  // Start only after the parent confirms backend :18082 and Vite :5175 are ready.
  // From gym-frontend: playwright-cli -s=fitcore-phase2-integrated open http://127.0.0.1:5175/login
  // Then: playwright-cli -s=fitcore-phase2-integrated run-code --filename=scripts/phase2-integrated.pw.js
  // No route interception, API mocks, stored auth artifacts, or external services are used.
  const uiOrigin = 'http://127.0.0.1:5175';
  const apiOrigin = 'http://127.0.0.1:18082';
  const memberId = '22222222-2222-2222-2222-222222222222';
  const output = '/Users/roshan/Desktop/FitCore/output/playwright/phase2-2026-10-20';
  const context = page.context();
  const checks = [];
  const screenshots = [];
  const unexpectedRequests = [];
  const pageErrors = [];
  let secondTab;
  let stage = 'verify local configuration';
  const check = (condition, message) => { if (!condition) throw new Error(`${stage}: ${message}`); };
  const isLoopback = (value) => {
    const url = new URL(value);
    return ['http:', 'ws:'].includes(url.protocol) && url.hostname === '127.0.0.1'
      && ['5175', '18082'].includes(url.port);
  };
  const inspectRequest = (request) => {
    const url = new URL(request.url());
    if (!['data:', 'blob:', 'about:'].includes(url.protocol) && !isLoopback(url.href)) {
      unexpectedRequests.push(`${url.origin}${url.pathname}`);
    }
  };
  const inspectPage = (view) => view.on('pageerror', (error) => pageErrors.push(error.message));
  const checkNetwork = () => {
    check(unexpectedRequests.length === 0, `non-loopback request detected: ${unexpectedRequests.join(', ')}`);
    check(pageErrors.length === 0, `browser runtime error: ${pageErrors.join('; ')}`);
  };
  const save = async (view, name) => {
    const path = `${output}/${name}.png`;
    await view.screenshot({ path, fullPage: true });
    screenshots.push(path);
  };
  const displayDate = (value) => new Intl.DateTimeFormat('en-US', {
    month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`));
  // Independent calendar expectation matching LocalDate.plusMonths end-of-month clamping.
  const plusMonths = (value, months) => {
    const [year, month, day] = value.split('-').map(Number);
    const lastDay = new Date(Date.UTC(year, month - 1 + months + 1, 0)).getUTCDate();
    return new Date(Date.UTC(year, month - 1 + months, Math.min(day, lastDay))).toISOString().slice(0, 10);
  };
  const matches = (method, path) => (response) => response.request().method() === method
    && new URL(response.url()).origin === apiOrigin && new URL(response.url()).pathname === `/api${path}`;
  const action = async (view, path, button, expectedStatus = 200) => {
    const [response] = await Promise.all([view.waitForResponse(matches('POST', path)), button.click()]);
    check(response.status() === expectedStatus, `${path} returned ${response.status()}, expected ${expectedStatus}`);
    checkNetwork();
    return response;
  };
  const planCard = (view, name) => view.locator('section').filter({
    has: view.getByRole('heading', { name: `${name} Plan`, exact: true }),
  });
  const plansView = async (view) => {
    await view.goto(`${uiOrigin}/membership/plans`);
    await view.getByRole('heading', { name: 'Basic Plan', exact: true }).waitFor();
  };
  const activeView = async (view, current, navigate = true) => {
    if (navigate) await view.goto(`${uiOrigin}/membership/active`);
    await view.getByRole('heading', { name: `${current.plan.name} Plan`, exact: true }).waitFor();
    await view.getByText(displayDate(current.endDate), { exact: true }).waitFor();
    check(await view.getByText('Remaining Days', { exact: true }).locator('..').locator('p').last().innerText() === String(current.remainingDays), 'UI remaining days differ from the server result');
    check((await view.locator('main').innerText()).includes('UTC'), 'membership timezone missing');
  };
  const historyView = async (view, expectedCount) => {
    await view.goto(`${uiOrigin}/membership/history`);
    await view.getByRole('table').waitFor();
    check(await view.locator('tbody tr').count() === expectedCount, `membership UI history row count differs from ${expectedCount}`);
  };

  try {
    // Verify the served Vite env before any UI authentication request can execute.
    const servedSource = await page.request.get(`${uiOrigin}/src/services/api.js`, { maxRedirects: 0 });
    check(servedSource.status() === 200, `local Vite source preflight returned ${servedSource.status()}`);
    const source = await servedSource.text();
    const configured = source.match(/"VITE_API_BASE_URL"\s*:\s*"([^"]+)"/);
    check(Boolean(configured), 'cannot verify Vite API origin; expected unbundled local dev server');
    const configuredUrl = new URL(configured[1]);
    check(configuredUrl.origin === apiOrigin && ['/', '/api', '/api/'].includes(configuredUrl.pathname), 'Vite API origin is not the disposable loopback backend');
    context.on('request', inspectRequest);
    inspectPage(page);
    await page.setViewportSize({ width: 1440, height: 1000 });

    stage = 'real browser login';
    await page.goto(`${uiOrigin}/login`);
    await page.evaluate(() => {
      localStorage.removeItem('token'); localStorage.removeItem('user');
      sessionStorage.removeItem('token'); sessionStorage.removeItem('user');
    });
    await page.reload();
    await page.getByLabel('Email', { exact: true }).fill('phase2-member@e2e.test');
    // Public password for the disposable local fixture, never an actual account credential.
    await page.getByLabel('Password', { exact: true }).fill('Fitcore@123');
    await action(page, '/auth/login', page.getByRole('button', { name: 'Login', exact: true }));
    await page.waitForURL(`${uiOrigin}/dashboard`);
    const stored = await page.evaluate(() => ({
      token: localStorage.getItem('token') || sessionStorage.getItem('token'),
      user: JSON.parse(localStorage.getItem('user') || sessionStorage.getItem('user') || 'null'),
    }));
    check(Boolean(stored.token) && stored.user?.id === memberId && stored.user?.role === 'MEMBER', 'login did not persist the intended fixture member');
    // Token stays inside this closure; it is never logged, returned, or saved to a file.
    const headers = { Authorization: `Bearer ${stored.token}` };
    const get = async (path, allowEmpty = false) => {
      check(path.startsWith('/') && !path.includes('://'), 'API read must use a relative local path');
      const response = await page.request.get(`${apiOrigin}/api${path}`, { headers, maxRedirects: 0 });
      if (allowEmpty && response.status() === 204) return null;
      check(response.status() === 200, `API read ${path} returned ${response.status()}`);
      return response.json();
    };
    const current = () => get(`/memberships/user/${memberId}/active`, true);
    const memberships = () => get(`/memberships/user/${memberId}`);
    const sessions = () => get(`/trainers/sessions/${memberId}`);
    const initial = await Promise.all([current(), memberships(), sessions(), get('/memberships/plans'), get('/trainers')]);
    check(initial[0] === null && initial[1].length === 0 && initial[2].length === 0, 'fixtures are not clean; reset the disposable member before rerunning');
    for (const expected of [{ id: 1, name: 'Basic', price: 29, months: 1 }, { id: 2, name: 'Premium', price: 79, months: 3 }, { id: 3, name: 'Elite', price: 149, months: 6 }]) {
      const plan = initial[3].find((item) => item.id === expected.id);
      check(plan?.name === expected.name && Number(plan.price) === expected.price && plan.durationMonths === expected.months, `plan fixture ${expected.id} differs from the agreed package`);
    }
    const alpha = initial[4].find((trainer) => trainer.id === 1);
    const beta = initial[4].find((trainer) => trainer.id === 2);
    check(alpha && beta && !alpha.availableSlots.length && !beta.availableSlots.length, 'enabled unrestricted trainer fixtures are missing');
    for (const trainer of [alpha, beta]) {
      check(trainer.user?.enabled !== false, 'trainer fixture is disabled');
      check(!trainer.photoUrl || trainer.photoUrl.startsWith('data:') || isLoopback(new URL(trainer.photoUrl, uiOrigin).href), 'trainer fixture would request an external photo');
    }
    checks.push('real login and clean local fixture preflight');

    stage = 'new Basic purchase';
    await plansView(page);
    check(await page.getByRole('button', { name: 'Purchase Plan', exact: true }).count() === 3, 'new member cannot purchase the three plans');
    const purchasedResponse = await action(page, `/memberships/subscribe/${memberId}/1`, planCard(page, 'Basic').getByRole('button', { name: 'Purchase Plan', exact: true }));
    const purchased = await purchasedResponse.json();
    check(purchased.plan.id === 1 && purchased.userId === memberId && purchased.status === 'ACTIVE', 'purchase returned the wrong entitlement');
    check(purchased.endDate === plusMonths(purchased.serverDate, 1) && purchased.startDate === purchased.serverDate, 'purchase dates do not match one UTC calendar month');
    check(purchased.timeZone === 'UTC' && purchased.remainingDays === (Date.parse(purchased.endDate) - Date.parse(purchased.serverDate)) / 86400000, 'server UTC date/day contract differs');
    await page.getByText('Basic plan purchased', { exact: true }).waitFor();
    check((await memberships()).length === 1, 'purchase created duplicate periods');
    await activeView(page, purchased);
    await page.reload();
    await activeView(page, purchased, false);
    await save(page, 'integrated-01-purchased-active');
    await historyView(page, 1);
    check((await page.locator('tbody').innerText()).includes('Basic'), 'purchase absent from UI history');
    checks.push('purchase, active reload, UTC days, and persisted history');

    stage = 'active Basic renewal';
    await activeView(page, purchased);
    const renewedResponse = await action(page, `/memberships/renew/${memberId}/${purchased.id}`, page.getByRole('button', { name: 'Renew Plan', exact: true }));
    check(renewedResponse.request().postDataJSON()?.expectedEndDate === purchased.endDate, 'UI renewal did not send the original expected expiry');
    const renewed = await renewedResponse.json();
    check(renewed.id === purchased.id && renewed.plan.id === 1 && renewed.startDate === purchased.startDate, 'active renewal replaced its history row');
    check(renewed.endDate === plusMonths(purchased.endDate, 1), 'active renewal did not extend exactly one calendar month');
    await page.getByText('Basic plan renewed', { exact: true }).waitFor();
    check((await memberships()).length === 1 && (await current()).endDate === renewed.endDate, 'renewal duplicated a period or did not persist');
    await activeView(page, renewed);
    await save(page, 'integrated-02-renewed-active');
    checks.push('active renewal extends the same stored period');

    stage = 'Premium upgrade';
    await plansView(page);
    check(await page.getByRole('button', { name: 'Purchase Plan', exact: true }).count() === 0, 'active membership still offers new purchase');
    const upgradedResponse = await action(page, `/memberships/upgrade/${memberId}/2`, planCard(page, 'Premium').getByRole('button', { name: 'Upgrade Plan', exact: true }));
    const upgradeBody = upgradedResponse.request().postDataJSON();
    check(upgradeBody.membershipId === renewed.id && upgradeBody.expectedEndDate === renewed.endDate, 'UI upgrade omitted the concurrency identity');
    const upgraded = await upgradedResponse.json();
    check(upgraded.id !== renewed.id && upgraded.plan.id === 2 && upgraded.endDate === renewed.endDate, 'upgrade did not retain expiry in a new Premium period');
    await page.getByText('Premium plan upgraded', { exact: true }).waitFor();
    const afterUpgrade = await memberships();
    const oldBasic = afterUpgrade.find((item) => item.id === renewed.id);
    check(afterUpgrade.length === 2 && oldBasic?.status === 'CANCELLED' && oldBasic.endDate === renewed.endDate, 'upgrade lost old history or created duplicate periods');
    check(afterUpgrade.filter((item) => item.status === 'ACTIVE').length === 1, 'upgrade left multiple active rows');
    await historyView(page, 2);
    const basicRow = page.getByRole('row').filter({ hasText: 'Basic' });
    check((await basicRow.innerText()).includes('Cancelled'), 'UI history does not display cancelled Basic period');
    await save(page, 'integrated-03-upgrade-history');
    checks.push('upgrade retains renewed expiry and preserves cancelled Basic history');

    stage = 'cross-tab stale renewal race';
    await plansView(page);
    secondTab = await context.newPage();
    inspectPage(secondTab);
    await plansView(secondTab);
    const renewPath = `/memberships/renew/${memberId}/${upgraded.id}`;
    // Both tabs have loaded the same expected expiry before either action starts.
    const responseA = page.waitForResponse(matches('POST', renewPath));
    const responseB = secondTab.waitForResponse(matches('POST', renewPath));
    const refreshA = page.waitForResponse(matches('GET', `/memberships/user/${memberId}/active`));
    const refreshB = secondTab.waitForResponse(matches('GET', `/memberships/user/${memberId}/active`));
    await Promise.all([
      page.getByRole('button', { name: 'Renew Plan', exact: true }).click(),
      secondTab.getByRole('button', { name: 'Renew Plan', exact: true }).click(),
    ]);
    const race = await Promise.all([responseA, responseB]);
    check(race.map((response) => response.status()).sort((left, right) => left - right).join(',') === '200,409', 'same-state renewals did not produce exactly one success and one conflict');
    for (const response of race) check(response.request().postDataJSON()?.expectedEndDate === upgraded.endDate, 'tabs did not submit the same expected expiry');
    const refreshed = await Promise.all([refreshA, refreshB]);
    const onceRenewedEnd = plusMonths(upgraded.endDate, 3);
    for (const response of refreshed) check(response.status() === 200 && (await response.json()).endDate === onceRenewedEnd, 'a tab did not reload the post-conflict current expiry');
    const losingTab = race[0].status() === 409 ? page : secondTab;
    await losingTab.getByRole('alert').waitFor();
    check((await losingTab.getByRole('alert').innerText()).includes('409'), 'stale-state inline error is missing');
    check(await losingTab.getByRole('heading', { name: 'Premium Plan', exact: true }).isVisible(), 'conflict removed the membership cards');
    const afterRace = await memberships();
    const finalCurrent = await current();
    check(afterRace.length === 2 && finalCurrent.id === upgraded.id && finalCurrent.endDate === onceRenewedEnd, 'stale requests duplicated a period or extended expiry more than once');
    check(afterRace.filter((item) => item.status === 'ACTIVE').length === 1, 'race left duplicate active entitlements');
    await save(losingTab, 'integrated-04-membership-conflict-retained');
    await losingTab.getByRole('link', { name: 'Active', exact: true }).click();
    await activeView(losingTab, finalCurrent, false);
    await save(losingTab, 'integrated-05-post-conflict-active');
    await secondTab.close(); secondTab = undefined;
    checks.push('real cross-tab same-state renewal produces one 200/one 409 and one extension');

    stage = 'Alpha trainer booking';
    const tomorrow = new Date(`${finalCurrent.serverDate}T00:00:00Z`);
    tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
    const bookingDate = tomorrow.toISOString().slice(0, 10);
    await page.goto(`${uiOrigin}/trainers/booking`);
    await page.getByRole('heading', { name: 'Session Booking', exact: true }).waitFor();
    // Freeze only browser timers; real backend requests and UTC session validation remain live.
    // This proves a failed immediate retry clears feedback before the 2200ms toast timeout.
    await page.clock.install({ time: new Date() });
    await page.clock.pauseAt(new Date(Date.now() + 1000));
    check((await page.locator('main').innerText()).includes('UTC'), 'booking UTC convention missing');
    await page.getByLabel('Select Trainer').selectOption('1');
    await page.getByLabel('Select Date').fill(bookingDate);
    await page.getByLabel('Select Time').fill('10:00');
    const book = async (trainerId, status) => {
      const refresh = page.waitForResponse(matches('GET', `/trainers/sessions/${memberId}`));
      const response = await action(page, `/trainers/book/${memberId}/${trainerId}`, page.getByRole('button', { name: 'Book Session', exact: true }), status);
      const request = response.request().postDataJSON();
      check(request.sessionDate === bookingDate && request.startTime === (trainerId === 2 && status === 200 ? '11:00' : '10:00'), 'UI sent the wrong booking date/start');
      check(request.endTime === (trainerId === 2 && status === 200 ? '12:00' : '11:00'), 'UI sent the wrong one-hour end');
      const refreshedSessions = await refresh;
      check(refreshedSessions.status() === 200, 'booking action failed to refresh the real session list');
      if (status === 409) {
        await page.getByRole('alert').waitFor();
        check((await page.getByRole('alert').innerText()).includes('409'), 'booking conflict is not displayed inline');
        check(await page.getByLabel('Select Trainer').inputValue() === String(trainerId)
          && await page.getByLabel('Select Date').inputValue() === bookingDate
          && await page.getByLabel('Select Time').inputValue() === '10:00', 'booking conflict lost form selections');
        check(await page.getByText(/Session booked with/).count() === 0, 'failed booking retains the previous success toast');
      }
      return response;
    };
    await book(1, 200);
    await page.getByText(`Session booked with ${alpha.user.fullName}`, { exact: true }).waitFor();
    let storedSessions = await sessions();
    check(storedSessions.length === 1 && storedSessions[0].trainer.id === 1
      && storedSessions[0].sessionDate === bookingDate && storedSessions[0].startTime.slice(0, 5) === '10:00'
      && storedSessions[0].endTime.slice(0, 5) === '11:00', 'Alpha booking did not persist the expected session');
    checks.push('real Alpha booking persists tomorrow UTC 10:00–11:00 and refreshes sessions');

    stage = 'repeat and cross-trainer member conflicts';
    check(await page.getByText(`Session booked with ${alpha.user.fullName}`, { exact: true }).isVisible(), 'stale-feedback regression requires a visible preceding success toast');
    await book(1, 409);
    await page.clock.resume();
    check((await sessions()).length === 1, 'repeat booking created a duplicate session');
    await save(page, 'integrated-06-booking-conflict-retained');
    await page.getByLabel('Select Trainer').selectOption('2');
    await book(2, 409);
    check((await sessions()).length === 1, 'member overlap across two trainers was accepted');
    checks.push('same-trainer duplicate and different-trainer member overlap both reject 409 without losing form');

    stage = 'adjacent Beta session and persisted history';
    await page.getByLabel('Select Time').fill('11:00');
    await book(2, 200);
    await page.getByText(`Session booked with ${beta.user.fullName}`, { exact: true }).waitFor();
    storedSessions = await sessions();
    check(storedSessions.length === 2, 'adjacent booking did not leave exactly two sessions');
    check(storedSessions.some((session) => session.trainer.id === 2 && session.sessionDate === bookingDate
      && session.startTime.slice(0, 5) === '11:00' && session.endTime.slice(0, 5) === '12:00'), 'adjacent Beta 11:00–12:00 session was not persisted');
    await page.getByRole('link', { name: 'History', exact: true }).click();
    await page.getByRole('table').waitFor();
    check(await page.locator('tbody tr').count() === 2, 'session history UI does not contain both records');
    for (const name of [alpha.user.fullName, beta.user.fullName]) check((await page.locator('tbody').innerText()).includes(name), `${name} absent from UI session history`);
    check((await page.locator('thead').innerText()).includes('UTC'), 'history does not identify UTC');
    await page.reload();
    await page.getByRole('table').waitFor();
    check(await page.locator('tbody tr').count() === 2, 'session history did not survive reload');
    await save(page, 'integrated-07-booking-history');
    checks.push('adjacent Beta session succeeds and both sessions survive history reload');
    checkNetwork();
    return { passed: checks.length, checks, membershipHistoryCount: afterRace.length,
      activeMembershipCount: 1, trainerSessionCount: storedSessions.length,
      renewalRaceStatuses: race.map((response) => response.status()).sort((left, right) => left - right),
      externalRequestCount: unexpectedRequests.length, screenshots };
  } catch (error) {
    await save(page, 'integrated-failure').catch(() => {});
    if (secondTab) await save(secondTab, 'integrated-failure-second-tab').catch(() => {});
    throw new Error(`${stage}: ${error.message}`);
  } finally {
    if (secondTab) await secondTab.close();
    context.off('request', inspectRequest);
  }
}
