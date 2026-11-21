# kenai-ac08-by
Kenai side of AC-08 (one owner per agent request). Story KN-10. Branch `test/kenai-ac08-by`, mode organic, Kenai only. Release: 0.2.

## Specs
- S1: «yo ya escojo en alguno de los 3 y listo» (AC-08 S1): a request answered on another surface disappears from Kenai.
- S2: the engine withdraws via `approvalResolved{fingerprint}` (engine `chat.cjs announceResolved`), with an optional `by` ("pi"|"koda"|"phone") coming in AC-08 U5. Kenai ignores `by` and unknown keys.

## Tasks
- T1 (S1, S2) Pin in `link/ProtocolTest.kt` that `approvalResolved` with `by` ("pi","koda","phone") and extra unknown keys decodes to `ServerMsg.ApprovalResolved(fp)`, and in `LinkClientTest.kt` that it retires the open request. No production change unless it fails. Risk: none expected (item 3 only if the decoder changes). Native review. · commit: see git log (test: pin approvalResolved by)
- T2 (S1) Live three-surface test (AC-08 T6) with the human and the phone. · blocked by Koda `ask` branch, U5 in release/0.2 and the human's deploy · evidence: (pending)

## Log
- L1: «la historia KN para «retirar si se responde en otra superficie» está APROBADA y la hija test/kenai-ac08-by va adelante, en organic» (human via Abito-core coordinator, 21 Nov).
