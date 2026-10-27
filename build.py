#!/usr/bin/env python3
"""Reproducible small APK build using Google build-tools and a local JDK 17."""
from pathlib import Path
import os, subprocess, zipfile

ROOT = Path(__file__).resolve().parent
bundled_jdk = next((ROOT / 'tools').glob('jdk-*/Contents/Home'), None)
java_home = os.environ.get('JAVA_HOME') or bundled_jdk
if not java_home:
    raise SystemExit('Installez un JDK 17 et définissez JAVA_HOME.')
JDK = Path(java_home)
SDK = Path(os.environ.get('ANDROID_HOME', ROOT / 'tools/sdk'))
BT = SDK / 'build-tools/34.0.0'
if not BT.exists(): BT = SDK / 'android-14'
ANDROID = SDK / 'platforms/android-29/android.jar'
if not ANDROID.exists(): ANDROID = SDK / 'android-10/android.jar'
if not (BT/'aapt2').is_file() or not ANDROID.is_file():
    raise SystemExit('Installez platforms;android-29 et build-tools;34.0.0 et définissez ANDROID_HOME.')
BUILD = ROOT / 'build'
OUT = ROOT / 'output'
ENV = dict(os.environ, JAVA_HOME=str(JDK), PATH=str(JDK / 'bin') + os.pathsep + os.environ['PATH'])
def run(*args):
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT, env=ENV)
for p in [BUILD / 'classes', BUILD / 'dex', OUT, ROOT / 'tools']: p.mkdir(parents=True, exist_ok=True)
run(BT/'aapt2','compile','--dir',ROOT/'app/res','-o',BUILD/'resources.zip')
run(BT/'aapt2','link','-o',BUILD/'base.apk','-I',ANDROID,'--manifest',ROOT/'app/AndroidManifest.xml',BUILD/'resources.zip')
run(JDK/'bin/javac','-source','8','-target','8','-encoding','UTF-8','-classpath',ANDROID,'-d',BUILD/'classes',*sorted((ROOT/'app/src').rglob('*.java')))
run(BT/'d8','--lib',ANDROID,'--min-api','26','--output',BUILD/'dex',*sorted((BUILD/'classes').rglob('*.class')))
with zipfile.ZipFile(BUILD/'base.apk','a',zipfile.ZIP_DEFLATED) as apk:
    apk.write(BUILD/'dex/classes.dex','classes.dex')
run(BT/'zipalign','-f','4',BUILD/'base.apk',BUILD/'aligned.apk')
key=ROOT/'tools/salon-development.keystore'
if not key.exists():
    run(JDK/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','salon','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Salon TV Local Development')
run(BT/'apksigner','sign','--ks',key,'--ks-key-alias','salon','--ks-pass','pass:android','--key-pass','pass:android','--out',OUT/'salon-tv.apk',BUILD/'aligned.apk')
run(BT/'apksigner','verify',OUT/'salon-tv.apk')
print(OUT/'salon-tv.apk')
