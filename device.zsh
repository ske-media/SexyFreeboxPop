# Shared by the macOS activation and restoration scripts.
SALON_ROOT="${0:A:h}"
SALON_ADB="${SALON_ADB:-$(command -v adb || true)}"
SALON_ADB="${SALON_ADB:-$HOME/.local/share/freebox-tools/platform-tools/adb}"
SALON_DEVICE="${1:-${SALON_DEVICE:-}}"
if [[ -z "$SALON_DEVICE" && -f "$SALON_ROOT/tools/device.txt" ]]; then
  SALON_DEVICE="$(< "$SALON_ROOT/tools/device.txt")"
fi
if [[ -z "$SALON_DEVICE" ]]; then
  print -u2 "Indiquez le Player : SALON_DEVICE=192.168.x.x:5555 $0"
  exit 1
fi
if [[ ! -x "$SALON_ADB" ]]; then
  print -u2 "ADB introuvable. Installez les Android SDK Platform-Tools ou définissez SALON_ADB."
  exit 1
fi
