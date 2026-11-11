"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* ui/SettingsScreens.kt (Settings, History), Gallery (MainActivity.kt), ui/DiagnosticsScreen.kt, ui/LicencesScreen.kt, ui/SessionScreen.kt. */

const settingSwitch = (title, hint, on, act, disabled) => listRow(title, { hint, trailing: switchEl(on, act, title, disabled) });
const linkRow = (title, screen) => listRow(title, { act: "tab", arg: screen, trailing: chevron() });
const clockText = (m) => `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;
function timeRow(label, which) {
  const q = st.settings.quiet;
  const m = q[which];
  const dis = !q.enabled;
  return `<div style="display:flex;align-items:center;gap:8px;padding:4px 16px;${dis ? "opacity:.5" : ""}"><span class="t-body" style="flex:1">${esc(label)}</span>
    <button type="button" class="step" ${dis ? "disabled" : ""} data-act="quietStep" data-arg="${which}:-30" aria-label="${esc(label)}, 30 minutes earlier">−</button>
    <span class="t-body" style="width:56px;font-family:var(--mono)">${clockText(m)}</span>
    <button type="button" class="step" ${dis ? "disabled" : ""} data-act="quietStep" data-arg="${which}:30" aria-label="${esc(label)}, 30 minutes later">+</button></div>`;
}
ACT.quietStep = (a) => { const [w, d] = a.split(":"); const q = st.settings.quiet; q[w] = (((q[w] + +d) % 1440) + 1440) % 1440; changed(); };

function settingsScreen() {
  const s = st.settings;
  let computer = `<div style="display:flex;align-items:center;gap:12px;padding:16px">${dot(linkDot())}<span class="t-body clamp2" style="flex:1">${esc(linkText())}</span></div>`;
  if (st.mode === "paired") computer += divider() + linkRow(S.diag_title, "DIAGNOSTICS") + divider() + `<div style="padding:16px">${pill(S.action_unpair, "unpair", { kind: "dan", w: true })}</div>`;
  else if (st.mode === "demo") computer += divider() + `<div style="padding:16px">${pill(S.demo_leave, "leaveDemo", { w: true })}</div>`;
  else computer += `<p class="t-sec dim" style="padding:0 16px 16px">${esc(S.settings_computer_none)}</p>`;
  const vol = Math.round((s.volume / 0.2) * 100);
  const items = [
    screenTitle(S.settings_title),
    heading(S.settings_section_computer), panel(computer),
    batteryHint() ? batteryCard() : "",
    heading(S.settings_section_display), panel(settingSwitch(S.overlay_title, S.overlay_hint, s.overlayWished && s.overlayPermission, "overlay")),
    heading(S.settings_section_sound),
    panel(settingSwitch(S.settings_sound, null, s.soundOn, "sound") + divider() +
      `<div style="padding:8px 16px"><div style="display:flex"><span class="t-body" style="flex:1">${esc(S.settings_volume)}</span><span class="t-sec dim" id="volpct">${vol}%</span></div>
      <input id="vol" class="slider" type="range" min="0" max="0.2" step="0.01" value="${s.volume}" style="--f:${vol}%" aria-label="${esc(S.settings_volume)}" data-input="volume" ${s.soundOn ? "" : "disabled"}></div>`),
    heading(S.settings_section_notices),
    panel(settingSwitch(S.notify_done, S.notify_done_hint, s.notifyDone, "notifyDone") + divider() +
      settingSwitch(S.quiet_hours, S.quiet_hint, s.quiet.enabled, "quiet") + timeRow(S.quiet_from, "from") + timeRow(S.quiet_to, "to") + '<div style="height:8px"></div>'),
    heading(S.settings_section_more),
    panel(linkRow(S.history_title, "HISTORY") + divider() + divider() + linkRow(S.gallery, "GALLERY")),
    heading(S.settings_section_about),
    panel(`<div style="padding:16px;display:flex;flex-direction:column;gap:8px"><p class="t-body">${esc(S.about_unofficial)}</p>
      <a class="t-body u" style="color:var(--text)" href="#" data-act="repo">${esc(S.about_repo)}</a><p class="t-sec dim">${esc(S.about_assets)}</p></div>${divider()}${linkRow(S.licences_title, "LICENCES")}`),
    '<div style="height:16px;flex:none"></div><div class="clear"></div>',
  ];
  return `<div class="scr gut"><div class="col">${items.join("")}</div></div>`;
}
/** MainActivity.setOverlay: the wish is saved first; without Android's permission its settings page opens. */
ACT.overlay = () => {
  const s = st.settings;
  const on = !(s.overlayWished && s.overlayPermission);
  s.overlayWished = on;
  if (on && !s.overlayPermission) { st.message = S.msg_overlay_permission; st.sys = "overlay"; }
  changed();
};
ACT.sound = () => { st.settings.soundOn = !st.settings.soundOn; changed(); };
ACT.notifyDone = () => { st.settings.notifyDone = !st.settings.notifyDone; changed(); };
ACT.quiet = () => { st.settings.quiet.enabled = !st.settings.quiet.enabled; changed(); };
ACT.repo = (_, el, e) => { e.preventDefault(); st.message = "Opens github.com/Alvarogds04/kenai in the browser."; changed(); };
ACT.unpair = () => setMode("none", true);
ACT.leaveDemo = () => setMode("none", true);
INPUT.volume = (v, el) => {
  st.settings.volume = +v;
  const pct = Math.round((+v / 0.2) * 100);
  el.style.setProperty("--f", pct + "%");
  document.getElementById("volpct").textContent = pct + "%";
};

/* ---------- History ---------- */
function historyScreen() {
  const groups = [];
  for (const d of st.decisions) {
    const day = dayOf(d.at);
    const label = day === "TODAY" ? S.history_today : day === "YESTERDAY" ? S.history_yesterday : new Date(d.at).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
    let g = groups.find((x) => x.label === label);
    if (!g) groups.push((g = { label, list: [] }));
    g.list.push(d);
  }
  const items = [screenTitle(S.history_title, "back"), `<p class="t-sec dim" style="padding:0 4px">${esc(S.history_note)}</p>`];
  if (!groups.length) items.push(panel(`<p class="t-body dim" style="padding:16px">${esc(S.history_empty)}</p>`));
  for (const g of groups) {
    items.push(heading(g.label));
    items.push(panel(g.list.map((d, i) => `${i > 0 ? divider() : ""}<div class="rrow">${dot(d.allowed ? "#0F9F6E" : "var(--danger)")}<span class="t-body clamp2" style="flex:1">${esc(decisionText(d.allowed, d.agent, d.tool))}</span><span class="t-sec dim">${hhmm(d.at)}</span></div>`).join("")));
  }
  if (groups.length) items.push(pill(S.history_clear, "clearHistory", { kind: "dan", w: true }));
  items.push('<div style="height:16px;flex:none"></div>');
  return `<div class="scr gut"><div class="col">${items.join("")}</div></div>`;
}
ACT.clearHistory = () => { st.decisions = []; changed(); };

/* ---------- Gallery: every state, then every emote, 3 columns ---------- */
function galleryScreen() {
  const cell = (k) => `<div style="display:flex;flex-direction:column;align-items:center">${clay(k, 98)}<span style="font-size:11px;line-height:16px;font-weight:500">${k}</span></div>`;
  return `<div class="scr gut">${screenTitle(S.gallery, "back")}<div style="height:12px"></div>
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:12px">${STATES.map(cell).join("")}${EMOTES.map(cell).join("")}</div><div style="height:16px"></div></div>`;
}

