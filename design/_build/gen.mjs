// Generates the three self-contained artboards of design/movil-identidad-pantallas-base/.
// Run: node design/_build/gen.mjs   (reads koda.json, a crop of the HU-055 Koda Chibi sprites)
import fs from "node:fs";
const K = JSON.parse(fs.readFileSync(new URL("./koda.json", import.meta.url)));
const OUT = new URL("../movil-identidad-pantallas-base/", import.meta.url);
fs.mkdirSync(OUT, { recursive: true });

/* ---------- shared CSS: tokens from design/system.md only ---------- */
const CSS = `
:root{
  /* design/system.md — role colours, never themed */
  --go:#b8ff2e;--sun:#ffe100;--red-11:#ff5c75;--island-live:#2eb4ff;--faint:#6f6d66;
  --on-role:#0b0c0e; /* ink on a role-colour fill (system.md --primary-ink, dark) */
  /* Dot identity families (never carry a state) */
  --id-abito:#8dff2b;--id-pi:#2dffac;--id-claude:#ff8a00;--id-codex:#2ea8ff;
  --font-ui:ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif;
  --font-mono:"Cascadia Mono","Consolas",ui-monospace,monospace;
  --radius-xl:12px;--radius-2xl:18px;--radius-full:999px;
  --ease-out:cubic-bezier(.23,1,.32,1);
}
[data-theme=dark]{
  --bg:#000;--card:#141518;--surface:#18181b;--tab-on:#1D1F23;
  --ink:#F5F6F8;--ink-2:#F1F2F4;--muted:#8E939C;
  --primary-bg:#F5F6F8;--primary-ink:#0B0C0E;--btn:rgba(255,255,255,.09);--btn-h:rgba(255,255,255,.15);
  --line:rgba(255,255,255,.25);--line-faint:rgba(255,255,255,.08);--fill:rgba(255,255,255,.06);--bubble:rgba(255,255,255,.1);
  --veil-a:22%;--sys:#F5F6F8;--wall:#0a0b0e;--focus:#fff;
}
[data-theme=light]{
  --bg:#ece4d2;--card:#f7f2e7;--surface:#fbf8f1;--tab-on:#e7e0d0;
  --ink:#1a1915;--ink-2:#2a2823;--muted:#5f5c54;
  --primary-bg:#201f1b;--primary-ink:#f7f4ec;--btn:rgba(0,0,0,.06);--btn-h:rgba(0,0,0,.12);
  --line:rgba(0,0,0,.28);--line-faint:rgba(0,0,0,.08);--fill:rgba(0,0,0,.05);--bubble:rgba(0,0,0,.07);
  --veil-a:34%;--sys:#1a1915;--wall:#d9d2c0;--focus:#0b3fd1;
}
*{box-sizing:border-box}
html,body{margin:0}
body{width:1440px;height:900px;overflow:hidden;background:var(--wall);color:var(--ink);font:14px/1.4 var(--font-ui);-webkit-font-smoothing:antialiased}
button{font:inherit;color:inherit;cursor:pointer}
:focus-visible{outline:2px solid var(--focus);outline-offset:2px}
header.top{position:absolute;left:32px;right:32px;top:14px;height:64px;display:flex;align-items:center;justify-content:space-between;gap:24px}
header.top h1{margin:0;font-size:18px;font-weight:600}
header.top p{margin:2px 0 0;color:var(--muted);font-size:13px;max-width:880px}
.ctl{display:flex;gap:8px}
.cbtn{min-height:44px;padding:0 16px;border:0;border-radius:99px;background:var(--btn);font-weight:600;font-size:13px}
.cbtn:hover{background:var(--btn-h)}
main.row{position:absolute;left:0;right:0;top:86px;display:flex;justify-content:center;gap:20px}
footer.note{position:absolute;left:32px;right:32px;bottom:10px;display:grid;grid-template-columns:1fr 1fr 1fr;gap:20px;font-size:12px;color:var(--muted);line-height:1.35}
footer.note b{color:var(--ink);font-weight:600}
figure{margin:0}
figcaption{margin:0 0 6px 4px;font-size:12px;font-weight:600;color:var(--muted);text-transform:uppercase;letter-spacing:.04em}
/* ---------- phone ---------- */
.phone{position:relative;width:330px;height:680px;border-radius:34px;background:var(--bg);overflow:hidden;box-shadow:0 0 0 1px var(--line-faint),0 0 0 8px #0c0d10;display:flex;flex-direction:column}
[data-theme=light] .phone{box-shadow:0 0 0 1px var(--line-faint),0 0 0 8px #2a2823}
.sbar{flex:none;height:26px;display:flex;align-items:center;justify-content:space-between;padding:0 18px;font-size:12px;font-weight:600;color:var(--sys)}
.sbar i{display:inline-block;width:11px;height:7px;border-radius:2px;background:var(--sys);margin-left:4px;opacity:.9}
.gest{flex:none;height:22px;display:flex;align-items:center;justify-content:center}
.gest::after{content:"";width:104px;height:4px;border-radius:2px;background:var(--sys);opacity:.85}
.body{position:relative;flex:1;min-height:0;display:flex;flex-direction:column}
.scroll{flex:1;min-height:0;overflow:hidden;padding:0 12px}
/* ---------- state veil ---------- */
[data-st=reposo]{--c:var(--faint)}[data-st=escucha]{--c:var(--island-live)}[data-st=piensa]{--c:var(--island-live)}
[data-st=trabaja]{--c:var(--island-live)}[data-st=espera]{--c:var(--sun)}[data-st=termino]{--c:var(--go)}[data-st=error]{--c:var(--red-11)}
.veil{background:linear-gradient(180deg,color-mix(in srgb,var(--c) var(--veil-a),var(--card)) 0%,var(--card) 92%)}
/* ---------- parts ---------- */
.koda{display:inline-block;image-rendering:pixelated;background-repeat:no-repeat;flex:none}
.chip{display:inline-flex;align-items:center;gap:6px;font-size:12px;font-weight:600;padding:2px 9px 2px 7px;border-radius:99px;background:color-mix(in srgb,var(--c) 26%,var(--card));color:var(--ink);min-height:22px}
.chip i{width:8px;height:8px;border-radius:50%;background:var(--c)}
.chip.sunk{background:var(--c);color:var(--on-role)}
.dot{width:10px;height:10px;border-radius:50%;flex:none}
.card{background:var(--card);border-radius:var(--radius-2xl);box-shadow:inset 0 0 0 1px var(--line-faint)}
.btn{position:relative;display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:48px;padding:0 20px;border:0;border-radius:99px;background:var(--btn);font-weight:600;font-size:14px;color:var(--ink)}
.btn.pri{background:var(--primary-bg);color:var(--primary-ink)}
.btn.go{background:var(--go);color:var(--on-role)}
.btn.block{width:100%}
.h{font-weight:700;letter-spacing:-.01em}
.muted{color:var(--muted)}
.mono{font-family:var(--font-mono);font-size:12.5px}
.nav{flex:none;display:flex;gap:4px;margin:0 12px 6px;padding:6px;border-radius:99px;background:var(--card);box-shadow:inset 0 0 0 1px var(--line-faint)}
.nav a{flex:1;min-height:48px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1px;border-radius:99px;font-size:11.5px;font-weight:600;color:var(--muted);text-decoration:none}
.nav a[aria-current]{background:var(--tab-on);color:var(--ink)}
.nav svg,.ic{width:22px;height:22px;stroke:currentColor;fill:none;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round;flex:none}
.sheet{position:absolute;left:0;right:0;bottom:0;background:var(--surface);border-radius:28px 28px 0 0;padding:10px 20px 8px;box-shadow:0 -1px 0 var(--line-faint)}
.sheet .grab{width:36px;height:4px;border-radius:2px;background:var(--line);margin:0 auto 14px}
.scrim{position:absolute;inset:0;background:rgba(0,0,0,.55)}
[data-theme=light] .scrim{background:rgba(26,25,21,.38)}
.fp{display:flex;gap:10px;align-items:flex-start;font-size:12px;color:var(--muted);margin:12px 0 6px}
.row2{display:flex;gap:10px}
.row2 .btn{flex:1}
.agent{display:flex;align-items:center;gap:12px;min-height:64px;padding:8px 14px}
.agent .t{flex:1;min-width:0}
.agent .t b{display:block;font-size:15px}
.agent .t span{font-size:13px;color:var(--muted);display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.bubble{max-width:82%;padding:10px 14px;border-radius:18px;font-size:14px}
.bubble.me{align-self:flex-end;background:var(--bubble);border-bottom-right-radius:6px}
.bubble.ab{align-self:flex-start;background:var(--card);border-bottom-left-radius:6px;box-shadow:inset 0 0 0 1px var(--line-faint)}
.composer{display:flex;gap:8px;align-items:center;padding:8px 12px}
.field{flex:1;min-height:48px;display:flex;align-items:center;padding:0 16px;border-radius:99px;background:var(--fill);color:var(--muted);font-size:14px;box-shadow:inset 0 0 0 1px var(--line-faint)}
.send{width:48px;height:48px;border-radius:50%;border:0;background:var(--primary-bg);color:var(--primary-ink);display:flex;align-items:center;justify-content:center}
.cmd{background:var(--card);border-radius:12px;padding:10px 12px;margin:10px 0 4px;color:var(--ink-2);box-shadow:inset 0 0 0 1px var(--line-faint);word-break:break-all}
.sec{font-size:12px;text-transform:uppercase;letter-spacing:.05em;margin:0 4px 6px;color:var(--muted);font-weight:600}
@media (prefers-reduced-motion:no-preference){.veil,.chip{transition:background .4s var(--ease-out)}}
`;

