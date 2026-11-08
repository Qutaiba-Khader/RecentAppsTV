#!/usr/bin/env python3
"""flowmap: a stored, complete, deterministic map of a project's code paths, with a coverage gate.

One file, Python 3 standard library only. A project keeps its own copy at tools/flow/flowmap.py.

  flowmap.py init [--name N]         set a project up (never overwrites)
  flowmap.py scout                   baseline run: gaps, and a rough cost estimate. Changes nothing tracked
  flowmap.py check [--no-run] [--twice] [--strict-det]
                                     THE FULL PASS. CLEAN means stop.
  flowmap.py quick [--base REF]      check after a change: only the affected tests + the map's consistency
  flowmap.py update                  mechanical map update: line numbers, skeleton rows, entries, totals
  flowmap.py justify FILE:LINE CLASS "REASON" [--map AREA]
  flowmap.py ledger freeze|show      big old project: today's gaps become debt that may only shrink
  flowmap.py mutate                  prove the gate tests can fail: apply each stored mutant, a test must fail
  flowmap.py hook install            pre-commit: update + quick; a failure blocks the commit
  flowmap.py helper routes|diff      OPTIONAL codebase-memory lookups. Never part of a pass
"""
import argparse
import bisect
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import xml.etree.ElementTree as ET
from urllib.parse import unquote, urlparse

CONFIG = 'flowmap.json'
OUT = os.environ.get('FLOW_OUT', '.flow')  # one folder per parallel run
VERSION = '1.0.0'
CLASSES = ('unreachable', 'defensive', 'external', 'presentation')
ID_RE = re.compile(r'\b(P-[A-Z0-9]+-\d+[a-z]?|G-[A-Z0-9]+-[a-z0-9-]+)\b')
ROW_ID_RE = re.compile(r'^\|\s*\**`?(P-[A-Z0-9]+-\d+[a-z]?|G-[A-Z0-9]+-[a-z0-9-]+)`?\**\s*\|', re.M)
JUST_RE = re.compile(r'^\|\s*`?([^\s`|:]+):(\d+)`?\s*\|\s*`?([0-9a-f]{10}(?:\.\d+)?)`?\s*\|\s*(\w+)\s*\|\s*(.*?)\s*\|\s*$', re.M)
SECTION_RE = re.compile(r'^#+\s*Not exercised', re.I | re.M)
WEAK_REASON = re.compile(r'hard to test|not worth|too hard|too long|no time|later|todo', re.I)
DEFECT_RE = re.compile(r"FLOWMAP-DEFECT|todo:\s*['\"`]DEFECT")
TOKENS_PER_GAP = 6500  # measured once: 1.07M agent tokens closed 163 gaps on the Stremio addon (2026-11-02)
RUN_ALL_FILES = ('flowmap.json', 'package.json', 'package-lock.json', 'pyproject.toml', 'requirements.txt', 'setup.py', 'go.mod', 'build.gradle', 'build.gradle.kts', 'Cargo.toml')


def rd(path):
    with open(path, encoding='utf8', errors='replace') as fh:
        return fh.read()


def wr(path, text):
    with open(path, 'w', encoding='utf8') as fh:
        fh.write(text)


def rj(path):
    return json.loads(rd(path))


def wj(path, value, **kw):
    wr(path, json.dumps(value, **kw))


def die(msg, code=2):
    print(f'flowmap: {msg}', file=sys.stderr)
    sys.exit(code)


def load_config(root='.'):
    p = os.path.join(root, CONFIG)
    if not os.path.exists(p):
        die(f'no {CONFIG} here: run `flowmap.py init` in the project root')
    cfg = rj(p)
    cfg.setdefault('maps', 'docs/flow-map')
    cfg.setdefault('index', 'docs/FLOW-MAP.md')
    cfg.setdefault('exclude', [])
    cfg.setdefault('tests', [])
    cfg.setdefault('lanes', [])
    cfg.setdefault('level', 'full')
    return cfg


def glob_match(path, pattern):
    """`**` spans folders, `*` stays inside one."""
    rx = ''
    i = 0
    while i < len(pattern):
        c = pattern[i]
        if pattern[i:i + 3] == '**/':
            rx += '(?:.*/)?'
            i += 3
        elif pattern[i:i + 2] == '**':
            rx += '.*'
            i += 2
        elif c == '*':
            rx += '[^/]*'
            i += 1
        elif c == '?':
            rx += '[^/]'
            i += 1
        else:
            rx += re.escape(c)
            i += 1
    return re.fullmatch(rx, path) is not None


def in_globs(path, patterns):
    return any(glob_match(path, p) for p in patterns)


def is_source(cfg, path):
    return in_globs(path, cfg.get('source', [])) and not in_globs(path, cfg['exclude'])


def walk_files(root='.'):
    out = []
    for d, dirs, files in os.walk(root):
        dirs[:] = sorted(x for x in dirs if x not in ('.git', 'node_modules', '__pycache__', OUT, 'build', 'dist', '.gradle', 'venv', '.venv', 'target'))
        for f in sorted(files):
            out.append(os.path.relpath(os.path.join(d, f), root).replace(os.sep, '/'))
    return out


def test_files(cfg):
    return [f for f in walk_files() if in_globs(f, cfg['tests'])]


# ------------------------------------------------------------------ source anchors

def normalized(line):
    return re.sub(r'\s+', ' ', line).strip()


class Source:
    """A file's lines and the anchor of each: the hash of its text and the code line before it (a bare `continue` repeats
    everywhere), plus an ordinal when both still repeat. A justification follows the anchor: it survives the lines moving, and
    fails when that code or the line above it changes."""
    cache = {}

    def __init__(self, path):
        self.lines = rd(path).split('\n')
        self.anchors = {}
        self.by_anchor = {}
        seen = {}
        before = ''
        for n, text in enumerate(self.lines, 1):
            h = hashlib.sha1(f'{before}\n{normalized(text)}'.encode()).hexdigest()[:10]
            before = normalized(text) or before
            seen[h] = seen.get(h, 0) + 1
            a = h if seen[h] == 1 else f'{h}.{seen[h]}'
            self.anchors[n] = a
            self.by_anchor[a] = n

    @classmethod
    def of(cls, path):
        if path not in cls.cache:
            cls.cache[path] = cls(path)
        return cls.cache[path]


