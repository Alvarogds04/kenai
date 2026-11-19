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
- [x] T1 (S6) Gate «Answer» on a real approval/question; «esperando…» otherwise (core/HomePanel, ui/HomePanel, ResultHero, MainActivity:571, strings + i18n). Test-first. · neutral · test-first RED (canAnswer unresolved) then GREEN, full suite green
- [ ] T2 (S7) `hasDetails` stops counting `project`; update HomePanelTest/SessionScreenTest. · neutral
- [ ] T3 (S4, S12) Map refusal codes in AppModel.onError; keep the card until `approvalResolved`/refusal; restore a claimed approval on refusal. · Risk: contract consumed both sides, security-adjacent (item 2/3), own commit
- [ ] T4 (S3, S10, S9) Notification: approval gets Allow via biometric prompt (opens app gate), question gets «Responder»; no content in the shade. · Risk: item 2 (security), own commit
- [ ] T5 (S5, S11) Docs/KDoc, ANDROID_LINK §7 sync, `agent_pi` pill, test «unlinked ⇒ no notification». · neutral
- [ ] T6 Live check with a real agent through jesscore once the PC receiver is on (UNVERIFIED until then).

## Log
- L1 (human, 17-19 Nov, verbatim in S1-S10; KN-04 criteria confirmed with «aa, ba»).
- L2 (handoff): work only in this worktree; no push/merge without the human; no engine changes; ask jesscore via intercom.
