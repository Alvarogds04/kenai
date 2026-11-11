"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* Building blocks, after ui/DesignSystem.kt, ui/DesignControls.kt, ui/DesignIcons.kt, character/KodaView.kt and character/CharacterPainter.kt. */

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const attr = (o) => Object.entries(o).filter(([, v]) => v !== undefined && v !== null && v !== false).map(([k, v]) => (v === true ? k : `${k}="${esc(v)}"`)).join(" ");

function icon(kind, size = 24, color = "currentColor") {
  return `<svg class="ic" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON[kind]}</svg>`;
}

/** KodaView: the 32x32 sprite at a whole-number scale, centred in a box of [box] px. The pose follows the state. */
function kodaEl(state, box, label) {
  const scale = Math.max(1, Math.floor(box / 32));
  const pose = poseOf(state);
  const lbl = label ? `role="img" aria-label="${esc(label)}"` : 'aria-hidden="true"';
  return `<span class="koda" style="width:${box}px;height:${box}px" ${lbl}><i data-pose="${pose}" data-scale="${scale}" style="--s:${32 * scale}px"></i></span>`;
}
let kodaTick = 0;
function paintKoda(root) {
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  (root || document).querySelectorAll("i[data-pose]").forEach((el) => {
    const st = KODA.seq[el.dataset.pose];
    const s = +el.dataset.scale;
    const f = reduce ? st.seq[0] : st.seq[kodaTick % st.seq.length];
    if (!el.style.backgroundImage) { el.style.backgroundImage = `url(${KODA.s32})`; el.style.backgroundSize = `${128 * s}px ${224 * s}px`; }
    el.style.backgroundPosition = `-${f * 32 * s}px -${st.row * 32 * s}px`;
  });
}
setInterval(() => { kodaTick++; paintKoda(); }, 125); // fps 8, as KodaPose

/* CharacterPainter: the clay body (Kenai's green, or the agent's colour lightened and darkened), two eyes, a soft shadow. Approximated. */
function hexMix(hex, to, t) {
  const n = parseInt(hex.slice(1), 16);
  const c = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((v) => Math.round(v + (to - v) * t));
  return `rgb(${c.join(",")})`;
}
let clayId = 0;
function clay(kind, size, color) {
  const id = "cg" + ++clayId;
  const skin = color ? [hexMix(color, 255, 0.22), color, hexMix(color, 0, 0.14)] : ["#ACDD96", "#8FCB78", "#78B562"];
  const E = "#0F1D0B";
  let eyes = `<rect x="70" y="92" width="14" height="22" rx="7" fill="${E}"/><rect x="116" y="92" width="14" height="22" rx="7" fill="${E}"/>`;
  let extra = "";
  const closed = `<path d="M68 104 Q77 110 86 104 M114 104 Q123 110 132 104" stroke="${E}" stroke-width="5" fill="none" stroke-linecap="round"/>`;
  const smile = `<path d="M68 106 Q77 96 86 106 M114 106 Q123 96 132 106" stroke="${E}" stroke-width="5" fill="none" stroke-linecap="round"/>`;
  if (kind === "sleeping" || kind === "yawn") {
    eyes = closed;
    if (kind === "sleeping") extra = `<text class="z" x="142" y="70" font-size="18" font-weight="700" fill="#F5F6F8">z</text><text class="z b" x="152" y="56" font-size="13" font-weight="700" fill="#F5F6F8">z</text>`;
    else extra = `<ellipse cx="100" cy="130" rx="8" ry="10" fill="${E}"/>`;
  } else if (kind === "finished" || kind === "happy" || kind === "proud") eyes = smile;
  else if (kind === "wink") eyes = `<rect x="70" y="92" width="14" height="22" rx="7" fill="${E}"/><path d="M114 104 Q123 110 132 104" stroke="${E}" stroke-width="5" fill="none" stroke-linecap="round"/>`;
  else if (kind === "love") eyes = `<path d="M77 112 L68 102 A5 5 0 0 1 77 96 A5 5 0 0 1 86 102Z M123 112 L114 102 A5 5 0 0 1 123 96 A5 5 0 0 1 132 102Z" fill="#F4505E"/>`;
  else if (kind === "surprised") eyes = `<circle cx="77" cy="103" r="10" fill="${E}"/><circle cx="123" cy="103" r="10" fill="${E}"/>`;
  else if (kind === "error" || kind === "annoyed") eyes = `<rect x="70" y="100" width="14" height="12" rx="5" fill="${E}"/><rect x="116" y="100" width="14" height="12" rx="5" fill="${E}"/><path d="M66 94 L88 99 M134 94 L112 99" stroke="${E}" stroke-width="4" stroke-linecap="round"/>`;
  else if (kind === "dizzy") eyes = `<path d="M70 96 L84 110 M84 96 L70 110 M116 96 L130 110 M130 96 L116 110" stroke="${E}" stroke-width="4.5" stroke-linecap="round"/>`;
  else if (kind === "question") extra = `<text x="146" y="72" font-size="26" font-weight="700" fill="${STATE_COLOR.question}">?</text>`;
  else if (kind === "approval") extra = `<text x="148" y="72" font-size="26" font-weight="700" fill="${STATE_COLOR.approval}">!</text>`;
  const anim = ["approval", "question", "searching", "thinking", "working", "dizzy", "error"].includes(kind) ? "st-" + kind : kind === "happy" ? "em-happy" : "";
  return `<svg class="clay blink ${anim}" width="${size}" height="${size}" viewBox="0 0 200 200" aria-hidden="true">
<defs><radialGradient id="${id}" cx="40%" cy="32%" r="75%"><stop offset="0" stop-color="${skin[0]}"/><stop offset=".55" stop-color="${skin[1]}"/><stop offset="1" stop-color="${skin[2]}"/></radialGradient></defs>
<ellipse cx="100" cy="182" rx="56" ry="7" fill="rgba(45,74,34,.25)"/>
<g class="cbody"><path d="M100 50C150 50 176 96 176 138C176 168 148 176 100 176C52 176 24 168 24 138C24 96 50 50 100 50Z" fill="url(#${id})"/>
<g class="eyes">${eyes}</g>${extra}</g></svg>`;
}

