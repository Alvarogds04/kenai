// Checks design/kenai-mejoras/ artboards. Run from the repo root: node design/kenai-mejoras/_check.cjs
// 1) loads each artboard, toggles the theme, clicks its controls; prints "errors: N" (console + page errors)
// 2) screenshots /tmp/kenai-mejoras-NN-{dark,light}.png (+ -narrow.png at 320 dp)
// 3) gentle-design self-check: every colour literal and font family in the file must be a design/system.md token
// 4) adaptive check: at 320 dp no element spills past its phone edge; Koda freezes with reduced motion
const fs = require("fs");
const path = require("path");
const { chromium } = require(process.env.HOME + "/.local/lib/node_modules/playwright");

const DIR = path.join(process.cwd(), "design/kenai-mejoras");
const FILES = fs.readdirSync(DIR).filter((f) => /^\d\d-.*\.html$/.test(f)).sort();

/* design/system.md values (role, identity, surfaces/ink dark + light, alpha fills) */
const TOKENS = new Set([
  "#b8ff2e", "#ffe100", "#ff5c75", "#2eb4ff", "#6f6d66",
  "#d8ff7a", "#8dff2b", "#3fe00f", "#a8d8ff", "#2ea8ff", "#0a6dff", "#b4ffe3", "#2dffac", "#00d482",
  "#ffd08a", "#ff8a00", "#e05a00", "#e3c4ff", "#b04dff", "#7000ff",
  "#000", "#141518", "#18181b", "#1d1f23", "#f5f6f8", "#f1f2f4", "#8e939c", "#0b0c0e",
  "#ece4d2", "#f7f2e7", "#fbf8f1", "#e7e0d0", "#1a1915", "#2a2823", "#5f5c54", "#201f1b", "#f7f4ec",
  "rgba(255,255,255,.09)", "rgba(255,255,255,.15)", "rgba(255,255,255,.25)", "rgba(255,255,255,.08)",
  "rgba(255,255,255,.06)", "rgba(255,255,255,.1)", "rgba(0,0,0,.06)", "rgba(0,0,0,.12)", "rgba(0,0,0,.28)",
  "rgba(0,0,0,.08)", "rgba(0,0,0,.05)", "rgba(0,0,0,.07)",
]);
/* scrims: #000 (--island-bg dark) at an alpha; not a new hue */
const SCRIMS = new Set(["rgba(0,0,0,.6)", "rgba(0,0,0,.38)"]);
const FONTS = ['ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif', '"Cascadia Mono","Consolas",ui-monospace,monospace'];