/* ---------- Koda: sprite from HU-055 (design/hu-055-koda/sprites), animated with its own sequences ---------- */
const LBL = { reposo: "en reposo", escucha: "escuchando", piensa: "pensando", trabaja: "trabajando", espera: "esperando tu sí", termino: "terminó", error: "con error" };
const kodaEl = (state, scale = 3, cycle = false) =>
  `<span class="koda" data-koda="${state}" ${cycle ? "data-cycle" : ""} data-scale="${scale}" role="img" aria-label="Koda, ${LBL[state]}" style="width:${32 * scale}px;height:${32 * scale}px"></span>`;

const JS = `
const KODA=${JSON.stringify(K)};
const reduce=matchMedia('(prefers-reduced-motion: reduce)').matches;
function paint(el,f){const s=+el.dataset.scale,st=KODA.seq[el.dataset.koda];
  el.style.backgroundImage='url('+KODA.s32+')';el.style.backgroundSize=(128*s)+'px '+(224*s)+'px';
  el.style.backgroundPosition='-'+(f*32*s)+'px -'+(st.row*32*s)+'px';}
const ticks=new WeakMap();
document.querySelectorAll('[data-koda]').forEach(el=>{ticks.set(el,0);paint(el,KODA.seq[el.dataset.koda].seq[0]);});
if(!reduce)setInterval(()=>{document.querySelectorAll('[data-koda]').forEach(el=>{const st=KODA.seq[el.dataset.koda];
  const t=(ticks.get(el)+1)%st.seq.length;ticks.set(el,t);paint(el,st.seq[t]);});},125);
document.getElementById('theme').addEventListener('click',e=>{const d=document.documentElement;
  const n=d.dataset.theme==='dark'?'light':'dark';d.dataset.theme=n;e.currentTarget.setAttribute('aria-pressed',n==='light');
  e.currentTarget.textContent=n==='light'?'Tema: claro':'Tema: oscuro';});
const ORDER=['reposo','escucha','piensa','trabaja','espera','termino','error'];
const NAMES={reposo:'En reposo',escucha:'Escuchando',piensa:'Pensando',trabaja:'Trabajando',espera:'Espera tu sí',termino:'Terminó',error:'Con error'};
let i=ORDER.indexOf('trabaja');
document.getElementById('react').addEventListener('click',()=>{i=(i+1)%ORDER.length;const s=ORDER[i];
  document.querySelectorAll('[data-cycle]').forEach(el=>{el.dataset.koda=s;el.setAttribute('aria-label','Koda, '+NAMES[s].toLowerCase());});
  document.querySelectorAll('[data-cyclest]').forEach(el=>{el.dataset.st=s;});
  document.querySelectorAll('[data-cyclelabel]').forEach(el=>{el.textContent=NAMES[s];});
  document.getElementById('react').textContent='Koda: '+NAMES[s];});
`;

