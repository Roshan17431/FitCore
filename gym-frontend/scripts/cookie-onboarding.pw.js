/* eslint-disable no-unused-expressions -- Actual owned disposable HTTPS user journey. */
async (page) => {
  const ui='https://127.0.0.1:18444',fixture='@FIXTURE_CONTROL@',password='Journey-Password!2026';
  const browser=page.context().browser(),contexts=[],checks=[],failures=[],errors=[],violations=[],gyms=[];
  const stamp=Date.now().toString(36),recent=[];let active,currentCheck;
  const assert=(ok,message)=>{if(!ok)throw Error(message);};
  const button=(p,name)=>p.getByRole('button',{name,exact:true});
  const field=(p,name)=>p.getByLabel(name,{exact:true});
  const heading=(p,name)=>p.getByRole('heading',{name,exact:true});
  const visit=async(p,path)=>{active=p;await p.bringToFront();await p.goto(ui+path);};
  const step=async(id,work)=>{currentCheck=id;await work();checks.push(id);};
  const responseFor=(p,method,path)=>p.waitForResponse(r=>new URL(r.url()).origin===ui&&r.request().method()===method&&(path instanceof RegExp?path.test(new URL(r.url()).pathname):new URL(r.url()).pathname==='/api'+path));
  const action=async(p,method,path,control,status)=>{
    active=p;await p.bringToFront();const [r]=await Promise.all([responseFor(p,method,path),control.click()]);
    assert(r.status()===status,`Expected ${status} for ${method} action; received ${r.status()}`);await r.finished();return r;
  };
  const fresh=async()=>{
    const c=await browser.newContext({ignoreHTTPSErrors:true,viewport:{width:1440,height:1000}});contexts.push(c);
    c.on('request',r=>{const u=new URL(r.url());if(!['about:','data:','blob:'].includes(u.protocol)&&u.origin!==ui)violations.push('external request');if(r.headers().authorization)violations.push('browser Authorization');if(/^\/api\/auth\/(?!me$)/.test(u.pathname))violations.push('legacy account route');});
    c.on('response',r=>{const u=new URL(r.url());if(u.pathname.startsWith('/api/')){recent.push({path:u.pathname,status:r.status()});if(recent.length>20)recent.shift();}});
    const p=await c.newPage();p.setDefaultTimeout(15000);p.on('pageerror',e=>errors.push(e.message));await visit(p,'/login');await p.waitForLoadState('networkidle');return p;
  };
  const receipt=async(r,status)=>{assert(r.status()===status,'receipt status');assert(!r.headers()['set-cookie'],'receipt set-cookie');assert((await r.text())==='','receipt must be empty without JWT/user');};
  const mail=async(p,address,fragment)=>{
    for(let n=0;n<150;n++){
      const r=await p.context().request.get(ui+'/__fixture/mailbox',{headers:{'X-FitCore-Fixture':fixture}});assert(r.status()===200,'owned synthetic mailbox admission');
      const found=(await r.json()).find(m=>m.recipient===address&&m.text.includes(fragment));
      if(found){const link=found.text.match(/https:\/\/127\.0\.0\.1:18444\/[^\s]+/g)?.find(s=>s.includes(fragment));assert(link,'fixed-origin mail link');return link.slice(ui.length);}
      await new Promise(resolve=>setTimeout(resolve,100));
    }
    throw Error('Synthetic email acceptance missing');
  };
  const verify=async(p,address,link)=>{
    await visit(p,link);await field(p,'Account email').fill(address);
    assert(!p.url().includes('#')&&!p.url().includes('token='),'security fragment not stripped');
    const r=await action(p,'POST','/browser/verify-email',button(p,'Verify email'),204);await receipt(r,204);
    await p.getByRole('status').filter({hasText:'Email verified. Sign in to continue.'}).waitFor();
    assert(new URL(p.url()).pathname==='/verify-email','verification auto-navigated to authenticated UI');
    assert(!(await p.context().cookies(ui)).some(c=>c.name==='__Host-fitcore_session'),'verification created auth cookie');
  };
  const login=async(p,email,secret=password,owner=false)=>{
    if(new URL(p.url()).pathname!=='/login')await visit(p,'/login');
    else {active=p;await p.bringToFront();}
    await field(p,'Email').fill(email);await field(p,'Password').fill(secret);
    await action(p,'POST','/browser/login',button(p,'Login'),200);await p.waitForURL(ui+(owner?'/owner':'/dashboard'));await button(p,'Sign Out').waitFor();
    const r=await p.context().request.get(ui+'/api/auth/me',{headers:{'Sec-Fetch-Site':'same-origin'}});assert(r.status()===200,'fresh session identity');return r.json();
  };
  const logout=async p=>{
    await action(p,'POST','/browser/logout',button(p,'Sign Out'),200);
    await p.waitForURL(ui+'/login');
    // The route can change as soon as UI identity is cleared. Do not reload the
    // document while that same document is still delivering and ACKing cookies.
    await p.waitForFunction(()=>!localStorage.getItem('fitcore:cookie-transition'));
    const metadata=await p.context().request.get(ui+'/api/browser/state',{headers:{'Sec-Fetch-Site':'same-origin'}});
    assert(metadata.status()===200,'logout control state unavailable');
    const state=await metadata.json();assert(state.contextKind==='PREAUTH'&&state.transitionState==='IDLE'&&state.currentContext&&!state.authenticated,'logout not settled');
    assert(!(await p.context().cookies(ui)).some(c=>c.name==='__Host-fitcore_session'),'logout left an AUTH cookie');
    await button(p,'Login').waitFor();
  };
  const negative=async(p,method,path,data)=>p.evaluate(async({method,path,data})=>{
    const s=await fetch('/api/browser/csrf',{cache:'no-store'});const c=await s.json();
    const r=await fetch('/api'+path,{method,cache:'no-store',headers:{'Content-Type':'application/json','X-CSRF-TOKEN':c.csrfToken},...(data?{body:JSON.stringify(data)}:{})});return r.status;
  },{method,path,data});
  const invite=async(owner,address,role)=>{
    await visit(owner,'/gym/invitations');await field(owner,'Invitation email').fill(address);await field(owner,'Invitation role').selectOption(role);
    const response=await action(owner,'POST','/gym/invitations',button(owner,'Create invitation'),201);
    const issued=await response.json();assert(issued.deliveryStatus==='QUEUED'&&!issued.invitationToken,'invitation exposes secret or claims delivery');
    assert(!(await owner.locator('body').innerText()).includes('undefined'),'invalid secret link rendered');return mail(owner,address,'#invitationToken=');
  };
  try{
    for(const gym of [1,2]){
      const id=caseName=>`onboarding-gym-${gym}.${caseName}`;
      const prefix=`Journey G${gym} ${stamp}`,email=`journey-owner-${gym}-${stamp}@fixture.invalid`,slug=`journey-${gym}-${stamp}`;
      let owner,member,trainer,ownerIdentity,memberIdentity,trainerIdentity,memberLink,trainerLink,ownerLink,resetLink;
      let memberEmail=`journey-member-${gym}-${stamp}@fixture.invalid`;const trainerEmail=`journey-trainer-${gym}-${stamp}@fixture.invalid`;
      await step(id('owner-signup-pending'),async()=>{
        owner=await fresh();await visit(owner,'/create-gym');
        for(const [label,value] of [['Full name',`${prefix} Owner`],['Email',email],['Password',password],['Confirm password',password],['Gym name',prefix],['Gym slug',slug]])await field(owner,label).fill(value);
        const r=await action(owner,'POST','/browser/registration/owner',button(owner,'Create gym workspace'),202);await receipt(r,202);
        await owner.getByRole('status').filter({hasText:'No account is created until verification succeeds.'}).waitFor();
        assert(!(await owner.context().cookies(ui)).some(c=>c.name==='__Host-fitcore_session'),'signup authenticated prematurely');
      });
      await step(id('unverified-login-denied'),async()=>{
        const pending=await fresh();await field(pending,'Email').fill(email);await field(pending,'Password').fill(password);
        await action(pending,'POST','/browser/login',button(pending,'Login'),401);
        await pending.getByRole('alert').filter({hasText:'Email or password is incorrect.'}).waitFor();assert(await button(pending,'Login').isEnabled(),'ordinary denial forced fresh context');await pending.context().close();
      });
      await step(id('wrong-email-verification-denied'),async()=>{
        ownerLink=await mail(owner,email,'#token=');await visit(owner,ownerLink);await field(owner,'Account email').fill(`wrong-${gym}-${stamp}@fixture.invalid`);
        await action(owner,'POST','/browser/verify-email',button(owner,'Verify email'),400);
        assert(!(await owner.context().cookies(ui)).some(c=>c.name==='__Host-fitcore_session'),'wrong email activated session');
      });
      await step(id('owner-verification-without-login'),async()=>{await verify(owner,email,ownerLink);});
      await step(id('verification-replay-denied'),async()=>{
        await visit(owner,ownerLink);await field(owner,'Account email').fill(email);
        await action(owner,'POST','/browser/verify-email',button(owner,'Verify email'),400);
        assert(!(await owner.context().cookies(ui)).some(c=>c.name==='__Host-fitcore_session'),'replay installed session');
      });
      await step(id('owner-explicit-login'),async()=>{ownerIdentity=await login(owner,email,password,true);await heading(owner,prefix).waitFor();assert(ownerIdentity.role==='OWNER','owner role missing');});
      await step(id('catalog-and-plan-creation'),async()=>{
        await visit(owner,'/admin/memberships');
        for(const [label,value] of [['Plan name',`${prefix} Basic`],['Price','39'],['Duration months','1'],['Features','Synthetic journey plan']])await field(owner,label).fill(value);
        await action(owner,'POST','/admin/membership-plans',button(owner,'Create Plan'),201);
        await visit(owner,'/gym/catalogs');await button(owner,'Create exercise').click();const dialog=owner.getByRole('dialog');
        for(const [label,value] of [['Exercise name',`${prefix} Press`],['Exercise category','Strength'],['Recommended sets','3'],['Recommended reps','8'],['Description','Synthetic journey technique']])await field(dialog,label).fill(value);
        await action(owner,'POST','/admin/catalog/exercises',button(dialog,'Save exercise'),201);await dialog.waitFor({state:'hidden'});await heading(owner,`${prefix} Press`).waitFor();
      });
      await step(id('member-trainer-invitation-receipts'),async()=>{memberLink=await invite(owner,memberEmail,'MEMBER');trainerLink=await invite(owner,trainerEmail,'TRAINER');});
      for(const role of ['MEMBER','TRAINER'])await step(id(`${role.toLowerCase()}-accept-verify-login`),async()=>{
        const address=role==='MEMBER'?memberEmail:trainerEmail,link=role==='MEMBER'?memberLink:trainerLink,p=await fresh();
        await visit(p,link);assert(!p.url().includes('#'),'invitation fragment not stripped');
        for(const [label,value] of [['Full name',`${prefix} ${role}`],['Invited email',address],['Password',password],['Confirm password',password]])await field(p,label).fill(value);
        await receipt(await action(p,'POST','/browser/registration/invitation',button(p,'Accept invitation'),202),202);
        await verify(p,address,await mail(p,address,'/verify-email#token='));const identity=await login(p,address);
        assert(identity.gymId===ownerIdentity.gymId&&identity.role===role,'invitation wrong gym or role');
        if(role==='MEMBER'){member=p;memberIdentity=identity;}else{trainer=p;trainerIdentity=identity;}
      });
      await step(id('owner-manages-own-member'),async()=>{
        await visit(owner,'/admin/users');const row=owner.getByRole('row').filter({hasText:memberEmail});await button(row,'Edit').click();const dialog=owner.getByRole('dialog');
        const changed=`journey-member-changed-${gym}-${stamp}@fixture.invalid`;await field(dialog,'Email').fill(changed);
        await field(dialog,'Fitness goal').fill('Journey strength');await action(owner,'PUT',/^\/api\/admin\/users\/[^/]+$/,button(dialog,'Save User'),200);await dialog.waitFor({state:'hidden'});
        memberEmail=changed;await owner.reload();await owner.getByRole('cell',{name:memberEmail,exact:true}).waitFor();
      });
      await step(id('email-change-reverification'),async()=>{
        assert((await member.context().request.get(ui+'/api/auth/me',{headers:{'Sec-Fetch-Site':'same-origin'}})).status()===401,'email change failed to revoke prior session');
        await visit(member,'/login');await member.getByRole('link',{name:'Request email verification',exact:true}).click();
        await button(member,'Sign out previous session').click();await field(member,'Account email').fill(memberEmail);
        await receipt(await action(member,'POST','/browser/verification/request',button(member,'Request verification'),202),202);
        await verify(member,memberEmail,await mail(member,memberEmail,'/verify-email#token='));
        const prior=memberIdentity.id;memberIdentity=await login(member,memberEmail);assert(memberIdentity.id===prior,'email verification replaced account');
      });
      await step(id('member-membership-and-booking'),async()=>{
        await visit(member,'/membership/plans');const card=member.locator('section').filter({has:heading(member,`${prefix} Basic Plan`)});
        await action(member,'POST',/^\/api\/memberships\/subscribe\/[^/]+\/[0-9]+$/,button(card,'Purchase Plan'),200);
        await visit(member,'/membership/active');await heading(member,`${prefix} Basic Plan`).waitFor();
        await visit(member,'/trainers/booking');await field(member,'Select Trainer').selectOption({label:`${prefix} TRAINER`});
        const date=new Date(Date.now()+(40+gym)*86400000).toISOString().slice(0,10);await field(member,'Select Date').fill(date);await field(member,'Select Time').fill('11:00');
        await action(member,'POST',/^\/api\/trainers\/book\/[^/]+\/[0-9]+$/,button(member,'Book Session'),200);
        await visit(member,'/trainers/history');await member.getByRole('cell',{name:'11:00',exact:true}).waitFor();
      });
      await step(id('trainer-and-member-manager-denial'),async()=>{
        for(const p of [member,trainer]){await visit(p,'/gym/catalogs');await p.waitForURL(ui+'/dashboard');assert(await negative(p,'GET','/admin/users')===403,'ordinary role acquired manager authority');}
        assert(trainerIdentity.role==='TRAINER','trainer activation not preserved');
      });
      await step(id('reset-revokes-prior-session'),async()=>{
        const recovery=await fresh();await visit(recovery,'/forgot-password');await field(recovery,'Email').fill(memberEmail);
        await receipt(await action(recovery,'POST','/browser/forgot-password',button(recovery,'Request Reset'),202),202);
        resetLink=await mail(recovery,memberEmail,'/reset-password#token=');await visit(recovery,resetLink);
        for(const [label,value] of [['Account email',memberEmail],['New password','Recovered-Journey!2026'],['Confirm password','Recovered-Journey!2026']])await field(recovery,label).fill(value);
        await receipt(await action(recovery,'POST','/browser/reset-password',button(recovery,'Change password'),204),204);
        await recovery.getByRole('status').filter({hasText:'Password changed. Sign in again; earlier sessions are invalid.'}).waitFor();
        assert(!(await recovery.context().cookies(ui)).some(c=>c.name==='__Host-fitcore_session'),'reset automatically authenticated');
        assert((await member.context().request.get(ui+'/api/auth/me',{headers:{'Sec-Fetch-Site':'same-origin'}})).status()===401,'old session survived reset');
        member=recovery;
      });
      await step(id('reset-replay-denied'),async()=>{
        await visit(member,resetLink);for(const [label,value] of [['Account email',memberEmail],['New password','Rejected-Replay!2026'],['Confirm password','Rejected-Replay!2026']])await field(member,label).fill(value);
        await action(member,'POST','/browser/reset-password',button(member,'Change password'),400);
        memberIdentity=await login(member,memberEmail,'Recovered-Journey!2026');
      });
      await step(id('logout-and-explicit-relogin'),async()=>{await logout(member);await login(member,memberEmail,'Recovered-Journey!2026');});
      gyms.push({owner,member,trainer,ownerIdentity,memberIdentity,prefix,email,memberEmail});
    }
    await step('onboarding.two-new-gyms-cross-reference-isolation',async()=>{
      assert(gyms[0].ownerIdentity.gymId!==gyms[1].ownerIdentity.gymId,'separate signup gyms collapsed');
      for(const [index,g] of gyms.entries()){
        const other=gyms[1-index];await visit(g.owner,'/admin/users');await g.owner.getByRole('cell',{name:g.memberEmail,exact:true}).waitFor();
        assert(!(await g.owner.locator('main').innerText()).includes(other.memberEmail),'foreign account listed');
        assert(await negative(g.owner,'GET','/users/'+other.memberIdentity.id)===404,'foreign member read');
        assert(await negative(g.owner,'PUT','/admin/users/'+other.memberIdentity.id,{fullName:'Forbidden',email:other.memberEmail,role:'ADMIN',enabled:true})===404,'foreign member modification');
        await visit(g.member,'/workouts/library');await heading(g.member,`${g.prefix} Press`).waitFor();assert(!(await g.member.locator('main').innerText()).includes(other.prefix),'foreign catalog listed');
      }
    });
    await step('onboarding.no-browser-secrets-or-external-delivery',async()=>{
      for(const c of contexts)for(const p of c.pages())if(!p.isClosed())assert(await p.evaluate(()=>[localStorage,sessionStorage].every(s=>Object.entries(s).every(([k,v])=>!['token','user'].includes(k)&&!/^eyJ[^.]+\.[^.]+\./.test(v)&&!/[?#](?:token|invitationToken)=/.test(v)))),'secret persisted in storage');
      assert(errors.length===0,'uncaught browser errors');assert(violations.length===0,'external or bearer request');
    });
  }catch(error){failures.push({id:currentCheck||'onboarding.execution',message:String(error.message||error).replace(/#[^\s]+/g,'#[redacted]').slice(0,1000),path:active?new URL(active.url()).pathname:'',body:active?(await active.locator('body').innerText().catch(()=>'<unavailable>')).replace(/[A-Za-z0-9_-]{32,}/g,'[redacted]').slice(0,1800):'',recent,errors});}
  finally{for(const c of contexts)await c.close().catch(()=>{});}
  return {suite:'fitcore-cookie-onboarding-v1',passed:checks.length,checks,failures,browserType:browser.browserType().name(),browserVersion:browser.version(),production:'BLOCKED'};
}
