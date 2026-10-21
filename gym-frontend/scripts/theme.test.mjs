import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { normalizeTheme } from '../src/utils/theme.js';

const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
const bootstrap = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
const applyBeforeReact = (saved, systemDark, blocked = false) => {
  const state = { classes: new Set(), dataset: {} };
  const context = {
    localStorage: { getItem: () => { if (blocked) throw new Error('Preference storage denied'); return saved; } },
    matchMedia: () => ({ matches: systemDark }),
    document: { documentElement: { dataset: state.dataset, classList: { toggle: (name, on) => on ? state.classes.add(name) : state.classes.delete(name) } } },
  };
  assert.ok(bootstrap, 'Theme must initialize before React to avoid the wrong first paint');
  vm.runInNewContext(bootstrap, context);
  return { dark: state.classes.has('dark'), theme: state.dataset.theme };
};
test('saved dark wins over light OS before React loads', () => assert.deepEqual(applyBeforeReact('dark', false), { dark: true, theme: 'dark' }));
test('saved light wins over dark OS before React loads', () => assert.deepEqual(applyBeforeReact('light', true), { dark: false, theme: 'light' }));
test('system preference resolves both ways before React loads', () => {
  assert.deepEqual(applyBeforeReact('system', true), { dark: true, theme: 'system' });
  assert.deepEqual(applyBeforeReact('system', false), { dark: false, theme: 'system' });
});
test('missing or invalid stored preference follows the system', () => {
  for (const value of [null, '', 'invalid']) assert.deepEqual(applyBeforeReact(value, true), { dark: true, theme: 'system' });
});
test('blocked preference storage still applies system theme', () => assert.deepEqual(applyBeforeReact(null, true, true), { dark: true, theme: 'system' }));
test('normalization accepts only supported theme values', () => {
  for (const value of ['light', 'dark', 'system']) assert.equal(normalizeTheme(value), value);
  for (const value of ['invalid', null, undefined, {}, 'LIGHT']) assert.equal(normalizeTheme(value), 'system');
});
