#!/usr/bin/env python3
"""Create a reviewable public-source snapshot, never a copy of the working folder."""
from pathlib import Path
import hashlib
import subprocess
import sys
import zipfile
from source_manifest import public_files

root = Path(__file__).resolve().parents[1]
subprocess.run([sys.executable, str(root / 'scripts/check-public-source.py')], check=True)
files = list(public_files(root))
output = root / 'artifacts/Ramble-Android-source.zip'
output.parent.mkdir(exist_ok=True)
temp = output.with_suffix('.zip.partial')
try:
    with zipfile.ZipFile(temp, 'w', zipfile.ZIP_DEFLATED) as archive:
        for file in files:
            archive.write(file, 'Ramble-Android/' + file.relative_to(root).as_posix())
    temp.replace(output)
finally:
    temp.unlink(missing_ok=True)
(output.parent / (output.name + '.sha256')).write_text(hashlib.sha256(output.read_bytes()).hexdigest() + '  ' + output.name + '\n')
print(f'Packaged {len(files)} reviewed source files: {output}')
