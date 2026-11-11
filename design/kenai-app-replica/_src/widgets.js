"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* The widget panel: an Android home screen (wallpaper, a grid of 10 x 6 cells of 94 x 106 dp) with the 9 widgets at the grid size of
   their res/xml/kenai_*_widget_info.xml (targetCellWidth x targetCellHeight). It reads and drives the phone only through window.kenai. */

const K = window.kenai;
/** [key, label (strings.xml), column, row, cells wide, cells high] */
const W_LAYOUT = [
  ["big", "widget_big_label", 0, 0, 4, 4],
  ["list", "widget_list_label", 4, 0, 4, 3],
  ["alert", "widget_alert_label", 8, 0, 2, 2],
  ["ring", "widget_ring_label", 8, 2, 2, 2],
  ["pill", "widget_pill_label", 4, 3, 4, 1],
  ["glance", "widget_glance_label", 0, 4, 3, 2],
  ["solo", "widget_solo_label", 3, 4, 2, 2],
  ["today", "widget_today_label", 5, 4, 2, 2],
  ["mood", "widget_mood_label", 7, 4, 1, 1],
];
const CELL_W = 94;
const CELL_H = 106;
const CELL_PAD = 4; // the launcher's inset around a widget
const wState = { theme: "dark", today: 0, last: null };

/**
 * What a tap opens, as the PendingIntents of WidgetHub do: openApp → Home; openScreen → that screen (LaunchScreen: RECENT is
 * History, PAIR is the pairing Scan); openSession → that agent's Session details. "approval" is the Waiting Alert while
 * something waits: the app opens on Home with the request's sheet up.
 */
function wOpen(to) {
  const [kind, arg] = to.split(":");
  if (kind === "session") { K.state.detailPill = arg; K.go("SESSION"); }
  else if (kind === "screen") K.go(arg);
  else if (kind === "approval") { K.go("HOME"); K.openApproval(); }
  else K.go("HOME");
  const ph = document.getElementById("phone");
  ph.classList.remove("flash");
  void ph.offsetWidth; // restart the flash
  ph.classList.add("flash");
}

function widgetsShell() {
  setHTML($("#widgets"), `<div class="kw-head"><span class="cap">Home screen · Kenai's 9 widgets at their grid size</span>
    <div class="kw-theme"><span>Widgets:</span><div class="seg" role="group" aria-label="Widget theme">
      <button type="button" data-wtheme="dark">dark</button><button type="button" data-wtheme="light">light</button></div></div></div>
    <div class="kw-home"><div class="kw-sbar"><span id="kw-clock"></span><span>Android launcher · 10 × 6 cells</span></div>
    <div class="kw-grid" id="kw-grid"></div>
    <p class="kw-legend"><b>Live from the phone:</b> every change redraws them, as WidgetHub pushes a new picture per change (no timer).
    A tap opens the phone like the real PendingIntents: the card opens Home or the top agent's Session details, the buttons their screen,
    the Waiting Alert the pending request. <b>Widgets: dark / light</b> follows the system on a real phone (values / values-notnight); the phone stays dark.</p></div>`);
  $("#widgets").addEventListener("click", (e) => {
    const t = e.target.closest("[data-wtheme]");
    if (t) { wState.theme = t.dataset.wtheme; drawWidgets(); return; }
    const b = e.target.closest("[data-to]");
    if (b && $("#kw-grid").contains(b)) wOpen(b.dataset.to);
  });
}

function drawWidgets() {
  const root = $("#widgets");
  root.classList.toggle("kw-light", wState.theme === "light");
  root.dataset.theme = wState.theme;
  root.querySelectorAll("[data-wtheme]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.wtheme === wState.theme)));
  const c = buildContent(K, wState.today);
  wState.last = c;
  const a = document.activeElement;
  const focus = a && a.id && a.id.startsWith("kw-") ? a.id : null;
  const html = W_LAYOUT.map(([key, label, col, row, cw, chh]) => {
    const w = cw * CELL_W - 2 * CELL_PAD;
    const h = chh * CELL_H - 2 * CELL_PAD;
    const r = W_RENDER[key](c, w, h);
    const pal = r.usage ? W_USAGE[r.usage] : W_TONE[r.look];
    const name = S[label];
    let n = 0;
    // Every button gets a stable id, so the keyboard focus survives a redraw.
    const inner = r.html.replace(/<button type="button"/g, () => `<button type="button" id="kw-${key}-${++n}"`);
    return `<div class="kw-cell" style="grid-column:${col + 1}/span ${cw};grid-row:${row + 1}/span ${chh}">
      <div class="kw" data-wid="${key}" data-look="${r.usage ? "USAGE_" + r.usage : r.look}" title="${esc(name)} · ${cw}×${chh}" style="--kw-border:${pal.border};--kw-tint:${pal.tint}">
      <div class="kw-tint"></div><button type="button" class="kw-hit" id="kw-${key}-0" data-to="${esc(r.open)}" aria-label="${esc(name + (r.label ? ": " + r.label : ""))}"></button>${inner}</div></div>`;
  }).join("");
  setHTML($("#kw-grid"), html);
  if (focus) { const el = document.getElementById(focus); if (el) el.focus(); }
  $("#kw-clock").textContent = new Date().toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

/** window.kenai.widgets: what the panel shows now, for the canvas check. */
K.widgets = { content: () => wState.last, today: () => wState.today, theme: (t) => { if (t) { wState.theme = t; drawWidgets(); } return wState.theme; } };

widgetsShell();
K.subscribe(() => { wState.today = todayCounter.observe(K); drawWidgets(); });
wState.today = todayCounter.observe(K);
drawWidgets();
