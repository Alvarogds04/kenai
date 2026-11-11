// Generates the three improvement directions of design/kenai-mejoras/ (S8b, T12).
// Run from the repo root: node design/kenai-mejoras/_gen.mjs
// Reads design/_build/koda.json (Koda pixel-cat sprite, embedded as a data URL) and writes self-contained HTML.
// Colours: only design/system.md tokens. Copy: app/src/main/res/values-es/strings.xml where a string exists.
import fs from "node:fs";

const K = JSON.parse(fs.readFileSync(new URL("../_build/koda.json", import.meta.url)));
const OUT = new URL("./", import.meta.url);

/* ---------------- tokens (design/system.md) ---------------- */
const DARK = `--bg:#000;--card:#141518;--surface:#18181b;--tab-on:#1D1F23;--ink:#F5F6F8;--ink-2:#F1F2F4;--muted:#8E939C;
--primary-bg:#F5F6F8;--primary-ink:#0B0C0E;--btn:rgba(255,255,255,.09);--btn-h:rgba(255,255,255,.15);
--line:rgba(255,255,255,.25);--line-faint:rgba(255,255,255,.08);--fill:rgba(255,255,255,.06);--bubble:rgba(255,255,255,.1);
--veil:22%;--board:#18181b;--bezel:#1D1F23;--scrim:rgba(0,0,0,.6);`;
const LIGHT = `--bg:#ece4d2;--card:#f7f2e7;--surface:#fbf8f1;--tab-on:#e7e0d0;--ink:#1a1915;--ink-2:#2a2823;--muted:#5f5c54;
--primary-bg:#201f1b;--primary-ink:#f7f4ec;--btn:rgba(0,0,0,.06);--btn-h:rgba(0,0,0,.12);
--line:rgba(0,0,0,.28);--line-faint:rgba(0,0,0,.08);--fill:rgba(0,0,0,.05);--bubble:rgba(0,0,0,.07);
--veil:34%;--board:#fbf8f1;--bezel:#2a2823;--scrim:rgba(0,0,0,.38);`;

const CSS = `
:root{
  --go:#b8ff2e;--sun:#ffe100;--red-11:#ff5c75;--island-live:#2eb4ff;--faint:#6f6d66;
  --on-role:#0B0C0E;
  --id-koda:#8dff2b;--id-claude:#ff8a00;--id-codex:#2dffac;--id-gemini:#b04dff;
  --font-ui:ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif;
  --font-mono:"Cascadia Mono","Consolas",ui-monospace,monospace;
  --r-xl:12px;--r-2xl:18px;--r-full:999px;
  --ease-out:cubic-bezier(.23,1,.32,1);--spring:cubic-bezier(.25,.9,.35,1.03);
  --pw:360px;
}
[data-theme=dark]{${DARK}}
[data-theme=light]{${LIGHT}}
.keep-dark{${DARK}color:var(--ink)}
body[data-narrow]{--pw:320px}
*{box-sizing:border-box}
[hidden]{display:none!important}
html,body{margin:0}
body{width:1440px;height:900px;overflow:hidden;background:var(--board);color:var(--ink);font:14px/1.45 var(--font-ui);-webkit-font-smoothing:antialiased}
button{font:inherit;color:inherit;cursor:pointer;border:0;background:none;padding:0}
h1,h2,h3,p,ul,ol{margin:0}
ul,ol{padding:0;list-style:none}
:focus-visible{outline:2px solid var(--ink);outline-offset:2px}
.top{position:absolute;left:28px;right:28px;top:10px;height:60px;display:flex;align-items:center;gap:24px}
.top .tt{flex:1;min-width:0}
.top h1{font-size:18px;font-weight:700;letter-spacing:-.01em}
.top p{font-size:13px;color:var(--muted);margin-top:2px;max-width:820px}
.ctl{display:flex;gap:8px;flex:none}
.cbtn{min-height:40px;padding:0 14px;border-radius:var(--r-full);background:var(--btn);font-weight:600;font-size:13px;white-space:nowrap}
.cbtn:hover{background:var(--btn-h)}
main.row{position:absolute;left:0;right:0;top:76px;display:flex;justify-content:center;gap:16px;align-items:flex-start}
figure{margin:0}
figcaption{margin:0 0 6px 2px;font-size:12px;font-weight:600;color:var(--muted);text-transform:uppercase;letter-spacing:.04em;white-space:nowrap}
.better{position:absolute;left:28px;right:28px;bottom:10px;height:244px;overflow:hidden;padding:14px 20px;background:var(--card);border-radius:var(--r-2xl);box-shadow:inset 0 0 0 1px var(--line-faint)}
.better>h2{font-size:14px;font-weight:700;margin-bottom:10px}
.better .cols{display:grid;grid-template-columns:1fr 1fr 1fr 1.1fr;gap:22px}
.better h3{font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:.05em;margin-bottom:6px;display:flex;align-items:center;gap:8px}
.better h3 i{width:10px;height:10px;border-radius:3px;background:var(--c)}
.better li,.better p{font-size:12.5px;line-height:1.38;color:var(--ink-2)}
.better li{padding-left:12px;position:relative;margin-bottom:5px}
.better li::before{content:"";position:absolute;left:0;top:8px;width:4px;height:4px;border-radius:50%;background:var(--muted)}
.better .trade{padding-left:18px;box-shadow:inset 1px 0 0 var(--line-faint)}
.better .trade p+p{margin-top:6px}
/* ---------- phone ---------- */
.phone{position:relative;width:var(--pw);height:760px;zoom:.7;border:8px solid var(--bezel);border-radius:44px;background:var(--bg);overflow:hidden;display:flex;flex-direction:column;color:var(--ink)}
.sbar{flex:none;height:30px;display:flex;align-items:center;justify-content:space-between;padding:0 24px;font-size:13px;font-weight:600}
.sbar i{display:inline-block;width:12px;height:8px;border-radius:2px;background:var(--ink);margin-left:4px}
.gest{flex:none;height:20px;display:flex;align-items:center;justify-content:center}
.gest::after{content:"";width:110px;height:4px;border-radius:2px;background:var(--ink)}
.scr{position:relative;flex:1;min-height:0;display:flex;flex-direction:column}
.scroll{flex:1;min-height:0;overflow:hidden;padding:0 14px}
.stack{display:flex;flex-direction:column;gap:12px}
.stack>*,.scr>*{flex-shrink:0}
.scr>.scroll,.scr>.scr{flex-shrink:1}
.scroll{-webkit-mask-image:linear-gradient(#000 calc(100% - 28px),transparent);mask-image:linear-gradient(#000 calc(100% - 28px),transparent)}
/* ---------- roles ---------- */
[data-r=sun]{--c:var(--sun)}[data-r=live]{--c:var(--island-live)}[data-r=go]{--c:var(--go)}[data-r=red]{--c:var(--red-11)}[data-r=faint]{--c:var(--faint)}
.veil{background:linear-gradient(180deg,color-mix(in srgb,var(--c) var(--veil),var(--card)) 0%,var(--card) 88%)}
.chip{display:inline-flex;align-items:center;gap:6px;min-height:26px;padding:2px 10px 2px 8px;border-radius:var(--r-full);background:var(--fill);font-size:12.5px;font-weight:600;color:var(--ink);white-space:nowrap;box-shadow:inset 0 0 0 1px var(--line-faint)}
.chip i{width:8px;height:8px;border-radius:50%;background:var(--c);flex:none}
.chip.solid{background:var(--c);color:var(--on-role);box-shadow:none}
.chip.solid i{background:var(--on-role)}
.card{background:var(--card);border-radius:var(--r-2xl);box-shadow:inset 0 0 0 1px var(--line-faint)}
.btn{position:relative;display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:48px;padding:6px 18px;border-radius:var(--r-full);background:var(--btn);font-weight:600;font-size:15px;color:var(--ink);text-align:center;line-height:1.2}
.btn:hover{background:var(--btn-h)}
.btn.pri{background:var(--primary-bg);color:var(--primary-ink)}
.btn.go{background:var(--go);color:var(--on-role)}
.btn.ghost{background:transparent}
.btn.ghost:hover{background:var(--fill)}
.btn.sm{min-height:44px;padding:4px 14px;font-size:14px}
.btn.lg{min-height:56px;font-size:16px}
.btn.xl{min-height:60px;font-size:17px}
.btn.block{width:100%}
.btn[disabled]{cursor:default;color:var(--muted)}
.btn .ic{width:20px;height:20px}
.icbtn{width:48px;height:48px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;flex:none}
.icbtn:hover{background:var(--fill)}
.row2{display:flex;gap:10px}.row2>*{flex:1}
.ic{width:22px;height:22px;stroke:currentColor;fill:none;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round;flex:none}
.mono{font-family:var(--font-mono);font-size:13px}
.cmd{background:var(--surface);border-radius:var(--r-xl);padding:10px 12px;color:var(--ink-2);box-shadow:inset 0 0 0 1px var(--line-faint);word-break:break-all;line-height:1.45}
.muted{color:var(--muted)}
.small{font-size:13px}
.tiny{font-size:12px}
.sec{font-size:12px;font-weight:600;letter-spacing:.05em;text-transform:uppercase;color:var(--muted);margin:0 2px 8px}
.h{font-weight:700;letter-spacing:-.01em}
.dot{display:inline-block;width:10px;height:10px;border-radius:50%;flex:none;box-shadow:0 0 0 1px var(--line)}
.halo{position:relative;flex:none;display:inline-flex;align-items:center;justify-content:center;border-radius:50%;background:color-mix(in srgb,var(--c) 18%,var(--card));box-shadow:inset 0 0 0 2px var(--c)}
.halo>.dot{position:absolute;right:1px;bottom:1px;box-shadow:0 0 0 2px var(--card)}
.koda{display:inline-block;image-rendering:pixelated;background-repeat:no-repeat;flex:none}
.row{display:flex;align-items:center;gap:12px}
.t{flex:1;min-width:0}
.t>b{display:block;font-size:15px;font-weight:600}
.t>span{display:block;font-size:13px;color:var(--muted)}
.clip{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.item{display:flex;align-items:center;gap:12px;min-height:64px;padding:8px 12px}
/* nav */
.nav{flex:none;display:flex;gap:4px;margin:6px 14px 6px;padding:6px;border-radius:var(--r-full);background:var(--card);box-shadow:inset 0 0 0 1px var(--line-faint)}
.nav a{position:relative;flex:1;min-height:52px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;border-radius:var(--r-full);font-size:12px;font-weight:600;color:var(--muted);text-decoration:none}
.nav a[aria-current]{background:var(--tab-on);color:var(--ink)}
.badge{position:absolute;top:3px;left:calc(50% + 6px);min-width:20px;height:20px;padding:0 5px;border-radius:10px;background:var(--sun);color:var(--on-role);font-size:11.5px;font-weight:700;display:flex;align-items:center;justify-content:center}
/* island strip (stays dark in both themes, as the app's island) */
.island{flex:none;margin:4px 12px 10px;min-height:52px;padding:6px 8px 6px 8px;border-radius:26px;background:var(--bg);display:flex;align-items:center;gap:10px;box-shadow:inset 0 0 0 1.5px var(--c)}
.island .t>b{font-size:14px}
.island .t>span{font-size:12px}
/* sheets */
.scrim{position:absolute;inset:0;background:var(--scrim)}
.sheet{position:absolute;left:0;right:0;bottom:0;background:var(--surface);border-radius:28px 28px 0 0;padding:10px 18px 14px;box-shadow:inset 0 1px 0 var(--line-faint)}
.grab{width:36px;height:4px;border-radius:2px;background:var(--line);margin:0 auto 14px}
/* options (radio rows) */
.opt{display:flex;gap:12px;align-items:flex-start;width:100%;min-height:60px;padding:12px 14px;border-radius:16px;background:var(--card);box-shadow:inset 0 0 0 1px var(--line-faint);text-align:left}
.opt .mark{width:22px;height:22px;border-radius:50%;flex:none;box-shadow:inset 0 0 0 2px var(--line);margin-top:1px}
.opt[aria-checked=true]{box-shadow:inset 0 0 0 2px var(--ink)}
.opt[aria-checked=true] .mark{box-shadow:inset 0 0 0 7px var(--ink)}
.opt[role=checkbox] .mark{border-radius:6px}
.opt b{display:block;font-size:15px}
.opt span{display:block;font-size:13px;color:var(--muted)}
/* switches */
.sw{flex:none;position:relative;width:56px;height:44px;border-radius:22px}
.sw::before{content:"";position:absolute;left:2px;top:6px;width:52px;height:32px;border-radius:16px;background:var(--btn);box-shadow:inset 0 0 0 2px var(--line)}
.sw::after{content:"";position:absolute;left:8px;top:12px;width:20px;height:20px;border-radius:50%;background:var(--muted)}
.sw[aria-checked=true]::before{background:var(--go);box-shadow:none}
.sw[aria-checked=true]::after{left:28px;background:var(--on-role)}
.setrow{display:flex;gap:12px;align-items:center;min-height:60px;padding:10px 14px}
.setrow+.setrow{box-shadow:inset 0 1px 0 var(--line-faint)}
.bar{height:6px;border-radius:3px;background:var(--fill);overflow:hidden;box-shadow:inset 0 0 0 1px var(--line-faint)}
.bar>i{display:block;height:100%;border-radius:3px;background:var(--c)}
.spin{width:18px;height:18px;border-radius:50%;box-shadow:inset 0 0 0 2.5px var(--line);border-top:2.5px solid var(--c)}
@media (prefers-reduced-motion:no-preference){
  .spin{animation:spin .9s linear infinite}
  .bar>i{transition:width .9s linear}
  .sheetnav{transition:transform .3s var(--ease-out)}
  .fade{animation:fade .16s var(--ease-out)}
  .kbtn:active .koda{transform:scale(.96)}
  .kbtn .koda{transition:transform .12s var(--ease-out)}
}
@keyframes spin{to{transform:rotate(360deg)}}
@keyframes fade{from{opacity:0;transform:translateY(4px)}to{opacity:1;transform:none}}
`;

