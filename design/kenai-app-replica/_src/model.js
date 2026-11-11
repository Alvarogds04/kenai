"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* The app's state (app/AppModel.kt, reduced), the demo script (link/DemoLink.kt) and the router (core/Navigation.kt). */

const st = {
  mode: "none", // Mode.NONE | DEMO | PAIRED; "paired" here is the fake desktop
  link: "connected", // paired only: connected | lost | notfound (DiscoveryState.NOT_FOUND)
  screen: "HOME",
  playing: true, cursor: 0, step: -1,
  sessions: [], approvals: [], questions: {},
  selected: null, detailPill: null, closedApproval: null, questionPill: null,
  decisions: [], message: null,
  settings: { overlayWished: false, overlayPermission: false, soundOn: true, volume: 0.12, notifyDone: true, quiet: { enabled: false, from: 1320, to: 480 } },
  batterySnoozed: false,
  chat: { messages: [], busy: false, model: "claude-sonnet", text: "", refusal: null, menu: false, confirmClear: false },
  diff: null, paste: null, address: null, pairRequest: null,
  camera: "ask", cameraAsked: false, scanWrong: false,
  sys: null, bio: null, diag: { running: false, results: [] },
};
const NOW = Date.now();
st.decisions = [
  { agent: "Claude Code", tool: "Edit", allowed: true, at: NOW - 26 * 3600e3 },
  { agent: "Codex", tool: "Bash", allowed: false, at: NOW - 27 * 3600e3 },
];

/* ---------- Navigation.kt ---------- */
const TAB_OF = { HOME: "HOME", CHAT: "CHAT", SETTINGS: "SETTINGS", HISTORY: "SETTINGS", GALLERY: "SETTINGS", DIAGNOSTICS: "SETTINGS", LICENCES: "SETTINGS", SESSION: null, SCAN: null };
const BACK = { HOME: null, CHAT: "HOME", SETTINGS: "HOME", SESSION: "HOME", SCAN: "HOME", HISTORY: "SETTINGS", GALLERY: "SETTINGS", DIAGNOSTICS: "SETTINGS", LICENCES: "SETTINGS" };
const barVisible = () => ["HOME", "CHAT", "SETTINGS"].includes(st.screen) && !st.keyboard;

/* ---------- derived, as AppModel and ui/LinkStatus.kt ---------- */
const connected = () => st.mode === "demo" || (st.mode === "paired" && st.link === "connected");
const sessions = () => (connected() ? st.sessions : []);
const approvals = () => (connected() ? st.approvals : []);
function linkText() {
  if (st.mode === "none") return S.status_not_connected;
  if (st.mode === "demo") return S.demo_mode;
  return st.link === "connected" ? `${S.status_connected} · ${DESKTOP.name}` : S.status_not_connected;
}
function linkDot() {
  if (st.mode === "demo") return "#B45309";
  if (st.mode === "paired" && st.link === "connected") return "#0F9F6E";
  return "#6B7280";
}
/** core/ChatTab.kt with AppModel:186-188 (demo is not paired). */
function chatTabState() {
  if (st.mode !== "paired") return "NOT_PAIRED";
  if (st.link !== "connected") return "NOT_CONNECTED";
  return "READY";
}
const chatAvailable = () => chatTabState() === "READY";
const questionFor = (pill) => (st.mode === "paired" && connected() ? st.questions[pill] || null : null);
const agentName = (pill) => (st.sessions.find((s) => s.pill === pill) || {}).agent || (PILLS[pill] || {}).name || "An agent";
const batteryHint = () => st.mode === "paired" && st.link !== "connected" && !st.batterySnoozed;
function focusOf(list) {
  return list.find((s) => s.pill === st.selected) || [...list].sort((a, b) => URGENCY.indexOf(a.state) - URGENCY.indexOf(b.state))[0] || null;
}
const hasDetails = (s) => !!(s.steps && s.steps.length) || !!s.finalLine || !!s.project || !!(s.files && s.files.length);