const ico = {
  home: `<svg viewBox="0 0 24 24"><path d="M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"/></svg>`,
  chat: `<svg viewBox="0 0 24 24"><path d="M4 5h16v11H9l-5 4z"/></svg>`,
  cfg: `<svg viewBox="0 0 24 24"><path d="M4 8h9M17 8h3M4 16h3M11 16h9"/><circle cx="15" cy="8" r="2"/><circle cx="9" cy="16" r="2"/></svg>`,
  send: `<svg class="ic" viewBox="0 0 24 24"><path d="M5 12h13M13 6l6 6-6 6"/></svg>`,
  qr: `<svg class="ic" viewBox="0 0 24 24"><path d="M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M8 9h2v2H8zM14 9h2v2h-2zM8 14h2v2H8zM13 14h3v2h-3z"/></svg>`,
  lock: `<svg class="ic" style="width:18px;height:18px" viewBox="0 0 24 24"><rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>`,
  back: `<svg class="ic" viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7"/></svg>`,
  close: `<svg class="ic" viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg>`,
};
const bars = `<div class="sbar" aria-hidden="true"><span>9:41</span><span>5G<i></i><i style="width:16px"></i></span></div>`;
const gest = `<div class="gest" aria-hidden="true"></div>`;
const nav = (cur) =>
  `<nav class="nav" aria-label="Secciones">${[["home", "Inicio"], ["chat", "Chat"], ["cfg", "Ajustes"]]
    .map(([k, t]) => `<a href="#" ${cur === k ? 'aria-current="page"' : ""}>${ico[k]}${t}</a>`)
    .join("")}</nav>`;
