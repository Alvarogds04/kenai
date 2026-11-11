"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* What the widgets draw, as plain data: a port of core/Glance.kt (GlanceBuilder), core/WidgetData.kt (WidgetTone, UsageTone,
   PlanUsageLook, WidgetContent, WidgetData.build) and AppModel.glance()/widgetContent(), read from window.kenai only.
   The English texts below are the Kotlin literals of those two files, verbatim (they are not in strings.xml). */

/* WidgetTone (res/values/colors.xml widget_tone_*, widget_tint_*, widget_border_*): the one palette of every widget. */
const W_TONE = {
  MUTED: { c: "#94A2B8", tint: "rgba(148,162,184,.15)", border: "rgba(148,162,184,.6)" },
  IDLE: { c: "#B8BDC7", tint: "rgba(184,189,199,.15)", border: "rgba(184,189,199,.6)" },
  WORKING: { c: "#3B9EFF", tint: "rgba(59,158,255,.2)", border: "rgba(59,158,255,.6)" },
  ATTENTION: { c: "#F5A524", tint: "rgba(245,165,36,.25)", border: "rgba(245,165,36,.6)" },
  DONE: { c: "#34D499", tint: "rgba(52,212,153,.2)", border: "rgba(52,212,153,.6)" },
  TROUBLE: { c: "#F4505E", tint: "rgba(244,80,94,.2)", border: "rgba(244,80,94,.6)" },
};
/* UsageTone (widget_usage_*): teal, yellow from 75 %, red from 90 %. */
const W_USAGE = {
  CALM: { c: "#0E8F9A", tint: "rgba(14,143,154,.2)", border: "rgba(14,143,154,.6)" },
  CLOSE: { c: "#F2D024", tint: "rgba(242,208,36,.25)", border: "rgba(242,208,36,.6)" },
  LIMIT: { c: "#D92D20", tint: "rgba(217,45,32,.2)", border: "rgba(217,45,32,.6)" },
};
/** WidgetTone.of: the tone of one agent's state; a computer that is not connected is always muted. */
function toneOf(state, isConnected = true) {
  if (!isConnected) return "MUTED";
  if (state === "approval" || state === "question") return "ATTENTION";
  if (state === "working" || state === "thinking" || state === "searching") return "WORKING";
  if (state === "finished") return "DONE";
  if (state === "error" || state === "ratelimit" || state === "dizzy") return "TROUBLE";
  return "IDLE";
}

/* GlanceBuilder */
const G_ORDER = ["approval", "question", "error", "ratelimit", "dizzy", "working", "searching", "thinking", "finished", "sleeping", "idle"];
const G_BUSY = ["working", "thinking", "searching"];
const G_TROUBLE = ["error", "ratelimit", "dizzy"];
const rankOf = (state) => { const i = G_ORDER.indexOf(state); return i < 0 ? G_ORDER.length : i; };
/** GlanceBuilder.lineOf: the state in a few words, never the step text. */
function glanceLineOf(state) {
  return ({ approval: "Waiting for your approval", question: "Has a question", error: "Stopped on an error", ratelimit: "Hit a limit",
    dizzy: "Needs a look", finished: "Finished", sleeping: "Idle", idle: "Idle", thinking: "Thinking", searching: "Searching" })[state] || "Working";
}
/** AppModel.agentName: the session's agent, else its pill's name, else "An agent". */
const wNameOf = (list, pill) => ((list.find((s) => s.pill === pill) || {}).agent || (PILLS[pill] || {}).name || "An agent");

/**
 * GlanceBuilder.build with AppModel.glance()'s arguments: paired = mode != NONE (Not paired has no pairing), the link is
 * connected in Demo mode and in Paired mode with the link up, and the desktop's name is only known on a real computer
 * (AppModel.onWelcome ignores the demo's "Demo").
 */
function buildGlance(k) {
  const s = k.state;
  if (s.mode === "none") return { headline: "Not paired", detail: "Open Kenai to connect to your computer", connected: false, lines: [], waiting: 0 };
  if (!k.derived.connected()) return { headline: "Not connected", detail: "Kenai cannot reach your computer", connected: false, lines: [], waiting: 0 };
  const list = k.derived.sessions();
  const approvals = k.derived.approvals().length;
  const where = s.mode === "paired" ? DESKTOP.name : null;
  const ranked = [...list].sort((a, b) => rankOf(a.state) - rankOf(b.state));
  const lines = ranked.slice(0, 4).map((x) => ({ agent: wNameOf(list, x.pill), text: glanceLineOf(x.state), state: x.state }));
  const questions = list.filter((x) => x.state === "question").length;
  const waiting = approvals + questions;
  const busy = list.filter((x) => G_BUSY.includes(x.state)).length;
  const trouble = list.filter((x) => G_TROUBLE.includes(x.state)).length;
  const g = (headline, detail, w = 0) => ({ headline, detail, connected: true, lines, waiting: w });
  if (approvals > 0) return g("Waiting for you", approvals === 1 ? "1 request to approve" : `${approvals} requests to approve`, waiting);
  if (questions > 0) return g("Waiting for your answer", questions === 1 ? "1 question from an agent" : `${questions} questions from agents`, waiting);
  if (trouble > 0) return g("An agent needs a look", trouble === 1 ? "1 agent stopped" : `${trouble} agents stopped`);
  if (busy > 0) return g(busy === 1 ? `${wNameOf(list, ranked.find((x) => G_BUSY.includes(x.state)).pill)} is working` : `${busy} agents are working`, where ? `On ${where}` : "Working");
  if (list.some((x) => x.state === "finished")) return g("All done", where ? `On ${where}` : "Finished");
  return g("Connected", where ? `To ${where}` : "Nothing is running");
}