/* PillButton: PRIMARY / SECONDARY / DANGER, onDark for the always-dark sheets. */
function pill(label, act, o = {}) {
  const cls = ["pb", o.kind || "", o.dark ? "dk" : "", o.w ? "wide" : ""].join(" ");
  const ic = o.icon ? icon(o.icon, o.iconSize || 20, o.kind === "pri" ? (o.dark ? "#0B0C0E" : "var(--onpri)") : "currentColor") : "";
  return `<button type="button" class="${cls}" ${attr({ "data-act": act, "data-arg": o.arg, disabled: o.disabled, style: o.style, "aria-label": o.aria })}>${ic}<span>${esc(label)}</span></button>`;
}
const panel = (inner, wash, style) => `<div class="panel" ${style ? `style="${style}"` : ""}>${wash ? `<div class="wash" style="--wash:${wash};opacity:.5"></div>` : ""}${inner}</div>`;
const divider = () => '<div class="div"></div>';
const heading = (t) => `<div class="sh ell">${esc(t)}</div>`;
const dot = (c, style = "") => `<span class="dot" style="background:${c};${style}"></span>`;
function listRow(title, o = {}) {
  const tag = o.act ? "button" : "div";
  return `<${tag} ${o.act ? 'type="button"' : ""} class="lr" ${attr({ "data-act": o.act, "data-arg": o.arg })}><span class="lr-t"><b>${esc(title)}</b>${o.hint ? `<span>${esc(o.hint)}</span>` : ""}</span>${o.trailing || ""}</${tag}>`;
}
const chevron = () => icon("CHEVRON", 20, "var(--dim)");
/** KenaiSwitch: 52 x 30 track, the "online" colour when on. */
const switchEl = (on, act, label, disabled) =>
  `<button type="button" role="switch" class="sw ${on ? "on" : ""}" ${attr({ "aria-checked": String(!!on), "aria-label": label, "data-act": act, disabled })}><i></i></button>`;
/** ScreenTitle: tabs get the plain title; sub-pages get a text "Back" button first. */
function screenTitle(title, backAct, trailing = "") {
  if (!backAct) return `<div class="st-root"><h1 class="t-title ell">${esc(title)}</h1>${trailing}</div>`;
  return `<div class="st-back"><button type="button" class="tbtn" data-act="${backAct}">${esc(S.action_back)}</button><h1 class="ell">${esc(title)}</h1>${trailing}</div>`;
}
/** MessagePanel: a notice with Close. */
const messagePanel = (text) =>
  panel(`<div style="display:flex;align-items:center;padding-left:16px"><p class="t-body" style="flex:1;padding:12px 0">${esc(text)}</p><button type="button" class="tlink" style="color:var(--dim)" data-act="closeMessage">${esc(S.action_close)}</button></div>`);
