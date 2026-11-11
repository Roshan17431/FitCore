// Real TLS and two independent backend processes sharing one owned disposable PostgreSQL.
import https from 'node:https';
import {readFile,readdir,writeFile} from 'node:fs/promises';
import {createPrivateKey,createHash,randomUUID} from 'node:crypto';
import {SignJWT} from 'jose';
const [,,configFile,output]=process.argv;
const config=JSON.parse(await readFile(configFile,'utf8')),key=createPrivateKey(config.privateKeyPem),checks=[];
const jar=new Map();let csrf;
const hash=value=>createHash('sha256').update(value).digest('hex');
const assert=(ok,message)=>{if(!ok)throw Error(message);};
const cookies=()=>[...jar].sort(([a],[b])=>a.localeCompare(b)).map(([k,v])=>`${k}=${v}`).join('; ');
async function proof(method,target,body,cookie){const now=Math.floor(Date.now()/1000);return new SignJWT({method,target,bodySha256:hash(body),cookieSha256:hash(cookie),clientIp:'127.0.0.1'}).setProtectedHeader({alg:'RS256'}).setIssuer('fitcore-local-bff').setAudience('fitcore-browser-spike').setIssuedAt(now).setExpirationTime(now+30).setJti(randomUUID()).sign(key);}
async function send(port,method,target,data,options={}){
  const body=data===undefined?'':JSON.stringify(data),cookie=options.cookie??cookies(),assertion=options.proof??await proof(method,target,body,cookie);
  return new Promise((resolve,reject)=>{
    const r=https.request({hostname:'127.0.0.1',port,method,path:target,ca:config.caPem,rejectUnauthorized:true,agent:false,headers:{Origin:config.publicOrigin,'Sec-Fetch-Site':'same-origin','Content-Type':'application/json','Content-Length':Buffer.byteLength(body),'X-FitCore-Gateway':assertion,'X-FitCore-Bootstrap':'1',...(cookie?{Cookie:cookie}:{}),...(csrf?{'X-CSRF-TOKEN':csrf}:{})}},res=>{
      let text='';res.on('data',chunk=>{text+=chunk;});res.on('end',()=>{let data;try{data=JSON.parse(text);}catch{/*empty*/}resolve({status:res.statusCode,data,cookies:res.headers['set-cookie']??[]});});res.on('error',reject);
    });r.setTimeout(10000,()=>r.destroy(Error('Bounded fixture request timeout')));r.on('error',reject);r.end(body);
  });
}
function accept(result){for(const line of result.cookies){const [pair]=line.split(';'),at=pair.indexOf('=');if(pair.slice(at+1)==='')jar.delete(pair.slice(0,at));else jar.set(pair.slice(0,at),pair.slice(at+1));}if(result.data?.csrfToken)csrf=result.data.csrfToken;}
let r=await send(18443,'POST','/api/browser/bootstrap',{});assert(r.status===200,'bootstrap on first instance');accept(r);
r=await send(18445,'POST','/api/browser/login',{email:'owner1@fixture.invalid',password:'Browser-Spike!2026'});assert(r.status===200,'second instance login');accept(r);
assert((await send(18443,'GET','/api/auth/me')).status===423,'first instance must observe pending login');checks.push('shared-pending-fence');
assert((await send(18443,'POST','/api/browser/ack',{})).status===200,'first instance acknowledges second instance delivery');
assert((await send(18445,'GET','/api/auth/me')).data.id==='20000000-0000-0000-0000-000000000001','shared authoritative session');checks.push('shared-session-ack-authority');
for(const name of ['__Host-fitcore_session','__Host-fitcore_preauth']) {
  const duplicate=await send(18443,'GET','/api/auth/me',undefined,{cookie:cookies()+`; ${name}=bad`});
  assert(duplicate.status===400&&duplicate.cookies.length===0,'raw duplicate cookie must fail closed');
}
r=await send(18445,'POST','/api/browser/logout',{});assert(r.status===200,'second instance logout');accept(r);
for(const value of ['bad','']) {
  const malformed=await send(18443,'POST','/api/browser/ack',{}, {cookie:cookies()+`; __Host-fitcore_session=${value}`});
  assert(malformed.status===400&&malformed.cookies.length===0,'raw malformed auth cannot satisfy logout absence');
}
assert((await send(18443,'POST','/api/browser/ack',{})).status===200,'first instance logout ack');assert((await send(18445,'GET','/api/auth/me')).status===401,'shared retirement');checks.push('shared-logout-retirement');
for(const cookie of ['__Host-fitcore_session=bad','__Host-fitcore_preauth=']) {
  const malformed=await send(18445,'POST','/api/browser/bootstrap',{}, {cookie});
  assert(malformed.status===400&&malformed.cookies.length===0,'raw malformed cookie cannot bootstrap a family');
}
checks.push('raw-cookie-parser-fail-closed');
const token=await proof('POST' ,'/api/browser/bootstrap','{}','');
const simultaneous=await Promise.all([send(18443,'POST','/api/browser/bootstrap',{}, {proof:token,cookie:''}),send(18445,'POST','/api/browser/bootstrap',{}, {proof:token,cookie:''})]);
assert(simultaneous.map(r=>r.status).sort().join(',')==='200,401','one shared nonce acceptance');checks.push('shared-gateway-replay');
const logins=await Promise.all([send(18443,'POST','/api/browser/login',{email:'owner1@fixture.invalid',password:'Browser-Spike!2026'}),send(18445,'POST','/api/browser/login',{email:'owner2@fixture.invalid',password:'Browser-Spike!2026'})]);
assert(logins.map(r=>r.status).sort().join(',')==='200,423','one shared family transition');checks.push('shared-concurrent-login');
// New receipts are shared state across actual JVMs; synthetic provider only.
// The preceding concurrent-login case deliberately left its family PENDING.
jar.clear();csrf=undefined;r=await send(18443,'POST','/api/browser/bootstrap',{});assert(r.status===200,'new independent receipt context');accept(r);
async function mailToken(address,fragment){
  for(let n=0;n<150;n++){
    for(const file of (await readdir(config.mailbox)).filter(f=>/^[0-9a-f-]{36}\.json$/.test(f))){
      const mail=JSON.parse(await readFile(`${config.mailbox}/${file}`,'utf8'));
      if(mail.recipient===address && mail.text.includes(fragment))return mail.text.split(fragment)[1].slice(0,43);
    }
    await new Promise(resolve=>setTimeout(resolve,100));
  }
  throw Error('Synthetic acceptance missing');
}
const address=`shared-${randomUUID()}@fixture.invalid`,secret='Shared-Owner!2026';
r=await send(18443,'POST','/api/browser/registration/owner',{fullName:'Shared Synthetic Owner',email:address,password:secret,gymName:'Shared Isolated Gym',slug:`shared-${randomUUID()}`});
assert(r.status===202 && r.cookies.length===0 && r.data===undefined,'tokenless owner intent');
const activation=await mailToken(address,'#token=');
const activations=await Promise.all([18443,18445].map(port=>send(port,'POST','/api/browser/verify-email',{email:address,token:activation})));
assert(activations.map(r=>r.status).sort().join(',')==='204,400' && activations.every(r=>r.cookies.length===0),'single-use cross-instance activation without cookies');
assert((await send(18445,'GET','/api/auth/me')).status===401,'activation never logs in');checks.push('shared-tokenless-owner-activation');
r=await send(18443,'POST','/api/browser/login',{email:address,password:secret});assert(r.status===200,'activated owner explicit login');accept(r);
assert((await send(18445,'POST','/api/browser/ack',{})).status===200,'new owner ACK');
const oldAuth=cookies(),oldCsrf=csrf;jar.clear();csrf=undefined;r=await send(18445,'POST','/api/browser/bootstrap',{});assert(r.status===200,'separate recovery preauth');accept(r);
r=await send(18443,'POST','/api/browser/forgot-password',{email:address});assert(r.status===202&&r.cookies.length===0,'recovery intent');
const reset=await mailToken(address,'/reset-password#token=');
const resets=await Promise.all([18443,18445].map(port=>send(port,'POST','/api/browser/reset-password',{email:address,token:reset,password:'Changed-Shared!2026'})));
assert(resets.map(r=>r.status).sort().join(',')==='204,400'&&resets.every(r=>r.cookies.length===0),'single reset consumption across instances');
assert((await send(18445,'GET','/api/auth/me',undefined,{cookie:oldAuth})).status===401 && oldCsrf,'old owner cookie revoked by actual password reset');checks.push('shared-reset-session-revocation');
for(const action of ['forgot-password','verification/request']){
  let denied=false;
  for(let n=0;n<51;n++){
    const result=await send(n%2?18443:18445,'POST',`/api/browser/${action}`,{email:'unknown-shared@fixture.invalid'});
    assert(result.cookies.length===0,'receipts never write cookies');
    if(result.status===429){denied=true;break;}assert(result.status===202,'generic tokenless receipt');
  }
  assert(denied,'shared account receipt ceiling');
  assert((await send(18443,'POST',`/api/browser/${action}`,{email:address})).status===429 && (await send(18445,'POST',`/api/browser/${action}`,{email:address})).status===429,'both instances deny account budget');
}
checks.push('shared-onboarding-abuse-budget');
assert((await send(18445,'GET','/api/browser/state')).data.transitionState==='IDLE','receipt abuse leaves context unchanged');checks.push('shared-onboarding-no-cookie');
// Bad credentials must consume a single shared ingress budget across both JVMs.
jar.clear();csrf=undefined;r=await send(18443,'POST','/api/browser/bootstrap',{});assert(r.status===200,'abuse fixture preauth');accept(r);
const remaining=300-(Math.floor(Date.now()/1000)%300);
if(remaining<15)await new Promise(resolve=>setTimeout(resolve,remaining*1000+100));
let exhausted=false,denials=0;
for(let n=0;n<51;n++){
  const attempt=await send(n%2?18443:18445,'POST','/api/browser/login',{email:'owner1@fixture.invalid',password:'incorrect-synthetic-password'});
  assert(attempt.cookies.length===0,'failed ingress never writes a cookie');
  if(attempt.status===429){exhausted=true;break;}
  assert(attempt.status===401,'bad credential remains generic');denials++;
}
assert(exhausted&&denials>0&&denials<=50,'shared local peer ceiling counts earlier login attempts');
const blocked=await Promise.all([18443,18445].map(port=>send(port,'POST','/api/browser/login',{email:'owner1@fixture.invalid',password:'Browser-Spike!2026'})));
assert(blocked.every(r=>r.status===429&&r.cookies.length===0),'both instances enforce exhausted budget');checks.push('shared-login-abuse-budget');
assert((await send(18445,'GET','/api/browser/state')).data.transitionState==='IDLE','rate denial never starts a family transition');checks.push('shared-login-abuse-no-cookie');
await writeFile(output,JSON.stringify({passed:checks.length,checks,failures:[]},null,2));
console.log(JSON.stringify({passed:checks.length,checks}));
