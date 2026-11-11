# kenai-0.1
Release 0.1 of Kenai for Android, on branch `release/0.1`: a functional canvas replica of today's app, plus the group C features (light theme, Spanish, vibration, quick settings tile actions).
Base: `beb41a8`. Previous plans: `odd/tasks/abito-movil-base.md`, `odd/tasks/diseno-movil-isla.md` (history).

## Specs
- S1: «quiero una maqueta de canvas que sea funcional igual a la app de verdad» + «1a» → the replica reproduces the real app as it is today (English texts, current look), not the «Isla en el bolsillo» direction. Every real screen is reachable (Pairing, Scan, Home, Chat, Settings, History, Gallery, Diagnostics, Licences, Session details), the bottom bar and buttons navigate, the approval/question/diff sheets work (Allow/Deny/Answer, simulated fingerprint), the 9 widgets are shown on an Android home screen and follow the demo's 8-step loop, and fake data fills the features the desktop does not serve yet (chat, question options, diffs, usage, services, session details).
- S2: «metemos todo de la C» → group C goes into release 0.1:
  - S2.5 «Tema claro»: the app gets a light theme (today it is dark only). Decided (L5, option 1a): it follows the system's dark/light mode, no new setting; the island, the sheets, the scan screen and the Koda hero card stay dark in both.
  - S2.6 «Español en la app»: the app is available in Spanish; this replaces the repo's «English only» rule. Decided (L5, option 2a): Spanish when the phone is in Spanish, English otherwise; no per-app picker. English stays the default.
  - S2.7a «Vibración al llegar una aprobación o una pregunta». Decided (L5, option 3a): a new "Vibrate" switch, on by default; it vibrates during quiet hours too (approvals and questions always notify); no double buzz when the approval notification already vibrates.
  - S2.7b «acciones en la tile de ajustes rápidos, que hoy solo abre la app». Decided (L5, options 4a+4b): the tile subtitle shows what waits ("N waiting" or the most urgent agent), and a tap opens the pending approval or question directly (Home when nothing waits). No Allow/Deny from the tile, no quiet-hours toggle.
- S3: No Coucou name, Mochi, icon or sounds (NOTICE.md). Nothing on the desktop side is touched in this repo.

## Tasks
- [~] T1 (S1) Functional canvas replica of today's app under `design/kenai-app-replica/` · split (L5): T1a phone screens + sheets + demo loop; T1b widget panel + Playwright check
- [ ] T1a (S1) Phone: every screen, sheets, demo loop, fake data · worker
- [ ] T1b (S1) Widget panel wired to the phone + Playwright check · worker, after T1a
- [ ] T2 (S2.5) Light theme · worker, parallel with T1a
- [ ] T3 (S2.6) Spanish strings and the end of the English-only rule · last, so it translates the strings T4/T5 add
- [ ] T4 (S2.7a) Vibration on an arriving approval or question
- [ ] T5 (S2.7b) Quick settings tile actions

## Log
- L1: «si sacalo, quiero una maqueta de canvas que sea funcional igual a la app de verdad, que funciones tiene coucou que podamos agregar?» (HANDOFF removed in beb41a8).
- L2: «1a, metemos todo de la C» — 1a: the replica copies the real app today (English, current look). C: items 5 light theme, 6 Spanish, 7 vibration + tile actions, as offered in the previous answer.
- L3: The harness's pi-lens ktlint autoformat rewrote HomePanel.kt after edits and broke SessionScreenTest's source-text checks; disabled for this project with `~/Apps/kenai/.pi-lens.json` (format and autofix off), outside the repo.
- L4: T1 worker gentle-ai-worker (mwc0zom5-3-n64b) timed out ("stalled for 4 min after: write"); it left only design/kenai-app-replica/_src/app.css (22.8 KB), no HTML, no checks run. T1 is not done.
- L5: «dale» — accepts the proposal: relaunch the replica split in two (T1a, T1b); 1a light theme follows the system; 2a Spanish by phone language; 3a vibrate also during quiet hours; 4 a+b tile subtitle + opens what is pending. Order: T1a ∥ T2, then T1b, T4, T5, T3 last (T4/T5 add strings T3 translates; app-strings.json is shared, so they are not parallel).
