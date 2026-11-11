"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* Pairing (ui/PairingScreen.kt), Scan (ui/ScanScreen.kt), Home (MainActivity.kt Home + ui/HomePanel.kt), bottom bar (ui/BottomBar.kt). */

function pairingScreen() {
  return `<div class="scr"><div class="pairing">
  <div style="height:48px"></div>${kodaEl("sleeping", 132)}<div style="height:24px"></div>
  <h1 class="t-display center">${esc(S.pairing_headline)}</h1>
  <p class="t-body dim center" style="padding:10px 0 28px">${esc(S.pairing_help)}</p>
  ${st.message ? messagePanel(st.message) + '<div style="height:12px"></div>' : ""}
  ${pill(S.scan_button, "scan", { kind: "pri", w: true, icon: "SCAN" })}
  <div style="height:12px"></div>
  <div class="row2">${pill(S.pair_paste_short, "openPaste")}${pill(S.demo_try, "startDemo")}</div>
  <div style="height:32px"></div>
  <p class="t-sec faint center">${esc(S.pairing_footer)}</p>
  <button type="button" class="t-sec dim u" style="min-height:48px;padding:14px 16px" data-act="tab" data-arg="SETTINGS">${esc(S.pairing_about)}</button>
  <div class="clear"></div></div></div>`;
}
ACT.scan = () => { st.scanWrong = false; go("SCAN"); };
ACT.startDemo = () => setMode("demo");

/* ---------- Scan ---------- */
function fakeQr() {
  let cells = "";
  let seed = 7;
  const rnd = () => (seed = (seed * 9301 + 49297) % 233280) / 233280;
  const finder = (x, y) => `<rect x="${x}" y="${y}" width="7" height="7" fill="#111"/><rect x="${x + 1}" y="${y + 1}" width="5" height="5" fill="#fff"/><rect x="${x + 2}" y="${y + 2}" width="3" height="3" fill="#111"/>`;
  for (let y = 0; y < 21; y++) for (let x = 0; x < 21; x++) {
    const inFinder = (x < 8 && y < 8) || (x > 12 && y < 8) || (x < 8 && y > 12);
    if (!inFinder && rnd() > 0.52) cells += `<rect x="${x}" y="${y}" width="1" height="1" fill="#111"/>`;
  }
  return `<svg width="104" height="104" viewBox="-1 -1 23 23" shape-rendering="crispEdges"><rect x="-1" y="-1" width="23" height="23" fill="#fff"/>${cells}${finder(0, 0)}${finder(14, 0)}${finder(0, 14)}</svg>`;
}
function scanScreen() {
  const top = `<div style="display:flex;align-items:center;padding-top:4px"><button type="button" aria-label="${esc(S.action_back)}" data-act="back" style="width:48px;height:48px;border-radius:50%;display:flex;align-items:center;justify-content:center">${icon("BACK", 24, "#F5F6F8")}</button><h1 class="t-title white ell">${esc(S.scan_title)}</h1></div>`;
  let body;
  if (st.camera === "granted") {
    body = `<div class="cam"><div class="desk">${fakeQr()}</div>
      <div class="vf" style="display:flex;align-items:center;justify-content:center"><div style="width:70%;aspect-ratio:1;border:3px solid #fff;border-radius:16px;box-shadow:0 0 0 999px rgba(0,0,0,.6)"></div></div>
      <div class="simbtns"><button type="button" data-act="simScan" data-arg="ok">Simulate scan: Kenai code</button><button type="button" data-act="simScan" data-arg="other">Simulate scan: other QR</button></div></div>
      <p class="t-body center" style="padding-bottom:12px;color:${st.scanWrong ? "var(--danger)" : "#9398A1"}">${esc(st.scanWrong ? S.scan_not_pairing : S.scan_hint)}</p>`;
  } else if (st.camera === "ask") {
    body = `<p class="t-body white" style="padding-top:12px">${esc(S.scan_why)}</p>${pill(S.scan_allow, "askCamera", { kind: "pri", dark: true, w: true })}${pill(S.scan_paste_instead, "back", { dark: true, w: true })}`;
  } else {
    body = `<p class="t-body white" style="padding-top:12px">${esc(S.scan_blocked)}</p>${pill(S.scan_open_settings, "cameraSettings", { kind: "pri", dark: true, w: true })}${pill(S.scan_paste_instead, "back", { dark: true, w: true })}`;
  }
  return `<div class="scan">${top}${body}</div>`;
}
let wrongTimer = null;
ACT.simScan = (a) => {
  if (a === "ok") { st.pairRequest = SAMPLE_LINK; go("HOME"); return; } // MainActivity: onText true → Home, then PairConfirm
  st.scanWrong = true; changed();
  clearTimeout(wrongTimer); wrongTimer = setTimeout(() => { st.scanWrong = false; changed(); }, 2500);
};
ACT.askCamera = () => { st.sys = "camera"; changed(); };
ACT.cameraSettings = () => { st.camera = "granted"; changed(); }; // stands for: the user allowed it in Android's settings and came back
ACT.cameraGrant = (a) => {
  st.sys = null;
  if (a === "deny") st.camera = st.cameraAsked ? "blocked" : "ask";
  else st.camera = "granted";
  st.cameraAsked = true;
  changed();
};

