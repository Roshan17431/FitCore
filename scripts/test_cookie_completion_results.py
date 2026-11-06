import copy
import json
import pathlib
import subprocess
import tempfile
import unittest

CHECKER=pathlib.Path(__file__).with_name('check_cookie_completion_results.py')
RECOVERY=['gym-1-incorrect-password-retry','gym-1-explicit-login-after-denial','gym-2-incorrect-password-retry','gym-2-explicit-login-after-denial','tab-close-received-cookie-recovery','lost-ack-receipt-recovery','lost-logout-receipt-recovery','old-cookie-recovery-remains-held','missing-cookie-recovery-remains-held','controlled-rate-denial-explicit-retry']
GYM_CASES=['owner-cookie-login','owner-plan-create','owner-exercise-create','owner-workout-draft','admin-cookie-login','admin-plan-update','admin-exercise-update','admin-workout-update-publish','referenced-exercise-name-protection','published-exercise-archive-protection','nutrition-catalog-create-update','member-cookie-login','member-local-catalogs','member-subscribe','member-renew','member-upgrade','member-workout-log','member-nutrition-log','member-progress-weight-bmi','member-attendance','member-booking','member-booking-conflict','peer-cookie-login','peer-booking-conflict','member-manager-route-denial','trainer-current-policy','admin-current-policy','catalog-archive']
GYM=[f'gym-{g}.{c}' for g in [1,2] for c in GYM_CASES]+['cookie-ui.cross-gym-persisted-catalog-isolation','cookie-ui.same-origin-no-bearer-no-runtime-errors']
POLICY=[f'gym-{g}.{c}' for g in [1,2] for c in ['expired-current-explicit-logout-reauthentication','password-change-explicit-logout-reauthentication','role-revocation-explicit-logout-reauthentication','disabled-account-explicit-logout-denied']]
RECOVERY += [f'gym-{g}-{c}' for g in [1,2] for c in ['same-revision-pre-mutation-remains-held','raw-different-family-recovery-remains-held']]
RESTART=['native-browser-restart-retains-fence','native-browser-restart-bound-proof-policy']
def report(suite,checks):return dict(suite=suite,passed=len(checks),checks=checks.copy(),failures=[],production='BLOCKED',browserType='chromium',browserVersion='1.2.3')
class CookieCompletionGateTest(unittest.TestCase):
    def run_check(self,change=None):
        recovery={'engines':[dict(engine=e,report=report('fitcore-cookie-recovery-v1',RECOVERY)) for e in ['chrome','firefox','webkit']]}
        for e in recovery['engines']:e['report']['browserType']='chromium' if e['engine']=='chrome' else e['engine']
        gym=report('fitcore-cookie-gym-ui-v1',GYM);restart=report('fitcore-cookie-native-restart-v1',RESTART)
        policy=report('fitcore-cookie-policy-ui-v1',POLICY)
        restart.update(receivedCookieProof=False,restartPolicyOutcome='missing-cookie',cookiePresence={'auth':False,'preauth':False},boundCandidate=False)
        if change=='copied-chrome':recovery['engines'][1]['report']=copy.deepcopy(recovery['engines'][0]['report'])
        if change=='received-cookie':restart.update(receivedCookieProof=True,restartPolicyOutcome='received-cookie',cookiePresence={'auth':True,'preauth':True},boundCandidate=True)
        if change=='received-unbound':restart.update(receivedCookieProof=True,restartPolicyOutcome='received-cookie',cookiePresence={'auth':True,'preauth':True},boundCandidate=False)
        if change=='missing-with-cookie':restart['cookiePresence']['preauth']=True
        if change=='missing-browser-type':recovery['engines'][2]['report'].pop('browserType')
        if change=='empty-recovery-version':recovery['engines'][1]['report']['browserVersion']='  '
        if change=='empty-version':restart['browserVersion']=''
        if change=='wrong-native-engine':restart['browserType']='webkit'
        if change=='wrong-gym-engine':gym['browserType']='firefox'
        if change=='missing-policy':policy['checks'].pop();policy['passed']-=1
        if change=='missing-restart-cookie-evidence':restart.pop('cookiePresence')
        if change=='restart-outage':restart.update(receivedCookieProof=False,restartPolicyOutcome='unexpected-status',cookiePresence={'auth':True,'preauth':False})
        if change=='missing-engine':recovery['engines'].pop()
        if change=='duplicate-engine':recovery['engines'][-1]['engine']='chrome'
        if change=='missing-recovery':recovery['engines'][0]['report']['checks'].pop();recovery['engines'][0]['report']['passed']-=1
        if change=='missing-gym':gym['checks'].pop();gym['passed']-=1
        if change=='missing-restart':restart['checks'].pop();restart['passed']-=1
        if change=='failure':gym['failures']=['unverified']
        if change=='bad-count':gym['passed']+=1
        if change=='duplicate-case':gym['checks'][-1]=gym['checks'][0]
        if change=='unexpected-suite':gym['suite']='old-unrelated'
        if change=='production-ready':restart['production']='READY'
        with tempfile.TemporaryDirectory() as folder:
            paths=[]
            for n,data in enumerate([recovery,gym,restart,policy]):
                p=pathlib.Path(folder,str(n)+'.json');p.write_text(json.dumps(data));paths.append(str(p))
            return subprocess.run(['python3',str(CHECKER),*paths],capture_output=True,text=True)
    def test_all_exact_evidence_passes(self):
        self.assertEqual(self.run_check().returncode,0)
        self.assertEqual(self.run_check('received-cookie').returncode,0)
    def test_incomplete_or_invalid_evidence_is_rejected(self):
        for change in ['received-unbound','missing-with-cookie','missing-browser-type','empty-recovery-version','copied-chrome','empty-version','wrong-native-engine','wrong-gym-engine','missing-policy','missing-restart-cookie-evidence','restart-outage','missing-engine','duplicate-engine','missing-recovery','missing-gym','missing-restart','failure','bad-count','duplicate-case','unexpected-suite','production-ready']:
            with self.subTest(change=change):self.assertNotEqual(self.run_check(change).returncode,0)
if __name__=='__main__':unittest.main()