/* ---------- Diagnostics ---------- */
function diagnosticsScreen() {
  const d = st.diag;
  if (!d.results.length && !d.running) setTimeout(runDiag, 0);
  const rows = d.results.map(([title, kind, detail, hint], i) => {
    const word = kind === "ok" ? S.diag_ok : kind === "problem" ? S.diag_problem : S.diag_skipped;
    const c = kind === "ok" ? "#0F9F6E" : kind === "problem" ? "var(--danger)" : "var(--faint)";
    return `${i > 0 ? divider() : ""}<div style="display:flex;gap:12px;align-items:flex-start;padding:12px 16px;min-height:56px">${dot(c, "margin-top:6px")}<div style="flex:1;display:flex;flex-direction:column;gap:2px"><p class="t-body">${esc(title)} · ${esc(word)}</p><p class="t-sec dim">${esc(detail)}</p>${hint ? `<p class="t-sec">${esc(hint)}</p>` : ""}</div></div>`;
  }).join("");
  const items = [screenTitle(S.diag_title, "back"), `<p class="t-sec dim" style="padding:0 4px">${esc(S.diag_intro)}</p>`,
    panel(rows + (d.running ? `<p class="t-sec dim" style="padding:16px">${esc(S.diag_running)}</p>` : "")),
    `<div class="row2 g8">${pill(S.diag_run, "diagRun", { disabled: d.running })}${pill(S.diag_copy, "diagCopy", { kind: "pri", disabled: !d.results.length || d.running })}</div>`,
    d.copied ? `<p class="t-sec dim" style="padding:0 4px">${esc(S.diag_copied)}</p>` : "", '<div class="clear"></div>'];
  return `<div class="scr gut"><div class="col">${items.join("")}</div></div>`;
}
function runDiag() {
  const d = st.diag;
  if (d.running) return;
  const all = st.link === "connected" ? DIAG_OK : DIAG_DOWN;
  d.running = true; d.results = []; d.copied = false;
  changed();
  let i = 0;
  const next = () => {
    if (st.diag !== d) return;
    d.results.push(all[i++]);
    if (i >= all.length) d.running = false; else setTimeout(next, 350);
    changed();
  };
  setTimeout(next, 350);
}
ACT.diagRun = () => runDiag();
ACT.diagCopy = () => { st.diag.copied = true; changed(); };

