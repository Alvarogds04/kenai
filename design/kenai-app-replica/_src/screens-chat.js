"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* ui/ChatScreen.kt, core/ChatTab.kt, core/MarkdownLite.kt (bold, inline code, bullets, fenced code). Answers are fake. */

const CHAT_MAX = 4000; // Protocol.CHAT_MAX_TEXT
const STATE_TITLE = { NOT_PAIRED: "chat_off_not_paired_title", NOT_CONNECTED: "chat_off_offline_title", CHAT_OFF: "chat_off_title", NO_MODELS: "chat_nomodel_title", READY: "chat_title" };
const STATE_BODY = { NOT_PAIRED: "chat_off_not_paired_body", NOT_CONNECTED: "chat_off_offline_body", CHAT_OFF: "chat_off_hint", NO_MODELS: "chat_off_hint", READY: "chat_title" };

function mdSpans(t) {
  return esc(t).replace(/`([^`]+)`/g, '<code style="font-family:var(--mono);background:var(--panel2);border-radius:4px;padding:0 3px">$1</code>').replace(/\*\*([^*]+)\*\*/g, "<b>$1</b>");
}
function markdown(text) {
  const out = [];
  const lines = text.split("\n");
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    if (l.startsWith("```")) {
      const code = [];
      for (i++; i < lines.length && !lines[i].startsWith("```"); i++) code.push(lines[i]);
      out.push(`<pre class="t-mono" style="margin:0;background:var(--panel2);border:1px solid var(--line);border-radius:12px;padding:12px;white-space:pre-wrap">${esc(code.join("\n"))}</pre>`);
    } else if (/^[-*] /.test(l)) out.push(`<div class="t-body" style="display:flex"><span>•&nbsp;&nbsp;</span><span style="flex:1">${mdSpans(l.slice(2))}</span></div>`);
    else if (l.trim()) out.push(`<p class="t-body">${mdSpans(l)}</p>`);
  }
  return `<div style="display:flex;flex-direction:column;gap:8px">${out.join("")}</div>`;
}
const typingDots = () => '<div class="typing" style="display:flex;gap:6px;padding:8px 0"><i></i><i></i><i></i></div>';

function bubble(m) {
  if (m.role === "user") return `<div style="display:flex;justify-content:flex-end"><p class="t-body" style="max-width:300px;background:var(--panel2);border-radius:18px 18px 6px 18px;padding:10px 14px;white-space:pre-wrap;word-break:break-word">${esc(m.text)}</p></div>`;
  let h = "";
  if (!m.text && m.status === "streaming") h = typingDots();
  else if (m.text) h = markdown(m.text);
  if (m.status === "failed") h += `<p class="t-sec dang">${esc(S.chat_err_canceled)}</p>`;
  return `<div style="display:flex;flex-direction:column;gap:6px">${h}</div>`;
}

function chatEmpty(state) {
  const btn = state === "NOT_PAIRED" ? pill(S.chat_go_pair, "tab", { kind: "pri", w: true, arg: "HOME" }) : state === "NOT_CONNECTED" ? pill(S.chat_go_settings, "tab", { kind: "pri", w: true, arg: "SETTINGS" }) : "";
  return `<div class="gut" style="height:100%;display:flex;flex-direction:column">${screenTitle(S.chat_title)}
    <div style="flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:0 8px">${kodaEl("sleeping", 120)}
    <p class="t-title center" style="padding-top:16px">${esc(S[STATE_TITLE[state]])}</p><p class="t-body dim center" style="padding:8px 0 20px">${esc(S[STATE_BODY[state]])}</p>${btn}</div></div>`;
}

