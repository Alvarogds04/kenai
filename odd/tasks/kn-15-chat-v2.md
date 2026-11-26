# kn-15-chat-v2
Release: 0.2 (story version undecided). Story KN-15 (vault `Historias/KN-15.md`). Branch `feat/kn-15-chat-v2`, mode organic, Kenai only, NO wire/protocol change (anything that needs the engine is reported, not invented). Decision of the human, 23 Nov: hybrid: ChatGPT-grade structure and functions with the shipped Koda tokens and colours (`chat-koda-kenai`, KN-03).

## Specs
- S1: «Como usuario de Kenai, quiero un chat fluido y cómodo (regenerar, editar, acciones por mensaje, markdown completo, buscar conversaciones), para conversar con modelos desde el teléfono como en ChatGPT.»
- S2: «Dado una respuesta con tablas, enlaces o listas anidadas, entonces se muestran bien.»
- S3: «Dado muchas conversaciones, cuando busco, entonces las encuentro en el teléfono.»
- S4: «Dado el diseño, entonces conserva las métricas y colores de Koda ya publicados.»
- S5: «Dado una respuesta, cuando toco regenerar o edito mi mensaje, entonces se reenvía el turno (el motor define la semántica).» Needs engine support that is NOT verified (Jesscore: «no lo sé»). Do not invent wire.

## Tasks
- T1 (S2, S4) Markdown: extend the pure parser behind `MarkdownLite` (`ui/ChatScreen.kt`) to headings, tables, links (safe: show target, open only http/https after a tap) and nested lists, test-first on the parser, render with existing tokens.
- T2 (S1, S4) Per-message actions: copy message, copy code block, select text, share; no engine involvement.
- T3 (S3) Local search over the cached conversations (`ChatCache`/`ChatConversations`): pure search function with tests, search field in the conversations sheet; works offline from the cache.
- T4 (S1) Composer and states polish within the hybrid design: input limit message, draft kept per conversation, clear stopped/failed/retry states; retry of a failed turn resends the same text through the existing send path.
- T5 (S5) Investigate only: read `docs/ANDROID_LINK.md` and `Protocol.kt` and write in the document Log whether regenerate/edit can be done client-side with the existing frames (e.g. resend the same user text as a new turn) or needs the engine; implement only the client-only variant if it is honest about what it does (a new turn, not a rewrite), otherwise leave pending with the evidence.
- T6 Live check on the phone: NOT for the child, UNVERIFIED on a device until then.

## Allowed edit surfaces
app/src/main/kotlin/com/kenai/android/ui/ChatScreen.kt, app/src/main/kotlin/com/kenai/android/core/Chat*.kt, app/src/main/kotlin/com/kenai/android/link/ChatLinkBackend.kt (no wire change), app/src/main/res/values/strings.xml, app/src/main/res/values-es/strings.xml, i18n/, app/src/main/kotlin/com/kenai/android/core/MarkdownLite.kt, app/src/main/kotlin/com/kenai/android/app/AppModel.kt (only the read-only chatCachedMessages accessor), app/src/test/ (incl. MarkdownLiteTest.kt), odd/tasks/kn-15-chat-v2.md

## Log
- L1 (human, 26 Nov): «dale hasta terminar con todo».
- L2 (coordinator, 26 Nov): parser lives in core/MarkdownLite.kt, added to the surfaces.
- L3 (child, 26 Nov): T3 search: pure `ChatSearch.filter` covers titles AND cached message text (tested), but the sheet can only pass titles today: the cached messages live in a private `ChatConversations` inside `app/AppModel.kt`, which is not in the Allowed edit surfaces. Needs a one-line accessor in AppModel (coordinator decision); until then the sheet searches titles (still offline, from the cache).
- L4 (child, 26 Nov): T5 evidence. `Protocol.kt` ClientMsg has only ChatModels, ChatConversations, ChatNew, ChatOpen, ChatDelete, ChatSend(id, model, text, conversation?), ChatCancel, ChatReset; `docs/ANDROID_LINK.md` defines no frame to rewrite, truncate or regenerate a turn. So a true regenerate/edit (replace the old answer, fork history) NEEDS THE ENGINE (S5 stays open for that). Client-only, honest variant implemented: «Ask again» resends the last user text as a NEW turn through the normal `chatSend`, «Edit as new message» loads a user message into the composer and sends a new turn; the old turn stays in the history. No wire change (a test pins that Protocol.kt has no regenerate frame). The engine's semantics for a real regenerate are still unknown (Jesscore: «no lo sé»).
- L5 (coordinator, 26 Nov): YES to a one-line read-only accessor in AppModel.kt (added to the surfaces); S5 stays open for the engine, no wire invented; no more reviews from the child (coordinator runs them against release/0.2); merge release/0.2 first. Done: `AppModel.chatCachedMessages()`, the sheet search now covers cached message text.
- L6 (child, 26 Nov): review finding fixed: drafts now round-trip ALL conversations through the saveable state (ChatDrafts.save/restore, behaviour-tested); also: Copy code copies every block, Edit never overwrites an unsent draft, search key includes the cached messages, ChatScreen KDoc updated, source-grep tests replaced by behaviour tests.
- L7 (child, 26 Nov): follow-up on branch fix/kn-15-followup (from release/0.2): lazy item wrapped in one Column (ChatRowPlan decides the row actions, tested), search index built once with stable keys, named limits, no dead guard/fallback, share/open failures say so, Heading level documented.
