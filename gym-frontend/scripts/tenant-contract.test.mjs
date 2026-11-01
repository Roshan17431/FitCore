import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
const require=createRequire(import.meta.url);
const source=(await readFile(new URL('../src/services/api.js',import.meta.url),'utf8')).replace("from 'axios'",`from '${pathToFileURL(require.resolve('axios')).href}'`).replaceAll('import.meta.env','({VITE_API_BASE_URL:"http://127.0.0.1:18081"})');
globalThis.localStorage={getItem:k=>k==='token'?'trusted-token':null,removeItem(){}};
globalThis.sessionStorage={getItem:()=>null,removeItem(){}};
globalThis.window={dispatchEvent(){},location:{href:''}};
const {default:api,authService,gymService}=await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
let captured;api.defaults.adapter=async config=>{captured=config;return {data:{},status:200,headers:{},config};};
test('auth/me carries stored bearer while public login does not',async()=>{assert.equal(typeof authService.me,'function');await authService.me();assert.equal(captured.headers.Authorization,'Bearer trusted-token');await authService.login('x@y.invalid','password');assert.equal(captured.headers.Authorization,undefined);});
test('owner signup and acceptance send only approved input fields',async()=>{assert.equal(typeof authService.registerOwner,'function');await authService.registerOwner({fullName:'Owner',email:'a@b.invalid',password:'secret',gymName:'Gym',slug:'gym',role:'OWNER',gymId:'forged',confirmPassword:'secret'});assert.deepEqual(Object.keys(JSON.parse(captured.data)).sort(),['email','fullName','gymName','password','slug']);await authService.register({fullName:'Member',email:'m@b.invalid',password:'secret',invitationToken:'one-shot',role:'ADMIN'});assert.equal(JSON.parse(captured.data).invitationToken,'one-shot');assert.equal(JSON.parse(captured.data).role,undefined);});
test('gym update and invitation APIs use current gym routes without body IDs',async()=>{assert.ok(gymService);await gymService.update({name:'New gym',id:'forged',status:'ACTIVE'});assert.equal(captured.url,'/gym');assert.deepEqual(JSON.parse(captured.data),{name:'New gym'});await gymService.invite({email:'m@b.invalid',role:'TRAINER',gymId:'forged'});assert.deepEqual(JSON.parse(captured.data),{email:'m@b.invalid',role:'TRAINER'});});

test('late response from a previous session is discarded',async()=>{
  const original=localStorage.getItem, adapter=api.defaults.adapter;let token='old';let started,release;
  const sent=new Promise(resolve=>{started=resolve;});localStorage.getItem=k=>k==='token'?token:null;
  api.defaults.adapter=config=>new Promise(resolve=>{started();release=()=>resolve({data:{gym:'old'},status:200,headers:{},config});});
  try {const request=gymService.get();await sent;token='new';release();await assert.rejects(request,error=>error.code==='ERR_CANCELED');assert.equal(token,'new');}
  finally {localStorage.getItem=original;api.defaults.adapter=adapter;}
});
test('only current-session 401 and suspended-gym 403 clear auth; normal 403 preserves it',async()=>{
  const originals=[localStorage,sessionStorage,window,globalThis.CustomEvent], adapter=api.defaults.adapter;
  const makeStorage=()=>{const values=new Map([['token','current'],['user','profile']]);return {getItem:k=>values.get(k)||null,removeItem:k=>values.delete(k)};};
  const events=[];globalThis.CustomEvent=class {constructor(type,options){this.type=type;this.detail=options.detail;}};
  globalThis.window={dispatchEvent:event=>events.push(event)};
  const fail=async(status,message,old=false)=>{globalThis.localStorage=makeStorage();globalThis.sessionStorage=makeStorage();events.length=0;api.defaults.adapter=async config=>{if(old)config.fitcoreToken='old';throw Object.assign(new Error('Denied'),{config,response:{status,data:{message}}});};await assert.rejects(gymService.get());};
  try {
    await fail(401,'Unauthorized',true);assert.equal(localStorage.getItem('token'),'current');assert.equal(events.length,0);
    await fail(403,'Forbidden');assert.equal(localStorage.getItem('token'),'current');assert.equal(events.length,0);
    await fail(401,'Unauthorized');assert.equal(localStorage.getItem('token'),null);assert.equal(sessionStorage.getItem('user'),null);assert.equal(events[0].type,'fitcore:session-ended');
    await fail(403,'GYM_UNAVAILABLE');assert.equal(localStorage.getItem('token'),null);assert.match(events[0].detail,/suspended/);
  }finally{[globalThis.localStorage,globalThis.sessionStorage,globalThis.window,globalThis.CustomEvent]=originals;api.defaults.adapter=adapter;}
});

test('signup native slug pattern is valid with browser Unicode-set semantics',async()=>{
  const signup=await readFile(new URL('../src/pages/Signup.jsx',import.meta.url),'utf8');
  const pattern=signup.match(/pattern="([^"]+)"/)[1];
  const valid=new RegExp(`^(?:${pattern})$`,'v');
  assert.equal(valid.test('northstar-gym'),true);assert.equal(valid.test('UPPER'),false);
});
