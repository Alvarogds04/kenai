"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* Building blocks, after ui/DesignSystem.kt, ui/DesignControls.kt, ui/DesignIcons.kt, character/KodaView.kt, character/CharacterView.kt and character/CharacterPainter.kt. */

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const attr = (o) => Object.entries(o).filter(([, v]) => v !== undefined && v !== null && v !== false).map(([k, v]) => (v === true ? k : `${k}="${esc(v)}"`)).join(" ");

function icon(kind, size = 24, color = "currentColor") {
  return `<svg class="ic" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON[kind]}</svg>`;
}

/**
 * KodaView / CharacterView: the 32x32 sprite at a whole-number scale, centred in a box of [box] px. [kind] is a state or an
 * emote; the pose follows it (KodaPose.of). o.glow: the agent colour as a soft light behind Koda (CharacterPainter,
 * AGENT_GLOW_ALPHA .38). o.still: the first frame only, as a widget (CharacterBitmap). box "fit": the largest whole scale the
 * parent allows, measured when painted. No label: hidden from the screen reader, as a CharacterView without a description.
 */
function kodaEl(kind, box, label, o = {}) {
  const fit = box === "fit";
  const scale = fit ? 1 : Math.max(1, Math.floor(box / 32));
  const lbl = label ? `role="img" aria-label="${esc(label)}"` : 'aria-hidden="true"';
  const size = fit ? "width:100%;height:100%" : `width:${box}px;height:${box}px`;
  const glow = o.glow ? `;background:radial-gradient(closest-side circle,${rgbaOf(o.glow, 0.38)},rgba(0,0,0,0))` : "";
  return `<span class="koda" style="${size}${glow}" ${lbl}><i data-pose="${poseOf(kind)}" data-scale="${scale}"${fit ? " data-fit" : ""}${o.still ? " data-still" : ""} style="--s:${32 * scale}px"></i></span>`;
}
/** "#RRGGBB" or "rgb(r,g,b)" with an alpha. */
function rgbaOf(c, a) {
  if (c[0] === "#") { const n = parseInt(c.slice(1), 16); return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`; }
  return c.replace(/^rgb\(([^)]*)\)$/, `rgba($1,${a})`);
}
let kodaTick = 0;
function paintKoda(root) {
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  (root || document).querySelectorAll("i[data-pose]").forEach((el) => {
    const st = KODA.seq[el.dataset.pose];
    if ("fit" in el.dataset) {
      const box = el.parentElement;
      const s = Math.max(1, Math.floor(Math.min(box.clientWidth, box.clientHeight) / 32));
      if (s !== +el.dataset.scale) { el.dataset.scale = s; el.style.setProperty("--s", 32 * s + "px"); el.style.backgroundSize = ""; }
    }
    const s = +el.dataset.scale;
    const f = reduce || "still" in el.dataset ? st.seq[0] : st.seq[kodaTick % st.seq.length];
    if (!el.style.backgroundImage) el.style.backgroundImage = `url(${KODA.s32})`;
    if (!el.style.backgroundSize) el.style.backgroundSize = `${128 * s}px ${224 * s}px`;
    el.style.backgroundPosition = `-${f * 32 * s}px -${st.row * 32 * s}px`;
  });
}
setInterval(() => { kodaTick++; paintKoda(); }, 125); // fps 8, as KodaPose

/** The launcher icon (mipmap-anydpi/ic_launcher.xml): Koda's face on black, the adaptive icon's visible circle. */
const launcherIcon = (size) =>
  `<svg class="lnch" width="${size}" height="${size}" viewBox="18 18 72 72" shape-rendering="crispEdges" aria-hidden="true"><circle cx="54" cy="54" r="36" fill="#000"/>${LAUNCHER_FG}</svg>`;

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
