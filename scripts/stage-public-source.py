#!/usr/bin/env python3
"""Build a new, reviewed publication directory from the allowlist. Never git-add the work folder."""
import argparse
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from source_manifest import public_files

root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--output',required=True,type=Path)
a=p.parse_args();output=a.output.expanduser().resolve()
if output==root or root in output.parents:
    raise SystemExit('The public staging directory must be outside the working folder.')
if output.exists():
    raise SystemExit('Refusing to replace an existing directory. Choose a new empty destination.')
subprocess.run([sys.executable,str(root/'scripts/check-public-source.py')],check=True)
output.parent.mkdir(parents=True,exist_ok=True)
staging=Path(tempfile.mkdtemp(prefix=output.name+'-review-',dir=output.parent))
try:
    files=list(public_files(root))
    for file in files:
        target=staging/file.relative_to(root)
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(file,target)
    subprocess.run([sys.executable,str(staging/'scripts/check-public-source.py')],cwd=staging,check=True)
    staging.rename(output)
finally:
    if staging.exists():shutil.rmtree(staging)
print(f'Staged {len(files)} reviewed source files in {output}. No Git repository was initialized or published.')
