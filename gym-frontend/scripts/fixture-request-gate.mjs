// Disposable HTTPS wrapper helper. Never imported by the application BFF.
export function dispatchFixtureRequest(gate,req,res,handler) {
  if (gate?.mode === 'hold-before') {
    req.pause();
    let released=false;
    gate.waiting.push(()=>{
      if(released)return;
      released=true;
      // A release resumes only this test-held request, never synthesizes/replays it.
      if(req.destroyed || res.destroyed)return;
      handler(req,res);req.resume();
    });
    return;
  }
  if(gate) {
    const end=res.end.bind(res);
    res.end=(...args)=>{
      if(res.statusCode>=200&&res.statusCode<300){
        if(gate.mode==='hold-body'){res.flushHeaders();res.write(' ');}
        gate.waiting.push(()=>{if(!res.destroyed)end(...args);});
      }else end(...args);
      return res;
    };
  }
  return handler(req,res);
}
