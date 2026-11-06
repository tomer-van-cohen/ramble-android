#!/usr/bin/env python3
"""Run synthetic Android tests via an explicitly chosen adb serial; no API key/cloud account."""
from pathlib import Path
import argparse,json,os,re,subprocess
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--serial',default='emulator-5554');args=p.parse_args()
adb=Path(os.environ.get('ANDROID_HOME',str(root/'.tooling/android-sdk')))/'platform-tools/adb'
base=[str(adb),'-s',args.serial]
# The full fixture suite clears app state and must never run on a personal handset.
hardware=subprocess.check_output(base+['shell','getprop','ro.hardware'],text=True).strip()
if hardware not in {'ranchu','goldfish'}:
 raise SystemExit('Refusing destructive fixture suite on physical hardware. Use a disposable emulator; run explicitly selected opt-in lab classes separately.')
for apk in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
 subprocess.run(base+['install','-r',str(root/apk)],check=True)
r=subprocess.run(base+['shell','am','instrument','-w','-r','io.github.tomervancohen.rambleflow.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,text=True,timeout=600)
log=root/'app/build/reports/direct-instrumentation.txt';log.parent.mkdir(parents=True,exist_ok=True);log.write_text(r.stdout+r.stderr)
match=re.search(r'OK \((\d+) tests\)',r.stdout)
passed=r.returncode==0 and match is not None and 'FAILURES!!!' not in r.stdout and 'Process crashed' not in r.stdout
result={'passed':passed,'tests':int(match.group(1)) if match else None,'serial':args.serial,'synthetic_audio':True,'provider':'in-process fake; no paid requests','log':str(log)}
(root/'app/build/reports/direct-instrumentation.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
raise SystemExit(0 if passed else 1)