/* ---------- Home ---------- */
function heroCard(focus, list) {
  const state = focus ? focus.state : "sleeping";
  const running = list.filter((s) => BUSY.includes(s.state)).length;
  const hasApproval = approvals().length > 0;
  const link = hasApproval ? "APPROVAL" : st.mode === "none" ? "PAIR" : "NONE";
  let who;
  if (!focus) {
    who = `<div class="empty">${kodaEl(state, 128, S.koda_description)}<p class="t-title white">${esc(S.home_empty_title)}</p><p class="t-sec dim clamp3">${esc(S.home_empty_hint)}</p></div>`;
  } else {
    const pillName = (PILLS[focus.pill] || {}).name || "";
    const name = focus.agent || pillName || S.app_name;
    const sub = pillName && pillName !== name ? pillName : S.agent_kind;
    who = `<div class="who">${kodaEl(state, 96, S.koda_description, { glow: focus.color })}<div><p class="t-title white clamp2">${esc(name)}</p><p class="t-sec dim ell">${esc(sub)}</p><p class="t-head white clamp3" style="padding-top:4px">${esc(homeLine(focus) || stateLabel(state))}</p></div></div>`;
  }
  const btns = [];
  if (link !== "NONE") btns.push(pill(link === "PAIR" ? S.link_pair : S.link_approval, link === "PAIR" ? "scan" : "review", { kind: "pri", dark: true }));
  if (focus && focus.state === "question") btns.push(pill(questionFor(focus.pill) ? S.question_see_answer : S.question_see, "openQuestion", { kind: "pri", dark: true, arg: focus.pill }));
  if (focus && hasDetails(focus)) btns.push(pill(S.session_details, "details", { dark: true, arg: focus.pill, icon: "CHEVRON", iconSize: 18 }));
  return `<div class="hero"><div class="wash" style="--wash:${roleOf(state)};opacity:.5"></div>
    <div class="lk">${dot(linkDot())}<span class="ell">${esc(linkText())}</span>${running > 0 ? `<span>${esc(fmt("home_running", running))}</span>` : ""}</div>
    ${who}${btns.length ? `<div class="btns">${btns.join("")}</div>` : ""}</div>`;
}
ACT.review = () => { st.closedApproval = null; changed(); };
ACT.details = (a) => { st.detailPill = a; go("SESSION"); };
ACT.pick = (a) => { st.selected = a; changed(); };

