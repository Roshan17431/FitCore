import test from 'node:test';
import assert from 'node:assert/strict';
import {createCookieSession} from '../src/services/browser-session.js';
const MARKER='fitcore:cookie-transition', REVISION='fitcore:cookie-revision';
const family='2b830b6d-8b6d-4f46-bcc3-39091199f323';
const user={id:'a',gymId:'gym-a',role:'OWNER',authzVersion:2};
const proof=(changes={})=>({csrfToken:'control-only',authenticated:false,transitionState:'IDLE',familyId:family,contextRevision:'7',familyRevision:'7',contextKind:'PREAUTH',currentContext:true,pendingKind:null,reauthenticationRequired:false,...changes});
const next=(changes={})=>proof({csrfToken:'next-control',authenticated:true,contextRevision:'8',familyRevision:'8',contextKind:'AUTH',...changes});
const response=(data,status=200,headers=new Headers())=>({ok:status>=200&&status<300,status,headers,text:async()=>JSON.stringify(data)});
const storage=()=>{const map=new Map();return{getItem:k=>map.get(k)??null,setItem:(k,v)=>map.set(k,String(v)),removeItem:k=>map.delete(k),map};};
const marker=(changes={})=>JSON.stringify({kind:'LOGIN',revision:'1',preFamilyId:family,preServerRevision:'7',...changes});
const fixture=(fetch,overrides={})=>{const env={storage:storage(),locks:{request:async(_n,_o,fn)=>fn()},fetch,timeoutMs:25,...overrides};return{env,session:createCookieSession(env)};};
const hold=f=>{f.env.storage.setItem(REVISION,'1');f.env.storage.setItem(MARKER,marker());};
const uncertain=e=>e.code==='COOKIE_UNCERTAIN';
const fresh=e=>e.code==='COOKIE_FRESH_CONTEXT_REQUIRED';

