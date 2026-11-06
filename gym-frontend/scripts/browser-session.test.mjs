import test from 'node:test';
import assert from 'node:assert/strict';
import {getEventListeners} from 'node:events';
// Exercise the adapter; only the browser/network boundary is simulated.
const moduleUrl = new URL('../src/services/browser-session.js', import.meta.url);
const load = async () => { try { return await import(moduleUrl); } catch (e) { if (e.code === 'ERR_MODULE_NOT_FOUND') return {}; throw e; } };
const storage = () => { const data = new Map(); return { getItem: k => data.get(k) ?? null, setItem: (k,v) => data.set(k,String(v)), removeItem: k => data.delete(k), data }; };
const locks = () => { let tail = Promise.resolve(); return { request: (_name,_options,fn) => { const next = tail.then(fn); tail = next.catch(() => {}); return next; } }; };
const response = (data,status=200) => ({ok:status>=200&&status<300,status,headers:new Headers(),text:async()=>JSON.stringify(data)});
const idle = {csrfToken:'csrf-memory',authenticated:false,transitionState:'IDLE'};
// Existing scenarios use a stateful fake server for the frozen metadata/ACK wire.
// New adversarial metadata tests use unmodified complete responses in browser-recovery.test.mjs.
const protocol = fetch => {
 let rev=0,latest;
 return async (url,config) => {
  const result=await fetch(url,config), data=JSON.parse(await result.text());
  if(data.transitionState){
   const pending=data.transitionState==='PENDING';
   if(pending)rev++;
   const kind=pending?(url.endsWith('/logout')?'LOGOUT':'LOGIN'):null;
   const authenticated=pending?false:data.authenticated;
   const full={familyId:'2b830b6d-8b6d-4f46-bcc3-39091199f323',contextRevision:String(rev),familyRevision:String(rev),contextKind:(pending?kind==='LOGIN':authenticated)?'AUTH':'PREAUTH',currentContext:true,pendingKind:kind,reauthenticationRequired:false,...data,authenticated};
   if(pending)latest=full;
   else if(latest&&data.transitionState==='IDLE')Object.assign(full,{csrfToken:latest.csrfToken,authenticated:latest.contextKind==='AUTH',contextKind:latest.contextKind});
   return response(full,result.status);
  }
  return response(data,result.status);
 };
};
const user = {id:'user-a',gymId:'gym-a',role:'OWNER',authzVersion:4};
const fixture = async (fetch, overrides={}) => { const {createCookieSession} = await load(); assert.equal(typeof createCookieSession,'function','cookie session coordinator must exist'); const env = {storage:storage(),locks:locks(),fetch:protocol(fetch),timeoutMs:50,...overrides}; return {env, session:createCookieSession(env)}; };

