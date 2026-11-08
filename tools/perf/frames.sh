#!/bin/bash
# frames.sh <label> : drive 30 D-pad moves at a human pace, then break frame time into phases
A="adb -s R58M463R4JP"; P=com.websnake.recentstv
$A shell "input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; input keyevent HOME"; sleep 1
$A shell am start -W -n $P/.RecentsActivity >/dev/null; sleep 2
$A shell dumpsys gfxinfo $P reset >/dev/null
# one shell, keys ~250 ms apart (a person pressing a remote)
$A shell 'for i in 1 2 3 4 5 6; do for k in 21 21 21 22 22 22; do input keyevent $k; done; done'
$A shell dumpsys gfxinfo $P framestats > /tmp/fs.txt
$A shell dumpsys gfxinfo $P | grep -E "Total frames|Janky|50th perc|90th perc|99th perc|gpu percentile" | sed "s/^/  /"
python3 - <<'PY'
import statistics as st
rows=[l.strip().split(',') for l in open('/tmp/fs.txt') if l.strip() and l.strip()[0].isdigit()]
hdr=None
for l in open('/tmp/fs.txt'):
    if l.startswith('Flags,'): hdr=l.strip().split(','); break
ix={h:i for i,h in enumerate(hdr)}
def d(r,a,b): return (int(r[ix[b]])-int(r[ix[a]]))/1e6
ok=[r for r in rows if r[0]=='0']
ph={'input+anim+layout(UI)':('IntendedVsync','DrawStart'),'record(UI)':('DrawStart','SyncQueued'),'sync':('SyncStart','IssueDrawCommandsStart'),'issue(RT)':('IssueDrawCommandsStart','SwapBuffers'),'swap->done':('SwapBuffers','FrameCompleted'),'total':('IntendedVsync','FrameCompleted')}
for k,(a,b) in ph.items():
    v=sorted(d(r,a,b) for r in ok)
    if v: print(f"  {k:24s} p50={v[len(v)//2]:.1f} p90={v[int(len(v)*.9)]:.1f} max={v[-1]:.1f}")
if 'GpuCompleted' in ix:
    v=sorted(d(r,'IssueDrawCommandsStart','GpuCompleted') for r in ok if int(r[ix['GpuCompleted']])>0)
    if v: print(f"  {'gpu(issue->done)':24s} p50={v[len(v)//2]:.1f} p90={v[int(len(v)*.9)]:.1f}")
print("  frames",len(ok))
PY
$A shell input keyevent BACK
