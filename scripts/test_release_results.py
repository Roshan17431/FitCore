import pathlib
import subprocess
import tempfile
import unittest

CHECKER = pathlib.Path(__file__).with_name('check_release_results.py')
REQUIRED = ['FoundationMigrationTest', 'TenantRecoveryTest', 'TenantIsolationTest', 'TenantSessionTest', 'SecurityRegressionTest', 'AccountSecurityTest', 'SharedAbuseOutageTest']

class ReleaseResultsTest(unittest.TestCase):
    def run_check(self, changed=None, missing=None):
        with tempfile.TemporaryDirectory() as folder:
            for name in REQUIRED:
                if name == missing:
                    continue
                attrs = changed if changed and name == 'TenantIsolationTest' else 'tests="2" failures="0" errors="0" skipped="0"'
                pathlib.Path(folder, f'TEST-{name}.xml').write_text(f'<testsuite name="com.fitcore.gym.{name}" {attrs}/>')
            return subprocess.run(['python3', str(CHECKER), folder], capture_output=True, text=True)
    def test_all_required_suites_pass(self):
        self.assertEqual(self.run_check().returncode, 0)
    def test_security_failure_blocks(self):
        self.assertNotEqual(self.run_check('tests="2" failures="1" errors="0" skipped="0"').returncode, 0)
    def test_skipped_postgres_blocks(self):
        self.assertNotEqual(self.run_check('tests="2" failures="0" errors="0" skipped="2"').returncode, 0)
    def test_absent_recovery_blocks(self):
        self.assertNotEqual(self.run_check(missing='TenantRecoveryTest').returncode, 0)
    def test_zero_test_security_suite_blocks(self):
        self.assertNotEqual(self.run_check('tests="0" failures="0" errors="0" skipped="0"').returncode, 0)

if __name__ == '__main__':
    unittest.main()
