#!/usr/bin/env python3
"""Sign an already-built unsigned APK with a persistent private local Android identity."""
from pathlib import Path
import os,subprocess,secrets,hashlib,json,re
root=Path(__file__).resolve().parents[1]
private=Path.home()/'.local/share/ramble-android/signing'
private.mkdir(parents=True,exist_ok=True,mode=0o700);private.chmod(0o700)
password=private/'password';store=private/'release.jks'
if not password.exists():password.write_text(secrets.token_urlsafe(48));password.chmod(0o600)
env=dict(os.environ,RAMBLE_SIGNING_PASSWORD=password.read_text().strip())
java=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home'))
if not store.exists():
 subprocess.run([str(java/'bin/keytool'),'-genkeypair','-keystore',str(store),'-storepass:env','RAMBLE_SIGNING_PASSWORD','-keypass:env','RAMBLE_SIGNING_PASSWORD','-alias','ramble','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Ramble Local Release'],env=env,check=True)
 store.chmod(0o600)
sdk=Path(os.environ.get('ANDROID_HOME',str(root/'.tooling/android-sdk')))
tools=sdk/'build-tools/36.0.0';out=root/'artifacts';out.mkdir(exist_ok=True)
source=root/'app/build/outputs/apk/release/app-release-unsigned.apk'
version=json.loads((source.parent/'output-metadata.json').read_text())['elements'][0]['versionName']
if not re.fullmatch(r'[A-Za-z0-9._-]+', version):raise SystemExit('Invalid APK version name')
aligned=out/'ramble-flow-aligned.apk';final=out/f'Ramble-{version}.apk'
subprocess.run([str(tools/'zipalign'),'-f','-p','4',str(source),str(aligned)],check=True)
subprocess.run([str(tools/'apksigner'),'sign','--ks',str(store),'--ks-key-alias','ramble','--ks-pass','env:RAMBLE_SIGNING_PASSWORD','--key-pass','env:RAMBLE_SIGNING_PASSWORD','--out',str(final),str(aligned)],env=dict(env,JAVA_HOME=str(java)),check=True)
subprocess.run([str(tools/'apksigner'),'verify','--verbose','--print-certs',str(final)],env=dict(env,JAVA_HOME=str(java)),check=True)
(out/(final.name+'.sha256')).write_text(hashlib.sha256(final.read_bytes()).hexdigest()+'  '+final.name+'\n')
aligned.unlink();print(final)
