// Clicks through every screen of the replica in each mode and reports console/page errors.
// Run from the repo root: node design/kenai-app-replica/check-phone.cjs   (screenshots: /tmp/kenai-replica-*.png)
const { chromium } = require(process.env.HOME + "/.local/lib/node_modules/playwright");
const path = require("path");

const FILE = "file://" + path.join(__dirname, "01-app-replica.html");
const VALID = "abito://pair?v=1&host=192.168.1.20&port=47821&fp=" + "ab".repeat(32) + "&token=abcdefghijklmnop1234&name=Desk";

(async () => {
  const browser = await chromium.launch();
  const errors = [];
  const failed = [];
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on("console", (m) => m.type() === "error" && errors.push("console: " + m.text()));
  page.on("pageerror", (e) => errors.push("page: " + e.message));
  let shot = 0;
  const snap = async (name) => { await page.waitForTimeout(250); await page.screenshot({ path: `/tmp/kenai-replica-${String(++shot).padStart(2, "0")}-${name}.png` }); };
  const ph = (sel) => page.locator("#phone").locator(sel).first();
  const tap = async (sel) => { await ph(sel).click(); await page.waitForTimeout(120); };
  const act = (a, arg) => tap(arg === undefined ? `[data-act="${a}"]` : `[data-act="${a}"][data-arg="${arg}"]`);
  const expect = async (what, sel) => {
    try { await ph(sel).waitFor({ state: "visible", timeout: 2500 }); } catch (e) { failed.push(what); console.log("  FAIL", what, e.message.split("\n")[0]); return false; }
    console.log("  ok  ", what); return true;
  };
  const screen = () => page.evaluate(() => window.kenai.state.screen);
  /** Koda where the app draws it (S8a): painted from the sprite, in [pose] (null: the pose for the open session), with the
   *  agent's light behind it or not, and no clay character anywhere. */
  const koda = async (what, sel, pose, glow) => {
    const r = await page.evaluate(([s, p]) => {
      const el = document.querySelector("#phone " + s);
      const i = el && el.querySelector("i[data-pose]");
      if (!i) return null;
      const want = p || poseOf(window.kenai.derived.sessions().find((x) => x.pill === window.kenai.state.detailPill).state);
      return { pose: i.dataset.pose, want, painted: !!i.style.backgroundImage, glow: el.style.background.includes("radial-gradient"), clay: document.querySelectorAll("svg.clay").length };
    }, [sel, pose]);
    if (r && r.pose === r.want && r.painted && r.glow === glow && r.clay === 0) { console.log("  ok  ", what); return; }
    failed.push(what); console.log("  FAIL", what, JSON.stringify(r));
  };
  const open = async (mode) => {
    await page.goto(FILE + "?mode=" + mode);
    await page.waitForTimeout(200);
    await page.evaluate(() => window.kenai.play(false));
  };
  const steps = (n) => page.evaluate((k) => { for (let i = 0; i < k; i++) window.kenai.step(); }, n);
  const tabs = async (label) => {
    for (const t of ["CHAT", "SETTINGS", "HOME"]) {
      await tap(`[data-tab="${t}"]`);
      if ((await screen()) !== t) failed.push(`${label}: tab ${t}`);
      await snap(`${label}-tab-${t.toLowerCase()}`);
    }
  };
  const subPages = async (label, list) => {
    for (const s of list) {
      await tap('[data-tab="SETTINGS"]');
      await act("tab", s);
      if ((await screen()) !== s) failed.push(`${label}: Settings → ${s}`);
      await page.waitForTimeout(s === "DIAGNOSTICS" ? 2200 : 100);
      await snap(`${label}-${s.toLowerCase()}`);
      await act("back");
      if ((await screen()) !== "SETTINGS") failed.push(`${label}: Back from ${s}`);
    }
  };

  /* ---------- Not paired: Pairing, paste, scan ---------- */
  console.log("mode: Not paired");
  await open("none");
  await expect("pairing headline", "text=Connect to your computer");
  await koda("pairing: Koda asleep, no light", "#content .koda", "reposo", false);
  await snap("none-pairing");
  await act("openPaste");
  await ph("#f-paste").fill("https://example.com/pair?v=1");
  await act("pastePair");
  await expect("invalid link refused", "text=That is not a Kenai pairing link.");
  await snap("none-paste-invalid");
  await ph("#f-paste").fill(VALID);
  await act("pastePair");
  if ((await page.evaluate(() => window.kenai.state.mode)) !== "paired") failed.push("valid pasted link pairs");
  else console.log("  ok   valid pasted link pairs");
  await snap("none-paste-valid-paired");

  await open("none");
  await tabs("none");
  await subPages("none", ["HISTORY", "GALLERY", "LICENCES"]);
  await tap('[data-tab="HOME"]');
  await act("scan");
  await expect("scan asks for the camera", "text=Allow camera");
  await act("askCamera");
  await page.locator('#phone [data-act="cameraGrant"][data-arg="once"]').first().click();
  await expect("camera viewfinder", "text=Simulate scan: Kenai code");
  await act("simScan", "other");
  await expect("other QR refused", "text=That is not a Kenai pairing code");
  await snap("none-scan-other");
  await act("simScan", "ok");
  await expect("pair confirm dialog", "text=Pair with Alvaro's PC?");
  await snap("none-pair-confirm");
  await act("pairOk");
  if ((await page.evaluate(() => window.kenai.state.mode)) !== "paired") failed.push("scan + confirm pairs");
  else console.log("  ok   scan + confirm pairs");

  /* ---------- Demo mode ---------- */
  console.log("mode: Demo");
  await open("demo");
  await snap("demo-step1");
  await tabs("demo");
  await tap('[data-tab="CHAT"]');
  await expect("demo chat says not paired", "text=Pair your computer first, then you can chat through it.");
  await koda("chat empty: Koda asleep, no light", "#content .koda", "reposo", false);
  await tap('[data-tab="HOME"]');
  await subPages("demo", ["HISTORY", "GALLERY", "LICENCES"]);
  await tap('[data-tab="HOME"]');
  await steps(2); // step 3: the approval
  await expect("approval sheet", "text=Wants to run a command");
  await koda("approval sheet: Koda waiting, the agent's light", "#layer .shead .koda", "espera", true);
  await page.waitForTimeout(650); // taps ignored for 600 ms
  await act("toggleCmd");
  await expect("exact command", ".cmd >> text=npm run build");
  await snap("demo-approval");
  await act("allow");
  await expect("fingerprint prompt", ".bio");
  await snap("demo-biometric");
  await act("bioTouch");
  await page.waitForTimeout(700);
  const allowed = await page.evaluate(() => window.kenai.state.decisions[0]);
  if (!(allowed && allowed.allowed && (await page.evaluate(() => window.kenai.state.approvals.length)) === 0)) failed.push("Allow records and clears");
  else console.log("  ok   Allow (fingerprint) records and clears");
  await snap("demo-after-allow");
  await page.evaluate(() => window.kenai.setMode("demo"));
  await page.evaluate(() => window.kenai.play(false));
  await steps(2);
  await page.waitForTimeout(650);
  await act("deny");
  const denied = await page.evaluate(() => window.kenai.state.decisions[0]);
  if (!denied || denied.allowed) failed.push("Deny records a denial"); else console.log("  ok   Deny direct");
  await steps(1); // step 4: Codex has a question, the most urgent: it is the hero
  await expect("question button (demo: see)", "text=See the question");
  await act("openQuestion");
  await expect("question sheet without options", "text=Answer it on your computer.");
  await koda("question sheet: Koda waiting, the agent's light", "#layer .shead .koda", "espera", true);
  await snap("demo-question");
  await act("notNow");

  /* ---------- Paired (fake data) ---------- */
  console.log("mode: Paired (fake data)");
  await open("paired");
  await steps(1);
  await expect("plan usage", "text=Plan usage");
  await expect("services", "text=Services");
  await snap("paired-home");
  await act("details", "integration_claude");
  await expect("session details", "text=Steps");
  await koda("session: Koda in the state's pose, the agent's light", "#content .panel .koda", null, true);
  await snap("paired-session");
  await act("openDiff", "app/build.gradle.kts");
  await expect("diff sheet", ".drow");
  await snap("paired-diff");
  await act("closeDiff");
  await act("back");
  await steps(2); // step 4: question with options (and the approval from step 3)
  await page.evaluate(() => { const s = window.kenai.state; const a = s.approvals[0]; if (a) s.closedApproval = a.fp; });
  await page.evaluate(() => window.kenai.openQuestion());
  await expect("question 1 of 2", "text=Question 1 of 2");
  await tap('[data-act="qPick"][data-arg="release/0.1"]');
  await act("qNext");
  await tap('[data-act="qPick"][data-arg="Unit tests"]');
  await tap('[data-act="qPick"][data-arg="Lint"]');
  await page.waitForTimeout(650);
  await snap("paired-question-2");
  await act("qSend");
  await act("bioTouch");
  await page.waitForTimeout(700);
  await expect("answer sent", "text=Answer sent");
  await page.evaluate(() => window.kenai.openApproval());
  await page.waitForTimeout(650);
  await act("deny");
  await tap('[data-tab="CHAT"]');
  await ph("#chatbox").fill("How do I build the release?");
  await act("chatSend");
  await page.waitForTimeout(4500);
  await expect("chat answer", "text=Want me to sign it too?");
  await act("chatMenu");
  await act("chatModel", "gpt-mini");
  await expect("model picked", "text=Model: GPT-5 mini");
  await snap("paired-chat");
  await act("chatClearAsk");
  await expect("clear dialog", "text=Clear this chat?");
  await act("clearOk");
  await tabs("paired");
  await subPages("paired", ["HISTORY", "GALLERY", "DIAGNOSTICS", "LICENCES"]);
  await tap('[data-tab="SETTINGS"]');
  await act("overlay");
  await expect("overlay permission page", "text=Display over other apps");
  const lnch = await page.locator("#phone .sysp .app svg.lnch path").count();
  if (lnch >= 5) console.log("  ok   app icon is the launcher's Koda face"); else failed.push("app icon is the launcher's Koda face");
  await act("sysOverlay");
  await act("sysClose");
  await page.selectOption("#linksel", "notfound");
  await tap('[data-tab="HOME"]');
  await expect("link lost panel", "text=Connection lost 2 min ago, reconnecting");
  await expect("discovery hint", "text=Can't find your computer");
  await act("openAddress");
  await ph("#f-address").fill("not an address");
  await act("saveAddress");
  await expect("bad address refused", "text=That is not a valid address.");
  await ph("#f-address").fill("192.168.1.30:47821");
  await act("saveAddress");
  await snap("paired-address-saved");
  await act("sysBack");

  // Gallery: every state, then every emote, as KodaPose.of; live, and still with reduced motion.
  await page.evaluate(() => window.kenai.go("GALLERY"));
  await page.waitForFunction(() => document.querySelectorAll("#content .koda i").length === 18); // render is on the next frame
  const GALLERY = ["reposo", "trabaja", "piensa", "piensa", "espera", "espera", "error", "termino", "espera", "reposo", "error", // states
    "escucha", "espera", "termino", "escucha", "reposo", "termino", "error"]; // love surprised proud wink yawn happy annoyed
  const cells = () => page.evaluate(() => [...document.querySelectorAll("#content .koda i")].map((i) => [i.dataset.pose, i.style.backgroundPosition]));
  const g1 = await cells();
  if (g1.map((c) => c[0]).join() === GALLERY.join()) console.log("  ok   gallery poses"); else { failed.push("gallery poses"); console.log("  FAIL gallery poses", JSON.stringify(g1)); }
  await expect("gallery names its states and emotes", "text=Loving");
  await page.waitForTimeout(700);
  if ((await cells())[1][1] !== g1[1][1]) console.log("  ok   gallery animates"); else failed.push("gallery animates");
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.waitForTimeout(200);
  const r1 = await cells();
  await page.waitForTimeout(700);
  const r2 = await cells();
  const first = await page.evaluate(() => [...document.querySelectorAll("#content .koda i")].every((i) => i.style.backgroundPosition === `-${KODA.seq[i.dataset.pose].seq[0] * 32 * +i.dataset.scale}px -${KODA.seq[i.dataset.pose].row * 32 * +i.dataset.scale}px`.replace(/(^| )-0px/g, "$10px")));
  if (first && r1.join() === r2.join()) console.log("  ok   reduced motion: first frame, still"); else failed.push("reduced motion: first frame, still");
  await page.emulateMedia({ reducedMotion: null });

  // Every screen through the API, for T1b.
  for (const s of ["HOME", "CHAT", "SETTINGS", "HISTORY", "GALLERY", "DIAGNOSTICS", "LICENCES", "SESSION", "SCAN"]) {
    await page.evaluate((x) => window.kenai.go(x), s);
    await page.waitForTimeout(80);
    if ((await screen()) !== s) failed.push("kenai.go " + s);
  }
  await page.evaluate(() => window.kenai.go("HOME"));
  await snap("paired-end");

  console.log("checks failed:", failed.length, failed.join(" | "));
  console.log("errors:", errors.length, errors.join("\n"));
  await browser.close();
  process.exit(errors.length || failed.length ? 1 : 0);
})();
