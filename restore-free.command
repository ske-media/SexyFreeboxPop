#!/bin/zsh
set -e
source "${0:A:h}/device.zsh"
"$SALON_ADB" connect "$SALON_DEVICE"
"$SALON_ADB" -s "$SALON_DEVICE" shell pm enable --user 0 net.oqee.launcher
"$SALON_ADB" -s "$SALON_DEVICE" shell cmd package set-home-activity net.oqee.launcher/net.oqee.tvlauncher.ui.home.HomeActivity
"$SALON_ADB" -s "$SALON_DEVICE" shell input keyevent KEYCODE_HOME
print "Accueil Free restauré. Salon reste disponible dans les applications."