# ------------------------------------------------------------------ coverage adapters
# Each returns {file: {'lines': {n: hit}, 'branches': [(line, code, hit)]}} and optionally an impact map {file: {test file}}.

NON_BMP = re.compile('[\U00010000-\U0010ffff]')
TRIVIAL_STRIP = re.compile(r'/\*[\s\S]*?\*/|//[^\n]*')


def trivial(text):
    return re.fullmatch(r'[\s})\];,]*', TRIVIAL_STRIP.sub('', text)) is not None


def read_node_v8(cfg, folder):
    """Merged BY SOURCE POSITION. The test runner's own lcov merge gave different branch lists from run to run and hid
    real gaps (163 on the addon), because each process reports different block ranges for the same code."""
    data = {}
    impact = {}
    tests = set(test_files(cfg))
    for name in sorted(os.listdir(folder)) if os.path.isdir(folder) else []:
        if not name.endswith('.json'):
            continue
        result = rj(os.path.join(folder, name)).get('result', [])
        rels = {}
        for script in result:
            if script['url'].startswith('file://'):
                rels[script['url']] = os.path.relpath(unquote(urlparse(script['url']).path)).replace(os.sep, '/')
        me = sorted(r for r in rels.values() if r in tests)
        for script in result:
            rel = rels.get(script['url'])
            if not rel or not is_source(cfg, rel):
                continue
            for t in me:
                impact.setdefault(rel, set()).add(t)
            d = data.get(rel)
            if d is None:
                # V8 counts UTF-16 units: a character outside the BMP is two of them
                text = NON_BMP.sub('\ufffd\ufffd', rd(rel))
                d = data[rel] = {'text': text, 'sum': 0, 'ranges': {}}
            seen = bytearray(len(d['text']))
            for fn in script['functions']:
                for r in fn['ranges']:
                    a, b = r['startOffset'], min(r['endOffset'], len(seen))
                    if a < b:
                        seen[a:b] = (b'\x01' if r['count'] > 0 else b'\x00') * (b - a)
                    d['ranges'][(r['startOffset'], r['endOffset'])] = 1
            d['sum'] |= int.from_bytes(seen, 'little')
    out = {}
    for rel, d in data.items():
        text = d['text']
        s = d['sum'].to_bytes(len(text) or 1, 'little')
        starts = [0]
        for m in re.finditer('\n', text):
            starts.append(m.end())
        lines = {}
        for n, st in enumerate(starts):
            en = starts[n + 1] if n + 1 < len(starts) else len(text)
            t = text[st:en]
            if trivial(t):
                continue
            lines[n + 1] = any(s[i] and not text[i].isspace() for i in range(st, en))
        branches = []
        for (a, b) in sorted(d['ranges']):
            b = min(b, len(text))
            if trivial(text[a:b]):
                continue
            i = a
            # the range's first real code decides, and names the line a person would look at (not the `{` before it)
            while i < b and text[i] in ' \t\r\n{})];,':
                i += 1
            if i >= b:
                continue
            branches.append((bisect.bisect_right(starts, i), re.sub(r'\s+', ' ', text[i:min(i + 70, b)]), bool(s[i])))
        out[rel] = {'lines': lines, 'branches': branches}
    return out, impact


def read_coverage_py(cfg, report, tests=None):
    j = rj(report)
    out = {}
    impact = {}
    stems = {}
    for t in tests if tests is not None else test_files(cfg):
        stems[os.path.splitext(os.path.basename(t))[0]] = t
    for rel, f in j.get('files', {}).items():
        rel = rel.replace(os.sep, '/')
        if not is_source(cfg, rel):
            continue
        lines = {n: True for n in f.get('executed_lines', [])}
        lines.update({n: False for n in f.get('missing_lines', [])})
        branches = [(a, f'branch to {b}', True) for a, b in f.get('executed_branches', [])]
        branches += [(a, f'branch to {b}', False) for a, b in f.get('missing_branches', [])]
        out[rel] = {'lines': lines, 'branches': branches}
        for ctxs in (f.get('contexts') or {}).values():
            for c in ctxs:
                for part in c.split('.'):
                    if part in stems:
                        impact.setdefault(rel, set()).add(stems[part])
    return out, impact


TAILS = {}


def resolve_source(cfg, name):
    """A report's path to the project's file, by the longest matching tail."""
    name = name.replace('\\', '/')
    if os.path.exists(name):
        return os.path.relpath(name).replace(os.sep, '/')
    cwd = os.getcwd()
    if cwd not in TAILS:
        TAILS[cwd] = {}
        for f in walk_files():
            TAILS[cwd].setdefault(os.path.basename(f), []).append(f)
    cache = TAILS[cwd]
    best = None
    for f in cache.get(os.path.basename(name), []):
        if name.endswith(f) or f.endswith(name):
            if best is None or len(f) > len(best):
                best = f
    return best


def read_lcov(cfg, report):
    out = {}
    cur = None
    for raw in rd(report).split('\n'):
        l = raw.strip()
        if l.startswith('SF:'):
            rel = resolve_source(cfg, l[3:])
            cur = out.setdefault(rel, {'lines': {}, 'branches': []}) if rel and is_source(cfg, rel) else None
        elif cur is None:
            continue
        elif l.startswith('DA:'):
            n, c = l[3:].split(',')[:2]
            cur['lines'][int(n)] = cur['lines'].get(int(n), False) or int(c) > 0
        elif l.startswith('BRDA:'):
            n, block, br, taken = l[5:].split(',')
            cur['branches'].append((int(n), f'block {block} branch {br}', taken != '-' and int(taken) > 0))
    return out, {}


def read_jacoco(cfg, report):
    out = {}
    for pkg in ET.parse(report).getroot().iter('package'):
        for sf in pkg.iter('sourcefile'):
            rel = resolve_source(cfg, f"{pkg.get('name')}/{sf.get('name')}")
            if not rel or not is_source(cfg, rel):
                continue
            d = out.setdefault(rel, {'lines': {}, 'branches': []})
            for line in sf.iter('line'):
                n = int(line.get('nr'))
                d['lines'][n] = int(line.get('ci', 0)) > 0
                for i in range(int(line.get('cb', 0))):
                    d['branches'].append((n, f'branch {i}', True))
                for i in range(int(line.get('mb', 0))):
                    d['branches'].append((n, f'missed branch {i}', False))
    return out, {}


