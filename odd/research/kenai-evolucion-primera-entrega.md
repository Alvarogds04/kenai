# Kenai evolution — first delivery

## Decisions of the human (23 Nov) — these supersede the proposals below where they differ
1. Chat: hybrid (ChatGPT-grade structure and functions with the shipped Koda tokens and colours).
2. KN-07 (SSH terminal like Moshi): NOT discarded; kept as a candidate path for the terminal (phase 3), decided by a short spike against a Jesscore PTY.
3. Pending requests: a segment inside Inicio; four tabs (Inicio, Agents, Chat, Ajustes).
4. D5: the phone has its own link connection.
5. Voice: dictation first, with Parakeet (Jesscore).
6. Jesscore contracts of section 6: approved to send.
7. Stories: A-H became KN-11..KN-18 in the vault (`Historias/KN-11.md` .. `KN-18.md`); existing KN-06/KN-08/KN-10 stay as they are and KN-11/KN-12/KN-13 extend them.
8. Direct provider vs KN-02: Kenai talks only to the engine.
Also: one phone front = the engine's link (decision of 23 Nov). Telegram is only a connector.

Status of the rest of this document: PROPOSAL.

Date: 23 Nov. Author: Kenai coordinator. Status: proposal for the human to decide. No production code touched.
Tags: [code] verified in code, [doc] documented only, [web] public source, [unv] not verified.

## 1. Diagnosis of the current app (release/0.2) [code]

- Tabs HOME / CHAT / SETTINGS plus child screens (Session, Question, History, Diagnostics, Scan). Pure nav rules in `core/Navigation.kt`; every deep link enters through `LaunchActivity`.
- Chat: `ChatBackend` seam + `ChatLinkBackend` (only implementation), bounded cache, conversations, model chip, markdown subset, cancel. Gaps: attachments, regenerate/edit, per-message actions, tables/links, local search, tool/reasoning rendering, one answer at a time, 4000-char input.
- Island: a real `WindowManager` overlay (permission policy, geometry, motion, no timers at rest, foreground service). Its content is read-only and always opens the app (KN-04 S10).
- Approvals/questions: complete and correct end to end (`ApprovalBook`, `NotificationPlan`, `BiometricGate`, `QuestionFlow`). Deny from the shade needs no prompt; Allow/Respond always land in an Activity that asks fingerprint or lock.
- Widgets: 10 surfaces over one `WidgetContent`. Real engine data: states, names, plan `usage` percent + `resetsAt`. Derived: all copy, tones, moods; `TodayWidget` is a phone-local counter.
- Link protocol: sessions (read-only, <=16), approvals, questions, diff, usage, services, chat. NOT present: terminal/PTY, agent output streaming, start/stop/resume agents, prompt to a specific session, token/cost metrics. `ANDROID_LINK.md` §16: the desktop side does not yet implement caps, approvals, questions or chat.
- Koda sprite: 7 poses (`REPOSO, ESCUCHA, PIENSA, TRABAJA, ESPERA, TERMINO, ERROR`) mapped from 11 wire states.

## 2. References and existing designs

- Moshi [web]: mobile terminal (SSH/Mosh/ET) straight to the user's own machine, no relay, no host daemon required. "Chat View" renders a live Claude Code/Codex session as native chat (tool calls, plans, approvals as cards, voice input); approvals resolve over its daemon bridge. Free tier: SSH, monitoring, push, biometric key protection, dictation. Pro: mosh, tmux/Herdr reattach, image paste, diff viewer.
  Lesson: Moshi's value is two views over ONE session (raw terminal / chat cards). Our equivalent must ride on Jesscore, not on SSH. KN-07 ("connect by SSH like Moshi") would be a parallel supervisor: recommend dropping it.