const phone = (label, inner, st) =>
  `<figure><figcaption>${label}</figcaption><div class="phone" ${st ? `data-st="${st}"` : ""} role="group" aria-label="${label}">${bars}<div class="body">${inner}</div>${gest}</div></figure>`;
const iconBtn = (label, svg) => `<button class="btn" type="button" style="width:48px;padding:0;background:transparent" aria-label="${label}">${svg}</button>`;

const page = ({ title, brief, notes, phones }) => `<!doctype html>
<html lang="es" data-theme="dark"><head><meta charset="utf-8"><meta name="viewport" content="width=1440"><title>${title}</title>
<style>${CSS}</style></head><body>
<header class="top"><div><h1>${title}</h1><p>${brief}</p></div>
<div class="ctl"><button class="cbtn" id="react" type="button">Koda: Trabajando</button><button class="cbtn" id="theme" type="button" aria-pressed="false">Tema: oscuro</button></div></header>
<main class="row">${phones.join("")}</main>
<footer class="note">${notes.map(([h, t]) => `<div><b>${h}</b> ${t}</div>`).join("")}</footer>
<script>${JS}</script></body></html>`;

/* ---------- common copy ---------- */
const CMD = "npm run migrate -- --env staging";
const fpNote = `Permitir en el teléfono pide tu huella o el bloqueo de pantalla. Denegar no.`;
const androidNotes = [
  "Android:",
  "barras de estado y de gestos respetadas; objetivos de 48 dp o más; Atrás cierra la hoja; ningún gesto es obligatorio. Emparejar, chat y aprobación llevarán FLAG_SECURE como hoy.",
];
const a11y = [
  "Accesibilidad:",
  "contraste AA en oscuro y claro (colores de rol solo como relleno, con tinta #0b0c0e encima), foco visible, Koda con etiqueta de texto; con movimiento reducido Koda queda en su primer cuadro.",
];
const chatBody = `<div class="bubble me">¿Qué terminó mientras no estaba?</div>
      <div class="bubble ab">Pi terminó la migración: se aplicó y pasan 14 tests. Claude Code espera tu sí para correr <span class="mono">npm run migrate</span>.</div>
      <div class="bubble me">Dejalo esperando, lo reviso en el escritorio.</div>`;
const composer = `<div class="composer"><div class="field">Escribí un mensaje</div><button class="send" type="button" aria-label="Enviar">${ico.send}</button></div>`;

