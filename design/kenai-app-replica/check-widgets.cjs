// Drives the widget panel of the replica in each mode: steps the demo loop through its 8 steps, checks that the 9 widgets follow
// (texts of core/Glance.kt and core/WidgetData.kt), taps every widget and button and checks the phone's screen, and toggles the
// widgets between dark and light. Run from the repo root: node design/kenai-app-replica/check-widgets.cjs
// Screenshots: /tmp/kenai-replica-w-*.png
const { chromium } = require(process.env.HOME + "/.local/lib/node_modules/playwright");
const path = require("path");

const FILE = "file://" + path.join(__dirname, "01-app-replica.html");
const SIZES = { big: [4, 4], list: [4, 3], alert: [2, 2], ring: [2, 2], pill: [4, 1], glance: [3, 2], solo: [2, 2], today: [2, 2], mood: [1, 1] };

(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const failed = [];
  let passed = 0;
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on("console", (m) => m.type() === "error" && errors.push("console: " + m.text()));
  page.on("pageerror", (e) => errors.push("page: " + e.message));
  let shot = 0;
  const snap = async (name) => { await page.waitForTimeout(150); await page.screenshot({ path: `/tmp/kenai-replica-w-${String(++shot).padStart(2, "0")}-${name}.png` }); };
  const ok = (what, cond, got) => {
    if (cond) { passed++; return true; }
    failed.push(what); console.log("  FAIL", what, got === undefined ? "" : "→ " + JSON.stringify(got)); return false;
  };
  const W = (id) => page.locator(`#widgets [data-wid="${id}"]`);
  const text = async (id) => (await W(id).innerText()).replace(/\s+/g, " ").trim();
  const has = async (what, id, s) => { const t = await text(id); ok(`${what}: ${id} shows "${s}"`, t.includes(s), t); };
  const lacks = async (what, id, s) => { const t = await text(id); ok(`${what}: ${id} does not show "${s}"`, !t.includes(s), t); };
  const k = (id) => page.locator(`#widgets [data-wid="${id}"] [data-k]`).first().innerText();
  const content = () => page.evaluate(() => window.kenai.widgets.content());
  const screen = () => page.evaluate(() => window.kenai.state.screen);
  const settle = () => page.waitForTimeout(90);
  const step = async () => { await page.evaluate(() => window.kenai.step()); await settle(); };
  const open = async (mode) => {
    await page.goto(FILE + "?mode=" + mode);
    await page.waitForTimeout(200);
    await page.evaluate(() => window.kenai.play(false));
    await settle();
  };
  const away = async (s) => { await page.evaluate((x) => { window.kenai.state.closedApproval = (window.kenai.state.approvals[0] || {}).fp || null; window.kenai.go(x); }, s); await settle(); };
  /** Taps [target] (the card when no selector) and expects the phone on [want]; from another screen first, so the change shows. */
  const tapOpens = async (what, id, sel, want, pill) => {
    await away(want === "LICENCES" ? "HOME" : "LICENCES");
    if (sel) await W(id).locator(sel).first().click();
    else await W(id).locator(".kw-hit").click({ position: { x: 30, y: 8 } });
    await settle();
    const s = await screen();
    const detail = await page.evaluate(() => window.kenai.state.detailPill);
    ok(`${what}: ${id} ${sel || "card"} → ${want}${pill ? " " + pill : ""}`, s === want && (!pill || detail === pill), [s, detail]);
  };

  /* ---------- grid sizes ---------- */
  await open("demo");
  ok("9 widgets on the home screen", (await page.locator("#widgets [data-wid]").count()) === 9);
  for (const [id, [cw, ch]] of Object.entries(SIZES)) {
    const b = await W(id).boundingBox();
    ok(`${id} is ${cw}×${ch} cells`, Math.abs(b.width - (cw * 94 - 8)) < 1.5 && Math.abs(b.height - (ch * 106 - 8)) < 1.5, [b.width, b.height]);
  }

  /* ---------- Koda in the widgets (S8a): a still picture, the pose for the state, the glow kept, no clay ---------- */
  const POSE = { idle: "reposo", sleeping: "reposo", thinking: "piensa", searching: "piensa", working: "trabaja", approval: "espera", question: "espera", ratelimit: "espera", finished: "termino", error: "error", dizzy: "error" };
  const faces = () => page.evaluate(() => [...document.querySelectorAll("#widgets .kw-face")].map((f) => {
    const i = f.querySelector(".koda i[data-pose]");
    return i && { wid: f.closest("[data-wid]").dataset.wid, pose: i.dataset.pose, still: "still" in i.dataset, pos: i.style.backgroundPosition, first: `-${KODA.seq[i.dataset.pose].seq[0] * 32 * +i.dataset.scale}px -${KODA.seq[i.dataset.pose].row * 32 * +i.dataset.scale}px`.replace(/(^| )-0px/g, "$10px"), w: i.offsetWidth, box: Math.min(f.clientWidth, f.clientHeight), glow: f.style.background.includes("radial-gradient") };
  }));
  for (let i = 0; i < 3; i++) await step(); // step 3: an approval, agents in several states
  const f1 = await faces();
  ok("koda: every widget face is Koda", f1.length >= 9 && f1.every(Boolean), f1.length);
  ok("koda: no clay left on the page", (await page.locator("svg.clay").count()) === 0);
  ok("koda: still, first frame of the pose", f1.every((f) => f.still && f.pos === f.first), f1.filter((f) => f.pos !== f.first));
  ok("koda: whole-number scale inside its face", f1.every((f) => f.w % 32 === 0 && f.w <= Math.max(32, f.box)), f1.map((f) => [f.wid, f.w, f.box]));
  const moodNow = (await content()).mood;
  ok("koda: Mood shows the pose for its state", f1.find((f) => f.wid === "mood").pose === POSE[moodNow], [moodNow, f1.find((f) => f.wid === "mood").pose]);
  ok("koda: the glow stays behind it", f1.find((f) => f.wid === "mood").glow && f1.find((f) => f.wid === "solo").glow);
  await page.waitForTimeout(500);
  const f2 = await faces();
  ok("koda: does not animate", f2.every((f, i) => f.pos === f1[i].pos), f2.map((f) => f.pos));
  await open("demo");

  /* ---------- Demo mode: the 8 steps ---------- */
  console.log("mode: Demo mode");
  await open("demo");
  const t0 = await page.evaluate(() => window.kenai.widgets.today());
  const demoSteps = async (label, where) => {
    const seen = [];
    for (let i = 0; i < 8; i++) {
      if (i > 0) await step();
      const s = await page.evaluate(() => window.kenai.state.step);
      const c = await content();
      seen.push(s);
      if (s === 0) { ok(`${label} step 1 headline`, c.glance.headline === "Claude Code is working", c.glance.headline); ok(`${label} step 1 detail`, c.glance.detail === (where ? "On " + where : "Working"), c.glance.detail); await has(label + " step 1", "pill", "Claude Code"); await has(label + " step 1", "solo", "Thinking"); }
      if (s === 1) { await has(label + " step 2", "glance", "2 agents are working"); await has(label + " step 2", "list", "Searching"); }
      if (s === 2) {
        ok(`${label} step 3 alert line`, (await k("alert")) === "1 waiting", await k("alert"));
        await has(label + " step 3", "alert", "Claude Code");
        await has(label + " step 3", "glance", "Waiting for you"); await has(label + " step 3", "glance", "1 request to approve");
        ok(`${label} step 3 look is attention`, (await W("big").getAttribute("data-look")) === "ATTENTION");
        ok(`${label} step 3 badges`, (await page.locator("#widgets .kw-badge").count()) >= 4);
        await snap(`${label}-step3-approval`);
      }
      if (s === 3) { ok(`${label} step 4 alert line`, (await k("alert")) === "2 waiting", await k("alert")); await has(label + " step 4", "list", "Has a question"); }
      if (s === 4) {
        const t = await page.evaluate(() => window.kenai.widgets.today());
        const paired = await page.evaluate(() => window.kenai.state.mode === "paired");
        // As in the real app, only a paired computer counts finished tasks; Demo mode never moves Today.
        ok(`${label} step 5 Today ${paired ? "counts the finished task" : "does not count in demo"}`, t === t0 + (paired ? 1 : 0), [t0, t]);
        ok(`${label} step 5 Today widget shows it`, (await k("today")) === String(t), await k("today"));
        ok(`${label} step 5 Today look`, (await W("today").getAttribute("data-look")) === (t > 0 ? "DONE" : "IDLE"));
        await has(label + " step 5", "list", "Stopped on an error"); await has(label + " step 5", "list", "Finished");
        await snap(`${label}-step5-finished`);
      }
      if (s === 5) await has(label + " step 6", "list", "Hit a limit");
      if (s === 6) { await has(label + " step 7", "solo", "Gemini CLI"); await has(label + " step 7", "solo", "Idle"); }
      if (s === 7) {
        await has(label + " step 8", "glance", "Connected"); await has(label + " step 8", "glance", where ? "To " + where : "Nothing is running");
        await has(label + " step 8", "list", "No agents running");
        ok(`${label} step 8 alert calm`, (await k("alert")) === "All calm", await k("alert")); await has(label + " step 8", "alert", "Nothing needs you");
        await snap(`${label}-step8-calm`);
      }
    }
    ok(`${label}: went through the 8 steps`, seen.join() === "0,1,2,3,4,5,6,7", seen);
  };
  await demoSteps("demo", null);
  await has("demo", "ring", "Turn on plan usage in the app");
  await has("demo", "big", "Plan usage is off");
  await has("demo", "big", "Turn it on in the app");
  for (let i = 0; i < 3; i++) await step(); // to step 3: an approval and two agents
  ok("demo back at step 3", (await page.evaluate(() => window.kenai.state.step)) === 2);
  // Kenai Big's six buttons, the Status Pill's four, every card, the List rows, the usage side.
  const top = "integration_claude";
  for (const [lbl, want, pill] of [["Home", "HOME"], ["Chat", "CHAT"], ["Settings", "SETTINGS"], ["Recent", "HISTORY"], ["Top agent", "SESSION", top], ["Pair again", "SCAN"]]) {
    await tapOpens("demo", "big", `button.kw-tile:has-text("${lbl}")`, want, pill);
  }
  for (const [lbl, want] of [["Open Kenai", "HOME"], ["Open Chat", "CHAT"], ["Open Settings", "SETTINGS"], ["Open recent decisions", "HISTORY"]]) {
    await tapOpens("demo", "pill", `button[aria-label="${lbl}"]`, want);
  }
  await tapOpens("demo", "big", "button.kw-right", "SETTINGS");
  await tapOpens("demo", "list", 'button.kw-lrow:has-text("Codex")', "SESSION", "agent_codex");
  for (const [id, want, pill] of [["big", "SESSION", top], ["pill", "SESSION", top], ["solo", "SESSION", top], ["mood", "SESSION", top], ["list", "HOME"], ["glance", "HOME"], ["ring", "HOME"], ["today", "HOME"]]) {
    await tapOpens("demo", id, null, want, pill);
  }
  await tapOpens("demo", "alert", null, "HOME");
  ok("demo: Waiting Alert opens the pending request", await page.evaluate(() => window.kenai.state.closedApproval === null && window.kenai.state.approvals.length === 1));
  ok("demo: the approval sheet is up", await page.locator("#phone").getByText("Wants to run a command").first().isVisible());
  await snap("demo-alert-opens-approval");

  /* ---------- Not paired ---------- */
  console.log("mode: Not paired");
  await open("none");
  const tn = await page.evaluate(() => window.kenai.widgets.today());
  for (let i = 0; i < 9; i++) {
    if (i > 0) await step();
    const c = await content();
    ok(`none ${i}: not connected`, !c.connected && c.glance.headline === "Not paired" && c.tiles.length === 0, c.glance.headline);
  }
  await has("none", "glance", "Not paired"); await has("none", "glance", "Open Kenai to connect to your computer");
  ok("none alert line", (await k("alert")) === "Not connected", await k("alert")); await has("none", "alert", "Waiting for your PC");
  await has("none", "list", "Waiting for your computer"); await has("none", "solo", "Not paired");
  await has("none", "ring", "Waiting for your computer"); await has("none", "big", "Not connected"); await has("none", "pill", "Not paired");
  ok("none: offline marks", (await page.locator("#widgets svg.kw-off").count()) >= 7, await page.locator("#widgets svg.kw-off").count());
  ok("none: all muted", (await page.locator('#widgets [data-wid][data-look="MUTED"]').count()) === 8);
  ok("none: Today did not move", (await page.evaluate(() => window.kenai.widgets.today())) === tn);
  await snap("none-not-connected");
  await tapOpens("none", "big", 'button.kw-tile:has-text("Pair again")', "SCAN");
  await tapOpens("none", "big", 'button.kw-tile:has-text("Top agent")', "HOME");
  await tapOpens("none", "mood", null, "HOME");
  await tapOpens("none", "big", "button.kw-right", "HOME");

  /* ---------- Paired (fake data) ---------- */
  console.log("mode: Paired (fake data)");
  await open("paired");
  await has("paired", "ring", "68%"); await has("paired", "ring", "Claude · 5 h");
  ok("paired ring uses the usage palette", (await W("ring").getAttribute("data-look")) === "USAGE_CALM");
  await has("paired", "big", "Claude · 5 h"); await has("paired", "big", "Codex · 5 h"); await lacks("paired", "big", "Plan usage is off");
  // The Today count is kept across modes (one phone, one day); one more task finishes at step 5.
  const t0Paired = await page.evaluate(() => window.kenai.widgets.today());
  for (let i = 1; i < 8; i++) {
    await step();
    const s = await page.evaluate(() => window.kenai.state.step);
    if (s === 0 || s === 1) await has("paired", "glance", "On Alvaro's PC");
    if (s === 4) ok("paired step 5 Today counts", (await page.evaluate(() => window.kenai.widgets.today())) === t0Paired + 1);
    if (s === 7) await has("paired step 8", "glance", "To Alvaro's PC");
  }
  await step(); await step(); await step(); // step 3, the approval
  ok("paired step 3 alert", (await k("alert")) === "1 waiting", await k("alert"));
  await tapOpens("paired", "big", "button.kw-right", "HOME");
  await snap("paired-step3");
  await page.selectOption("#linksel", "lost");
  await settle();
  await has("paired lost", "glance", "Not connected"); await has("paired lost", "glance", "Kenai cannot reach your computer");
  ok("paired lost alert", (await k("alert")) === "Not connected", await k("alert"));
  await has("paired lost", "big", "Not connected"); await has("paired lost", "ring", "Waiting for your computer");
  await snap("paired-link-lost");
  await page.selectOption("#linksel", "connected");
  await settle();

  /* ---------- Widgets: dark / light ---------- */
  console.log("widgets: dark / light");
  const card = () => W("today").evaluate((el) => getComputedStyle(el).backgroundColor);
  const ink = () => W("today").locator("[data-k]").evaluate((el) => getComputedStyle(el).color);
  ok("dark by default", (await card()) === "rgb(23, 24, 28)" && (await ink()) === "rgb(255, 255, 255)", [await card(), await ink()]);
  await page.locator('[data-wtheme="light"]').click();
  await settle();
  ok("light card and text (values-notnight)", (await card()) === "rgb(247, 247, 250)" && (await ink()) === "rgb(28, 28, 30)", [await card(), await ink()]);
  ok("light toggle pressed", (await page.locator('[data-wtheme="light"]').getAttribute("aria-pressed")) === "true");
  ok("the phone stays dark", (await page.locator("#phone .screen").evaluate((el) => getComputedStyle(el).backgroundColor)) === "rgb(0, 0, 0)");
  await step();
  ok("light survives a redraw", (await card()) === "rgb(247, 247, 250)");
  await snap("light");
  await page.locator('[data-wtheme="dark"]').click();
  await settle();
  ok("back to dark", (await card()) === "rgb(23, 24, 28)");
  await snap("dark");

  console.log("checks passed:", passed);
  console.log("checks failed:", failed.length, failed.join(" | "));
  console.log("errors:", errors.length, errors.join("\n"));
  await browser.close();
  process.exit(errors.length || failed.length ? 1 : 0);
})();
