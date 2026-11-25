# kn-04-followup
Release: 0.2. Leftovers of KN-04 (agent requests through jesscore) after `feat/kn-04-agent-requests` was merged into `release/0.2` (aa46856). Branch `fix/kn-04-followup`, mode organic, Kenai only, no engine or Koda changes. Parent document: odd/tasks/kn-04-agent-requests.md (read its Specs S4, S5, S10, S12 and the T3b/T5/T6 lines).

## Specs
- S1: «Allow always asks fingerprint or screen lock (kenai-0.2 S4); an intent never carries an allow.» (KN-04 S10; must keep holding)
- S2: «S5: With no agent connected or the phone unlinked, nothing reaches that phone.» (KN-04 S5)
- S3: «S4: After answering, the request is resolved in Kenai; if resolved elsewhere, Kenai marks it resolved and does not allow answering again.» (KN-04 S4, S12)

## Tasks
- T1 (S3) Close what is left of T3b of the KN-04 document. Commit 51ec28b already pairs a refusal with its request by the echoed `fingerprint` (FIFO fallback) and handles `failed`. Verify against the code and tests which of these review findings (review-7f236e08be82d0e2) are still open: `refusedButPending` clears the answer note of an unrelated request; `answerRefused` is tested only by source greps (extract a pure function and test it). Fix only what is still open, test-first. Risk: item 2/3 (answers path), own commit, native review.
- T2 (S2) Test «unlinked ⇒ no notification»: with the phone unlinked (no pairing, or link stopped) no approval, question or status notification is posted. Test-first through the public seam (the pure plan in `core/NotificationPlan.kt` or the model), no source greps. Risk: none.
- T3 (S1, S3) Live check on the real phone through jesscore: Deny, a question with several options / multiSelect, the shade «Responder» tap, an approval Allow behind the fingerprint. · NOT for this child: it needs the human, the phone and the engine. Stays pending in this document; evidence goes into the KN-04 document L-entries. UNVERIFIED on a device until then.

## Log
- L1 (human, 21 Nov): «lo que quede pendiente de esas sesiones dejalo pendiente y abrelo que salga de ti como hija en segun la nueva norma».
- L2 (coordinator, 23 Nov): review of T2 (uncommitted): `NotificationPlan.mayPost` ignores its `kind`, and `Kind.QUESTION` has no call site in `AppModel` (only APPROVAL and STATUS are gated), so the test pins a function whose three kinds are not all used. Fix before committing T2: gate the question notification too (find where the question notice is posted), and make the test meaningful through the seam (or drop `kind` if it cannot differ). Then T2 gets its own commit and a native review (RDD is mandatory here; the review of T1 was declined at the host consent prompt, ask the human again, do not skip silently). Rules: JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 and ANDROID_HOME=/home/alvaro/Android/Sdk for gradle (no local.properties in worktrees).