/* PlanUsageLook */
const usageTone = (pct) => (pct >= 90 ? "LIMIT" : pct >= 75 ? "CLOSE" : "CALM");
function resetLine(atMs, nowMs) {
  if (atMs <= 0 || atMs <= nowMs) return null;
  const at = new Date(atMs);
  const day = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((day(at) - day(new Date(nowMs))) / 864e5);
  const clock = `${String(at.getHours()).padStart(2, "0")}:${String(at.getMinutes()).padStart(2, "0")}`;
  if (days === 0) return `resets at ${clock}`;
  if (days < 7) return `resets ${at.toLocaleDateString("en-GB", { weekday: "short" })} ${clock}`;
  return `resets ${at.toLocaleDateString("en-GB", { month: "short" })} ${at.getDate()} ${clock}`;
}
/** WidgetData.row: one UsageRing (plan, window, pct, the "Near limit" note from the red line). */
function usageRow(plan, window, pct, resetsAtMs, nowMs) {
  const shown = Math.max(0, Math.min(100, pct));
  const near = shown >= 90;
  return { plan, window, shown, label: `${plan} · ${window}`, tone: usageTone(shown), near, note: near ? ["Near limit", resetLine(resetsAtMs, nowMs)].filter(Boolean).join(" · ") : null };
}
/**
 * The plan usage the computer sends (its switch on): only the fake desktop of "Paired (fake data)" sends it, from the same
 * FAKE.usage as the phone's Home. Demo mode sends none (DemoLink has no usage), so the ring asks to turn it on.
 */
function usageRowsNow(k, nowMs) {
  if (k.state.mode !== "paired" || !k.derived.connected()) return [];
  const u = FAKE.usage;
  return [["Claude", u.claude], ["Codex", u.codex]].filter(([, p]) => p && p.five).map(([name, p]) => usageRow(name, "5 h", p.five.pct, nowMs + p.five.resetMin * 60e3, nowMs));
}

/** WidgetData.build plus WidgetContent's derived values, for one picture of every widget. */
function buildContent(k, today) {
  const g = buildGlance(k);
  const list = g.connected ? k.derived.sessions() : [];
  const tiles = [...list].sort((a, b) => rankOf(a.state) - rankOf(b.state)).slice(0, 5)
    .map((x) => ({ pill: x.pill, name: wNameOf(list, x.pill), state: x.state, word: glanceLineOf(x.state), tone: toneOf(x.state) }));
  const rows = g.connected ? usageRowsNow(k, Date.now()) : [];
  const top = tiles[0] || null;
  const waiting = g.waiting;
  const c = { glance: g, headline: g.headline, connected: g.connected, waiting, tiles, top, usageRows: rows, ring: rows[0] || null, today };
  c.usageSide = !c.connected ? "NOT_CONNECTED" : rows.length ? "ROWS" : "OFF";
  c.emptyLine = tiles.length ? null : !c.connected ? "Waiting for your computer" : "No agents running";
  c.alertLine = !c.connected ? "Not connected" : waiting > 0 ? `${waiting} waiting` : "All calm";
  c.alertSub = !c.connected ? "Waiting for your PC" : waiting > 0 ? (top ? top.name : "") : "Nothing needs you";
  c.todayMood = today > 0 ? "finished" : "idle";
  c.todayLook = today > 0 ? "DONE" : "IDLE";
  c.look = !c.connected ? "MUTED" : waiting > 0 ? "ATTENTION" : top ? toneOf(top.state) : "IDLE";
  c.mood = top ? top.state : c.connected ? "idle" : "sleeping";
  // Glance.look() / Glance.mood(): the summary widget's own, from its lines (the same rule).
  c.glanceLook = !g.connected ? "MUTED" : g.waiting > 0 ? "ATTENTION" : g.lines[0] ? toneOf(g.lines[0].state) : "IDLE";
  c.glanceMood = g.lines[0] ? g.lines[0].state : g.connected ? "idle" : "sleeping";
  return c;
}

/**
 * The Today count (core/TodayCounter + AppModel.onSessions): a session moving into FINISHED from another state it was seen in
 * counts one. lastState is forgotten when the link stops (a mode change) and trimmed to the sessions present, as AppModel does.
 * As in the real app it counts on a paired computer only (AppModel.onSessions: mode == Mode.PAIRED), so Demo mode stays at 0.
 */
const todayCounter = {
  count: 0, last: new Map(), mode: null,
  observe(k) {
    if (k.state.mode !== this.mode) { this.mode = k.state.mode; this.last.clear(); }
    const list = k.derived.sessions();
    for (const s of list) {
      const prev = this.last.get(s.pill);
      this.last.set(s.pill, s.state);
      if (k.state.mode === "paired" && prev !== undefined && prev !== "finished" && s.state === "finished") this.count++;
    }
    const here = new Set(list.map((s) => s.pill));
    for (const p of [...this.last.keys()]) if (!here.has(p)) this.last.delete(p);
    return this.count;
  },
};
