"use strict";
/* eslint-disable no-unused-vars, no-undef -- the _src files are concatenated into one script by ../_build.mjs */
/* Data copied from the app. Strings: res/values/strings.xml (injected verbatim by _build.mjs). */
const S = __STRINGS__;
const KODA = __KODA__;
const LICENCES = __LICENCES__;

/** Android's %1$s / %2$d / %% formatting, enough for strings.xml. */
function fmt(key, ...args) {
  return S[key].replace(/%(\d)\$[sd]/g, (_, i) => String(args[+i - 1])).replace(/%%/g, "%");
}

/* core/IconSpec.kt: 24 grid, stroke 1.9, round caps and joins. */
const ICON = {
  HOME: '<path d="M4 11.5L12 5L20 11.5"/><path d="M6 10.5V19H18V10.5"/>',
  CHAT: '<rect x="3.5" y="5.5" width="17" height="12" rx="2"/><path d="M11 17.5L7 20.5V17.5"/>',
  SLIDERS: '<path d="M4 7H13M17 7H20M4 17H7M11 17H20"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>',
  BACK: '<path d="M14.5 6L8.5 12L14.5 18"/>',
  SEND: '<path d="M12 19V6"/><path d="M6.5 11.5L12 6L17.5 11.5"/>',
  LOCK: '<rect x="5.5" y="10.5" width="13" height="9" rx="2.5"/><path d="M8.5 10.5V8M15.5 10.5V8"/><path d="M8.5 8A3.5 3.5 0 0 1 15.5 8"/>',
  SCAN: '<path d="M4 8V6L6 4H8M16 4H18L20 6V8M20 16V18L18 20H16M8 20H6L4 18V16M8 12H16"/>',
  CHEVRON: '<path d="M9.5 6L15.5 12L9.5 18"/>',
  CHECK: '<path d="M5.5 12.5L9.7 16.7L18.5 7.5"/>',
  CLOSE: '<path d="M7 7L17 17M17 7L7 17"/>',
};

/* character/CharacterTypes.kt BotState, StateColors; character/KodaModel.kt KodaRole and KodaPose. */
const STATES = ["idle", "working", "thinking", "searching", "approval", "question", "error", "finished", "ratelimit", "sleeping", "dizzy"];
const EMOTES = ["love", "surprised", "proud", "wink", "yawn", "happy", "annoyed"];
const rgb = (r, g, b) => `rgb(${Math.round(r * 255)},${Math.round(g * 255)},${Math.round(b * 255)})`;
const STATE_COLOR = {
  idle: rgb(0.902, 0.914, 0.933), working: rgb(0.231, 0.62, 1.0), thinking: rgb(0.545, 0.361, 0.965),
  searching: rgb(0.388, 0.396, 0.949), approval: rgb(0.961, 0.647, 0.141), question: rgb(0.133, 0.827, 0.933),
  error: rgb(0.957, 0.314, 0.369), finished: rgb(0.204, 0.831, 0.6), ratelimit: rgb(0.984, 0.573, 0.235),
  sleeping: rgb(0.58, 0.635, 0.722), dizzy: rgb(0.957, 0.447, 0.714),
};
const KODA_ROLE = { GO: "#B8FF2E", SUN: "#FFE100", LIVE: "#2EB4FF", RED: "#FF5C75", FAINT: "#6F6D66" };
function roleOf(st) {
  if (st === "approval" || st === "question" || st === "ratelimit") return KODA_ROLE.SUN;
  if (st === "working" || st === "thinking" || st === "searching") return KODA_ROLE.LIVE;
  if (st === "finished") return KODA_ROLE.GO;
  if (st === "error" || st === "dizzy") return KODA_ROLE.RED;
  return KODA_ROLE.FAINT;
}
function poseOf(st) {
  if (st === "idle" || st === "sleeping") return "reposo";
  if (st === "thinking" || st === "searching") return "piensa";
  if (st === "working") return "trabaja";
  if (st === "approval" || st === "question" || st === "ratelimit") return "espera";
  if (st === "finished") return "termino";
  return "error";
}
/* ui/HomePanel.kt stateLabel */
const STATE_LABEL = {
  idle: "state_ready", working: "state_working", thinking: "state_thinking", searching: "state_searching",
  approval: "state_approval", question: "state_question", error: "state_error", finished: "state_finished",
  ratelimit: "state_ratelimit", sleeping: "state_sleeping", dizzy: "state_dizzy",
};
const stateLabel = (st) => S[STATE_LABEL[st]];
/* ui/HomePanel.kt stateColor */
const stateColor = (st) => (st === "idle" ? "#0F9F6E" : STATE_COLOR[st] || "#6B7280");

