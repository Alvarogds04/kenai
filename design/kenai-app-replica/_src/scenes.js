"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* T15 (S18-S19): three views beside the live phone, on the same page and in the same state.
   - Screens: a jump rail that puts the live phone on every screen, sheet and state (it drives window.kenai).
   - Over other apps: the island of ui/IslandOverlay.kt over a fake app, by Kind, with core/OverlayPolicy.kt's gating and tap guard.
   - Notifications: the heads-up, the shade and the lock screen of app/Notifications.kt, loud and quiet.
   Design only, no app code: it reads and drives the phone only through window.kenai, and every app text is S.* (strings.xml).
   The island's sizes and rules come from core/IslandGeometry.kt, core/IslandTimeline.kt and core/IslandPlan.kt; the colours are
   IslandSurface's (pure black, #F5F6F8, #9398A1, #2A2B2E) plus the app's PRIMARY for Allow. Android's shade and lock screen are
   stand-ins: no RemoteViews and no real system UI. */

const SC = window.kenai;
/** No motion: no transition, and the chronometer stays still (the page also kills CSS motion, app.css). */
const scnReduce = () => matchMedia("(prefers-reduced-motion: reduce)").matches;

/** core/IslandGeometry.kt at a 360 px screen and 1 dp = 1 px, and the timings of IslandTimeline / OverlayPolicy / Protocol. */
const SCN_ISL = {
  ear: 14, sbar: 28,
  workingW: 240, workingH: 52,
  cardW: Math.min(360 - 2 * 12 - 2 * 14, 340), cardH: 96, approvalH: 168,
  tapGuardMs: 600, holdMs: 10000, retractMs: 340, ttlS: 120,
  cornerSmall: 14, cornerLarge: 22,
};
const SCN_VIEWS = [["widgets", "Widgets"], ["screens", "Screens"], ["island", "Over other apps"], ["notif", "Notifications"]];
const SCN_KINDS = [["WORKING", "Working"], ["APPROVAL", "Approval"], ["QUESTION", "Question"], ["FINISHED", "Finished"], ["ERROR", "Error"], ["RATELIMIT", "Rate limit"]];
const SCN_STATUS = { finished: S.notif_finished, error: S.notif_error, question: S.notif_question, ratelimit: S.notif_ratelimit };
const SCN_NKINDS = [["auto", "Follow the live phone"], ["approval", "Approval"], ["finished", "Finished"], ["error", "Error"], ["question", "Question"], ["ratelimit", "Rate limit"], ["link", "Link only"]];

/** Everything these views own. The phone's state stays st (model.js); this is only what the page is showing. */
const scn = {
  view: "widgets", chip: null,
  follow: true, kind: "WORKING", denyOnly: false,
  onScreen: false, switchOff: false, noPermit: false,
  islKey: "", islShownAt: -1e9, guardHit: false, flashKey: "", retracted: false, flashTimer: null, guardTimer: null,
  alert: "heads", nkind: "auto", notifAt: 0, chronoTimer: null,
};

/* ---------- the toolbar: a View control, and a shorter subtitle so one row still fits ---------- */
function scnToolbar() {
  const sub = $("#tbar .brand span");
  if (sub) sub.textContent = "Today's app · demo loop, 2.5 s a step";
  const wrap = document.createElement("div");
  wrap.className = "ctl vctl";
  setHTML(wrap, '<span>View</span><div class="seg vseg" role="group" aria-label="View">' +
    SCN_VIEWS.map(([k, l]) => `<button type="button" data-view="${k}">${esc(l)}</button>`).join("") + "</div>");
  $("#tbar").appendChild(wrap);
  wrap.addEventListener("click", (e) => { const b = e.target.closest("[data-view]"); if (b) scnView(b.dataset.view); });
  // Any other toolbar change (mode, step, play, link) is not a chip any more.
  $("#tbar").addEventListener("click", (e) => { if (!e.target.closest("[data-view]")) scn.chip = null; });
  $("#tbar").addEventListener("change", () => { scn.chip = null; });
}