def read_gocover(cfg, report):
    """A Go coverprofile counts blocks: each block is a branch of the code around it."""
    out = {}
    for raw in rd(report).split('\n'):
        m = re.match(r'^(.+):(\d+)\.\d+,(\d+)\.\d+ \d+ (\d+)$', raw.strip())
        if not m:
            continue
        rel = resolve_source(cfg, m.group(1))
        if not rel or not is_source(cfg, rel):
            continue
        d = out.setdefault(rel, {'lines': {}, 'branches': []})
        a, b, hit = int(m.group(2)), int(m.group(3)), int(m.group(4)) > 0
        for n in range(a, b + 1):
            d['lines'][n] = d['lines'].get(n, False) or hit
        d['branches'].append((a, f'block to line {b}', hit))
    merged = {}
    for rel, d in out.items():
        best = {}
        for line, code, hit in d['branches']:
            best[(line, code)] = best.get((line, code), False) or hit
        merged[rel] = {'lines': d['lines'], 'branches': [(l, c, h) for (l, c), h in best.items()]}
    return merged, {}


READERS = {'lcov': read_lcov, 'jacoco': read_jacoco, 'gocover': read_gocover}


def lane_python(lane):
    return os.environ.get('FLOWMAP_PYTHON') or lane.get('python', sys.executable)


def run_lane(cfg, lane, out, files=None):
    """Run one lane's tests with coverage. `files` narrows to those test files when the lane can take a list."""
    adapter = lane.get('adapter', 'none')
    env = {**os.environ, **{k: str(v) for k, v in lane.get('env', {}).items()}}
    cmd = lane.get('command', '')
    if files is not None:
        cmd = lane['command_files'].replace('{files}', ' '.join(files))
    elif adapter == 'node-v8':
        folder = os.path.join(out, f"v8-{lane['name']}")
        shutil.rmtree(folder, ignore_errors=True)
        os.makedirs(folder)
        env['NODE_V8_COVERAGE'] = os.path.abspath(folder)
    elif adapter == 'coverage-py':
        py = lane_python(lane)
        rc = os.path.join(out, f"coveragerc-{lane['name']}")
        src = lane.get('coverage_source', '.')
        wr(rc, f'[run]\nbranch = True\nparallel = True\ndynamic_context = test_function\nsource = {src}\ndata_file = {os.path.abspath(out)}/.coverage-{lane["name"]}\n')
        for old in os.listdir(out):
            if old.startswith(f".coverage-{lane['name']}"):
                os.remove(os.path.join(out, old))
        cmd = f'{py} -m coverage run --rcfile={rc} {lane["test_args"]}'
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, env=env, timeout=lane.get('timeout_s', 3600))
    return r.returncode, (r.stdout or '') + (r.stderr or '')


def read_lane(cfg, lane, out):
    adapter = lane.get('adapter', 'none')
    if adapter == 'node-v8':
        return read_node_v8(cfg, os.path.join(out, f"v8-{lane['name']}"))
    if adapter == 'coverage-py':
        py = lane_python(lane)
        rc = os.path.join(out, f"coveragerc-{lane['name']}")
        j = os.path.join(out, f"coverage-{lane['name']}.json")
        subprocess.run(f'{py} -m coverage combine -q --keep --rcfile={rc}', shell=True, capture_output=True)
        r = subprocess.run(f'{py} -m coverage json -q --show-contexts --rcfile={rc} -o {j}', shell=True, capture_output=True, text=True)
        if r.returncode:
            raise RuntimeError(f'coverage json failed: {r.stderr[:200]}')
        return read_coverage_py(cfg, j)
    if adapter in READERS:
        return READERS[adapter](cfg, lane['report'])
    return {}, {}


def merge_cov(into, new):
    for f, d in new.items():
        cur = into.setdefault(f, {'lines': {}, 'branches': []})
        for n, h in d['lines'].items():
            cur['lines'][n] = cur['lines'].get(n, False) or h
        cur['branches'] += d['branches']


# ------------------------------------------------------------------ the stored map

def map_files(cfg):
    d = cfg['maps']
    return sorted(os.path.join(d, f) for f in os.listdir(d) if f.endswith('.md')) if os.path.isdir(d) else []


def split_section(text):
    m = SECTION_RE.search(text)
    return (text, '') if not m else (text[:m.start()], text[m.start():])


def read_map(cfg):
    rows, just, todos, bad = {}, {}, [], []
    for f in map_files(cfg):
        text = rd(f)
        body, sec = split_section(text)
        for i in ROW_ID_RE.findall(body):
            rows.setdefault(i, f)
        for line in body.split('\n'):
            if re.search(r'\|\s*TODO\s*\|', line):
                todos.append(f'{os.path.basename(f)}: {line[:90]}')
        for path, line, anchor, cls, reason in JUST_RE.findall(sec):
            key = (path, anchor)
            just[key] = {'map': f, 'line': int(line), 'class': cls, 'reason': reason}
            if cls not in CLASSES:
                bad.append(f'{path}:{line} class "{cls}" is not one of {", ".join(CLASSES)}')
            if len(reason) < 15 or WEAK_REASON.search(reason):
                bad.append(f'{path}:{line} the reason is not a reason: "{reason[:50]}"')
    return rows, just, todos, bad


def scan_tests(cfg):
    ids, defects = {}, []
    for f in test_files(cfg):
        for n, line in enumerate(rd(f).split('\n'), 1):
            for i in ID_RE.findall(line):
                ids.setdefault(i, f)
            if DEFECT_RE.search(line):
                defects.append(f'{f}:{n}')
    return ids, defects


def compute_gaps(cov):
    """Every line never run and every branch never taken, each under its anchor."""
    gaps = {}
    totals = {'lines': 0, 'lines_hit': 0, 'branches': 0, 'branches_hit': 0}
    for f in sorted(cov):
        src = Source.of(f) if os.path.exists(f) else None
        d = cov[f]
        totals['lines'] += len(d['lines'])
        totals['lines_hit'] += sum(1 for h in d['lines'].values() if h)
        totals['branches'] += len(d['branches'])
        totals['branches_hit'] += sum(1 for b in d['branches'] if b[2])
        if not src:
            continue
        for n in sorted(n for n, h in d['lines'].items() if not h):
            a = src.anchors.get(n)
            if a:
                gaps[(f, a)] = {'line': n, 'kind': 'line', 'code': normalized(src.lines[n - 1])[:70]}
        for line, code, hit in sorted(d['branches'], key=lambda b: (b[0], b[1])):
            a = src.anchors.get(line)
            if a and not hit and (f, a) not in gaps:
                gaps[(f, a)] = {'line': line, 'kind': 'branch', 'code': code[:70]}
    return gaps, totals


