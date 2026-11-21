#!/usr/bin/env bash
set -euo pipefail

# Run real platform APIs on the minimum supported Android version. Capture
# diagnostics before the action shuts down the emulator, including on failure.
mkdir -p app/build/qa
test_status=0
gradle connectedDebugAndroidTest --no-daemon --stacktrace || test_status=$?
adb logcat -d > app/build/qa/android-26-logcat.txt || true

copy_status=0
adb pull /sdcard/Android/data/br.com.educatransito/files/qa/. app/build/qa/ || copy_status=$?
if (( test_status != 0 )); then
    exit "$test_status"
fi
if (( copy_status != 0 )); then
    exit "$copy_status"
fi

# Missing sample output must not be mistaken for completed visual verification.
test -s app/build/qa/relatorio-simples-teste.pdf
test -s app/build/qa/relatorio-detalhado-teste.pdf
