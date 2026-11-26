# kn-11-pendientes
Release: 0.2 (version of the story still undecided by the human). Story KN-11 (vault `~/Apps/koda/vault/📋 Backlog/Historias/KN-11.md`). Branch `feat/kn-11-pendientes`, mode organic, Kenai only, no protocol or engine change. Parent plan: odd/research/kenai-evolucion-primera-entrega.md (decisions of 23 Nov: pending requests are a segment inside Inicio, four tabs later).

## Specs
- S1: KN-11 story: «Como usuario de Kenai, quiero ver en Inicio, arriba, todos los pedidos pendientes de mis sesiones (preguntas y aprobaciones) en una sola lista, para atenderlos sin buscarlos por sesión.»
- S2: «Dado un pedido respondido o vencido en otra superficie, cuando llega `approvalResolved{fingerprint}`, entonces desaparece de la lista sin enviar nada.»
- S3: «Dado Permitir o una respuesta, entonces siempre pide huella o bloqueo de pantalla; Denegar sigue sin pedirlo.» (KN-04 S10 must keep holding: nothing in this list decides without the existing gates.)
- S4: Human decision 23 Nov: the list lives inside Inicio, not in its own tab.

## Tasks
- [x] T1 (S1, S2) Pure plan: from the current approvals and pending questions build one ordered list (kind, agent name, session, time left) in `core/` with unit tests: ordering, expiry, removal on `approvalResolved`, empty state. Test-first, no UI. Risk: none. Done in ce2939c.
- [x] T2 (S1, S3, S4) Home UI: a «Pendientes» segment at the top of Inicio using T1; each row opens the existing approval sheet or question screen (reuse `ApprovalSheet`, `QuestionScreen`, `BiometricGate`; no new decision path). English and Spanish strings through `i18n/` + `node scripts/gen-strings.mjs`. Update docs only if a described behavior changes. Risk: item 2 only if a decision path is touched (it must not be); otherwise none. Done in the commit that follows ce2939c (see git log).
- T3 (S1) Live check on the real phone with two sessions waiting. NOT for the child; stays pending, UNVERIFIED on a device until then.

## Allowed edit surfaces
app/src/main/kotlin/com/kenai/android/core/, app/src/main/kotlin/com/kenai/android/ui/HomePanel.kt, app/src/main/kotlin/com/kenai/android/MainActivity.kt, app/src/main/kotlin/com/kenai/android/app/AppModel.kt (read access to state only, no behavior change), app/src/main/res/values/strings.xml, app/src/main/res/values-es/strings.xml, i18n/, app/src/test/, odd/tasks/kn-11-pendientes.md

## Log
- L1 (human, 23 Nov): «vamos con todas ellas» / decisions: chat hybrid, four tabs with pending requests inside Inicio, own connection, dictation with Parakeet, only the engine; stories KN-11..KN-18 created, version left pending («solo dejalo pendiente y luego se ve»).
- L2 (human, 23 Nov): «dale» to start with the first phase-1 story; coordinator chose KN-11 (no new dependency; KN-12 builds on it).
- L3 (coordinator, 26 Nov): MainActivity.kt added so Inicio can compose the Pendientes item
