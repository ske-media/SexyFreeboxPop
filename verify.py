#!/usr/bin/env python3
"""On-device smoke checks of real remote navigation and app handoffs."""
import json, subprocess, time, re
from device import connection
from pathlib import Path
ROOT=Path(__file__).resolve().parent
(ROOT/"output").mkdir(exist_ok=True)
BASE=connection()
def shell(command):return subprocess.check_output(BASE+['shell',command],text=True,timeout=25)
def home():shell('am start -W -n dev.salontv/.HomeActivity')
def keys(*values):
    for key in values:shell('input keyevent '+str(key))
def resumed():
    output=shell('dumpsys activity activities')
    return next((line.strip() for line in output.splitlines() if 'mResumedActivity:' in line),'')
def expect(pkg):
    until=time.monotonic()+12
    while time.monotonic()<until:
        line=resumed()
        if pkg+'/' in line:return line
    raise RuntimeError('Expected '+pkg+', saw '+line)
checks=[]
for name,moves,pkg in [('Free TV',0,'net.oqee.androidtv'),('Netflix',1,'com.netflix.ninja'),('YouTube',2,'com.google.android.youtube.tv')]:
    home();keys(*([22]*moves),23)
    checks.append({'check':name,'result':expect(pkg)})
    keys(3)
    checks.append({'check':'HOME after '+name,'result':expect('dev.salontv')})
home();keys(20,23)
checks.append({'check':'All apps via remote','result':expect('dev.salontv')})
shell('uiautomator dump /sdcard/salon-ui.xml')
xml=shell('cat /sdcard/salon-ui.xml')
assert 'Vos applications' in xml and 'Retour' in xml
subprocess.run(BASE+['exec-out','screencap','-p'],stdout=open(ROOT/'output/apps.png','wb'),check=True)
keys(4);home();keys(20,22,23)
shell('uiautomator dump /sdcard/salon-ui.xml')
assert 'Réglages du Player' in shell('cat /sdcard/salon-ui.xml')
keys(22,23)
checks.append({'check':'Play Store via settings','result':expect('com.android.vending')})
home();keys(22,22,22,23)
shell('uiautomator dump /sdcard/salon-ui.xml')
assert 'Votre écran, en grand.' in shell('cat /sdcard/salon-ui.xml')
subprocess.run(BASE+['exec-out','screencap','-p'],stdout=open(ROOT/'output/airplay-help.png','wb'),check=True)
keys(23)
checks.append({'check':'AirPlay via remote','result':expect('io.github.jqssun.airplay')})
home()
(ROOT/'output/smoke-checks.json').write_text(json.dumps(checks,indent=2,ensure_ascii=False))
print(json.dumps(checks,indent=2,ensure_ascii=False))
