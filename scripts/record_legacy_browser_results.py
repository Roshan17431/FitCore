#!/usr/bin/env python3
"""Record the exact executed disposable bearer regressions before the next harness overwrites logs."""
import json
import pathlib
import sys
if len(sys.argv)!=4 or sys.argv[2] not in ('enabled','disabled'):
    raise SystemExit('Pass local artifact folder, enabled/disabled and local output JSON')
folder,mode,output=pathlib.Path(sys.argv[1]),sys.argv[2],pathlib.Path(sys.argv[3])
names=['integrated','security-integrated']+(['phase3-integrated','tenant-isolation-integrated','owner-integrated','catalog-integrated'] if mode=='enabled' else ['onboarding-disabled'])
suites=[]
for name in names:
    text=(folder/(name+'.log')).read_text()
    data=json.loads(text.split('### Result\n',1)[1].split('\n###',1)[0].strip())
    checks=data.get('checks',[])
    if not checks or data.get('passed')!=len(checks) or len(checks)!=len(set(checks)) or data.get('uncaughtErrors',0) or data.get('externalRequests',0):
        raise SystemExit('Incomplete or failed browser evidence: '+name)
    suites.append({'suite':name,**data})
output.write_text(json.dumps({'mode':mode,'passed':sum(s['passed'] for s in suites),'suites':suites},indent=2))
