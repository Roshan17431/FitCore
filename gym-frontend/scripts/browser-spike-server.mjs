// Local spike harness only: HTTPS SPA wrapper; configuration and synthetic keys stay outside Git.
import https from 'node:https';
import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { dispatchFixtureRequest } from './fixture-request-gate.mjs';
import { createBff } from '../bff/gateway.mjs';

const options=JSON.parse(await readFile(process.argv[2], 'utf8'));
const handler=createBff(options);
const root=path.resolve(options.staticRoot);
const types={'.html':'text/html; charset=utf-8','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.woff2':'font/woff2','.ico':'image/x-icon'};
const gates = new Map();
const server=https.createServer({key:options.tlsKeyPem,cert:options.tlsCertPem,minVersion:'TLSv1.2',maxHeaderSize:16384},async(req,res)=>{
  // This entire wrapper exists only in the disposable test harness, never app hosting.
  if(req.method==='GET' && ['/__fixture/browser-session.js','/__fixture/restart-proof.js'].includes(req.url)) {
    res.setHeader('Content-Type','application/javascript');res.setHeader('Cache-Control','no-store');
    res.end(await readFile(new URL(req.url.endsWith('restart-proof.js')?'./cookie-restart-proof.mjs':'../src/services/browser-session.js',import.meta.url)));return;
  }
  if(req.url.startsWith('/__fixture/')) {
    if(req.headers['x-fitcore-fixture']!==options.fixtureControl || req.socket.remoteAddress!=='127.0.0.1'){res.writeHead(403).end();return;}
    const command=req.url.substring('/__fixture/'.length);
    if(req.method==='GET' && command==='mailbox'){
      // Test wrapper only, random control key + actual loopback; never mounted by app hosting.
      res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-store');
      const files=(await readdir(options.mailbox)).filter(name=>/^[0-9a-f-]{36}\.json$/.test(name));
      const messages=await Promise.all(files.map(async name=>JSON.parse(await readFile(path.join(options.mailbox,name),'utf8'))));
      res.end(JSON.stringify(messages));return;
    }
    if(req.method==='GET' && command==='state'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify([...gates].map(([target,gate])=>({target,mode:gate.mode,waiting:gate.waiting.length}))));return;}
    if(req.method!=='POST'){res.writeHead(405).end();return;}
    let body='';for await(const chunk of req){body+=chunk;if(body.length>256){res.writeHead(413).end();return;}}
    if(!['/api/browser/bootstrap','/api/browser/login','/api/browser/logout','/api/browser/ack'].includes(body)){res.writeHead(400).end();return;}
    if(['hold','hold-body','hold-before'].includes(command) && !gates.has(body))gates.set(body,{waiting:[],mode:command});
    else if(command==='release') {const gate=gates.get(body);gates.delete(body);for(const send of gate?.waiting??[])send();}
    else {res.writeHead(409).end();return;}
    res.writeHead(204).end();return;
  }
  if(req.url.startsWith('/api/')) {
    return dispatchFixtureRequest(gates.get(req.url),req,res,handler);
  }
  res.setHeader('Content-Security-Policy',"default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
  res.setHeader('Cache-Control','no-store');
  res.setHeader('X-Content-Type-Options','nosniff');
  res.setHeader('Referrer-Policy','no-referrer');
  if(!['GET','HEAD'].includes(req.method)){res.writeHead(405).end();return;}
  try{
    const url=new URL(req.url,options.publicOrigin);
    if(url.origin!==options.publicOrigin || /%|\\|\.\./.test(url.pathname)){res.writeHead(400).end();return;}
    let file=path.join(root,url.pathname);
    if(!path.extname(file))file=path.join(root,'index.html');
    const bytes=await readFile(file);
    res.setHeader('Content-Type',types[path.extname(file)]??'application/octet-stream');
    res.end(req.method==='HEAD'?undefined:bytes);
  }catch{res.writeHead(404).end();}
});
server.requestTimeout=20000;server.headersTimeout=20000;
server.listen(new URL(options.publicOrigin).port,'127.0.0.1');
for(const signal of ['SIGTERM','SIGINT'])process.once(signal,()=>{server.closeAllConnections();server.close();});
