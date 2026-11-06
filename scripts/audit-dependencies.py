#!/usr/bin/env python3
"""Query OSV for every exact Maven coordinate in the checked-in lockfile.

Only public package names/versions are sent. Network/parse failures fail the check;
a clean result means no advisory match in this database, not a security guarantee.
"""
import argparse
import datetime
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, default=ROOT / 'artifacts/dependency-osv-current.json')
    args = parser.parse_args()
    coordinates = set()
    for line in (ROOT / 'app/gradle.lockfile').read_text().splitlines():
        name = line.split('=', 1)[0]
        if not line.startswith('#') and name.count(':') == 2:
            group, artifact, version = name.split(':')
            coordinates.add((group + ':' + artifact, version))
    if not coordinates:
        raise RuntimeError('No locked Maven dependencies found')
    coordinates = sorted(coordinates)
    findings = []
    for start in range(0, len(coordinates), 150):
        batch = coordinates[start:start + 150]
        request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
            data=json.dumps({'queries': [{'package': {'ecosystem': 'Maven', 'name': name}, 'version': version}
                                         for name, version in batch]}).encode(),
            headers={'Content-Type': 'application/json', 'User-Agent': 'ramble-dependency-review'})
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)['results']
        if len(result) != len(batch):
            raise RuntimeError('Incomplete OSV response')
        for (name, version), item in zip(batch, result):
            if item.get('vulns'):
                findings.append({'package': name, 'version': version, 'advisories': item['vulns']})
    report = {'checked_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'database': 'OSV', 'packages_checked': len(coordinates), 'findings': findings}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(f'OSV: {len(coordinates)} locked coordinates checked, {len(findings)} affected coordinates')
    for item in findings:
        print(item['package'] + ':' + item['version'] + ' — ' + ', '.join(v['id'] for v in item['advisories']))
    return bool(findings)

if __name__ == '__main__':
    raise SystemExit(main())
