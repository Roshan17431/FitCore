import test from 'node:test';
import assert from 'node:assert/strict';
import {createCookieSession} from '../src/services/browser-session.js';
const family='2b830b6d-8b6d-4f46-bcc3-39091199f323';
const proof=(overrides={})=>({csrfToken:'control-only',authenticated:false,reauthenticationRequired:false,currentContext:true,familyId:family,contextRevision:'7',familyRevision:'7',contextKind:'PREAUTH',transitionState:'IDLE',pendingKind:null,...overrides});
const response=(body='',status=202)=>({ok:status>=200&&status<300,status,headers:new Headers(),text:async()=>typeof body==='string'?body:JSON.stringify(body)});
const fixture=(fetch,overrides={})=>{const map=new Map();let locked=false;const env={storage:{getItem:key=>map.get(key)??null,setItem:(key,value)=>map.set(key,String(value)),removeItem:key=>map.delete(key)},locks:{request:async(_n,_o,fn)=>{locked=true;try{return await fn();}finally{locked=false;}}},fetch:async(...args)=>{assert.equal(locked,true);return fetch(...args);},...overrides};return {session:createCookieSession(env),map};};
const routes=['registration/owner','registration/invitation','verification/request','verify-email','forgot-password','reset-password'];
for(const route of routes)test(`account receipt ${route} requires exact idle PREAUTH proof and returns no identity`,async()=>{
 const calls=[];const status=['verify-email','reset-password'].includes(route)?204:202;
 const f=fixture(async(url,config)=>{calls.push([url,config]);return url.endsWith('/csrf')?response(proof(),200):response('',status);});
 const result=await f.session.cookieAccountRequest({url:`/browser/${route}`,method:'post',data:{email:'synthetic@fixture.invalid',token:'memory-only'}});
 assert.equal(result.status,status);assert.equal(result.data,'');
 assert.deepEqual(calls.map(([url])=>url),['/api/browser/csrf',`/api/browser/${route}`,'/api/browser/csrf']);
 assert.equal(calls[1][1].headers['X-CSRF-TOKEN'],'control-only');
 assert.equal(calls[1][1].credentials,'same-origin');assert.equal(calls[1][1].headers.Authorization,undefined);
 assert.deepEqual([...f.map],[],'account receipts never write transition markers or secrets');
});
for(const url of ['/auth/register','/auth/verify-email','/browser/login','/browser/registration/owner?x=1','https://invalid/registration/owner','/browser/registration/owner/'])test(`account adapter denies non-receipt ${url} before network`,async()=>{
 let calls=0;const f=fixture(async()=>{calls++;return response();});
 await assert.rejects(()=>f.session.cookieAccountRequest({url,method:'POST'}),e=>e.code==='COOKIE_ROUTE_DISABLED');assert.equal(calls,0);
});
test('account adapter requires POST',async()=>{let calls=0;const f=fixture(async()=>{calls++;return response();});await assert.rejects(()=>f.session.cookieAccountRequest({url:'/browser/verify-email',method:'GET'}),e=>e.code==='COOKIE_ROUTE_DISABLED');assert.equal(calls,0);});
for(const overrides of [{authenticated:true,contextKind:'AUTH'},{contextKind:'AUTH'},{reauthenticationRequired:true}])test(`account adapter requires explicit signout for AUTH or expired source ${JSON.stringify(overrides)}`,async()=>{
 const calls=[];const f=fixture(async url=>{calls.push(url);return response(proof(overrides),200);});
 await assert.rejects(()=>f.session.cookieAccountRequest({url:'/browser/forgot-password',method:'POST'}),e=>e.code==='COOKIE_REAUTH_REQUIRED');assert.deepEqual(calls,['/api/browser/csrf']);
});
for(const changed of [{csrfToken:'replacement'},{familyId:'ab830b6d-8b6d-4f46-bcc3-39091199f323'},{contextRevision:'8',familyRevision:'8'},{authenticated:true,contextKind:'AUTH'}])test(`receipt refuses changed proof ${JSON.stringify(changed)}`,async()=>{
 let reads=0;const f=fixture(async url=>url.endsWith('/csrf')?response(proof(++reads===1?{}:changed),200):response());
 await assert.rejects(()=>f.session.cookieAccountRequest({url:'/browser/forgot-password',method:'POST'}),e=>e.code==='COOKIE_UNCERTAIN');
});
test('unexpected receipt body cannot expose tokens or establish identity',async()=>{
 const f=fixture(async url=>url.endsWith('/csrf')?response(proof(),200):response({token:'forbidden',user:{id:'forbidden'}},202));
 await assert.rejects(()=>f.session.cookieAccountRequest({url:'/browser/forgot-password',method:'POST'}),e=>e.code==='COOKIE_UNCERTAIN');
});
test('transient receipt transport failure is retryable uncertainty without a writer marker or automatic replay',async()=>{
 let writes=0;const f=fixture(async url=>{if(url.endsWith('/csrf'))return response(proof(),200);writes++;throw Error('offline');});
 await assert.rejects(()=>f.session.cookieAccountRequest({url:'/browser/forgot-password',method:'POST'}),e=>e.code==='COOKIE_UNCERTAIN');assert.equal(writes,1);assert.deepEqual([...f.map],[]);
});
test('controlled receipt denial keeps its status only after unchanged context proof',async()=>{
 const calls=[];const f=fixture(async url=>{calls.push(url);return url.endsWith('/csrf')?response(proof(),200):response({message:'RATE_LIMITED'},429);});
 await assert.rejects(()=>f.session.cookieAccountRequest({url:'/browser/forgot-password',method:'POST'}),e=>e.response?.status===429);
 assert.deepEqual(calls,['/api/browser/csrf','/api/browser/forgot-password','/api/browser/csrf']);
});
test('a transition completed while a receipt waited cannot be adopted',async()=>{
 let grant,calls=0;const f=fixture(async()=>{calls++;return response(proof(),200);},{locks:{request:(_n,_o,fn)=>new Promise(resolve=>{grant=()=>resolve(fn());})}});
 const result=f.session.cookieAccountRequest({url:'/browser/forgot-password',method:'POST'});f.map.set('fitcore:cookie-revision','1');grant();
 await assert.rejects(result,e=>e.code==='ERR_CANCELED');assert.equal(calls,0);
});
test('ordinary tenant adapter continues to deny receipt routes',async()=>{
 let calls=0;const f=fixture(async()=>{calls++;return response();});
 for(const route of routes)await assert.rejects(()=>f.session.cookieApiRequest({url:`/browser/${route}`,method:'POST'}),e=>e.code==='COOKIE_ROUTE_DISABLED');assert.equal(calls,0);
});

