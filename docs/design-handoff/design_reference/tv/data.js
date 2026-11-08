/* ── Recent Apps TV · data + icon rendering ──────────────────────────── */
/* Original app identities (no real logos). Each "icon" is a colored
   squircle with a simple glyph (letter or basic shape). */

window.TV = window.TV || {};

/* most-recent first. #1 sits immediately left of Kill All in the RTL row. */
TV.APPS = [
  { id: "vista",   name: "Vista",    color: "#E0464E", glyph: "play",      cat: "Streaming" },
  { id: "resound", name: "Resound",  color: "#8B5CF6", glyph: "wave",      cat: "Music" },
  { id: "vela",    name: "Vela",     color: "#2A9DF4", glyph: "ring",      cat: "Browser" },
  { id: "loop",    name: "Loop",     color: "#F0653A", glyph: "L",         cat: "Short video" },
  { id: "nimbus",  name: "Nimbus",   color: "#5865F2", glyph: "diamond",   cat: "Cloud games" },
  { id: "kickoff", name: "Kickoff",  color: "#F2A93B", glyph: "K",         cat: "Sports" },
  { id: "mosaic",  name: "Mosaic",   color: "#14B8C4", glyph: "grid",      cat: "Photos" },
  { id: "encore",  name: "Encore",   color: "#EC4899", glyph: "E",         cat: "Live TV" },
];

/* installed-apps universe for the Exceptions list (apps + a few system ones).
   excluded=true means hidden from the overlay. */
TV.INSTALLED = [
  ...TV.APPS.map(a => ({ id: a.id, name: a.name, color: a.color, glyph: a.glyph, cat: a.cat })),
  { id: "store",    name: "App Store", color: "#3B82F6", glyph: "S", cat: "System" },
  { id: "home",     name: "Home",      color: "#64748B", glyph: "H", cat: "System" },
  { id: "files",    name: "Files",     color: "#10B981", glyph: "F", cat: "System" },
  { id: "settings2",name: "Settings",  color: "#94A3B8", glyph: "G", cat: "System", excluded: true },
];

/* returns inner HTML for a glyph inside a colored tile */
TV.glyphHTML = function (g) {
  switch (g) {
    case "play":
      return `<span class="gl-play"></span>`;
    case "ring":
      return `<span class="gl-ring"></span>`;
    case "diamond":
      return `<span class="gl-diamond"></span>`;
    case "grid":
      return `<span class="gl-grid"><i></i><i></i><i></i><i></i></span>`;
    case "wave":
      return `<span class="gl-wave"><i></i><i></i><i></i><i></i></span>`;
    default:
      return `<span class="gl-letter">${g}</span>`;
  }
};

/* builds a single app-icon tile element (the colored squircle).
   `cls` extra classes; shape applied by parent via data-shape. */
TV.iconTile = function (app, extra) {
  const el = document.createElement("div");
  el.className = "app-tile" + (extra ? " " + extra : "");
  el.style.setProperty("--tile", app.color);
  el.innerHTML = TV.glyphHTML(app.glyph);
  return el;
};
