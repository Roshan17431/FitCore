import test, {after} from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {MemoryRouter} from 'react-router-dom';
import {createServer} from 'vite';
const server=await createServer({configFile:false,root:new URL('..',import.meta.url).pathname,logLevel:'silent',define:{'import.meta.env.VITE_AUTH_TRANSPORT':JSON.stringify('cookie-spike')},server:{middlewareMode:true,watch:null},appType:'custom'});
after(()=>server.close());
const {AuthContext}=await server.ssrLoadModule('/src/context/auth-context.js');
const {ThemeContext}=await server.ssrLoadModule('/src/context/theme-context.js');
const page=async(name,props={},value={})=>{
 const {default:Component}=await server.ssrLoadModule(`/src/pages/${name}.jsx`);
 return renderToStaticMarkup(React.createElement(ThemeContext.Provider,{value:{theme:'system',setTheme(){}}},React.createElement(AuthContext.Provider,{value:{cookieTransport:true,invitationToken:'x'.repeat(43),...value}},React.createElement(MemoryRouter,null,React.createElement(Component,props)))));
};
test('ordinary cookie owner signup renders verified registration form',async()=>{
 const html=await page('Signup',{owner:true});assert.match(html,/<form/);assert.match(html,/Email verification is required/);assert.doesNotMatch(html,/Registration is unavailable/);
});
test('cookie invitation signup accepts only captured memory invitation',async()=>{
 assert.match(await page('Signup'),/<form/);assert.doesNotMatch(await page('Signup',{}, {invitationToken:null}),/<form/);
});
test('cookie verification and reset render actionable forms with a memory fragment',async()=>{
 for(const [reset,path] of [[false,'/verify-email'],[true,'/reset-password']]){
  const html=await page('SecurityFlow',{reset},{securityToken:{path,token:'x'.repeat(43)}});assert.match(html,/<form/);assert.doesNotMatch(html,/unavailable in this cookie/);assert.doesNotMatch(html,/x{43}/);
 }
});
test('fresh-context guidance is accessible, conservative and disables original sign-in',async()=>{
 const html=await page('Login',{}, {sessionFreshContextRequired:true,sessionError:'Cannot prove the interrupted session.'});
 assert.match(html,/role="alert"/);assert.match(html,/separate browser profile/);assert.match(html,/all private windows/);assert.match(html,/original.*(?:blocked|fenced)/i);assert.match(html,/<button[^>]*disabled[^>]*>Login/);
 assert.doesNotMatch(html,/Check sign-in status/);
});
test('retryable uncertainty still offers status check and expired source still offers explicit signout',async()=>{
 assert.match(await page('Login',{}, {sessionRecoveryAvailable:true}),/Check sign-in status/);
 assert.match(await page('Login',{}, {sessionSignOutRequired:true}),/Sign out previous session/);
});
test('cookie account forms explain explicit signout instead of submitting an AUTH source',async()=>{
 for(const [name,props] of [['Signup',{owner:true}],['ForgotPassword',{}],['SecurityFlow',{}]]){
  const html=await page(name,props,{user:{id:'owner',role:'OWNER'}});assert.match(html,/Sign out/);assert.doesNotMatch(html,/<form/);
 }
});
test('cookie provider retains invitation and security fragments in React memory and exposes receipt methods',async()=>{
 const {AuthProvider}=await server.ssrLoadModule('/src/context/AuthContext.jsx');let auth;
 function Probe(){auth=React.useContext(AuthContext);return null;}
 renderToStaticMarkup(React.createElement(AuthProvider,{initialInvitation:'invitation-memory',initialSecurity:{path:'/verify-email',token:'security-memory'}},React.createElement(Probe)));
 assert.equal(auth.invitationToken,'invitation-memory');assert.equal(auth.securityToken.token,'security-memory');
 for(const method of ['pendingOwner','pendingInvite','requestVerification','register','registerOwner','verifyEmail','completeReset','resetPassword'])assert.equal(typeof auth[method],'function',method);
 const values=new Map();globalThis.localStorage={getItem:key=>values.get(key)??null,setItem:(key,value)=>values.set(key,value),removeItem:key=>values.delete(key)};
 Object.defineProperty(globalThis,'navigator',{configurable:true,value:{locks:{request:async(_n,_o,fn)=>fn()}}});
 const calls=[];globalThis.fetch=async(url)=>{calls.push(url);return {ok:true,status:url.endsWith('/verify-email')?204:url.endsWith('/csrf')?200:202,headers:new Headers(),text:async()=>url.endsWith('/csrf')?JSON.stringify({csrfToken:'control',authenticated:false,reauthenticationRequired:false,currentContext:true,familyId:'2b830b6d-8b6d-4f46-bcc3-39091199f323',contextRevision:'1',familyRevision:'1',contextKind:'PREAUTH',transitionState:'IDLE',pendingKind:null}):''};};
 assert.deepEqual(await auth.verifyEmail('synthetic@fixture.invalid','memory'),{success:true,verified:true});
 assert.deepEqual(await auth.registerOwner({}),{success:true,pending:true});
 assert.ok(!calls.includes('/api/auth/me')&&!calls.some(url=>url.endsWith('/login')));assert.deepEqual([...values],[]);
});

test('cookie invitation receipt shows queue intent with safe metadata and no secret controls',async()=>{
 const {InvitationReceipt}=await server.ssrLoadModule('/src/pages/Invitations.jsx');
 const html=renderToStaticMarkup(React.createElement(InvitationReceipt,{receipt:{id:'fixture-invite',role:'MEMBER',expiresAt:'2030-01-01T00:00:00Z',deliveryStatus:'QUEUED',invitationToken:'forbidden-secret'}}));
 assert.match(html,/role="status"/);assert.match(html,/Invitation queued/);assert.match(html,/fixture-invite/);assert.match(html,/provider acceptance or mailbox delivery/);assert.doesNotMatch(html,/forbidden-secret|Copy invitation|One-time invitation link/);
});
