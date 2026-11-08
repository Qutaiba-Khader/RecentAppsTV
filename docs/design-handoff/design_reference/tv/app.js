/* ════════════════════════════════════════════════════════════════════
   Recent Apps TV — D-pad app logic (vanilla)
   Screens: overlay · settings · exceptions · shizuku  (+ empty variant)
   Keys: ← → focus · ↑ ↓ dismiss · Enter = OK · Esc/Backspace = BACK
   ════════════════════════════════════════════════════════════════════ */
(function () {
  "use strict";

  /* ── refs ─────────────────────────────────────────────────────────── */
  const $ = (s) => document.querySelector(s);
  const tv = $("#tv"), tvwrap = $("#tvwrap"), chrome = $("#chrome");
  const strip = $("#strip"), ovCount = $("#ovCount");
  const overlay = $("#overlay"), settings = $("#settings"),
        exceptions = $("#exceptions"), shizuku = $("#shizuku");
  const setList = $("#setList"), excList = $("#excList");
  const launch = $("#launch"), launchTile = $("#launchTile"), launchName = $("#launchName");
  const closedHint = $("#closedHint");

  const SIZE = { small: "142px", medium: "180px", large: "230px" };
  const SHAPE = { square: "16px", rounded: "32px", circle: "50%" };
  const DEFAULT_TASKS = TV.APPS.map((a) => a.id);
  const byId = (id) => TV.APPS.find((a) => a.id === id) || TV.INSTALLED.find((a) => a.id === id);

  /* ── state ────────────────────────────────────────────────────────── */
  const State = {
    scene: "overlay",
    overlayClosed: false,
    tasks: [...DEFAULT_TASKS],
    excluded: new Set(["settings2"]),
    settings: { showKillAll: true, size: "medium", shape: "rounded" },
  };
  const App = {
    screen: "overlay",
    focusItems: [], ovFocus: 0,
    setRows: [], setFocus: 0,
    excRows: [], excFocus: 0, excScroll: 0,
    shzFocus: 0,
  };

  const notExcluded = (id) => !State.excluded.has(id);
  const liveTasks = () => State.tasks.filter(notExcluded);

  /* ── card / kill-all builders ─────────────────────────────────────── */
  function makeCard(app) {
    const c = document.createElement("div");
    c.className = "card";
    c.appendChild(TV.iconTile(app));
    const name = document.createElement("div");
    name.className = "name";
    name.textContent = app.name;
    const cat = document.createElement("span");
    cat.className = "cat"; cat.textContent = app.cat;
    name.appendChild(cat);
    const hu = document.createElement("div"); hu.className = "hint up"; hu.textContent = "▲";
    const hd = document.createElement("div"); hd.className = "hint down"; hd.textContent = "▼";
    c.append(name, hu, hd);
    return c;
  }
  function makeKillAll() {
    const k = document.createElement("div");
    k.className = "killall";
    k.innerHTML =
      '<div class="ka-icon"><div class="ka-handle"></div></div><div class="ka-label">Kill all</div>';
    return k;
  }

  /* ── overlay render ───────────────────────────────────────────────── */
  function applyCardSettings() {
    tv.style.setProperty("--card-size", SIZE[State.settings.size]);
    tv.style.setProperty("--card-radius", SHAPE[State.settings.shape]);
  }
  function defaultOvFocus() {
    return State.settings.showKillAll && liveTasks().length ? 1 : 0;
  }
  function renderCarousel() {
    applyCardSettings();
    strip.innerHTML = "";
    App.focusItems = [];
    const tasks = liveTasks().map(byId).filter(Boolean);
    if (State.settings.showKillAll && tasks.length) {
      const ka = makeKillAll();
      strip.appendChild(ka);
      App.focusItems.push({ type: "killall", el: ka });
    }
    tasks.forEach((app) => {
      const card = makeCard(app);
      strip.appendChild(card);
      App.focusItems.push({ type: "card", el: card, app });
    });
    ovCount.textContent = tasks.length ? tasks.length + (tasks.length === 1 ? " app" : " apps") : "";
  }
  function centerFocused() {
    const it = App.focusItems[App.ovFocus];
    if (!it) return;
    const el = it.el;
    const center = el.offsetLeft + el.offsetWidth / 2;
    const pad = 76;
    const w = strip.scrollWidth;
    let tx;
    if (w > 1920 - pad * 2) {
      const min = 1920 - pad - w;   // content right edge pinned to (1920-pad)
      const max = pad;              // content left edge pinned to pad
      tx = Math.max(min, Math.min(max, 960 - center));
    } else {
      tx = (1920 - w) / 2;          // whole strip fits → center it
    }
    strip.style.transform = "translate(" + tx + "px, -50%)";
  }
  function applyOverlayFocus() {
    App.focusItems.forEach((it, i) => {
      it.el.classList.toggle("focused", i === App.ovFocus);
      if (i !== App.ovFocus) it.el.classList.remove("peek-up", "peek-down");
    });
    centerFocused();
  }
  function clearPeek() {
    App.focusItems.forEach((it) => it.el.classList.remove("peek-up", "peek-down"));
  }

  /* ── overlay interactions ─────────────────────────────────────────── */
  function overlayKey(k) {
    if (State.overlayClosed) {
      if (k === "Enter" || k.indexOf("Arrow") === 0) reopenOverlay();
      return;
    }
    const it = App.focusItems[App.ovFocus];
    if (!it) return;
    if (k === "ArrowRight") { clearPeek(); moveFocus(-1); }       // visually right → toward Kill All
    else if (k === "ArrowLeft") { clearPeek(); moveFocus(+1); }   // visually left → older apps
    else if (k === "ArrowUp") dismissStep(it, "up");
    else if (k === "ArrowDown") dismissStep(it, "down");
    else if (k === "Enter") activate(it);
  }
  function moveFocus(d) {
    const n = App.focusItems.length;
    App.ovFocus = Math.max(0, Math.min(n - 1, App.ovFocus + d));
    applyOverlayFocus();
  }
  function dismissStep(it, dir) {
    if (it.type !== "card") return;
    const el = it.el;
    const peekCls = dir === "up" ? "peek-up" : "peek-down";
    const other = dir === "up" ? "peek-down" : "peek-up";
    el.classList.remove(other);
    if (!el.classList.contains(peekCls)) {
      el.classList.add(peekCls);                                  // stage 1 — peek
    } else {
      el.classList.remove(peekCls);
      el.classList.add(dir === "up" ? "flyout-up" : "flyout-down"); // stage 2 — fly off
      const id = it.app.id;
      setTimeout(() => removeTask(id), 330);
    }
  }
  function removeTask(id) {
    const at = App.ovFocus;
    State.tasks = State.tasks.filter((t) => t !== id);
    if (!liveTasks().length) { State.scene = "empty"; setScene("empty"); return; }
    renderCarousel();
    App.ovFocus = Math.min(at, App.focusItems.length - 1);
    applyOverlayFocus();
  }
  function activate(it) {
    if (it.type === "killall") killAll();
    else switchToApp(it.app);
  }
  function switchToApp(app) {
    launch.style.background = app.color;
    launchTile.innerHTML = TV.glyphHTML(app.glyph);
    launchName.textContent = app.name;
    launch.classList.add("show");
    setTimeout(() => { launch.classList.remove("show"); closeOverlay(); }, 850);
  }
  function killAll() {
    App.focusItems.forEach((it, i) => {
      if (it.type === "card") {
        it.el.style.transitionDelay = i * 28 + "ms";
        it.el.classList.add("flyout-up");
      } else { it.el.style.opacity = "0"; }
    });
    setTimeout(() => { State.tasks = []; setScene("empty"); }, 520);
  }
  function closeOverlay() {
    State.overlayClosed = true;
    tv.classList.remove("show-overlay");
    overlay.classList.remove("active");
    closedHint.classList.add("show");
  }
  function reopenOverlay() {
    State.overlayClosed = false;
    closedHint.classList.remove("show");
    tv.classList.add("show-overlay");
    overlay.classList.add("active");
    renderCarousel();
    App.ovFocus = defaultOvFocus();
    applyOverlayFocus();
  }

  /* ── settings render ──────────────────────────────────────────────── */
  function rowShell(title, desc) {
    const row = document.createElement("div");
    row.className = "row";
    const main = document.createElement("div");
    main.className = "r-main";
    const t = document.createElement("div"); t.className = "r-title"; t.textContent = title;
    const d = document.createElement("div"); d.className = "r-desc"; d.textContent = desc;
    main.append(t, d);
    const ctrl = document.createElement("div"); ctrl.className = "r-ctrl";
    row.append(main, ctrl);
    return { row, ctrl };
  }
  function buildToggle(on) {
    const t = document.createElement("div");
    t.className = "toggle" + (on ? " on" : "");
    return t;
  }
  function buildSeg(labels, values, current) {
    const seg = document.createElement("div");
    seg.className = "seg";
    labels.forEach((lab, i) => {
      const o = document.createElement("div");
      o.className = "opt" + (values[i] === current ? " sel" : "");
      o.textContent = lab;
      seg.appendChild(o);
    });
    return seg;
  }
  function renderSettings() {
    setList.innerHTML = "";
    App.setRows = [];

    // 1 · Show Kill All
    let s = rowShell("Show Kill All", "A clear-all tile at the start of the row.");
    s.ctrl.appendChild(buildToggle(State.settings.showKillAll));
    setList.appendChild(s.row);
    App.setRows.push({ el: s.row, kind: "toggle" });

    // 2 · Size
    s = rowShell("Size of task", "Scales every card in the overlay.");
    s.ctrl.appendChild(buildSeg(["Small", "Medium", "Large"], ["small", "medium", "large"], State.settings.size));
    setList.appendChild(s.row);
    App.setRows.push({ el: s.row, kind: "seg", values: ["small", "medium", "large"], get: () => State.settings.size, set: (v) => (State.settings.size = v) });

    // 3 · Shape (+ live preview)
    s = rowShell("Shape of task", "Corner style of every card.");
    const prev = document.createElement("div");
    prev.className = "shape-prev"; prev.dataset.shape = State.settings.shape;
    s.ctrl.append(prev, buildSeg(["Square", "Rounded", "Circle"], ["square", "rounded", "circle"], State.settings.shape));
    setList.appendChild(s.row);
    App.setRows.push({ el: s.row, kind: "seg", values: ["square", "rounded", "circle"], get: () => State.settings.shape, set: (v) => (State.settings.shape = v), prev: prev });

    // 4 · Exceptions
    s = rowShell("Exceptions", excLabel());
    const go = document.createElement("div"); go.className = "r-go"; go.textContent = "‹";
    s.ctrl.appendChild(go);
    setList.appendChild(s.row);
    App.setRows.push({ el: s.row, kind: "go" });

    applySettingsFocus();
  }
  function excLabel() {
    const n = State.excluded.size;
    return n ? n + (n === 1 ? " app hidden" : " apps hidden") : "No apps hidden";
  }
  function applySettingsFocus() {
    App.setRows.forEach((r, i) => r.el.classList.toggle("focused", i === App.setFocus));
  }
  function settingsKey(k) {
    const r = App.setRows[App.setFocus];
    if (k === "ArrowUp") { App.setFocus = Math.max(0, App.setFocus - 1); applySettingsFocus(); return; }
    if (k === "ArrowDown") { App.setFocus = Math.min(App.setRows.length - 1, App.setFocus + 1); applySettingsFocus(); return; }
    if (r.kind === "toggle") {
      if (k === "Enter" || k === "ArrowLeft" || k === "ArrowRight") {
        State.settings.showKillAll = !State.settings.showKillAll;
        renderSettings();
      }
    } else if (r.kind === "seg") {
      if (k === "ArrowRight" || k === "ArrowLeft" || k === "Enter") {
        const dir = k === "ArrowRight" ? -1 : k === "ArrowLeft" ? 1 : 1; // RTL: right=earlier
        const i = r.values.indexOf(r.get());
        let ni = k === "Enter" ? (i + 1) % r.values.length : Math.max(0, Math.min(r.values.length - 1, i + dir));
        r.set(r.values[ni]);
        applyCardSettings();
        renderSettings();
      }
    } else if (r.kind === "go") {
      if (k === "Enter") openExceptions();
    }
  }

  /* ── exceptions render ────────────────────────────────────────────── */
  function renderExceptions() {
    excList.innerHTML = "";
    App.excRows = TV.INSTALLED.map((app) => {
      const excluded = State.excluded.has(app.id);
      const row = document.createElement("div");
      row.className = "exc-row" + (excluded ? " excluded" : "");
      const tile = document.createElement("div");
      tile.className = "er-tile"; tile.style.setProperty("--tile", app.color);
      tile.innerHTML = TV.glyphHTML(app.glyph);
      const name = document.createElement("div"); name.className = "er-name"; name.textContent = app.name;
      const cat = document.createElement("div"); cat.className = "er-cat"; cat.textContent = app.cat;
      const st = document.createElement("div"); st.className = "er-state";
      st.textContent = excluded ? "Hidden from recents" : "Shown";
      const tog = buildToggle(excluded);
      row.append(tile, name, cat, st, tog);
      excList.appendChild(row);
      return { el: row, app };
    });
    applyExcFocus();
  }
  function applyExcFocus() {
    App.excRows.forEach((r, i) => r.el.classList.toggle("focused", i === App.excFocus));
    const scroll = document.querySelector(".exc-scroll");
    const row = App.excRows[App.excFocus] && App.excRows[App.excFocus].el;
    if (!row || !scroll) return;
    const top = row.offsetTop, h = row.offsetHeight, vh = scroll.clientHeight;
    let cur = App.excScroll;
    if (top - cur < 20) cur = top - 20;
    if (top + h - cur > vh - 20) cur = top + h - (vh - 20);
    cur = Math.max(0, cur);
    App.excScroll = cur;
    excList.style.transform = "translateY(" + -cur + "px)";
  }
  function excKey(k) {
    if (k === "ArrowUp") { App.excFocus = Math.max(0, App.excFocus - 1); applyExcFocus(); }
    else if (k === "ArrowDown") { App.excFocus = Math.min(App.excRows.length - 1, App.excFocus + 1); applyExcFocus(); }
    else if (k === "Enter") {
      const app = App.excRows[App.excFocus].app;
      if (State.excluded.has(app.id)) State.excluded.delete(app.id);
      else State.excluded.add(app.id);
      renderExceptions();
    }
  }

  /* ── shizuku ──────────────────────────────────────────────────────── */
  function applyShzFocus() {
    const btns = document.querySelectorAll("#shizuku .btn");
    btns.forEach((b, i) => b.classList.toggle("focused", i === App.shzFocus));
  }
  function shzKey(k) {
    const btns = document.querySelectorAll("#shizuku .btn");
    if (k === "ArrowLeft") App.shzFocus = Math.min(btns.length - 1, App.shzFocus + 1);
    else if (k === "ArrowRight") App.shzFocus = Math.max(0, App.shzFocus - 1);
    else if (k === "Enter") { State.tasks = [...DEFAULT_TASKS]; setScene("overlay"); return; }
    applyShzFocus();
  }

  /* ── scene routing ────────────────────────────────────────────────── */
  function clearScreens() {
    ["show-overlay", "show-empty", "show-settings", "show-shizuku"].forEach((c) => tv.classList.remove(c));
    document.querySelectorAll(".screen").forEach((sc) => sc.classList.remove("active"));
  }
  function setScene(scene) {
    State.overlayClosed = false;
    closedHint.classList.remove("show");
    launch.classList.remove("show");
    clearScreens();
    if (scene === "overlay" && !liveTasks().length) scene = "empty";
    State.scene = scene;

    if (scene === "overlay") {
      tv.classList.add("show-overlay"); overlay.classList.add("active"); App.screen = "overlay";
      renderCarousel(); App.ovFocus = defaultOvFocus(); applyOverlayFocus();
    } else if (scene === "empty") {
      tv.classList.add("show-empty"); overlay.classList.add("active"); App.screen = "overlay";
      renderCarousel();
    } else if (scene === "settings") {
      tv.classList.add("show-settings"); settings.classList.add("active"); App.screen = "settings";
      App.setFocus = 0; renderSettings();
    } else if (scene === "shizuku") {
      tv.classList.add("show-shizuku"); shizuku.classList.add("active"); App.screen = "shizuku";
      App.shzFocus = 0; applyShzFocus();
    }
    updateScenePicker();
  }
  function openExceptions() {
    document.querySelectorAll(".screen").forEach((sc) => sc.classList.remove("active"));
    exceptions.classList.add("active"); App.screen = "exceptions";
    App.excFocus = 0; App.excScroll = 0; renderExceptions();
  }
  function openSettings() {
    document.querySelectorAll(".screen").forEach((sc) => sc.classList.remove("active"));
    settings.classList.add("active"); App.screen = "settings"; renderSettings();
  }
  function back() {
    if (App.screen === "exceptions") openSettings();
    else if (App.screen === "settings" || App.screen === "shizuku") setScene("overlay");
    else if (App.screen === "overlay" && !State.overlayClosed && State.scene !== "empty") closeOverlay();
  }
  function updateScenePicker() {
    const map = { overlay: "overlay", empty: "empty", settings: "settings", shizuku: "shizuku" };
    const cur = App.screen === "exceptions" ? "settings" : map[State.scene] || State.scene;
    document.querySelectorAll("#scenePicker button").forEach((b) => {
      b.classList.toggle("on", b.dataset.scene === cur);
    });
  }

  /* ── input ────────────────────────────────────────────────────────── */
  const KEYS = { ArrowLeft: 1, ArrowRight: 1, ArrowUp: 1, ArrowDown: 1, Enter: 1 };
  window.addEventListener("keydown", (e) => {
    if (embed) return;
    if (e.key === "Escape" || e.key === "Backspace") { e.preventDefault(); back(); return; }
    if (!KEYS[e.key]) return;
    e.preventDefault();
    if (App.screen === "overlay") overlayKey(e.key);
    else if (App.screen === "settings") settingsKey(e.key);
    else if (App.screen === "exceptions") excKey(e.key);
    else if (App.screen === "shizuku") shzKey(e.key);
  });
  document.querySelectorAll("#scenePicker button").forEach((b) => {
    b.addEventListener("click", () => {
      const sc = b.dataset.scene;
      if (sc === "overlay") State.tasks = [...DEFAULT_TASKS];
      if (sc === "empty") State.tasks = [];
      setScene(sc);
      window.focus();
    });
  });

  /* ── scaling ──────────────────────────────────────────────────────── */
  function fit() {
    const isEmbed = document.body.classList.contains("embed");
    const ch = isEmbed ? 0 : chrome.offsetHeight + 30;
    const pad = isEmbed ? 0 : 48;
    const sc = Math.min((window.innerWidth - pad) / 1920, (window.innerHeight - ch - (isEmbed ? 0 : 40)) / 1080);
    tv.style.cssText += ";position:absolute;top:0;left:0;transform-origin:0 0;transform:scale(" + sc + ")";
    tvwrap.style.width = 1920 * sc + "px";
    tvwrap.style.height = 1080 * sc + "px";
  }
  window.addEventListener("resize", fit);

  /* ── embed / frame mode (for the state-map canvas) ────────────────── */
  const params = new URLSearchParams(location.search);
  const frame = params.get("frame");
  const embed = !!frame;
  if (embed) document.body.classList.add("embed");

  function applyFrame(f) {
    State.tasks = [...DEFAULT_TASKS]; State.settings.shape = "rounded"; applyCardSettings();
    if (f === "overlay") setScene("overlay");
    else if (f === "killall") { setScene("overlay"); App.ovFocus = 0; applyOverlayFocus(); }
    else if (f === "peek") {
      setScene("overlay"); applyOverlayFocus();
      const it = App.focusItems[App.ovFocus]; if (it) it.el.classList.add("peek-up");
    } else if (f === "dismiss") {
      setScene("overlay"); applyOverlayFocus();
      const it = App.focusItems[App.ovFocus]; if (it) it.el.classList.add("midflight");
    } else if (f === "empty") { State.tasks = []; setScene("empty"); }
    else if (f === "settings") setScene("settings");
    else if (f === "circle") {
      State.settings.shape = "circle"; setScene("settings"); applyCardSettings();
      App.setFocus = 2; applySettingsFocus();
    } else if (f === "exceptions") { setScene("settings"); openExceptions(); App.excFocus = 2; applyExcFocus(); }
    else if (f === "shizuku") setScene("shizuku");
  }

  /* ── boot ─────────────────────────────────────────────────────────── */
  applyCardSettings();
  fit();
  window.__frame = (f) => { document.body.classList.add("embed"); applyFrame(f); fit(); };
  if (embed) applyFrame(frame);
  else setScene("overlay");
  // re-center once fonts settle (card sizes are fixed px, but be safe)
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => { if (App.screen === "overlay") centerFocused(); });
})();
