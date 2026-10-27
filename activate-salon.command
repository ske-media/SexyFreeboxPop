#!/bin/zsh
set -e
source "${0:A:h}/device.zsh"
"$SALON_ADB" connect "$SALON_DEVICE"
"$SALON_ADB" -s "$SALON_DEVICE" shell am start -W -n dev.salontv/.HomeActivity
"$SALON_ADB" -s "$SALON_DEVICE" shell pm disable-user --user 0 net.oqee.launcher
"$SALON_ADB" -s "$SALON_DEVICE" shell cmd package set-home-activity dev.salontv/.HomeActivity
"$SALON_ADB" -s "$SALON_DEVICE" shell input keyevent KEYCODE_HOME
print "Accueil Salon activé."