test('cookie transport fails closed when Web Locks are unsupported',async()=>{
 for (const overrides of [{locks:null},{storage:null},{storage:{getItem(){throw Error('denied');}}}]) {
  let calls=0; const {session}=await fixture(async()=>{calls++;return response(idle);},overrides);
  await assert.rejects(session.browserLogin('x','secret'),e=>e.code==='COOKIE_UNSUPPORTED');assert.equal(calls,0);
 }
});
test('login bootstraps under marker, ACKs delivery, then reads fresh me without exposing login identity or tokens',async()=>{
 const calls=[];let env, bootstrapped=false;
 const fixtureResult=await fixture(async(url,config)=>{
  calls.push([url,config]);
  if(url==='/api/browser/csrf')return bootstrapped?response(idle):response({},401);
  if(url==='/api/browser/bootstrap'){bootstrapped=true;assert.ok(env.storage.getItem('fitcore:cookie-transition'));return response(idle);}
  if(url==='/api/browser/login')return response({user:{...user,role:'ADMIN'},csrfToken:'new-csrf',transitionState:'PENDING'});
  if(url==='/api/browser/ack'){assert.equal(config.headers['X-CSRF-TOKEN'],'new-csrf');return response({acknowledged:true});}
  if(url==='/api/auth/me')return response(user);
  throw Error(url);
 });env=fixtureResult.env;
 assert.deepEqual(await fixtureResult.session.browserLogin('owner@fixture.invalid','secret'),user);
 assert.deepEqual(calls.map(c=>c[0]),['/api/browser/csrf','/api/browser/bootstrap','/api/browser/login','/api/browser/ack','/api/browser/csrf','/api/auth/me','/api/browser/csrf']);
 for(const [,c] of calls){assert.equal(c.credentials,'same-origin');assert.equal(c.headers.Authorization,undefined);}
 assert.equal(env.storage.getItem('fitcore:cookie-transition'),null);
 const persisted=JSON.stringify([...env.storage.data]);assert.ok(!persisted.includes('csrf')&&!persisted.includes('secret')&&!persisted.includes('owner@'));
});
test('logout ACK uses fresh preauth CSRF and never clears uncertainty before ACK succeeds',async()=>{
 let env;const urls=[];
 const f=await fixture(async(url,c)=>{urls.push(url);if(url.endsWith('/csrf'))return response({...idle,authenticated:true});if(url.endsWith('/logout'))return response({csrfToken:'preauth-csrf',transitionState:'PENDING'});assert.ok(env.storage.getItem('fitcore:cookie-transition'));assert.equal(c.headers['X-CSRF-TOKEN'],'preauth-csrf');return response({acknowledged:true});});env=f.env;
 await f.session.browserLogout();assert.deepEqual(urls,['/api/browser/csrf','/api/browser/logout','/api/browser/ack','/api/browser/csrf','/api/browser/csrf']);assert.equal(env.storage.getItem('fitcore:cookie-transition'),null);
});
test('live tabs serialize cookie transitions including ACK instead of rejecting the active marker',async()=>{
 const shared={storage:storage(),locks:locks()};const calls=[];let release,started;
 const sent=new Promise(r=>{started=r;});let n=0;
 const fetch=async url=>{calls.push(url);if(url.endsWith('/csrf'))return response(idle);if(url.endsWith('/login')){if(++n===1){started();await new Promise(r=>{release=r;});}return response({csrfToken:'next',transitionState:'PENDING'});}if(url.endsWith('/ack'))return response({acknowledged:true});return response(user);};
 const a=await fixture(fetch,shared),b=await fixture(fetch,shared);
 const first=a.session.browserLogin('a','secret');await sent;const second=b.session.browserLogin('b','secret');await new Promise(r=>setImmediate(r));assert.equal(n,1);release();await Promise.all([first,second]);assert.equal(n,2);assert.equal(calls.filter(u=>u.endsWith('/ack')).length,2);
});
test('uncertain cookie delivery persists across client recreation without retry',async()=>{
 let calls=0,release;const shared={storage:storage(),locks:locks()};
 const f=await fixture(async url=>{calls++;if(url.endsWith('/csrf'))return response(idle);return new Promise(r=>{release=()=>r(response({csrfToken:'late',transitionState:'PENDING'}));});},{...shared,timeoutMs:5});
 await assert.rejects(f.session.browserLogin('a','secret'),e=>e.code==='COOKIE_UNCERTAIN');assert.ok(shared.storage.getItem('fitcore:cookie-transition'));
 release();await new Promise(r=>setImmediate(r));
 const b=await fixture(async()=>{calls++;return response(idle);},shared);
 await assert.rejects(b.session.browserLogin('b','secret'),e=>e.code==='COOKIE_UNCERTAIN');await assert.rejects(b.session.browserMe(),e=>e.code==='COOKIE_UNCERTAIN');assert.equal(calls,2);
});
test('backend PENDING blocks bootstrap, ACK recovery, writers and business calls without retry',async()=>{
 const calls=[];const f=await fixture(async url=>{calls.push(url);return response({...idle,transitionState:'PENDING'});});
 await assert.rejects(f.session.browserMe(),e=>e.code==='COOKIE_UNCERTAIN');await assert.rejects(f.session.browserLogout(),e=>e.code==='COOKIE_UNCERTAIN');await assert.rejects(f.session.cookieApiRequest({url:'/gym'}),e=>e.code==='COOKIE_UNCERTAIN');assert.deepEqual(calls,['/api/browser/csrf']);
});
test('lost ACK response preserves marker; fresh me is never accepted',async()=>{
 const calls=[];const f=await fixture(async url=>{calls.push(url);if(url.endsWith('/csrf'))return response(idle);if(url.endsWith('/login'))return response({csrfToken:'new',transitionState:'PENDING'});throw Error('connection lost');});
 await assert.rejects(f.session.browserLogin('a','secret'),e=>e.code==='COOKIE_UNCERTAIN');assert.ok(f.env.storage.getItem('fitcore:cookie-transition'));assert.ok(!calls.includes('/api/auth/me'));
});
test('cookie API confines URLs, rejects direct auth writers and never forwards caller authorization',async()=>{
 const calls=[];const f=await fixture(async(url,c)=>{calls.push([url,c]);return response(url.endsWith('/csrf')?{...idle,authenticated:true}:url.endsWith('/me')?user:{name:'gym'});});
 for(const url of ['https://evil.invalid/api/gym','//evil.invalid/x','/browser/login','/auth/register','/auth/reset-password'])await assert.rejects(f.session.cookieApiRequest({url,method:'post'}));
 assert.equal(calls.length,0);
 await f.session.browserMe();
 const result=await f.session.cookieApiRequest({url:'/gym',method:'patch',data:'{"name":"new"}',params:{gymId:'forged'},headers:{Authorization:'Bearer stale','X-FitCore-Gateway':'forged'}});
 assert.deepEqual(result.data,{name:'gym'});const [url,c]=calls.find(([u])=>u.startsWith('/api/gym?'));assert.equal(url,'/api/gym?gymId=forged');assert.equal(c.headers.Authorization,undefined);assert.equal(c.headers['X-FitCore-Gateway'],undefined);assert.equal(c.headers['X-CSRF-TOKEN'],'csrf-memory');
});

