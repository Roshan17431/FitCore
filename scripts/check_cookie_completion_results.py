#!/usr/bin/env python3
"""Require complete local cookie recovery, native restart and real gym-form evidence."""
import json
import pathlib
import sys

RECOVERY={'gym-1-incorrect-password-retry','gym-1-explicit-login-after-denial','gym-2-incorrect-password-retry','gym-2-explicit-login-after-denial','tab-close-received-cookie-recovery','lost-ack-receipt-recovery','lost-logout-receipt-recovery','old-cookie-recovery-remains-held','missing-cookie-recovery-remains-held','controlled-rate-denial-explicit-retry'}
RECOVERY|={f'gym-{g}-{c}' for g in [1,2] for c in ['same-revision-pre-mutation-remains-held','raw-different-family-recovery-remains-held']}
RECOVERY|={'fresh-context-accessible-instructions','fresh-isolated-context-does-not-repair-original'}
ONBOARDING={f'onboarding-gym-{g}.{c}' for g in [1,2] for c in ['owner-signup-pending','wrong-email-verification-denied','verification-replay-denied','reset-replay-denied','unverified-login-denied','owner-verification-without-login','owner-explicit-login','catalog-and-plan-creation','member-trainer-invitation-receipts','member-accept-verify-login','trainer-accept-verify-login','owner-manages-own-member','email-change-reverification','member-membership-and-booking','trainer-and-member-manager-denial','reset-revokes-prior-session','logout-and-explicit-relogin']}|{'onboarding.two-new-gyms-cross-reference-isolation','onboarding.no-browser-secrets-or-external-delivery'}
POLICY={f'gym-{g}.{c}' for g in [1,2] for c in ['expired-current-explicit-logout-reauthentication','password-change-explicit-logout-reauthentication','role-revocation-explicit-logout-reauthentication','disabled-account-explicit-logout-denied']}
GYM_CASES={'owner-cookie-login','owner-plan-create','owner-exercise-create','owner-workout-draft','admin-cookie-login','admin-plan-update','admin-exercise-update','admin-workout-update-publish','referenced-exercise-name-protection','published-exercise-archive-protection','nutrition-catalog-create-update','member-cookie-login','member-local-catalogs','member-subscribe','member-renew','member-upgrade','member-workout-log','member-nutrition-log','member-progress-weight-bmi','member-attendance','member-booking','member-booking-conflict','peer-cookie-login','peer-booking-conflict','member-manager-route-denial','trainer-current-policy','admin-current-policy','catalog-archive'}
GYM={f'gym-{gym}.{case}' for gym in [1,2] for case in GYM_CASES}|{'cookie-ui.cross-gym-persisted-catalog-isolation','cookie-ui.same-origin-no-bearer-no-runtime-errors'}
RESTART={'native-browser-restart-retains-fence','native-browser-restart-bound-proof-policy'}

def certify(report,suite,required):
    if not isinstance(report,dict):raise ValueError('Invalid browser evidence')
    checks=report.get('checks')
    if (report.get('suite')!=suite or report.get('production')!='BLOCKED' or report.get('failures')!=[]
        or not isinstance(checks,list) or any(not isinstance(case,str) for case in checks)
        or len(checks)!=len(set(checks)) or report.get('passed')!=len(checks) or not required<=set(checks)):
        raise ValueError('Incomplete or failed '+suite+' evidence')
    return len(checks)

def engine(report,expected):
    if (not isinstance(report,dict) or report.get('browserType')!=expected
        or not isinstance(report.get('browserVersion'),str) or not report['browserVersion'].strip()):
        raise ValueError('Actual browser engine/version does not match '+expected)

def check(recovery_file,gym_file,restart_file,policy_file,onboarding_file):
    recovery=json.loads(pathlib.Path(recovery_file).read_text())
    engines=recovery.get('engines',[])
    if (not isinstance(engines,list) or len(engines)!=3 or any(not isinstance(e,dict) for e in engines)
        or {e.get('engine') for e in engines}!={'chrome','firefox','webkit'}):
        raise ValueError('Missing or duplicated browser engine evidence')
    for e in engines:engine(e.get('report'),'chromium' if e['engine']=='chrome' else e['engine'])
    recovery_count=sum(certify(e.get('report'),'fitcore-cookie-recovery-v1',RECOVERY) for e in engines)
    gym=json.loads(pathlib.Path(gym_file).read_text());engine(gym,'chromium')
    gym_count=certify(gym,'fitcore-cookie-gym-ui-v1',GYM)
    policy=json.loads(pathlib.Path(policy_file).read_text());engine(policy,'chromium')
    policy_count=certify(policy,'fitcore-cookie-policy-ui-v1',POLICY)
    onboarding=json.loads(pathlib.Path(onboarding_file).read_text());engine(onboarding,'chromium')
    onboarding_count=certify(onboarding,'fitcore-cookie-onboarding-v1',ONBOARDING)
    restart=json.loads(pathlib.Path(restart_file).read_text());engine(restart,'chromium')
    restart_count=certify(restart,'fitcore-cookie-native-restart-v1',RESTART)
    cookies=restart.get('cookiePresence')
    if (not isinstance(cookies,dict) or set(cookies)!={'auth','preauth'}
        or any(type(v) is not bool for v in cookies.values()) or type(restart.get('receivedCookieProof')) is not bool):
        raise ValueError('Missing actual restart cookie evidence')
    if restart['receivedCookieProof']:
        if (not cookies['auth'] or restart.get('boundCandidate') is not True
            or restart.get('restartPolicyOutcome')!='received-cookie'):
            raise ValueError('Invalid bound restart candidate evidence')
    elif cookies['auth'] or cookies['preauth'] or restart.get('boundCandidate') is not False or restart.get('restartPolicyOutcome')!='missing-cookie':
        raise ValueError('Restart proof loss requires actually missing AUTH and PREAUTH cookies')
    return dict(recovery_executions=recovery_count,gym_ui=gym_count,native_restart=restart_count,policy_ui=policy_count,onboarding_ui=onboarding_count,production='BLOCKED')

if __name__=='__main__':
    try:
        if len(sys.argv)!=6:raise ValueError('Pass recovery, gym UI, native restart policy UI and onboarding JSON paths')
        print(json.dumps(check(*sys.argv[1:]),sort_keys=True))
    except (ValueError,TypeError,OSError) as error:
        print(str(error),file=sys.stderr);sys.exit(1)