function chatScreen() {
  const c = st.chat;
  const tab = chatTabState();
  if (tab !== "READY" && !c.messages.length) return chatEmpty(tab);
  const clear = c.messages.length ? `<button type="button" class="tlink" data-act="chatClearAsk">${esc(S.chat_clear)}</button>` : "";
  const reason = !chatAvailable() ? panel(`<div style="padding:16px;display:flex;flex-direction:column;gap:4px"><p class="t-head">${esc(S[STATE_TITLE[tab]])}</p><p class="t-sec dim">${esc(S[STATE_BODY[tab]])}</p></div>`, null, "margin:4px 0") : "";
  const list = c.messages.length
    ? `<div class="scr" id="chatlist" style="position:static;height:100%;display:flex;flex-direction:column;gap:12px">${c.messages.map(bubble).join("")}</div>`
    : `<div style="height:100%;display:flex;align-items:center;justify-content:center"><p class="t-body dim center" style="padding:16px">${esc(S.chat_empty)}</p></div>`;
  const len = c.text.trim().length;
  const canSend = chatAvailable() && len >= 1 && len <= CHAT_MAX;
  const cur = FAKE.models.find((m) => m.id === c.model);
  let note = "";
  if (c.refusal === "BUSY") note = `<p class="t-sec dang">${esc(S.chat_ref_busy)}</p>`;
  else if (len > CHAT_MAX) note = `<p class="t-sec dang">${esc(S.chat_ref_too_long)}</p>`;
  else if (len > CHAT_MAX * 0.9) note = `<p class="t-sec dim">${esc(fmt("chat_count", len, CHAT_MAX))}</p>`;
  const action = c.busy
    ? `<button type="button" class="pb" style="height:56px" data-act="chatStop"><span>${esc(S.chat_stop)}</span></button>`
    : `<button type="button" aria-label="${esc(S.chat_send)}" data-act="chatSend" ${canSend ? "" : "disabled"} style="width:56px;height:56px;border-radius:50%;background:var(--pri);display:flex;align-items:center;justify-content:center;flex:none;opacity:${canSend ? 1 : 0.4}">${icon("SEND", 22, "var(--onpri)")}</button>`;
  const menu = c.menu ? `<div class="menu" style="left:16px;bottom:120px">${FAKE.models.map((m) => `<button type="button" data-act="chatModel" data-arg="${m.id}">${esc(m.label)}</button>`).join("")}</div>` : "";
  return `<div class="gut" style="height:100%;display:flex;flex-direction:column">${screenTitle(S.chat_title, null, clear)}
    <p class="t-sec dim" style="padding:4px">${esc(S.chat_note)}</p>${reason}
    <div style="flex:1;min-height:0;position:relative">${list}</div>
    <div style="display:flex;flex-direction:column;gap:8px;padding:8px 0">
      <button type="button" class="chip" style="min-height:48px;padding:0 14px;gap:6px;align-self:flex-start;background:var(--panel2)" data-act="chatMenu"><span class="ell">${esc(fmt("chat_model", cur ? cur.label : "—"))}</span><span style="transform:rotate(90deg);display:flex">${icon("CHEVRON", 16, "var(--dim)")}</span></button>
      <div style="display:flex;align-items:flex-end;gap:8px"><textarea id="chatbox" rows="1" class="field" style="border-radius:26px;background:var(--panel);border-color:var(--line);padding:16px;font-size:15px;resize:none;max-height:120px" placeholder="${esc(S.chat_placeholder)}" data-input="chat" ${chatAvailable() ? "" : "disabled"}>${esc(c.text)}</textarea>${action}</div>
      ${note}</div>${menu}</div>`;
}
function afterScreen() {
  const l = document.getElementById("chatlist");
  if (l) l.scrollTop = l.scrollHeight; // follow the answer as it grows
}
INPUT.chat = (v) => {
  const before = st.chat.text.trim().length;
  st.chat.text = v; st.chat.refusal = null;
  const after = v.trim().length;
  if ((before === 0) !== (after === 0) || after > CHAT_MAX * 0.9 || before > CHAT_MAX * 0.9) changed();
};
ACT.chatMenu = () => { st.chat.menu = !st.chat.menu; changed(); };
ACT.chatModel = (id) => { st.chat.model = id; st.chat.menu = false; changed(); };
ACT.chatClearAsk = () => { st.chat.confirmClear = true; changed(); };
ACT.clearCancel = () => { st.chat.confirmClear = false; changed(); };
ACT.clearOk = () => { st.chat.confirmClear = false; st.chat.messages = []; changed(); };
let chatTimer = null;
ACT.chatSend = () => {
  const c = st.chat;
  if (c.busy) { c.refusal = "BUSY"; changed(); return; }
  const text = c.text.trim();
  if (!text || !chatAvailable()) return;
  c.messages.push({ role: "user", text });
  const answer = { role: "assistant", text: "", status: "streaming" };
  c.messages.push(answer);
  c.text = ""; c.busy = true;
  changed();
  // A fake stream: the dots, then the answer a few words at a time.
  const words = FAKE.reply.split(/(?<=\s)/);
  let i = 0;
  clearInterval(chatTimer);
  setTimeout(() => {
    chatTimer = setInterval(() => {
      if (answer.status !== "streaming") { clearInterval(chatTimer); return; }
      answer.text += words.slice(i, i + 3).join(""); i += 3;
      if (i >= words.length) { answer.status = "done"; c.busy = false; clearInterval(chatTimer); }
      changed();
    }, 90);
  }, 700);
};
ACT.chatStop = () => {
  const last = st.chat.messages[st.chat.messages.length - 1];
  if (last && last.status === "streaming") last.status = "failed";
  st.chat.busy = false; clearInterval(chatTimer); changed();
};
