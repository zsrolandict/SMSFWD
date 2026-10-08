#!/usr/bin/env bash
set -euo pipefail
cd /workspace/SMSFWD
source scripts/cloud-env.sh
cd android
./gradlew -Duser.home=/workspace/toolchains/java-home --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
mkdir -p /workspace/artifacts
cp app/build/outputs/apk/debug/app-debug.apk /workspace/artifacts/SMSFWD-debug.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify /workspace/artifacts/SMSFWD-debug.apk