test('out-of-band cookie identity changes discard old-tenant business requests',async()=>{
 let current=user;const calls=[],events=[];
 const f=await fixture(async url=>{calls.push(url);if(url.endsWith('/csrf'))return response({...idle,authenticated:true});if(url.endsWith('/me'))return response(current);return response({private:'old tenant'});});
 f.session.subscribeSessionChange(e=>events.push(e));assert.deepEqual(await f.session.browserMe(),user);
 current={...user,id:'user-b',gymId:'gym-b'};
 await assert.rejects(f.session.cookieApiRequest({url:'/gym'}),e=>e.code==='ERR_CANCELED');assert.ok(!calls.includes('/api/gym'));assert.ok(events.some(e=>e.state==='ENDED'));
});
test('session notifications invalidate listeners immediately and unsubscribe cleanly',async()=>{
 let channel;const events=[];const f=await fixture(async()=>response(idle),{channel:()=>channel={postMessage(){},close(){}}});
 const off=f.session.subscribeSessionChange(e=>events.push(e));channel.onmessage({data:{state:'TRANSITION'}});assert.equal(events[0].external,true);assert.equal(events[0].state,'TRANSITION');off();channel.onmessage({data:{state:'IDLE'}});assert.equal(events.length,1);
});

test('normalized and encoded dot paths cannot bypass guarded cookie writers',async()=>{
 let calls=0;const f=await fixture(async()=>{calls++;return response({...idle,authenticated:true});});
 for(const url of ['/gym/../browser/login','/gym/%2e%2e/browser/login','/auth/me?bypass=1','/gym//../browser/logout','/gym/%252e%252e/browser/login'])await assert.rejects(f.session.cookieApiRequest({url,method:'POST'}),e=>e.code==='COOKIE_ROUTE_DISABLED');
 assert.equal(calls,0);
});

test('queued old-view mutation cannot inherit a later login identity',async()=>{
 let current=user;const calls=[];
 const f=await fixture(async(url,c)=>{
  calls.push([url,c]);
  if(url.endsWith('/csrf'))return response({...idle,authenticated:true});
  if(url.endsWith('/login')){current={...user,id:'user-b',gymId:'gym-b'};return response({csrfToken:'b-csrf',transitionState:'PENDING'});}
  if(url.endsWith('/ack'))return response({acknowledged:true});
  if(url.endsWith('/me'))return response(current);
  return response({name:'B overwritten by A draft'});
 });
 await f.session.browserMe();
 const login=f.session.browserLogin('b@fixture.invalid','secret');
 const oldWrite=f.session.cookieApiRequest({url:'/gym',method:'PATCH',data:{name:'A draft'}});
 const rejected=assert.rejects(oldWrite,e=>e.code==='ERR_CANCELED');
 assert.equal((await login).gymId,'gym-b');await rejected;
 assert.equal(calls.filter(([url])=>url==='/api/gym').length,0);
});

test('queued old-view mutation cannot inherit a refreshed raw-cookie identity',async()=>{
 let current=user;const calls=[];
 const f=await fixture(async(url,c)=>{calls.push([url,c]);return response(url.endsWith('/csrf')?{...idle,authenticated:true}:url.endsWith('/me')?current:{name:'overwritten'});});
 await f.session.browserMe();current={...user,id:'user-b',gymId:'gym-b'};
 const restored=f.session.browserMe();
 const write=f.session.cookieApiRequest({url:'/gym',method:'PATCH',data:{name:'A draft'}});
 const rejected=assert.rejects(write,e=>e.code==='ERR_CANCELED');
 assert.equal((await restored).gymId,'gym-b');await rejected;assert.ok(!calls.some(([url])=>url==='/api/gym'));
});

