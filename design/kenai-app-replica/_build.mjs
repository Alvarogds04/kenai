// Builds design/kenai-app-replica/01-app-replica.html from _src/ (run: node design/kenai-app-replica/_build.mjs).
// Reads, never writes, the app's licence assets and the Koda sprite crop in design/_build/koda.json.
import fs from "node:fs";

const here = new URL("./", import.meta.url);
const repo = new URL("../../", here);
const read = (u) => fs.readFileSync(u, "utf8");
const JS = ["data.js", "ui.js", "model.js", "screens-home.js", "screens-settings.js", "screens-chat.js", "sheets.js", "main.js", "widgets-data.js", "widgets-render.js", "widgets.js"];

const koda = read(new URL("design/_build/koda.json", repo)).trim();
const lic = ["NOTICES.txt", "MIT.txt", "Apache-2.0.txt"].map((f) => read(new URL("app/src/main/assets/licenses/" + f, repo))).join("\n\n");
// Every English text of the app, verbatim from res/values/strings.xml.
const strings = {};
for (const m of read(new URL("app/src/main/res/values/strings.xml", repo)).matchAll(/<string name="([^"]+)">([\s\S]*?)<\/string>/g)) {
  strings[m[1]] = m[2].replace(/\\'/g, "'").replace(/\\"/g, '"').replace(/&gt;/g, ">").replace(/&lt;/g, "<").replace(/&amp;/g, "&");
}
const css = ["app.css", "widgets.css"].map((f) => read(new URL("_src/" + f, here))).join("\n");
const js = JS.filter((f) => fs.existsSync(new URL("_src/" + f, here)))
  .map((f) => `/* ---- ${f} ---- */\n` + read(new URL("_src/" + f, here)))
  .join("\n")
  .replace("__KODA__", () => koda)
  .replace("__LICENCES__", () => JSON.stringify(lic))
  .replace("__STRINGS__", () => JSON.stringify(strings));

const html = `<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=1440">
<title>Kenai for Android · replica of today's app</title>
<style>${css}</style></head>
<body>
<header class="tbar" id="tbar"></header>
<main class="stage">
  <div class="col-phone">
    <div class="cap">Kenai for Android · today</div>
    <div class="phone" id="phone"></div>
    <div class="hint" id="hint">The phone is live: tap it like the real app.</div>
  </div>
  <section id="widgets" class="col-home" aria-label="Home screen widgets"></section>
</main>
<script>
${js}
</script>
</body></html>
`;
fs.writeFileSync(new URL("01-app-replica.html", here), html);
console.log("wrote 01-app-replica.html", html.length, "bytes");