/* ---------------- Koda (design/_build/koda.json) ---------------- */
const LBL = { reposo: "durmiendo", escucha: "escuchando", piensa: "pensando", trabaja: "trabajando", espera: "esperando tu sí", termino: "terminó", error: "con un error" };
const kodaEl = (pose, scale = 2, attrs = "") =>
  `<span class="koda" data-koda="${pose}" data-scale="${scale}" role="img" aria-label="Koda, ${LBL[pose]}" style="width:${32 * scale}px;height:${32 * scale}px" ${attrs}></span>`;
const halo = (pose, r, size, scale, idc = "", attrs = "") =>
  `<span class="halo" data-r="${r}" style="width:${size}px;height:${size}px" ${attrs}>${kodaEl(pose, scale)}${idc ? `<span class="dot" style="background:var(--id-${idc})" aria-hidden="true"></span>` : ""}</span>`;

const BASE_JS = `
const KODA=${JSON.stringify(K)};
const LBL=${JSON.stringify(LBL)};
const $=(s,r=document)=>r.querySelector(s), $$=(s,r=document)=>[...r.querySelectorAll(s)];
const reduce=matchMedia('(prefers-reduced-motion: reduce)').matches;
const ticks=new WeakMap();
function paint(el,f){const s=+el.dataset.scale,st=KODA.seq[el.dataset.koda];
  el.style.backgroundImage='url('+KODA.s32+')';el.style.backgroundSize=(128*s)+'px '+(224*s)+'px';
  el.style.backgroundPosition='-'+(f*32*s)+'px -'+(st.row*32*s)+'px';}
function koda(el,pose){el.dataset.koda=pose;el.setAttribute('aria-label','Koda, '+LBL[pose]);ticks.set(el,0);paint(el,KODA.seq[pose].seq[0]);}
$$('[data-koda]').forEach(el=>{ticks.set(el,0);paint(el,KODA.seq[el.dataset.koda].seq[0]);});
if(!reduce)setInterval(()=>{$$('[data-koda]').forEach(el=>{const st=KODA.seq[el.dataset.koda];
  const t=((ticks.get(el)||0)+1)%st.seq.length;ticks.set(el,t);paint(el,st.seq[t]);});},125);
$('#theme').addEventListener('click',e=>{const d=document.documentElement;const n=d.dataset.theme==='dark'?'light':'dark';
  d.dataset.theme=n;e.currentTarget.setAttribute('aria-pressed',String(n==='light'));e.currentTarget.textContent=n==='light'?'Tema: claro':'Tema: oscuro';});
$('#narrow').addEventListener('click',e=>{const on=!document.body.hasAttribute('data-narrow');document.body.toggleAttribute('data-narrow',on);
  e.currentTarget.setAttribute('aria-pressed',String(on));e.currentTarget.textContent=on?'Ancho: 320 dp':'Ancho: 360 dp';});
/* expiry countdown: text keeps updating with reduced motion; only the bar transition is motion */
setInterval(()=>{$$('[data-countdown]').forEach(el=>{let s=Math.max(0,+el.dataset.countdown-1);el.dataset.countdown=s;
  el.textContent='Caduca en '+Math.floor(s/60)+':'+String(s%60).padStart(2,'0');});
  $$('[data-countbar]').forEach(b=>{const s=+$('[data-countdown]').dataset.countdown;b.style.width=(s/300*100)+'%';});},1000);
/* radio and checkbox rows, switches */
$$('[role=radiogroup]').forEach(g=>$$('[role=radio]',g).forEach(o=>o.addEventListener('click',()=>{$$('[role=radio]',g).forEach(x=>x.setAttribute('aria-checked',String(x===o)));})));
$$('[role=checkbox],[role=switch]').forEach(o=>o.addEventListener('click',()=>o.setAttribute('aria-checked',String(o.getAttribute('aria-checked')!=='true'))));
`;

/* ---------------- icons (24 grid, stroke 1.9 as core/IconSpec.kt) ---------------- */
const P = {
  home: '<path d="M4 11.5L12 5L20 11.5"/><path d="M6 10.5V19H18V10.5"/>',
  chat: '<rect x="3.5" y="5.5" width="17" height="12" rx="2"/><path d="M11 17.5L7 20.5V17.5"/>',
  cfg: '<path d="M4 7H13M17 7H20M4 17H7M11 17H20"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>',
  back: '<path d="M14.5 6L8.5 12L14.5 18"/>',
  close: '<path d="M7 7L17 17M17 7L7 17"/>',
  send: '<path d="M12 19V6"/><path d="M6.5 11.5L12 6L17.5 11.5"/>',
  lock: '<rect x="5.5" y="10.5" width="13" height="9" rx="2.5"/><path d="M8.5 10.5V8M15.5 10.5V8"/><path d="M8.5 8A3.5 3.5 0 0 1 15.5 8"/>',
  scan: '<path d="M4 8V6L6 4H8M16 4H18L20 6V8M20 16V18L18 20H16M8 20H6L4 18V16M8 12H16"/>',
  chev: '<path d="M9.5 6L15.5 12L9.5 18"/>',
  check: '<path d="M5.5 12.5L9.7 16.7L18.5 7.5"/>',
  fp: '<path d="M7 6.5A7 7 0 0 1 19 11.5"/><path d="M5 10a7 7 0 0 0-.2 1.5V14"/><path d="M8.5 19.5A10 10 0 0 0 9.5 15V11.5a2.5 2.5 0 0 1 5 0V13"/><path d="M12 11.5V15a8 8 0 0 1-1.6 5"/><path d="M14.5 16.5a12 12 0 0 1-.8 3.5"/><path d="M17.3 14.5V13"/>',
  warn: '<path d="M12 4.5L20.5 19H3.5Z"/><path d="M12 10V14M12 16.6V16.7"/>',
  clock: '<circle cx="12" cy="12" r="8"/><path d="M12 7.5V12L15 14"/>',
  moon: '<path d="M19.5 14.5A8 8 0 1 1 9.5 4.5a6.3 6.3 0 0 0 10 10z"/>',
  wifi: '<path d="M4 9.5a12 12 0 0 1 16 0M7 13a7.5 7.5 0 0 1 10 0M10 16.4a3 3 0 0 1 4 0"/>',
  folder: '<path d="M4 7.5V18H20V9H11.5L9.5 6.5H4Z"/>',
  list: '<path d="M9 7H20M9 12H20M9 17H20M4.5 7V7.1M4.5 12V12.1M4.5 17V17.1"/>',
  agents: '<circle cx="9" cy="9" r="3"/><path d="M3.5 19a5.5 5.5 0 0 1 11 0"/><path d="M15.5 6.5a3 3 0 0 1 0 5.5M17.5 14.5a5 5 0 0 1 3 4.5"/>',
};
const ic = (k, extra = "") => `<svg class="ic" viewBox="0 0 24 24" aria-hidden="true" ${extra}>${P[k]}</svg>`;

