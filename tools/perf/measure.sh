#!/bin/bash
# Usage: measure.sh <label>   — runs on CT 200 against the A50
A="adb -s R58M463R4JP"; P=com.websnake.recentstv; L=$1
out=${OUT:-/tmp}/$L.txt; : > $out
log(){ echo "$*" | tee -a $out; }
$A shell "input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard"
ttc(){ # time from am start to the "read N tasks" log line (content ready)
  $A logcat -c; t0=$($A shell 'date +%s%3N'); $A shell am start -W -n $P/.RecentsActivity | grep -E "LaunchState|TotalTime" | tr '\n' ' '
  sleep 2.5; line=$($A logcat -d -v epoch -s RecentsRepo:I RTV:I | grep -E "read [0-9]+ tasks|content" | head -1)
  t1=$(echo "$line" | awk '{print $1}' | tr -d . ); echo " content_ms=$(( ${t1:0:13} - t0 ))"
}
for i in 1 2 3 4; do $A shell am force-stop $P; $A shell input keyevent HOME; sleep 1.5; log "cold$i $(ttc)"; $A shell input keyevent BACK; sleep 1; done
for i in 1 2 3 4; do $A shell input keyevent HOME; sleep 1.5; log "warm$i $(ttc)"; $A shell input keyevent BACK; sleep 1; done
# animation smoothness: 30 D-pad moves
$A shell am start -W -n $P/.RecentsActivity >/dev/null; sleep 2; $A shell dumpsys gfxinfo $P reset >/dev/null
for i in $(seq 1 6); do $A shell "input keyevent DPAD_LEFT; input keyevent DPAD_LEFT; input keyevent DPAD_LEFT; input keyevent DPAD_RIGHT; input keyevent DPAD_RIGHT"; sleep 0.4; done
$A shell dumpsys gfxinfo $P | grep -E "Total frames|Janky|50th|90th|95th|99th|Slow UI|Slow issue|Slow bitmap" | sed 's/^/  /' | tee -a $out
$A shell input keyevent BACK; sleep 5
# idle ("asleep") cost: process state, memory, CPU over 60 s
log "idle: $($A shell "dumpsys meminfo $P | grep -E 'TOTAL PSS|TOTAL:' | head -1")"
pid=$($A shell pidof $P); log "pid=$pid"
if [ -n "$pid" ]; then
  c0=$($A shell "cat /proc/$pid/stat" | awk '{print $14+$15}'); w0=$($A shell "cat /proc/$pid/status" | grep -E "voluntary_ctxt" | awk '{s+=$2} END{print s}')
  sleep 60
  c1=$($A shell "cat /proc/$pid/stat" | awk '{print $14+$15}'); w1=$($A shell "cat /proc/$pid/status" | grep -E "voluntary_ctxt" | awk '{s+=$2} END{print s}')
  log "idle60s cpu_ticks=$((c1-c0)) ctx_switches=$((w1-w0)) threads=$($A shell "ls /proc/$pid/task | wc -l")"
  log "oom_adj/procstate: $($A shell "dumpsys activity processes $P | grep -E 'oom:|procState|curProcState' | head -3 | tr -s ' '")"
fi
