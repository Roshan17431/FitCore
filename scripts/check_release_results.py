#!/usr/bin/env python3
"""Fail closed when critical PostgreSQL/security suites were skipped or absent."""
import json
import pathlib
import sys
import xml.etree.ElementTree as ET

REQUIRED = {'FoundationMigrationTest', 'TenantRecoveryTest', 'TenantIsolationTest', 'TenantSessionTest', 'SecurityRegressionTest', 'AccountSecurityTest', 'SharedAbuseOutageTest'}

def check(folder):
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    found = set()
    reports = list(pathlib.Path(folder).glob('TEST-*.xml'))
    if not reports:
        raise ValueError('No JUnit results; release verification did not run')
    for report in reports:
        suite = ET.parse(report).getroot()
        values = {key: int(suite.get(key, '0')) for key in totals}
        if any(value < 0 for value in values.values()) or values['tests'] == 0:
            raise ValueError('Empty or invalid test suite')
        if values['failures'] or values['errors'] or values['skipped']:
            raise ValueError('Failed, errored or skipped tests block release')
        found.add(suite.get('name', '').split('.')[-1])
        for key in totals:
            totals[key] += values[key]
    if not REQUIRED <= found:
        raise ValueError('Missing critical suites: ' + ', '.join(sorted(REQUIRED - found)))
    return totals

if __name__ == '__main__':
    try:
        if len(sys.argv) != 2:
            raise ValueError('Pass one local Surefire report directory')
        print(json.dumps(check(sys.argv[1]), sort_keys=True))
    except (ValueError, ET.ParseError, OSError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