/* core/Pills.kt (the pills the demo uses, plus the services) */
const PILLS = {
  integration_claude: { name: "Claude Code", color: "#F5F6F8" },
  agent_codex: { name: "Codex", color: "#2DD4BF" },
  agent_gemini: { name: "Gemini CLI", color: "#8AB4F8" },
  integration_github: { name: "GitHub", color: "#F4505E" },
  integration_vercel: { name: "Vercel", color: "#7C5CFF" },
};

/* core/HomePanel.kt URGENCY */
const URGENCY = ["approval", "question", "error", "ratelimit", "dizzy", "working", "searching", "thinking", "finished", "sleeping", "idle"];

/* core/ToolLabels.kt (the tools this replica meets) */
const TOOL_KNOWN = {
  askuserquestion: "Asking a question", bash: "Running a command", shell: "Running a command", read: "Reading files",
  edit: "Editing files", multiedit: "Editing files", write: "Editing files", grep: "Searching files", glob: "Searching files",
  ls: "Looking at files", websearch: "Searching the web", webfetch: "Reading a web page", task: "Running a sub-agent", todowrite: "Planning",
};
const TOOL_ACTION = {
  askuserquestion: "ask a question", bash: "run a command", shell: "run a command", read: "read files", edit: "edit files",
  multiedit: "edit files", write: "edit files", grep: "search files", glob: "search files", ls: "look at files",
  websearch: "search the web", webfetch: "read a web page", task: "run a sub-agent",
};
const toolKey = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, "");
const toolClean = (n) => { const c = n.replace(/[_\-.:]+/g, " ").replace(/([a-z0-9])(?=[A-Z])/g, "$1 ").trim().toLowerCase(); return c.charAt(0).toUpperCase() + c.slice(1); };
function toolAction(tool) { const n = tool.trim(); return n ? TOOL_ACTION[toolKey(n)] || toolClean(n).toLowerCase() : "do something"; }
/** ToolLabels.label: a tool name in plain words; free text ("Reading the project") gives "" so the screen shows the state. */
function toolLabel(raw) {
  const text = raw.trim().replace(/·$/, "").trim();
  if (!text) return "";
  const at = text.indexOf(" · ");
  const head = at > 0 ? text.slice(0, at).trim() : text;
  const rest = at > 0 ? text.slice(at + 3).trim() : "";
  if (!/^[A-Za-z0-9_\-.:]+$/.test(head)) return "";
  const known = TOOL_KNOWN[toolKey(head)];
  const plausible = head.length <= 40 && !/[0-9]{3,}/.test(head) && (head.length <= 24 || /[_\-.:]/.test(head));
  if (!known && !plausible) return "";
  const tool = known || toolClean(head);
  // SafeText.requestLabel keeps only the program's name of a shell command.
  const program = rest && TOOL_ACTION[toolKey(head)] === "run a command" ? rest.split(/\s+/)[0].split("/").pop() : "";
  return program ? tool + " · " + program : tool;
}
/** SafeText.prose: words that look like a path, an address or a key become "…". */
const prose = (t) => t.trim().split(/\s+/).map((w) => { const c = w.replace(/^[.,;:!?"'()[\]]+|[.,;:!?"'()[\]]+$/g, ""); return /[\/\\@=]|:\/\//.test(c) || c.length > 32 ? "…" : w; }).join(" ");

/* core/HomeText.kt */
const BUSY = ["working", "thinking", "searching"];
function homeLine(s) {
  if (s.state === "finished" && s.finalLine) { const p = prose(s.finalLine); if (p && p !== "…") return p; }
  return toolLabel(s.text) || null;
}
const recentText = (allowed, tool) => (allowed ? "Allowed: " : "Denied: ") + toolAction(tool);
const decisionText = (allowed, agent, tool) => `You ${allowed ? "allowed" : "denied"} ${agent || "an agent"} to ${toolAction(tool)}`;

/* link/LinkHealth.kt, BatteryHint (not Samsung) and link/Diagnostics.kt texts */
const LINK_LOST = ["Connection lost 2 min ago, reconnecting", "The computer stopped answering. This is usual after the phone slept."];
const BATTERY_STEPS = ["Open the app's battery setting and choose Unrestricted (or Don't optimise)."];
const DIAG_OK = [
  ["Phone network", "ok", "Wi-Fi is connected"],
  ["Saved address answers", "ok", "Answers in 38 ms at 192.168.x.x"],
  ["Computer announces itself", "ok", "Found, same address as the saved one"],
  ["Secure connection (pinned certificate)", "ok", "The computer's certificate is the one you paired with"],
  ["Pairing code and version", "ok", "Pairing code accepted, protocol version 1"],
];
const DIAG_DOWN = [
  ["Phone network", "ok", "Wi-Fi is connected"],
  ["Saved address answers", "problem", "No answer from 192.168.x.x", "Is the computer on and Kenai running with the Phone link switched on? Same Wi-Fi? A guest network or client isolation blocks it."],
  ["Computer announces itself", "problem", "The computer is not announcing itself here", "Some networks block this. If the saved address answers, you can ignore it; otherwise type the address in Home."],
  ["Secure connection (pinned certificate)", "skipped", "Needs the address to answer"],
  ["Pairing code and version", "skipped", "Needs the secure connection"],
];

/* Fake desktop for "Paired (fake data)": what the computer would send with its switches on. */
const DESKTOP = { name: "Alvaro's PC", host: "192.168.1.20", port: 47821 };
const SAMPLE_LINK = "abito://pair?v=1&host=192.168.1.20&port=47821&fp=" + "3f".repeat(32) + "&token=demo-token-1234567890&name=Alvaro%27s%20PC";
const FAKE = {
  details: {
    integration_claude: {
      project: "kenai", finalLine: "Build passed. I fixed two lint warnings and updated the README.",
      steps: ["Read · app/build.gradle.kts", "Grep", "Edit", "Bash · npm run build", "Write"],
      files: [
        { name: "app/build.gradle.kts", added: 3, removed: 1, isNew: false },
        { name: "docs/RELEASE.md", added: 12, removed: 0, isNew: true },
      ],
    },
    agent_codex: { project: "kenai-desktop", steps: ["Glob", "Read", "Bash · npm test"], files: [] },
    agent_gemini: { project: "notes", steps: [], files: [] },
  },
  questions: [
    { question: "Which branch?", multiSelect: false, options: [
      { label: "release/0.1", description: "The branch of this release" },
      { label: "main", description: "Merge straight into main" } ] },
    { question: "What should I run after the merge?", multiSelect: true, options: [
      { label: "Unit tests", description: "./gradlew test" },
      { label: "Lint", description: "ktlint and Android lint" },
      { label: "Nothing", description: "" } ] },
  ],
  diff: [
    "@@ -12,7 +12,9 @@ android {", " defaultConfig {", '     applicationId = "com.kenai.android"', "-    versionCode = 9",
    "+    versionCode = 10", '+    versionName = "0.1.0"', "+    resourceConfigurations += listOf(\"en\")", "     minSdk = 29", " }",
  ],
  usage: {
    claude: { plan: "Max", five: { pct: 68, resetMin: 134 }, week: { pct: 41, day: "Monday" } },
    codex: { plan: "Plus", five: { pct: 22, resetMin: 47 }, week: { pct: 86, day: "Thursday" }, credits: 2 },
  },
  services: [
    { title: "GitHub", reason: "2 to review", headline: "3 pull requests open", items: [{ label: "Light theme", detail: "#41" }, { label: "Spanish strings", detail: "#42" }] },
    { title: "Vercel", reason: null, headline: "Last deploy ready", items: [{ label: "kenai-site", detail: "4 min ago" }] },
  ],
  models: [{ id: "claude-sonnet", label: "Claude Sonnet" }, { id: "gpt-mini", label: "GPT-5 mini" }],
  reply: "Sure. To build the release:\n\n- run **./gradlew assembleRelease**\n- the APK lands in `app/build/outputs/apk/release`\n\n```\n./gradlew assembleRelease\n```\nWant me to sign it too?",
};
