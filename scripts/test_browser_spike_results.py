import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest
from xml.sax.saxutils import quoteattr
CHECKER=pathlib.Path(__file__).with_name('check_browser_spike_results.py')
class BrowserGateTest(unittest.TestCase):
    def run_check(self,change=None,encoding='raw'):
        with tempfile.TemporaryDirectory() as folder:
            p=pathlib.Path(folder)
            # Literal mandatory evidence from independent transport and browser suites.
            names=['forwards raw bytes and raw target with a verified request-bound RS256 proof, stripping untrusted headers','never follows upstream redirects or retries a timed-out transition','cookie transport fails closed when Web Locks are unsupported','uncertain cookie delivery persists across client recreation without retry']
            baseline=names.copy()
            names += ['a pre-lock storage capability read cannot overwrite another client next-revision marker and strand recovery', 'controlled gym denial with changed proof remains held without confirming identity', 'recovery fails closed for uncorrelated context {"contextRevision":"7","familyRevision":"7"}', 'recovery fails closed for uncorrelated context {"familyId":"8b7cbd5c-1059-41d8-9494-37b1bc457b02"}', 'received-cookie recovery preserves a replacement marker during post-ACK status validation', 'queued lock is aborted within its bound and a late grant never runs protected work', 'expired or revoked current AUTH requires explicit logout and ACK before fresh credentials']
            names += ['unwritable storage fails closed inside the lock before any proof or writer request', 'native restart rejects 503 with still present AUTH', 'native restart rejects other family', 'test hold-before gate forwards no request until its exact held request is released']
            if change and change.startswith('missing-critical-'):names.pop(int(change.removeprefix('missing-critical-')))
            if change=='baseline-only':names=baseline
            report={'suite':'fitcore-browser-spike-v1','passed':len(CASES),'checks':CASES.copy(),'failures':[],'webLocks':True,'production':'BLOCKED'}
            shared={'passed':8,'checks':['shared-pending-fence','shared-session-ack-authority','shared-logout-retirement','shared-gateway-replay','shared-concurrent-login','raw-cookie-parser-fail-closed','shared-login-abuse-budget','shared-login-abuse-no-cookie'],'failures':[]}
            if change in ['missing-foreground','missing-pageshow']:
                report['checks'].remove('foreground-session-revalidation' if change=='missing-foreground' else 'persisted-pageshow-revalidation');report['passed']-=1
            if change=='missing-shared':shared['checks'].pop();shared['passed']-=1
            if encoding=='node22':names=[n.replace(chr(34),'&quot;') for n in names]
            if encoding=='overencoded':names=[n.replace(chr(34),'&amp;quot;') for n in names]
            xml='<testsuites>'+''.join('<testcase name='+quoteattr(n)+'/>' for n in names)+'</testsuites>'
            if change=='missing-node':xml='<testsuites><testcase name="unrelated"/></testsuites>'
            if change=='skipped-node':xml=xml.replace('/>','><skipped/></testcase>',1)
            if change=='missing-browser':report['checks']=report['checks'][:-1];report['passed']-=1
            if change=='duplicate-browser':report['checks'][-1]=report['checks'][0]
            if change=='bad-count':report['passed']+=1
            if change=='failed-browser':report['failures']=['security failed']
            if change=='unsupported-browser':report['webLocks']=False
            (p/'shared.json').write_text(json.dumps(shared));(p/'node.xml').write_text(xml);(p/'browser.json').write_text(json.dumps(report))
            return subprocess.run(['python3',str(CHECKER),str(p/'node.xml'),str(p/'browser.json'),str(p/'shared.json')],capture_output=True,text=True)
    def test_complete_evidence_passes(self):self.assertEqual(self.run_check().returncode,0)
    def test_node22_entity_encoded_exact_names_pass(self):
        self.assertEqual(self.run_check(encoding='node22').returncode,0)
    def test_each_exact_critical_name_is_required_in_both_xml_formats(self):
        for encoding in ['raw','node22']:
            for index in range(15):
                with self.subTest(encoding=encoding,index=index):
                    self.assertNotEqual(self.run_check(f'missing-critical-{index}',encoding).returncode,0)
            with self.subTest(encoding=encoding,baseline_only=True):
                self.assertNotEqual(self.run_check('baseline-only',encoding).returncode,0)
    def test_more_than_one_extra_entity_layer_is_rejected(self):
        self.assertNotEqual(self.run_check(encoding='overencoded').returncode,0)
    def test_missing_or_failed_security_evidence_blocks(self):
        for change in ['baseline-only','missing-node','skipped-node','missing-browser','duplicate-browser','bad-count','failed-browser','unsupported-browser','missing-shared','missing-foreground','missing-pageshow']:
            with self.subTest(change=change):self.assertNotEqual(self.run_check(change).returncode,0)
CASES=['secure-http-only-host-cookie','no-browser-auth-secrets','same-origin-no-store','legacy-bearer-route-blocked','unsafe-without-csrf-blocked','no-state-changing-get','cross-origin-blocked','client-tenant-selector-blocked',
 'gym-1-all-role-isolation','gym-1-membership-rules','gym-1-booking-conflict','gym-1-catalog-history','gym-2-all-role-isolation','gym-2-membership-rules','gym-2-booking-conflict','gym-2-catalog-history',
 'foreground-session-revalidation','persisted-pageshow-revalidation','pending-blocks-business','pending-blocks-second-login','pending-blocks-logout','two-tab-gym-change','logout-pending-blocks-login','logout-expires-auth-cookie','logout-no-old-session','refresh-back-session-restore','live-web-lock-serialization','queued-old-view-write-blocked','tab-close-uncertainty','timeout-uncertainty','no-uncaught-errors']
if __name__=='__main__':unittest.main()
