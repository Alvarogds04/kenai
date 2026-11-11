"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* ui/Sheets.kt (BottomSheetHost, ApprovalSheet, QuestionSheet), ui/DiffSheet.kt, PasteSheet (ui/PairingScreen.kt),
   AddressSheet (ui/DiscoveryHint.kt), ui/PairConfirm.kt, and the Android surfaces: BiometricPrompt, permission dialogs. */

const GUARD_MS = 600; // ApprovalSheetPlan.GUARD_MS
let armedAt = {};
function armed(key) {
  if (!(key in armedAt)) { armedAt[key] = Date.now(); setTimeout(changed, GUARD_MS + 20); }
  return Date.now() - armedAt[key] >= GUARD_MS;
}
const sheet = (key, inner, wash) => ({
  key,
  html: `<div class="host"><div class="scrim" data-act="sheetDismiss" data-arg="${esc(key)}"></div><div class="sheet" role="dialog" aria-modal="true">${wash ? `<div class="wash" style="--wash:${wash};opacity:.45"></div>` : ""}<div class="grab"></div>${inner}</div></div>`,
});
ACT.sheetDismiss = () => ACT.sysBack();

/** ApprovalSheetPlan.next: the first request the user has not closed; never over Scan. */
const pendingApproval = () => (st.screen === "SCAN" ? null : approvals().find((a) => a.fp !== st.closedApproval) || null);

function approvalSheet(r) {
  const name = agentName(r.pill);
  const kind = (PILLS[r.pill] || {}).name || "";
  const on = armed("a:" + r.fp);
  const cmd = st.showCommand === r.fp;
  const inner = `<div class="shead">${clay("approval", 84, (PILLS[r.pill] || {}).color)}<div><p class="t-sec dim ell">${esc(kind && kind !== name ? name + " · " + kind : name)}</p><p class="t-title white clamp3">${esc(fmt("approval_wants", toolAction(r.tool)))}</p></div></div>
  <p class="t-sec dim" style="padding-top:12px">${esc(S.approval_caution)}</p>
  <button type="button" class="t-sec dim u" style="min-height:48px;padding:12px 0" data-act="toggleCmd" data-arg="${esc(r.fp)}">${esc(cmd ? S.approval_hide_cmd : S.approval_show_cmd)}</button>
  ${cmd ? `<div class="cmd"><span class="t-label dim">${esc(r.tool)}</span><span class="t-mono white">${esc(r.command)}</span></div><div style="height:8px"></div>` : ""}
  <div class="row2 g10" style="padding-top:4px">${pill(S.action_deny, "deny", { dark: true, disabled: !on, arg: r.fp })}${pill(S.action_allow, "allow", { kind: "pri", dark: true, disabled: !on, arg: r.fp, icon: "LOCK", iconSize: 18 })}</div>
  <p class="t-label dim" style="padding-top:10px">${esc(S.approval_hint)}</p>
  <button type="button" class="later" data-act="notNow">${esc(S.approval_later)}</button>`;
  return sheet("approval:" + r.fp, inner, STATE_COLOR.approval);
}
ACT.toggleCmd = (fp) => { st.showCommand = st.showCommand === fp ? null : fp; changed(); };
ACT.deny = (fp) => decide(fp, false);
ACT.allow = (fp) => {
  const r = st.approvals.find((a) => a.fp === fp);
  if (!r || st.bio) return;
  st.bio = { title: S.action_allow, sub: `${r.tool}: ${r.command}`, onOk: () => decide(fp, true) };
  changed();
};
ACT.notNow = () => {
  if (st.questionPill) st.questionPill = null;
  else { const p = pendingApproval(); if (p) st.closedApproval = p.fp; }
  changed();
};

