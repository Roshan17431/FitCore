import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import test from 'node:test';

// Only replace Vite's build-time env injection; exercise the real services and Axios pipeline.
const require = createRequire(import.meta.url);
const source = (await readFile(new URL('../src/services/api.js', import.meta.url), 'utf8'))
  .replace("from 'axios'", `from '${pathToFileURL(require.resolve('axios')).href}'`)
  .replace("from '../utils/catalog'", `from '${new URL('../src/utils/catalog.js', import.meta.url).href}'`).replace("from './browser-session.js'", `from '${new URL('../src/services/browser-session.js', import.meta.url).href}'`).replaceAll('import.meta.env', '({ VITE_API_BASE_URL: "http://127.0.0.1:18081" })');
globalThis.localStorage = { getItem: () => null };
globalThis.sessionStorage = { getItem: () => null };
const { default: api, membershipService, trainerService, catalogService, workoutService } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
const plan = { id: 3, name: 'Premium', price: '120', durationMonths: 3, features: ['Classes'] };
const membership = { id: 41, plan, startDate: '2030-01-01', endDate: '2030-04-01', status: 'ACTIVE', remainingDays: 29, serverDate: '2030-03-03', timeZone: 'UTC' };
const respond = (data, status = 200) => {
  api.defaults.adapter = async (config) => ({ data, status, statusText: 'OK', headers: {}, config });
};

test('active membership retains identity and package data for guarded renewals and upgrades', async () => {
  respond(membership);
  const active = await membershipService.getActive(7);
  assert.equal(active.plan, 'Premium');
  assert.equal(active.planId, 3);
  assert.equal(active.planPrice, 120);
  assert.equal(active.durationMonths, 3);
  assert.equal(active.id, 41);
  assert.equal(active.endDate, '2030-04-01');
});

test('remaining days use the server UTC result rather than the browser clock', async () => {
  respond(membership);
  const active = await membershipService.getActive(7);
  assert.equal(active.remainingDays, 29);
  assert.equal(active.serverDate, '2030-03-03');
  assert.equal(active.timeZone, 'UTC');
});

test('effective status takes precedence over the stored lifecycle enum', async () => {
  respond([{ ...membership, status: 'ACTIVE', effectiveStatus: 'UPCOMING' }]);
  const [history] = await membershipService.getHistory(7);
  assert.equal(history.status, 'Upcoming');
});

test('a successful empty 204 current response means no current entitlement', async () => {
  respond('', 204);
  assert.equal(await membershipService.getActive(7), null);
});

test('history preserves renewal identity, plan metadata, and expired status', async () => {
  respond([{ ...membership, status: 'EXPIRED' }]);
  const [history] = await membershipService.getHistory(7);
  assert.equal(history.planId, 3);
  assert.equal(history.planPrice, 120);
  assert.equal(history.durationMonths, 3);
  assert.equal(history.endDate, '2030-04-01');
  assert.equal(history.planName, 'Premium');
  assert.equal(history.status, 'Expired');
});

test('renew sends membership identity and its expected expiry to the dedicated endpoint', async () => {
  let captured;
  api.defaults.adapter = async (config) => {
    captured = config;
    return { data: membership, status: 200, headers: {}, config };
  };
  assert.equal(typeof membershipService.renew, 'function', 'dedicated renewal action is available');
  await membershipService.renew(7, 41, '2030-04-01');
  assert.equal(captured.url, '/memberships/renew/7/41');
  assert.deepEqual(JSON.parse(captured.data), { expectedEndDate: '2030-04-01' });
});

test('upgrade sends the current membership and expected expiry to the target-plan endpoint', async () => {
  let captured;
  api.defaults.adapter = async (config) => {
    captured = config;
    return { data: membership, status: 200, headers: {}, config };
  };
  assert.equal(typeof membershipService.upgrade, 'function', 'dedicated upgrade action is available');
  await membershipService.upgrade(7, 5, 41, '2030-04-01');
  assert.equal(captured.url, '/memberships/upgrade/7/5');
  assert.deepEqual(JSON.parse(captured.data), { membershipId: 41, expectedEndDate: '2030-04-01' });
});

test('booking serializes business fields without replaying session tenant or security metadata', async () => {
  let captured;
  api.defaults.adapter = async config => { captured = config; return {data:{},status:200,headers:{},config}; };
  const member = { id: 'member-a', fullName: 'Local Member', email: 'member@fixture.invalid', role: 'MEMBER', gymId: 'gym-a', authzVersion: 7 };
  await trainerService.bookSession(member.id, 3, { member, trainer: {id:3,user:{...member,id:'trainer-a',role:'TRAINER'},specialization:'Strength',gymId:'gym-a'}, gymId:'forged', sessionDate:'2030-01-01',startTime:'10:00',endTime:'11:00' });
  const body=JSON.parse(captured.data);
  assert.equal(captured.url,'/trainers/book/member-a/3');
  assert.equal(body.member.id,member.id);
  assert.equal(body.trainer.id,3);
  assert.equal(body.sessionDate,'2030-01-01');
  assert.ok(!captured.data.includes('gymId') && !captured.data.includes('authzVersion'));
});


test('catalog resource adapters use manager collection, item, archive and publish endpoints', async () => {
  const captured = [];
  api.defaults.adapter = async config => { captured.push({ method: config.method, url: config.url }); return { data: { id: 3 }, status: 200, headers: {}, config }; };
  for (const kind of ['exercises', 'workouts', 'nutrition']) {
    await catalogService[kind].list(); await catalogService[kind].get(3); await catalogService[kind].create({}); await catalogService[kind].update(3, {}); await catalogService[kind].archive(3);
    assert.deepEqual(captured.splice(0), [{ method: 'get', url: `/admin/catalog/${kind}` }, { method: 'get', url: `/admin/catalog/${kind}/3` }, { method: 'post', url: `/admin/catalog/${kind}` }, { method: 'put', url: `/admin/catalog/${kind}/3` }, { method: 'post', url: `/admin/catalog/${kind}/3/archive` }]);
  }
  await catalogService.workouts.publish(3);
  assert.deepEqual(captured, [{ method: 'post', url: '/admin/catalog/workouts/3/publish' }]);
});

test('library mapping keeps approved media and suppresses arbitrary legacy image URLs', async () => {
  respond([{ id: 1, name: 'Legacy', category: 'Chest', imageUrl: 'https://unreviewed.invalid/legacy.jpg' }, { id: 2, name: 'Reviewed', category: 'Chest', imageUrl: '/images/reviewed.png', imageApproved: true }]);
  const [group] = await workoutService.getLibrary();
  assert.equal(group.exercises[0].image, ''); assert.equal(group.exercises[0].imageApproved, false);
  assert.equal(group.exercises[1].image, '/images/reviewed.png'); assert.equal(group.exercises[1].imageApproved, true);
});

test('library categories treat object prototype names as ordinary gym-provided text', async () => {
  const categories = ['constructor', 'toString', '__proto__', 'Strength'];
  respond(categories.flatMap((category, index) => [
    { id: index * 2 + 1, name: `${category} first`, category },
    { id: index * 2 + 2, name: `${category} second`, category },
  ]));
  const groups = await workoutService.getLibrary();
  assert.deepEqual(groups.map(group => group.category), categories);
  for (const group of groups) assert.equal(group.exercises.length, 2);
  assert.equal(Object.prototype.exercises, undefined);
});