/* ---------------- frame helpers ---------------- */
const sbar = `<div class="sbar" aria-hidden="true"><span>14:41</span><span>5G<i></i><i style="width:18px"></i></span></div>`;
const gest = `<div class="gest" aria-hidden="true"></div>`;
const phone = (n, label, inner, attrs = "") =>
  `<figure><figcaption>${n} · ${label}</figcaption><div class="phone" role="group" aria-label="${label}" ${attrs}>${sbar}<div class="scr">${inner}</div>${gest}</div></figure>`;
const nav = (cur, items = [["home", "Inicio"], ["chat", "Chat"], ["cfg", "Ajustes"]], badge = {}) =>
  `<nav class="nav" aria-label="Secciones">${items
    .map(([k, t]) => `<a href="#" ${cur === k ? 'aria-current="page"' : ""} ${badge[k] ? `aria-label="${t}, ${badge[k]} esperándote"` : ""}>${ic(k === "today" ? "list" : k)}${t}${badge[k] ? `<span class="badge" aria-hidden="true">${badge[k]}</span>` : ""}</a>`)
    .join("")}</nav>`;
const island = (r, pose, title, sub = "", right = "", extra = "") =>
  `<div class="island keep-dark" data-r="${r}" role="status" ${extra}>${kodaEl(pose, 1.25)}<span class="t"><b class="clip">${title}</b>${sub ? `<span class="clip">${sub}</span>` : ""}</span>${right}</div>`;

const page = ({ title, lede, controls = "", phones, better, js = "" }) => `<!doctype html>
<html lang="es" data-theme="dark"><head><meta charset="utf-8"><meta name="viewport" content="width=1440"><title>${title} · Kenai mejoras</title>
<style>${CSS}</style></head><body>
<header class="top"><div class="tt"><h1>${title}</h1><p>${lede}</p></div>
<div class="ctl">${controls}<button class="cbtn" id="narrow" type="button" aria-pressed="false">Ancho: 360 dp</button><button class="cbtn" id="theme" type="button" aria-pressed="false">Tema: oscuro</button></div></header>
<main class="row">${phones.join("")}</main>
<section class="better" aria-labelledby="better-h"><h2 id="better-h">Qué mejora frente a la app de hoy</h2><div class="cols">
<div data-r="go"><h3><i></i>UI</h3><ul>${better.ui.map((x) => `<li>${x}</li>`).join("")}</ul></div>
<div data-r="live"><h3><i></i>UX</h3><ul>${better.ux.map((x) => `<li>${x}</li>`).join("")}</ul></div>
<div data-r="sun"><h3><i></i>CX</h3><ul>${better.cx.map((x) => `<li>${x}</li>`).join("")}</ul></div>
<div class="trade" data-r="red"><h3><i></i>Compromiso</h3>${better.trade.map((x) => `<p>${x}</p>`).join("")}</div>
</div></section>
<script>${BASE_JS}${js}</script></body></html>`;

/* ---------------- shared copy (values-es/strings.xml where it exists) ---------------- */
const CMD = "rm -rf app/build &amp;&amp; ./gradlew assembleRelease";
const HINT = "Permitir en el teléfono pide tu huella o el bloqueo de pantalla. Denegar no."; // approval_hint
const CAUTION = "Permitilo solo si lo pediste vos."; // approval_caution
const fpHint = `<p class="row tiny muted" style="gap:8px;align-items:flex-start">${ic("lock", 'style="width:18px;height:18px"')}<span>${HINT}</span></p>`;
const decideBtns = (attrs = ["", ""], size = "lg") =>
  `<div class="row2"><button class="btn ${size}" type="button" ${attrs[0]}>Denegar</button><button class="btn go ${size}" type="button" ${attrs[1]}>${ic("fp")}Permitir</button></div>`;
const countChip = (secs = 272) => `<span class="chip solid" data-r="sun"><i></i><span data-countdown="${secs}">Caduca en 4:32</span></span>`;

/* =====================================================================
   01 · Decidí en un toque
   ===================================================================== */
const hero1 = `<section class="card" data-r="sun" aria-labelledby="p1-who" style="overflow:hidden">
  <div class="veil" style="padding:14px 14px 10px">
    <div class="row">${halo("espera", "sun", 64, 1.6, "claude")}
      <div class="t"><h2 id="p1-who" class="h" style="font-size:20px">Claude Code</h2><span>Quiere ejecutar un comando en kenai</span></div></div>
    <div class="row" style="margin-top:10px;gap:10px">${countChip()}<div class="bar" data-r="sun" style="flex:1" aria-hidden="true"><i data-countbar style="width:90%"></i></div></div>
  </div>
  <div class="stack" style="padding:4px 14px 14px;gap:10px">
    <div class="cmd mono" aria-label="Comando exacto">${CMD}</div>
    <p class="row small" style="gap:8px">${ic("warn", 'style="width:18px;height:18px"')}<span><b>Borra archivos</b> dentro de kenai. ${CAUTION}</span></p>
    ${decideBtns(['data-decide="denied"', 'data-decide="allowed"'])}
    <p class="row tiny muted" style="gap:6px;justify-content:center">${ic("lock", 'style="width:16px;height:16px"')}Permitir pide tu huella. Denegar no.</p>
  </div></section>`;

const ISL_COUNT = `<span class="chip solid" data-r="sun" aria-label="2 te esperan"><i></i>2</span>`;
const homePending = `<div data-home="pending" class="scr">
  ${island("sun", "espera", "Claude Code espera tu sí", "Alvaro PC · 3 agentes", ISL_COUNT)}
  <div class="scroll stack">
    ${hero1}
    <section aria-labelledby="p1-also"><h2 id="p1-also" class="sec">También te espera</h2>
      <div class="card item">${halo("espera", "sun", 44, 1.1, "gemini")}<div class="t"><b>Gemini CLI</b><span class="clip">Tiene una pregunta · notes</span></div><button class="btn sm" type="button">Responder</button></div></section>
    <section aria-labelledby="p1-run"><h2 id="p1-run" class="sec">En curso · 1</h2>
      <div class="card item">${halo("trabaja", "live", 44, 1.1, "codex")}<div class="t"><b>Codex</b><span class="clip">Ejecutando un comando · npm test</span></div><span class="chip" data-r="live"><i></i>Trabajando</span></div></section>
  </div></div>`;

const homeCalm = `<div data-home="calm" class="scr" hidden>
  ${island("faint", "reposo", "Alvaro PC · todo tranquilo", "Conectado", `<span class="chip" data-r="live"><i></i>1 en curso</span>`)}
  <div class="scroll stack">
    <section class="card" style="padding:22px 18px;display:flex;flex-direction:column;align-items:center;text-align:center;gap:8px" aria-labelledby="p1-calm">
      ${kodaEl("reposo", 3)}<h2 id="p1-calm" class="h" style="font-size:20px">Nada te espera</h2>
      <p class="muted">Codex está trabajando en kenai-desktop. Te aviso apenas alguien necesite tu sí.</p></section>
    <section aria-labelledby="p1-run2"><h2 id="p1-run2" class="sec">En curso · 1</h2>
      <div class="card item">${halo("trabaja", "live", 44, 1.1, "codex")}<div class="t"><b>Codex</b><span class="clip">Ejecutando un comando · npm test</span></div><span class="chip" data-r="live"><i></i>Trabajando</span></div></section>
    <section aria-labelledby="p1-rec"><div class="row" style="justify-content:space-between"><h2 id="p1-rec" class="sec">Recientes</h2><button class="btn ghost sm" type="button">Ver todo</button></div>
      <ul class="card">
        <li class="item">${ic("check")}<div class="t"><b>Permitiste: editar archivos</b><span>Claude Code · Ayer</span></div></li>
        <li class="item" style="box-shadow:inset 0 1px 0 var(--line-faint)">${ic("close")}<div class="t"><b>Denegaste: ejecutar un comando</b><span>Codex · Ayer</span></div></li></ul></section>
  </div></div>`;

