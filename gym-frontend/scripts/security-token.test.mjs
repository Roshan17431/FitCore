import test from 'node:test';
import assert from 'node:assert/strict';
import {captureSecurityToken,validateResetPassword} from '../src/utils/security-token.js';

test('security fragment capture is route-bound, scrubbed and memory-only',()=>{
 const token='x'.repeat(43);let clean;
 globalThis.window={location:{pathname:'/verify-email',hash:'#token='+token,search:''},history:{state:null,replaceState:(_s,_t,url)=>{clean=url;}}};
 assert.deepEqual(captureSecurityToken(),{path:'/verify-email',token});assert.equal(clean,'/verify-email');
 window.location={pathname:'/reset-password',hash:'#token='+token,search:'?source=local'};
 assert.deepEqual(captureSecurityToken(),{path:'/reset-password',token});assert.equal(clean,'/reset-password?source=local');
 window.location.pathname='/dashboard';assert.equal(captureSecurityToken(),null);
});
test('replacement or malformed security fragments do not retain the old secret',()=>{
 let replaced;globalThis.window={location:{pathname:'/verify-email',hash:'#token=invalid',search:''},history:{state:null,replaceState:(_s,_t,url)=>{replaced=url;}}};
 assert.deepEqual(captureSecurityToken(),{path:'/verify-email',token:null});assert.equal(replaced,'/verify-email');
});
test('reset validates confirmation, composition, length and BCrypt byte ceiling',()=>{
 assert.equal(validateResetPassword('Changed-Password!2026','Changed-Password!2026'),'');
 for(const [a,b] of [['short','short'],['Changed-Password!2026','different'],['lowercaseonly123!','lowercaseonly123!'],['A!1'+'é'.repeat(36),'A!1'+'é'.repeat(36)]])assert.notEqual(validateResetPassword(a,b),'');
});
