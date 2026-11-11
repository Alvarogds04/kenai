"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* The Android home screen with Kenai's 9 widgets, drawn from window.kenai on every change (app/WidgetHub.kt pushes them the same
   way: no timer, a new picture per change). Layouts: res/layout/widget_*.xml; logic: app/*Widget*.kt; data: widgets-data.js. */

/* res/drawable/ic_widget_*.xml (24 grid, stroke 2, round) and ic_widget_offline.xml (filled, widget_tone_muted). */
const W_ICON = {
  home: '<path d="M3 11.5L12 4L21 11.5"/><path d="M5.5 10V19.5A.5.5 0 0 0 6 20H10V14.5H14V20H18A.5.5 0 0 0 18.5 19.5V10"/>',
  chat: '<path d="M5 4.5H19A1.5 1.5 0 0 1 20.5 6V15A1.5 1.5 0 0 1 19 16.5H11L7 20V16.5H5A1.5 1.5 0 0 1 3.5 15V6A1.5 1.5 0 0 1 5 4.5Z"/>',
  settings: '<circle cx="12" cy="12" r="6.4"/><circle cx="12" cy="12" r="2.5"/><path d="M18.4 12H21.4M16.53 16.53L18.65 18.65M12 18.4V21.4M7.47 16.53L5.35 18.65M5.6 12H2.6M7.47 7.47L5.35 5.35M12 5.6V2.6M16.53 7.47L18.65 5.35"/>',
  recent: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12L15.2 14"/>',
  session: '<path d="M13.5 4H20V10.5"/><path d="M20 4L11 13"/><path d="M18 14.5V18.5A1.5 1.5 0 0 1 16.5 20H5.5A1.5 1.5 0 0 1 4 18.5V7.5A1.5 1.5 0 0 1 5.5 6H9.5"/>',
  pair: '<path d="M4 4H10V10H4Z"/><path d="M14 4H20V10H14Z"/><path d="M4 14H10V20H4Z"/><path d="M14 14H16.5M20 14V16.5M14 17.5V20M17.5 20H20"/>',
};
const wIcon = (k, size) => `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${W_ICON[k]}</svg>`;
const wOffline = (size, color = W_TONE.MUTED.c) => `<svg class="kw-off" width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true"><path fill="${color}" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM4 12c0-4.42 3.58-8 8-8 1.85 0 3.55.63 4.9 1.69L5.69 16.9C4.63 15.55 4 13.85 4 12zM12 20c-1.85 0-3.55-.63-4.9-1.69L18.31 7.1C19.37 8.45 20 10.15 20 12c0 4.42-3.58 8-8 8z"/></svg>`;
const wDot = (color, size = 10, style = "") => `<span class="kw-dot" style="width:${size}px;height:${size}px;background:${color};${style}"></span>`;
const wRgba = (hex, a) => { const n = parseInt(hex.slice(1), 16); return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`; };
/** CharacterBitmap.of: Koda still (the first frame of the pose for [state]); with a tone, the radial glow (0.55, or 0.32 for muted and idle) behind it. */
function wFace(state, tone, style = "") {
  const glow = tone ? `background:radial-gradient(closest-side circle,${wRgba(W_TONE[tone].c, tone === "MUTED" || tone === "IDLE" ? 0.32 : 0.55)},rgba(0,0,0,0));` : "";
  return `<span class="kw-face" style="${glow}${style}">${kodaEl(state, "fit", null, { still: true })}</span>`;
}
const wBadge = (n) => (n > 0 ? `<span class="kw-badge" aria-label="${esc(fmt("widget_badge_description", n))}">${n}</span>` : "");
const wBtn = (to, inner, cls, label, style = "") => `<button type="button" class="${cls}" data-to="${esc(to)}" aria-label="${esc(label)}" style="${style}">${inner}</button>`;
/** WidgetHub.openApp / openSession / openScreen as a data-to target. */
const toSession = (top) => (top ? "session:" + top.pill : "home");

/** WidgetHub.responsive: the largest of SMALL, MEDIUM (from mediumAt) and LARGE (from largeAt) that fits the widget, in dp. */
function sizeOf(w, h, mediumAt, largeAt) {
  const fits = ([mw, mh]) => w >= mw && h >= mh;
  return fits(largeAt) ? "LARGE" : fits(mediumAt) ? "MEDIUM" : "SMALL";
}

/* ---------- one renderer per widget: (content, width, height) → inner HTML of the card ---------- */
const W_RENDER = {
  /** GlanceWidget (3x2): the Glance, its own look and mood. */
  glance(c, w, h) {
    const g = c.glance;
    const size = sizeOf(w, h, [0, 100], [0, 150]);
    const n = size === "SMALL" ? 1 : size === "MEDIUM" ? 2 : 3;
    const lines = g.lines.slice(0, n).map((l) => `<div class="kw-row" style="margin-top:3px">${wDot(W_TONE[toneOf(l.state)].c, 8)}<span class="kw-ell" style="margin-left:6px;font-size:13px;font-weight:700;flex:0 1 auto">${esc(l.agent)}</span><span class="kw-sec kw-ell" style="margin-left:6px;flex:1 1 0">${esc(l.text)}</span></div>`).join("");
    const detail = !g.lines.length || size === "LARGE" ? `<div class="kw-sec kw-ell">${esc(g.detail)}</div>` : "";
    return { look: c.glanceLook, open: "home", html: `<div class="kw-in" style="align-items:center;padding:12px">${wFace(c.glanceMood, c.glanceLook, "width:64px;height:64px;flex:none")}
      <div style="flex:1;min-width:0;margin-left:8px"><div class="kw-row">${g.connected ? "" : wOffline(16).replace("<svg", '<svg style="margin-right:6px"')}<span class="kw-name kw-ell" style="flex:1" data-k="headline">${esc(g.headline)}</span>${g.waiting > 0 ? `<span style="margin-left:6px">${wBadge(g.waiting)}</span>` : ""}</div>${detail}${lines}</div></div>` };
  },
  /** SoloWidget (2x2): Kenai over half the card, the top agent with its dot and state word, or the headline with an icon. */
  solo(c, w, h) {
    const size = sizeOf(w, h, [100, 100], [200, 200]);
    const top = c.top;
    const nameRow = top
      ? `<div class="kw-row">${wDot(W_TONE[top.tone].c)}<span class="kw-ell" style="margin-left:6px;font-size:${size === "LARGE" ? 20 : 16}px;font-weight:700">${esc(top.name)}</span></div>`
      : `<div class="kw-row">${c.connected ? wDot(W_TONE[c.look].c, 18) : wOffline(18)}<span class="kw-note kw-ell" style="margin-left:6px">${esc(c.headline)}</span></div>`;
    const stateLine = size === "SMALL" && top ? "" : `<div class="kw-ell" style="width:100%;text-align:center;font-size:${size === "LARGE" ? 15 : 13}px;color:var(--w-sec)">${esc(top ? top.word : c.emptyLine || "")}</div>`;
    const badge = c.waiting > 0 ? `<span style="position:absolute;top:10px;right:10px">${wBadge(c.waiting)}</span>` : "";
    return { look: c.look, open: toSession(top), html: `<div class="kw-in kw-col" style="padding:10px">${wFace(c.mood, c.look, "flex:1;width:100%")}${nameRow}${stateLine}</div>${badge}` };
  },
  /** ListWidget (4x3): the headline and a badge, then a row per agent; with no agent a centred empty state. */
  list(c, w, h) {
    const pad = "padding:10px 14px";
    if (!c.tiles.length) {
      return { look: c.look, open: "home", html: `<div class="kw-in kw-col" style="${pad};justify-content:center">${wFace(c.mood, c.look, "flex:1;width:100%")}
        <div class="kw-row">${c.connected ? wDot(W_TONE[c.look].c, 18) : wOffline(18, W_TONE[c.look].c)}<span class="kw-note kw-ell" style="margin-left:6px">${esc(c.headline)}</span></div><div class="kw-state kw-ell" style="width:100%;text-align:center">${esc(c.emptyLine)}</div></div>` };
    }
    const size = sizeOf(w, h, [0, 192], [0, 290]);
    const shown = size === "SMALL" ? 2 : size === "MEDIUM" ? 3 : 5;
    const rows = c.tiles.slice(0, shown).map((t) => wBtn("session:" + t.pill,
      `<span style="width:3px;height:28px;border-radius:2px;flex:none;background:${W_TONE[t.tone].c}"></span>${wFace(t.state, null, "width:36px;height:36px;flex:none;margin-left:8px")}
      <span style="flex:1;min-width:0;margin-left:10px;display:flex;flex-direction:column"><span class="kw-name kw-ell">${esc(t.name)}</span><span class="kw-state kw-ell">${esc(t.word)}</span></span>${wDot(W_TONE[t.tone].c, 10, "margin-left:8px")}`,
      "kw-lrow", `${t.name}, ${t.word}`)).join("");
    return { look: c.look, open: "home", html: `<div class="kw-in" style="${pad};flex-direction:column"><div class="kw-row" style="height:28px">${wDot(W_TONE[c.look].c)}<span class="kw-name kw-ell" style="flex:1;margin-left:8px">${esc(c.headline)}</span>${c.waiting > 0 ? `<span style="margin-left:8px">${wBadge(c.waiting)}</span>` : ""}</div>${rows}</div>` };
  },
  /** CharacterBigWidget (4x4): Kenai and the top agent on the left, plan usage on the right, buttons below. */
  big(c, w, h) {
    const size = sizeOf(w, h, [0, 290], [0, 400]);
    const top = c.top;
    let who = "";
    if (top) who = `<div class="kw-row">${wDot(W_TONE[top.tone].c)}<span class="kw-name kw-ell" style="margin-left:6px">${esc(top.name)}</span></div>`;
    else if (!c.connected) who = `<div class="kw-row">${wOffline(18).replace("<svg", '<svg style="margin-right:2px"')}<span class="kw-name kw-ell" style="margin-left:6px">${esc(c.headline)}</span></div>`;
    const left = `<div style="flex:45;min-width:0;display:flex;flex-direction:column;align-items:center">${wFace(c.mood, c.look, "flex:1;width:100%")}${who}<div class="kw-state kw-ell" style="width:100%;text-align:center">${esc(top ? top.word : c.emptyLine || "")}</div></div>`;
    let right;
    if (c.usageSide === "ROWS") {
      const rows = (size === "SMALL" ? c.usageRows.slice(0, 1) : c.usageRows).map((r, i) => `<div style="margin-top:${i ? 8 : 0}px" aria-label="${esc(fmt("widget_ring_cd", r.plan, r.shown))}">
        <div class="kw-sec kw-ell">${esc(r.label)}</div><div style="font-size:${r.near ? 32 : 18}px;font-weight:${r.near ? 700 : 400}">${r.shown}%</div>${r.note ? `<div class="kw-ell" style="font-size:12px;color:var(--w-soft)">${esc(r.note)}</div>` : ""}<div class="kw-bar"><i style="width:${r.shown}%;background:${W_USAGE[r.tone].c}"></i></div></div>`).join("");
      right = wBtn("home", rows, "kw-right", S.widget_ring_label, "flex:55;margin-left:8px");
    } else {
      const off = c.usageSide === "OFF";
      right = wBtn(off ? "screen:SETTINGS" : "home", `<span class="kw-note" style="display:block">${esc(off ? S.widget_big_usage_off : S.widget_big_not_connected)}</span><span class="kw-sec" style="display:block;margin-top:2px">${esc(off ? S.widget_big_usage_off_line : c.emptyLine || "")}</span>`, "kw-right", off ? S.widget_big_usage_off : S.widget_big_not_connected, "flex:55;margin-left:8px");
    }
    const B = [["home", "screen:HOME", S.widget_btn_home, S.widget_btn_home_cd], ["chat", "screen:CHAT", S.widget_btn_chat, S.widget_btn_chat_cd], ["settings", "screen:SETTINGS", S.widget_btn_settings, S.widget_btn_settings_cd],
      ["recent", "screen:HISTORY", S.widget_btn_recent, S.widget_btn_recent_cd], ["session", top ? "session:" + top.pill : "screen:HOME", S.widget_btn_session, S.widget_btn_session_cd], ["pair", "screen:SCAN", S.widget_btn_pair, S.widget_btn_pair_cd]];
    const tile = (b) => wBtn(b[1], `${wIcon(b[0], 22)}<span class="kw-ell" style="max-width:100%">${esc(b[2])}</span>`, "kw-tile" + (c.look === "ATTENTION" ? " att" : ""), b[3]);
    const rowOf = (list, mt = 0) => `<div style="display:flex;margin-top:${mt}px">${list.map(tile).join("")}</div>`;
    const buttons = size === "SMALL" ? "" : `<div style="margin-top:8px">${size === "LARGE" ? rowOf(B.slice(0, 3)) + rowOf(B.slice(3), 6) : rowOf(B.slice(0, 4))}</div>`;
    const badge = c.waiting > 0 ? `<span style="position:absolute;top:12px;right:12px">${wBadge(c.waiting)}</span>` : "";
    return { look: c.look, open: toSession(top), html: `<div class="kw-in" style="flex-direction:column;padding:12px"><div style="flex:1;min-height:0;display:flex">${left}${right}</div>${buttons}</div>${badge}` };
  },
  /** StatusPillWidget (4x1): dot, name and state word, then four round buttons. */
  pill(c, w, h) {
    const size = sizeOf(w, h, [290, 0], [360, 0]);
    const top = c.top;
    const name = top ? top.name : c.headline;
    const state = top ? top.word : c.emptyLine || "";
    const dot = !top && !c.connected ? wOffline(10, W_TONE[c.look].c) : wDot(W_TONE[top ? top.tone : c.look].c);
    const B = [["home", "screen:HOME", S.widget_btn_home_cd], ["chat", "screen:CHAT", S.widget_btn_chat_cd], ["settings", "screen:SETTINGS", S.widget_btn_settings_cd], ["recent", "screen:HISTORY", S.widget_btn_recent_cd]];
    return { look: c.look, open: toSession(top), label: fmt("widget_pill_cd", name, state), html: `<div class="kw-in" style="align-items:center;padding:0 8px 0 16px"><div class="kw-row" style="flex:1">${dot}
      ${size === "SMALL" ? "" : `<span class="kw-name kw-ell" style="margin-left:8px;flex:0 1 auto">${esc(name)}</span>`}${size === "LARGE" ? `<span class="kw-state kw-ell" style="margin-left:8px;flex:1 1 0">${esc(state)}</span>` : ""}</div>
      ${B.map((b) => wBtn(b[1], wIcon(b[0], 20), "kw-rb", b[2])).join("")}</div>` };
  },
  /** WaitingAlertWidget (2x2): calm, or "N waiting" in orange with Kenai anxious. */
  alert(c) {
    const waiting = c.waiting > 0;
    return { look: c.look, open: waiting ? "approval" : "home", html: `<div class="kw-in kw-col" style="padding:10px;justify-content:center">${wFace(c.mood, c.look, "flex:1;width:100%")}${c.connected ? "" : wOffline(18)}
      ${waiting ? `<div class="kw-ell" style="width:100%;text-align:center;font-size:20px;font-weight:700;color:var(--w-att-text)" data-k="alert">${esc(c.alertLine)}</div>` : `<div class="kw-name kw-ell" style="width:100%;text-align:center" data-k="alert">${esc(c.alertLine)}</div>`}
      <div class="kw-state kw-ell" style="width:100%;text-align:center" data-k="alertsub">${esc(c.alertSub)}</div></div>` };
  },
  /** UsageRingWidget (2x2): the ring and its percentage in the usage palette, or how to turn plan usage on. */
  ring(c) {
    const r = c.ring;
    if (!r) {
      return { look: c.connected ? "IDLE" : "MUTED", open: "home", label: S.widget_ring_label, html: `<div class="kw-in kw-col" style="padding:10px;justify-content:center">${wFace("idle", null, "width:44px;height:44px;flex:none")}
        <div class="kw-note" style="margin-top:6px;text-align:center;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden" data-k="ring">${esc(c.connected ? S.widget_usage_off : c.emptyLine)}</div></div>` };
    }
    const R = 44;
    const len = 2 * Math.PI * R;
    const arc = r.shown > 0 ? `<circle cx="50" cy="50" r="${R}" fill="none" stroke="${W_USAGE[r.tone].c}" stroke-width="12" stroke-linecap="round" stroke-dasharray="${(len * r.shown) / 100} ${len}" transform="rotate(-90 50 50)"/>` : "";
    return { usage: r.tone, open: "home", label: fmt("widget_ring_cd", r.plan, r.shown), html: `<div class="kw-in kw-col" style="padding:10px;justify-content:center">
      <div style="position:relative;flex:1;min-height:0;width:100%"><svg viewBox="0 0 100 100" style="position:absolute;inset:0;width:100%;height:100%" aria-hidden="true"><circle cx="50" cy="50" r="${R}" fill="none" stroke="rgba(128,128,128,.25)" stroke-width="12"/>${arc}</svg>
      <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:${r.near ? 26 : 18}px;font-weight:${r.near ? 700 : 400}" data-k="ring">${r.shown}%</div></div>
      <div class="kw-state kw-ell" style="width:100%;text-align:center">${esc(r.label)}</div>${r.note ? `<div class="kw-ell" style="width:100%;text-align:center;font-size:12px;color:var(--w-soft)">${esc(r.note)}</div>` : ""}</div>` };
  },
  /** TodayWidget (2x2): today's finished tasks, Kenai pleased once there is one. */
  today(c) {
    return { look: c.todayLook, open: "home", label: fmt("widget_today_cd", c.today), html: `<div class="kw-in kw-col" style="padding:10px;justify-content:center">${wFace(c.todayMood, c.todayLook, "flex:1;width:100%")}
      <div style="font-size:34px;font-weight:700;line-height:1.1" data-k="today">${c.today}</div><div class="kw-state">${esc(S.widget_today_unit)}</div></div>` };
  },
  /** MoodWidget (1x1): only Kenai's face, with his glow; the "not connected" mark in the corner. */
  mood(c) {
    return { look: c.look, open: toSession(c.top), html: `<div class="kw-in" style="padding:4px">${wFace(c.mood, c.look, "flex:1")}</div>${c.connected ? "" : `<span style="position:absolute;right:3px;bottom:3px">${wOffline(14)}</span>`}` };
  },
};