def source_files(cfg):
    return [f for f in walk_files() if is_source(cfg, f)]


def tree_hash(cfg):
    h = hashlib.sha1()
    for f in sorted(set(source_files(cfg) + test_files(cfg))):
        h.update(f.encode())
        h.update(open(f, 'rb').read())
    return h.hexdigest()


def ledger_path(cfg):
    return os.path.join(cfg['maps'], 'ledger.json')


def read_ledger(cfg):
    p = ledger_path(cfg)
    return set(map(tuple, rj(p))) if os.path.exists(p) else set()


def consistency(cfg):
    """What the map and the tests must agree on, with no test run."""
    problems = []
    rows, just, todos, bad = read_map(cfg)
    ids, defects = scan_tests(cfg)
    for i in sorted(set(rows) - set(ids)):
        problems.append(f'map row with no test: {i} ({os.path.basename(rows[i])})')
    for i in sorted(set(ids) - set(rows)):
        problems.append(f'test with no map row: {i} ({ids[i]})')
    problems += [f'unfilled row: {t}' for t in todos] + bad
    entries = os.path.join(cfg['maps'], 'entries.md')
    if os.path.exists(entries):
        for line in rd(entries).split('\n'):
            if re.search(r'\|\s*TODO\s*\|', line):
                problems.append(f'entry with no owner: {line.strip()[:90]}')
    return problems, defects, rows, just, ids


def lane_list(cfg):
    if not cfg['lanes']:
        die('flowmap.json has no lanes: say how the tests run')
    return cfg['lanes']


def gather(cfg, run=True):
    os.makedirs(OUT, exist_ok=True)
    cov, impact, failed, output = {}, {}, [], []
    for lane in lane_list(cfg):
        if run and (lane.get('command') is not None or lane.get('test_args')):
            code, text = run_lane(cfg, lane, OUT)
            if code:
                failed.append(lane['name'])
                output.append(f"--- lane {lane['name']} exit {code}\n" + '\n'.join(text.split('\n')[-30:]))
        try:
            c, i = read_lane(cfg, lane, OUT)
        except (RuntimeError, OSError, ValueError, ET.ParseError) as e:
            # a lane whose report cannot be read proves nothing: it fails the pass, it is never read as empty
            if lane['name'] not in failed:
                failed.append(lane['name'])
            output.append(f"--- lane {lane['name']}: no coverage could be read: {str(e)[:300]}")
            c, i = {}, {}
        merge_cov(cov, c)
        for k, v in i.items():
            impact.setdefault(k, set()).update(v)
    # A source file no test ever loaded is the largest gap of all: it would otherwise be silently missing.
    for f in source_files(cfg):
        if f not in cov:
            src = Source.of(f)
            cov[f] = {'lines': {n: False for n, t in enumerate(src.lines, 1) if not trivial(t)}, 'branches': []}
    return cov, impact, failed, output


def write_totals(cfg, text):
    p = cfg['index']
    if not os.path.exists(p):
        return
    s = rd(p)
    n = re.sub(r'<!-- TOTALS:START -->.*?<!-- TOTALS:END -->', f'<!-- TOTALS:START -->\n{text}\n<!-- TOTALS:END -->', s, flags=re.S)
    if n != s:
        wr(p, n)


def pct(a, b):
    return f'{a}/{b} ({100 * a / b:.1f}%)' if b else '0/0'


def cmd_check(args):
    cfg = load_config()
    level = cfg['level']
    first = gather(cfg, run=not args.no_run)
    cov, impact, failed, output = first
    gaps, totals = compute_gaps(cov)
    det_problem = None
    if args.twice and not args.no_run:
        Source.cache.clear()
        cov2, _, failed2, _ = gather(cfg)
        gaps2, totals2 = compute_gaps(cov2)
        if set(gaps) != set(gaps2) or totals != totals2 or failed != failed2:
            diff = sorted(f'{k[0]}:{(gaps.get(k) or gaps2.get(k))["line"]}' for k in set(gaps) ^ set(gaps2))[:10]
            det_problem = f'two runs gave different results ({len(set(gaps) ^ set(gaps2))} gaps differ): {diff}. Fix the test or the reader before trusting this gate'
    problems, defects, rows, just, ids = consistency(cfg)
    ledger = read_ledger(cfg) if level != 'full' else set()
    open_gaps = [k for k in gaps if k not in just and k not in ledger]
    stale = sorted(k for k in just if k not in gaps)
    ledger_gone = sorted(k for k in ledger if k not in gaps)
    pass_path = os.path.join(OUT, 'pass.json')
    tree = tree_hash(cfg)
    prev = rj(pass_path) if os.path.exists(pass_path) else {}
    gap_id = hashlib.sha1(json.dumps(sorted(map(list, gaps))).encode()).hexdigest()
    if not args.no_run and prev.get('tree') == tree and prev.get('gaps') != gap_id:
        det_problem = det_problem or 'same code and tests as the last pass, but different coverage: the gate is not deterministic here'
    clean = not (failed or defects or open_gaps or stale or problems or det_problem)
    summary = {'at': time.strftime('%Y-%m-%d %H:%M:%S %z'), 'version': VERSION, 'tree': tree, 'gaps': gap_id, 'clean': clean, 'level': level, 'failed_lanes': failed,
               'open_defects': len(defects), 'paths': sum(1 for i in rows if i.startswith('P-')), 'gates': sum(1 for i in rows if i.startswith('G-')), 'justified': len(just),
               'ledger': len(ledger), 'unjustified': len(open_gaps), 'stale_justifications': len(stale), 'map_problems': len(problems), **totals}
    if not args.no_run:
        wj(pass_path, summary, indent=1)
        wj(os.path.join(OUT, 'impact.json'), {k: sorted(v) for k, v in impact.items()}, indent=0)
    if clean:
        write_totals(cfg, f"**Last clean pass {summary['at']}:** {summary['paths']} paths, {summary['gates']} gate tests · lines {pct(totals['lines_hit'], totals['lines'])} · branches {pct(totals['branches_hit'], totals['branches'])} · {len(just)} justified, 0 unjustified.")
    for t in output:
        print(t)
    print(f"flowmap check [{level}]: tests {'FAILED ' + ','.join(failed) if failed else ('not run' if args.no_run else 'passed')} · defects {len(defects)} · lines {pct(totals['lines_hit'], totals['lines'])} · branches {pct(totals['branches_hit'], totals['branches'])} · unjustified {len(open_gaps)} · stale {len(stale)} · map problems {len(problems)}{f' · ledger {len(ledger)} (gone {len(ledger_gone)})' if ledger else ''}")
    for d in defects[:40]:
        print(f'  DEFECT {d}')
    for p in problems[:60]:
        print(f'  MAP {p}')
    for k in sorted(open_gaps, key=lambda x: (x[0], gaps[x]['line']))[:200]:
        g = gaps[k]
        print(f"  GAP {k[0]}:{g['line']} {k[1]} {g['kind']} — {g['code']}")
    for k in stale[:40]:
        print(f"  STALE {k[0]}:{just[k]['line']} {k[1]}: this justification's line is now covered or its code changed; review it, then delete or re-justify the row")
    if det_problem:
        print(f'  NOT DETERMINISTIC {det_problem}')
    if ledger_gone:
        print(f'  ledger: {len(ledger_gone)} gaps are gone; run `flowmap.py ledger freeze` to shrink it')
    print('CLEAN: stop here. No more rounds until the code changes.' if clean else 'NOT CLEAN: fix, then run the full pass again.')
    return 0 if clean else 1


