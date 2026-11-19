# kn-04-agent-requests
KN-04: Kenai shows the agents' notices and requests (Claude Code, Codex, Pi with Gentle Shell) and answers them from the app and the Android notification, through jesscore. Story: ~/Apps/koda/vault/📋 Backlog/Historias/KN-04.md. Engine contract: abito-core engine/docs/PROTOCOL.md (AC-07). Branch feat/kn-04-agent-requests from feat/kenai-0.2.

## Specs
- S1: «importante kenai esta pensado para que le salga los avisos de claudecode, codex y eso como koda eso est apuesto?» / «Si por que seto tiene que ser igual que koda»
- S2: Notices (finish, fail, limit) show on Kenai's home and raise an Android notification, like Koda.
- S3: A permission or question shows with the same text as Koda; accept, reject or pick one or several options from Kenai and from the notification.
- S4: After answering, the request is resolved in Kenai; if resolved elsewhere, Kenai marks it resolved and does not allow answering again.
- S5: With no agent connected or the phone unlinked, nothing reaches that phone.
- S6 (A-a): An `attention` notice with no real request shows «esperando…», NOT an «Answer» button; the button appears only when an `approval`/`question` frame arrives.
- S7 (B-a): Hide the session detail screen while the engine sends no steps/finalLine/files.
- S8: «quiero lo generico y que siempre se use lo generico como requisito»: requests come from the agent's generic native mechanism; never a custom ask tool.
- S9: «de momento no quiiero que kenai ni nkoda, pueden alnzar comandos lo que se van a poder es aceptar por hooks»: Kenai launches no commands.
- S10: Allow always asks fingerprint or screen lock (kenai-0.2 S4); an intent never carries an allow.
- S11: Protocol.kt:112 KDoc stops saying «at most 500 characters» (engine sends the producer's text verbatim, 500/2000).
- S12: Refusal codes `off`, `resolved`, `truncated`, `invalid_answer`, `bad_request` are handled by Kenai, not shown as a generic error.

## Tasks
- [x] T1 (S6) Gate «Answer» on a real approval/question; «esperando…» otherwise (core/HomePanel, ui/HomePanel, ResultHero, MainActivity:571, strings + i18n). Test-first. · neutral · test-first RED (canAnswer unresolved) then GREEN, full suite green · commit 636d46e, native review approved (review-7db43f216db575f5), 4 non-blocking findings fixed in the follow-up commit
- [x] T2 (S7) `hasDetails` stops counting `project`; update HomePanelTest/SessionScreenTest. · neutral · test-first RED then GREEN, full suite green
- [x] T3 (S4, S12) Map refusal codes in AppModel.onError; keep the card until `approvalResolved`/refusal; restore a claimed approval on refusal. · Risk: contract consumed both sides, security-adjacent (item 2/3), own commit · commit 9cc7bf1, native review approved (review-7f236e08be82d0e2, 5 non-blocking findings -> T3b); worker mwo30vu1; RED (LinkRefusalTest 4 of 5 failed) then GREEN; full suite 1037 tests, 0 failures, 3 skipped
- [ ] T3b (S4, S12) Non-blocking findings of the T3 review (review-7f236e08be82d0e2): the refusal frame carries no fingerprint and LinkClient pairs it with ONE in-flight slot, so two answers in flight can mix up cards (use a FIFO of in-flight answers, or ask the engine to add `fingerprint` to refusals); `refusedButPending` clears the answer note of an unrelated request; `answerRefused` is tested only by source greps (extract a pure function); `failed` code unmapped. · Risk: item 2/3, own commit
- [x] T4 (S3, S10, S9) Notification: approval gets Allow via biometric prompt (opens app gate), question gets «Responder»; no content in the shade. · Risk: item 2 (security), own commit · worker mwo3ky8a; RED (7 of 55 failed) then GREEN; full suite 1043 tests, 0 failures, 3 skipped; UNVERIFIED on a device until T6
- [ ] T5 (S5, S11) Docs/KDoc done (Protocol.kt, ANDROID_LINK §6/8.4); `agent_pi` pill BLOCKED: Kenai's catalog mirrors Koda's pills.ts (PillsTest) and Koda has no `agent_pi`; test «unlinked ⇒ no notification» pending. · neutral
- [x] T7 (S3, L8) A `question` frame with no `sessions` row still shows on Home and in the shade (SessionsWithQuestions + announceSessions). · Risk: none (UI state only)
- [x] T8 (S2, S3, L9) Keep the link alive in the background: start the foreground service from the screen. · Risk: item 5 (environment) · live-verified
- [ ] T6 Live check with a real agent through jesscore once the PC receiver is on (UNVERIFIED until then).

## Log
- L1 (human, 17-19 Nov, verbatim in S1-S10; KN-04 criteria confirmed with «aa, ba»).
- L2 (handoff): work only in this worktree; no push/merge without the human; no engine changes; ask jesscore via intercom.
- L3 (human, 19 Nov, via jesscore, verbatim «1 si, 2 si»): (a) «Responder» on a question notification opens the app at that question, never a bare Allow in the shade. (b) Pi gets its own `agent_pi` pill/icon like Claude Code and Codex; no visual preference given, so a sober one consistent with the rest.
- L4 (jesscore, 19 Nov): PC receiver ON (engine 83ed71e); phone d-3b099c35 paired with answers ON; jesscore runs the first live test with the human and reports the outcome.
- L5 (human, 19 Nov, via jesscore, verbatim): «y que ambos muestren a pi, claude code y el resto». Decision: Koda adds `agent_pi` (name Pi, sober colour, category AGENT) to pills.ts and Kenai mirrors it, keeping the shared catalog. Koda's session is offline; jesscore passes it the request. Until then Kenai's catalog is unchanged so PillsTest keeps passing; the `agent_pi` part of T5 stays pending.
- L6 (jesscore-2, 19 Nov): QP/1 level 2/3 handshake fix is live in the engine (main 7984cdd, restarted). T6 can run once the human reloads Pi.
- L7 (T4 verify, mwo3vdpy, 19 Nov): independent verifier found no blocking issue in dd2c18d (S3, S9, S10 PASS; S5 PASS for the T4 surface, its test reads source only). Non-blocking: a stale `wantAllow` is not cleared on expiry (same-fingerprint request only, still behind the prompt; earlier behaviour); Review/Allow/Responder PendingIntent codes (fixed below); behaviour on device UNVERIFIED until T6.
- L8 (live probe, 19 Nov): the engine's `question` frame for a Pi ask decoded fine (test added with the exact frame) but nothing showed: Home, the badge and the notification read `sessions`, and the engine sent no `sessions` row for agent_pi. Fix T7: a pending question now gives its agent a QUESTION row (core/SessionsWithQuestions) and goes through the same announcement path as a `sessions` frame. Reinstalled on the phone; live re-test pending.
- L9 (live test, 19 Nov): the link dropped ~10 s after HOME. Root cause (adb): Android denied `startForegroundService` from a background receiver («Background started FGS: Disallowed … uidState: RCVR»), `LinkService.start` swallowed it, and opening the app never retried (the link already existed), so the process stayed cached and the firewall cut its network (`blocked=APP_BACKGROUND`). Fix T8: `AppModel.ensureLinkService()` from `MainActivity.onStart` + a log line on refusal. Verified on the device: service `isForeground=true`, `procState=FGS`, link green for 60 s in background.