/* ---------- QuestionSheet + core/QuestionFlow.kt ---------- */
function questionSheet(pillId) {
  const wash = STATE_COLOR.question;
  const colour = (PILLS[pillId] || {}).color;
  const req = questionFor(pillId);
  if (!req) {
    return sheet("q0:" + pillId, `<div class="shead">${clay("question", 84, colour)}<p class="t-title white clamp3" style="flex:1">${esc(fmt("question_title", agentName(pillId)))}</p></div>
      <p class="t-body dim" style="padding:12px 0 16px">${esc(S.question_body)}</p>${pill_(S.question_ok, "notNow")}`, wash);
  }
  if (!st.qflow || st.qflow.fp !== req.fp) st.qflow = { fp: req.fp, index: 0, picks: req.questions.map(() => []) };
  const f = st.qflow;
  const q = req.questions[f.index];
  const on = armed("q:" + req.fp);
  const isLast = f.index === req.questions.length - 1;
  const complete = f.picks.every((p) => p.length > 0);
  const opts = q.options.map((o) => {
    const sel = f.picks[f.index].includes(o.label);
    return `<button type="button" class="opt ${q.multiSelect ? "multi" : ""}" role="${q.multiSelect ? "checkbox" : "radio"}" aria-checked="${sel}" data-act="qPick" data-arg="${esc(o.label)}"><span class="mk">${sel ? icon("CHECK", 16, "#0B0C0E") : ""}</span><div><b class="t-body" style="font-weight:600">${esc(o.label)}</b>${o.description ? `<span class="t-sec dim">${esc(o.description)}</span>` : ""}</div></button>`;
  }).join("");
  const nav = [];
  if (f.index > 0) nav.push(pill(S.question_back, "qBack", { dark: true }));
  if (isLast) nav.push(pill(S.question_send, "qSend", { kind: "pri", dark: true, disabled: !(on && complete), icon: "LOCK", iconSize: 18 }));
  else nav.push(pill(S.question_next, "qNext", { kind: "pri", dark: true, disabled: f.picks[f.index].length === 0 }));
  const position = req.questions.length > 1 ? `<p class="t-label dim">${esc(fmt("question_position", f.index + 1, req.questions.length))}</p>` : "";
  const inner = `<div class="shead">${clay("question", 84, colour)}<div><p class="t-sec dim clamp2">${esc(fmt("question_title", agentName(pillId)))}</p>${position}</div></div>
  <p class="t-head white" style="padding-top:12px">${esc(q.question)}</p>
  ${q.multiSelect ? `<p class="t-sec dim" style="padding-top:2px">${esc(S.question_pick_many)}</p>` : ""}
  <div style="display:flex;flex-direction:column;gap:8px;padding-top:12px">${opts}</div>
  <div class="row2 g10" style="padding-top:16px">${nav.join("")}</div>
  <p class="t-label dim" style="padding-top:10px">${esc(S.approval_hint)}</p>
  <button type="button" class="later" data-act="notNow">${esc(S.approval_later)}</button>`;
  return sheet("q:" + req.fp, inner, wash);
}
const pill_ = (label, act) => pill(label, act, { kind: "pri", dark: true, w: true });
ACT.openQuestion = (p) => { st.questionPill = p; changed(); };
ACT.qPick = (label) => {
  const req = questionFor(st.questionPill);
  const q = req.questions[st.qflow.index];
  const cur = st.qflow.picks[st.qflow.index];
  st.qflow.picks[st.qflow.index] = q.multiSelect ? (cur.includes(label) ? cur.filter((x) => x !== label) : [...cur, label]) : [label];
  changed();
};
ACT.qNext = () => { st.qflow.index++; changed(); };
ACT.qBack = () => { st.qflow.index--; changed(); };
ACT.qSend = () => {
  const pillId = st.questionPill;
  const picks = st.qflow.picks;
  st.bio = { title: S.question_confirm_title, sub: picks.map((p) => p.join(", ")).join("; "), onOk: () => {
    st.questionPill = null; delete st.questions[pillId]; st.qflow = null; st.message = S.question_sent;
    const s = st.sessions.find((x) => x.pill === pillId);
    if (s) { s.state = "working"; s.text = "Bash · git checkout"; }
  } };
  changed();
};

/* ---------- DiffSheet (≤ 200 lines, + and − besides the colour) ---------- */
function diffSheet() {
  const d = st.diff;
  const rows = d.rows.slice(0, 200).map((l) => {
    const k = l[0];
    const col = k === "+" ? "#34D399" : k === "-" ? "#FF8D97" : k === "@" ? "#9398A1" : "#F5F6F8";
    const bg = k === "+" ? "rgba(52,211,153,.12)" : k === "-" ? "rgba(255,141,151,.12)" : "transparent";
    const mark = k === "+" ? "+" : k === "-" ? "−" : k === "@" ? "" : " ";
    return `<div class="drow" style="color:${col};background:${bg}"><b>${mark}</b><span>${esc(k === "@" ? l : l.slice(1))}</span></div>`;
  }).join("");
  const added = d.rows.filter((l) => l[0] === "+").length, removed = d.rows.filter((l) => l[0] === "-").length;
  return sheet("diff:" + d.name, `<p class="t-title white clamp2">${esc(d.name)}</p>
    <p class="t-sec dim" style="padding:2px 0 10px">${esc(fmt("file_summary", added, removed))}</p>
    <div class="dlist">${rows}</div>${d.rows.length > 200 ? `<p class="t-sec dim" style="padding-top:8px">${esc(S.file_truncated)}</p>` : ""}
    <div style="padding-top:12px">${pill(S.file_close, "closeDiff", { dark: true, w: true })}</div>`);
}
ACT.openDiff = (name) => {
  const f = Object.values(FAKE.details).flatMap((d) => d.files).find((x) => x.name === name);
  st.diff = { name, rows: f && f.isNew ? ["@@ -0,0 +1,12 @@", ...Array.from({ length: 12 }, (_, i) => "+" + ["# Release 0.1", "", "- Light theme", "- Spanish", "- Vibration", "- Tile actions", "", "Build with", "", "    ./gradlew assembleRelease", "", "Then sign the APK."][i])] : FAKE.diff };
  changed();
};
ACT.closeDiff = () => { st.diff = null; changed(); };