for(const admission of ['suspended','preauth','unauthorized']) test(`${admission} context admission invalidates the established session before business work`,async()=>{
  let denied=false;const calls=[],events=[];
  const f=await fixture(async url=>{
   calls.push(url);
   if(url.endsWith('/csrf')){
    if(!denied)return response({...idle,authenticated:true});
    if(admission==='suspended')return response({message:'GYM_UNAVAILABLE'},403);
    if(admission==='unauthorized')return response({message:'SESSION_RETIRED'},401);
    return response(idle);
   }
   if(url.endsWith('/me'))return response(user);
   if(url.endsWith('/bootstrap'))return response(idle);
   return response({private:'old tenant'});
  });
  f.session.subscribeSessionChange(e=>events.push(e));await f.session.browserMe();denied=true;
  await assert.rejects(f.session.cookieApiRequest({url:'/gym'}));
  assert.ok(events.some(e=>e.state==='ENDED'),`${admission} must notify protected content/cache invalidation`);
  assert.ok(!calls.includes('/api/gym'));
  assert.ok(!calls.includes('/api/browser/bootstrap'),'business admission must not create a replacement context');
  denied=false;const before=calls.length;
  await assert.rejects(f.session.cookieApiRequest({url:'/gym'}),e=>e.code==='ERR_CANCELED');
  assert.equal(calls.length,before,'ended identity must stay cleared until authoritative restore');
});

const lifecycleFixture = async (overrides = {}) => {
 const {subscribeCookieLifecycle} = await load();
 assert.equal(typeof subscribeCookieLifecycle,'function','cookie foreground/restored-page lifecycle subscription must exist');
 const windowTarget=new EventTarget(),documentTarget=new EventTarget();documentTarget.visibilityState='visible';
 let displayed=user,caches=['protected A'],reads=0,release,writing=0;
 const options={windowTarget,documentTarget,canRestore:()=>writing===0&&displayed!==null,restore:()=>{
  displayed=null;caches=[];reads++;return new Promise(resolve=>{release=()=>{displayed={...user,id:'user-b',gymId:'gym-b'};resolve();};});
 },...overrides};
 const off=subscribeCookieLifecycle(options);
 return {windowTarget,documentTarget,off,state:()=>({displayed,caches,reads}),release:()=>release(),writer:value=>{writing=value;}};
};
const pageShow = persisted => Object.assign(new Event('pageshow'),{persisted});
const tick = () => new Promise(resolve=>setImmediate(resolve));
test('foreground events invalidate displayed identity and protected caches before one coalesced restore',async()=>{
 const f=await lifecycleFixture();
 f.documentTarget.visibilityState='hidden';f.documentTarget.dispatchEvent(new Event('visibilitychange'));assert.equal(f.state().reads,0);
 f.documentTarget.visibilityState='visible';f.documentTarget.dispatchEvent(new Event('visibilitychange'));
 assert.deepEqual(f.state(),{displayed:null,caches:[],reads:1});
 f.windowTarget.dispatchEvent(new Event('focus'));f.windowTarget.dispatchEvent(pageShow(true));assert.equal(f.state().reads,1);
 f.release();await tick();assert.equal(f.state().displayed.gymId,'gym-b');
 f.windowTarget.dispatchEvent(new Event('focus'));assert.equal(f.state().reads,2);f.release();f.off();
});
test('persisted pageshow revalidates accepted identity but ordinary pageshow does not',async()=>{
 const f=await lifecycleFixture();f.windowTarget.dispatchEvent(pageShow(false));assert.equal(f.state().reads,0);
 f.windowTarget.dispatchEvent(pageShow(true));assert.deepEqual(f.state(),{displayed:null,caches:[],reads:1});f.release();f.off();
});
test('foreground restoration skips own pending login and logout and unauthenticated views',async()=>{
 const f=await lifecycleFixture();f.writer(1);
 for(const event of [new Event('focus'),pageShow(true)])f.windowTarget.dispatchEvent(event);
 f.documentTarget.dispatchEvent(new Event('visibilitychange'));assert.equal(f.state().reads,0);assert.equal(f.state().displayed,user);
 f.writer(0);f.windowTarget.dispatchEvent(new Event('focus'));assert.equal(f.state().reads,1);
 f.windowTarget.dispatchEvent(new Event('focus'));assert.equal(f.state().reads,1);f.release();f.off();
 const loggedOut=await lifecycleFixture({canRestore:()=>false});loggedOut.windowTarget.dispatchEvent(pageShow(true));loggedOut.windowTarget.dispatchEvent(new Event('focus'));assert.equal(loggedOut.state().reads,0);loggedOut.off();
});
test('lifecycle unsubscribe removes foreground and persisted restoration listeners',async()=>{
 const f=await lifecycleFixture();
 assert.equal(getEventListeners(f.windowTarget,'focus').length+getEventListeners(f.windowTarget,'pageshow').length+getEventListeners(f.documentTarget,'visibilitychange').length,3);
 f.off();
 for(const [target,type] of [[f.windowTarget,'focus'],[f.windowTarget,'pageshow'],[f.documentTarget,'visibilitychange']])assert.equal(getEventListeners(target,type).length,0);
 f.windowTarget.dispatchEvent(new Event('focus'));f.windowTarget.dispatchEvent(pageShow(true));f.documentTarget.dispatchEvent(new Event('visibilitychange'));assert.equal(f.state().reads,0);
});