const homeOffline = `<div data-home="offline" class="scr" hidden>
  ${island("red", "error", "Sin conexión con Alvaro PC", "Último dato hace 3 min")}
  <div class="scroll stack">
    <section class="card veil" data-r="red" style="padding:18px 16px;display:flex;flex-direction:column;gap:10px" aria-labelledby="p1-off" role="alert">
      <div class="row">${kodaEl("error", 2)}<h2 id="p1-off" class="h" style="font-size:19px">No se encuentra tu computadora</h2></div>
      <p class="small">¿El teléfono está en el mismo Wi-Fi? ¿La computadora está despierta, con Koda abierto? Los pedidos te llegan apenas vuelva la conexión.</p>
      <div class="row2"><button class="btn pri" type="button">${ic("wifi")}Volver a probar</button><button class="btn" type="button">¿No se conecta?</button></div></section>
    <section aria-labelledby="p1-stale"><h2 id="p1-stale" class="sec">Lo último que sabemos · hace 3 min</h2>
      <div class="card item">${halo("trabaja", "faint", 44, 1.1, "codex")}<div class="t"><b>Codex</b><span class="clip">Ejecutando un comando · npm test</span></div><span class="chip"><i style="background:var(--faint)"></i>Sin confirmar</span></div></section>
  </div></div>`;

const p1 = phone(1, "Inicio", `<div class="scr">${homePending}${homeCalm}${homeOffline}</div>${nav("home", undefined, { home: 2 })}`);

const p2 = phone(2, "Confirmar con huella", `
  ${island("sun", "espera", "Claude Code espera tu sí", "Alvaro PC · 3 agentes", ISL_COUNT)}
  <div class="scroll" aria-hidden="true"><div class="card veil" data-r="sun" style="padding:14px;height:160px"><div class="row">${halo("espera", "sun", 64, 1.6, "claude")}<div class="t"><b style="font-size:20px">Claude Code</b><span>Quiere ejecutar un comando</span></div></div></div></div>
  <div class="scrim"></div>
  <section class="sheet" role="dialog" aria-modal="true" aria-labelledby="bio-t" style="text-align:center;display:flex;flex-direction:column;gap:12px">
    <div class="grab"></div>
    <h2 id="bio-t" class="h" style="font-size:20px">Confirmá que sos vos</h2>
    <p class="small muted">Permitir que Claude Code ejecute un comando en Alvaro PC</p>
    <div class="cmd mono" style="text-align:left">${CMD}</div>
    <button class="halo" data-r="live" id="fp-touch" type="button" aria-label="Sensor de huella: tocá para simular la huella" style="width:84px;height:84px;margin:6px auto 0">${ic("fp", 'style="width:44px;height:44px"')}</button>
    <p class="small">Tocá el sensor de huella</p>
    <div class="row2"><button class="btn ghost" type="button">Cancelar</button><button class="btn" type="button">Usar el PIN</button></div>
  </section>`);

const resultViews = {
  sending: { r: "live", pose: "piensa", isl: "Enviando a Alvaro PC…", h: "Enviando tu decisión", p: "Kenai espera que tu computadora confirme que la recibió. No cierres nada.", meta: "", spin: true },
  allowed: { r: "go", pose: "termino", isl: "Permitido · Alvaro PC lo recibió", h: "Permitiste ejecutar un comando", p: "Claude Code sigue trabajando en kenai.", meta: "14:41 · vibró una vez · quedó en Actividad" },
  denied: { r: "faint", pose: "escucha", isl: "Denegado · Alvaro PC lo recibió", h: "Denegaste ejecutar un comando", p: "Claude Code no lo va a ejecutar. Si querés, explicale por qué en tu computadora.", meta: "14:41 · vibró dos veces · quedó en Actividad" },
  expired: { r: "faint", pose: "reposo", isl: "Ese pedido ya no está pendiente", h: "Ese pedido ya no está pendiente.", p: "Caducó a las 14:45 o se respondió en tu computadora. Desde el teléfono no se envió nada.", meta: "" },
  failed: { r: "red", pose: "error", isl: "No se pudo enviar", h: "Tu decisión no llegó", p: "Se perdió la conexión con tu computadora. El pedido sigue abierto: volver a intentar es seguro.", meta: "", retry: true },
};
const resultHtml = Object.entries(resultViews)
  .map(([k, v]) => `<div data-res="${k}" class="scr fade" ${k === "allowed" ? "" : "hidden"}>
  ${island(v.r, v.pose, v.isl, "Alvaro PC", v.spin ? `<span class="spin" data-r="live" aria-hidden="true"></span>` : "")}
  <div class="scroll stack">
    <section class="card veil" data-r="${v.r}" style="padding:20px 16px;display:flex;flex-direction:column;align-items:center;text-align:center;gap:8px" aria-labelledby="res-${k}">
      ${kodaEl(v.pose, 3)}<h2 id="res-${k}" class="h" style="font-size:20px">${v.h}</h2><p class="small">${v.p}</p>
      ${v.meta ? `<p class="row tiny muted" style="gap:6px">${ic("clock", 'style="width:16px;height:16px"')}${v.meta}</p>` : ""}
      ${v.retry ? `<div class="row2" style="width:100%;margin-top:4px"><button class="btn pri" type="button">Volver a intentar</button><button class="btn" type="button">¿No se conecta?</button></div>` : ""}
      ${v.spin ? `<div class="row2" style="width:100%;margin-top:4px"><button class="btn" type="button" disabled>Denegar</button><button class="btn" type="button" disabled>Enviando…</button></div>` : ""}
    </section>
    ${v.spin || v.retry ? "" : `<section aria-labelledby="next-${k}"><h2 id="next-${k}" class="sec">Lo siguiente</h2>
      <div class="card item">${halo("espera", "sun", 44, 1.1, "gemini")}<div class="t"><b>Gemini CLI tiene una pregunta</b><span class="clip">¿En qué rama? · notes</span></div><button class="btn sm pri" type="button">Responder</button></div></section>`}
  </div></div>`)
  .join("");
const p3 = phone(3, "Resultado", `<div class="scr" aria-live="polite">${resultHtml}</div>${nav("home", undefined, { home: 1 })}`);

const p4 = phone(4, "Pregunta", `
  ${island("sun", "espera", "Claude Code espera tu sí", "Tocá para revisar el pedido", `<button class="icbtn" type="button" aria-label="Revisar el pedido de Claude Code">${ic("chev")}</button>`)}
  <div class="row" style="padding:0 8px 0 4px;gap:4px"><button class="icbtn" type="button" aria-label="Atrás">${ic("back")}</button><h2 class="h" style="font-size:17px">Gemini CLI tiene una pregunta</h2></div>
  <div class="scroll stack" style="padding-top:6px">
    <div><p class="tiny muted" style="margin-bottom:6px">Pregunta 1 de 2</p><div class="row2" style="gap:6px" aria-hidden="true"><div class="bar" data-r="live"><i style="width:100%"></i></div><div class="bar"></div></div></div>
    <h3 class="h" id="q1" style="font-size:24px;margin-top:4px">¿En qué rama?</h3>
    <div role="radiogroup" aria-labelledby="q1" class="stack" style="gap:8px">
      <button class="opt" role="radio" aria-checked="true" type="button"><span class="mark"></span><span><b>release/0.1</b><span>La rama de esta versión</span></span></button>
      <button class="opt" role="radio" aria-checked="false" type="button"><span class="mark"></span><span><b>main</b><span>Fusionar directo en main</span></span></button></div>
    <p class="tiny muted">Responder acá está activado en Koda, en tu computadora. Si ya respondiste allá, esta pregunta se cierra sola.</p>
  </div>
  <div style="padding:8px 14px 2px" class="row2"><button class="btn" type="button">Atrás</button><button class="btn pri" type="button">Siguiente</button></div>`);

const p5 = phone(5, "Avisos y widget", `
  <section class="card" style="margin:2px 10px 10px;padding:12px;border-radius:24px" aria-label="Panel de notificaciones">
    <div class="row" style="justify-content:space-between;margin:0 4px 10px"><b style="font-size:20px">14:41</b><span class="small muted">mar 11 nov</span></div>
    <div class="row" style="gap:8px">
      <button class="btn" type="button" aria-label="Wi-Fi: Casa" style="border-radius:20px;min-height:64px;width:64px;padding:0;flex:none">${ic("wifi")}</button>
      <button class="btn" data-r="sun" type="button" aria-label="Kenai: Claude Code, esperando tu aprobación. Abre el pedido." style="flex:1;min-width:0;background:var(--sun);color:var(--on-role);justify-content:flex-start;border-radius:20px;min-height:64px;text-align:left;padding:6px 12px">${kodaEl("espera", 1)}<span style="min-width:0"><b style="display:block;font-size:14px">Kenai</b><span class="tiny" style="display:block;font-weight:500">Claude Code · Esperando tu aprobación</span></span></button></div>
    <article class="card" style="margin-top:10px;padding:12px 14px;background:var(--surface)" aria-labelledby="n1">
      <p class="row tiny muted" style="gap:6px">${kodaEl("espera", 0.5)}Kenai · ahora</p>
      <h3 id="n1" style="font-size:15px;margin-top:4px">Claude Code necesita tu aprobación</h3>
      <p class="small muted">Quiere ejecutar un comando · <span data-countdown="272">Caduca en 4:32</span></p>
      <div class="row" style="gap:6px;margin-top:6px"><button class="btn ghost sm" type="button">Denegar</button><button class="btn ghost sm" type="button">Revisar el pedido</button></div></article>
  </section>
  <div class="scroll"><p class="tiny muted" style="margin:0 4px 8px">Pantalla de inicio · widget «Aprobaciones pendientes»</p>
    <section class="card veil" data-r="sun" style="padding:14px;border-radius:24px" aria-labelledby="w1">
      <div class="row">${kodaEl("espera", 2)}<div class="t"><h3 id="w1" class="h" style="font-size:18px">2 te esperan</h3><span>Alvaro PC</span></div></div>
      <ul style="margin:10px 0" class="stack" >
        <li class="row small" style="gap:8px"><span class="dot" style="background:var(--id-claude)"></span><b>Claude Code</b><span class="muted clip">Espera tu sí</span></li>
        <li class="row small" style="gap:8px"><span class="dot" style="background:var(--id-gemini)"></span><b>Gemini CLI</b><span class="muted clip">Tiene una pregunta</span></li></ul>
      <button class="btn pri block" type="button">Revisar el pedido</button></section></div>`);