/* ---------- PasteSheet and AddressSheet ---------- */
/** link/Pairing.kt PairingPayload.parse */
function parsePairing(text) {
  let u;
  try { u = new URL(text.trim()); } catch (_) { return null; }
  if (u.protocol !== "abito:" || (u.host || u.pathname.replace(/^\/\//, "")) !== "pair") return null;
  const q = u.searchParams;
  if (q.get("v") !== "1") return null;
  const host = q.get("host"), port = +q.get("port"), fp = (q.get("fp") || "").toLowerCase(), token = q.get("token") || "";
  if (!host || host.length > 253 || !(port >= 1 && port <= 65535)) return null;
  if (!/^[0-9a-f]{64}$/.test(fp) || !/^[A-Za-z0-9_-]{16,128}$/.test(token)) return null;
  return { host, port, name: (q.get("name") || "").slice(0, 64) };
}
function fieldSheet(key, title, hint, placeholder, value, bad, badText, buttons) {
  return sheet(key, `<p class="t-title white">${esc(title)}</p><p class="t-sec dim" style="padding:4px 0 12px">${esc(hint)}</p>
    <input id="f-${key}" class="field" type="url" autocomplete="off" spellcheck="false" placeholder="${esc(placeholder)}" value="${esc(value)}" data-input="${key}">
    ${bad ? `<p class="t-sec dang" style="padding-top:8px">${esc(badText)}</p>` : ""}${buttons}`);
}
const pasteSheet = () => fieldSheet("paste", S.paste_title, S.paste_hint, "abito://pair?…", st.paste.text, st.paste.bad, S.msg_bad_link,
  `<div class="row2 g10" style="padding-top:16px">${pill(S.pair_clipboard, "clipboard", { dark: true })}${pill(S.pair_button, "pastePair", { kind: "pri", dark: true, disabled: !st.paste.text.trim() })}</div>`);
const addressSheet = () => fieldSheet("address", S.address_title, S.address_hint, "192.168.1.20:47821", st.address.text, st.address.bad, S.address_bad,
  `<div style="padding-top:16px">${pill(S.address_save, "saveAddress", { kind: "pri", dark: true, w: true, disabled: !st.address.text.trim() })}</div>`);
INPUT.paste = (v) => { const was = !!st.paste.text.trim(); st.paste.text = v; const bad = st.paste.bad; st.paste.bad = false; if (bad || was !== !!v.trim()) changed(); };
INPUT.address = (v) => { const was = !!st.address.text.trim(); st.address.text = v; const bad = st.address.bad; st.address.bad = false; if (bad || was !== !!v.trim()) changed(); };
ACT.openPaste = () => { st.paste = { text: "", bad: false }; changed(); };
ACT.clipboard = () => { st.paste.text = SAMPLE_LINK; st.paste.bad = false; changed(); }; // the canvas' clipboard holds a sample link
ACT.pastePair = () => {
  if (parsePairing(st.paste.text)) { st.paste = null; st.keyboard = false; setMode("paired", true); } // PasteSheet pairs directly (model.pair)
  else { st.paste.bad = true; changed(); }
};
ACT.openAddress = () => { st.address = { text: "", bad: false }; changed(); };
ACT.saveAddress = () => {
  const ok = /^(\d{1,3}\.){3}\d{1,3}(:\d{1,5})?$/.test(st.address.text.trim()) && st.address.text.trim().split(/[.:]/).slice(0, 4).every((n) => +n <= 255);
  if (ok) { st.address = null; st.keyboard = false; st.link = "connected"; changed(); } else { st.address.bad = true; changed(); }
};

function sheetLayer() {
  if (st.diff) return diffSheet();
  if (st.paste) return pasteSheet();
  if (st.address) return addressSheet();
  if (st.questionPill) return questionSheet(st.questionPill);
  const r = pendingApproval();
  if (r) return approvalSheet(r);
  return null;
}

/* ---------- PairConfirm (Material 3 AlertDialog) and Clear chat ---------- */
function dialogLayer() {
  if (st.pairRequest) {
    const p = parsePairing(st.pairRequest);
    const body = fmt("pair_confirm_body", p.host, p.port) + (st.mode === "paired" ? "\n\n" + S.pair_confirm_replace : "");
    return { key: "pair", html: `<div class="dhost"><div class="dlg" role="alertdialog"><h2>${esc(fmt("pair_confirm_title", p.name || p.host))}</h2><p>${esc(body)}</p>
      <div class="acts"><button type="button" class="tbtn" data-act="pairCancel">${esc(S.action_cancel)}</button><button type="button" class="tbtn" data-act="pairOk">${esc(S.pair_confirm_ok)}</button></div></div></div>` };
  }
  if (st.chat.confirmClear) {
    return { key: "clear", html: `<div class="dhost"><div class="dlg" role="alertdialog"><h2>${esc(S.chat_clear_title)}</h2><p>${esc(S.chat_clear_body)}</p>
      <div class="acts"><button type="button" class="tbtn" data-act="clearCancel">${esc(S.action_cancel)}</button><button type="button" class="tbtn" style="color:#F2B8B5" data-act="clearOk">${esc(S.chat_clear)}</button></div></div></div>` };
  }
  return null;
}
ACT.pairCancel = () => { st.pairRequest = null; changed(); };
ACT.pairOk = () => { st.pairRequest = null; setMode("paired", true); };

/* ---------- BiometricPrompt (BiometricGate), simulated ---------- */
function bioLayer() {
  if (!st.bio) return null;
  const b = st.bio;
  return { key: "bio:" + b.title + b.sub, html: `<div class="bio-host"><div class="bio" role="dialog" aria-label="${esc(b.title)}">
    <div class="app">${icon("LOCK", 16, "#bdc1c6")} Kenai</div><h2>${esc(b.title)}</h2><p class="sub">${esc(b.sub)}</p>
    <button type="button" class="fp ${b.ok ? "ok" : ""}" data-act="bioTouch" aria-label="Touch the fingerprint sensor">${b.ok ? icon("CHECK", 36) : '<svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M12 11v3M8.5 9.5a4 4 0 0 1 7 2.5v2a8 8 0 0 1-1 4M8 13v1a6 6 0 0 1-1 4M5 9a8 8 0 0 1 14 2v2M4 15v-1M17.5 19.5"/></svg>'}</button>
    <p class="fpt">${b.ok ? "Fingerprint recognised" : "Touch the fingerprint sensor"}</p>
    <div class="acts"><button type="button" class="tbtn" style="color:#8ab4f8" data-act="bioPin">Use PIN</button><button type="button" class="tbtn" style="color:#8ab4f8" data-act="bioCancel">${esc(S.action_cancel)}</button></div></div></div>` };
}
ACT.bioTouch = () => {
  if (!st.bio || st.bio.ok) return;
  st.bio.ok = true; changed();
  setTimeout(() => { const b = st.bio; st.bio = null; if (b) b.onOk(); changed(); }, 450);
};
ACT.bioPin = ACT.bioTouch;
ACT.bioCancel = () => { st.bio = null; changed(); }; // onFail with an empty message: nothing shown

/* ---------- Android's own screens: the camera permission, the overlay permission, battery settings ---------- */
function sysLayer() {
  if (st.sys === "camera") {
    return { key: "cam", html: `<div class="sysd"><div class="box">${icon("SCAN", 28, "#8ab4f8")}<p>Allow <b>Kenai</b> to take pictures and record video?</p>
      <button type="button" data-act="cameraGrant" data-arg="once">While using the app</button><button type="button" data-act="cameraGrant" data-arg="once">Only this time</button><button type="button" data-act="cameraGrant" data-arg="deny">Don't allow</button></div></div>` };
  }
  if (st.sys === "overlay" || st.sys === "battery") {
    const overlay = st.sys === "overlay";
    const on = st.settings.overlayPermission;
    const opts = overlay
      ? `<div class="opt2"><span>Allow display over other apps</span><span style="margin-left:auto">${switchEl(on, "sysOverlay", "Allow display over other apps")}</span></div>`
      : `<button type="button" class="opt2" data-act="sysClose"><span class="radio on"></span>Unrestricted</button><button type="button" class="opt2" data-act="sysClose"><span class="radio"></span>Optimised</button><button type="button" class="opt2" data-act="sysClose"><span class="radio"></span>Restricted</button>`;
    return { key: st.sys, html: `<div class="sysp"><div class="top"><button type="button" aria-label="Back" data-act="sysClose">${icon("BACK", 24, "#e8eaed")}</button><h2>${overlay ? "Display over other apps" : "App battery usage"}</h2></div>
      <div class="app">${kodaEl("idle", 40)}<span>Kenai</span></div>${opts}<p class="note">Android settings, simulated by the canvas.</p></div>` };
  }
  return null;
}
ACT.sysOverlay = () => { st.settings.overlayPermission = !st.settings.overlayPermission; changed(); };
ACT.sysClose = () => { st.sys = null; changed(); };
