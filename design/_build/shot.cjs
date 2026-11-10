const { chromium } = require(process.env.HOME + "/.local/lib/node_modules/playwright");
(async () => {
  const b = await chromium.launch(); const errs = [];
  for (const f of process.argv.slice(2)) {
    const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
    p.on("console", m => m.type() === "error" && errs.push(f + ": " + m.text())); p.on("pageerror", e => errs.push(f + ": " + e.message));
    await p.goto("file://" + process.cwd() + "/design/movil-identidad-pantallas-base/" + f);
    for (const th of ["dark", "light"]) {
      if ((await p.getAttribute("html", "data-theme")) !== th) await p.click("#theme");
      await p.waitForTimeout(500);
      await p.screenshot({ path: `/tmp/dm-${f.slice(0, 2)}-${th}.png` });
    }
    for (let i = 0; i < 7; i++) await p.click("#react");
    await p.close();
  }
  console.log("errors:", errs.length, errs.join("\n")); await b.close();
})();
