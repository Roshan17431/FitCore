/* eslint-disable no-unused-expressions -- Playwright-CLI run-code evaluates this function expression. */
async (page) => {
  // Owned disposable HTTPS harness only. Run AFTER browser-spike.pw.js on the same DB.
  // No mocks, bearer helpers, stored JWTs, raw business writes, or fixture resets.
  const ui = 'https://127.0.0.1:18444';
  const password = 'Browser-Spike!2026';
  const stamp = Date.now().toString(36);
  const checks = [], failures = [], contexts = [], violations = [], errors = [], managers = [];
  const expected = [], recent = [];
  let activePage;
  const focus = async view => { activePage=view; await view.bringToFront(); };
  const visit = async (view,url) => { await focus(view); return view.goto(url); };
  const reload = async view => { await focus(view); return view.reload(); };
  const assert = (ok, message) => { if (!ok) throw new Error(message); };
  const button = (view, name) => view.getByRole('button', { name, exact: true });
  const heading = (view, name) => view.getByRole('heading', { name, exact: true });
  const field = (view, name) => view.getByLabel(name, { exact: true });
  const card = (view, name) => view.locator('section').filter({ has: heading(view, name) });
  const dialog = view => view.getByRole('dialog');
  const dateLabel = value => new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`));
  const plusMonths = (value, months) => {
    const [year, month, day] = value.split('-').map(Number);
    const last = new Date(Date.UTC(year, month + months, 0)).getUTCDate();
    return new Date(Date.UTC(year, month - 1 + months, Math.min(day, last))).toISOString().slice(0, 10);
  };
  const step = async (id, work) => {
    try { await work(); checks.push(id); }
    catch (error) { failures.push({ id, message: String(error.message || error).slice(0, 1400), url:activePage?.url(), body:activePage?(await activePage.locator('body').innerText().catch(()=>'<unavailable>')).replace(/[A-Za-z0-9_-]{32,}/g,'[redacted]').slice(0,1800):'', recent:recent.slice(-16), pageerrors:errors.slice(-8) }); throw error; }
  };
  const responseFor = (view, method, path) => view.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === method && url.origin === ui && (path instanceof RegExp ? path.test(url.pathname) : url.pathname === `/api${path}`);
  });
  const action = async (view, method, path, control, status = 200) => {
    await focus(view);
    const [response] = await Promise.all([responseFor(view, method, path), control.click()]);
    assert(response.status() === status, `${method} business action returned ${response.status()}, expected ${status}`);
    await response.finished();
    return response;
  };
  const freshLogin = async (email, owner = false) => {
    const context = await page.context().browser().newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
    contexts.push(context);
    context.on('request', request => {
      const url = new URL(request.url());
      if (!['about:', 'data:', 'blob:'].includes(url.protocol) && url.origin !== ui) violations.push(`external request ${url.origin}${url.pathname}`);
      if (request.headers().authorization) violations.push('Authorization header used');
      if (url.pathname === '/api/auth/login') violations.push('legacy login endpoint used');
    });
    context.on('response',response=>{const url=new URL(response.url());if(url.pathname.startsWith('/api/')){recent.push({path:url.pathname,status:response.status()});if(recent.length>40)recent.shift();}});
    const view = await context.newPage();
    view.on('pageerror', error => errors.push(error.message));
    view.setDefaultTimeout(12000);
    await visit(view,`${ui}/login`);
    await view.getByText('Secure browser session', { exact: true }).waitFor();
    await field(view, 'Email').fill(email);
    await field(view, 'Password').fill(password);
    await action(view, 'POST', '/browser/login', button(view, 'Login'));
    await view.waitForURL(`${ui}/${owner ? 'owner' : 'dashboard'}`);
    await button(view, 'Sign Out').waitFor();
    // Only inspect storage for forbidden credentials; never return values or cookie handles.
    assert(await view.evaluate(() => [localStorage, sessionStorage].every(storage =>
      Object.entries(storage).every(([key, value]) => !['token', 'user'].includes(key) && !/^eyJ[^.]+\.[^.]+\./.test(value)))), 'credential or legacy identity persisted in Web Storage');
    const cookies = await context.cookies(ui);
    assert(cookies.some(cookie => cookie.name === '__Host-fitcore_session' && cookie.httpOnly && cookie.secure), 'secure HttpOnly browser session missing');
    return view;
  };
  const catalog = async (view, tab = 'Exercises') => {
    await visit(view,`${ui}/gym/catalogs`);
    await heading(view, 'Gym Catalogs').waitFor();
    await button(view, tab).click();
  };
  const saveCatalog = async (view, kind, create = false) => {
    await action(view, create ? 'POST' : 'PUT', new RegExp(`^/api/admin/catalog/${kind}${create ? '$' : '/[0-9]+$'}`), button(dialog(view), `Save ${kind === 'exercises' ? 'exercise' : kind === 'workouts' ? 'workout' : 'nutrition plan'}`), create ? 201 : 200);
    await dialog(view).waitFor({ state: 'hidden' });
  };
  const archive = async (view, name, kind) => {
    await button(view, `Archive ${name}`).click();
    await action(view, 'POST', new RegExp(`^/api/admin/catalog/${kind}/[0-9]+/archive$`), button(dialog(view), 'Confirm archive'));
    await dialog(view).waitFor({ state: 'hidden' });
    await card(view, name).getByText('Archived', { exact: true }).waitFor();
  };
  const plan = async (view, name, price, edit = false) => {
    if (edit) await button(view.getByRole('row').filter({ has: view.getByRole('cell', { name, exact: true }) }), 'Edit').click();
    await field(view, 'Plan name').fill(name);
    await field(view, 'Price').fill(String(price));
    await field(view, 'Duration months').fill('1');
    await field(view, 'Features').fill(`Synthetic cookie UI ${stamp}`);
    await action(view, edit ? 'PUT' : 'POST', new RegExp(`^/api/admin/membership-plans${edit ? '/[0-9]+$' : '$'}`), button(view, edit ? 'Save Plan' : 'Create Plan'), edit ? 200 : 201);
    await view.getByRole('cell', { name, exact: true }).waitFor();
  };
  const book = async (view, trainerId, date, time, status = 200) => {
    await visit(view,`${ui}/trainers/booking`);
    await field(view, 'Select Trainer').selectOption(String(trainerId));
    await field(view, 'Select Date').fill(date);
    await field(view, 'Select Time').fill(time);
    return action(view, 'POST', /^\/api\/trainers\/book\/[^/]+\/[0-9]+$/, button(view, 'Book Session'), status);
  };
  const gymCases = [
    'owner-cookie-login', 'owner-plan-create', 'owner-exercise-create', 'owner-workout-draft',
    'admin-cookie-login', 'admin-plan-update', 'admin-exercise-update', 'admin-workout-update-publish',
    'referenced-exercise-name-protection', 'published-exercise-archive-protection', 'nutrition-catalog-create-update',
    'member-cookie-login', 'member-local-catalogs', 'member-subscribe', 'member-renew', 'member-upgrade',
    'member-workout-log', 'member-nutrition-log', 'member-progress-weight-bmi', 'member-attendance',
    'member-booking', 'member-booking-conflict', 'peer-cookie-login', 'peer-booking-conflict',
    'member-manager-route-denial', 'trainer-current-policy', 'admin-current-policy', 'catalog-archive',
  ];
  for (const gym of [1, 2]) for (const id of gymCases) expected.push(`gym-${gym}.${id}`);
  expected.push('cookie-ui.cross-gym-persisted-catalog-isolation', 'cookie-ui.same-origin-no-bearer-no-runtime-errors');

  try {
    assert(new URL(page.url()).origin === ui, 'Open the owned HTTPS :18444 harness before running this suite');
    for (const gym of [1, 2]) {
      const id = name => `gym-${gym}.${name}`;
      const prefix = `CookieUI G${gym} ${stamp}`;
      const basic = `${prefix} Basic`, premium = `${prefix} Premium`;
      const exercise = `${prefix} Press`, workout = `${prefix} Foundation`, nutrition = `${prefix} Nutrition`;
      const otherPrefix = `CookieUI G${gym === 1 ? 2 : 1} ${stamp}`;
      let owner, admin, member, peer, trainer, purchased, renewed;
      // Failed prerequisites are failures below, never fabricated passes or silently skipped tests.
      try {
        await step(id('owner-cookie-login'), async () => { owner = await freshLogin(`owner${gym}@fixture.invalid`, true); managers.push({ view: owner, gym, prefix }); });
        await step(id('owner-plan-create'), async () => {
          await visit(owner,`${ui}/admin/memberships`);
          await plan(owner, basic, 39); await plan(owner, premium, 99);
          await reload(owner); await owner.getByRole('cell', { name: basic, exact: true }).waitFor();
          assert(!(await owner.locator('main').innerText()).includes(otherPrefix), 'foreign plan visible to owner');
        });
        await step(id('owner-exercise-create'), async () => {
          await catalog(owner); await button(owner, 'Create exercise').click();
          await field(dialog(owner), 'Exercise name').fill(exercise);
          await field(dialog(owner), 'Exercise category').fill('Cookie UI Strength');
          await field(dialog(owner), 'Recommended sets').fill('3');
          await field(dialog(owner), 'Recommended reps').fill('8');
          await field(dialog(owner), 'Description').fill('Synthetic technique guide');
          await saveCatalog(owner, 'exercises', true); await heading(owner, exercise).waitFor();
        });
        await step(id('owner-workout-draft'), async () => {
          await catalog(owner, 'Workouts'); await button(owner, 'Create workout').click();
          await field(dialog(owner), 'Workout level or name').fill(workout);
          await field(dialog(owner), 'Duration').fill('4 weeks');
          await field(dialog(owner), 'Schedule').fill('Monday synthetic strength');
          assert(await field(dialog(owner), 'Workout status').inputValue() === 'DRAFT', 'new workout is not draft');
          await dialog(owner).getByRole('checkbox', { name: exercise, exact: true }).check();
          await saveCatalog(owner, 'workouts', true);
          await visit(owner,`${ui}/workouts/plans`); await heading(owner, 'Workout Management').waitFor();
          assert(await heading(owner, workout).count() === 0, 'draft workout publicly visible');
        });
        await step(id('admin-cookie-login'), async () => { admin = await freshLogin(`admin${gym}@fixture.invalid`); });
        await step(id('admin-plan-update'), async () => {
          await visit(admin,`${ui}/admin/memberships`); await plan(admin, basic, 49, true);
          await reload(admin);
          await admin.getByRole('row').filter({ has: admin.getByRole('cell', { name: basic, exact: true }) }).getByRole('cell', { name: '$49', exact: true }).waitFor();
        });
        await step(id('admin-exercise-update'), async () => {
          await catalog(admin); await button(admin, `Edit ${exercise}`).click();
          await field(dialog(admin), 'Description').fill('Updated synthetic technique guide');
          await saveCatalog(admin, 'exercises'); await card(admin, exercise).getByText('Updated synthetic technique guide', { exact: true }).waitFor();
        });
        await step(id('admin-workout-update-publish'), async () => {
          await catalog(admin, 'Workouts'); await button(admin, `Edit ${workout}`).click();
          await field(dialog(admin), 'Duration').fill('5 weeks');
          await dialog(admin).getByText(exercise, { exact: true }).first().waitFor();
          await saveCatalog(admin, 'workouts');
          await action(admin, 'POST', /^\/api\/admin\/catalog\/workouts\/[0-9]+\/publish$/, button(admin, `Publish ${workout}`));
          await card(admin, workout).getByText('PUBLISHED', { exact: true }).waitFor();
          await visit(admin,`${ui}/workouts/plans`); await card(admin, workout).getByText('5 weeks', { exact: true }).waitFor();
          await card(admin, workout).getByText(exercise, { exact: true }).waitFor();
        });
        await step(id('referenced-exercise-name-protection'), async () => {
          await catalog(admin); await button(admin, `Edit ${exercise}`).click();
          await field(dialog(admin), 'Exercise name').fill(`${exercise} Renamed`);
          await action(admin, 'PUT', /^\/api\/admin\/catalog\/exercises\/[0-9]+$/, button(dialog(admin), 'Save exercise'), 409);
          await dialog(admin).getByRole('alert').waitFor();
          await button(dialog(admin), 'Cancel').click(); await reload(admin);
          await heading(admin, exercise).waitFor(); assert(await heading(admin, `${exercise} Renamed`).count() === 0, 'protected name changed');
        });
        await step(id('published-exercise-archive-protection'), async () => {
          await button(admin, `Archive ${exercise}`).click();
          await action(admin, 'POST', /^\/api\/admin\/catalog\/exercises\/[0-9]+\/archive$/, button(dialog(admin), 'Confirm archive'), 409);
          await dialog(admin).getByRole('alert').waitFor(); await button(dialog(admin), 'Cancel').click();
          await card(admin, exercise).getByText('Active', { exact: true }).waitFor();
        });
        await step(id('nutrition-catalog-create-update'), async () => {
          await catalog(owner, 'Nutrition'); await button(owner, 'Create nutrition plan').click();
          for (const [label, value] of [['Nutrition category', nutrition], ['Calories (kcal estimate)', '1800'], ['Protein (g estimate)', '100'], ['Carbs (g estimate)', '200'], ['Fats (g estimate)', '60'], ['Meals', 'Synthetic oats']]) await field(dialog(owner), label).fill(value);
          await saveCatalog(owner, 'nutrition', true);
          await catalog(admin, 'Nutrition'); await button(admin, `Edit ${nutrition}`).click();
          await field(dialog(admin), 'Meals').fill('Synthetic oats updated'); await saveCatalog(admin, 'nutrition');
          await card(admin, nutrition).getByText('Synthetic oats updated', { exact: true }).waitFor();
        });
        await step(id('member-cookie-login'), async () => { member = await freshLogin(`ui-member${gym}@fixture.invalid`); });
        await step(id('member-local-catalogs'), async () => {
          for (const [path, name] of [['/membership/plans', `${basic} Plan`], ['/workouts/library', exercise], ['/workouts/plans', workout], ['/nutrition/diet-plans', nutrition]]) {
            await visit(member,`${ui}${path}`); await heading(member, name).waitFor();
            assert(!(await member.locator('main').innerText()).includes(otherPrefix), `foreign gym catalog visible on ${path}`);
          }
          await member.getByText('Synthetic oats updated', { exact: true }).waitFor();
        });
        await step(id('member-subscribe'), async () => {
          await visit(member,`${ui}/membership/plans`);
          purchased = await (await action(member, 'POST', /^\/api\/memberships\/subscribe\/[^/]+\/[0-9]+$/, button(card(member, `${basic} Plan`), 'Purchase Plan'))).json();
          assert(purchased.plan.name === basic && purchased.endDate === plusMonths(purchased.startDate, 1), 'purchase does not match one-month synthetic plan');
          await visit(member,`${ui}/membership/active`); await heading(member, `${basic} Plan`).waitFor();
          await member.getByText(dateLabel(purchased.endDate), { exact: true }).waitFor();
        });
        await step(id('member-renew'), async () => {
          renewed = await (await action(member, 'POST', /^\/api\/memberships\/renew\/[^/]+\/[0-9]+$/, button(member, 'Renew Plan'))).json();
          assert(renewed.id === purchased.id && renewed.endDate === plusMonths(purchased.endDate, 1), 'renew did not extend existing membership by one month');
          await member.getByText(dateLabel(renewed.endDate), { exact: true }).waitFor();
        });
        await step(id('member-upgrade'), async () => {
          await visit(member,`${ui}/membership/plans`);
          const upgraded = await (await action(member, 'POST', /^\/api\/memberships\/upgrade\/[^/]+\/[0-9]+$/, button(card(member, `${premium} Plan`), 'Upgrade Plan'))).json();
          assert(upgraded.plan.name === premium && upgraded.endDate === renewed.endDate, 'upgrade changed expiry or selected wrong plan');
          await visit(member,`${ui}/membership/active`); await heading(member, `${premium} Plan`).waitFor();
          await member.getByText(dateLabel(renewed.endDate), { exact: true }).waitFor();
          await visit(member,`${ui}/membership/history`); await member.getByRole('cell', { name: premium, exact: true }).waitFor();
        });
        await step(id('member-workout-log'), async () => {
          await visit(member,`${ui}/workouts/plans`); await button(card(member, workout), 'Assign Plan').click();
          await card(member, exercise).getByLabel('Sets', { exact: true }).fill('3');
          await card(member, exercise).getByLabel('Reps', { exact: true }).fill('8');
          await button(card(member, exercise), 'Mark Complete').click();
          await member.getByText('100%', { exact: true }).waitFor();
          await action(member, 'POST', /^\/api\/workouts\/logs\/[^/]+$/, button(member, 'Save Workout'));
          await visit(member,`${ui}/workouts/history`); await member.getByRole('cell', { name: '12 min', exact: true }).waitFor();
          await member.getByRole('cell', { name: '1', exact: true }).waitFor();
        });
        await step(id('member-nutrition-log'), async () => {
          await visit(member,`${ui}/nutrition/food-logger`);
          for (const [label, value] of [['Food name', `${prefix} Oats`], ['Calories', '321'], ['Protein', '17'], ['Carbs', '45'], ['Fat', '8']]) await field(member, label).fill(value);
          await action(member, 'POST', /^\/api\/nutrition\/log\/[^/]+$/, button(member, 'Add Food'));
          await reload(member); await member.getByText(`${prefix} Oats`, { exact: true }).first().waitFor();
          await visit(member,`${ui}/nutrition/calorie-tracker`); await member.getByText('Consumed Calories: 321', { exact: true }).waitFor();
        });
        await step(id('member-progress-weight-bmi'), async () => {
          await visit(member,`${ui}/progress/weight`); await field(member, 'Weight kg').fill('73.4');
          await action(member, 'POST', /^\/api\/tracking\/progress\/[^/]+$/, button(member, 'Add Weight'));
          await reload(member); await member.getByRole('cell', { name: '73.4 kg', exact: true }).waitFor();
          await visit(member,`${ui}/progress/bmi`); await field(member, 'Height cm').fill('175'); await field(member, 'Weight kg').fill('73.4');
          await member.getByText('24', { exact: true }).waitFor(); await member.getByText('Normal', { exact: true }).waitFor();
        });
        await step(id('member-attendance'), async () => {
          await visit(member,`${ui}/attendance/check-in`);
          await action(member, 'POST', /^\/api\/tracking\/attendance\/check-in\/[^/]+$/, button(member, 'Check-In'));
          await member.getByText('Checked In', { exact: true }).waitFor();
          assert(await button(member, 'Check-In').isDisabled(), 'duplicate check-in UI is enabled');
          await action(member, 'PUT', /^\/api\/tracking\/attendance\/check-out\/[0-9]+$/, button(member, 'Check-Out'));
          await reload(member); assert(await button(member, 'Check-Out').isDisabled(), 'closed attendance still checked in');
          await visit(member,`${ui}/attendance/history`); await member.getByRole('table').waitFor();
          const cells = await member.locator('tbody tr').first().locator('td').allTextContents();
          assert(cells.length === 3 && cells[1] !== '--' && cells[2] !== '--', 'check-in/out history is missing times');
        });
        // Use a fresh day well beyond the old suite's tomorrow 10:00 slot.
        const bookingDate = new Date(Date.now() + (30 + gym) * 86400000).toISOString().slice(0, 10);
        await step(id('member-booking'), async () => {
          await book(member, gym * 100, bookingDate, '14:00');
          await visit(member,`${ui}/trainers/history`); await member.getByRole('cell', { name: dateLabel(bookingDate), exact: true }).waitFor();
          await member.getByRole('cell', { name: '14:00', exact: true }).waitFor();
        });
        await step(id('member-booking-conflict'), async () => {
          await book(member, gym * 100, bookingDate, '14:30', 409); await member.getByRole('alert').waitFor();
          await visit(member,`${ui}/trainers/history`); await member.getByRole('cell', { name: dateLabel(bookingDate), exact: true }).waitFor(); assert(await member.locator('tbody tr').count() === 1, 'rejected overlap changed confirmed session count');
        });
        await step(id('peer-cookie-login'), async () => { peer = await freshLogin(`ui-peer${gym}@fixture.invalid`); });
        await step(id('peer-booking-conflict'), async () => {
          await book(peer, gym * 100, bookingDate, '14:00', 409); await peer.getByRole('alert').waitFor();
          await visit(peer,`${ui}/trainers/history`); await peer.getByText('No records found', { exact: true }).waitFor();
        });
        await step(id('member-manager-route-denial'), async () => {
          for (const route of ['/gym/catalogs', '/admin/users', '/owner']) { await visit(member,`${ui}${route}`); await member.waitForURL(`${ui}/dashboard`); }
          assert(await member.getByRole('link', { name: 'Catalogs', exact: true }).count() === 0, 'member has catalog manager navigation');
        });
        await step(id('trainer-current-policy'), async () => {
          trainer = await freshLogin(`trainer${gym}@fixture.invalid`);
          for (const route of ['/gym/catalogs', '/admin/users', '/owner']) { await visit(trainer,`${ui}${route}`); await trainer.waitForURL(`${ui}/dashboard`); }
          await visit(trainer,`${ui}/workouts/plans`); await heading(trainer, workout).waitFor();
          assert(!(await trainer.locator('main').innerText()).includes(otherPrefix), 'trainer sees foreign catalog');
          // Current UI is self-scoped; it does not offer a trainer roster or member records editor.
          await visit(trainer,`${ui}/trainers/history`); await trainer.getByText('No records found', { exact: true }).waitFor();
          assert(await trainer.getByRole('link', { name: 'Catalogs', exact: true }).count() === 0, 'trainer has manager navigation');
        });
        await step(id('admin-current-policy'), async () => {
          await visit(admin,`${ui}/admin/users`);
          const ownerRow = admin.getByRole('row').filter({ hasText: `owner${gym}@fixture.invalid` });
          await ownerRow.getByRole('button', { name: 'Edit', exact: true }).waitFor();
          assert(await ownerRow.getByRole('button', { name: 'Edit', exact: true }).isDisabled(), 'ADMIN can edit OWNER');
          const selfRow = admin.getByRole('row').filter({ hasText: `admin${gym}@fixture.invalid` });
          assert(await selfRow.getByRole('button', { name: 'Edit', exact: true }).isDisabled(), 'ADMIN can edit ADMIN');
          assert(await admin.getByRole('cell', { name: `owner${gym === 1 ? 2 : 1}@fixture.invalid`, exact: true }).count() === 0, 'admin sees foreign gym user');
          const memberRow = admin.getByRole('row').filter({ hasText: `ui-member${gym}@fixture.invalid` });
          await memberRow.getByRole('button', { name: 'Edit', exact: true }).click();
          assert((await field(dialog(admin), 'Role').locator('option').allTextContents()).join(',') === 'MEMBER,TRAINER', 'ADMIN can grant elevated role');
          await button(dialog(admin), 'Close modal').click();
          await visit(admin,`${ui}/gym/settings`); await admin.waitForURL(`${ui}/dashboard`);
        });
        await step(id('catalog-archive'), async () => {
          await catalog(admin, 'Workouts'); await archive(admin, workout, 'workouts');
          await catalog(admin); await archive(admin, exercise, 'exercises');
          await catalog(admin, 'Nutrition'); await archive(admin, nutrition, 'nutrition');
          for (const [path, name] of [['/workouts/plans', workout], ['/workouts/library', exercise], ['/nutrition/diet-plans', nutrition]]) {
            await visit(member,`${ui}${path}`);
            await heading(member, path.startsWith('/nutrition') ? 'Nutrition Management' : 'Workout Management').waitFor();
            assert(await heading(member, name).count() === 0, `archived catalog still public on ${path}`);
          }
          await visit(member,`${ui}/workouts/history`); await member.getByRole('cell', { name: '1', exact: true }).waitFor();
          // Membership UI exposes deletion, not archive. Leave purchased plans/history intact.
        });
      } catch { /* The failing step is recorded; independent Gym B still runs. */ }
    }
    await step('cookie-ui.cross-gym-persisted-catalog-isolation', async () => {
      assert(managers.length === 2, 'both gym manager sessions are required');
      for (const { view, gym, prefix } of managers) {
        const foreign = `CookieUI G${gym === 1 ? 2 : 1} ${stamp}`;
        await visit(view,`${ui}/admin/memberships`);
        await view.getByRole('cell', { name: `${prefix} Basic`, exact: true }).waitFor();
        assert(!(await view.locator('main').innerText()).includes(foreign), 'foreign persisted membership plans visible');
        for (const [tab, name] of [['Exercises', `${prefix} Press`], ['Workouts', `${prefix} Foundation`], ['Nutrition', `${prefix} Nutrition`]]) {
          await catalog(view, tab); await heading(view, name).waitFor();
          assert(!(await view.locator('main').innerText()).includes(foreign), `foreign persisted ${tab} visible`);
        }
      }
    });
    await step('cookie-ui.same-origin-no-bearer-no-runtime-errors', async () => {
      assert(violations.length === 0, violations.join('; '));
      assert(errors.length === 0, `uncaught browser errors: ${errors.join('; ')}`);
    });
  } catch (error) {
    if (!failures.length) failures.push({ id: 'cookie-ui.preflight', message: String(error.message || error) });
  } finally {
    for (const context of contexts) await context.close().catch(() => {});
  }
  for (const id of expected) if (!checks.includes(id) && !failures.some(failure => failure.id === id)) failures.push({ id, message: 'Not executed because a prerequisite failed; coverage remains unverified.' });
  return { suite: 'fitcore-cookie-gym-ui-v1', passed: checks.length, checks, failures, browserType:page.context().browser().browserType().name(), browserVersion:page.context().browser().version(), production: 'BLOCKED' };
}
