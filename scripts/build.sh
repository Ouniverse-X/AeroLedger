#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# This workstation has an old /usr/local zlib that corrupts Java ZIP output.
# Scope the workaround to this build; no system library or global config is changed.
if [[ "$(uname -s)" == Linux && -e /usr/local/lib/libz.so.1.2.9 && -e /lib/x86_64-linux-gnu/libz.so.1 ]]; then
  export LD_PRELOAD="/lib/x86_64-linux-gnu/libz.so.1${LD_PRELOAD:+:$LD_PRELOAD}"
fi
if (( $# )); then
  exec ./gradlew "$@"
else
  exec ./gradlew :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --max-workers=4
fi
