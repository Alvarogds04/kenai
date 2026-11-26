# kn-12-circulo
Release: 0.2 (story version undecided). Story KN-12 (vault `Historias/KN-12.md`) plus the KN-11 follow-ups. Branch `feat/kn-12-circulo`, mode organic, Kenai only, no protocol change. Plan: odd/research/kenai-evolucion-primera-entrega.md.

## Specs
- S1: «Como usuario de Kenai, quiero que el círculo flotante de Koda avise con discreción y, al tocarlo, abra un panel con las preguntas de distintas sesiones, para responderlas sin ir a la app.»
- S2: «Dado pedidos pendientes, cuando el círculo está visible, entonces avisa con una pulsación discreta y el número de pedidos.»
- S3: «Dado Denegar, entonces se resuelve desde el panel sin abrir la app.» and «elegir una opción o Permitir abre Kenai para la huella (Android no deja pedirla desde un overlay ni desde la notificación).» Allow and answers never complete inside the overlay (KN-04 S10).
- S4: «Dado que no hay permiso de superposición, entonces los pedidos llegan como notificaciones normales (KN-08).»
- S5 (KN-11 review follow-ups, non-blocking findings of review-b29e4b56c728fba5).

## Tasks
- T0 (S5) Fix the KN-11 findings, test-first: an approval row whose session is not found must still open its sheet (the early return in MainActivity's row tap handler applies only to question rows); `ApprovalSheetPlan.hero` vs an expired first approval (decide and test); either use `PendingPlan.resolved` or remove it; tidy the inline fully-qualified names. Risk: none. Done in 789451c.
- T1 (S1, S2) Pure plan in `core/` for the circle: what it shows (count, discreet pulse, quiet when nothing pending, reduced motion honoured) and the panel's content (pending list from `PendingPlan`), with unit tests. Done in 50f016f.
- T2 (S1, S3, S4) Overlay UI: the small Koda circle and the expandable panel reusing the island window plumbing (`IslandOverlay`, geometry, tap guard, permission policy, `safely{}`); the panel lists pending requests per session; Deny resolves via the existing `decide(fp,false)` path with no new decision code; every other action hands off to MainActivity through the existing deep link. Strings EN/ES via i18n. Risk: item 2 only if a decision path is touched (it must not be). Done in 29601eb.
- T3 Live check on the real phone: NOT for the child, UNVERIFIED on a device until then.

## Allowed edit surfaces
app/src/main/kotlin/com/kenai/android/core/, app/src/main/kotlin/com/kenai/android/ui/IslandOverlay.kt, app/src/main/kotlin/com/kenai/android/ui/HomePanel.kt, app/src/main/kotlin/com/kenai/android/MainActivity.kt, app/src/main/kotlin/com/kenai/android/app/AppModel.kt (wiring only), app/src/main/res/values/strings.xml, app/src/main/res/values-es/strings.xml, i18n/, app/src/test/, odd/tasks/kn-12-circulo.md

## Log
- L1 (human, 26 Nov): «dale hasta terminar con todo» (all of phase 1 that does not depend on Jesscore). Decisions of 23 Nov: pending inside Inicio, own connection, only the engine.
