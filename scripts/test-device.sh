#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
adb_tool=${ADB:-adb}
device=${ANDROID_SERIAL:-emulator-5580}
"$adb_tool" -s "$device" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_tool" -s "$device" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$adb_tool" -s "$device" shell pm grant cn.edu.buaa.ledger android.permission.POST_NOTIFICATIONS
"$adb_tool" -s "$device" shell appops set cn.edu.buaa.ledger SCHEDULE_EXACT_ALARM allow
mkdir -p artifacts/tests
"$adb_tool" -s "$device" shell am instrument -w cn.edu.buaa.ledger.test/androidx.test.runner.AndroidJUnitRunner | tee artifacts/tests/device-tests.txt
if ! grep -Eq 'OK \([0-9]+ tests?\)' artifacts/tests/device-tests.txt; then
  exit 1
fi