const A = page({
  title: "1 · Decidí en un toque",
  lede: "El pedido pendiente es el protagonista de Inicio: qué, quién, riesgo y vencimiento, con Denegar y Permitir (huella) a la vista. Cada decisión termina en un estado claro y encadena lo siguiente.",
  controls: `<button class="cbtn" id="ctl-home" type="button">Inicio: con pedido</button><button class="cbtn" id="ctl-res" type="button">Resultado: permitido</button>`,
  phones: [p1, p2, p3, p4, p5],
  better: {
    ui: [
      "El pedido ocupa el primer pantallazo: agente, acción, comando exacto, riesgo («Borra archivos») y vencimiento con barra.",
      "Una franja tipo isla resume el estado con el color de rol de system.md (--sun, --go, --red-11), oscura en los dos temas como la isla.",
      "Permitir lleva la huella; Denegar es neutro. El riesgo va con texto e ícono, nunca solo con color.",
    ],
    ux: [
      "Decidir sin abrir una hoja: un toque y la huella. Hoy hace falta abrir el pedido primero.",
      "Cada decisión muestra enviando, permitido, denegado, caducó o no se pudo enviar, y propone lo siguiente pendiente.",
      "Vacío («Nada te espera») y sin conexión diseñados, con el último dato marcado como «Sin confirmar».",
    ],
    cx: [
      "Te alejás tranquilo: sabés qué llegó a la computadora y qué no, y si reintentar es seguro.",
      "La vibración distingue el sí (una) del no (dos). La notificación suma Denegar sin desbloquear; el tile sigue sin decidir nada, solo abre el pedido.",
    ],
    trade: [
      "Con un pedido abierto, Inicio deja de mostrar a Koda grande y el progreso de los demás: queda optimizada para decidir, no para mirar.",
      "Las señales de riesgo y el vencimiento necesitan datos que la computadora quizá no manda hoy (a confirmar); sin ellos se ocultan. Denegar desde la notificación es nuevo y hay que decidirlo.",
    ],
  },
  js: `
const HOME=['pending','calm','offline'],HOMEN={pending:'con pedido',calm:'nada te espera',offline:'sin conexión'};let hi=0;
$('#ctl-home').addEventListener('click',e=>{hi=(hi+1)%HOME.length;$$('[data-home]').forEach(x=>x.hidden=x.dataset.home!==HOME[hi]);e.currentTarget.textContent='Inicio: '+HOMEN[HOME[hi]];});
const RES=['allowed','denied','expired','failed','sending'],RESN={allowed:'permitido',denied:'denegado',expired:'caducó',failed:'no se pudo enviar',sending:'enviando'};let ri=0;
function showRes(k){ri=RES.indexOf(k);$$('[data-res]').forEach(x=>x.hidden=x.dataset.res!==k);$('#ctl-res').textContent='Resultado: '+RESN[k];}
$('#ctl-res').addEventListener('click',()=>showRes(RES[(ri+1)%RES.length]));
let pend=null;function decide(k){clearTimeout(pend);showRes('sending');pend=setTimeout(()=>showRes(k),900);}
$$('[data-decide]').forEach(b=>b.addEventListener('click',()=>decide(b.dataset.decide)));
$('#fp-touch').addEventListener('click',()=>decide('allowed'));
`,
});

/* =====================================================================
   02 · Timeline del día
   ===================================================================== */
const entry = (time, r, text, kind, action = "") =>
  `<li class="tl" data-kind="${kind}" data-r="${r}"><span class="tl-time">${time}</span><span class="tl-dot" aria-hidden="true"></span><div class="tl-body"><span>${text}</span>${action}</div></li>`;
const group = (id, name, idc, project, r, pose, state, entries) =>
  `<section class="tg" aria-labelledby="g-${id}"><div class="row tg-h">${halo(pose, r, 48, 1.25, idc)}<div class="t"><b id="g-${id}">${name}</b><span class="clip">${project}</span></div><span class="chip ${r === "sun" ? "solid" : ""}" data-r="${r}"><i></i>${state}</span></div>
  <ol class="tl-list">${entries.join("")}</ol></section>`;
const TL_CSS = `<style>
.tg{padding:10px 0 4px}.tg+.tg{box-shadow:inset 0 1px 0 var(--line-faint)}
.tg-h{min-height:52px}
.tl-list{margin:4px 0 0 0}
.tl{display:grid;grid-template-columns:44px 14px 1fr;align-items:start;gap:8px;min-height:36px;position:relative}
.tl-time{font-size:12.5px;color:var(--muted);font-variant-numeric:tabular-nums;padding-top:2px;text-align:right}
.tl-dot{width:10px;height:10px;border-radius:50%;background:var(--c);margin-top:6px;box-shadow:0 0 0 3px var(--bg)}
.tl::before{content:"";position:absolute;left:56px;top:16px;bottom:-6px;width:2px;background:var(--line-faint)}
.tl:last-child::before{display:none}
.tl-body{display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-size:14px;padding-bottom:8px}
.tl-body>span{flex:1;min-width:140px}
.sum{display:grid;grid-template-columns:repeat(3,1fr);gap:0}
.sum>button{display:flex;flex-direction:column;align-items:flex-start;gap:2px;padding:10px 12px;min-height:64px;text-align:left;border-radius:14px}
.sum>button:hover{background:var(--fill)}
.sum>button+button{box-shadow:inset 1px 0 0 var(--line-faint)}
.sum b{font-size:22px;font-variant-numeric:tabular-nums;line-height:1.1}
.sum span{font-size:12px;color:var(--muted);line-height:1.25}
.filters{display:flex;gap:6px;overflow-x:auto;scrollbar-width:none;padding:2px 0}
.fchip{flex:none;min-height:44px;padding:0 12px;border-radius:var(--r-full);background:var(--fill);font-size:13px;font-weight:600;white-space:nowrap;box-shadow:inset 0 0 0 1px var(--line-faint)}
.fchip[aria-pressed=true]{background:var(--primary-bg);color:var(--primary-ink)}
</style>`;

const tlGroups = [
  group("claude", "Claude Code", "claude", "kenai", "sun", "espera", "Espera tu sí", [
    entry("14:40", "sun", "Quiere ejecutar un comando", "need", `<button class="btn sm pri" type="button">Revisar</button>`),
    entry("14:31", "faint", "Editó 2 archivos · +15 −1", "did"),
    entry("14:12", "go", "Terminó: el build pasó y arregló dos avisos de lint", "done"),
  ]),
  group("gemini", "Gemini CLI", "gemini", "notes", "sun", "espera", "Tiene una pregunta", [
    entry("14:38", "sun", "«¿En qué rama?»", "need", `<button class="btn sm pri" type="button">Responder</button>`),
  ]),
  group("codex", "Codex", "codex", "kenai-desktop", "live", "trabaja", "Trabajando", [
    entry("14:36", "live", "Ejecutando un comando · npm test", "did"),
    entry("13:58", "faint", "Permitiste: editar archivos", "did"),
    entry("13:20", "go", "Terminó: actualizó el README", "done"),
  ]),
];

const q1 = phone(1, "Hoy", `${TL_CSS}
  <div style="padding:6px 16px 8px"><div class="row" style="justify-content:space-between"><h2 class="h" style="font-size:26px">Hoy</h2><span class="chip" data-r="go"><i></i>Alvaro PC</span></div>
    <p class="small muted">martes 11 de noviembre · actualizado hace 10 s</p></div>
  <div class="scroll stack" style="gap:10px">
    <section class="card sum" aria-label="Resumen de hoy">
      <button type="button"><b>5</b><span>tareas terminadas</span></button>
      <button type="button"><b>3</b><span>decisiones tuyas</span></button>
      <button type="button" data-r="live"><b>68%</b><span>Claude · 5 h</span><span class="bar" style="width:100%;margin-top:3px" aria-hidden="true"><i style="width:68%"></i></span></button></section>
    <div class="filters" role="toolbar" aria-label="Filtrar la línea de tiempo">
      <button class="fchip" type="button" aria-pressed="true" data-f="all">Todo</button>
      <button class="fchip" type="button" aria-pressed="false" data-f="need">Te necesitan · 2</button>
      <button class="fchip" type="button" aria-pressed="false" data-f="done">Terminó · 2</button>
      <button class="fchip" type="button" aria-pressed="false" data-f="error">Errores · 0</button></div>
    <div id="tl" aria-live="polite">${tlGroups.join("")}
      <div id="tl-empty" class="card" style="padding:18px;display:flex;gap:12px;align-items:center" hidden>${kodaEl("termino", 1.5)}<p class="small">Ningún agente tuvo errores hoy. <button class="btn ghost sm" type="button" data-f="all" style="text-decoration:underline">Ver todo</button></p></div></div>
  </div>${nav("today", [["today", "Hoy"], ["chat", "Chat"], ["cfg", "Ajustes"]], { today: 2 })}`);