function staticCheck(html) {
  const src = html.replace(/data:image\/png;base64,[A-Za-z0-9+/=]+/g, "");
  const cols = new Set([...src.matchAll(/#[0-9a-fA-F]{3,8}\b|rgba?\([^)]*\)/g)].map((m) => m[0].toLowerCase().replace(/\s+/g, "")));
  const bad = [...cols].filter((c) => !TOKENS.has(c) && !SCRIMS.has(c));
  const fams = [...src.matchAll(/--font-(?:ui|mono):([^;]+);/g)].map((m) => m[1].trim());
  const otherFonts = [...src.matchAll(/font-family:([^;"}]+)/g)].map((m) => m[1].trim()).filter((f) => !/^var\(--font-(ui|mono)\)$/.test(f));
  const fontOk = fams.length === 2 && fams.every((f) => FONTS.includes(f)) && otherFonts.length === 0;
  const external = [...src.matchAll(/(?:src|href)\s*=\s*"(https?:)?\/\/[^"]+"|url\(\s*["']?https?:/g)].length;
  return { colours: cols.size, bad, fontOk, external };
}

(async () => {
  const b = await chromium.launch();
  const errs = [];
  for (const f of FILES) {
    const nn = f.slice(0, 2);
    const html = fs.readFileSync(path.join(DIR, f), "utf8");
    const st = staticCheck(html);
    const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
    p.on("console", (m) => m.type() === "error" && errs.push(f + ": " + m.text()));
    p.on("pageerror", (e) => errs.push(f + ": " + e.message));
    await p.goto("file://" + path.join(DIR, f));
    await p.waitForTimeout(400);
    for (const th of ["dark", "light"]) {
      if ((await p.getAttribute("html", "data-theme")) !== th) await p.click("#theme");
      await p.waitForTimeout(300);
      await p.screenshot({ path: `/tmp/kenai-mejoras-${nn}-${th}.png` });
    }
    await p.click("#theme"); // back to dark
    // artboard controls and in-phone interactions
    const strip = await p.$eval(".better", (e) => e.scrollHeight - e.clientHeight);
    const ctl = await p.$$eval(".ctl .cbtn", (bs) => bs.map((x) => x.id).filter((id) => id !== "theme" && id !== "narrow"));
    for (const id of ctl) for (let i = 0; i < 6; i++) await p.click("#" + id);
    let clicks = 0;
    for (const sel of ["[data-decide=denied]", "#fp-touch", "[data-f=error]", "[data-f=need]", "[data-f=all]", "#kodabtn", "#handle", "#handle", "[data-go='3']", "[data-step='3'] [data-go='2']"]) {
      const el = await p.$(sel);
      if (el && (await el.isVisible())) { await el.click(); clicks++; }
    }
    await p.waitForTimeout(1100);
    // narrow screens: 320 dp, nothing past the phone's right edge (horizontal scrollers excluded)
    await p.click("#narrow");
    await p.waitForTimeout(200);
    const spill = await p.$$eval(".phone", (ps) => {
      let n = 0; const where = [];
      for (const ph of ps) {
        const r = ph.getBoundingClientRect();
        for (const el of ph.querySelectorAll("*")) {
          if (el.closest(".agchips,.filters,[hidden]") || el.closest("svg") && el.tagName !== "svg") continue;
          const e = el.getBoundingClientRect();
          if (!e.width || !e.height) continue;
          if (e.right > r.right + 1 || e.left < r.left - 1) { n++; if (where.length < 4) where.push((ph.getAttribute("aria-label") || "") + " > " + el.tagName.toLowerCase() + "." + (el.className.baseVal ?? el.className)); }
        }
      }
      return { n, where };
    });
    await p.screenshot({ path: `/tmp/kenai-mejoras-${nn}-narrow.png` });
    // small targets: buttons/links inside phones under 44 px in either side (painted size, before zoom)
    const small = await p.$$eval(".phone button:not([hidden]), .phone a", (bs) => bs.filter((x) => {
      if (x.closest("[hidden]")) return false; const r = x.getBoundingClientRect(); if (!r.width) return false;
      return r.height / 0.7 < 43.5 || r.width / 0.7 < 43.5; }).map((x) => x.textContent.trim().slice(0, 24) || x.getAttribute("aria-label")));
    await p.close();
    // reduced motion: Koda keeps its first frame
    const ctx = await b.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: "reduce" });
    const q = await ctx.newPage();
    q.on("pageerror", (e) => errs.push(f + " (reduced): " + e.message));
    await q.goto("file://" + path.join(DIR, f));
    const pos = () => q.$$eval("[data-koda]", (ks) => ks.map((k) => k.style.backgroundPosition).join("|"));
    const a = await pos(); await q.waitForTimeout(700); const c = await pos();
    await ctx.close();
    console.log(`${f}: colours ${st.colours} (${st.bad.length ? "NOT tokens: " + st.bad.join(" ") : "all system.md tokens"}), fonts ${st.fontOk ? "system.md stacks only" : "DEVIATION"}, external URLs ${st.external}, controls ${ctl.length}, phone clicks ${clicks}, 320dp spill ${spill.n}${spill.n ? " " + spill.where.join("; ") : ""}, targets <44px ${small.length}${small.length ? " [" + small.join(", ") + "]" : ""}, reduced-motion Koda frozen ${a === c}, "Qué mejora" overflow ${strip}px`);
  }
  console.log("errors:", errs.length, errs.join("\n"));
  await b.close();
})();
