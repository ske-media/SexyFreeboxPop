"""Local ADB configuration; no Player addresses are committed."""
import os
import shutil
from pathlib import Path

def connection():
    root = Path(__file__).resolve().parent
    device = os.environ.get('SALON_DEVICE', '')
    config = root / 'tools/device.txt'
    if not device and config.exists():
        device = config.read_text().strip()
    if not device:
        raise SystemExit('Définissez SALON_DEVICE avec IP:port du Player.')
    adb = os.environ.get('SALON_ADB') or shutil.which('adb') or str(Path.home()/'.local/share/freebox-tools/platform-tools/adb')
    if not Path(adb).is_file():
        raise SystemExit('Installez Android SDK Platform-Tools ou définissez SALON_ADB.')
    return [adb, '-s', device]
