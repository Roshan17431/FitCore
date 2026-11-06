import test from 'node:test';
import assert from 'node:assert/strict';
import {dispatchFixtureRequest} from './fixture-request-gate.mjs';
test('test hold-before gate forwards no request until its exact held request is released',()=>{
 const gate={mode:'hold-before',waiting:[]};let calls=0,paused=false;
 const req={pause:()=>{paused=true;},resume:()=>{paused=false;},destroyed:false},res={destroyed:false};
 dispatchFixtureRequest(gate,req,res,(actual,response)=>{calls++;assert.equal(actual,req);assert.equal(response,res);});
 assert.equal(calls,0);assert.equal(paused,true);assert.equal(gate.waiting.length,1);
 gate.waiting[0]();gate.waiting[0]();assert.equal(calls,1,'release cannot replay a request');assert.equal(paused,false);
});
test('test hold-before release never forwards an abandoned held request',()=>{
 const gate={mode:'hold-before',waiting:[]};let calls=0;const req={pause(){},resume(){},destroyed:false},res={destroyed:false};
 dispatchFixtureRequest(gate,req,res,()=>{calls++;});req.destroyed=true;gate.waiting[0]();assert.equal(calls,0);
});
