#!/usr/bin/env python3
"""Fail closed on private paths and recognizable credentials; never print matched values.

Default: inspect only the explicit public-source manifest. --git-index also checks
all tracked paths/content (including files an ignore rule would otherwise hide).
Gitleaks is an additional independent scan, not replaced by these privacy rules.
"""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from source_manifest import allowed, public_files

ROOT = Path(__file__).resolve().parents[1]
RULES = {
    'provider credential': re.compile(r'(?:gsk_[A-Za-z0-9]{20,}|sk-(?:proj-)?[A-Za-z0-9_-]{20,}|AIza[A-Za-z0-9_-]{30,})'),
    'GitHub credential': re.compile(r'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})'),
    'private key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'personal machine path': re.compile('/' + r'Users/[^/\s]+'),
    'device serial': re.compile(r'\bR3C[A-Z0-9]{7,}\b'),
    'publisher OAuth inventory': re.compile(r'\b\d{10,}-[a-z0-9]+\.apps\.googleusercontent\.com\b'),
    'account email': re.compile(r'\b[A-Za-z0-9._%+-]+@(?!example\.(?:com|org|net)\b)[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b'),
}


def findings(path, data):
    try:
        text = data.decode('utf-8')
    except UnicodeDecodeError:
        return []  # Binary images/audio/JARs require the separate artifact/visual review.
    return [(str(path), text.count('\n', 0, m.start()) + 1, category)
            for category, rule in RULES.items() for m in rule.finditer(text)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--git-index', action='store_true')
    args = parser.parse_args()
    errors = []
    if args.git_index:
        tracked = subprocess.run(['git', 'ls-files', '-z'], cwd=ROOT, capture_output=True, check=True).stdout.split(b'\0')
        paths = [Path(p.decode()) for p in tracked if p]
    else:
        paths = [p.relative_to(ROOT) for p in public_files(ROOT)]
    reviewed_assets = json.loads((ROOT / 'scripts/public-binary-assets.json').read_text())
    for path in paths:
        file = ROOT / path
        symlink = any((ROOT / Path(*path.parts[:i])).is_symlink() for i in range(1, len(path.parts) + 1))
        if not allowed(path) or not file.is_file() or symlink:
            errors.append((str(path), 0, 'path outside reviewed source boundary'))
            continue
        data = (subprocess.run(['git', 'show', ':' + path.as_posix()], cwd=ROOT, capture_output=True, check=True).stdout
                if args.git_index else file.read_bytes())
        errors.extend(findings(path, data))
        try:
            data.decode('utf8')
        except UnicodeDecodeError:
            if reviewed_assets.get(path.as_posix()) != hashlib.sha256(data).hexdigest():
                errors.append((str(path), 0, 'binary asset needs explicit privacy/provenance review'))
    for path, line, category in errors:
        print(f'{path}:{line}: {category} [value withheld]')
    print(f'Public source check: {len(paths)} files, {len(errors)} findings')
    return bool(errors)


if __name__ == '__main__':
    raise SystemExit(main())
