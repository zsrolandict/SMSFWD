#!/usr/bin/env bash
set -euo pipefail
cd /workspace/SMSFWD
mkdir -p /workspace/toolchains/java-home /workspace/toolchains/android-user
python3 - <<'PY'
import pathlib,urllib.request,hashlib,tarfile,zipfile,shutil
root=pathlib.Path('/workspace/toolchains')
jdk=root/'jdk/jdk-21.0.12.1+1'
if not (jdk/'bin/javac').exists():
 p=root/'jdk.tar.gz'
 urllib.request.urlretrieve('https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz',p)
 assert hashlib.sha256(p.read_bytes()).hexdigest()=='ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94','JDK checksum mismatch'
 (root/'jdk').mkdir(exist_ok=True)
 with tarfile.open(p) as t:t.extractall(root/'jdk',filter='data')
 p.unlink()
sdk=root/'android'
if not (sdk/'cmdline-tools/latest/bin/sdkmanager').exists():
 sdk.mkdir(exist_ok=True)
 p=root/'android-tools.zip'
 urllib.request.urlretrieve('https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip',p)
 assert hashlib.sha1(p.read_bytes()).hexdigest()=='e025545c62a8e64c7559119566a569fb1dec5f60','Android tools checksum mismatch'
 staging=root/'android-tools-extracted'
 with zipfile.ZipFile(p) as z:z.extractall(staging)
 (sdk/'cmdline-tools').mkdir(exist_ok=True)
 shutil.move(str(staging/'cmdline-tools'),str(sdk/'cmdline-tools/latest'))
 for f in (sdk/'cmdline-tools/latest/bin').iterdir(): f.chmod(0o755)
 p.unlink();staging.rmdir()
PY
source scripts/cloud-env.sh
if [[ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" || ! -x "$ANDROID_HOME/build-tools/35.0.0/aapt2" ]]; then
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'
fi
npm ci --no-audit --no-fund
npm run android:sync
npm test
bash scripts/build-android.sh