const q2 = phone(2, "Pedido con contexto", `${TL_CSS}
  <div style="padding:6px 16px 8px" aria-hidden="true"><h2 class="h" style="font-size:26px">Hoy</h2></div>
  <div class="scroll" aria-hidden="true">${tlGroups[0].replaceAll("g-claude", "g2-claude")}</div>
  <div class="scrim"></div>
  <section class="sheet stack" role="dialog" aria-modal="true" aria-labelledby="ctx-t" style="gap:10px">
    <div class="grab"></div>
    <div class="row">${halo("espera", "sun", 52, 1.3, "claude")}<div class="t"><h2 id="ctx-t" class="h" style="font-size:18px">Claude Code</h2><span>Quiere ejecutar un comando · kenai</span></div></div>
    <div>${countChip()}</div>
    <div class="cmd mono">${CMD}</div>
    <div><h3 class="sec" style="margin-bottom:4px">Lo que hizo antes</h3>
      <ol>${entry("14:31", "faint", "Editó app/build.gradle.kts · +3 −1", "did")}${entry("14:31", "faint", "Creó docs/RELEASE.md · +12", "did")}${entry("14:12", "go", "Ejecutó npm run build · pasó", "done")}</ol></div>
    <p class="small">${CAUTION}</p>
    ${decideBtns()}
    ${fpHint}
  </section>`);

const q3 = phone(3, "Pregunta en el hilo", `${TL_CSS}
  <div style="padding:6px 16px 8px"><h2 class="h" style="font-size:26px">Hoy</h2></div>
  <div class="scroll">
    <section class="tg" aria-labelledby="g3-gem"><div class="row tg-h">${halo("espera", "sun", 48, 1.25, "gemini")}<div class="t"><b id="g3-gem">Gemini CLI</b><span>notes</span></div><span class="chip solid" data-r="sun"><i></i>Tiene una pregunta</span></div>
      <ol class="tl-list"><li class="tl" data-r="sun"><span class="tl-time">14:38</span><span class="tl-dot" aria-hidden="true"></span>
        <div class="card stack" style="padding:12px;gap:8px;margin-bottom:8px">
          <p class="tiny muted">Pregunta 1 de 2</p>
          <h3 id="q3h" class="h" style="font-size:19px">¿En qué rama?</h3>
          <div role="radiogroup" aria-labelledby="q3h" class="stack" style="gap:6px">
            <button class="opt" role="radio" aria-checked="true" type="button" style="background:var(--surface)"><span class="mark"></span><span><b>release/0.1</b><span>La rama de esta versión</span></span></button>
            <button class="opt" role="radio" aria-checked="false" type="button" style="background:var(--surface)"><span class="mark"></span><span><b>main</b><span>Fusionar directo en main</span></span></button></div>
          <button class="btn pri" type="button">Siguiente</button>
          <p class="tiny muted">Después: «¿Qué corro después de fusionar?» · Elegí una o más</p></div></li>
        ${entry("14:02", "faint", "Leyó 4 notas de la reunión", "did")}</ol></section>
    ${tlGroups[0].replaceAll("g-claude", "g3-claude")}
  </div>${nav("today", [["today", "Hoy"], ["chat", "Chat"], ["cfg", "Ajustes"]], { today: 2 })}`);

const q4 = phone(4, "Modo calma", `
  ${island("faint", "reposo", "Horas de silencio hasta las 8:00", "Los pedidos y las preguntas siguen llegando", `<span class="icbtn" aria-hidden="true">${ic("moon")}</span>`)}
  <div class="scroll stack" style="gap:14px">
    <section style="display:flex;flex-direction:column;align-items:center;text-align:center;gap:6px;padding:6px 8px 0" aria-labelledby="calm-h">
      ${halo("reposo", "faint", 112, 2.75)}
      <h2 id="calm-h" class="h" style="font-size:22px;margin-top:4px">Todo en calma</h2>
      <p class="muted">Kenai no suena por lo que termina. Si alguien necesita tu sí, te avisa igual.</p></section>
    <section class="card" aria-labelledby="calm-d" style="padding:6px 0">
      <h3 id="calm-d" class="sec" style="padding:10px 14px 0">Desde las 23:00 · 0 te esperan</h3>
      <div class="item">${halo("termino", "go", 44, 1.1, "codex")}<div class="t"><b>Codex terminó</b><span class="clip">npm test pasó · kenai-desktop · 23:42</span></div></div>
      <div class="item" style="box-shadow:inset 0 1px 0 var(--line-faint)">${halo("termino", "go", 44, 1.1, "claude")}<div class="t"><b>Claude Code terminó</b><span class="clip">Build de kenai listo · 00:15</span></div></div>
      </section>
    </div>
  <div class="row2" style="padding:6px 14px 0"><button class="btn" type="button">Ver el día</button><button class="btn ghost" type="button">Terminar el silencio</button></div>${nav("today", [["today", "Hoy"], ["chat", "Chat"], ["cfg", "Ajustes"]])}`);

const sw = (label, hint, on, isNew = false) =>
  `<div class="setrow"><div class="t"><b id="sw-${label.length}-${hint.length}">${label}${isNew ? ` <span class="chip" data-r="live" style="min-height:20px;font-size:11px;padding:0 8px;margin-left:4px"><i></i>Nuevo</span>` : ""}</b><span>${hint}</span></div><button class="sw" type="button" role="switch" aria-checked="${on}" aria-labelledby="sw-${label.length}-${hint.length}"></button></div>`;
const q5 = phone(5, "Ajustes · Avisos", `
  <div class="row" style="padding:0 8px 0 4px;gap:4px"><button class="icbtn" type="button" aria-label="Atrás">${ic("back")}</button><h2 class="h" style="font-size:20px">Avisos</h2></div>
  <div class="scroll stack" style="gap:12px;padding-top:6px">
    <section aria-labelledby="s-av"><h3 id="s-av" class="sec">Durante el día</h3><div class="card">
      ${sw("Resumen del día", "A las 18:00, un aviso con lo que hicieron tus agentes hoy.", true, true)}
      ${sw("Cuando un agente termina o falla", "Un aviso discreto. Los pedidos y las preguntas siempre te avisan.", true)}
      ${sw("Vibrar", "Una vibración corta cuando llega un pedido o una pregunta, incluso en horas de silencio.", true)}</div></section>
    <section aria-labelledby="s-qh"><h3 id="s-qh" class="sec">Horas de silencio</h3><div class="card">
      ${sw("Horas de silencio", "Sin sonido cuando un agente termina o falla.", true)}
      <div class="setrow"><button class="btn sm" type="button" style="flex:1">Desde 23:00</button><button class="btn sm" type="button" style="flex:1">Hasta 08:00</button></div>
      ${sw("Modo calma en Inicio", "Inicio muestra solo lo que te necesita y un resumen.", true, true)}</div></section>
    <p class="tiny muted" style="padding:0 4px">El historial se guarda solo en este teléfono y nunca se envía a ningún lado.</p>
  </div>${nav("cfg", [["today", "Hoy"], ["chat", "Chat"], ["cfg", "Ajustes"]])}`);

const B = page({
  title: "2 · Timeline del día",
  lede: "Inicio pasa a ser «Hoy»: lo que te necesita fijado arriba y debajo lo que hizo cada agente, en orden y con hora, con Koda en el color de su estado. Un resumen de tres cifras y un modo calma para las horas de silencio.",
  phones: [q1, q2, q3, q4, q5],
  better: {
    ui: [
      "Línea de tiempo con hora, punto de color de rol y texto; cada agente agrupado con su Koda en un halo del color de su estado y su color de identidad.",
      "Resumen de hoy en tres cifras que se pueden tocar (terminadas, decisiones, uso del plan) en lugar de paneles sueltos.",
    ],
    ux: [
      "Filtros Todo / Te necesitan / Terminó / Errores, con estado vacío real («Ningún agente tuvo errores hoy»).",
      "El pedido trae «Lo que hizo antes» para decidir con contexto; las preguntas se responden dentro del hilo, sin cambiar de pantalla.",
    ],
    cx: [
      "Al volver al escritorio ya sabés qué pasó: el día cuenta una historia, no un estado suelto.",
      "Modo calma de noche que no esconde pedidos, resumen diario opcional y la privacidad del historial dicha a la vista.",
    ],
    trade: [
      "Más para leer: con muchos agentes el feed crece y hay que desplazarse; decidir un pedido cuesta dos toques (Revisar → Permitir) en vez de uno.",
      "Los pasos y archivos dependen de «Mostrar los detalles de la sesión en el teléfono» en la computadora; sin eso el hilo queda corto. El resumen del día y el modo calma son ajustes nuevos.",
    ],
  },
  js: `
function filter(f){$$('.fchip').forEach(c=>c.setAttribute('aria-pressed',String(c.dataset.f===f)));let any=false;
  $$('#tl .tg').forEach(g=>{let vis=0;$$('.tl',g).forEach(e=>{const show=f==='all'||e.dataset.kind===f;e.hidden=!show;if(show)vis++;});g.hidden=!vis;if(vis)any=true;});
  $('#tl-empty').hidden=any;}
$$('[data-f]').forEach(c=>c.addEventListener('click',()=>filter(c.dataset.f)));
`,
});

/* =====================================================================
   03 · Koda al frente
   ===================================================================== */
