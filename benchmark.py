#!/usr/bin/env python3
"""Small local comparison; Android's activity timing is not time-to-content."""
import json, subprocess, re, statistics
from device import connection
from pathlib import Path
ROOT=Path(__file__).resolve().parent
(ROOT/"output").mkdir(exist_ok=True)
BASE=connection()+['shell']
def shell(cmd):return subprocess.check_output(BASE+[cmd],text=True,timeout=30)
result={}
for name,pkg,component in [('Free','net.oqee.launcher','net.oqee.launcher/net.oqee.tvlauncher.ui.home.HomeActivity'),('Salon','dev.salontv','dev.salontv/.HomeActivity')]:
    cold=[];warm=[]
    for i in range(3):
        shell('am force-stop '+pkg)
        out=shell('am start -W -n '+component)
        cold.append(int(re.search(r'TotalTime: (\d+)',out)[1]))
        # Move another real Activity in front, then return to the resident home.
        shell('am start -W -a android.settings.SETTINGS')
        out=shell('am start -W -n '+component)
        warm.append(int(re.search(r'TotalTime: (\d+)',out)[1]))
    result[name]={'cold_ms':cold,'cold_median_ms':statistics.median(cold),'warm_ms':warm,'warm_median_ms':statistics.median(warm)}
shell('am start -W -n dev.salontv/.HomeActivity')
(ROOT/'output/benchmark.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