/* ---------- link/DemoLink.kt: the 8-step script, every 2.5 s ---------- */
const FINGERPRINT = "demo-fp-bash-npm-run-build";
function session(pill, agent, state, text, i, n) {
  const s = { pill, agent, state, text, i, n, color: PILLS[pill].color };
  if (st.mode === "paired") {
    // What "Show session details on the phone" adds; the steps grow with the script.
    const d = FAKE.details[pill];
    s.project = d.project;
    s.steps = d.steps.slice(0, Math.max(1, Math.min(d.steps.length, i)));
    if (pill === "agent_gemini") s.steps = [];
    s.files = i >= 2 ? d.files : [];
    if (state === "finished" && d.finalLine) s.finalLine = d.finalLine;
  }
  return s;
}
function step() {
  const c = st.cursor % 8;
  st.step = c;
  if (c === 0) st.sessions = [session("integration_claude", "Claude Code", "thinking", "Reading the project", 1, 6)];
  else if (c === 1) st.sessions = [session("integration_claude", "Claude Code", "working", "Editing files", 2, 6), session("agent_codex", "Codex", "searching", "Searching the code", 1, 4)];
  else if (c === 2) {
    if (!st.approvals.some((a) => a.fp === FINGERPRINT)) st.approvals.push({ pill: "integration_claude", fp: FINGERPRINT, tool: "Bash", command: "npm run build", truncated: false });
    st.sessions = [session("integration_claude", "Claude Code", "approval", "Waiting for your approval", 3, 6), session("agent_codex", "Codex", "working", "Running tests", 2, 4)];
    st.closedApproval = null;
  } else if (c === 3) {
    st.sessions = [session("integration_claude", "Claude Code", "working", "Building", 4, 6), session("agent_codex", "Codex", "question", "Which branch?", 3, 4)];
    if (st.mode === "paired") st.questions.agent_codex = { fp: "q-" + st.cursor, questions: FAKE.questions };
  } else if (c === 4) {
    st.sessions = [session("integration_claude", "Claude Code", "finished", "Done", 6, 6), session("agent_codex", "Codex", "error", "Tests failed", 3, 4)];
    delete st.questions.agent_codex;
    if (st.questionPill === "agent_codex") st.questionPill = null;
  } else if (c === 5) st.sessions = [session("agent_codex", "Codex", "ratelimit", "Rate limited", 3, 4)];
  else if (c === 6) st.sessions = [session("agent_gemini", "Gemini CLI", "sleeping", "Idle", 0, 0)];
  else { st.approvals = st.approvals.filter((a) => a.fp !== FINGERPRINT); st.sessions = []; }
  st.cursor++;
}
let timer = null;
function schedule() {
  clearInterval(timer);
  timer = null;
  if (st.playing && st.mode !== "none") timer = setInterval(() => { step(); changed(); }, 2500);
}
function setMode(mode, keepScreen) {
  st.mode = mode;
  st.cursor = 0; st.step = -1;
  st.sessions = []; st.approvals = []; st.questions = {};
  st.selected = null; st.closedApproval = null; st.questionPill = null; st.diff = null; st.pairRequest = null;
  st.link = "connected"; st.diag = { running: false, results: [] };
  if (mode !== "paired") st.chat = { ...st.chat, messages: [], busy: false, refusal: null };
  if (!keepScreen) st.screen = "HOME";
  if (mode !== "none") step();
  schedule();
  changed();
}
/** AppModel.decide: the decision is kept on the phone (never the command), the request leaves. */
function decide(fp, allow) {
  const r = st.approvals.find((a) => a.fp === fp);
  if (!r) { st.message = S.msg_not_pending; changed(); return; }
  st.approvals = st.approvals.filter((a) => a.fp !== fp);
  st.decisions.unshift({ agent: agentName(r.pill), tool: r.tool, allowed: allow, at: Date.now() });
  changed();
}

/* ---------- change notification ---------- */
const subs = [];
let pending = false;
function changed() {
  if (pending) return;
  pending = true;
  requestAnimationFrame(() => {
    pending = false;
    render();
    subs.forEach((fn) => { try { fn(st); } catch (e) { console.error(e); } });
  });
}
function go(screen) {
  if (!(screen in TAB_OF)) throw new Error("unknown screen " + screen);
  st.screen = screen;
  st.keyboard = false;
  changed();
}

const INPUT = {}; // data-input handlers, added by the screens
/** Every tappable thing carries data-act; the screens add their handlers here. */
const ACT = {
  tab: (a) => go(a),
  back: () => { const b = BACK[st.screen]; if (b) go(b); },
  closeMessage: () => { st.message = null; changed(); },
};