/* =============== A · Isla en el bolsillo (evolución de hoy) =============== */
const A = page({
  title: "A · Isla en el bolsillo",
  brief:
    "Evolución de la app de hoy: una tarjeta-isla arriba con Koda y el agente en foco, los demás agentes en lista debajo, barra inferior de tres secciones y el permiso como hoja inferior.",
  notes: [
    ["Compromiso:", "la más cercana a lo que ya existe (poco riesgo al implementar); el permiso exige abrir una hoja, un toque más que resolverlo en la tarjeta, y con muchos agentes la lista crece bajo una tarjeta fija."],
    androidNotes,
    a11y,
  ],
  phones: [
    phone(
      "Emparejar",
      `<div class="scroll" style="display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;gap:14px;padding:0 24px">
      ${kodaEl("reposo", 4)}
      <h2 class="h" style="font-size:22px;margin:4px 0 0">Conectá con tu computadora</h2>
      <p class="muted" style="margin:0;font-size:14px">En tu computadora abrí Abito, después Ajustes, Teléfono Android, y escaneá el código.</p>
      <button class="btn pri block" type="button" style="margin-top:6px">${ico.qr}Escanear código QR</button>
      <div class="row2" style="width:100%"><button class="btn" type="button">Pegar el enlace</button><button class="btn" type="button">Probar la demo</button></div>
      <span class="muted" style="font-size:12px;margin-top:6px">Abito para Android · solo en tu red local</span></div>`,
      "reposo"
    ),
    phone(
      "Inicio",
      `<div class="scroll" style="padding:0">
      <section class="veil" data-st="trabaja" data-cyclest style="border-radius:0 0 30px 30px;padding:8px 18px 20px;box-shadow:inset 0 -1px 0 var(--line-faint)">
        <div style="display:flex;justify-content:space-between;align-items:center;min-height:32px;font-size:12.5px;color:var(--muted)"><span><span class="dot" style="display:inline-block;background:var(--go);margin-right:6px"></span>Estudio-PC</span><span>3 en curso</span></div>
        <div style="display:flex;align-items:center;gap:14px;margin-top:6px">${kodaEl("trabaja", 3, true)}
          <div><div class="h" style="font-size:20px">Claude Code</div><div class="muted" style="font-size:13px">Migrando tablas de pedidos</div>
          <span class="chip" style="margin-top:8px"><i></i><span data-cyclelabel>Trabajando</span></span></div></div>
      </section>
      <div style="display:flex;flex-direction:column;gap:8px;margin:12px">
        <div class="card agent" data-st="espera"><span class="dot" style="background:var(--id-codex)"></span><div class="t"><b>Codex</b><span>Quiere correr un comando</span></div><span class="chip sunk"><i style="background:var(--on-role)"></i>Tu sí</span></div>
        <div class="card agent" data-st="termino"><span class="dot" style="background:var(--id-pi)"></span><div class="t"><b>Pi</b><span>Migración aplicada, 14 tests pasan</span></div><span class="chip"><i></i>Listo</span></div>
        <div class="card agent" data-st="reposo"><span class="dot" style="background:var(--id-abito)"></span><div class="t"><b>Abito</b><span>Todo tranquilo</span></div><span class="chip"><i></i>Reposo</span></div>
      </div></div>${nav("home")}`,
      ""
    ),
    phone(
      "Aprobación",
      `<div class="scroll" style="opacity:.55"><div class="card agent" style="margin-top:12px"><span class="dot" style="background:var(--id-claude)"></span><div class="t"><b>Claude Code</b><span>Quiere ejecutar un comando</span></div></div></div>
     <div class="scrim"></div>
     <section class="sheet" data-st="espera" aria-label="Pedido de permiso">
      <div class="grab"></div>
      <div style="display:flex;gap:12px;align-items:center">${kodaEl("espera", 2)}<div><div class="h" style="font-size:17px">Claude Code</div><div class="muted" style="font-size:13px">Quiere ejecutar un comando</div></div></div>
      <div class="cmd mono">${CMD}</div>
      <p class="muted" style="margin:6px 2px;font-size:13px">Permitir solo si lo pediste vos. Caduca en 4:32.</p>
      <div class="fp">${ico.lock}<span>${fpNote}</span></div>
      <div class="row2" style="margin-top:6px"><button class="btn" type="button">Denegar</button><button class="btn go" type="button">Permitir</button></div>
      <button class="btn block" type="button" style="margin-top:8px;background:transparent">Ahora no</button></section>`,
      "espera"
    ),
    phone(
      "Chat",
      `<div class="scroll" style="display:flex;flex-direction:column;gap:10px;padding:8px 12px">
      <div class="h" style="font-size:20px;min-height:40px;display:flex;align-items:center">Chat</div>
      ${chatBody}
      <div style="align-self:flex-start;display:flex;align-items:center;gap:8px" class="muted">${kodaEl("escucha", 2)}<span style="font-size:12px">Abito está escribiendo…</span></div></div>
     ${composer}${nav("chat")}`,
      "escucha"
    ),
  ],
});