- `design/moshi-chat` (3 options): undecided, no human comment, and it conflicts with the shipped Koda chat metrics (`chat-koda-kenai`, human-approved 17 Nov, commit 14db01a). Needs one human decision.
- `design/kenai-mejoras` option 01 "Decidí en un toque" was chosen and shipped. `movil-identidad-pantallas-base` option 01 chosen, in progress.
- ChatGPT mobile as quality bar: conversation list with search, regenerate/edit, per-message actions, rich markdown, stop/continue, model picker. Not yet studied in detail; do it as a design task (section 8 R3).

## 3. Proposed mobile experience and navigation

Bottom bar of four: **Inicio** (what needs me now) · **Agents** (sessions, per-session detail; terminal only if Jesscore provides it) · **Chat** (ChatGPT-grade) · **Ajustes**.
- Pending requests: a "Pendientes" segment at the top of Inicio/Agents (one list across sessions) rather than a fifth tab. Allow/answer always passes the lock prompt.
- Koda circle: small floating circle in Koda's pose; discreet pulse when something is pending; tap expands a panel listing pending questions per session. Choosing an option or Allow hands off to the Activity for the fingerprint (Android cannot start an activity from a notification receiver since API 31 [web]; an overlay cannot run `BiometricPrompt`). Deny can stay one tap.
- Agents screen without a terminal (phase 2): session cards with state, steps, diff, approvals, last notices. Terminal (phase 3, conditional) = raw view + chat-card view over the same Jesscore session.

## 4. Reuse vs rebuild

Reuse: overlay plumbing, `LinkService`, `ApprovalBook`/`Protocol`/`LinkClient`, `NotificationPlan`/`Notifications`/`BiometricGate`/`QuestionFlow`, `ChatBackend` seam, chat cache, `Glance`/`WidgetData`/`WidgetHub`.
Rebuild: island content (interactive panel), chat message list/markdown/composer, Agents screens.
Consolidate: `GlanceWidget`, `ListWidget`, `GlanceTile` (three renderings of one content).
Delete from scope: any phone-side terminal/supervisor not backed by Jesscore.

## 5. KN-11..KN-17 review

**Finding: KN-11..KN-17 and the "architecture review" are not written anywhere I could read** (vault Historias ends at KN-10; no grep match in koda, kenai, abito-core, Engram). I cannot review them. Decision needed: where is that review (a chat, another session)? Meanwhile this document proposes a candidate set, to be reconciled with the real one:

| Proposed | Story | Needs |
|---|---|---|
| A | Pending requests list + retire on `approvalResolved` everywhere (extends KN-10) | nothing new; engine `approvalResolved` exists [code] |
| B | Koda circle + expandable panel | none for UI; real multi-session QA needs `question` per pillId (exists) |
| C | Widget consolidation + "Sesiones y actividad" widget | none (real data only) |
| D | Quota widget (configurable) | only plan percent/reset; JC-11 and the `usage` frame are missing on the desktop [doc] |
| E | Chat v2 (regenerate/edit, rich markdown, search, actions) | mostly client; regenerate/edit may need engine support [unv] |
| F | Agents (read-only sessions detail) | `sessions` exists but Linux-only via /proc [code] |
| G | Agents: open/create/continue + terminal | new Jesscore contracts (section 6) |
| H | Voice | research + Jesscore audio path (section 7) |

Ordering: A, C, B, E (client-only, can start now) → D after JC-11 → F → G → H.

## 6. Dependencies for Jesscore (to send to its coordinator after approval)

