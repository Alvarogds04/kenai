# KN-13 — Widget proposal (T1)

Status: PROPOSAL, nothing deleted. The human decides removals (T3). Source: reading of the widget classes, `WidgetData`, `Glance`, `TilePlan`, the manifest and `res/xml/*widget_info.xml` on branch `feat/kn-13-widgets`. Not verified on a device (T4).

## Data that exists (the only inputs any widget may use)

Real, from the engine: per session `pillId`, agent name, `state` (11 wire states), `statusText`, `stepIndex`/`stepCount`, `updatedAtMs`; with the `details` capability also `steps`, `finalLine`, `project`; the pending approvals and questions this phone holds; plan `usage` (percent + `resetsAt`, only when the computer sends it; the desktop does not yet implement JC-11, so KN-14, not here).
Derived on the phone: every copy line, tone and mood, the Waiting count mix (phone requests + questions inferred from states), and `today` (`TodayStore`: a counter of finished tasks kept on the phone, reset at local midnight).
Note: `WidgetContent` today never carries `stepIndex/stepCount` nor `project`, so no widget shows steps; the new widget has to add them (T2).

## The ten surfaces

| # | Surface | Size | Purpose | Data | Real / derived | Proposal | Reason |
|---|---|---|---|---|---|---|---|
| 1 | `GlanceWidget` | 3x2 | Summary: headline, badge, up to 3 agent lines | `Glance` (states, names, waiting) | Real states; headline and lines derived wording | **REDESIGN** into «Sesiones y actividad» | Closest to the S1 goal; same content as notification and tile. Add steps, pending count and notifications. |
| 2 | `ListWidget` | 4x3 | Headline plus up to 5 rows (agent, state word, dot) | `WidgetContent.tiles` | Real | **MERGE** into #1 (the large size of the new widget) | Same content as #1 at a larger size; two widgets for one data set. No function lost if #1 resizes to 4x3. |
| 3 | `GlanceTile` (quick-settings tile) | tile | On while connected; second line says what waits; opens the pending item | `TilePlan` | Real | **KEEP** | Not a widget; different surface (shade), already consistent through `TilePlan`. Out of the «fewer widgets» count. |
| 4 | `WaitingAlertWidget` | 3x2 / 2x2 | «N te esperan», one row per request/question, opens Home | phone requests and questions | Real (phone holds them) | **MERGE** into #1 as the pending/notifications section (its rows and button) | S1 wants sessions, activity and notifications in ONE widget; this is the notifications part. Keep its row logic (`alertRows`, `alertButton`) and its tone rule. |
| 5 | `SoloWidget` | 2x2 | Kenai + top agent name + state word | top tile | Real | **REMOVE** (or keep as the compact size of #1 if the human prefers) | Subset of #1; one agent only. Explain in the app where it went. |
| 6 | `StatusPillWidget` | 4x1 | Slim line: top agent + four shortcut buttons (Home, Chat, Settings, Recent) | top tile | Real | **KEEP-or-REMOVE, human decision** | The only widget with launcher shortcuts; removing it loses that function. Alternative: move the shortcuts to the 4x4 widget (#7). |
| 7 | `CharacterBigWidget` | 4x4 | Kenai + top agent, plan usage rows (Claude, Codex), shortcut buttons | tiles + `usageRows` | Real (usage only when the computer sends it) | **REDESIGN later** (quotas = KN-14) | Its usage half belongs to the quota widget (blocked on JC-11). Until then keep unchanged; do not touch in KN-13 beyond what T3 decides. |
| 8 | `UsageRingWidget` | 2x2 | Ring with plan percentage | `ring` | Real when sent, otherwise asks for the switch | **KEEP, owned by KN-14** | Quota widget; out of KN-13. Already follows S4 (shows only what the computer sent, no estimate). |
| 9 | `TodayWidget` | 2x2 | Count of tasks finished today | `TodayStore` counter on the phone | **Derived**, phone-local, not an engine metric | **REMOVE**, or keep only labelled «aprox. / en este teléfono» | S2/S4: a phone-local count presented as a day metric is an estimate. Silent loss avoided by an in-app note. If kept, it must be labelled as approximate and counted on this phone only. |
| 10 | `MoodWidget` | 1x1 | Kenai's face in the top state | top tile | Real (face is derived from state) | **KEEP (cheap, decorative) or REMOVE** | No information beyond a pose; a 1x1 is the only size that fits a crowded launcher. Low value; human taste. |

## Proposed result

From ten surfaces to **five receivers**: «Sesiones y actividad» (#1+#2+#4, sizes 3x2 and 4x3, optional 2x2 compact replacing #5), Status Pill (#6, if kept), Kenai Big (#7, until KN-14), Usage Ring (#8, KN-14), Mood (#10, if kept); plus the quick-settings tile (#3). `TodayWidget` (#9) retired or relabelled.

## «Sesiones y actividad» content (for T2)

- Headline and tone: unchanged `Glance`/`WidgetContent` rule.
- Rows (up to 5, most urgent first): name, state word, dot; **steps** «3/8» only when `stepCount > 0` (real engine data); **project** only when `details` is on.
- Pending section: the count and rows of `alertRows` (requests then questions), with the button to Home. Notifications = what the phone holds as pending; the widget does not read the system notification shade (not possible without a listener permission; not proposed).
- Phone-derived values (if any, e.g. a today count) carry the word «aprox.» / «approx.» (S2). No percentages or ETAs invented from steps (S4): steps are shown as `n/m`, never as «60 %».
- Strings EN/ES. No wire change.

## Decisions needed from the human (T3 is blocked until they are in the Log)

1. Retire `ListWidget` and `WaitingAlertWidget` by merging them into the new widget? (recommended: yes)
2. `SoloWidget`: remove, or keep as the 2x2 size of the new widget? (recommended: remove, 2x2 compact size of the new one)
3. `TodayWidget`: remove, or keep labelled «aprox., en este teléfono»? (recommended: remove)
4. `StatusPillWidget`: keep (it holds the shortcuts) or remove? `MoodWidget`: keep or remove?
5. Is the new widget a redesign of `GlanceWidget` (keeps the existing placed widgets for users, recommended) or a new receiver?

## S3: explanation of what was removed

Whatever is removed: the app shows a one-time card in Settings/Inicio naming each retired widget and where its function went; a removed receiver stays in the manifest as a thin alias only if needed to avoid a broken placed widget (decided in T3, after the human decision).
