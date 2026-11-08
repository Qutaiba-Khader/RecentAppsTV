#!/bin/bash
# flowmap JVM lane: runs the unit tests + JaCoCo inside CT 200 (where Gradle and the
# Android SDK live) on the folder flowmap is working in — the project itself, or a
# mutant copy. The host path /mnt/pve/Shared1Tb is /mnt/Shared1Tb inside the CT, so
# run `mutate` with TMPDIR on Shared1Tb:  TMPDIR=/mnt/pve/Shared1Tb/tmp-flowmap python3 tools/flow/flowmap.py mutate
set -e
here="$(pwd)"
ct="${here/#\/mnt\/pve\/Shared1Tb/\/mnt\/Shared1Tb}"
if [ "$ct" = "$here" ]; then echo "jvm-lane: $here is not on Shared1Tb, CT 200 cannot see it" >&2; exit 2; fi
exec pct exec 200 -- bash -c "cd '$ct' && ./gradlew -q :app:testDebugUnitTest --rerun :app:createDebugUnitTestCoverageReport"