test('definitive unchanged wrong-password denial clears only its marker and permits an explicit retry',async()=>{
 let current=proof(), attempts=0;const calls=[];
 const f=fixture(async(url)=>{calls.push(url);if(url.endsWith('/csrf'))return response(current);if(url.endsWith('/login')){if(++attempts===1)return response({message:'INVALID_CREDENTIALS'},401);current=next();return response({...current,transitionState:'PENDING',pendingKind:'LOGIN',authenticated:false});}if(url.endsWith('/ack'))return response({acknowledged:true});if(url.endsWith('/me'))return response(user);throw Error(url);});
 await assert.rejects(f.session.browserLogin('account','wrong'),e=>e.response?.status===401&&e.cookieSessionUser===null);
 assert.equal(f.env.storage.getItem(MARKER),null);
 assert.deepEqual(await f.session.browserLogin('account','correct'),user);assert.equal(attempts,2);
 assert.ok(!JSON.stringify([...f.env.storage.map]).includes('control'));
});
for(const changed of [{csrfToken:'different'},{familyId:'8b7cbd5c-1059-41d8-9494-37b1bc457b02'},{contextRevision:'8',familyRevision:'8'},{authenticated:true,contextKind:'AUTH'},{transitionState:'PENDING',pendingKind:'LOGIN',authenticated:false}])test(`denial cannot clear changed proof ${JSON.stringify(changed)}`,async()=>{
 let reads=0;const f=fixture(async url=>url.endsWith('/csrf')?response(proof(++reads===1?{}:changed)):response({message:'INVALID_CREDENTIALS'},401));
 await assert.rejects(f.session.browserLogin('account','wrong'),uncertain);assert.ok(f.env.storage.getItem(MARKER));
});
for(const status of [429,500])test(`unknown ${status} response retains a bound marker without writer replay`,async()=>{
 let writes=0;const f=fixture(async url=>url.endsWith('/csrf')?response(proof()):(writes++,response({message:status===429?'UNKNOWN_RATE_DENIAL':'INVALID_CREDENTIALS'},status)));
 await assert.rejects(f.session.browserLogin('a','pw'),uncertain);assert.equal(writes,1);assert.deepEqual(JSON.parse(f.env.storage.getItem(MARKER)),{kind:'LOGIN',revision:'1',preFamilyId:family,preServerRevision:'7'});
});
test('wrong account switch restores only a newly confirmed previous identity',async()=>{
 const current=proof({authenticated:true,contextKind:'AUTH'});const f=fixture(async url=>url.endsWith('/csrf')?response(current):url.endsWith('/me')?response(user):response({message:'INVALID_CREDENTIALS'},401));
 await assert.rejects(f.session.browserLogin('other','wrong'),e=>e.response?.status===401&&e.cookieSessionUser?.id==='a');assert.equal(f.env.storage.getItem(MARKER),null);
});
for(const state of ['PENDING','IDLE'])test(`exact next-revision ${state} received-cookie recovery confirms fresh identity`,async()=>{
 let acked=state==='IDLE';const calls=[];const f=fixture(async url=>{calls.push(url);if(url.endsWith('/csrf'))return response(next({transitionState:acked?'IDLE':'PENDING',pendingKind:acked?null:'LOGIN',authenticated:acked}));if(url.endsWith('/ack')){acked=true;return response({acknowledged:true});}if(url.endsWith('/me'))return response(user);throw Error(url);});hold(f);
 assert.deepEqual(await f.session.browserRecovery(),user);assert.equal(f.env.storage.getItem(MARKER),null);assert.equal(calls.filter(x=>x.endsWith('/ack')).length,state==='PENDING'?1:0);assert.ok(!calls.some(x=>x.endsWith('/login')||x.endsWith('/bootstrap')));
});
for(const mismatch of [{contextRevision:'7',familyRevision:'7'},{contextRevision:'9',familyRevision:'9'},{familyId:'8b7cbd5c-1059-41d8-9494-37b1bc457b02'},{contextRevision:'7',familyRevision:'8',transitionState:'PENDING',pendingKind:'LOGIN',authenticated:false,currentContext:false},{contextKind:'PREAUTH',authenticated:false},{pendingKind:'LOGOUT',transitionState:'PENDING',authenticated:false},{reauthenticationRequired:true,authenticated:false},{currentContext:false}])test(`recovery fails closed for uncorrelated context ${JSON.stringify(mismatch.pendingKind==='LOGOUT'?{pendingKind:'LOGOUT',transitionState:'PENDING'}:mismatch)}`,async()=>{
 const calls=[];const f=fixture(async url=>{calls.push(url);return response(next(mismatch));});hold(f);await assert.rejects(f.session.browserRecovery(),fresh);assert.equal(f.env.storage.getItem(MARKER),marker());assert.deepEqual(calls,['/api/browser/csrf']);
});
for(const old of [JSON.stringify({kind:'LOGIN',revision:'1'}),marker({kind:'BOOTSTRAP'}),marker({preServerRevision:'9223372036854775808'}),'{broken'])test('old or unbound markers are never guessed or upgraded',async()=>{
 let calls=0;const f=fixture(async()=>{calls++;return response(next());});hold(f);f.env.storage.setItem(MARKER,old);await assert.rejects(f.session.browserRecovery(),fresh);assert.equal(f.env.storage.getItem(MARKER),old);assert.equal(calls,0);
});
test('recovery checks context again after me and retains marker if raw family changes',async()=>{
 let reads=0;const f=fixture(async url=>url.endsWith('/me')?response(user):response(next(++reads>1?{familyId:'8b7cbd5c-1059-41d8-9494-37b1bc457b02'}:{})));hold(f);await assert.rejects(f.session.browserRecovery(),uncertain);assert.equal(f.env.storage.getItem(MARKER),marker());
});
test('recovery never clears a newer local marker even if old me resolves successfully',async()=>{
 let release;const f=fixture(async url=>url.endsWith('/me')?new Promise(resolve=>{release=()=>resolve(response(user));}):response(next()));hold(f);const recovering=f.session.browserRecovery();while(!release)await new Promise(r=>setImmediate(r));f.env.storage.setItem(MARKER,marker({revision:'2'}));f.env.storage.setItem(REVISION,'2');release();await assert.rejects(recovering,uncertain);assert.equal(f.env.storage.getItem(MARKER),marker({revision:'2'}));
});
test('timed out response cannot ACK, install identity or remove its bound marker later',async()=>{
 let release;const calls=[];const f=fixture(async url=>{calls.push(url);if(url.endsWith('/csrf'))return response(proof());return new Promise(resolve=>{release=()=>resolve(response(next({transitionState:'PENDING',pendingKind:'LOGIN',authenticated:false})));});},{timeoutMs:3});await assert.rejects(f.session.browserLogin('a','pw'),uncertain);const own=f.env.storage.getItem(MARKER);release();await new Promise(r=>setImmediate(r));assert.equal(f.env.storage.getItem(MARKER),own);assert.deepEqual(calls,['/api/browser/csrf','/api/browser/login']);
});
for(const invalid of [{contextRevision:9007199254740992},{familyRevision:'9223372036854775808'},{csrfToken:'x'.repeat(4097)},{familyId:'not-a-family'},{pendingKind:'UNKNOWN'},{contextRevision:'07'}])test(`invalid or oversized metadata blocks writer ${Object.keys(invalid)[0]}`,async()=>{
 let writes=0;const f=fixture(async url=>url.endsWith('/csrf')?response(proof(invalid)):(writes++,response({})));await assert.rejects(f.session.browserLogin('a','pw'),uncertain);assert.equal(writes,0);
});
test('expired or revoked current AUTH requires explicit logout and ACK before fresh credentials',async()=>{
 let current=proof({contextKind:'AUTH',reauthenticationRequired:true}),writes=[];const f=fixture(async url=>{if(url.endsWith('/csrf'))return response(current);if(url.endsWith('/logout')){writes.push('logout');current=next({authenticated:false,contextKind:'PREAUTH'});return response({...current,transitionState:'PENDING',pendingKind:'LOGOUT'});}if(url.endsWith('/login')){writes.push('login');current=next({contextRevision:'9',familyRevision:'9'});return response({...current,transitionState:'PENDING',pendingKind:'LOGIN',authenticated:false});}if(url.endsWith('/ack')){writes.push('ack');return response({acknowledged:true});}return response(user);});
 await assert.rejects(f.session.browserLogin('a','pw'),e=>e.code==='COOKIE_REAUTH_REQUIRED');assert.deepEqual(writes,[]);await f.session.browserLogout();assert.deepEqual(await f.session.browserLogin('a','pw'),user);assert.deepEqual(writes,['logout','ack','login','ack']);
});
test('queued lock is aborted within its bound and a late grant never runs protected work',async()=>{
 let grant,signal,calls=0;const f=fixture(async()=>{calls++;return response(proof());},{lockTimeoutMs:3,locks:{request:(_n,options,fn)=>{signal=options.signal;return new Promise(resolve=>{grant=()=>Promise.resolve(fn()).then(resolve,resolve);});}}});
 await assert.rejects(f.session.browserMe(),e=>e.code==='COOKIE_LOCK_TIMEOUT');assert.equal(signal.aborted,true);await grant();assert.equal(calls,0);assert.equal(f.env.storage.getItem(MARKER),null);
});
test('queued acquisition bound never aborts an already acquired lock',async()=>{
 let signal;const f=fixture(async()=>{await new Promise(r=>setTimeout(r,8));return response(proof());},{timeoutMs:40,lockTimeoutMs:2,locks:{request:async(_n,options,fn)=>{signal=options.signal;return fn();}}});
 assert.equal(await f.session.browserMe(),null);assert.equal(signal.aborted,false);
});
test('canonical long revision strings recover without rounding',async()=>{
 const f=fixture(async url=>url.endsWith('/me')?response(user):response(next({contextRevision:'9007199254740993',familyRevision:'9007199254740993'})));hold(f);f.env.storage.setItem(MARKER,marker({preServerRevision:'9007199254740992'}));assert.deepEqual(await f.session.browserRecovery(),user);assert.equal(f.env.storage.getItem(MARKER),null);
});
test('logout recovery confirms anonymous next PREAUTH and ACKs only LOGOUT',async()=>{
 let acked=false;const f=fixture(async url=>{if(url.endsWith('/ack')){acked=true;return response({acknowledged:true});}assert.ok(url.endsWith('/csrf'));return response(next({authenticated:false,contextKind:'PREAUTH',transitionState:acked?'IDLE':'PENDING',pendingKind:acked?null:'LOGOUT'}));});hold(f);f.env.storage.setItem(MARKER,marker({kind:'LOGOUT'}));assert.equal(await f.session.browserRecovery(),null);assert.equal(f.env.storage.getItem(MARKER),null);
});
test('denial carrying a cookie header is never treated as a safe retry',async()=>{
 const f=fixture(async url=>url.endsWith('/csrf')?response(proof()):response({message:'INVALID_CREDENTIALS'},401,new Headers({'set-cookie':'unexpected=opaque'})));await assert.rejects(f.session.browserLogin('a','pw'),uncertain);assert.ok(f.env.storage.getItem(MARKER));
});
test('native abort rejection is normalized as queued lock timeout without modifying its marker',async()=>{
 const f=fixture(async()=>{throw Error('must not run');},{lockTimeoutMs:2,locks:{request:(_n,{signal})=>new Promise((_,reject)=>signal.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError'))))}});hold(f);await assert.rejects(f.session.browserRecovery(),e=>e.code==='COOKIE_LOCK_TIMEOUT');assert.equal(f.env.storage.getItem(MARKER),marker());
});
test('a no-marker explicit status check can require sign-out of an expired current source',async()=>{
 const f=fixture(async()=>response(proof({contextKind:'AUTH',reauthenticationRequired:true})));await assert.rejects(f.session.browserRecovery(),e=>e.code==='COOKIE_REAUTH_REQUIRED');assert.equal(f.env.storage.getItem(MARKER),null);
});
test('denial cannot remove marker if previous-account me is no longer authoritative',async()=>{
 const f=fixture(async url=>url.endsWith('/csrf')?response(proof({authenticated:true,contextKind:'AUTH'})):url.endsWith('/me')?response({message:'Unauthorized'},401):response({message:'INVALID_CREDENTIALS'},401));await assert.rejects(f.session.browserLogin('b','wrong'),uncertain);assert.ok(f.env.storage.getItem(MARKER));
});

test('lost or expired current cookie cannot bootstrap during explicit recovery',async()=>{
 const calls=[];const f=fixture(async url=>{calls.push(url);return response({message:'Unauthorized'},401);});hold(f);await assert.rejects(f.session.browserRecovery(),fresh);assert.equal(f.env.storage.getItem(MARKER),marker());assert.deepEqual(calls,['/api/browser/csrf']);
});
test('expired current PREAUTH requires explicit logout and ACK before a credential login',async()=>{
 let current=proof({reauthenticationRequired:true}),writes=[];
 const f=fixture(async url=>{
  if(url.endsWith('/csrf'))return response(current);
  if(url.endsWith('/logout')){writes.push('logout');current=next({authenticated:false,contextKind:'PREAUTH'});return response({...current,transitionState:'PENDING',pendingKind:'LOGOUT'});}
  if(url.endsWith('/login')){writes.push('login');current=next({contextRevision:'9',familyRevision:'9'});return response({...current,transitionState:'PENDING',pendingKind:'LOGIN',authenticated:false});}
  if(url.endsWith('/ack')){writes.push('ack');return response({acknowledged:true});}
  if(url.endsWith('/me'))return response(user);
  throw Error(url);
 });
 await assert.rejects(f.session.browserMe(),e=>e.code==='COOKIE_REAUTH_REQUIRED');
 await assert.rejects(f.session.browserLogin('a','pw'),e=>e.code==='COOKIE_REAUTH_REQUIRED');
 assert.deepEqual(writes,[]);assert.equal(f.env.storage.getItem(MARKER),null);
 await f.session.browserLogout();assert.deepEqual(await f.session.browserLogin('a','pw'),user);
 assert.deepEqual(writes,['logout','ack','login','ack']);
});

test('controlled gym denial with unchanged proof releases only its marker and allows an explicit retry',async()=>{
 let current=proof(),attempts=0;
 const f=fixture(async url=>{
  if(url.endsWith('/csrf'))return response(current);
  if(url.endsWith('/login')){
   if(++attempts===1)return response({message:'GYM_UNAVAILABLE'},403);
   current=next();return response({...current,transitionState:'PENDING',pendingKind:'LOGIN',authenticated:false});
  }
  if(url.endsWith('/ack'))return response({acknowledged:true});
  if(url.endsWith('/me'))return response(user);
  throw Error(url);
 });
 await assert.rejects(f.session.browserLogin('account','pw'),e=>e.response?.status===403&&e.response.data.message==='GYM_UNAVAILABLE'&&e.cookieDefinitiveDenial===true&&e.cookieSessionUser===null);
 assert.equal(f.env.storage.getItem(MARKER),null);assert.equal(attempts,1);
 assert.deepEqual(await f.session.browserLogin('account','pw'),user);assert.equal(attempts,2);
});

test('controlled gym denial with changed proof remains held without confirming identity',async()=>{
 let reads=0;const calls=[];
 const f=fixture(async url=>{calls.push(url);if(url.endsWith('/csrf'))return response(proof(++reads===1?{}:{csrfToken:'changed-control'}));if(url.endsWith('/login'))return response({message:'GYM_UNAVAILABLE'},403);throw Error(url);});
 await assert.rejects(f.session.browserLogin('account','pw'),uncertain);
 assert.equal(f.env.storage.getItem(MARKER),marker());
 assert.deepEqual(calls,['/api/browser/csrf','/api/browser/login','/api/browser/csrf']);
});

test('denial reconciliation preserves a replacement marker arriving with the post-denial status',async()=>{
 let reads=0;const events=[],calls=[];
 const newer=marker({revision:'2',kind:'LOGOUT',preServerRevision:'8'});
 const f=fixture(async url=>{
  calls.push(url);
  if(url.endsWith('/csrf')){
   if(++reads===2){f.env.storage.setItem(MARKER,newer);f.env.storage.setItem(REVISION,'2');}
   return response(proof({authenticated:true,contextKind:'AUTH'}));
  }
  if(url.endsWith('/login'))return response({message:'INVALID_CREDENTIALS'},401);
  throw Error(url);
 });
 f.session.subscribeSessionChange(e=>events.push(e.state));
 await assert.rejects(f.session.browserLogin('other','wrong'),uncertain);
 assert.equal(f.env.storage.getItem(MARKER),newer);assert.equal(f.env.storage.getItem(REVISION),'2');
 assert.ok(!events.includes('IDLE'));assert.ok(!calls.includes('/api/auth/me'));
 await assert.rejects(f.session.cookieApiRequest({url:'/gym'}),uncertain);assert.ok(!calls.includes('/api/gym'));
});

test('received-cookie recovery preserves a replacement marker during post-ACK status validation',async()=>{
 let acked=false;const events=[],calls=[];
 const newer=marker({revision:'2',kind:'LOGOUT',preServerRevision:'8'});
 const f=fixture(async url=>{
  calls.push(url);
  if(url.endsWith('/ack')){acked=true;return response({acknowledged:true});}
  if(url.endsWith('/csrf')){
   if(acked){f.env.storage.setItem(MARKER,newer);f.env.storage.setItem(REVISION,'2');}
   return response(next({authenticated:acked,transitionState:acked?'IDLE':'PENDING',pendingKind:acked?null:'LOGIN'}));
  }
  throw Error(url);
 });
 hold(f);f.session.subscribeSessionChange(e=>events.push(e.state));
 await assert.rejects(f.session.browserRecovery(),uncertain);
 assert.equal(f.env.storage.getItem(MARKER),newer);assert.equal(f.env.storage.getItem(REVISION),'2');
 assert.deepEqual(calls,['/api/browser/csrf','/api/browser/ack','/api/browser/csrf']);assert.ok(!events.includes('IDLE'));
 await assert.rejects(f.session.cookieApiRequest({url:'/gym'}),uncertain);assert.ok(!calls.includes('/api/gym'));
});

test('final ownership check rejects a new marker after fresh identity proof without releasing it',async()=>{
 const backing=storage(),events=[],calls=[];let reads=0,armed=false,ownershipReads=0;
 const newer=marker({revision:'2',kind:'LOGOUT',preServerRevision:'8'});
 const guarded={...backing,getItem:key=>{
  // The first read accepts confirm()'s final ownership check. The second is
  // clearOwned()'s independent check, where another context has taken ownership.
  if(armed&&key===MARKER&&++ownershipReads===2){backing.setItem(MARKER,newer);backing.setItem(REVISION,'2');}
  return backing.getItem(key);
 }};
 const f=fixture(async url=>{
  calls.push(url);
  if(url.endsWith('/csrf')){if(++reads===2)armed=true;return response(next());}
  if(url.endsWith('/me'))return response(user);
  throw Error(url);
 },{storage:guarded});
 hold(f);f.session.subscribeSessionChange(e=>events.push(e.state));
 await assert.rejects(f.session.browserRecovery(),uncertain);
 assert.equal(backing.getItem(MARKER),newer);assert.equal(backing.getItem(REVISION),'2');assert.ok(!events.includes('IDLE'));
 assert.deepEqual(calls,['/api/browser/csrf','/api/auth/me','/api/browser/csrf']);
 await assert.rejects(f.session.cookieApiRequest({url:'/gym'}),uncertain);assert.ok(!calls.includes('/api/gym'));
});

test('storage read failure after denial status keeps its marker and never confirms previous identity',async()=>{
 const backing=storage(),events=[],calls=[];let reads=0,failNextMarkerRead=false;
 const guarded={...backing,getItem:key=>{
  if(key===MARKER&&failNextMarkerRead){failNextMarkerRead=false;throw Error('storage unavailable');}
  return backing.getItem(key);
 }};
 const f=fixture(async url=>{
  calls.push(url);
  if(url.endsWith('/csrf')){if(++reads===2)failNextMarkerRead=true;return response(proof({authenticated:true,contextKind:'AUTH'}));}
  if(url.endsWith('/login'))return response({message:'INVALID_CREDENTIALS'},401);
  throw Error(url);
 },{storage:guarded});
 f.session.subscribeSessionChange(e=>events.push(e.state));
 await assert.rejects(f.session.browserLogin('other','wrong'),uncertain);
 assert.equal(backing.getItem(MARKER),marker());assert.equal(backing.getItem(REVISION),'1');assert.ok(!events.includes('IDLE'));
 assert.deepEqual(calls,['/api/browser/csrf','/api/browser/login','/api/browser/csrf']);
 await assert.rejects(f.session.cookieApiRequest({url:'/gym'}),uncertain);assert.ok(!calls.includes('/api/gym'));
});


test('complete controlled rate denial confirms fresh prior identity and permits only a new explicit login',async()=>{
 let current=proof({authenticated:true,contextKind:'AUTH'}),attempts=0,identityReads=0;
 const f=fixture(async url=>{
  if(url.endsWith('/csrf'))return response(current);
  if(url.endsWith('/login')){
   if(++attempts===1)return response({message:'RATE_LIMITED'},429);
   current=next();return response({...current,transitionState:'PENDING',pendingKind:'LOGIN',authenticated:false});
  }
  if(url.endsWith('/ack'))return response({acknowledged:true});
  if(url.endsWith('/me')){identityReads++;return response(user);}
  throw Error(url);
 });
 await assert.rejects(f.session.browserLogin('account','pw'),e=>e.response?.status===429&&e.response.data.message==='RATE_LIMITED'&&e.cookieDefinitiveDenial===true&&e.cookieSessionUser?.id==='a');
 assert.equal(f.env.storage.getItem(MARKER),null);assert.equal(identityReads,1);assert.equal(attempts,1);
 await new Promise(resolve=>setImmediate(resolve));assert.equal(attempts,1,'reconciliation must never schedule a writer replay');
 assert.deepEqual(await f.session.browserLogin('account','pw'),user);assert.equal(attempts,2);
});

test('controlled rate denial with changed CSRF proof remains held without replay or accepted identity',async()=>{
 let reads=0;const calls=[];
 const f=fixture(async url=>{calls.push(url);if(url.endsWith('/csrf'))return response(proof(++reads===1?{}:{csrfToken:'different-control'}));if(url.endsWith('/login'))return response({message:'RATE_LIMITED'},429);throw Error(url);});
 await assert.rejects(f.session.browserLogin('account','pw'),uncertain);
 assert.equal(f.env.storage.getItem(MARKER),marker());assert.deepEqual(calls,['/api/browser/csrf','/api/browser/login','/api/browser/csrf']);
});

test('incomplete controlled rate denial body cannot release the marker or replay login',async()=>{
 const calls=[];
 const f=fixture(async url=>{
  calls.push(url);if(url.endsWith('/csrf'))return response(proof());
  if(url.endsWith('/login'))return{...response({message:'RATE_LIMITED'},429),text:async()=>{throw Error('body delivery interrupted');}};
  throw Error(url);
 });
 await assert.rejects(f.session.browserLogin('account','pw'),uncertain);
 assert.equal(f.env.storage.getItem(MARKER),marker());assert.deepEqual(calls,['/api/browser/csrf','/api/browser/login']);
});

test('a pre-lock storage capability read cannot overwrite another client next-revision marker and strand recovery',async()=>{
 const {Worker}=await import('node:worker_threads');
 // A real second JS agent pauses its synchronous storage read between reading N
 // and returning it. The lock holder then performs the actual LOGIN marker write.
 const shared=new SharedArrayBuffer(8192),words=new Int32Array(shared,0,8),bytes=new Uint8Array(shared,32);
 const ancillary=new Map();
 const backing={
  getItem:key=>key===REVISION?(Atomics.load(words,0)?String(Atomics.load(words,0)):null):key===MARKER?(Atomics.load(words,1)?new TextDecoder().decode(bytes.slice(0,Atomics.load(words,1))):null):(ancillary.get(key)??null),
  setItem:(key,value)=>{ancillary.set(key,String(value));if(key===REVISION)Atomics.store(words,0,Number(value));if(key===MARKER){const encoded=new TextEncoder().encode(value);bytes.set(encoded);Atomics.store(words,1,encoded.length);}},
  removeItem:key=>{ancillary.delete(key);if(key===MARKER)Atomics.store(words,1,0);},
 };
 const moduleUrl=new URL('../src/services/browser-session.js',import.meta.url).href;
 const worker=new Worker(`const {workerData,parentPort}=require('node:worker_threads');(async()=>{
  const {createCookieSession}=await import(workerData.moduleUrl),w=new Int32Array(workerData.shared,0,8),b=new Uint8Array(workerData.shared,32);let paused=false;const other=new Map();
  const storage={getItem(key){if(key==='fitcore:cookie-revision'){const n=Atomics.load(w,0);if(!paused){paused=true;Atomics.store(w,2,1);Atomics.notify(w,2);Atomics.wait(w,3,0);}return n?String(n):null;}if(key==='fitcore:cookie-transition'){const n=Atomics.load(w,1);return n?new TextDecoder().decode(b.slice(0,n)):null;}return other.get(key)??null;},setItem(key,value){other.set(key,String(value));if(key==='fitcore:cookie-revision')Atomics.store(w,0,Number(value));},removeItem(key){other.delete(key);}};
  const client=createCookieSession({storage,locks:{request:async(_n,_o,fn)=>fn()},fetch:async()=>{throw Error('no proof request allowed from blocked reader');}});
  try{await client.browserMe();parentPort.postMessage('unexpected-success');}catch(error){parentPort.postMessage(error.code);}
 })().catch(error=>{throw error;});`,{eval:true,workerData:{shared,moduleUrl}});
 const finished=new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});
 try{
  for(let n=0;!Atomics.load(words,2)&&n<200;n++)await new Promise(resolve=>setTimeout(resolve,5));
  assert.equal(Atomics.load(words,2),1,'reader captured revision N before lock acquisition');
  let recovering=false,proofReads=0;
  const f=fixture(async url=>{if(url.endsWith('/csrf')){proofReads++;return response(recovering?next():proof());}if(url.endsWith('/login'))throw Error('lost response after next cookie was received');if(url.endsWith('/me'))return response(user);throw Error(url);},{storage:backing});
  await assert.rejects(f.session.browserLogin('a','pw'),uncertain);
  const owned=backing.getItem(MARKER);assert.equal(JSON.parse(owned).revision,'1');
  Atomics.store(words,3,1);Atomics.notify(words,3);
  assert.equal(await finished,'COOKIE_UNCERTAIN');
  assert.equal(backing.getItem(REVISION),'1','reader must not restore its stale revision N');
  assert.equal(backing.getItem(MARKER),owned);
  recovering=true;assert.deepEqual(await f.session.browserRecovery(),user);
  assert.equal(backing.getItem(MARKER),null);assert.ok(proofReads>=3,'valid subsequent recovery actually reads proof');
 }finally{Atomics.store(words,3,1);Atomics.notify(words,3);await worker.terminate();}
});

test('unwritable storage fails closed inside the lock before any proof or writer request',async()=>{
 let calls=0,inside=false,writeInside;
 const f=fixture(async()=>{calls++;return response(proof());},{storage:{getItem:()=>null,setItem:()=>{writeInside=inside;throw Error('write unavailable');},removeItem:()=>{}},locks:{request:async(_n,_o,fn)=>{inside=true;try{return await fn();}finally{inside=false;}}}});
 await assert.rejects(f.session.browserLogin('a','pw'),e=>e.code==='COOKIE_UNSUPPORTED');assert.equal(writeInside,true);assert.equal(calls,0);
});

test('transient status network failure stays retryable without weakening the original fence',async()=>{
 let online=false;const f=fixture(async url=>{if(!online)throw Error('offline');return url.endsWith('/me')?response(user):response(next());});hold(f);
 await assert.rejects(f.session.browserRecovery(),uncertain);assert.equal(f.env.storage.getItem(MARKER),marker());
 online=true;assert.deepEqual(await f.session.browserRecovery(),user);assert.equal(f.env.storage.getItem(MARKER),null);
});
test('fresh-context requirement never bootstraps or releases an old revision and can still accept later exact N+1 proof',async()=>{
 let delivered=false;const calls=[];const f=fixture(async url=>{calls.push(url);return url.endsWith('/me')?response(user):response(delivered?next():proof());});hold(f);
 await assert.rejects(f.session.browserRecovery(),fresh);assert.equal(f.env.storage.getItem(MARKER),marker());
 await assert.rejects(f.session.browserLogin('a','pw'),uncertain);assert.deepEqual(calls,['/api/browser/csrf']);
 delivered=true;assert.deepEqual(await f.session.browserRecovery(),user);assert.equal(f.env.storage.getItem(MARKER),null);
});
