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
    pinned: ["mosaic", "store", "files"],
    settings: { showKillAll: true, size: "medium", shape: "rounded", pinPos: "left", pinSize: "small", bg: "dim", bgColor: 0 },
  };
  const BG_COLORS = [
    { name: "Graphite", c: "#12151a" }, { name: "Midnight", c: "#0b1733" }, { name: "Forest", c: "#0a2619" },
    { name: "Plum", c: "#24112f" }, { name: "Black", c: "#000000" },
  ];
  function applyBg() {
    const S = State.settings, sc = $("#scrim");
    sc.dataset.bg = S.bg;
    sc.style.setProperty("--bg-color", BG_COLORS[S.bgColor].c);
  }
  const App = {
    screen: "overlay",
    focusItems: [], ovFocus: 0,
    setRows: [], setFocus: 0,
    excRows: [], excFocus: 0, excScroll: 0,
    shzFocus: 0, setScroll: 0, excAct: 0, listMode: "exceptions",
    menu: null, holdT: null, holdArmed: false,
  };

  const notExcluded = (id) => !State.excluded.has(id);
  const livePins = () => State.pinned.filter(notExcluded);
  const liveTasks = () => State.tasks.filter((id) => notExcluded(id) && !State.pinned.includes(id));
  const ICON = {
    pin: '<svg viewBox="0 0 24 24"><path d="M9 3h6l-1 6 3.5 3.5V15h-11v-2.5L10 9z"/><path d="M12 15v6"/></svg>',
    unpin: '<svg viewBox="0 0 24 24"><path d="M9 3h6l-1 6 3.5 3.5V15h-11v-2.5L10 9z"/><path d="M12 15v6M3 3l18 18"/></svg>',
    hide: '<svg viewBox="0 0 24 24"><path d="M3 3l18 18"/><path d="M10.6 6.1A10 10 0 0 1 12 6c5 0 9 4.5 10 6-.5.8-1.6 2.2-3.1 3.5M6.6 7.6C4.6 8.9 2.9 10.8 2 12c1 1.5 5 6 10 6 1.6 0 3.1-.5 4.4-1.2"/><path d="M9.9 10a3 3 0 0 0 4.1 4.1"/></svg>',
    show: '<svg viewBox="0 0 24 24"><path d="M2 12c1-1.5 5-6 10-6s9 4.5 10 6c-1 1.5-5 6-10 6S3 13.5 2 12z"/><circle cx="12" cy="12" r="3"/></svg>',
    info: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.5"/></svg>',
  };
  const toastEl = $("#toast"); let toastT;
  function toast(msg) { toastEl.textContent = msg; toastEl.classList.add("show"); clearTimeout(toastT); toastT = setTimeout(() => toastEl.classList.remove("show"), 1800); }

  /* ── card / kill-all builders ─────────────────────────────────────── */
  function makeCard(app, pinned) {
    const c = document.createElement("div");
    c.className = "card" + (pinned ? " pinned" : "");
    const tile = TV.iconTile(app);
    if (pinned) {
      const b = document.createElement("div"); b.className = "pin-badge"; b.innerHTML = ICON.pin; tile.appendChild(b);
      if (State.tasks.includes(app.id)) { const d = document.createElement("div"); d.className = "run-dot"; c.appendChild(d); }
    }
    c.appendChild(tile);
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
    let i = App.focusItems.findIndex((it) => it.type === "card" && !it.pinned);
    if (i < 0) i = App.focusItems.findIndex((it) => it.type === "card");
    return Math.max(0, i);
  }
  function renderCarousel() {
    applyCardSettings();
    strip.innerHTML = "";
    App.focusItems = [];
    const tasks = liveTasks().map(byId).filter(Boolean);
    const pins = livePins().map(byId).filter(Boolean);
    const px = parseInt(SIZE[State.settings.size], 10);
    tv.style.setProperty("--pin-size", (State.settings.pinSize === "small" ? Math.round(px * 0.72) : px) + "px");
    const addKA = () => {
      if (!(State.settings.showKillAll && tasks.length)) return;
      const ka = makeKillAll(); strip.appendChild(ka); App.focusItems.push({ type: "killall", el: ka });
    };
    const addTasks = () => {
      if (!tasks.length && pins.length) {
        const e = document.createElement("div"); e.className = "empty-slot";
        e.innerHTML = '<div class="t">No recent apps</div><div class="s">Pinned apps are still one press away.</div>';
        strip.appendChild(e); return;
      }
      tasks.forEach((app) => { const card = makeCard(app, false); strip.appendChild(card); App.focusItems.push({ type: "card", el: card, app }); });
    };
    const addPins = () => {
      if (!pins.length) return;
      const g = document.createElement("div"); g.className = "pin-group";
      const lbl = document.createElement("div"); lbl.className = "pin-label"; lbl.innerHTML = ICON.pin + "<span>Pinned</span>";
      g.appendChild(lbl);
      pins.forEach((app) => {
        const card = makeCard(app, true);
        card.style.setProperty("--card-size", "var(--pin-size)");
        g.appendChild(card); App.focusItems.push({ type: "card", el: card, app, pinned: true });
      });
      strip.appendChild(g);
    };
    addKA();
    if (State.settings.pinPos === "right") { addPins(); addTasks(); } else { addTasks(); addPins(); }
    ovCount.textContent = tasks.length ? tasks.length + (tasks.length === 1 ? " app" : " apps") + (pins.length ? " · " + pins.length + " pinned" : "") : (pins.length ? pins.length + " pinned" : "");
  }
  function centerFocused() {
    const it = App.focusItems[App.ovFocus];
    if (!it) { strip.style.transform = "translate(" + (1920 - strip.scrollWidth) / 2 + "px, -50%)"; return; }
    const k = tv.getBoundingClientRect().width / 1920 || 1;
    const sr = strip.getBoundingClientRect(), er = it.el.getBoundingClientRect();
    const center = (er.left + er.width / 2 - sr.left) / k;
    const pad = 76, w = strip.scrollWidth;
    const tx = w > 1920 - pad * 2 ? Math.max(1920 - pad - w, Math.min(pad, 960 - center)) : (1920 - w) / 2;
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
    if (App.menu) { menuKey(k); return; }
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
    if (it.type !== "card" || it.pinned) return;
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
    if (!liveTasks().length && !livePins().length) { State.scene = "empty"; setScene("empty"); return; }
    renderCarousel();
    App.ovFocus = Math.max(0, Math.min(at, App.focusItems.length - 1));
    applyOverlayFocus();
  }

  /* ── long-press OK menu ───────────────────────────────────────────── */
  function openMenu() {
    const it = App.focusItems[App.ovFocus];
    if (!it || it.type !== "card") return;
    it.el.classList.remove("pressing");
    clearPeek();
    const app = it.app, pinned = State.pinned.includes(app.id), running = State.tasks.includes(app.id);
    const items = [
      { id: "pin", label: pinned ? "Unpin" : "Pin to row", icon: pinned ? ICON.unpin : ICON.pin },
      { id: "exc", label: "Add to exceptions", icon: ICON.hide },
      { id: "info", label: "App info", icon: ICON.info },
    ];
    const m = document.createElement("div"); m.className = "ctx";
    const head = document.createElement("div"); head.className = "ctx-head";
    const t = TV.iconTile(app); const txt = document.createElement("div");
    txt.innerHTML = '<div class="t"></div><div class="s"></div>';
    txt.firstChild.textContent = app.name;
    txt.lastChild.textContent = [pinned ? "Pinned" : null, running ? "Running" : "Not running"].filter(Boolean).join(" · ");
    head.append(t, txt); m.appendChild(head);
    items.forEach((x) => { const r = document.createElement("div"); r.className = "ctx-item"; r.innerHTML = x.icon + "<span></span>"; r.lastChild.textContent = x.label; x.el = r; m.appendChild(r); });
    it.el.appendChild(m);
    App.menu = { it, items, focus: 0, el: m };
    tv.classList.add("menu-open");
    requestAnimationFrame(() => it.el.classList.add("menu-open"));
    menuFocus();
  }
  function menuFocus() { App.menu.items.forEach((x, i) => x.el.classList.toggle("focused", i === App.menu.focus)); }
  function closeMenu() {
    if (!App.menu) return;
    const { it, el } = App.menu;
    it.el.classList.remove("menu-open"); tv.classList.remove("menu-open");
    setTimeout(() => el.remove(), 220);
    App.menu = null;
  }
  function focusApp(id, fallback) {
    const i = App.focusItems.findIndex((x) => x.app && x.app.id === id);
    App.ovFocus = i >= 0 ? i : Math.max(0, Math.min(fallback, App.focusItems.length - 1));
    applyOverlayFocus();
  }
  function menuKey(k) {
    const M = App.menu;
    if (k === "ArrowUp") { M.focus = Math.max(0, M.focus - 1); menuFocus(); }
    else if (k === "ArrowDown") { M.focus = Math.min(M.items.length - 1, M.focus + 1); menuFocus(); }
    else if (k === "Enter") {
      const act = M.items[M.focus].id, app = M.it.app, at = App.ovFocus;
      closeMenu();
      if (act === "pin") {
        const on = State.pinned.includes(app.id);
        State.pinned = on ? State.pinned.filter((p) => p !== app.id) : [...State.pinned, app.id];
        if (!liveTasks().length && !livePins().length) { setScene("empty"); return; }
        renderCarousel(); focusApp(app.id, at);
        toast(on ? app.name + " unpinned" : app.name + " pinned");
      } else if (act === "exc") {
        State.excluded.add(app.id);
        M.it.el.classList.add("flyout-up");
        setTimeout(() => removeTask("__none__"), 330);
        toast(app.name + " added to exceptions");
      } else if (act === "info") {
        toast("Opening App info · " + app.name);
      }
    }
  }
  function activate(it) {
    if (it.type === "killall") killAll();
    else switchToApp(it.app, it);
  }
  /* Select animation: press-in → tile zooms from its spot to full screen in the app's
     color (icon + name) → overlay closes behind it → zoom fades into the app. */
  function switchToApp(app, it) {
    if (App.selecting) return;
    App.selecting = true;
    const tile = it.el.querySelector(".app-tile");
    const k = tv.getBoundingClientRect().width / 1920 || 1;
    const tr = tv.getBoundingClientRect(), r = tile.getBoundingClientRect();
    const z = document.createElement("div"); z.id = "zoom";
    z.style.left = (r.left - tr.left) / k + "px"; z.style.top = (r.top - tr.top) / k + "px";
    z.style.width = r.width / k + "px"; z.style.height = r.height / k + "px";
    z.style.borderRadius = getComputedStyle(tile).borderRadius;
    z.style.background = app.color;
    z.innerHTML = '<div class="z-ic">' + TV.glyphHTML(app.glyph) + '</div><div class="z-name"></div>';
    z.lastChild.textContent = app.name;
    tv.appendChild(z);
    tv.classList.add("selecting"); it.el.classList.add("chosen");
    z.getBoundingClientRect();
    App.selT = [
      setTimeout(() => z.classList.add("grow"), 20),
      setTimeout(() => { tv.classList.remove("selecting"); it.el.classList.remove("chosen"); closeOverlay(); z.classList.add("fade"); }, 900),
      setTimeout(() => { z.remove(); App.selecting = false; }, 1300),
    ];
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
  function section(t) { const e = document.createElement("div"); e.className = "set-section"; e.textContent = t; setList.appendChild(e); }
  function addRow(title, desc, ctrl, def) {
    const s = rowShell(title, desc); ctrl.forEach((c) => s.ctrl.appendChild(c)); setList.appendChild(s.row);
    App.setRows.push(Object.assign({ el: s.row }, def));
  }
  function posPreview() {
    const p = document.createElement("div"); p.className = "pos-prev";
    const L = State.settings.pinPos === "left";
    const seq = L ? ["p","p","p","gap","t","t","t","k"] : ["t","t","t","gap","p","p","p","k"];
    seq.forEach((x) => { const i = document.createElement(x === "gap" ? "span" : "i"); if (x === "gap") i.className = "gap"; else if (x !== "t") i.className = x; p.appendChild(i); });
    return p;
  }
  function sizePreview() {
    const p = document.createElement("div"); p.className = "size-prev";
    const sm = State.settings.pinSize === "small";
    [[30,""],[30,""],[sm ? 21 : 30,"p"],[sm ? 21 : 30,"p"]].forEach(([w, c]) => { const i = document.createElement("i"); i.style.width = i.style.height = w + "px"; if (c) i.className = c; p.appendChild(i); });
    return p;
  }
  function chevron() { const g = document.createElement("div"); g.className = "r-go"; g.textContent = "‹"; return g; }
  function renderSettings() {
    setList.innerHTML = "";
    App.setRows = [];
    const S = State.settings;
    section("Overlay");
    addRow("Show Kill All", "A clear-all tile at the start of the row.", [buildToggle(S.showKillAll)], { kind: "toggle", key: "showKillAll" });
    addRow("Size of task", "Scales every card in the overlay.", [buildSeg(["Small", "Medium", "Large"], ["small", "medium", "large"], S.size)],
      { kind: "seg", values: ["small", "medium", "large"], get: () => S.size, set: (v) => (S.size = v) });
    const prev = document.createElement("div"); prev.className = "shape-prev"; prev.dataset.shape = S.shape;
    addRow("Shape of task", "Corner style of every card.", [prev, buildSeg(["Square", "Rounded", "Circle"], ["square", "rounded", "circle"], S.shape)],
      { kind: "seg", values: ["square", "rounded", "circle"], get: () => S.shape, set: (v) => (S.shape = v) });
    section("Pinned apps");
    const np = State.pinned.length;
    addRow("Pinned apps", np ? np + (np === 1 ? " app pinned" : " apps pinned") : "No apps pinned", [chevron()], { kind: "go", target: "pins" });
    const seg = buildSeg(["Left", "Right"], ["left", "right"], S.pinPos); seg.classList.add("ltr");
    addRow("Pinned position", "Which end of the row pinned apps stay on.", [posPreview(), seg],
      { kind: "seg", ltr: true, values: ["left", "right"], get: () => S.pinPos, set: (v) => (S.pinPos = v) });
    addRow("Pinned icon size", "Pinned apps can be smaller than tasks.", [sizePreview(), buildSeg(["Small", "Same as tasks"], ["small", "same"], S.pinSize)],
      { kind: "seg", values: ["small", "same"], get: () => S.pinSize, set: (v) => (S.pinSize = v) });
    section("Background");
    addRow("Background", "What sits behind the row.", [buildSeg(["Dim", "Blur", "Color", "Image"], ["dim", "blur", "color", "image"], S.bg)],
      { kind: "seg", values: ["dim", "blur", "color", "image"], get: () => S.bg, set: (v) => { S.bg = v; applyBg(); } });
    if (S.bg === "color") {
      const sw = document.createElement("div"); sw.className = "swatches";
      BG_COLORS.forEach((b, i) => { const d = document.createElement("i"); d.style.background = b.c; if (i === S.bgColor) d.className = "sel"; sw.appendChild(d); });
      addRow("Background color", BG_COLORS[S.bgColor].name, [sw],
        { kind: "seg", values: BG_COLORS.map((_, i) => i), get: () => S.bgColor, set: (v) => { S.bgColor = v; applyBg(); } });
    }
    if (S.bg === "image") {
      const th = document.createElement("div"); th.className = "bg-thumb";
      addRow("Background image", "wallpaper.jpg · from device storage", [th, chevron()], { kind: "pick" });
    }
    section("Hidden apps");
    addRow("Exceptions", excLabel(), [chevron()], { kind: "go", target: "exceptions" });
    applySettingsFocus();
  }
  function excLabel() {
    const n = State.excluded.size;
    return n ? n + (n === 1 ? " app hidden" : " apps hidden") : "No apps hidden";
  }
  function applySettingsFocus() {
    App.setRows.forEach((r, i) => r.el.classList.toggle("focused", i === App.setFocus));
    const row = App.setRows[App.setFocus] && App.setRows[App.setFocus].el, box = document.querySelector(".set-scroll");
    if (!row || !box) return;
    const top = row.offsetTop, h = row.offsetHeight, vh = box.clientHeight;
    let cur = App.setScroll;
    if (top - cur < 60) cur = top - 60;
    if (top + h - cur > vh - 70) cur = top + h - (vh - 70);
    App.setScroll = Math.max(0, cur);
    setList.style.transform = "translateY(" + -App.setScroll + "px)";
  }
  function settingsKey(k) {
    const r = App.setRows[App.setFocus];
    if (k === "ArrowUp") { App.setFocus = Math.max(0, App.setFocus - 1); applySettingsFocus(); return; }
    if (k === "ArrowDown") { App.setFocus = Math.min(App.setRows.length - 1, App.setFocus + 1); applySettingsFocus(); return; }
    if (r.kind === "toggle") {
      if (k === "Enter" || k === "ArrowLeft" || k === "ArrowRight") { State.settings[r.key] = !State.settings[r.key]; renderSettings(); }
    } else if (r.kind === "seg") {
      const i = r.values.indexOf(r.get());
      let ni;
      if (k === "Enter") ni = (i + 1) % r.values.length;
      else if (k === "ArrowRight") ni = r.ltr ? i + 1 : i - 1;
      else if (k === "ArrowLeft") ni = r.ltr ? i - 1 : i + 1;
      else return;
      r.set(r.values[Math.max(0, Math.min(r.values.length - 1, ni))]);
      applyCardSettings(); renderSettings();
    } else if (r.kind === "go" && k === "Enter") openList(r.target);
    else if (r.kind === "pick" && k === "Enter") toast("Opening image picker…");
  }

  /* ── list screens: exceptions + pinned apps ───────────────────────── */
  const LIST = {
    exceptions: {
      title: "Exceptions", sub: "Apps switched on here are hidden from the overlay.",
      has: (id) => State.excluded.has(id),
      toggle: (id) => (State.excluded.has(id) ? State.excluded.delete(id) : State.excluded.add(id)),
      on: "Hidden from recents", off: "Shown",
      count: () => "<b>" + State.excluded.size + "</b> of " + TV.INSTALLED.length + " hidden",
      actions: [
        { label: "Hide all", icon: "hide", run: () => TV.INSTALLED.forEach((a) => State.excluded.add(a.id)), msg: "All apps hidden from recents" },
        { label: "Show all", icon: "show", run: () => State.excluded.clear(), msg: "All apps shown in recents" },
      ],
    },
    pins: {
      title: "Pinned apps", sub: "Pinned apps always appear in the row, running or not.",
      has: (id) => State.pinned.includes(id),
      toggle: (id) => (State.pinned = State.pinned.includes(id) ? State.pinned.filter((p) => p !== id) : [...State.pinned, id]),
      on: "Pinned", off: "Not pinned",
      count: () => "<b>" + State.pinned.length + "</b> pinned",
      actions: [{ label: "Unpin all", icon: "unpin", run: () => (State.pinned = []), msg: "All apps unpinned" }],
    },
  };
  function renderExceptions() {
    const L = LIST[App.listMode];
    $("#listTitle").textContent = L.title;
    $("#listSub").textContent = L.sub;
    const bar = $("#listActions"); bar.innerHTML = "";
    App.excBtns = L.actions.map((a) => { const b = document.createElement("div"); b.className = "btn sm"; b.innerHTML = ICON[a.icon] + "<span></span>"; b.lastChild.textContent = a.label; bar.appendChild(b); return b; });
    const cnt = document.createElement("div"); cnt.className = "exc-count"; cnt.innerHTML = L.count(); bar.appendChild(cnt);
    excList.innerHTML = "";
    App.excRows = TV.INSTALLED.map((app) => {
      const on = L.has(app.id);
      const row = document.createElement("div");
      row.className = "exc-row" + (on ? " excluded" : "");
      const tile = document.createElement("div");
      tile.className = "er-tile"; tile.style.setProperty("--tile", app.color);
      tile.innerHTML = TV.glyphHTML(app.glyph);
      const name = document.createElement("div"); name.className = "er-name"; name.textContent = app.name;
      const cat = document.createElement("div"); cat.className = "er-cat"; cat.textContent = app.cat;
      const st = document.createElement("div"); st.className = "er-state"; st.textContent = on ? L.on : L.off;
      row.append(tile, name, cat, st, buildToggle(on));
      excList.appendChild(row);
      return { el: row, app };
    });
    applyExcFocus();
  }
  function applyExcFocus() {
    App.excBtns.forEach((b, i) => b.classList.toggle("focused", App.excFocus === 0 && i === App.excAct));
    App.excRows.forEach((r, i) => r.el.classList.toggle("focused", i + 1 === App.excFocus));
    const scroll = document.querySelector(".exc-scroll");
    const row = App.excFocus > 0 && App.excRows[App.excFocus - 1].el;
    if (!row) { App.excScroll = 0; excList.style.transform = "translateY(0)"; return; }
    const top = row.offsetTop, h = row.offsetHeight, vh = scroll.clientHeight;
    let cur = App.excScroll;
    if (top - cur < 20) cur = top - 20;
    if (top + h - cur > vh - 20) cur = top + h - (vh - 20);
    App.excScroll = Math.max(0, cur);
    excList.style.transform = "translateY(" + -App.excScroll + "px)";
  }
  function excKey(k) {
    const L = LIST[App.listMode];
    if (k === "ArrowUp") { App.excFocus = Math.max(0, App.excFocus - 1); applyExcFocus(); }
    else if (k === "ArrowDown") { App.excFocus = Math.min(App.excRows.length, App.excFocus + 1); applyExcFocus(); }
    else if (App.excFocus === 0 && (k === "ArrowLeft" || k === "ArrowRight")) {
      App.excAct = Math.max(0, Math.min(App.excBtns.length - 1, App.excAct + (k === "ArrowLeft" ? 1 : -1))); applyExcFocus();
    } else if (k === "Enter") {
      if (App.excFocus === 0) { const a = L.actions[App.excAct]; a.run(); toast(a.msg); }
      else L.toggle(App.excRows[App.excFocus - 1].app.id);
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
    if (scene === "overlay" && !liveTasks().length && !livePins().length) scene = "empty";
    State.scene = scene;

    if (scene === "overlay") {
      tv.classList.add("show-overlay"); overlay.classList.add("active"); App.screen = "overlay";
      renderCarousel(); App.ovFocus = defaultOvFocus(); applyOverlayFocus();
    } else if (scene === "empty") {
      tv.classList.add("show-empty"); overlay.classList.add("active"); App.screen = "overlay";
      renderCarousel();
    } else if (scene === "settings") {
      tv.classList.add("show-settings"); settings.classList.add("active"); App.screen = "settings";
      App.setFocus = 0; App.setScroll = 0; renderSettings();
    } else if (scene === "shizuku") {
      tv.classList.add("show-shizuku"); shizuku.classList.add("active"); App.screen = "shizuku";
      App.shzFocus = 0; applyShzFocus();
    }
    updateScenePicker();
  }
  function openList(mode) {
    document.querySelectorAll(".screen").forEach((sc) => sc.classList.remove("active"));
    exceptions.classList.add("active"); App.screen = "exceptions"; App.listMode = mode;
    App.excFocus = 1; App.excAct = 0; App.excScroll = 0; renderExceptions();
  }
  const openExceptions = () => openList("exceptions");
  function openSettings() {
    document.querySelectorAll(".screen").forEach((sc) => sc.classList.remove("active"));
    settings.classList.add("active"); App.screen = "settings"; renderSettings();
  }
  function back() {
    if (App.menu) { closeMenu(); return; }
    if (App.screen === "exceptions") openSettings();
    else if (App.screen === "settings" || App.screen === "shizuku") setScene("overlay");
    else if (App.screen === "overlay" && !State.overlayClosed && State.scene !== "empty") closeOverlay();
  }
  function updateScenePicker() {
    const map = { overlay: "overlay", empty: "empty", settings: "settings", shizuku: "shizuku" };
    const cur = App.screen === "exceptions" ? "settings" : map[State.scene] || State.scene;
    window.addEventListener("keyup", (e) => {
    if (e.key !== "Enter" || !App.holdArmed) return;
    App.holdArmed = false;
    document.querySelectorAll(".card.pressing").forEach((c) => c.classList.remove("pressing"));
    if (App.holdT) { clearTimeout(App.holdT); App.holdT = null; overlayKey("Enter"); }
  });
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
    if (App.screen === "overlay" && e.key === "Enter" && !State.overlayClosed && !App.menu) {
      if (e.repeat) return;
      const it = App.focusItems[App.ovFocus];
      if (it && it.type === "card") {
        App.holdArmed = true; it.el.classList.add("pressing");
        App.holdT = setTimeout(() => { App.holdT = null; openMenu(); }, 550);
        return;
      }
    }
    if (e.repeat && e.key === "Enter") return;
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
    closeMenu(); (App.selT || []).forEach(clearTimeout); document.querySelectorAll("#zoom").forEach((z) => z.remove());
    document.querySelectorAll(".card.chosen").forEach((c) => c.classList.remove("chosen")); tv.classList.remove("selecting"); App.selecting = false;
    State.tasks = [...DEFAULT_TASKS]; State.pinned = ["mosaic", "store", "files"]; State.excluded = new Set(["settings2"]);
    Object.assign(State.settings, { showKillAll: true, size: "medium", shape: "rounded", pinPos: "left", pinSize: "small", bg: "dim", bgColor: 0 });
    applyCardSettings(); applyBg();
    const focusPinned = () => { const i = App.focusItems.findIndex((x) => x.pinned); if (i >= 0) { App.ovFocus = i; applyOverlayFocus(); } };
    const setRow = (title) => { App.setFocus = App.setRows.findIndex((r) => r.el.querySelector(".r-title").textContent === title); applySettingsFocus(); };
    if (f === "overlay") setScene("overlay");
    else if (f === "killall") { setScene("overlay"); App.ovFocus = 0; applyOverlayFocus(); }
    else if (f === "peek") { setScene("overlay"); const it = App.focusItems[App.ovFocus]; if (it) it.el.classList.add("peek-up"); }
    else if (f === "dismiss") { setScene("overlay"); const it = App.focusItems[App.ovFocus]; if (it) it.el.classList.add("midflight"); }
    else if (f === "menu") { setScene("overlay"); App.ovFocus = 2; applyOverlayFocus(); openMenu(); }
    else if (f === "menu-pinned") { setScene("overlay"); focusPinned(); openMenu(); }
    else if (f === "pins-left") { setScene("overlay"); focusPinned(); }
    else if (f === "pins-right") { State.settings.pinPos = "right"; setScene("overlay"); focusPinned(); }
    else if (f === "pins-same") { State.settings.pinSize = "same"; setScene("overlay"); focusPinned(); }
    else if (f === "empty") { State.tasks = []; State.pinned = []; setScene("empty"); }
    else if (f === "empty-pins") { State.tasks = []; setScene("overlay"); App.ovFocus = 0; applyOverlayFocus(); }
    else if (f === "settings") setScene("settings");
    else if (f === "circle") { State.settings.shape = "circle"; setScene("settings"); applyCardSettings(); App.setFocus = 2; applySettingsFocus(); }
    else if (f === "settings-pins") { setScene("settings"); setRow("Pinned position"); }
    else if (f === "pins-list") { setScene("settings"); openList("pins"); App.excFocus = 3; applyExcFocus(); }
    else if (f === "exceptions") { setScene("settings"); openList("exceptions"); App.excFocus = 3; applyExcFocus(); }
    else if (f === "exc-bulk") { setScene("settings"); openList("exceptions"); App.excFocus = 0; applyExcFocus(); }
    else if (f === "exc-allhidden") { TV.INSTALLED.forEach((a) => State.excluded.add(a.id)); setScene("settings"); openList("exceptions"); App.excFocus = 0; App.excAct = 1; applyExcFocus(); }
    else if (f === "shizuku") setScene("shizuku");
    else if (f === "select-press") { setScene("overlay"); App.focusItems[App.ovFocus].el.classList.add("pressing"); }
    else if (f === "select" || f === "select-mid" || f === "select-full") {
      setScene("overlay"); App.selecting = false;
      const it = App.focusItems[App.ovFocus]; switchToApp(it.app, it);
      if (f !== "select") {
        App.selT.forEach(clearTimeout);
        const z = $("#zoom"); z.style.transition = "none"; z.classList.remove("grow");
        if (f === "select-mid") { const r = { l: parseFloat(z.style.left), t: parseFloat(z.style.top), w: parseFloat(z.style.width) };
          z.style.left = r.l * 0.45 + "px"; z.style.top = r.t * 0.45 + "px"; z.style.width = r.w + (1920 - r.w) * 0.55 + "px"; z.style.height = r.w + (1080 - r.w) * 0.55 + "px"; z.style.borderRadius = "18px";
          z.querySelector(".z-ic").style.transform = "scale(1.1)"; }
        else { z.classList.add("grow"); z.querySelector(".z-name").style.transition = "none"; }
      }
    }
    else if (f === "settings-bg") { State.settings.bg = "color"; State.settings.bgColor = 1; applyBg(); setScene("settings"); setRow("Background color"); }
    else if (f === "settings-bg-image") { State.settings.bg = "image"; applyBg(); setScene("settings"); setRow("Background image"); }
    else if (f === "bg-blur") { State.settings.bg = "blur"; applyBg(); setScene("overlay"); }
    else if (f === "bg-color") { State.settings.bg = "color"; State.settings.bgColor = 1; applyBg(); setScene("overlay"); }
    else if (f === "bg-image") { State.settings.bg = "image"; applyBg(); setScene("overlay"); }
  }

  /* ── boot ─────────────────────────────────────────────────────────── */
  applyCardSettings(); applyBg();
  fit();
  window.__frame = (f) => { document.body.classList.add("embed"); applyFrame(f); fit(); };
  if (embed) applyFrame(frame);
  else setScene("overlay");
  // re-center once fonts settle (card sizes are fixed px, but be safe)
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => { if (App.screen === "overlay") centerFocused(); });
})();
