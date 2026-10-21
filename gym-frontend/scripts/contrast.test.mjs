import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const css = readFileSync(new URL('../src/index.css', import.meta.url), 'utf8');
const variables = block => Object.fromEntries([...block.matchAll(/--([\w-]+):\s*(#[\da-f]{6})\s*;/gi)].map(match => [match[1], match[2]]));
const light = variables(css.match(/:root\s*\{([^}]+)\}/)[1]);
const dark = { ...light, ...variables(css.match(/:root\.dark\s*\{([^}]+)\}/)[1]) };
const luminance = color => {
  assert.match(color || '', /^#[\da-f]{6}$/i, 'Expected a concrete color token');
  const linear = color.slice(1).match(/../g).map(v => parseInt(v,16)/255).map(v => v <= .04045 ? v/12.92 : ((v+.055)/1.055)**2.4);
  return linear.reduce((sum,v,i) => sum+v*[.2126,.7152,.0722][i],0);
};
const ratio = (a,b) => (Math.max(luminance(a),luminance(b))+.05)/(Math.min(luminance(a),luminance(b))+.05);
for (const [mode,palette] of Object.entries({light,dark})) {
  test(`${mode}: primary and secondary text meet AA on every surface`, () => {
    for(const text of ['ink','muted'])for(const bg of ['canvas','surface','surface-alt']) assert.ok(ratio(palette[text],palette[bg])>=4.5,`${text} on ${bg}`);
  });
  test(`${mode}: action and status text meet AA`, () => {
    for(const [fg,bg] of [['on-brand','brand'],['on-brand','brand-hover'],['brand','brand-soft']]) assert.ok(ratio(palette[fg],palette[bg])>=4.5,`${fg} on ${bg}`);
  });
  test(`${mode}: control boundaries and focus indicators meet 3:1`, () => {
    for(const fg of ['control-line','focus'])for(const bg of ['canvas','surface']) assert.ok(ratio(palette[fg],palette[bg])>=3,`${fg} on ${bg}`);
  });
}
