#!/usr/bin/env python3
"""Require actual frontend coordination/BFF results AND disposable HTTPS browser evidence."""
import json
import pathlib
import sys
import xml.etree.ElementTree as ET
NODE_REQUIRED={
 'forwards raw bytes and raw target with a verified request-bound RS256 proof, stripping untrusted headers',
 'never follows upstream redirects or retries a timed-out transition',
 'cookie transport fails closed when Web Locks are unsupported',
 'uncertain cookie delivery persists across client recreation without retry',
}
BROWSER_REQUIRED={
 'secure-http-only-host-cookie','no-browser-auth-secrets','same-origin-no-store','legacy-bearer-route-blocked','unsafe-without-csrf-blocked','no-state-changing-get','cross-origin-blocked','client-tenant-selector-blocked',
 'gym-1-all-role-isolation','gym-1-membership-rules','gym-1-booking-conflict','gym-1-catalog-history','gym-2-all-role-isolation','gym-2-membership-rules','gym-2-booking-conflict','gym-2-catalog-history',
 'foreground-session-revalidation','persisted-pageshow-revalidation','pending-blocks-business','pending-blocks-second-login','pending-blocks-logout','two-tab-gym-change','logout-pending-blocks-login','logout-expires-auth-cookie','logout-no-old-session','refresh-back-session-restore','live-web-lock-serialization','queued-old-view-write-blocked','tab-close-uncertainty','timeout-uncertainty','no-uncaught-errors',
}
SHARED_REQUIRED={'shared-pending-fence','shared-session-ack-authority','shared-logout-retirement','shared-gateway-replay','shared-concurrent-login','raw-cookie-parser-fail-closed'}
def check(node_file,browser_file,shared_file):
    xml=ET.parse(node_file).getroot()
    cases=xml.findall('.//testcase')
    if not cases or xml.findall('.//failure') or xml.findall('.//error') or xml.findall('.//skipped'):
        raise ValueError('Failed, absent or skipped frontend evidence')
    if not NODE_REQUIRED<={case.get('name') for case in cases}:
        raise ValueError('Missing critical frontend coordination or gateway tests')
    report=json.loads(pathlib.Path(browser_file).read_text())
    checks=report.get('checks',[])
    if (report.get('suite')!='fitcore-browser-spike-v1' or report.get('webLocks') is not True
        or report.get('production')!='BLOCKED' or report.get('failures')!=[]
        or not isinstance(checks,list) or any(not isinstance(c,str) for c in checks)
        or len(checks)!=len(set(checks)) or report.get('passed')!=len(checks)
        or not BROWSER_REQUIRED<=set(checks)):
        raise ValueError('Incomplete or failed HTTPS cookie/race evidence')
    shared=json.loads(pathlib.Path(shared_file).read_text())
    shared_checks=shared.get('checks',[])
    if (shared.get('failures')!=[] or not isinstance(shared_checks,list) or any(not isinstance(c,str) for c in shared_checks)
        or len(shared_checks)!=len(set(shared_checks)) or shared.get('passed')!=len(shared_checks)
        or not SHARED_REQUIRED<=set(shared_checks)):
        raise ValueError('Incomplete or failed two-instance evidence')
    return {'frontend':len(cases),'browser_spike':len(checks),'shared_backend':len(shared_checks),'production':'BLOCKED'}
if __name__=='__main__':
    try:
        if len(sys.argv)!=4:raise ValueError('Pass local Node JUnit, browser JSON and shared-instance JSON paths')
        print(json.dumps(check(*sys.argv[1:]),sort_keys=True))
    except (ValueError,OSError,TypeError,ET.ParseError) as error:
        print(str(error),file=sys.stderr);sys.exit(1)