test('cookie-bearing account denial cannot be accepted as a controlled receipt denial',async()=>{
 const f=fixture(async url=>url.endsWith('/csrf')?response(proof(),200):{...response({message:'RATE_LIMITED'},429),headers:new Headers({'set-cookie':'unexpected=opaque'})});
 await assert.rejects(()=>f.session.cookieAccountRequest({url:'/browser/forgot-password',method:'POST'}),e=>e.code==='COOKIE_UNCERTAIN');
});
test('receipt never accepts replacement local ownership after its response',async()=>{
 let reads=0;const f=fixture(async url=>{if(url.endsWith('/csrf')){if(++reads===2)f.map.set('fitcore:cookie-transition','new-owner');return response(proof(),200);}return response();});
 await assert.rejects(()=>f.session.cookieAccountRequest({url:'/browser/forgot-password',method:'POST'}),e=>e.code==='COOKIE_UNCERTAIN');assert.equal(f.map.get('fitcore:cookie-transition'),'new-owner');
});
for(const current of [{currentContext:false},{contextRevision:'6',familyRevision:'7'},{transitionState:'PENDING',pendingKind:'LOGIN'}])test(`receipt never sends from insufficient context ${JSON.stringify(current)}`,async()=>{
 const calls=[];const f=fixture(async url=>{calls.push(url);return response(proof(current),200);});
 await assert.rejects(()=>f.session.cookieAccountRequest({url:'/browser/forgot-password',method:'POST'}),e=>e.code==='COOKIE_UNCERTAIN');assert.deepEqual(calls,['/api/browser/csrf']);
});
