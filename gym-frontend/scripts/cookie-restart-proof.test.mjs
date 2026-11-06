import test from 'node:test';
import assert from 'node:assert/strict';
import {restartProof} from './cookie-restart-proof.mjs';
const family='2b830b6d-8b6d-4f46-bcc3-39091199f323';
const marker={kind:'LOGIN',preFamilyId:family,preServerRevision:'7'};
const metadata={familyId:family,contextRevision:'8',familyRevision:'8',contextKind:'AUTH',currentContext:true,transitionState:'PENDING',pendingKind:'LOGIN',authenticated:false,reauthenticationRequired:false,csrfToken:'synthetic-control'};
const candidate=(changes={})=>({status:200,metadata,marker,cookiePresence:{auth:true,preauth:false},...changes});
test('native restart accepts only an actually received exact bound pending next-revision cookie',()=>{assert.equal(restartProof(candidate()),true);});
test('native restart proof loss requires exact context-required 401 and actually absent cookies',()=>{assert.equal(restartProof(candidate({status:401,metadata:{message:'BROWSER_CONTEXT_REQUIRED'},cookiePresence:{auth:false,preauth:false}})),false);});
for(const [label,input] of [
 ['503 with still present AUTH',candidate({status:503,metadata:{message:'UNAVAILABLE'}})],
 ['503 with no cookies',candidate({status:503,metadata:{message:'UNAVAILABLE'},cookiePresence:{auth:false,preauth:false}})],
 ['401 with still present AUTH',candidate({status:401,metadata:{message:'BROWSER_CONTEXT_REQUIRED'}})],
 ['unexpected 401 body',candidate({status:401,metadata:{message:'Unauthorized'},cookiePresence:{auth:false,preauth:false}})],
 ['other family',candidate({metadata:{...metadata,familyId:'8b7cbd5c-1059-41d8-9494-37b1bc457b02'}})],
 ['same revision',candidate({metadata:{...metadata,contextRevision:'7',familyRevision:'7'}})],
 ['unbound marker',candidate({marker:{kind:'LOGIN'}})],
 ['retired cookie',candidate({metadata:{...metadata,currentContext:false}})],
 ['missing AUTH for metadata',candidate({cookiePresence:{auth:false,preauth:false}})],
 ['wrong pending kind',candidate({metadata:{...metadata,pendingKind:'LOGOUT'}})],
])test('native restart rejects '+label,()=>assert.throws(()=>restartProof(input)));