function agentRows(others) {
  return panel(others.map((s, i) => {
    const name = s.agent || (PILLS[s.pill] || {}).name || "";
    return `${i > 0 ? divider() : ""}<button type="button" class="arow" data-act="pick" data-arg="${s.pill}">${kodaEl(s.state, 40)}<div><b class="ell">${esc(name)}</b><span class="ell">${esc(homeLine(s) || stateLabel(s.state))}</span></div>${dot(roleOf(s.state))}</button>`;
  }).join(""));
}
function usagePanel() {
  const u = FAKE.usage;
  const bar = (plan, label, pct, reset) => {
    const c = pct < 50 ? "#0F9F6E" : pct < 80 ? "#B45309" : "var(--danger)";
    return `<div style="display:flex;flex-direction:column;gap:4px" aria-label="${esc(fmt("usage_desc", plan, label, pct))}. ${esc(reset)}" role="group">
      <div style="display:flex"><span class="t-body" style="flex:1">${esc(label)}</span><span class="t-body">${esc(fmt("usage_pct", pct))}</span></div>
      <div class="bar8"><i style="width:${pct}%;background:${c}"></i></div><span class="t-sec dim">${esc(reset)}</span></div>`;
  };
  const block = (name, p) => `<div style="padding:16px;display:flex;flex-direction:column;gap:10px">
    <div style="display:flex;align-items:center"><span class="t-head" style="flex:1">${esc(name)}</span><span class="t-sec dim">${esc(p.plan)}</span></div>
    ${bar(name, S.usage_five_hours, p.five.pct, (p.five.resetMin < 60 ? fmt("usage_resets_m", p.five.resetMin) : fmt("usage_resets_hm", Math.floor(p.five.resetMin / 60), p.five.resetMin % 60)))}
    ${bar(name, S.usage_week, p.week.pct, fmt("usage_resets_week", p.week.day))}
    ${p.credits ? `<span class="t-sec dim">${esc(fmt("usage_credits", p.credits))}</span>` : ""}</div>`;
  return `<div style="display:flex;flex-direction:column;gap:8px">${heading(S.usage_title)}${panel(block(S.usage_claude, u.claude) + divider() + block(S.usage_codex, u.codex))}</div>`;
}
function servicesPanel() {
  const cards = FAKE.services.map((c) => panel(`<div style="padding:16px;display:flex;flex-direction:column;gap:6px">
    <div style="display:flex;align-items:center;gap:8px"><span class="t-sec dim ell" style="flex:1">${esc(c.title)}</span>${c.reason ? `<span class="t-label dim">${esc(c.reason)}</span>` : ""}</div>
    <p class="t-title clamp2">${esc(c.headline)}</p>
    ${c.items.map((l) => `<div style="display:flex;gap:12px;align-items:center"><span class="t-body ell" style="flex:1">${esc(l.label)}</span><span class="t-sec dim">${esc(l.detail)}</span></div>`).join("")}</div>`)).join("");
  return `<div style="display:flex;flex-direction:column;gap:8px">${heading(S.services_title)}${cards}</div>`;
}
function dayOf(at) {
  const d = new Date(at), n = new Date();
  const a = new Date(d.getFullYear(), d.getMonth(), d.getDate()), b = new Date(n.getFullYear(), n.getMonth(), n.getDate());
  const days = Math.round((b - a) / 864e5);
  return days === 0 ? "TODAY" : days === 1 ? "YESTERDAY" : "OTHER";
}
const hhmm = (at) => new Date(at).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
function recentPanel() {
  if (!st.decisions.length) return "";
  const rows = st.decisions.slice(0, 2).map((d, i) => {
    const day = dayOf(d.at);
    const when = day === "TODAY" ? hhmm(d.at) : day === "YESTERDAY" ? S.history_yesterday : new Date(d.at).toLocaleDateString("en-GB");
    return `${i > 0 ? divider() : ""}<button type="button" class="rrow" data-act="tab" data-arg="HISTORY">${dot(d.allowed ? "#0F9F6E" : "var(--danger)")}<span class="t-body ell" style="flex:1">${esc(recentText(d.allowed, d.tool))}</span><span class="t-sec dim">${esc(when)}</span></button>`;
  }).join("");
  return `<div style="display:flex;flex-direction:column;gap:8px"><div style="display:flex;align-items:center">${heading(S.recent_title).replace('class="sh ell"', 'class="sh ell" style="flex:1"')}<button type="button" class="t-label dim" style="min-height:48px;padding:12px 4px" data-act="tab" data-arg="HISTORY">${esc(S.recent_all)}</button></div>${panel(rows)}</div>`;
}
function linkHealthPanel() {
  return panel(`<div style="padding:16px;display:flex;flex-direction:column;gap:4px"><p class="t-body">${esc(LINK_LOST[0])}</p><p class="t-sec dim">${esc(LINK_LOST[1])}</p></div>${divider()}${listRow(S.diag_title, { act: "tab", arg: "DIAGNOSTICS", trailing: chevron() })}`);
}
const batteryCard = () => panel(`<div style="padding:16px;display:flex;flex-direction:column;gap:8px"><p class="t-head">${esc(S.battery_title)}</p><p class="t-sec dim">${esc(S.battery_body)}</p>
  ${BATTERY_STEPS.map((s, i) => `<p class="t-sec">${i + 1}. ${esc(s)}</p>`).join("")}
  <div class="row2 g8" style="padding-top:4px">${pill(S.battery_not_now, "snoozeBattery")}${pill(S.battery_open, "openBattery", { kind: "pri" })}</div></div>`);
