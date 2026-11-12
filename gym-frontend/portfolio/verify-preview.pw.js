/* eslint-disable no-unused-expressions -- Playwright CLI executes this function. */
async (page) => {
  const origin = 'http://127.0.0.1:18880';
  const failures = [];
  page.on('request', (request) => {
    const url = new URL(request.url());
    if (url.origin !== origin || url.pathname.startsWith('/api')) failures.push(url.pathname);
  });
  page.on('console', (message) => {
    if (message.type() === 'error') failures.push(message.text());
  });
  page.on('pageerror', (error) => failures.push(error.message));

  await page.goto(origin);
  await page.getByRole('heading', { name: 'Atlas Strength' }).waitFor();
  await page.getByText('Goblet squat', { exact: true }).waitFor();

  await page.getByLabel('Gym workspace').selectOption('harbor-movement');
  await page.getByRole('heading', { name: 'Harbor Movement' }).waitFor();
  await page.getByText('Mobility coaching', { exact: true }).waitFor();
  await page.getByText('Single-leg balance', { exact: true }).waitFor();
  if (await page.getByText('Morgan Hale', { exact: true }).count()) throw new Error('Previous gym still shown');

  await page.setViewportSize({ width: 375, height: 800 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) {
    throw new Error('Preview has horizontal overflow');
  }
  await page.reload();
  await page.getByRole('heading', { name: 'Atlas Strength' }).waitFor();

  const storage = await page.evaluate(() => [localStorage.length, sessionStorage.length, document.cookie]);
  if (storage[0] || storage[1] || storage[2] || failures.length) throw new Error('Unexpected browser state or request');
  if ((await page.request.get(origin + '/api/auth/me')).status() !== 404) throw new Error('API route available');
  return { passed: ['two gyms', 'workspace switch', 'mobile width', 'reload reset', 'no browser session state', 'no API or external requests'] };
}