def changed_files(base):
    names = set()
    for cmd in ([f'git diff --name-only {base}'] if base else ['git diff --name-only HEAD']) + ['git ls-files --others --exclude-standard']:
        r = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        if r.returncode:
            return None
        names.update(x for x in r.stdout.split('\n') if x)
    return sorted(names)


def cmd_quick(args):
    """Doubtful evidence never narrows: anything this cannot answer means "run everything", with the reason named."""
    cfg = load_config()
    problems, defects, *_ = consistency(cfg)
    changed = changed_files(args.base)
    reason = None
    pick = set()
    if changed is None:
        reason = 'git could not list the changes'
    impact_path = os.path.join(OUT, 'impact.json')
    impact = rj(impact_path) if os.path.exists(impact_path) else None
    if reason is None:
        all_tests = set(test_files(cfg))
        for f in changed:
            if os.path.basename(f) in RUN_ALL_FILES or f == cfg['maps']:
                reason = f'{f} can change every result'
                break
            if f in all_tests:
                pick.add(f)
            elif is_source(cfg, f):
                if impact is None:
                    reason = 'no coverage data yet: run the full pass once'
                    break
                if not impact.get(f):
                    reason = f'{f} has no recorded tests (new, or its lane records no per-test data)'
                    break
                pick.update(t for t in impact[f] if t in all_tests)
    failed = []
    for lane in lane_list(cfg):
        if reason:
            code, text = run_lane(cfg, lane, OUT)
        elif not pick:
            continue
        else:
            mine = sorted(t for t in pick if in_globs(t, lane.get('tests', cfg['tests'])))
            if not mine:
                continue
            if not lane.get('command_files'):
                code, text = run_lane(cfg, lane, OUT)
                reason = reason or f"lane {lane['name']} cannot run a list of files"
            else:
                code, text = run_lane(cfg, lane, OUT, files=mine)
        if code:
            failed.append(lane['name'])
            print('\n'.join(text.split('\n')[-30:]))
    print(f"flowmap quick: {len(changed or [])} changed files · {'ALL tests: ' + reason if reason else f'{len(pick)} test files'} · failed {failed or 'none'} · defects {len(defects)} · map problems {len(problems)}")
    for p in problems[:40]:
        print(f'  MAP {p}')
    for d in defects[:20]:
        print(f'  DEFECT {d}')
    print('Coverage was not re-measured: run `flowmap.py check` (the full pass) before a release or deploy.')
    return 1 if failed or problems or defects else 0


# ------------------------------------------------------------------ the mechanical update

ENTRY_PATTERNS = [
    (r'\.(js|ts|mjs|cjs)$', r'''\b(?:router|app|server)\.(get|post|put|patch|delete|all)\(\s*(?:\[\s*)?['"`]([^'"`]+)''', 'http'),
    (r'\.(js|ts|mjs|cjs)$', r'\b(setInterval|process\.on)\(', 'timer/signal'),
    (r'\.py$', r'''@\w*\.(?:route|get|post|put|patch|delete)\(\s*['"]([^'"]+)''', 'http'),
    (r'\.py$', r'''if __name__ == ['"]__main__['"]''', 'main'),
    (r'\.go$', r'''(?:HandleFunc|Handle)\(\s*"([^"]+)''', 'http'),
    (r'\.go$', r'^func main\(\)', 'main'),
    (r'\.(kt|java)$', r'''@(?:Get|Post|Put|Delete|Patch)(?:Mapping)?\(\s*"([^"]+)''', 'http'),
    (r'\.(kt|java)$', r'\bfun main\(|public static void main\(', 'main'),
    (r'\.rs$', r'^\s*(?:pub\s+)?(?:async\s+)?fn main\(', 'main'),
    (r'\.(c|cc|cpp)$', r'^\s*int main\(', 'main'),
]


def find_entries(cfg):
    found = {}
    for f in source_files(cfg):
        rules = [(rx, kind) for ext, rx, kind in ENTRY_PATTERNS if re.search(ext, f)]
        if not rules:
            continue
        text = rd(f)
        for rx, kind in rules:
            for m in re.finditer(rx, text, re.M):
                name = m.group(m.lastindex) if m.lastindex else kind
                if kind == 'timer/signal':
                    name = m.group(1)
                found[f'{kind} {name} @ {f}'] = (kind, name, f)
    return found