function scnView(v) {
  scn.view = v;
  $("#widgets").hidden = v !== "widgets";
  $("#scenes").hidden = v === "widgets";
  document.querySelectorAll("[data-view]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.view === v)));
  if (v === "island") scn.islKey = ""; // entering the view opens the island with its motion
  scnRender();
}

/* ---------- what a chip leaves behind: nothing ---------- */
function scnReset() {
  st.closedApproval = null; st.diff = null; st.questionPill = null; st.sys = null; st.detailPill = null;
  st.bio = null; st.pairRequest = null; st.paste = null; st.address = null; st.showCommand = null;
  st.keyboard = false; st.qflow = null; st.selected = null; st.message = null;
}

/** Jack into the demo loop and walk it to [target]; then hold, so the picture stays put. */
function scnStep(target) {
  scnReset();
  setMode("paired"); // runs step 0 (cursor 0 -> 1) and starts the loop
  let guard = 0;
  while (st.step !== target && guard++ < 9) step();
  st.playing = false;
  schedule();
  changed();
}

/* ---------- View 1: Screens ---------- */
const SCN_STEPS = [
  { step: 0, label: "Thinking", state: "thinking" },
  { step: 1, label: "Working ×2", state: "working" },
  { step: 2, label: "Approval", state: "approval" },
  { step: 3, label: "Question", state: "question" },
  { step: 4, label: "Finished + error", state: "finished" },
  { step: 5, label: "Rate limit", state: "ratelimit" },
  { step: 6, label: "Sleeping", state: "sleeping" },
  { step: 7, label: "No agents", state: null },
];
function scnStepChip(s) {
  const lead = dot(s.state ? stateColor(s.state) : "#6B7280");
  return scnChip("step" + s.step, `${lead}<b>${s.step}</b> ${esc(s.label)}`, STEP_TEXT[s.step], "scene:step", s.step);
}

function scnGroups() {
  return [
    { title: "Start", items: [
      scnChip("notpaired", dot(stateColor("sleeping")) + esc(S.pairing_headline), "setMode(\"none\")", "scene:none"),
      scnChip("scan", dot("#8AB4F8") + esc(S.scan_button), "ScanScreen", "scene:scan"),
    ] },
    { title: "Home · the demo loop", wide: true, items: SCN_STEPS.map(scnStepChip) },
    { title: "Agents", items: [
      scnChip("session", esc(S.session_details), "step 4 · SessionScreen", "scene:session"),
      scnChip("approval", `${esc(S.action_allow)} / ${esc(S.action_deny)}`, "step 2 · ApprovalSheet", "scene:approval"),
      scnChip("question", esc(S.question_see), "step 3 · QuestionSheet", "scene:question"),
      scnChip("diff", esc(S.files_heading), "step 4 · DiffSheet", "scene:diff"),
    ] },
    { title: "Chat", items: [scnChip("chat", esc(S.chat_title), "paired · step 1", "scene:chat")] },
    { title: "Settings", wide: true, items: [
      scnChip("go:SETTINGS", esc(S.tab_settings), "SettingsScreen", "scene:go", "SETTINGS"),
      scnChip("go:HISTORY", esc(S.history_title), "HistoryScreen", "scene:go", "HISTORY"),
      scnChip("go:GALLERY", esc(S.gallery), "GalleryScreen", "scene:go", "GALLERY"),
      scnChip("go:DIAGNOSTICS", esc(S.diag_title), "DiagnosticsScreen", "scene:go", "DIAGNOSTICS"),
      scnChip("go:LICENCES", esc(S.licences_title), "LicencesScreen", "scene:go", "LICENCES"),
      scnChip("overlay", esc(S.overlay_title), "Android's own page", "scene:overlay"),
    ] },
    { title: "Link states", items: [
      scnChip("lost", esc(S.status_not_connected), "paired · link lost", "scene:lost"),
      scnChip("notfound", esc(S.discovery_title), "paired · not found", "scene:notfound"),
    ] },
  ];
}

function scnScreens() {
  const stepNow = st.mode === "none" ? "The phone is not paired: it shows Pairing."
    : `${st.mode === "demo" ? "Demo mode" : "Paired"} · demo step ${st.step} of 0-7 · ${STEP_TEXT[st.step] || ""}`;
  const cards = scnGroups().map((g) => `<section class="scn-card${g.wide ? " wide" : ""}">
    <h3>${esc(g.title)}</h3><div class="scn-chips">${g.items.join("")}</div></section>`).join("");
  return `<div class="scn-head"><div><h2>Screens</h2><p class="scn-note">Every screen, sheet and state on the live phone at the left.
      A chip puts the phone there and leaves it consistent: no sheet, no dialog and no keyboard of the chip before.</p></div>
    <div class="scn-play"><span class="scn-k">Demo loop</span><div class="seg" role="group" aria-label="Demo loop">
      <button type="button" id="scn-play-on" data-scn="play:on" aria-pressed="${String(st.playing)}">Play</button>
      <button type="button" id="scn-play-off" data-scn="play:off" aria-pressed="${String(!st.playing)}">Hold</button></div></div></div>
    <div class="scn-now"><b>Now:</b> ${esc(stepNow)} <span class="scn-dim">· screen ${esc(st.screen)}</span></div>
    <div class="scn-grid">${cards}</div>`;
}

/* ---------- View 2: Over other apps ---------- */
const SCN_DEMO = {
  WORKING: { kind: "WORKING", pill: "integration_claude", agent: "Claude Code", state: "working", text: "Editing files" },
  APPROVAL: { kind: "APPROVAL", pill: "integration_claude", agent: "Claude Code", state: "approval", text: toolAction("Bash"), fp: FINGERPRINT },
  QUESTION: { kind: "QUESTION", pill: "agent_codex", agent: "Codex", state: "question", text: "Which branch?" },
  FINISHED: { kind: "FINISHED", pill: "integration_claude", agent: "Claude Code", state: "finished", text: "Done" },
  ERROR: { kind: "ERROR", pill: "agent_codex", agent: "Codex", state: "error", text: toolLabel("Bash · npm test") },
  RATELIMIT: { kind: "RATELIMIT", pill: "agent_codex", agent: "Codex", state: "ratelimit", text: "" },
};
const SCN_FLASH = ["FINISHED", "ERROR", "RATELIMIT"];

/** OverlayPolicy.blocker, in its own order and with its own words. */
function scnBlocker() {
  if (scn.switchOff) return "switch is off";
  if (scn.noPermit) return "'display over other apps' is not allowed";
  if (scn.onScreen) return "the app is on screen";
  return null;
}

function scnDemoSpec(kind) {
  const d = SCN_DEMO[kind] || SCN_DEMO.WORKING;
  const spec = { ...d };
  if (spec.kind === "APPROVAL") { spec.fp = spec.fp || FINGERPRINT; spec.canAllow = !scn.denyOnly; }
  spec.key = [spec.kind, spec.agent, spec.text, spec.fp || "", spec.canAllow === false ? "deny" : ""].join("|");
  return spec;
}

/** IslandPlan.active: a waiting request, then a question, then an agent at work. Nothing else, and nothing when blocked. */
function scnIslandSpec() {
  if (scnBlocker()) return null;
  if (!scn.follow) return scnDemoSpec(scn.kind);
  const a = approvals()[0];
  let spec;
  if (a) spec = { kind: "APPROVAL", pill: a.pill, agent: agentName(a.pill), state: "approval", text: toolAction(a.tool), fp: a.fp, canAllow: !a.truncated && !scn.denyOnly };
  else {
    const q = sessions().find((s) => s.state === "question");
    const w = q ? null : sessions().find((s) => BUSY.includes(s.state));
    const s = q || w;
    if (s) {
      const kind = q ? "QUESTION" : "WORKING";
      spec = { kind, pill: s.pill, agent: s.agent || agentName(s.pill), state: s.state, text: toolLabel(s.text) };
    }
  }
  if (!spec) return null;
  spec.key = [spec.kind, spec.agent, spec.text, spec.fp || "", spec.canAllow === false ? "deny" : ""].join("|");
  return spec;
}

/** The fake app underneath: a plain message list, invented, light so the black island reads. */
const SCN_MESSAGES = [
  ["Marta", "I left the keys under the plant.", "9:12"],
  ["Building manager", "The lift is fixed. Sorry for the wait.", "8:40"],
  ["Nextcloud", "3 files finished syncing.", "8:02"],
  ["Iván", "See you at 19:00?", "Yesterday"],
  ["Bank", "A purchase of 12,40 € was made with your card.", "Mon"],
];
function scnOtherApp(short) {
  const rows = (short ? SCN_MESSAGES.slice(0, 2) : SCN_MESSAGES).map(([n, t, w]) => `<div class="oth-row">
    <span class="oth-av">${esc(n[0])}</span><div class="oth-col"><b>${esc(n)}</b><span>${esc(t)}</span></div><span class="oth-when">${esc(w)}</span></div>`).join("");
  return `<div class="oth"><div class="oth-bar"><span class="oth-back">‹</span><b>Messages</b><span class="oth-me">A</span></div>
    <div class="oth-list">${rows}</div></div>`;
}
const scnClock = () => new Date().toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
const scnStatusBar = (lite) => `<div class="sbar${lite ? " lite" : ""}"><span>${esc(scnClock())}</span><span class="cam"></span><span class="r"><i></i><i class="w"></i></span></div>`;

function scnIslandInner(s) {
  if (s.kind === "WORKING") {
    return `<div class="isl-work" data-isl="open" role="button" tabindex="0" id="scn-isl-body" aria-label="${esc(S.app_name)}">${kodaEl(s.state, 34)}<div class="isl-col"><b>${esc(s.agent)}</b>${s.text ? `<span>${esc(s.text)}</span>` : ""}</div></div>`;
  }
  if (s.kind === "APPROVAL") {
    const allow = s.canAllow !== false;
    const lock = icon("LOCK", 18, "var(--onpri)");
    return `<div class="isl-req">
      <div class="isl-head" data-isl="open" role="button" tabindex="0" id="scn-isl-body" aria-label="${esc(S.app_name)}">${kodaEl("approval", 40)}<div class="isl-col">
        <span class="isl-agent">${esc(s.agent)}</span>
        <p class="isl-ask">${esc(fmt("approval_wants", s.text))}</p></div></div>
      <div class="isl-btns"><button type="button" class="isl-btn" id="scn-isl-deny" data-isl="deny">${esc(S.action_deny)}</button>
        ${allow ? `<button type="button" class="isl-btn pri" id="scn-isl-allow" data-isl="allow">${lock}<span>${esc(S.action_allow)}</span></button>` : ""}</div>
      <p class="isl-hint">${esc(allow ? S.approval_hint : S.approval_on_computer)}</p></div>`;
  }
  const line = s.kind === "QUESTION" ? S.island_answer_on_pc : s.text || "";
  return `<div class="isl-res" data-isl="open" role="button" tabindex="0" id="scn-isl-body" aria-label="${esc(S.app_name)}">${kodaEl(s.state, 52)}<div class="isl-col">
    <div class="isl-row"><b>${esc(s.agent)}</b><span>${esc(SCN_STATUS[s.kind.toLowerCase()] || "")}</span></div>
    ${line ? `<p>${esc(line)}</p>` : ""}</div></div>`;
}

function scnIslandEl(spec, fresh) {
  if (!spec) return "";
  const working = spec.kind === "WORKING";
  const w = working ? SCN_ISL.workingW : SCN_ISL.cardW;
  const h = SCN_ISL.sbar + (working ? SCN_ISL.workingH : spec.kind === "APPROVAL" ? SCN_ISL.approvalH : SCN_ISL.cardH);
  const corner = working ? SCN_ISL.cornerSmall : SCN_ISL.cornerLarge;
  const style = `--iw:${w}px;--ih:${h}px;--ic:${corner}px`;
  const cls = fresh ? "" : " in";
  const about = spec.kind === "APPROVAL" ? fmt("approval_wants", spec.text)
    : spec.kind === "WORKING" ? stateLabel(spec.state)
      : `${SCN_STATUS[spec.kind.toLowerCase()] || ""}${spec.text ? " · " + spec.text : ""}`;
  return `<div class="isl${cls}" id="scn-isl" data-kind="${spec.kind}" style="${style}" role="region" aria-label="${esc(spec.agent + ": " + about)}">
    <span class="isl-ear l"></span><span class="isl-ear r"></span>
    <div class="isl-in">${scnIslandInner(spec)}</div></div>`;
}

function scnToggle(key, on, label, hint) {
  return `<div class="scn-tg"><button type="button" id="scn-sw-${esc(key)}" role="switch" class="sw ${on ? "on" : ""}" aria-checked="${on ? "true" : "false"}" aria-label="${esc(label)}" data-scn="switch:${esc(key)}"><i></i></button>
    <div class="scn-tgt"><b>${esc(label)}</b>${hint ? `<span>${esc(hint)}</span>` : ""}</div></div>`;
}

function scnIsland() {
  const spec = scn.retracted ? null : scnIslandSpec();
  const fresh = !!spec && spec.key !== scn.islKey;
  scn.islKey = spec ? spec.key : "";
  if (fresh) { scn.islShownAt = performance.now(); scn.retracted = false; }
  const blocker = scnBlocker();
  const guardArmed = performance.now() - scn.islShownAt < SCN_ISL.tapGuardMs;
  const kinds = SCN_KINDS.map(([k, l]) => `<button type="button" id="scn-kind-${k}" class="scn-chip sm" data-scn="kind:${k}" aria-pressed="${String(!scn.follow && scn.kind === k)}"><span class="scn-cl">${esc(l)}</span></button>`).join("");
  return `<div class="scn-head tight"><h2>Over other apps</h2><p class="scn-note">The island of <code>IslandOverlay.kt</code> over a fake app: <code>IslandGeometry.kt</code>'s box, <code>IslandPlan.active</code>'s pick, <code>IslandTimeline</code>'s hold.</p></div>
    <div class="scn-row">
      <div class="scn-frame">
        <div class="phone scn-phone" id="scn-isl-phone">${scnStatusBar(true)}${scnOtherApp(false)}
          ${blocker ? `<div class="isl-blocked"><span class="scn-k">OverlayPolicy.blocker</span><code>${esc(blocker)}</code></div>` : scnIslandEl(spec, fresh)}
          <div class="gbar"><button type="button" tabindex="-1" aria-hidden="true"></button></div></div>
        <p class="scn-cap">Messages · invented · the island is black over the camera cutout</p>
      </div>
      <div class="scn-side">
        <section class="scn-card"><h3>What the island shows</h3>
          ${scnToggle("follow", scn.follow, "Follow the live phone", scn.follow ? "Now: " + scnPickText() : "Picks like IslandPlan.active: a request, then a question, then an agent at work")}
          <p class="scn-note">Or pick a Kind by hand:</p>
          <div class="scn-chips">${kinds}</div>
          <p class="scn-note"><code>ToolLabels.label</code> keeps only a tool's name: the demo's prose is dropped from the working strip, as in the app.</p>
          ${scnToggle("denyOnly", scn.denyOnly, "Deny only", "A request whose command cannot be shown in full: the card offers Deny only and says to answer on the computer")}
        </section>
        <section class="scn-card"><h3>May it show? (OverlayPolicy)</h3>
          ${scnToggle("switchOff", scn.switchOff, "The switch is off", "Show Kenai over other apps")}
          ${scnToggle("noPermit", scn.noPermit, "Permission missing", "“display over other apps” is not allowed")}
          ${scnToggle("onScreen", scn.onScreen, "Kenai is on screen", "Inside Kenai the screen already shows it")}
          <p class="scn-note">${blocker ? `Hidden. The log gets <code>${esc(blocker)}</code>.` : "Allowed: nothing blocks it, so the island may exist."}</p>
        </section>
        <section class="scn-card"><h3>Tap guard · 600 ms</h3>
          <p class="scn-note">A tap in the first 600 ms is ignored: it is most likely meant for the app underneath, or it is an accident
            (<code>OverlayPolicy.tapAccepted</code>).</p>
          <div class="scn-badge${guardArmed || scn.guardHit ? " hit" : ""}" aria-live="polite">tap guard 600 ms${scn.guardHit ? " · tap ignored" : ""}</div>
          <p class="scn-note">Then: <b>${esc(S.action_deny)}</b> calls <code>decide(fp,false)</code> on the live phone. <b>${esc(S.action_allow)}</b>
            opens the live approval, because Allow always takes the fingerprint. A tap on the body opens Home.</p>
        </section>
        <section class="scn-card"><h3>Timing</h3>
          <p class="scn-note">Working, a question and a request are persistent: they stay until something changes.
            A finished, an error and a rate limit are held <b>10 s</b> and then retract in <b>340 ms</b> (the PC's curve).</p>
          <p class="scn-note">While the pill shows an approval, the notification is quiet: it goes to the shade only, never on top of the pill.</p>
          ${scn.retracted ? `<button type="button" class="cb" id="scn-again" data-scn="island:again">Show it again</button>` : ""}
        </section>
      </div>
    </div>`;
}

/** The line under the Follow switch: what the pill is about right now. */
function scnPickText() {
  const s = scnIslandSpec();
  if (!s) return "nothing to show";
  return `${s.agent} · ${s.kind.toLowerCase()}`;
}

/* ---------- View 3: Notifications ---------- */
function scnChronoText() {
  if (scnReduce() || !scn.notifAt) return "· 2:00";
  const left = Math.max(0, SCN_ISL.ttlS - Math.floor((Date.now() - scn.notifAt) / 1000));
  return "· " + Math.floor(left / 60) + ":" + String(left % 60).padStart(2, "0");
}
const scnN = (inner, o = {}) => `<article class="shd-n${o.pub ? " pub" : ""}${o.ongoing ? " ongoing" : ""}">
  <div class="shd-h"><span class="shd-ic">${launcherIcon(14)}</span><span class="shd-app">${esc(o.app || S.app_name)}</span>
    ${o.chrono ? `<span class="shd-chrono" id="scn-chrono">${esc(scnChronoText())}</span>` : ""}
    ${o.channel ? `<span class="shd-ch">${esc(o.channel)}</span>` : ""}</div>
  ${inner}</article>`;

function scnApprovalNotice() {
  const r = approvals()[0];
  const agent = r ? agentName(r.pill) : SCN_DEMO.APPROVAL.agent;
  const tool = r ? r.tool : "Bash";
  return { fp: r ? r.fp : null, agent, line: fmt("approval_wants", toolAction(tool)) };
}
function scnStatusList() {
  if (scn.nkind === "auto") return sessions().filter((s) => SCN_STATUS[s.state]).slice(0, 3);
  if (!SCN_STATUS[scn.nkind]) return [];
  const live = sessions().find((s) => s.state === scn.nkind);
  const d = SCN_DEMO[scn.nkind.toUpperCase()];
  return [live || { pill: d.pill, agent: d.agent, state: scn.nkind, text: d.text }];
}
function scnNotifModel() {
  const wantApproval = scn.nkind === "auto" || scn.nkind === "approval";
  const approval = wantApproval ? scnApprovalNotice() : null;
  const statuses = scn.nkind === "link" ? [] : scnStatusList();
  return { approval, statuses };
}

function scnNotifChannel() {
  return scn.alert === "quiet" ? "approvals_quiet · LOW" : "approvals_v3 · HIGH";
}

function scnShadeCard(m) {
  if (!m) return "";
  return scnN(`<p class="shd-t">${esc(`${m.agent} needs your approval`)}</p>
    <p class="shd-x">${esc(m.line)}</p>
    <div class="shd-a"><button type="button" id="scn-ndeny" data-scn="notif:deny">${esc(S.action_deny)}</button>
      <button type="button" id="scn-nreview" data-scn="notif:review">${esc(S.action_review)}</button></div>`,
  { chrono: true, channel: scnNotifChannel() });
}
function scnStatusCard(s) {
  const what = SCN_STATUS[s.state] || S.notif_finished;
  const sub = toolLabel(s.text || "");
  return scnN(`<p class="shd-t">${esc(s.agent)}</p><p class="shd-x">${esc(what)}</p>${sub ? `<p class="shd-s">${esc(sub)}</p>` : ""}`,
    { channel: "updates · LOW" });
}
function scnLinkCard(expanded) {
  const headline = st.mode === "none" ? S.status_not_connected
    : st.mode === "demo" ? S.status_connected
      : st.link === "connected" ? `${S.status_connected} · ${DESKTOP.name}` : st.link === "connecting" ? S.status_connecting : S.status_not_connected;
  const detail = st.mode === "none" ? "" : `${DESKTOP.name} · ${DESKTOP.host}`;
  const lines = (expanded ? sessions() : []).map((s) => `${s.agent || agentName(s.pill)}: ${stateLabel(s.state)}`);
  return scnN(`<p class="shd-t">${esc(headline)}</p>${detail ? `<p class="shd-x">${esc(detail)}</p>` : ""}
    ${lines.length ? `<p class="shd-lines">${lines.map((l) => esc(l)).join("<br>")}</p>` : ""}`,
  { channel: "link · LOW · ongoing" });
}

function scnNotif() {
  const m = scnNotifModel();
  if (m.approval && !scn.notifAt) scn.notifAt = Date.now();
  if (!m.approval) scn.notifAt = 0;
  const shade = [m.approval ? scnShadeCard(m.approval) : "", ...m.statuses.map(scnStatusCard),
    scnLinkCard(!m.approval), // while a request waits the link notification says the link only
  ].filter(Boolean).join("");
  const headsUp = m.approval && scn.alert === "heads" ? `<div class="shd-hu" role="status">
    <div class="shd-h"><span class="shd-ic">${launcherIcon(14)}</span><span class="shd-app">${esc(S.app_name)}</span><span class="shd-chrono" id="scn-chrono-hu">${esc(scnChronoText())}</span></div>
    <p class="shd-t">${esc(`${m.approval.agent} needs your approval`)}</p><p class="shd-x">${esc(m.approval.line)}</p>
    <div class="shd-a"><button type="button" data-scn="notif:deny">${esc(S.action_deny)}</button><button type="button" data-scn="notif:review">${esc(S.action_review)}</button></div></div>` : "";
  const lockApproval = m.approval ? scnN(`<p class="shd-t">${esc(S.approval_title)}</p><p class="shd-x">${esc(S.notif_unlock)}</p>`, { pub: true }) : "";
  const lockStatus = m.statuses.map((s) => scnN(`<p class="shd-t">${esc(S.app_name)}</p><p class="shd-x">${esc(SCN_STATUS[s.state] || S.notif_finished)}</p>`, { pub: true })).join("");
  const lockLink = scnN(`<p class="shd-t">${esc(S.app_name)}</p><p class="shd-x">${esc(connected() ? S.status_connected : S.status_not_connected)}</p>`, { pub: true });
  const kinds = SCN_NKINDS.map(([k, l]) => `<button type="button" id="scn-nkind-${k}" class="scn-chip sm" data-scn="nkind:${k}" aria-pressed="${String(scn.nkind === k)}"><span class="scn-cl">${esc(l)}</span></button>`).join("");
  return `<div class="scn-head tight"><h2>Notifications</h2><p class="scn-note"><code>Notifications.kt</code>: one loud approval, quiet link and states, each kind in its own group. Android's shade and lock screen are stand-ins.</p></div>
    <div class="scn-row">
      <div class="scn-frame">
        <div class="phone scn-phone" id="scn-shade-phone">${scnStatusBar(true)}
          <div class="nt-app">${scnOtherApp(true)}</div>
          <div class="shd"><div class="shd-grab"></div>
            <div class="shd-q"><span>${esc(scnClock())}</span><span class="shd-qr">${icon("SCAN", 14, "#CBD0D8")}<i></i><i class="b"></i></span></div>
            <div class="shd-list">${shade}</div></div>
          ${headsUp}<div class="gbar"><button type="button" tabindex="-1" aria-hidden="true"></button></div></div>
        <p class="scn-cap">${scn.alert === "heads" ? "Heads-up over the app, and the shade below · loud" : "Quiet: no heads-up, the shade only · the pill is the alert"}</p>
      </div>
      <div class="scn-frame">
        <div class="phone scn-phone scn-lock" id="scn-lock-phone">${scnStatusBar(false)}
          <div class="lk-clock">${esc(scnClock())}</div><div class="lk-date">Monday, 17 November</div>
          <div class="lk-lock">${icon("LOCK", 18, "#CBD0D8")}</div>
          <div class="shd-list lk">${lockApproval}${lockStatus}${lockLink}</div>
          <div class="gbar"><button type="button" tabindex="-1" aria-hidden="true"></button></div></div>
        <p class="scn-cap">Lock screen · the PUBLIC versions (VISIBILITY_PRIVATE)</p>
      </div>
      <div class="scn-side narrow">
        <section class="scn-card"><h3>Approval alert</h3>
          <div class="scn-chips">
            <button type="button" id="scn-alert-heads" class="scn-chip sm" data-scn="alert:heads" aria-pressed="${String(scn.alert === "heads")}"><span class="scn-cl">Heads-up (no pill)</span></button>
            <button type="button" id="scn-alert-quiet" class="scn-chip sm" data-scn="alert:quiet" aria-pressed="${String(scn.alert === "quiet")}"><span class="scn-cl">Quiet (pill is showing)</span></button></div>
          <p class="scn-note"><code>approvals_v3</code> · HIGH · vibration <code>0,500,150,500</code>: heads-up and sound.</p>
          <p class="scn-note"><code>approvals_quiet</code> · LOW · no heads-up, no sound: the pill is the alert, the shade keeps the record.</p>
        </section>
        <section class="scn-card"><h3>Notification kinds</h3><div class="scn-chips">${kinds}</div>
          <p class="scn-note">${scn.nkind === "auto" ? "Following the live phone." : `Overridden: ${esc((SCN_NKINDS.find((k) => k[0] === scn.nkind) || [])[1] || "")}.`}</p>
          <p class="scn-note">Each kind has its own group (<code>kenai.approvals</code>, <code>kenai.link</code>, <code>kenai.status</code>) and no summary, so no «2» bundle.</p>
          <p class="scn-note">No Allow here: it always takes the app and the fingerprint. The link one is ongoing and not dismissible, and while a request waits it shows the link state only.</p>
        </section>
      </div>
    </div>`;
}

/* ---------- render ---------- */
function scnRender() {
  const root = $("#scenes");
  if (!root || root.hidden) { scnChronoStop(); scnFlashStop(); return; }
  const a = document.activeElement;
  const keep = a && a.id && root.contains(a) ? a.id : null;
  setHTML(root, scn.view === "screens" ? scnScreens() : scn.view === "island" ? scnIsland() : scn.view === "notif" ? scnNotif() : "");
  paintKoda(root);
  if (keep) { const el = document.getElementById(keep); if (el) el.focus(); }
  scnAfter();
}

function scnAfter() {
  if (scn.view === "island") {
    const el = $("#scn-isl");
    if (el && !el.classList.contains("in")) {
      requestAnimationFrame(() => { el.classList.add("in"); }); // slides down from the top edge
      clearTimeout(scn.guardTimer);
      scn.guardTimer = setTimeout(() => { scn.guardHit = false; changed(); }, SCN_ISL.tapGuardMs + 40);
    }
    scnFlashWatch(scn.retracted ? null : scnIslandSpec());
    scnChronoStop();
  } else if (scn.view === "notif") {
    clearTimeout(scn.guardTimer); scn.guardTimer = null;
    scnFlashStop();
    if (scn.notifAt && !scnReduce()) {
      if (!scn.chronoTimer) scn.chronoTimer = setInterval(scnChronoTick, 1000);
    } else scnChronoStop();
  } else { scnChronoStop(); scnFlashStop(); }
}

function scnChronoStop() {
  clearInterval(scn.chronoTimer); scn.chronoTimer = null;
}
function scnFlashStop() {
  clearTimeout(scn.flashTimer); scn.flashTimer = null;
  scn.flashKey = "";
}
function scnChronoTick() {
  const t = scnChronoText();
  const a = $("#scn-chrono"), b = $("#scn-chrono-hu");
  if (!a && !b) { scnChronoStop(); return; }
  if (a) a.textContent = t;
  if (b) b.textContent = t;
}

/** IslandTimeline: a finished, an error and a rate limit are held 10 s, then the island curves back up in 340 ms. */
function scnFlashWatch(spec) {
  if (!spec || !SCN_FLASH.includes(spec.kind) || spec.key === scn.flashKey) return;
  scn.flashKey = spec.key;
  scn.retracted = false;
  clearTimeout(scn.flashTimer);
  scn.flashTimer = setTimeout(() => {
    const el = $("#scn-isl");
    if (el) { el.classList.remove("in"); el.classList.add("out"); }
    setTimeout(() => { scn.retracted = true; changed(); }, SCN_ISL.retractMs + 30);
  }, SCN_ISL.holdMs);
}

/* ---------- chips, switches and taps ---------- */
function scnChip(key, labelHtml, hint, act, arg) {
  return `<button type="button" id="scn-chip-${esc(key)}" class="scn-chip" data-scn="${esc(act)}"${arg === undefined || arg === null ? "" : ` data-arg="${esc(arg)}"`} data-chip="${esc(key)}" aria-pressed="${String(scn.chip === key)}">
    <span class="scn-cl">${labelHtml}</span>${hint ? `<span class="scn-h">${esc(hint)}</span>` : ""}</button>`;
}
/** A tap the guard lets through ("now" is what OverlayPolicy.tapAccepted would say). */
function scnTapOk() {
  if (performance.now() - scn.islShownAt >= SCN_ISL.tapGuardMs) return true;
  scn.guardHit = true;
  clearTimeout(scn.guardTimer);
  scn.guardTimer = setTimeout(() => { scn.guardHit = false; changed(); }, 700);
  changed();
  return false;
}
/** The demo's Deny/Action needs a live request: make the demo's own when the phone has none. */
function scnEnsureApproval() {
  if (approvals().length) return approvals()[0].fp;
  scnStep(2);
  return (approvals()[0] || {}).fp || null;
}

const SCN_ACTIONS = {
  "play:on": () => { if (st.mode === "none") setMode("paired"); else SC.play(true); },
  "play:off": () => { if (st.mode === "none") setMode("paired"); st.playing = false; schedule(); changed(); },

  "scene:none": () => setMode("none"),
  "scene:scan": () => go("SCAN"),
  "scene:step": (el) => scnStep(+el.dataset.arg),
  "scene:session": () => { scnStep(4); st.detailPill = "integration_claude"; go("SESSION"); },
  "scene:approval": () => { scnStep(2); SC.openApproval(); },
  "scene:question": () => { scnStep(3); SC.openQuestion("agent_codex"); },
  "scene:diff": () => { scnStep(4); ACT.openDiff("app/build.gradle.kts"); },
  "scene:chat": () => { scnStep(1); go("CHAT"); },
  "scene:go": (el) => go(el.dataset.arg),
  "scene:overlay": () => { go("SETTINGS"); st.sys = "overlay"; changed(); },
  "scene:lost": () => { scnReset(); setMode("paired"); st.link = "lost"; st.batterySnoozed = false; st.diag = { running: false, results: [] }; changed(); },
  "scene:notfound": () => { scnReset(); setMode("paired"); st.link = "notfound"; st.batterySnoozed = false; st.diag = { running: false, results: [] }; changed(); },

  "switch:follow": () => { scn.follow = !scn.follow; scn.islKey = ""; changed(); },
  "switch:denyOnly": () => { scn.denyOnly = !scn.denyOnly; scn.islKey = ""; changed(); },
  "switch:onScreen": () => { scn.onScreen = !scn.onScreen; changed(); },
  "switch:switchOff": () => { scn.switchOff = !scn.switchOff; changed(); },
  "switch:noPermit": () => { scn.noPermit = !scn.noPermit; changed(); },
  "island:again": () => { scn.retracted = false; scn.flashKey = ""; scn.islKey = ""; changed(); },

  "alert:heads": () => { scn.alert = "heads"; changed(); },
  "alert:quiet": () => { scn.alert = "quiet"; changed(); },
  "notif:deny": () => { const fp = scnEnsureApproval(); if (fp) decide(fp, false); },
  "notif:review": () => { go("HOME"); SC.openApproval(); },
};

function scnBinder() {
  const root = $("#scenes");
  root.addEventListener("click", (e) => {
    const tap = e.target.closest("[data-isl]");
    if (tap && root.contains(tap)) {
      if (!scnTapOk()) return;
      const what = tap.dataset.isl;
      if (what === "deny") { const fp = scnEnsureApproval(); if (fp) decide(fp, false); }
      else if (what === "allow") { scnEnsureApproval(); SC.openApproval(); }
      else go("HOME");
      return;
    }
    const b = e.target.closest("[data-scn]");
    if (!b || b.disabled || !root.contains(b)) return;
    const key = b.dataset.scn;
    if (key.startsWith("kind:")) { scn.follow = false; scn.kind = key.slice(5); scn.retracted = false; scn.flashKey = ""; scn.islKey = ""; changed(); return; }
    if (key.startsWith("nkind:")) { scn.nkind = key.slice(6); scn.notifAt = 0; changed(); return; }
    const fn = SCN_ACTIONS[key];
    if (!fn) return;
    if (b.dataset.chip) scn.chip = b.dataset.chip;
    fn(b);
  });
  root.addEventListener("keydown", (e) => {
    const tap = e.target.closest("[data-isl='open']");
    if (tap && (e.key === "Enter" || e.key === " ")) { e.preventDefault(); if (scnTapOk()) go("HOME"); }
  });
}

/* ---------- init ---------- */
scnToolbar();
scnBinder();
scnView("widgets");
SC.subscribe(() => scnRender());