/* ---------- Licences: assets/licenses, paragraphs in mono ---------- */
const LIC_PARAS = LICENCES.replace(/\r\n/g, "\n").split(/\n\s*\n/).map((p) => p.replace(/^\n+|\n+$/g, "")).filter((p) => p.trim());
function licencesScreen() {
  return `<div class="scr gut"><div style="display:flex;flex-direction:column;gap:10px">${screenTitle(S.licences_title, "back")}<p class="t-sec dim" style="padding:0 4px">${esc(S.licences_intro)}</p>
    ${LIC_PARAS.map((p) => `<p class="t-sec" style="padding:0 4px;font-family:var(--mono);white-space:pre-wrap">${esc(p)}</p>`).join("")}<div class="clear"></div></div></div>`;
}

/* ---------- Session details ---------- */
function sessionScreen() {
  const s = sessions().find((x) => x.pill === st.detailPill);
  if (!s) return `<div class="scr gut">${screenTitle(S.session_details, "back")}<p class="t-body dim" style="padding:16px">${esc(S.session_ended)}</p></div>`;
  const name = s.agent || (PILLS[s.pill] || {}).name || "";
  const busy = BUSY.includes(s.state);
  const items = [screenTitle(name, "back"),
    panel(`<div style="display:flex;align-items:center;gap:16px;padding:16px">${clay(s.state, 96, s.color)}<div style="flex:1;display:flex;flex-direction:column;gap:6px"><p class="t-head clamp3">${esc(homeLine(s) || stateLabel(s.state))}</p>${s.project ? `<span class="chip"><span class="ell">${esc(fmt("session_project", s.project))}</span></span>` : ""}</div></div>`, STATE_COLOR[s.state])];
  if (s.finalLine) items.push(heading(S.session_last), panel(`<p class="t-body" style="padding:16px">${esc(s.finalLine)}</p>`, STATE_COLOR.finished));
  if (s.files && s.files.length) {
    items.push(heading(S.files_heading), panel([...s.files].reverse().map((f, i) => (i > 0 ? divider() : "") +
      listRow(f.name, { hint: f.isNew ? S.files_new : null, act: "openDiff", arg: f.name, trailing: `<span class="t-sec dim" style="font-family:var(--mono)">${esc(fmt("files_counts", f.added, f.removed))}</span>` })).join("")));
  }
  items.push(heading(S.session_steps));
  const steps = [...(s.steps || [])].reverse();
  if (!steps.length) items.push(panel(`<p class="t-sec dim" style="padding:16px">${esc(st.mode === "paired" ? S.session_no_steps : S.session_hint)}</p>`));
  else items.push(panel(steps.map((stp, i) => {
    const current = i === 0 && busy;
    const mark = current ? dot(s.color, "margin:0 7px") : icon("CHECK", 22, i === 0 ? "#0F9F6E" : "var(--faint)");
    const label = toolLabel(stp) || (current ? S.session_step_working : S.session_step_done);
    return `${i > 0 ? divider() : ""}<div style="display:flex;align-items:center;gap:12px;min-height:48px;padding:10px 16px">${mark}<span class="t-body" style="flex:1;color:${i === 0 ? "var(--text)" : "var(--dim)"};font-weight:${current ? 600 : 400}">${esc(label)}</span></div>`;
  }).join("")));
  items.push('<div style="height:16px;flex:none"></div>');
  return `<div class="scr gut"><div class="col">${items.join("")}</div></div>`;
}