def update_entries(cfg):
    p = os.path.join(cfg['maps'], 'entries.md')
    old = {}
    if os.path.exists(p):
        for m in re.finditer(r'^\|\s*(\S+(?: \S+)*?)\s*\|\s*`?([^|`]+?)`?\s*\|\s*`?([^|`]+?)`?\s*\|\s*([^|]+?)\s*\|$', rd(p), re.M):
            if m.group(1) not in ('Kind', '---'):
                old[f'{m.group(1)} {m.group(2)} @ {m.group(3)}'] = m.group(4)
    found = find_entries(cfg)
    # rows a person or agent added by hand (triggers no pattern can see) stay; a found entry that is gone goes.
    keep = {k: v for k, v in old.items() if k in found or v.startswith('manual')}
    added = [k for k in found if k not in old]
    rows = {**{k: 'TODO' for k in added}, **keep}
    lines = ['# Entry points', '', 'Every way work enters the code. Found by `flowmap.py update`; rows whose owner starts with `manual` are added by hand (timers, queues, UI events no pattern finds) and stay.', 'Owner = the area or test family that exercises it. A row with `TODO` fails the gate.', '', '| Kind | Entry | File | Owner |', '|---|---|---|---|']
    for k in sorted(rows):
        kind, rest = k.split(' ', 1)
        name, f = rest.rsplit(' @ ', 1)
        lines.append(f'| {kind} | `{name}` | `{f}` | {rows[k]} |')
    os.makedirs(cfg['maps'], exist_ok=True)
    text = '\n'.join(lines) + '\n'
    if not os.path.exists(p) or rd(p) != text:
        wr(p, text)
    return added, [k for k in old if k not in keep]


def cmd_update(args):
    cfg = load_config()
    changes = []
    rows, just, _, _ = read_map(cfg)
    ids, _ = scan_tests(cfg)
    # 1. line numbers follow their anchors; a row whose anchor is gone is left for review (the gate names it stale)
    for f in map_files(cfg):
        text = rd(f)
        body, sec = split_section(text)

        def fix(m):
            path, line, anchor = m.group(1), int(m.group(2)), m.group(3)
            if os.path.exists(path):
                n = Source.of(path).by_anchor.get(anchor)
                if n and n != line:
                    changes.append(f'{path}: justification moved {line} → {n}')
                    return m.group(0).replace(f'{path}:{line}', f'{path}:{n}', 1)
            return m.group(0)
        new_sec = JUST_RE.sub(fix, sec)
        # 2. rows of tests that no longer exist go
        lines = []
        for line in body.split('\n'):
            m = ROW_ID_RE.match(line)
            if m and m.group(1) not in ids:
                changes.append(f'row removed, its test is gone: {m.group(1)}')
                continue
            lines.append(line)
        new = '\n'.join(lines) + new_sec
        if new != text:
            wr(f, new)
    # 3. tests with no row get a skeleton for a person or agent to fill: the gate refuses TODO
    missing = sorted(set(ids) - set(rows))
    if missing:
        p = os.path.join(cfg['maps'], 'new-paths.md')
        head = '# Paths waiting to be filled\n\nAdded by `flowmap.py update`. Fill each row and move it to its area map.\n\n| ID | Entry | Conditions | Expected | Checks | If it fails |\n|---|---|---|---|---|---|\n'
        cur = rd(p) if os.path.exists(p) else head
        cur += ''.join(f'| {i} | TODO | TODO | TODO | TODO | TODO |\n' for i in missing)
        wr(p, cur)
        changes += [f'skeleton row for new test: {i}' for i in missing]
    # 4. the entry inventory
    added, gone = update_entries(cfg)
    changes += [f'new entry, needs an owner: {k}' for k in added] + [f'entry gone: {k}' for k in gone]
    print(f'flowmap update: {len(changes)} changes')
    for c in changes[:80]:
        print(f'  {c}')
    return 0


def cmd_justify(args):
    cfg = load_config()
    path, _, line = args.where.rpartition(':')
    if not path or not line.isdigit() or not os.path.exists(path):
        die('where must be FILE:LINE of an existing file')
    if args.cls not in CLASSES:
        die(f'class must be one of {", ".join(CLASSES)}')
    if len(args.reason) < 15 or WEAK_REASON.search(args.reason):
        die('that is not a reason. Say the proof (unreachable), what would have to fail (defensive), where it IS exercised (external), or why only looks change (presentation). If it can be tested, test it.')
    src = Source.of(path)
    n = int(line)
    if n not in src.anchors:
        die(f'{path} has no line {n}')
    target = os.path.join(cfg['maps'], f'{args.map}.md')
    text = rd(target) if os.path.exists(target) else f'# {args.map}\n'
    if not SECTION_RE.search(text):
        text = text.rstrip('\n') + '\n\n## Not exercised — justified\n\n| Location | Anchor | Class | Reason |\n|---|---|---|---|\n'
    text = text.rstrip('\n') + f'\n| `{path}:{n}` | `{src.anchors[n]}` | {args.cls} | {args.reason} |\n'
    os.makedirs(cfg['maps'], exist_ok=True)
    wr(target, text)
    print(f'justified {path}:{n} ({src.anchors[n]}) in {target}')
    return 0


def cmd_ledger(args):
    cfg = load_config()
    p = ledger_path(cfg)
    if args.action == 'show':
        print(f'{len(read_ledger(cfg))} gaps in the ledger ({p})')
        return 0
    if cfg['level'] == 'full':
        die('level "full" allows no ledger: set "level": "standard" or "quick" in flowmap.json first')
    cov, _, _, _ = gather(cfg, run=False)
    gaps, _ = compute_gaps(cov)
    _, just, _, _ = read_map(cfg)
    now = {k for k in gaps if k not in just}
    old = read_ledger(cfg)
    if old and now - old and not args.first:
        die(f'the ledger may only shrink: {len(now - old)} gaps are new. Close them, or justify them')
    wj(p, sorted(map(list, now)), indent=0)
    print(f'ledger: {len(old)} → {len(now)} gaps')
    return 0


def gap_blocks(gaps):
    """Neighbouring uncovered lines are one piece of work: one test closes them."""
    lines = {}
    for (f, _), g in gaps.items():
        lines.setdefault(f, []).append(g['line'])
    count = 0
    for ns in lines.values():
        ns.sort()
        count += 1 + sum(1 for a, b in zip(ns, ns[1:]) if b - a > 2)
    return count


