import test from 'node:test';import assert from 'node:assert/strict';
import {captureInvitation,validateSignup,canEditAccount,workoutKey,clearTrainingState} from '../src/utils/tenant-session.js';
test('invitation capture removes the fragment without persisting the secret',()=>{let replaced;globalThis.window={location:{pathname:'/register',hash:'#invitationToken='+'a'.repeat(43),search:''},history:{state:null,replaceState:(...args)=>{replaced=args[2];}}};assert.equal(captureInvitation(),'a'.repeat(43));assert.equal(replaced,'/register');window.location.hash='#invitationToken=invalid';assert.equal(captureInvitation(),null);});
test('signup checks password confirmation composition and gym slug',()=>{const good={fullName:'Gym Owner',email:'owner@fixture.invalid',password:'Owner-Password!2026',confirmPassword:'Owner-Password!2026',gymName:'Test gym',slug:'test-gym'};assert.equal(validateSignup(good,true),'');for(const patch of [{confirmPassword:'mismatch'},{password:'short'},{slug:'Bad Slug'},{email:'invalid'}])assert.notEqual(validateSignup({...good,...patch},true),'');});
test('management policy protects owners and limits administrators',()=>{assert.equal(canEditAccount({role:'ADMIN'},{role:'ADMIN'}),false);assert.equal(canEditAccount({role:'OWNER'},{role:'OWNER'}),false);assert.equal(canEditAccount({role:'OWNER'},{role:'ADMIN'}),true);assert.equal(canEditAccount({role:'MEMBER'},{role:'MEMBER'}),false);});
test('workout selection is scoped by gym and account; logout clears only training state',()=>{function storage(){const map=new Map([['workoutPlan:old','1'],['fitcore-theme','dark']]);return {get length(){return map.size;},key:i=>[...map.keys()][i],removeItem:key=>map.delete(key),getItem:key=>map.get(key)};}globalThis.localStorage=storage();globalThis.sessionStorage=storage();assert.notEqual(workoutKey({gymId:'A',id:'u'}),workoutKey({gymId:'B',id:'u'}));clearTrainingState();assert.equal(localStorage.getItem('workoutPlan:old'),undefined);assert.equal(localStorage.getItem('fitcore-theme'),'dark');});

test('remembered account adoption discards an older per-tab credential',async()=>{
  const {readSessionToken}=await import('../src/utils/tenant-session.js');
  const make=entries=>{const data=new Map(entries);return {getItem:key=>data.get(key)||null,removeItem:key=>data.delete(key)};};
  globalThis.localStorage=make([['token','gym-A']]);globalThis.sessionStorage=make([['token','gym-B'],['user','old B']]);
  assert.equal(typeof readSessionToken,'function');assert.equal(readSessionToken(),'gym-A');assert.equal(sessionStorage.getItem('token'),null);assert.equal(sessionStorage.getItem('user'),null);
  localStorage.removeItem('token');assert.equal(readSessionToken(),null);
});
