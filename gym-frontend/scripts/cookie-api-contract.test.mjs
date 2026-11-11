import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
const require=createRequire(import.meta.url);
const env='({VITE_AUTH_TRANSPORT:"cookie-spike"})';
const cookieSource=(await readFile(new URL('../src/services/browser-session.js',import.meta.url),'utf8')).replaceAll('import.meta.env',env);
const cookieUrl=`data:text/javascript;base64,${Buffer.from(cookieSource).toString('base64')}`;
const source=(await readFile(new URL('../src/services/api.js',import.meta.url),'utf8')).replace("from 'axios'",`from '${pathToFileURL(require.resolve('axios')).href}'`).replace("from '../utils/catalog'",`from '${new URL('../src/utils/catalog.js',import.meta.url).href}'`).replace("from './browser-session.js'",`from '${cookieUrl}'`).replaceAll('import.meta.env',env);
const values=new Map([['token','legacy-secret'],['user','legacy-profile']]);
globalThis.localStorage={getItem:k=>{assert.ok(k!=='token'&&k!=='user','cookie transport must not read bearer identity');return values.get(k)??null;},setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
globalThis.sessionStorage={getItem:()=>{throw Error('No bearer fallback');}};
Object.defineProperty(globalThis,'navigator',{configurable:true,value:{locks:{request:async(_n,_o,fn)=>fn()}}});
const calls=[];let authenticated=false,revision=0,csrfToken='csrf';
const user={id:'current-user',gymId:'current-gym',role:'OWNER',authzVersion:2};
globalThis.fetch=async(url,config)=>{calls.push([url,config]);let data={};if(url.endsWith('/csrf'))data={authenticated,csrfToken,transitionState:'IDLE'};else if(url.endsWith('/login')){authenticated=true;revision++;csrfToken='new';data={user:{...user,role:'ADMIN'},csrfToken:'new',transitionState:'PENDING'};}else if(url.endsWith('/logout')){authenticated=false;revision++;csrfToken='preauth';data={csrfToken:'preauth',transitionState:'PENDING'};}else if(url.endsWith('/ack'))data={acknowledged:true};else if(url.endsWith('/me'))data=user;else if(url==='/api/gym/invitations'&&config.method==='POST')data={id:'invite-receipt',role:'MEMBER',expiresAt:'2030-01-01T00:00:00Z',deliveryStatus:'QUEUED',invitationToken:'unexpected-secret'};else data={name:'fresh gym'};if(data.transitionState)Object.assign(data,{familyId:'2b830b6d-8b6d-4f46-bcc3-39091199f323',contextRevision:String(revision),familyRevision:String(revision),contextKind:authenticated?'AUTH':'PREAUTH',currentContext:true,pendingKind:data.transitionState==='PENDING'?(authenticated?'LOGIN':'LOGOUT'):null,reauthenticationRequired:false,authenticated:data.transitionState==='IDLE'&&authenticated});return{ok:true,status:/^\/api\/browser\/(registration\/|verification\/|forgot-password$)/.test(url)?202:/^\/api\/browser\/(verify-email|reset-password)$/.test(url)?204:200,headers:new Headers(),text:async()=>/^\/api\/browser\/(registration\/|verification\/|verify-email$|forgot-password$|reset-password$)/.test(url)?'':JSON.stringify(data)};};
const {default:api,authService,gymService}=await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
test('cookie auth service uses ACK plus fresh me and relative business adapter without bearer reads',async()=>{
 assert.deepEqual(await authService.login('owner@fixture.invalid','password'),user);
 assert.deepEqual(await authService.me(),user);
 assert.deepEqual(await gymService.get(),{name:'fresh gym'});
 assert.equal(api.defaults.baseURL,'/api');
 assert.ok(calls.every(([url,c])=>url.startsWith('/api/')&&c.credentials==='same-origin'&&!c.headers.Authorization));
 assert.equal(values.get('token'),'legacy-secret');
});
test('legacy token-returning registration methods remain denied without network or cookie writes',async()=>{
 const before=calls.length;
 for(const action of [()=>authService.register({}),()=>authService.registerOwner({})])await assert.rejects(action(),/unavailable/);
 assert.equal(calls.length,before);
});
test('cookie logout is explicit server transition and fresh unauthenticated me returns null',async()=>{
 assert.equal(typeof authService.logout,'function');await authService.logout();assert.equal(await authService.me(),null);
 assert.ok(calls.some(([url])=>url==='/api/browser/logout'));
});

test('account service methods use only frozen receipt routes without legacy auth fallback',async()=>{
 const before=calls.length;
 const input={fullName:'Synthetic Owner',email:'owner@fixture.invalid',password:'Password-2026!',gymName:'Fixture Gym',slug:'fixture',invitationToken:'memory-invitation',token:'memory-token'};
 for(const action of [()=>authService.pendingOwner(input),()=>authService.pendingInvite(input),()=>authService.requestVerification(input.email),()=>authService.verifyEmail(input),()=>authService.resetPassword(input.email),()=>authService.completeReset(input)])assert.equal(await action(),'');
 const writes=calls.slice(before).filter(([,config])=>config.method==='POST');
 assert.deepEqual(writes.map(([url])=>url),['/api/browser/registration/owner','/api/browser/registration/invitation','/api/browser/verification/request','/api/browser/verify-email','/api/browser/forgot-password','/api/browser/reset-password']);
 assert.ok(!calls.slice(before).some(([url])=>url==='/api/auth/me'||url.endsWith('/login')));
 assert.equal(values.get('token'),'legacy-secret');
});

test('cookie invitation service returns only queued receipt metadata even if a response contains an unexpected secret',async()=>{
 await authService.login('owner@fixture.invalid','password');
 assert.deepEqual(await gymService.invite({email:'member@fixture.invalid',role:'MEMBER'}),{id:'invite-receipt',role:'MEMBER',expiresAt:'2030-01-01T00:00:00Z',deliveryStatus:'QUEUED'});
});