Verified today [code]: `/proc` session listing (Linux, kinds pi/claude-code/codex/opencode/gemini); hook installers; Pi conversations through the SDK child; `ask`/`ui_response`; `decision`/`answer`/`approvalResolved{fingerprint, by?}`; phone link chat + conversations (`link/chat.cjs`).
Absent today [code]: PTY/terminal streaming or attach (no pty dependency); creating/resuming Claude Code or Codex sessions (only Pi); plan usage on the link (JC-11); any user-facing voice (only media transcription via sherpa-onnx/Parakeet for Minikodas).
Contracts to request (each a separate request, none assumed):
1. `sessions` with stable session ids across kinds, state, origin, plus macOS/Windows listing.
2. Session control: create/continue per kind, only for tools Jesscore integrates; prompt to a given session.
3. Terminal stream: output frames, input, resize, reconnect with replay, per-device authorization, read-only default.
4. `usage` frame (JC-11) with a stated source, and a field saying what is unknown.
5. Conformance fixtures for question/approval/notice (shared with Koda).
6. Chat: regenerate/edit semantics, if wanted.
7. Audio path (section 7), only after the feasibility check.

## 7. Voice with NVIDIA [web, partial, [unv] for fit]

- Riva/NIM: streaming ASR (Parakeet/Conformer, 100-800 ms chunks) and TTS (FastPitch, MagpieTTS), needs an NVIDIA GPU server; enterprise deployment licensed separately.
- PersonaPlex (Jan 2026): 7B full-duplex, a fine-tune of Kyutai's Moshi model (not the terminal app); code MIT, weights NVIDIA Open Model License.
- Not evaluated yet: latency on this machine's GPU, license terms for our use, interruption handling, phone-side capture. Jesscore already runs Parakeet through sherpa-onnx for transcription: the cheapest first step is dictation into chat through that path. Do not choose a stack before a spike.

## 8. Phases

- Phase 1 (now, client-only, no engine): A, C, B (panel with handoff to the Activity), E (client part), widget consolidation. Needs: HU in the vault first (rule), design decision on chat direction.
- Phase 2 (needs Jesscore): D after JC-11; F; Koda conformance fixtures.
- Phase 3 (new Jesscore contracts): G (open/continue sessions, terminal).
- Research (parallel): R1 Moshi chat-view UX in depth, R2 ChatGPT mobile patterns, R3 NVIDIA voice spike, R4 terminal rendering libraries on Android (own spike, no code in production).
- Possibly infeasible/costly: full terminal for Claude Code/Codex without Jesscore PTY; accurate token/cost widgets (no data); approving from the overlay or shade without opening the app (Android + our security rule).

## 9. Koda proposal (section 6 of koda-jesscore-primera-entrega.md) — Kenai view, human decides

1. One contract with engine PROTOCOL as source of truth + fixtures: compatible with `ANDROID_LINK.md`; accept in principle.
2. Retire everywhere: Kenai already handles `approvalResolved` (KN-10); `by` ignored. Accept.
3. Single phone front = the engine link: consistent with the backlog decision (jesscore single backend). Consequence for Kenai 0.1: it speaks `island-agent` link/`ANDROID_LINK`, so it works only once Koda 0.3/engine serves approvals; until then it pairs and lists. Human to confirm.
4. D5 own connection vs shared slots: Kenai prefers its own connection (it must live in the background; sharing Settings' slots couples it to the foreground app). Engine slot count (4) must cover phone + Koda. Human to decide.
5. Koda state vocabulary: Kenai has 7 poses over 11 states; propose Koda's `idle, thinking, asking, error` map to `REPOSO, PIENSA, ESPERA, ERROR`; Kenai adds `TRABAJA, ESCUCHA, TERMINO`. Needs agreement on the asset source.
Message to Koda's coordinator is queued (their session was offline); full answer after the human decides.

## 10. Risks, alternatives, decisions pending

Decisions for the human: (a) where the real KN-11..17 review lives; (b) chat direction (Moshi-like vs shipped Koda metrics); (c) drop KN-07 SSH; (d) tab structure (Pendientes inside Inicio vs own tab); (e) D5; (f) voice: dictation first or full-duplex spike; (g) approve the Jesscore request list in section 6.
Risks: building Agents/terminal before Jesscore contracts creates a parallel engine; showing quotas without data misleads; overlay expecting to decide breaks the lock rule; HU missing for every item (workspace rule: HU first).
