# kn-05-long-line
Release: 0.2. Story KN-05 (the link must not drop by itself every few seconds). Cause found 18 Nov from jesscore's network capture: the engine sent one line of ~200 KB (`chatModels`, 1774 models) and Kenai cuts above 64 KiB (`LinkClient.kt` readLine, `Protocol.MAX_LINE_BYTES`): it closes without `bye` and reconnects a second later, in a loop. The engine fix is Abito-core's (AC-05 which models are sent, AC-06 size cap); not here. The diagnostic logging of T13 is archived at tag `archive/kn-05-link-diagnostics` and is NOT to be re-applied. Branch `fix/kn-05-long-line`, mode organic, Kenai only.

## Specs
- S1: KN-05 story: «Como usuario de Kenai, quiero que el teléfono mantenga abierto el enlace con mi motor mientras no cambie nada, para que la pantalla de inicio y el chat no se queden en «sin conexión» aunque el emparejamiento esté bien.»
- S2: «ante una línea demasiado larga muestre un error claro en vez de reconectar en bucle» (what the story leaves for Kenai).

## Tasks
- T1 [x fab44f0] (S2) A line above `Protocol.MAX_LINE_BYTES` stops the reconnect loop: the link reports a distinct, final-or-backed-off state with a clear message in English and Spanish (the engine sent something the phone cannot read; update the engine) instead of `LostReason.NETWORK` + retry every second. Decide the smallest shape from the code (a new `LostReason`, or a fatal flag like auth) and keep 0.1 desktops working. Test-first with a fake desktop that sends an over-long line: RED today (it reconnects), GREEN after. Update `docs/ANDROID_LINK.md` and strings (`node scripts/gen-strings.mjs`). Risk: item 3 (consumed contract) only if the wire changes (it must not); otherwise none. Native review.
- T2 [dropped: no failing test justifies it; no behaviour changed] (S1) A test «same address and link active ⇒ `stop()` is not called» only if the code shows the discovery restart still exists; the story's first guess was refuted on 18 Nov, so do not change behaviour without a failing test.
- T3 (S1) Live check: two minutes connected on the real phone with nothing touched, link stays «conectado». · NOT for this child (needs the phone and the engine after AC-05/AC-06); stays pending here.

## Log
- L1 (human, 17-18 Nov, KN-05): the 0.2 phone closes its link every 5-12 s; engine ruled out first, cause later found in the capture above.
- L2 (human, 21 Nov): «lo que quede pendiente de esas sesiones dejalo pendiente y abrelo que salga de ti como hija en segun la nueva norma».