const STEP_CSS = `<style>
.steps{display:flex;gap:6px;padding:4px 18px 0}
.steps i{flex:1;height:6px;border-radius:3px;background:var(--fill);box-shadow:inset 0 0 0 1px var(--line-faint)}
.steps i.on{background:var(--ink);box-shadow:none}
.vf{position:relative;width:220px;height:220px;margin:4px auto 0;border-radius:28px;background:var(--surface);box-shadow:inset 0 0 0 1px var(--line-faint)}
.vf>b{position:absolute;width:36px;height:36px;border:4px solid var(--ink)}
.vf>b:nth-child(1){left:14px;top:14px;border-right:0;border-bottom:0;border-radius:12px 0 0 0}
.vf>b:nth-child(2){right:14px;top:14px;border-left:0;border-bottom:0;border-radius:0 12px 0 0}
.vf>b:nth-child(3){left:14px;bottom:14px;border-right:0;border-top:0;border-radius:0 0 0 12px}
.vf>b:nth-child(4){right:14px;bottom:14px;border-left:0;border-top:0;border-radius:0 0 12px 0}
.qr{position:absolute;inset:58px;display:grid;grid-template-columns:repeat(7,1fr);gap:3px}
.qr span{border-radius:2px;background:var(--ink)}
.qr span.o{background:transparent}
.say{position:relative;padding:12px 16px;border-radius:20px;background:var(--card);font-size:17px;font-weight:600;line-height:1.35;box-shadow:inset 0 0 0 1px var(--line-faint);text-align:center}
.say::after{content:"";position:absolute;left:50%;bottom:-8px;width:16px;height:16px;margin-left:-8px;background:var(--card);transform:rotate(45deg);box-shadow:inset -1px -1px 0 var(--line-faint)}
.kbtn{display:block;margin:0 auto;border-radius:32px;padding:4px}
.agchips{display:flex;gap:8px;overflow-x:auto;scrollbar-width:none;padding:2px}
.agchip{flex:none;display:flex;align-items:center;gap:10px;min-height:60px;padding:8px 16px 8px 10px;border-radius:20px;background:var(--card);box-shadow:inset 0 0 0 1px var(--line-faint);text-align:left}
.agchip b{display:block;font-size:15px}.agchip span{display:block;font-size:12.5px;color:var(--muted)}
.sheetnav{position:absolute;left:0;right:0;bottom:0;background:var(--surface);border-radius:28px 28px 0 0;padding:6px 14px 6px;box-shadow:inset 0 1px 0 var(--line-faint),0 -1px 0 var(--line-faint);transform:translateY(calc(100% - 118px))}
.sheetnav[data-open]{transform:none}
.sheetnav .tabs{display:flex;gap:6px}
.sheetnav .tabs button{flex:1;min-height:64px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;border-radius:20px;font-size:13px;font-weight:600;color:var(--muted)}
.sheetnav .tabs button[aria-current]{background:var(--tab-on);color:var(--ink)}
.handle{display:flex;flex-direction:column;align-items:center;gap:6px;width:100%;min-height:44px;justify-content:center;font-size:12px;color:var(--muted)}
.handle::before{content:"";width:40px;height:5px;border-radius:3px;background:var(--line)}
.wgrid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;padding:0 14px}
.wid{border-radius:26px;background:var(--card);box-shadow:inset 0 0 0 1px var(--line-faint);padding:12px;display:flex;flex-direction:column;min-width:0}
.ring{width:72px;height:72px;flex:none}
.ring circle{fill:none;stroke-width:8}
</style>`;

const QR = [1,1,1,0,1,1,1, 1,0,1,0,1,0,1, 1,1,1,0,1,1,1, 0,0,0,1,0,1,0, 1,0,1,1,1,0,1, 0,1,0,1,0,1,1, 1,1,1,0,1,0,1];
const pairSteps = [
  `<div data-step="1" class="stack" style="align-items:center;text-align:center;gap:10px" hidden>
    ${kodaEl("escucha", 5)}<h2 class="h" style="font-size:26px">Hola, soy Koda</h2>
    <p class="muted" style="font-size:16px">En tu computadora abrí Koda, después Ajustes › Teléfono Android.</p>
    <button class="btn pri lg block" type="button" data-go="2">Ya lo abrí</button>
    <button class="btn ghost block" type="button">Probá el modo demo</button></div>`,
  `<div data-step="2" class="stack" style="align-items:stretch;text-align:center;gap:10px">
    <h2 class="h" style="font-size:24px">Escaneá el código</h2>
    <p class="muted" style="font-size:15px">Apuntá la cámara al código que aparece en Ajustes › Teléfono Android de tu computadora.</p>
    <div class="vf" role="img" aria-label="Visor de la cámara con el código de emparejamiento"><b></b><b></b><b></b><b></b>
      <div class="qr">${QR.map((v) => `<span class="${v ? "" : "o"}"></span>`).join("")}</div>
      <span style="position:absolute;right:-14px;bottom:-18px">${kodaEl("piensa", 2)}</span></div>
    <p class="tiny muted">Kenai usa la cámara solo en esta pantalla, para leer el código. No se guarda ni se envía nada.</p>
    <div class="row2"><button class="btn" type="button" data-go="1">Atrás</button><button class="btn pri" type="button" data-go="3">Leí el código</button></div>
    <button class="btn ghost block" type="button">Mejor pegar el enlace</button></div>`,
  `<div data-step="3" class="stack" style="align-items:center;text-align:center;gap:10px" hidden>
    ${kodaEl("termino", 5)}<h2 class="h" style="font-size:24px">¿Emparejar con Alvaro PC?</h2>
    <p class="muted" style="font-size:15px">192.168.1.20:47821 en tu red. Emparejá solo con una computadora que sea tuya.</p>
    <button class="btn go lg block" type="button">Emparejar</button>
    <button class="btn block" type="button" data-go="2">Atrás</button></div>`,
];
const r1 = phone(1, "Emparejar en 3 pasos", `${STEP_CSS}
  <div class="steps" role="progressbar" aria-label="Paso del emparejamiento" aria-valuemin="1" aria-valuemax="3" aria-valuenow="2" id="steps"><i class="on"></i><i class="on"></i><i></i></div>
  <p class="tiny muted" style="padding:6px 18px 0" id="steplabel">Paso 2 de 3</p>
  <div class="scroll" style="padding:14px 20px;display:flex;flex-direction:column;justify-content:center">${pairSteps.join("")}</div>`);

const r2 = phone(2, "Inicio", `${STEP_CSS}
  <div class="row" style="justify-content:space-between;padding:2px 18px 6px"><span class="row small" style="gap:6px"><span class="dot" style="background:var(--go)"></span>Alvaro PC · conectado</span><span class="small muted">3 agentes</span></div>
  <div class="scroll" style="padding:0 12px">
    <section class="veil" data-r="live" id="hero3" style="border-radius:32px;padding:18px 16px 14px;display:flex;flex-direction:column;align-items:center;gap:12px">
      <p class="say" id="say" aria-live="polite">Claude Code está compilando kenai. Codex corre los tests.</p>
      <button class="kbtn" type="button" id="kodabtn" aria-label="Tocá a Koda para cambiar de estado">${kodaEl("trabaja", 6, 'id="koda3"')}</button>
      <div class="row" style="gap:8px"><span class="chip solid" id="chip3"><i></i><span id="chip3t">Trabajando</span></span><button class="btn sm pri" type="button" id="act3" hidden>Revisar el pedido</button></div>
    </section>
    <div class="agchips" style="margin-top:12px" role="list" aria-label="Agentes">
      <button class="agchip" role="listitem" type="button">${halo("trabaja", "live", 44, 1.1, "claude")}<span><b>Claude Code</b><span>Trabajando</span></span></button>
      <button class="agchip" role="listitem" type="button">${halo("piensa", "live", 44, 1.1, "codex")}<span><b>Codex</b><span>Pensando</span></span></button>
      <button class="agchip" role="listitem" type="button">${halo("reposo", "faint", 44, 1.1, "gemini")}<span><b>Gemini CLI</b><span>Durmiendo</span></span></button></div>
  </div>
  <div class="scrim" id="navscrim" hidden></div>
  <nav class="sheetnav" id="sheetnav" aria-label="Secciones">
    <button class="handle" type="button" id="handle" aria-expanded="false" aria-controls="navmore">Deslizá o tocá para ver más</button>
    <div class="tabs"><button type="button" aria-current="page">${ic("home")}Inicio</button><button type="button">${ic("agents")}Agentes</button><button type="button">${ic("chat")}Chat</button><button type="button">${ic("cfg")}Ajustes</button></div>
    <div id="navmore" class="stack" style="gap:10px;padding:12px 4px 4px">
      <section class="card" style="padding:12px 14px" aria-labelledby="u3"><h3 id="u3" class="sec">Uso del plan</h3>
        <div class="stack" style="gap:10px">
          <div data-r="live"><div class="row small" style="justify-content:space-between"><b>Claude · 5 horas</b><span>68%</span></div><div class="bar" style="margin:4px 0"><i style="width:68%"></i></div><span class="tiny muted">Se reinicia en 2 h 14</span></div>
          <div data-r="live"><div class="row small" style="justify-content:space-between"><b>Codex · Semana</b><span>86%</span></div><div class="bar" style="margin:4px 0"><i style="width:86%"></i></div><span class="tiny muted">Se reinicia el jueves</span></div></div></section>
      <section class="card" aria-labelledby="r3"><h3 id="r3" class="sec" style="padding:12px 14px 0">Recientes</h3>
        <div class="item">${ic("check")}<div class="t"><b>Permitiste: editar archivos</b><span>Claude Code · Ayer</span></div></div></section>
    </div>
  </nav>`);

const r3 = phone(3, "Aprobación", `
  <div class="row" style="padding:0 8px 0 4px;gap:4px"><button class="icbtn" type="button" aria-label="Cerrar">${ic("close")}</button><span class="small muted">Pedido de permiso</span></div>
  <section class="veil" data-r="sun" style="margin:0 12px;border-radius:32px;padding:14px 16px 16px;display:flex;flex-direction:column;align-items:center;text-align:center;gap:8px" aria-labelledby="ap3">
    ${kodaEl("espera", 5)}${countChip()}
    <h2 id="ap3" class="h" style="font-size:24px;line-height:1.2">Claude Code quiere ejecutar un comando</h2>
    <p class="small muted">en kenai · Alvaro PC</p></section>
  <div class="scroll stack" style="padding:12px 16px 0;gap:8px">
    <div class="cmd mono" style="font-size:14px">${CMD}</div>
    <p class="small">${CAUTION}</p></div>
  <div class="stack" style="padding:0 16px 4px;gap:8px">
    <button class="btn go xl block" type="button">${ic("fp")}Permitir</button>
    <div class="row2"><button class="btn lg" type="button">Denegar</button><button class="btn ghost lg" type="button">Ahora no</button></div>
    ${fpHint}</div>`);