ACT.snoozeBattery = () => { st.batterySnoozed = true; changed(); };
ACT.openBattery = () => { st.sys = "battery"; changed(); };
const discoveryHint = () => panel(`<div style="padding:16px;display:flex;flex-direction:column;gap:8px"><p class="t-head">${esc(S.discovery_title)}</p><p class="t-sec dim">${esc(S.discovery_body)}</p>
  <div class="row2 g8" style="padding-top:4px">${pill(S.discovery_pair_again, "scan")}${pill(S.discovery_enter_address, "openAddress")}</div></div>`);
const overlayHint = () => panel(`<div style="display:flex;align-items:center;padding-left:16px"><p class="t-sec dim" style="flex:1;padding:12px 0">${esc(S.overlay_missing)}</p><button type="button" class="t-label" style="min-height:48px;padding:14px 16px" data-act="overlay" data-arg="on">${esc(S.overlay_allow)}</button></div>`);

function homeScreen() {
  if (st.mode === "none") return pairingScreen();
  const list = sessions();
  const focus = focusOf(list);
  const others = list.filter((s) => !focus || s.pill !== focus.pill);
  const items = [heroCard(focus, list)];
  const g = (x) => `<div class="gut">${x}</div>`;
  if (st.mode === "paired" && st.link !== "connected") items.push(g(linkHealthPanel()));
  if (batteryHint()) items.push(g(batteryCard()));
  if (st.mode === "paired" && st.link === "notfound") items.push(g(discoveryHint()));
  if (st.message) items.push(g(messagePanel(st.message)));
  if (others.length) items.push(g(agentRows(others)));
  if (st.mode === "paired" && connected()) { items.push(g(usagePanel())); items.push(g(servicesPanel())); }
  const recent = recentPanel();
  if (recent) items.push(g(recent));
  if (st.settings.overlayWished && !st.settings.overlayPermission) items.push(g(overlayHint()));
  items.push('<div style="height:16px;flex:none"></div><div class="clear"></div>');
  return `<div class="scr"><div class="col">${items.join("")}</div></div>`;
}

/* ---------- ui/BottomBar.kt ---------- */
function bottomBar() {
  const sel = TAB_OF[st.screen];
  const tab = (id, ic, label) => {
    const on = sel === id;
    const alert = id === "HOME" && approvals().length > 0 ? '<span class="alert" style="background:' + STATE_COLOR.approval + '"></span>' : "";
    return `<button type="button" role="tab" aria-selected="${on}" data-act="tab" data-arg="${id}" data-tab="${id}">${icon(ic, 22, on ? "var(--text)" : "var(--dim)")}<span>${esc(label)}</span>${alert}</button>`;
  };
  return `<nav class="bbar" role="tablist">${tab("HOME", "HOME", S.tab_home)}${tab("CHAT", "CHAT", S.tab_chat)}${tab("SETTINGS", "SLIDERS", S.tab_settings)}</nav>`;
}