def cmd_scout(args):
    cfg = load_config()
    files = source_files(cfg)
    src_lines = sum(len(rd(f).split('\n')) for f in files)
    print(f'source: {len(files)} files, {src_lines} lines · test files: {len(test_files(cfg))} · lanes: {[l["name"] for l in lane_list(cfg)]}')
    entries = find_entries(cfg)
    print(f'entries found by pattern: {len(entries)}')
    cov, _, failed, output = gather(cfg, run=not args.no_run)
    gaps, totals = compute_gaps(cov)
    for t in output:
        print(t)
    print(f"baseline: tests {'FAILED ' + ','.join(failed) if failed else 'passed'} · lines {pct(totals['lines_hit'], totals['lines'])} · branches {pct(totals['branches_hit'], totals['branches'])} · gaps {len(gaps)}")
    if failed:
        print('No estimate: the tests did not run, so this baseline says nothing. Make them run first (read what they touch: a test that drives a real service needs its own lane there).')
    else:
        blocks = gap_blocks(gaps)
        est = blocks * TOKENS_PER_GAP
        print(f'estimate to close them at level "full": about {est / 1000:.0f}k agent tokens ({blocks} separate gaps × {TOKENS_PER_GAP}, measured once on one project; rough). Writing the map rows and gate tables comes on top.')
    print('Show this to the owner before spending. Levels: quick (gates, entries, new code only, ledger for the rest), standard (main flows), full (everything).')
    return 0


def cmd_mutate(args):
    """Coverage proves code ran, not that a test checks it. Each stored mutant must make some test fail."""
    cfg = load_config()
    p = os.path.join(cfg['maps'], 'mutants.json')
    if not os.path.exists(p):
        die(f'{p} not found: list the gate conditions to flip, see the skill')
    mutants = rj(p)
    survivors, broken, killed = [], [], 0
    for m in mutants:
        if m.get('redundant'):
            continue
        with tempfile.TemporaryDirectory(prefix='flowmap-mut-') as tmp:
            work = os.path.join(tmp, 'w')
            shutil.copytree('.', work, symlinks=True, ignore=shutil.ignore_patterns('.git', OUT, '__pycache__', 'node_modules'))
            if os.path.isdir('node_modules'):
                os.symlink(os.path.abspath('node_modules'), os.path.join(work, 'node_modules'))
            target = os.path.join(work, m['file'])
            text = rd(target)
            if text.count(m['find']) != 1:
                broken.append(f"{m['id']}: `{m['find'][:50]}` occurs {text.count(m['find'])} times in {m['file']}, not once")
                continue
            wr(target, text.replace(m['find'], m['replace']))
            alive = True
            for lane in lane_list(cfg):
                cmd = lane.get('command') if lane.get('adapter') != 'coverage-py' else f"{lane_python(lane)} {lane['test_args']}"
                if m.get('command'):
                    cmd = m['command']
                try:
                    r = subprocess.run(cmd, shell=True, cwd=work, capture_output=True, timeout=lane.get('timeout_s', 3600))
                except subprocess.TimeoutExpired:
                    alive = False
                    break
                if r.returncode:
                    alive = False
                    break
            if alive:
                survivors.append(m['id'])
            else:
                killed += 1
    print(f'flowmap mutate: {killed} killed · {len(survivors)} survived · {len(broken)} stale · {sum(1 for m in mutants if m.get("redundant"))} marked redundant')
    for s in survivors:
        print(f'  SURVIVED {s}: no test notices this condition. Add the test, or mark it "redundant" with the reason')
    for b in broken:
        print(f'  STALE {b}')
    return 1 if survivors or broken else 0


HOOK = '''#!/bin/sh
# flowmap: the map moves with the code. Delete this file to turn it off.
python3 tools/flow/flowmap.py update >/dev/null || exit 1
git add -A {maps} {index} 2>/dev/null
exec python3 tools/flow/flowmap.py quick
'''


def cmd_hook(args):
    cfg = load_config()
    if subprocess.run('git rev-parse --git-dir', shell=True, capture_output=True).returncode:
        die('not a git repository')
    d = subprocess.run('git rev-parse --git-path hooks', shell=True, capture_output=True, text=True).stdout.strip()
    os.makedirs(d, exist_ok=True)
    p = os.path.join(d, 'pre-commit')
    if os.path.exists(p) and 'flowmap' not in rd(p):
        die(f'{p} exists and is not ours: add these lines to it yourself:\n{HOOK}')
    wr(p, HOOK.replace('{maps}', cfg['maps']).replace('{index}', cfg['index']))
    os.chmod(p, 0o755)
    print(f'installed {p}: each commit updates the map and runs the affected tests; a failure blocks the commit')
    return 0


# ------------------------------------------------------------------ optional helper: codebase-memory

def mcp_call(url, auth, tool, arguments):
    def post(body, sid=None):
        h = {'content-type': 'application/json', 'accept': 'application/json, text/event-stream'}
        if auth:
            h['authorization'] = auth
        if sid:
            h['mcp-session-id'] = sid
        return urllib.request.urlopen(urllib.request.Request(url, json.dumps(body).encode(), h), timeout=60)
    r = post({'jsonrpc': '2.0', 'id': 1, 'method': 'initialize', 'params': {'protocolVersion': '2025-03-26', 'capabilities': {}, 'clientInfo': {'name': 'flowmap', 'version': VERSION}}})
    sid = r.headers.get('mcp-session-id')
    r.read()
    post({'jsonrpc': '2.0', 'method': 'notifications/initialized'}, sid).read()
    t = post({'jsonrpc': '2.0', 'id': 2, 'method': 'tools/call', 'params': {'name': tool, 'arguments': arguments}}, sid).read().decode()
    data = [l[6:] for l in t.split('\n') if l.startswith('data: ')]
    return json.loads(data[-1] if data else t)['result']['content'][0]['text']


def cmd_helper(args):
    """A starting point and a second opinion, nothing more: never read by a pass, and its absence costs nothing."""
    cfg = load_config()
    c = cfg.get('codebase_memory') or {}
    url = os.environ.get('FLOWMAP_CBM_URL') or c.get('url')
    project = c.get('project') or cfg.get('name')
    if not url:
        print('helper: no codebase-memory configured (FLOWMAP_CBM_URL or codebase_memory.url). Nothing lost: use `flowmap.py update` and read the code.')
        return 0
    try:
        if args.what == 'routes':
            print(mcp_call(url, os.environ.get('FLOWMAP_CBM_AUTH'), 'query_graph', {'project': project, 'query': 'MATCH (r:Route) RETURN r.name, r.file_path', 'max_rows': 500}))
        else:
            print(mcp_call(url, os.environ.get('FLOWMAP_CBM_AUTH'), 'detect_changes', {'project': project, 'since': args.since, 'fixpoint': True, 'via': True, 'seed_filter': 'product', 'limit': 300}))
    except Exception as e:
        print(f'helper: codebase-memory did not answer ({str(e)[:120]}). Nothing lost: a pass never needs it.')
    return 0