const r4 = phone(4, "Chat con Koda", `${STEP_CSS}
  <div class="row" style="padding:0 12px 6px 4px;gap:6px"><button class="icbtn" type="button" aria-label="Atrás">${ic("back")}</button>${halo("escucha", "live", 40, 1)}<h2 class="h" style="font-size:19px;flex:1">Chat</h2></div>
  <div class="scroll stack" style="gap:10px;padding-top:4px">
    <p class="tiny muted" style="text-align:center">Hoy</p>
    <p style="align-self:flex-end;max-width:82%;padding:10px 14px;border-radius:20px 20px 6px 20px;background:var(--bubble);font-size:15px">¿Cómo compilo la versión de Android?</p>
    <div class="row" style="align-items:flex-end;gap:8px">${kodaEl("termino", 1.25)}<p style="max-width:80%;padding:10px 14px;border-radius:20px 20px 20px 6px;background:var(--card);box-shadow:inset 0 0 0 1px var(--line-faint);font-size:15px">Corré <span class="mono">./gradlew assembleRelease</span>. El APK queda en <span class="mono">app/build/outputs/apk/release</span>. ¿Querés que también lo firme?</p></div>
    <p style="align-self:flex-end;max-width:82%;padding:10px 14px;border-radius:20px 20px 6px 20px;background:var(--bubble);font-size:15px">Sí, con la clave de release.</p>
    <div class="row muted small" style="gap:8px" role="status">${kodaEl("piensa", 1.25)}Koda está escribiendo…</div>
    <div class="row" style="gap:6px;flex-wrap:wrap"><button class="chip" type="button" style="min-height:44px">¿Qué terminó hoy?</button><button class="chip" type="button" style="min-height:44px">¿Quién me espera?</button></div>
  </div>
  <div class="row" style="padding:6px 12px 0;gap:8px"><button class="chip" type="button" style="min-height:44px;flex:none">Modelo: Claude Sonnet</button><p class="tiny muted">La clave de API nunca llega a este teléfono.</p></div>
  <div class="row" style="padding:8px 12px 4px;gap:8px"><label class="t" style="min-height:52px;display:flex;align-items:center;padding:0 18px;border-radius:var(--r-full);background:var(--fill);box-shadow:inset 0 0 0 1px var(--line-faint)"><span class="muted" style="font-size:15px">Preguntame lo que quieras…</span></label><button class="icbtn" type="button" aria-label="Enviar" style="width:52px;height:52px;background:var(--primary-bg);color:var(--primary-ink)">${ic("send")}</button></div>`);

const r5 = phone(5, "Widgets", `${STEP_CSS}
  <div style="padding:6px 22px 14px"><p style="font-size:44px;font-weight:300;line-height:1">14:41</p><p class="small muted">martes 11 de noviembre</p></div>
  <div class="wgrid">
    <section class="wid veil" data-r="live" style="grid-column:span 4;flex-direction:row;align-items:center;gap:12px" aria-label="Widget Estado grande">
      ${kodaEl("trabaja", 2.5)}<div class="t"><b style="font-size:17px">Claude Code</b><span>Trabajando · kenai</span>
        <div class="row" style="gap:6px;margin-top:8px"><button class="icbtn" type="button" aria-label="Abrir el chat" style="background:var(--btn);width:44px;height:44px">${ic("chat")}</button><button class="icbtn" type="button" aria-label="Abrir las decisiones recientes" style="background:var(--btn);width:44px;height:44px">${ic("clock")}</button><button class="icbtn" type="button" aria-label="Abrir Ajustes" style="background:var(--btn);width:44px;height:44px">${ic("cfg")}</button></div></div>
      <svg class="ring" viewBox="0 0 72 72" role="img" aria-label="Uso del plan Claude, 5 horas, 68 por ciento usado"><circle cx="36" cy="36" r="30" style="stroke:var(--fill)"/><circle cx="36" cy="36" r="30" style="stroke:var(--ink)" stroke-dasharray="128 189" transform="rotate(-90 36 36)" stroke-linecap="round"/><text x="36" y="41" text-anchor="middle" style="fill:var(--ink);font:700 15px var(--font-ui)">68%</text></svg></section>
    <section class="wid" data-r="live" style="grid-column:span 2;align-items:center;text-align:center;gap:6px" aria-label="Widget Ánimo de los agentes">
      ${halo("piensa", "live", 104, 2.75)}<b class="small">Pensando</b></section>
    <section class="wid veil" data-r="go" style="grid-column:span 2;align-items:center;text-align:center;gap:2px" aria-label="Widget Terminadas hoy">
      ${kodaEl("termino", 2)}<b style="font-size:30px;line-height:1.1">5</b><span class="tiny muted">hechas hoy</span></section>
    <section class="wid" data-r="sun" style="grid-column:span 4;flex-direction:row;align-items:center;gap:10px;padding:8px 14px;border-radius:var(--r-full)" aria-label="Widget Estado en una línea">
      ${kodaEl("espera", 1.25)}<span class="t clip"><b style="display:inline;font-size:14px">Gemini CLI</b> <span style="display:inline" class="small muted">· Tiene una pregunta</span></span><span class="chip solid"><i></i>Responder</span></section>
  </div>`);

const C = page({
  title: "3 · Koda al frente",
  lede: "Koda grande y reactivo es el centro: te cuenta en una frase qué pasa, cambia de pose con cada estado y responde al toque. Navegación en una hoja inferior, letra más grande y emparejar como un recorrido de tres pasos con el QR.",
  controls: `<button class="cbtn" id="ctl-koda" type="button">Koda: Trabajando</button>`,
  phones: [r1, r2, r3, r4, r5],
  better: {
    ui: [
      "Koda a 192 px sobre el velo del color de rol, con una frase que resume; títulos de 24–26 px, botones de 56–60 px y más aire.",
      "Los colores de rol de system.md solo en el velo, el chip de estado y la acción principal; widgets con Koda como figura y el anillo de uso en tinta.",
    ],
    ux: [
      "Navegación en una hoja inferior: pestañas grandes al alcance del pulgar y, al deslizar, uso del plan y recientes sin cambiar de pantalla.",
      "Emparejar en tres pasos con barra de progreso, el QR en el medio, Atrás en cada paso y «Mejor pegar el enlace» siempre visible.",
    ],
    cx: [
      "La app tiene personalidad: Koda reacciona al toque y te habla en una frase en vez de un tablero.",
      "El primer uso explica qué pasa y por qué la cámara; el chat sugiere preguntas útiles («¿Quién me espera?»).",
    ],
    trade: [
      "Menos información por pantalla: uso del plan, servicios y recientes quedan dentro de la hoja; con varios pedidos a la vez, una sola frase no alcanza.",
      "El personaje grande puede competir con un pedido urgente si no se cuida el orden; más trabajo de animación y de pruebas en pantallas chicas y con texto grande.",
    ],
  },
  js: `
const ST=[
 {pose:'trabaja',r:'live',chip:'Trabajando',say:'Claude Code está compilando kenai. Codex corre los tests.'},
 {pose:'espera',r:'sun',chip:'Espera tu sí',say:'Claude Code te espera: quiere ejecutar un comando.',act:'Revisar el pedido'},
 {pose:'termino',r:'go',chip:'Terminó',say:'¡Listo! Hoy terminaron 5 tareas.'},
 {pose:'reposo',r:'faint',chip:'Durmiendo',say:'Todo tranquilo. Te aviso si alguien te necesita.'},
 {pose:'error',r:'red',chip:'Error',say:'Codex se detuvo por un error en npm test.',act:'Ver qué pasó'}];
let si=0;
function setKoda(i){si=i;const s=ST[i];koda($('#koda3'),s.pose);$('#hero3').dataset.r=s.r;$('#chip3').dataset.r=s.r;
  const faint=s.r==='faint';$('#chip3').classList.toggle('solid',!faint);$('#chip3t').textContent=s.chip;$('#say').textContent=s.say;
  $('#act3').hidden=!s.act;if(s.act)$('#act3').textContent=s.act;$('#ctl-koda').textContent='Koda: '+s.chip;}
$('#kodabtn').addEventListener('click',()=>setKoda((si+1)%ST.length));
$('#ctl-koda').addEventListener('click',()=>setKoda((si+1)%ST.length));
$('#handle').addEventListener('click',e=>{const open=!$('#sheetnav').hasAttribute('data-open');$('#sheetnav').toggleAttribute('data-open',open);
  $('#navscrim').hidden=!open;e.currentTarget.setAttribute('aria-expanded',String(open));e.currentTarget.textContent=open?'Tocá para cerrar':'Deslizá o tocá para ver más';});
$('#navscrim').addEventListener('click',()=>$('#handle').click());
function step(n){$$('[data-step]').forEach(s=>s.hidden=s.dataset.step!==String(n));$$('#steps i').forEach((b,i)=>b.classList.toggle('on',i<n));
  $('#steps').setAttribute('aria-valuenow',n);$('#steplabel').textContent='Paso '+n+' de 3';}
$$('[data-go]').forEach(b=>b.addEventListener('click',()=>step(+b.dataset.go)));
`,
});

const files = [
  ["01-decidi-en-un-toque.html", A],
  ["02-timeline-del-dia.html", B],
  ["03-koda-al-frente.html", C],
];
for (const [f, h] of files) fs.writeFileSync(new URL(f, OUT), h);
console.log("ok", files.map((x) => `${x[0]} ${(x[1].length / 1024).toFixed(1)} KB`).join(", "));
