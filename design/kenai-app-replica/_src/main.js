"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* The canvas: toolbar, phone frame (status bar, gesture bar), the router of MainActivity.kt, and window.kenai for T1b. */

const $ = (s, r = document) => r.querySelector(s);
/** Every template escapes its text with esc(); markup is set through one helper. */
const setHTML = (el, html) => el.replaceChildren(document.createRange().createContextualFragment(html));
const STEP_TEXT = [
  "Claude Code thinking", "Claude working · Codex searching", "Approval: Bash npm run build", "Claude building · Codex asks “Which branch?”",
  "Claude finished · Codex error", "Codex rate limited", "Gemini CLI sleeping", "Approval resolved · no agents",
];

function toolbar() {
  setHTML($("#tbar"), `<div class="brand"><b>Kenai for Android · functional replica</b><span>Today's app, English, dark · DemoLink loop, 2.5 s a step</span></div>
  <button type="button" class="cb" id="play" data-tb="play"></button>
  <button type="button" class="cb" id="step" data-tb="step">Step</button>
  <div class="ctl" id="stepinfo"></div>
  <div class="seg" role="group" aria-label="Mode" id="mode">
    <button type="button" data-tb="mode" data-arg="none">Not paired</button>
    <button type="button" data-tb="mode" data-arg="demo">Demo mode</button>
    <button type="button" data-tb="mode" data-arg="paired">Paired (fake data)</button></div>
  <label class="ctl" id="linkctl">Link <select id="linksel"><option value="connected">Connected</option><option value="lost">Connection lost</option><option value="notfound">Can't find computer</option></select></label>`);
  $("#tbar").addEventListener("click", (e) => {
    const b = e.target.closest("[data-tb]");
    if (!b) return;
    if (b.dataset.tb === "play") { st.playing = !st.playing; schedule(); changed(); }
    if (b.dataset.tb === "step") { if (st.mode === "none") setMode("demo"); else { step(); schedule(); changed(); } }
    if (b.dataset.tb === "mode") setMode(b.dataset.arg);
  });
  $("#linksel").addEventListener("change", (e) => { st.link = e.target.value; st.batterySnoozed = false; st.diag = { running: false, results: [] }; changed(); });
}
function updateToolbar() {
  $("#play").textContent = st.playing ? "Pause" : "Play";
  $("#play").setAttribute("aria-pressed", String(!st.playing));
  setHTML($("#stepinfo"), st.mode === "none" ? "<i>Demo loop stopped (not paired)</i>" : `Step ${st.step + 1} of 8 · <i>${esc(STEP_TEXT[st.step] || "")}</i>`);
  document.querySelectorAll("#mode button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.arg === st.mode)));
  $("#linkctl").hidden = st.mode !== "paired";
  $("#linksel").value = st.link;
}

const TEXT_FIELD = "input[type=text],input[type=url],textarea";
function phoneSkeleton() {
  setHTML($("#phone"), `<div class="sbar"><span id="clock"></span><span class="cam"></span><span class="r"><i></i><i class="w"></i></span></div>
  <div class="screen" id="screen"><div id="content"></div><div id="bar"></div><div id="layer"></div><div id="dlg"></div><div id="bio"></div><div id="sys"></div></div>
  <div class="gbar"><button type="button" aria-label="Back (system gesture)" data-act="sysBack"></button></div>`);
  const phone = $("#phone");
  phone.addEventListener("click", (e) => {
    const el = e.target.closest("[data-act]");
    if (!el || el.disabled || !phone.contains(el)) return;
    const fn = ACT[el.dataset.act];
    if (fn) fn(el.dataset.arg, el, e);
  });
  phone.addEventListener("input", (e) => {
    const fn = INPUT[e.target.dataset.input];
    if (fn) fn(e.target.value, e.target);
  });
  phone.addEventListener("focusin", (e) => { if (!st.keyboard && e.target.matches(TEXT_FIELD)) { st.keyboard = true; changed(); } });
  phone.addEventListener("focusout", (e) => { if (e.target.matches(TEXT_FIELD)) setTimeout(() => { const a = document.activeElement; if (st.keyboard && !(a && a.matches && a.matches(TEXT_FIELD))) { st.keyboard = false; changed(); } }, 0); });
}
/** System Back: closes what is on top first, then goes up one level (Navigation.kt Nav.back). */
ACT.sysBack = () => {
  if (st.bio) { ACT.bioCancel(); return; }
  if (st.sys) { st.sys = null; changed(); return; }
  if (st.pairRequest) { st.pairRequest = null; changed(); return; }
  if (st.chat.confirmClear || st.chat.menu) { st.chat.confirmClear = false; st.chat.menu = false; changed(); return; }
  if (st.diff) { st.diff = null; changed(); return; }
  if (st.paste) { st.paste = null; changed(); return; }
  if (st.address) { st.address = null; changed(); return; }
  if (st.questionPill) { st.questionPill = null; changed(); return; }
  const pending = typeof pendingApproval === "function" && pendingApproval();
  if (pending) { st.closedApproval = pending.fp; changed(); return; }
  ACT.back();
};

const SCREENS = {
  HOME: () => homeScreen(),
  SCAN: () => scanScreen(),
  CHAT: () => (typeof chatScreen === "function" ? chatScreen() : ""),
  SETTINGS: () => settingsScreen(),
  HISTORY: () => historyScreen(),
  GALLERY: () => galleryScreen(),
  DIAGNOSTICS: () => diagnosticsScreen(),
  LICENCES: () => licencesScreen(),
  SESSION: () => sessionScreen(),
};
const scrollMem = {};
let lastScreen = null;
const layerKeys = {};
function setLayer(id, out) {
  const el = $("#" + id);
  const key = out ? out.key : "";
  const fresh = key !== layerKeys[id];
  const keepScroll = el.querySelector(".sheet");
  const top = keepScroll && !fresh ? keepScroll.scrollTop : 0;
  setHTML(el, out ? out.html : "");
  const sh = el.querySelector(".sheet");
  if (sh) { if (fresh) sh.classList.add("enter"); sh.scrollTop = top; }
  layerKeys[id] = key;
}
function render() {
  const a = document.activeElement;
  const focusId = a && a.id && $("#phone").contains(a) ? a.id : null;
  const sel = focusId && "selectionStart" in a ? [a.selectionStart, a.selectionEnd] : null;
  const old = $("#content .scr");
  if (old && lastScreen) scrollMem[lastScreen] = old.scrollTop;
  const top = st.screen === "HOME" || st.screen === "SCAN" ? "" : "ptop";
  const chatPad = st.screen === "CHAT" && barVisible() ? "padding-bottom:96px" : "";
  setHTML($("#content"), `<div class="${top}" style="position:absolute;inset:0;${chatPad}"><div style="position:relative;height:100%">${SCREENS[st.screen]()}</div></div>`);
  const scr = $("#content .scr");
  // Same screen: keep the scroll. A screen left and opened again starts at the top, as a recomposed LazyColumn does.
  if (scr) scr.scrollTop = st.screen === lastScreen ? scrollMem[st.screen] || 0 : 0;
  if (typeof afterScreen === "function") afterScreen();
  lastScreen = st.screen;
  setHTML($("#bar"), barVisible() ? bottomBar() : "");
  setLayer("layer", typeof sheetLayer === "function" ? sheetLayer() : null);
  setLayer("dlg", typeof dialogLayer === "function" ? dialogLayer() : null);
  setLayer("bio", typeof bioLayer === "function" ? bioLayer() : null);
  setLayer("sys", typeof sysLayer === "function" ? sysLayer() : null);
  if (focusId) {
    const n = document.getElementById(focusId);
    if (n) { n.focus(); if (sel && "setSelectionRange" in n) try { n.setSelectionRange(sel[0], sel[1]); } catch (_) { /* not a text field */ } }
  }
  paintKoda($("#phone"));
  $("#clock").textContent = new Date().toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
  updateToolbar();
}

/* The API the widget panel (T1b) drives and reads. */
window.kenai = {
  state: st,
  go,
  subscribe(fn) { subs.push(fn); return () => { const i = subs.indexOf(fn); if (i >= 0) subs.splice(i, 1); }; },
  openApproval() { st.closedApproval = null; if (st.screen === "SCAN") st.screen = "HOME"; changed(); return approvals().length > 0; },
  openQuestion(pill) {
    const p = pill || (sessions().find((s) => s.state === "question") || {}).pill;
    if (!p) return false;
    st.questionPill = p; changed(); return true;
  },
  setMode, step: () => { step(); changed(); },
  play(on) { st.playing = on !== false; schedule(); changed(); },
  derived: { sessions, approvals, focus: () => focusOf(sessions()), linkText, connected },
};

toolbar();
phoneSkeleton();
setInterval(() => { const c = $("#clock"); if (c) c.textContent = new Date().toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" }); }, 30000);
const startMode = new URLSearchParams(location.search).get("mode");
setMode(["none", "demo", "paired"].includes(startMode) ? startMode : "demo");