# ------------------------------------------------------------------ init

def detect_lanes(files):
    lanes, source, tests = [], [], []
    names = set(files)
    if any(f.endswith('.test.js') or f.endswith('.test.mjs') for f in files) or 'package.json' in names:
        lanes.append({'name': 'node', 'adapter': 'node-v8', 'command': 'node --test', 'command_files': 'node --test {files}', 'tests': ['**/*.test.js', '**/*.test.mjs']})
        source += ['src/**/*.js', 'lib/**/*.js', '*.js']
        tests += ['**/*.test.js', '**/*.test.mjs']
    if any(f.endswith('.py') for f in files):
        lanes.append({'name': 'python', 'adapter': 'coverage-py', 'python': 'python3', 'test_args': '-m unittest discover -s tests', 'command_files': 'python3 -m unittest {files}', 'coverage_source': '.', 'tests': ['tests/**/*.py', 'test/**/*.py', '**/test_*.py']})
        source += ['**/*.py']
        tests += ['tests/**/*.py', 'test/**/*.py', '**/test_*.py']
    if 'go.mod' in names:
        lanes.append({'name': 'go', 'adapter': 'gocover', 'command': 'go test -coverprofile=.flow/go.cover ./...', 'report': '.flow/go.cover', 'tests': ['**/*_test.go']})
        source += ['**/*.go']
        tests += ['**/*_test.go']
    if 'Cargo.toml' in names:
        lanes.append({'name': 'rust', 'adapter': 'lcov', 'command': 'cargo llvm-cov --lcov --output-path .flow/rust.lcov', 'report': '.flow/rust.lcov', 'tests': ['tests/**/*.rs']})
        source += ['src/**/*.rs']
        tests += ['tests/**/*.rs']
    if any(os.path.basename(f) in ('build.gradle', 'build.gradle.kts') for f in files):
        lanes.append({'name': 'jvm', 'adapter': 'jacoco', 'command': './gradlew test jacocoTestReport', 'report': 'build/reports/jacoco/test/jacocoTestReport.xml', 'tests': ['**/src/test/**']})
        source += ['**/src/main/**/*.kt', '**/src/main/**/*.java']
        tests += ['**/src/test/**']
    return lanes, source, tests


def cmd_init(args):
    here = os.path.dirname(os.path.abspath(__file__))
    tpl = os.path.join(os.path.dirname(here), 'templates')
    if not os.path.isdir(tpl):
        die(f'templates not found at {tpl}: run init from the base skill copy of this script')
    name = args.name or os.path.basename(os.path.abspath('.'))
    made = []
    files = walk_files()
    if not os.path.exists(CONFIG):
        lanes, source, tests = detect_lanes(files)
        exclude = [t for t in tests] + ['tools/flow/**']
        wj(CONFIG, {'name': name, 'level': 'full', 'source': source, 'exclude': exclude, 'tests': tests, 'maps': 'docs/flow-map', 'index': 'docs/FLOW-MAP.md', 'lanes': lanes, 'codebase_memory': {'project': name}}, indent=2)
        made.append(CONFIG)
    def put(dst, tmpl):
        if not os.path.exists(dst):
            os.makedirs(os.path.dirname(dst) or '.', exist_ok=True)
            text = rd(os.path.join(tpl, tmpl)).replace('{{NAME}}', name).replace('{{DATE}}', time.strftime('%Y-%m-%d'))
            wr(dst, text)
            made.append(dst)
    put('docs/FLOW-MAP.md', 'FLOW-MAP.md')
    put('docs/flow-map/main.md', 'area.md')
    put(f'.claude/skills/{name}-flowmap/SKILL.md', 'project-skill.md')
    os.makedirs('tools/flow', exist_ok=True)
    dst = 'tools/flow/flowmap.py'
    if not os.path.exists(dst):
        shutil.copy2(os.path.abspath(__file__), dst)
        os.chmod(dst, 0o755)
        made.append(dst)
    gi = rd('.gitignore') if os.path.exists('.gitignore') else ''
    if '.flow' not in gi.split('\n') and '.flow/' not in gi.split('\n'):
        wr('.gitignore', gi + ('' if gi.endswith('\n') or not gi else '\n') + '.flow/\n')
        made.append('.gitignore')
    print('created: ' + (', '.join(made) if made else 'nothing, it was all there'))
    print(f'next: review {CONFIG} (source, tests, lanes), then `python3 tools/flow/flowmap.py scout`')
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest='cmd', required=True)
    p = sub.add_parser('init')
    p.add_argument('--name')
    p.set_defaults(fn=cmd_init)
    p = sub.add_parser('scout')
    p.add_argument('--no-run', action='store_true')
    p.set_defaults(fn=cmd_scout)
    p = sub.add_parser('check')
    p.add_argument('--no-run', action='store_true')
    p.add_argument('--twice', action='store_true')
    p.set_defaults(fn=cmd_check)
    p = sub.add_parser('quick')
    p.add_argument('--base')
    p.set_defaults(fn=cmd_quick)
    sub.add_parser('update').set_defaults(fn=cmd_update)
    p = sub.add_parser('justify')
    p.add_argument('where')
    p.add_argument('cls')
    p.add_argument('reason')
    p.add_argument('--map', default='main')
    p.set_defaults(fn=cmd_justify)
    p = sub.add_parser('ledger')
    p.add_argument('action', choices=['freeze', 'show'])
    p.add_argument('--first', action='store_true')
    p.set_defaults(fn=cmd_ledger)
    sub.add_parser('mutate').set_defaults(fn=cmd_mutate)
    p = sub.add_parser('hook')
    p.add_argument('action', choices=['install'])
    p.set_defaults(fn=cmd_hook)
    p = sub.add_parser('helper')
    p.add_argument('what', choices=['routes', 'diff'])
    p.add_argument('--since', default='HEAD~1')
    p.set_defaults(fn=cmd_helper)
    args = ap.parse_args()
    sys.exit(args.fn(args))


if __name__ == '__main__':
    main()