/* =============== B · Bandeja de pendientes =============== */
const topbar = (t, right = "") =>
  `<div style="display:flex;align-items:center;gap:10px;min-height:56px;padding:0 14px 0 16px">${kodaEl("reposo", 1)}<div style="flex:1"><div class="h" style="font-size:18px">${t}</div></div>${right}</div>`;
const B = page({
  title: "B · Bandeja de pendientes",
  brief:
    "El móvil como bandeja: lo que espera tu sí va primero y se resuelve en la propia tarjeta; después lo que está en curso y lo ya terminado. Koda es la mascota de la barra superior y crece solo en el pedido de permiso.",
  notes: [
    ["Compromiso:", "lo más rápido para responder un permiso (un toque desde Inicio) y se lee de un vistazo; Koda pierde protagonismo en Inicio y cuesta más «ver vivo» al agente en foco."],
    androidNotes,
    a11y,
  ],
  phones: [
    phone(
      "Emparejar",
      `<div class="scroll" style="padding:8px 20px">
      <h2 class="h" style="font-size:22px;margin:18px 0 6px">Conectá con tu computadora</h2>
      <p class="muted" style="margin:0 0 18px">Tres pasos, una sola vez. Todo queda en tu red local.</p>
      <ol style="list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:10px">
        ${["Abrí Abito en tu computadora.", "Andá a Ajustes y después a Teléfono Android.", "Escaneá el código que aparece."]
          .map((s, n) => `<li class="card" style="display:flex;gap:12px;align-items:center;min-height:56px;padding:8px 14px"><span style="width:28px;height:28px;border-radius:50%;background:var(--tab-on);display:flex;align-items:center;justify-content:center;font-weight:700;font-size:13px;flex:none">${n + 1}</span><span style="font-size:14px">${s}</span></li>`)
          .join("")}</ol>
      <div style="margin-top:20px;display:flex;flex-direction:column;gap:8px">
        <button class="btn pri block" type="button">${ico.qr}Escanear código QR</button>
        <button class="btn block" type="button">Pegar el enlace</button>
        <button class="btn block" type="button" style="background:transparent;text-decoration:underline">Probar la demo</button></div></div>`,
      ""
    ),
    phone(
      "Inicio",
      `${topbar("Abito", `<span class="chip" data-st="termino"><i></i>Estudio-PC</span>`)}
     <div class="scroll" style="display:flex;flex-direction:column;gap:14px;padding-top:4px">
      <section aria-labelledby="b1"><h2 id="b1" class="sec">Esperan tu sí · 1</h2>
        <div class="card" data-st="espera" style="padding:14px;background:color-mix(in srgb,var(--sun) 16%,var(--card))">
          <div style="display:flex;gap:12px;align-items:center">${kodaEl("espera", 2)}<div><b style="font-size:15px">Claude Code</b><div class="muted" style="font-size:13px">Quiere ejecutar un comando</div></div></div>
          <div class="cmd mono" style="margin:10px 0">${CMD}</div>
          <div class="row2"><button class="btn" type="button">Denegar</button><button class="btn go" type="button">Permitir</button></div></div></section>
      <section aria-labelledby="b2"><h2 id="b2" class="sec">En curso · 2</h2>
        <div style="display:flex;flex-direction:column;gap:8px">
          <div class="card agent" data-st="trabaja"><span class="dot" style="background:var(--id-claude)"></span><div class="t"><b>Claude Code</b><span>Migrando tablas de pedidos</span></div><span class="chip"><i></i>Trabaja</span></div>
          <div class="card agent" data-st="piensa"><span class="dot" style="background:var(--id-codex)"></span><div class="t"><b>Codex</b><span>Buscando en 3 archivos</span></div><span class="chip"><i></i>Piensa</span></div></div></section>
      <section aria-labelledby="b3"><h2 id="b3" class="sec">Terminaron hoy · 1</h2>
        <div class="card agent" data-st="termino"><span class="dot" style="background:var(--id-pi)"></span><div class="t"><b>Pi</b><span>Migración aplicada, 14 tests pasan</span></div><span class="chip"><i></i>Listo</span></div></section></div>${nav("home")}`,
      ""
    ),
    phone(
      "Aprobación",
      `<div style="display:flex;align-items:center;min-height:56px;padding:0 8px">${iconBtn("Volver", ico.back)}<div class="h" style="font-size:17px;margin-left:4px">Pedido de permiso</div></div>
     <div class="scroll" style="padding:0 16px;display:flex;flex-direction:column" data-st="espera">
      <div class="veil" style="border-radius:var(--radius-2xl);padding:18px;display:flex;flex-direction:column;align-items:center;text-align:center;gap:6px">${kodaEl("espera", 4)}
        <div class="h" style="font-size:20px">Claude Code</div><div class="muted">Quiere ejecutar un comando</div><span class="chip sunk"><i style="background:var(--on-role)"></i>Caduca en 4:32</span></div>
      <div class="sec" style="margin-top:16px">Comando exacto</div>
      <div class="cmd mono" style="margin-top:0">${CMD}</div>
      <p class="muted" style="margin:8px 4px;font-size:13px">Permitir solo si lo pediste vos.</p>
      <div class="fp">${ico.lock}<span>${fpNote}</span></div>
      <div style="margin-top:auto;display:flex;flex-direction:column;gap:8px;padding-bottom:6px"><button class="btn go block" type="button" style="min-height:56px;font-size:16px">Permitir</button><button class="btn block" type="button" style="min-height:56px">Denegar</button></div></div>`,
      "espera"
    ),
    phone(
      "Chat",
      `${topbar("Chat", `<span class="muted" style="font-size:12px">Estudio-PC</span>`)}
     <div class="scroll" style="display:flex;flex-direction:column;gap:10px;padding-top:6px">
      <div class="bubble me">¿Qué terminó mientras no estaba?</div>
      <div class="bubble ab">Pi terminó la migración: se aplicó y pasan 14 tests. Claude Code espera tu sí para correr <span class="mono">npm run migrate</span>.</div>
      <div class="card" data-st="espera" style="align-self:stretch;padding:6px 6px 6px 14px;display:flex;align-items:center;gap:10px;background:color-mix(in srgb,var(--sun) 16%,var(--card))"><span style="flex:1;font-size:13px">1 permiso espera tu sí</span><button class="btn" type="button">Ver</button></div></div>
     ${composer}${nav("chat")}`,
      ""
    ),
  ],
});

