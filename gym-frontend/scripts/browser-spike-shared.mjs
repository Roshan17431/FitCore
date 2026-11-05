// Real TLS and two independent backend processes sharing one owned disposable PostgreSQL.
import https from 'node:https';
import {readFile,writeFile} from 'node:fs/promises';
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
await writeFile(output,JSON.stringify({passed:checks.length,checks,failures:[]},null,2));
console.log(JSON.stringify({passed:checks.length,checks}));
