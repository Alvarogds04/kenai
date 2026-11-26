# kn-13-widgets
Release: 0.2 (story version undecided). Story KN-13 (vault `Historias/KN-13.md`). Branch `feat/kn-13-widgets`, mode organic, Kenai only. Audit of the ten widget surfaces: see odd/research/kenai-evolucion-primera-entrega.md (section 1) and the audit table in the coordinator diagnosis (GlanceWidget, SoloWidget, ListWidget, CharacterBigWidget, StatusPillWidget, WaitingAlertWidget, TodayWidget, MoodWidget, UsageRingWidget, GlanceTile).

## Specs
- S1: «Como usuario de Kenai, quiero menos widgets y mejores, con uno que me muestre mis sesiones, su actividad y mis notificaciones, para ver lo importante sin abrir la app.»
- S2: «Dado el widget de sesiones, entonces solo muestra datos reales del motor (estado, nombre, pasos) y etiqueta como aproximado lo calculado en el teléfono (p. ej. `TodayWidget`).»
- S3: «Dado un widget retirado, entonces el usuario lo ve explicado, sin pérdida silenciosa de funciones.»
- S4: No percentages or estimates presented as complete metrics when the engine does not provide them (human, 23 Nov).

## Tasks
- T1 (S1, S3) Write `odd/research/kn-13-widgets-proposal.md`: for EACH of the ten surfaces its purpose, data consumed, real vs derived, and keep / merge / redesign / remove with the reason. NO deletion yet: the human decides what is removed. Report to the coordinator and wait for the decision on removals.
- T2 (S1, S2) New «Sesiones y actividad» widget (or the redesign of the existing one the proposal recommends) from real data only (`Glance`/`WidgetData`: states, names, steps when `details` exist, pending count), phone-derived values labelled approximate; test-first on the data builder; no wire change. Strings EN/ES.
- T3 (S3) Only after the human decides: remove or merge the agreed widgets, with a visible explanation in the app (no silent loss). Do not start T3 without that decision in the document Log.
- T4 Live check on a real launcher: NOT for the child, UNVERIFIED on a device until then.

## Allowed edit surfaces
app/src/main/kotlin/com/kenai/android/app/ (widget files, GlanceTile, WidgetHub, CharacterBitmap only), app/src/main/kotlin/com/kenai/android/core/WidgetData.kt, app/src/main/kotlin/com/kenai/android/core/Glance.kt, app/src/main/kotlin/com/kenai/android/core/TilePlan.kt, app/src/main/AndroidManifest.xml (widget receivers only), app/src/main/res/layout/, app/src/main/res/xml/, app/src/main/res/drawable/, app/src/main/res/values/strings.xml, app/src/main/res/values-es/strings.xml, i18n/, app/src/test/, odd/research/kn-13-widgets-proposal.md, odd/tasks/kn-13-widgets.md

## Log
- L1 (human, 26 Nov): «dale hasta terminar con todo». Human, 23 Nov: priority widgets are one of sessions/activity/notifications and one of quotas (quotas = KN-14, blocked on the engine's `usage` JC-11, not here).