/* =============== C · Una cosa a la vez =============== */
const C = page({
  title: "C · Una cosa a la vez",
  brief:
    "Sin barra inferior: Koda grande al centro sobre el velo de color del estado, un solo agente en foco y fichas para cambiar de agente. El chat y los ajustes se abren desde un botón; el permiso ocupa toda la pantalla.",
  notes: [
    ["Compromiso:", "la más expresiva y la que más usa a Koda; chat y ajustes cuestan más de descubrir (sin barra) y el deslizar-para-permitir necesita siempre los botones como alternativa accesible, así que repite la acción."],
    androidNotes,
    a11y,
  ],
  phones: [
    phone(
      "Emparejar",
      `<div class="scroll veil" data-st="reposo" style="padding:0 24px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;gap:12px">
      ${kodaEl("escucha", 6)}
      <h2 class="h" style="font-size:24px;margin:8px 0 0">Hola, soy Koda</h2>
      <p class="muted" style="margin:0 0 6px;font-size:14px">Mostrame el código QR de Abito en tu computadora y empezamos.</p>
      <button class="btn pri block" type="button">${ico.qr}Escanear código QR</button>
      <div style="display:flex;gap:4px"><button class="btn" type="button" style="background:transparent;text-decoration:underline;padding:0 12px">Pegar el enlace</button><button class="btn" type="button" style="background:transparent;text-decoration:underline;padding:0 12px">Probar la demo</button></div></div>`,
      "reposo"
    ),
    phone(
      "Inicio",
      `<div style="display:flex;justify-content:space-between;align-items:center;min-height:56px;padding:0 8px 0 18px"><span class="muted" style="font-size:13px"><span class="dot" style="display:inline-block;background:var(--go);margin-right:6px"></span>Estudio-PC</span>${iconBtn("Ajustes", ico.cfg.replace("<svg", '<svg class="ic"'))}</div>
     <div class="scroll veil" data-st="trabaja" data-cyclest style="flex:none;height:360px;margin:0 12px;border-radius:28px;display:flex;flex-direction:column;align-items:center;text-align:center;padding:26px 18px 16px;gap:4px">
        ${kodaEl("trabaja", 6, true)}
        <span class="chip" style="margin-top:6px"><i></i><span data-cyclelabel>Trabajando</span></span>
        <div class="h" style="font-size:24px;margin-top:6px">Claude Code</div>
        <div class="muted" style="font-size:14px">Migrando tablas de pedidos</div>
        <div style="margin-top:14px;width:100%;height:6px;border-radius:3px;background:var(--fill)"><div style="width:62%;height:100%;border-radius:3px;background:var(--c)"></div></div>
        <div class="muted" style="font-size:12px;align-self:flex-end">paso 3 de 5</div></div>
     <div role="tablist" aria-label="Agentes" style="display:flex;gap:8px;padding:12px;overflow:hidden">
        ${[["Claude Code", "var(--id-claude)", true], ["Codex", "var(--id-codex)", false], ["Pi", "var(--id-pi)", false]]
          .map(([n, c, on]) => `<button role="tab" aria-selected="${on}" class="btn" type="button" style="padding:0 14px;${on ? "background:var(--tab-on);" : ""}"><span class="dot" style="background:${c}"></span>${n}</button>`)
          .join("")}</div>
     <div style="position:relative;flex:1"><button class="btn pri" type="button" style="position:absolute;right:16px;top:4px;width:56px;height:56px;padding:0;border-radius:18px" aria-label="Chat con Abito">${ico.chat.replace("<svg", '<svg class="ic"')}</button>
       <span class="muted" style="position:absolute;left:18px;top:20px;font-size:12px;max-width:190px">Hay un permiso esperando: deslizá hacia arriba</span></div>`,
      ""
    ),
    phone(
      "Aprobación",
      `<div class="veil" data-st="espera" style="flex:none;height:300px;border-radius:0 0 28px 28px;display:flex;flex-direction:column;align-items:center;text-align:center;padding:14px 20px 12px;gap:4px">
      ${kodaEl("espera", 5)}<span class="chip sunk" style="margin-top:6px"><i style="background:var(--on-role)"></i>Caduca en 4:32</span>
      <div class="h" style="font-size:19px;margin-top:6px">Claude Code quiere ejecutar un comando</div></div>
     <div class="scroll" style="padding:0 18px">
      <div class="cmd mono" style="margin-top:14px">${CMD}</div>
      <p class="muted" style="margin:6px 2px;font-size:13px">Permitir solo si lo pediste vos.</p>
      <div class="fp">${ico.lock}<span>${fpNote}</span></div></div>
     <div style="padding:0 16px 8px;display:flex;flex-direction:column;gap:8px">
      <div role="group" aria-label="Deslizá para permitir" style="position:relative;height:56px;border-radius:99px;background:color-mix(in srgb,var(--go) 30%,var(--card));display:flex;align-items:center;justify-content:center;font-weight:700;font-size:14px;box-shadow:inset 0 0 0 1px var(--line-faint)"><span style="position:absolute;left:4px;width:48px;height:48px;border-radius:50%;background:var(--go);color:var(--on-role);display:flex;align-items:center;justify-content:center">${ico.send}</span>Deslizá para permitir</div>
      <div class="row2"><button class="btn" type="button">Denegar</button><button class="btn go" type="button">Permitir</button></div></div>`,
      "espera"
    ),
    phone(
      "Chat",
      `<div style="display:flex;align-items:center;gap:8px;min-height:56px;padding:0 8px">${iconBtn("Cerrar chat", ico.close)}<div class="h" style="font-size:17px;flex:1">Chat con Abito</div>${kodaEl("escucha", 1)}</div>
     <div class="scroll" style="display:flex;flex-direction:column;gap:10px;padding-top:6px">${chatBody}</div>
     ${composer}`,
      "escucha"
    ),
  ],
});

const files = [
  ["01-isla-en-el-bolsillo.html", A],
  ["02-bandeja-de-pendientes.html", B],
  ["03-una-cosa-a-la-vez.html", C],
];
for (const [f, h] of files) fs.writeFileSync(new URL(f, OUT), h);
console.log("ok", files.map((x) => x[0]).join(", "));
