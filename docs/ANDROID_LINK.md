# The Kenai phone link — protocol v1

This document is the **contract of the phone link**: what the Kenai Android app (release 0.1) implements and
expects today. The Koda desktop implements its side from this document alone, so it is written as the desktop's
requirements, not as a description of “whatever the phone happens to do”.

- **Phone side:** `app/src/main/kotlin/com/kenai/android/link/` (protocol, client, pairing, discovery, approvals),
  `app/src/main/kotlin/com/kenai/android/core/QuestionFlow.kt` (answering rules),
  `app/src/main/kotlin/com/kenai/android/app/NsdDiscovery.kt` (mDNS browsing).
- **Desktop side (this document's audience):** Koda, branch `release/0.2`,
  `apps/desktop-tauri/src/phone_link/` (`ids.rs`, `pairing.rs`, `server.rs`, `hub.rs`, `admission.rs`, `source.rs`).
- **Executable reference:** `tools/dev-desktop.mjs` is a second, independent implementation of this protocol
  (Node's TLS stack, its own certificate, its own JSON) that the phone passes its interoperability tests against.
  Where this document quotes behaviour, that script is the authority for it.

Everything here is **local network only**. There is no relay, no cloud, no account: the desktop listens on the
LAN, the phone connects to an address inside it, and nothing leaves the network.

> Wire identifiers stay **`abito`** (`abito://pair`, `_abito._tcp`) until K2-12. They are wire identifiers, not
> branding: do not rename them, or every phone pairing in the field stops working.

---

## 0. Where the two sides stand today

The desktop's current slice A is real and correct: identifiers, certificate and token, the pairing link and its
QR SVG, a TLS server, LAN-only admission, `hello` → `welcome`, the session hub, `ping`/`pong`, `bye` and `error`.
What it does **not** do yet is in the table in [§16](#16-support-today-phone-vs-desktop), and the short version is:

- `server::serve()` is implemented and tested but **never called**: `src/paused.rs` marks `Feature::Phone` as
  paused, so nothing listens, nothing is announced, and no timer runs. That is the single switch to flip.
- There is no mDNS announcement: `ids::MDNS_SERVICE` exists as a constant and is only asserted in a test.
- There is no pairing surface: `pairing::pairing_link` and `pairing::qr_svg` are only called from tests.
- `welcome` never carries `caps` (`Features` is an empty trait and `Shared::features` is deliberately unused),
  so a phone is told that the desktop has no optional feature at all.
- Approvals, questions, diffs, usage, services and chat do not exist on the desktop yet. `hub.rs` has the state
  names and the vocabulary for them; nothing sends or accepts them.

The phone is the complete side: it speaks every message in this document, and it degrades correctly when the
desktop offers less (see [`caps`](#desktop--phone-welcome) and the “ignored when not offered” rules).

The conversation frames (`chatConversations`, `chatNew`, `chatOpen`, `chatDelete`, `chatDeleted`, and `conversation`
on `chatSend`) are the newest and the most cautious: they are additive on v1, a desktop that knows none of them drops
them, and the phone then chats exactly as it did in 0.1 — one conversation, no list on screen.

---

## 1. Identifiers

| Thing | Value | Where |
| --- | --- | --- |
| Pairing scheme and host | `abito://pair` | `pairing.rs`, `Pairing.kt`, `AndroidManifest.xml` |
| mDNS service type | `_abito._tcp` | `ids::MDNS_SERVICE`, `Discovery.SERVICE_TYPE` |
| Default port | `47821` | `Discovery.DEFAULT_PORT`; any port in the link wins |
| Protocol version | `1` (`v`) | `Protocol.VERSION`, `server::PROTOCOL` |
| Certificate common name | `Abito phone link` | `ids::CERT_CN` (the phone never checks a name) |
| Fallback computer name | `Abito` | `ids::DEFAULT_NAME` |

The certificate's common name and the computer name are cosmetic: the phone authenticates the certificate by pinning
the **fingerprint**, never by name, issuer or hostname.

---

## 2. Pairing: `abito://pair`

The desktop shows the link as a QR code in its phone-link settings and lets the user copy it. The phone accepts it
three ways, all through the **same parser**, so a scanned code, a pasted link and an `abito://` intent are accepted
and refused for the same reasons:

- the camera scan screen, which decodes a QR and hands the text to the parser;
- “Paste the link”;
- an `VIEW` intent for `abito://pair` (the manifest filter makes a QR scanned by any camera app open Kenai).

### 2.1 Format

```
abito://pair?v=1&host=<host>&port=<port>&fp=<sha256>&token=<token>&name=<name>
```

Example (real shape; the fingerprint is 64 lowercase hex characters):

```
abito://pair?v=1&host=192.168.1.20&port=47821&fp=6f1c0e13a9b6d0c58b7c1a94e2d3f4a5b6c7d8e9f0a1b2c3d4e5f60718293a4b&token=aZ3kQ9mN2pXrT7vL&name=Alvaro%20PC
```

Worked example from the reference desktop (`--name "Léa's PC & co"`), which shows how the name is encoded:

```
abito://pair?v=1&host=192.168.1.20&port=47821&fp=abababab…ab&token=tok_en-1234567890abcd&name=L%C3%A9a%27s%20PC%20%26%20co
```

### 2.2 Fields

| Field | Required | Type / format | Rules the phone enforces |
| --- | --- | --- | --- |
| `v` | yes | integer as text | must be exactly `1`. Anything else is refused (no forward compatibility is attempted). |
| `host` | yes | IPv4 literal or host name, percent-encoded | non-blank, at most 253 characters. The phone connects to this string verbatim, so an IPv4 address of the computer on the LAN is what belongs here. |
| `port` | yes | integer as text | `1..65535`. Real ones are the listener's port; `47821` is the default. |
| `fp` | yes | **SHA-256 of the certificate DER, 64 lowercase hex characters** | exactly 64 hex characters; uppercase is accepted and lowercased. This is the only thing that authenticates the desktop. |
| `token` | yes | 16 to 128 characters of `[A-Za-z0-9_-]` | length and alphabet are enforced; the desktop's own generator makes **32 characters** (`random_token`: 32 random bytes, low six bits, base64url alphabet = 192 bits of entropy). |
| `name` | no | percent-encoded UTF-8, human name of the computer | cut to 64 characters on the phone; the desktop cuts it to 48 before building the link. Missing means empty, and the phone then shows a generic name. |

Rules that make parsing predictable:

- Unknown fields are **ignored**, not refused. A link that still carries a dead cloud relay's `relay`, `room`,
  `key` or `access` fields pairs as the same LAN pairing — an app update must never lose a pairing that way.
- The whole link is at most **2048 characters** on the phone (`PairingScan.MAX_LINK`); anything longer is not
  offered as a pairing. Real links are a few hundred characters.
- Fields are percent-decoded with UTF-8; a field that fails to decode, or any required field missing or out of
  range, makes the link invalid (the app says so and offers nothing else).

### 2.3 The fingerprint (`fp`)

```
fp = lowercase hex of SHA-256( DER bytes of the desktop's TLS certificate )
```

- The desktop computes it once, when it creates the certificate, and stores it with the certificate
  (`pairing.rs` → `Identity.fingerprint`, `hex(sha256(cert_der))`).
- The phone **pins** it: its trust manager accepts exactly the leaf certificate whose SHA-256 equals `fp`, accepts
  no other certificate, no CA chain, no client certificate and no hostname. A desktop whose certificate changes
  must pair again — that is the point.
- Uppercase is tolerated on input, and the comparison is case-insensitive. SHA-256 digests on the desktop are
  produced in lowercase hex; keep it that way so both sides read the same string.

### 2.4 The token

The token is the **shared secret of the pairing**: a random string the desktop generates once and keeps next to
the certificate (OS keystore, never a file, never `settings.json`). It is sent only inside `hello` and only after
the TLS handshake has already authenticated the desktop by pin. Its purposes:

- it distinguishes *this* phone's pairing from a stranger that somehow reached the pinned certificate (a
  re-flashed phone, a screenshot of the QR, a lost phone);
- it can be rotated (`new_token`) to lock out whatever held the old one, without a new certificate and therefore
  without a new QR shape (the QR does hold the token, so a rotation means a new link — pair again).

The desktop **never** sends the token back, never logs it, and never puts it in a URL after pairing. The phone
keeps it in encrypted storage (`SecureStore`) as part of the five stored fields (host, port, fingerprint, token,
name), one per line, so an older build's extra relay lines still read as the same LAN pairing.

### 2.5 The QR code

- The desktop renders the link as an SVG QR (`qrcode` crate, ~220×220, quiet zone on, black on white). It is
  a rendition of the link string; nothing else is encoded and no image needs signing.
- Any camera app scanning it opens `abito://pair` — that is why the manifest declares the `VIEW` filter.
- The phone's own scan screen decodes QR, and if it cannot find a valid link it says so and offers pasting.

---

## 3. Finding the computer

The address in the QR is a snapshot: a laptop changes address between Wi-Fi networks, a hotspot or a router
restart. The phone therefore has two ways in, in this order.

### 3.1 The saved address first

The phone always tries the stored address first, with a **2 s TCP connect timeout**
(`DiscoveryPolicy.SAVED_CONNECT_TIMEOUT_MS`). A wrong address must fail fast instead of stalling the link. Only
after **1 failure in a row** (`FAILURES_BEFORE_DISCOVERY`) does it search the network.

### 3.2 The mDNS announcement

While the link is on, the desktop announces a DNS-SD service:

| Item | Value |
| --- | --- |
| Service type | `_abito._tcp` |
| Instance name | the computer's name (the same `name` as in the link) |
| Port | the listening port (`47821` by default) |
| TXT `fp` | **the first 16 lowercase hex characters** of the certificate's SHA-256 (`Discovery.FP_ID_LEN`) |
| TXT `v` | the protocol version, `"1"` (`Discovery.TXT_VERSION`) |

The phone's rules for what it sees:

- It browses `_abito._tcp` with the framework's `NsdManager` and holds a multicast lock only while browsing;
  services are resolved one at a time and only `Inet4Address` hosts are used.
- **Only the fingerprint id plus the version decide “this is my computer”.** Name and address never count:
  a matching id with any other name is still a candidate, and a name that matches with another id is not.
- A candidate must be a **local IPv4 address** (private `10/8`, `172.16/12`, `192.168/16`, link-local
  `169.254/16`, loopback, or CGNAT `100.64/10`) and a port in `1..65535`; duplicates by address+port are tried
  once, in the order they were announced.
- Each candidate is then **checked with a bare TLS handshake that carries no token** (`PinnedProbe`): connect
  (2 s), handshake (5 s), and accept only the full pinned certificate. On a match the new address is saved and the
  link reconnects there (`Discovery.withAddress` keeps the token and the pinned fingerprint untouched). A
  mismatch is dropped for the rest of the search; an unreachable one may be tried again if it is announced again.
- The announcement is a **hint, never a credential**. Because the probe takes no token, a lookalike that
  announces the same id cannot learn anything by answering.

Timing, so the desktop knows what to expect:

| Rule | Value |
| --- | --- |
| One search lasts | 15 s (`WINDOW_MS`), then the phone shows a calm “not found” hint |
| Waits between searches | 0, 20 s, 60 s, 180 s, then 300 s (`ROUND_DELAYS_MS`) — never faster, never slower than 5 minutes |
| Searches only when | paired, not in demo mode, Wi-Fi (or Ethernet) up, link down (`mayDiscover`) |
| Stops | the moment the link is up (`AddressFinder.connected`) |
| A new network | searches at once and resets the wait (`networkChanged`) |

### 3.3 A manual address

The Home screen also has an “address” sheet: `192.168.1.20`, `192.168.1.20:47821` or `my-pc.local:47821`
(`Discovery.parseAddress`), port defaulting to `47821`. No whitespace, no `/`, no `@`, no `[`; the host must be a
valid IPv4 literal or a DNS-style name of at most 253 characters. This is the fallback when mDNS is blocked by the
network (guest Wi-Fi with client isolation, some corporate APs).

---

## 4. Transport

| Aspect | Rule |
| --- | --- |
| Transport | TLS over TCP, **TLS 1.3 or 1.2 only** (both sides restrict the protocol list). |
| Certificate | Self-signed, ECDSA P-256, no CA, no hostname check; pinned by `fp` (§2.3). The desktop's own certificate is created once and kept in the OS keystore. |
| Socket | `TCP_NODELAY` on; keepalive on. |
| Framing | **Newline-delimited JSON**: one JSON object per line, `\n` terminated, UTF-8, no pretty printing, no blanks needed. A line that is not valid JSON is ignored by the phone (never fatal). |
| Line limit | **64 KiB per message** (`Protocol.MAX_LINE_BYTES = 65536` bytes). The phone closes the connection on a longer line; the desktop answers `{"type":"error","code":"protocol","message":"line too long"}` and closes. A desktop must never build a longer line: all field caps below keep the worst case far under 64 KiB. |
| Timeouts | The desktop requires the first line (`hello`) within **10 s** of the TLS handshake (3 s when the admission pool is nearly full) and allows **20 min of silence** afterwards (`IDLE_TIMEOUT`). The reference desktop (`dev-desktop.mjs`) destroys a socket after **90 s** of silence, so a real desktop should expect a ping well before that. |
| Phone read timeout | 60 s (`LinkClient.readTimeoutMs`). Nothing inbound for that long ends the connection as `SILENT` and reconnects. |
| Keep-alive | The phone sends `{"type":"ping"}` every **20 s** and expects `pong`. Any inbound line proves the connection works, so the desktop may answer pong and send nothing else. |
| Liveness check | When the screen turns on, a network appears or the 4-minute alarm fires, the phone sends a ping and gives the desktop **8 s** (`pongWaitMs`) to answer *anything*; silence closes the connection so the loop reconnects instead of trusting a half-dead socket. |
| Half-open sockets | A phone that slept or changed network keeps a socket that looks open and delivers nothing. That is why the phone pings and does its own liveness check; the desktop's `IDLE_TIMEOUT` closes the other side. |
| Reconnect | Exponential backoff **1, 2, 4, 8, 16, 30 s** (`backoffMs`), capped at 30 s, reset as soon as a connection has worked. After a drop the first retry is 1 s. `retryNow()` cuts the wait short when the network changes or a new address is found. |
| Fatal outcomes | A wrong token is final: the phone shows the error and **stops retrying** with that pairing (nothing will ever change by retrying). It is told as `{"type":"error","code":"auth"}` — also for a wrong `hello` version, which the engine treats the same way. A wrong `welcome.v` is final too, but the phone raises the `version` error itself instead of waiting to be told: an `error` frame is final only with `code:"auth"`. Any other error code is shown and the link keeps retrying. |
| Why a link ended | As far as the phone can tell: `SILENT` (read timeout or a liveness check went unanswered), `CLOSED` (desktop closed it), `NETWORK` (reset / unreachable / Wi-Fi changed). The category is what Home's “connection lost” line talks about. |
| `bye` | On `stop()` the phone writes `{"type":"bye"}` best effort and closes. The desktop closes the connection on `bye`; it must not treat it as an error and must not keep the slot. |

The desktop must treat every connection as disposable: **no state that matters exists only in a connection.** A
reconnect is the normal case after sleep, Wi-Fi changes and desktop restarts.

---

## 5. The conversation, in order

The phone opens a connection like this (`LinkClient.loop` + `read`):

1. **TCP connect** to the saved address (2 s) — or to a newly found one, then **TLS handshake** (10 s) with the
   pinned certificate. Only TLS 1.2/1.3 are offered; no client certificate.
2. **The phone writes `hello`** as the first line: `v`, `token`, `device`, and `caps` when it has anything optional
   to ask for.
3. The desktop validates it and answers **`welcome`**: `v`, `desktop`, `os`, and `caps` for the features the user
   switched on. The phone compares `v` with `1` (a mismatch is a final `version` error) and stores the offered caps
   *before* telling its UI, so anything reacting to the welcome can already use them.
4. The desktop sends **`sessions`** immediately after `welcome` — the first line after the welcome is the whole
   current session list. The phone shows the island from it.
5. If both sides offered `chat`, the phone asks for the model list at once: `{"type":"chatModels"}`, answered by
   `chatModels`, and right after it for the conversation list: `{"type":"chatConversations"}`. That is why the Chat
   screen already has its list when it opens. A desktop that has no conversations never answers the second one, and
   nothing else changes for it.
6. From here the conversation is event-driven in both directions, until the link ends:
   - the desktop pushes `sessions` whenever something changed, `approval` / `approvalResolved` / `question` as
     agents need the user, `usage` / `services` when the user's switches produce new content;
   - the phone writes `decision`, `answer`, `getDiff`, `chatSend` / `chatCancel` / `chatReset` / `chatModels`
     whenever the user acts, and `ping` every 20 s;
   - the desktop answers `pong`, and `error` for anything it refuses.

Ordering rules the desktop must keep:

- **`welcome` before anything else.** `sessions`, `usage`, `services`, `approval` and `question` are only valid
  after a `welcome`: the phone ignores what it was not offered, and an unexpected line before the welcome is
  simply not what the protocol promises.
- **`sessions` first.** A phone that just reconnected has no rows; sending sessions right after the welcome is what
  fills the island.
- **`approvalResolved` answers `decision` and `answer`.** That is the only confirmation the phone accepts (§8.3,
  §9.3). A desktop that decides by itself (the agent's command timed out, a terminal said yes) sends
  `approvalResolved` alone.
- **Only what changed.** `sessions` is a whole list, not a patch; the desktop's hub already avoids sending a
  list whose content did not change and keeps `updatedAt` stable in that case. Keep doing that: every `sessions`
  line makes the phone redraw.
- **Re-offer after a reconnect.** A link drop clears the phone's pending approvals (`ApprovalBook.clear`), so
  whatever is still pending must be sent again after the next `welcome` — with the **same fingerprint**, so the
  phone can match the user's answer to its own bookkeeping.
- **Nothing waits on the phone.** The phone's writer runs on its own thread: a desktop that stalls blocks nothing
  in the UI, but it must still not stop answering `ping`, or the phone gives the link up as `SILENT`.

---

## 6. Messages

Field names and types are exact. `required` means the phone refuses the whole message if it is missing or
ill-typed (or, for `org.json` defaults, that the documented default applies). JSON object key order is irrelevant.

Conventions used below: **string** = JSON string; **int**/ **long** = JSON number, integral; **bool** = JSON
`true`/`false`; **array** = JSON array. "Cut" means the phone trims and truncates; a desktop should cut first, so
the field is honest rather than silently shortened.

### 6.0 Capabilities

The optional features, by the constant the phone defines and the exact string that goes on the wire. A capability
is only in force when **both** sides name it: the phone asks in `hello`, the desktop answers in `welcome`, and a
feature the desktop does not offer is simply absent from the phone's screens.

| Constant (`Protocol.kt`) | Wire value | What it enables |
| --- | --- | --- |
| `CAP_CHAT` | `chat` | The Chat screen: `chatModels`, `chatSend`, `chatCancel`, `chatReset` from the phone; `chatModels`, `chatDelta`, `chatDone`, `chatError` from the desktop. An engine with several conversations also exchanges `chatConversations`, `chatNew`, `chatOpen` and `chatDelete` (phone) and `chatConversations` and `chatDeleted` (desktop); a desktop that knows none of those drops them and the chat is the single conversation of 0.1. |
| `CAP_DETAILS` | `details` | `steps`, `finalLine`, `project` and `color` on each session, and the session details screen on the phone. |
| `CAP_ANSWERS` | `answers` | Answering Claude Code's questions from the phone: `question` from the desktop, `answer` from the phone. |
| `CAP_DIFFS` | `diffs` | `files` on each session and `getDiff` → `diff` for the lines of one file. |
| `CAP_USAGE` | `usage` | The Claude and Codex plan pills: `usage` from the desktop. |
| `CAP_SERVICES` | `services` | The read-only service cards: `services` from the desktop. |

The phone asks for all six (`Protocol.CAPABILITIES`, in that order) and knows nothing else. A desktop names in
`welcome` only the ones the user switched on; a string it does not know is ignored by the phone, so a future
capability can appear without breaking an older app. Sending the data of a capability that was not offered is
allowed but ignored.

---

### Phone → desktop: `hello`

The first line of every connection. Sent once, before anything else.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"hello"` |
| `v` | int | yes | `1`. The desktop compares it and refuses the rest with `auth` if it differs. |
| `token` | string | yes | The pairing token from the QR. Compared **in constant time** on the desktop (`constant_eq`). |
| `device` | string | yes | What the phone calls itself: `Build.MODEL`, or `"Android"` when the model is unknown (e.g. `"Pixel 7"`). The desktop may log it; it must not display it as a name of the phone the user chose. |
| `caps` | array of strings | no | The optional features the app understands, `["chat","details","answers","diffs","usage","services"]` in that order. **Omitted entirely when empty**, so an app without them sends exactly the v1 hello. A desktop must read it as “the phone can use these”; it must not require any of them. |

```json
{"type":"hello","v":1,"token":"aZ3kQ9mN2pXrT7vL","device":"Pixel 7","caps":["chat","details","answers","diffs","usage","services"]}
```

The desktop's checks (`server.rs`), all mandatory:

1. First line on the connection and within 10 s, otherwise close and read nothing more.
2. `type == "hello"`, else close silently.
3. `v == 1` **and** the token equals the stored one byte for byte (constant time). On failure wait **400 ms**
   (`AUTH_PENALTY`), then send `{"type":"error","code":"auth","message":"bad token or version"}` and close. The
   pause is what makes guessing slow.
4. Capabilities asked for are remembered for this connection only; they are read again on every reconnect.

---

### Phone → desktop: `decision`

Allow or deny an approval that is waiting. Only `allow` and `deny` exist from the phone — there is **no “always
allow”**, no per-tool rule, no remembered decision: the user decides each request, once.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"decision"` |
| `fingerprint` | string | yes | The 64 lowercase hex fingerprint of the request that was sent, verbatim. |
| `decision` | string | yes | `"allow"` or `"deny"`, nothing else. |

```json
{"type":"decision","fingerprint":"6f1c0e13a9b6d0c58b7c1a94e2d3f4a5b6c7d8e9f0a1b2c3d4e5f60718293a4b","decision":"allow"}
```

The phone only sends a `decision` for a request it still holds and that is younger than 120 s, and only once per
request (§8). The desktop must:

- look the fingerprint up among its **pending** approvals;
- ignore a decision for an unknown, already resolved or expired request (the reference desktop logs
  `DECISION ignored` and sends nothing back — silence is correct here);
- apply the decision to the agent, then **always** confirm with `approvalResolved`.

---

### Phone → desktop: `answer`

The user's answers to an AskUserQuestion. Only with the `answers` capability in both directions.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"answer"` |
| `fingerprint` | string | yes | The 64 lowercase hex fingerprint of the question that was sent. |
| `picks` | array of arrays of strings | yes | **One list per question, in the order the questions were asked.** Each list holds the exact option `label`s the desktop offered. A single choice has exactly one label; a multiple choice has one or more. |

```json
{"type":"answer","fingerprint":"…64 hex…","picks":[["develop"],["Lint","Build"]]}
```

```json
{"type":"answer","fingerprint":"…64 hex…","picks":[["main"],["Tests"]]}
```

The phone builds `picks` from taps only: nothing the user types can become a label, the order inside a list is
the order the options were listed (not the order of the taps), and the message is only sent when every question
has an answer (§9). The desktop validates it exactly as §9.2 describes and answers with `approvalResolved`.

---

### Phone → desktop: `getDiff`

Ask for the lines of one file of one session, when the session's `files` list named it. Only with the `diffs`
capability in both directions.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"getDiff"` |
| `pillId` | string | yes | The session the file belongs to, as the phone received it. |
| `fileId` | long | yes | The `id` of that entry in `files`. |

```json
{"type":"getDiff","pillId":"integration_claude","fileId":2}
```

The answer is one or more `diff` messages (§“Desktop → phone: `diff`”). The phone never asks for a file it was
not offered, so the desktop may treat an unknown pair as `gone`.

---

### Phone → desktop: `chatModels`, `chatSend`, `chatCancel`, `chatReset`

Only with the `chat` capability in both directions. The phone sends `chatModels` once when the welcome offered
`chat`, and again whenever the Chat screen is reopened.

`chatModels` has a single field:

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"chatModels"` |

```json
{"type":"chatModels"}
```

`chatSend` starts an answer:

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"chatSend"` |
| `id` | string | yes | The message id the phone made: **1 to 64 characters of `[A-Za-z0-9_-]`**. The desktop echoes it on every `chatDelta`, `chatDone` and `chatError`. |
| `model` | string | yes | One `id` from the `chatModels` list, verbatim. |
| `text` | string | yes | The user's message: at most **4000 characters** (`Protocol.CHAT_MAX_TEXT`). The phone refuses to send longer, so a desktop that receives longer can refuse it as `too_long`. Newlines inside are fine (the JSON line escapes them). |
| `conversation` | string | no | The conversation to write in: an `id` from the last `chatConversations`. **Absent means the engine's open one**, which is the whole of the 0.1 frame — a phone that was never offered a conversation list never sends it. |

```json
{"type":"chatSend","id":"c1","model":"openai/gpt-4o","text":"hola Kenai"}
```

```json
{"type":"chatSend","id":"c7","model":"openai/gpt-4o","text":"hola","conversation":"c1"}
```

`chatCancel` stops an answer that is still being written:

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"chatCancel"` |
| `id` | string | yes | The id of the answer to stop. |

```json
{"type":"chatCancel","id":"c1"}
```

`chatReset` starts a new conversation: the desktop forgets its side of the conversation and stops any running
answer (which it reports as `chatError` with `reason:"canceled"`). An engine with several conversations answers it the
way DESIGN.md §4.2 defines: it creates a conversation, opens it, and says so with a fresh `chatConversations`.

```json
{"type":"chatReset"}
```

---

### Phone → desktop: `chatConversations`, `chatNew`, `chatOpen`, `chatDelete`

Only with the `chat` capability, and only against an engine that holds several conversations (DESIGN.md §3.1, §4.2).
**Nothing is negotiated:** a desktop that knows none of these frames drops them and never answers, and the phone then
keeps the single conversation of 0.1, sending no `conversation` on `chatSend`.

`chatConversations` asks for the list, answered by the desktop's `chatConversations` frame. The phone asks it once
after every `welcome` that offered `chat` (right after `chatModels`) and again when the Chat screen is opened:

```json
{"type":"chatConversations"}
```

`chatNew` creates a conversation and the engine opens it:

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"chatNew"` |
| `title` | string | no | What the row should say, at most 120 characters. Absent lets the engine name the conversation after the first message. |

```json
{"type":"chatNew","title":"Presupuesto"}
```

`chatOpen` opens one the list named. The engine answers with a fresh list; while an answer is still being written
there it refuses with `busy`, and the phone does not even ask while its own answer is streaming: it waits for the turn
to end.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"chatOpen"` |
| `id` | string | yes | An `id` from the last list. |

```json
{"type":"chatOpen","id":"c1"}
```

`chatDelete` **deletes a conversation for ever**: the engine removes the index entry and every file of its history
(DESIGN.md Q3). There is no undo, no trash and no tombstone, which is why **the phone asks the user first and says the
deletion is irreversible** ("irreversible" in the phone's own words). Sending it is a decision the user took, never a
side effect of opening a row.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"chatDelete"` |
| `id` | string | yes | An `id` from the last list. A `chatDelete` for an id this device does not see is `bad_request`. |

```json
{"type":"chatDelete","id":"c1"}
```

The engine answers a delete with a fresh `chatConversations` — never with anything that reads like a recoverable
delete — and with the `chatDeleted` below. The one exception the phone makes on its own: while an answer is still
streaming in the conversation that is open, the phone does not send this frame at all (the engine would answer `busy`,
and the answer on screen would be orphaned).

The phone only sends `chatNew`, `chatOpen` and `chatDelete` after a desktop has answered a list at least once on that
connection; asking for the list is what finds out. None of the four frames exists in a desktop of 0.1, so it sees the
phone of 0.1.

---

### Phone → desktop: `ping` and `bye`

| Message | Body | Meaning |
| --- | --- | --- |
| `ping` | `{"type":"ping"}` | Keep-alive and liveness check, every 20 s and on every wake-up while connected. **The desktop must answer `pong`.** |
| `bye` | `{"type":"bye"}` | Best effort, written when the user switches the link off or the app goes away. **The desktop closes the connection** and frees the slot; an unknown type must never be an error. |

---

### Desktop → phone: `welcome`

The desktop's first line, in answer to a valid `hello`.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"welcome"` |
| `v` | int | yes | `1`. The phone treats any other value as a final `version` error and stops. |
| `desktop` | string | no | The computer's name, shown to the user (“Connected · Alvaro PC”). Missing means empty. |
| `os` | string | no | A short OS name (`rustls`: `std::env::consts::OS`, e.g. `"linux"`, `"windows"`, `"macos"`). Informational; the phone may show it in Diagnostics. |
| `caps` | array of strings | no | The optional features the user has switched on **right now**, a subset of `["chat","details","answers","diffs","usage","services"]`. **Omitted entirely when empty.** |

```json
{"type":"welcome","v":1,"desktop":"Alvaro PC","os":"linux","caps":["details","diffs","answers","usage","services"]}
```

```json
{"type":"welcome","v":1,"desktop":"Alvaro PC","os":"linux"}
```

Rules:

- A capability may be listed only if the desktop will actually serve it: the phone will use it. The switch that
  turns a feature off therefore changes the next `welcome`, and the phone re-reads the caps on each connection.
- Unknown strings in `caps` are ignored by the phone (`strings()` keeps only non-empty strings); a desktop may
  name a future capability without breaking anything, but then it must serve it.
- The phone ignores a `question` unless `answers` was offered, a `diff` unless `diffs` was, `services` unless
  `services` was, `usage` unless `usage` was, and chat messages are only exchanged after `chat` was offered in
  both directions. Sending what was not offered is safe but useless.

---

### Desktop → phone: `sessions`

The whole session list the island shows, sent after the welcome and whenever its content changed.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"sessions"` |
| `sessions` | array of objects | yes | At most **16** entries (the desktop's hub sends the first 16 and drops a session without a `pillId`). An empty array clears the phone's list. |

Each entry (see §7 for the meaning of each field):

| Field | Type | Required | Default | Rules |
| --- | --- | --- | --- | --- |
| `pillId` | string | yes | — | The agent's pill id, the key of the session: `integration_claude` (Claude Code), `agent_codex` (Codex), `agent_gemini` (Gemini CLI), `agent_opencode` (OpenCode). One entry per agent pill. |
| `agent` | string | no | `""` | The display name (“Claude Code”). Cut to 64 characters by the desktop's hub. |
| `state` | string | no | `idle` | One of the eleven names in §7.2. An unknown value is drawn as idle. |
| `statusText` | string | no | `""` | One line under the agent name, control characters stripped, at most 200 characters. |
| `stepIndex` | int | no | `0` | Progress numerator (“step 3 of 6”). |
| `stepCount` | int | no | `0` | Progress denominator; `0` means “no progress to show”. |
| `updatedAt` | long | no | `0` | Epoch milliseconds of the last content change. It must **not** move when nothing changed: the phone treats a list with the same content as the same list and shows “updated 2 min ago” from it. |
| `steps` | array of strings | no | `[]` | **Only with the `details` capability.** The recent steps, oldest first, at most **20** entries of at most **200** characters each (e.g. `"Edit · src/app.ts"`). Non-string entries are skipped. |
| `finalLine` | string | no | absent | **Only with `details`.** The last line of a finished agent, at most 200 characters; blank means absent. |
| `project` | string | no | absent | **Only with `details`.** The **folder name** the agent runs in — never a path (§15). At most 64 characters; the phone cuts anything after the last `/` or `\`, and refuses `""`, `"/"`, `"."` and `".."`. |
| `color` | string | no | absent | **Only with `details`.** The agent's colour as `#RRGGBB` (exactly 7 characters, hex). Anything else is dropped by the phone. |
| `files` | array of objects | no | `[]` | **Only with the `diffs` capability.** The files the agent changed, newest last (§“file entries”). |

File entry (inside `sessions[].files`):

| Field | Type | Required | Default | Rules |
| --- | --- | --- | --- | --- |
| `id` | long | yes | — | The id the phone sends back in `getDiff`. Must be `>= 0`; an entry without it is skipped. |
| `name` | string | yes | — | **A file name, never a path.** The phone keeps only the last segment and cuts it to 80 characters; `""`, `"."`, `".."` are dropped. |
| `added` | int | no | `0` | Added lines, `>= 0` (negative counts become 0). |
| `removed` | int | no | `0` | Removed lines, `>= 0`. |
| `tooLarge` | bool | no | `false` | The desktop will not show its lines; the phone shows a “too large” state and sends no `getDiff`. |
| `isNew` | bool | no | `false` | A new file the agent created. |

The phone keeps at most the **last 20** file entries of a session.

```json
{"type":"sessions","sessions":[
  {"pillId":"integration_claude","agent":"Claude Code","state":"working","statusText":"Editing files","stepIndex":2,"stepCount":6,"updatedAt":1710000000000,
   "steps":["Read · README.md","Grep · TODO","Edit · src/app.ts"],"project":"abito","color":"#2DD4BF",
   "files":[{"id":1,"name":"app.ts","added":2,"removed":1},{"id":3,"name":"generated.json","added":5400,"removed":0,"tooLarge":true}]},
  {"pillId":"agent_codex","agent":"Codex","state":"idle","statusText":"Idle","stepIndex":0,"stepCount":0,"updatedAt":1710000000000}
]}
```

Without the corresponding capability the desktop **must omit** those fields for every connection whose hello did
not ask for them, and the phone accepts plain v1 sessions from a desktop that never offers them.

---

### Desktop → phone: `approval`

An approval request: the agent is about to run a command and the user must allow or deny it.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"approval"` |
| `pillId` | string | yes | The agent's pill id, as in `sessions`, so the phone can name it. |
| `fingerprint` | string | yes | **64 lowercase hex characters** (§8.1). The phone refuses the whole message if it is not. Uppercase is refused too: send it lowercase. |
| `tool` | string | yes | The tool the agent wants to use, as the agent names it (`"Bash"`, `"Write"`, `"Edit"`, `"WebFetch"…`). Shown as the eyebrow of the sheet. |
| `command` | string | yes | What will run: the exact command line for Bash, or a one-line summary of the action. **Jesscore sends the producer's own text verbatim, newlines kept, up to that producer's bound (500 characters, 2000 for structured requests)**; an older desktop may cut it at 500 and flag it with `truncated`. |
| `createdAt` | long | yes | Epoch milliseconds when the request was made. The phone shows it in Diagnostics; it never uses it to expire the request (§8.2). |
| `truncated` | bool | no | `true` when the sender cut `command` (an older desktop at 500 characters), so the phone can say “the command was shortened”. Absent means `false`; jesscore never cuts and never sets it. |

```json
{"type":"approval","pillId":"integration_claude","fingerprint":"6f1c0e13a9b6d0c58b7c1a94e2d3f4a5b6c7d8e9f0a1b2c3d4e5f60718293a4b","tool":"Bash","command":"npm run build","createdAt":1710000000000,"truncated":false}
```

```json
{"type":"approval","pillId":"agent_codex","fingerprint":"…64 hex…","tool":"Bash","command":"for f in $(git ls-files); do …","createdAt":1710000000000,"truncated":true}
```

The desktop must also put the agent's session into the `approval` **state** in `sessions`, so the island and Home
agree with the sheet. Sending the same approval again on the same connection is safe (the phone keeps the first
arrival time); after a reconnect, pending approvals must be re-offered (§5).

---

### Desktop → phone: `approvalResolved`

The request is gone: decided here, decided on the computer, timed out, or the question was answered.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"approvalResolved"` |
| `fingerprint` | string | yes | The fingerprint of the request or question this resolves. The phone removes it, closes the sheet and lands on the result. |

```json
{"type":"approvalResolved","fingerprint":"6f1c0e13a9b6d0c58b7c1a94e2d3f4a5b6c7d8e9f0a1b2c3d4e5f60718293a4b"}
```

`approvalResolved` is also the confirmation the phone waits for after a `decision` or an `answer` (§8.3, §9.3).
A fingerprint the phone does not hold is harmless (it is ignored).

---

### Desktop → phone: `question`

A question Claude Code asked through AskUserQuestion, to be answered from the phone. Only with `answers`.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"question"` |
| `pillId` | string | yes | The agent's pill id. |
| `fingerprint` | string | yes | 64 lowercase hex characters, computed like an approval (§8.1) with `tool = "AskUserQuestion"`. |
| `createdAt` | long | yes | Epoch milliseconds the question was asked (the phone expires it at 120 s, §9.4). |
| `questions` | array of objects | yes | **1 to 4** questions. Each: `question` (string, non-blank, the exact wording), `multiSelect` (bool, absent = `false`), `options` (array of **1 to 8** objects with `label` (non-blank, **all different within one question**) and `description` (string, may be empty)). |

```json
{"type":"question","pillId":"integration_claude","fingerprint":"…64 hex…","createdAt":1710000000000,
 "questions":[
  {"question":"Which branch should I use?","multiSelect":false,"options":[{"label":"main","description":"The default branch"},{"label":"develop","description":""}]},
  {"question":"Which checks should I run?","multiSelect":true,"options":[{"label":"Lint","description":""},{"label":"Tests","description":"Unit tests"},{"label":"Build","description":""}]}
 ]}
```

A question that breaks any of those limits is **dropped whole** by the phone (it never guesses what was meant),
which is why the desktop must keep them. The agent's session should also reach the `question` state.

---

### Desktop → phone: `diff`

One part of a file's lines, in answer to `getDiff`. Several parts make one file.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"diff"` |
| `pillId` | string | yes | Echo of the request. |
| `fileId` | long | yes | Echo of the request. |
| `name` | string | no | The file name (last segment only, cut to 80 characters). |
| `added` | int | no | Added lines, `>= 0`. |
| `removed` | int | no | Removed lines, `>= 0`. |
| `tooLarge` | bool | no | The desktop refuses to show this file's lines (the phone shows the reason instead of the text). |
| `gone` | bool | no | The desktop no longer has the file (the branch moved, the agent ended, the user asked for a file it never had). |
| `truncated` | bool | no | There were more lines than the desktop sends (§10): what arrived is not all of it. |
| `part` | int | yes | Zero-based index of this part: `0 <= part < parts`. |
| `parts` | int | yes | How many parts this file has: **1 to 4**. |
| `lines` | array of `[string, string]` pairs | yes | At most **100** pairs per part (`PART_LINES`). Each pair is `[kind, text]`: `kind` is `"+"`, `"-"`, `" "` or `"@"`, and anything else is read as a context line `" "`; a kind longer than one character is also a context line. `text` is cut to **400** characters. |

```json
{"type":"diff","pillId":"integration_claude","fileId":1,"name":"app.ts","added":2,"removed":1,"tooLarge":false,"gone":false,"truncated":false,"part":0,"parts":1,
 "lines":[["@","@@ 10"],[" ","function greet(name) {"],["-","  return 'Hello ' + name;"],["+","  return `Hello, ${name}!`;"],[" ","}"]]}
```

A part that breaks the limits is dropped by the phone (more than 100 lines, `parts` outside `1..4`, `part` outside
`0 until parts`, a malformed pair) — so a desktop that miscalculates shows nothing at all. `gone` is sent as a
single part: `{"…","gone":true,"part":0,"parts":1,"lines":[]}`.

---

### Desktop → phone: `usage`

How much of the Claude and Codex plans is used. Only with `usage`. Sent when the numbers change and, periodically,
as the percentages move.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"usage"` |
| `claude` | object | no | The Claude plan, or absent/`null` when the desktop does not know it. |
| `codex` | object | no | The Codex plan, same rules. **Both absent clears the phone's usage display.** |

A plan object:

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `fiveHour` | window | no | The 5-hour window. |
| `sevenDay` | window | no | The 7-day window. |
| `resetCredits` | int | no | Codex's free resets, `0..99`; anything else is dropped. |
| `plan` | string | no | A short plan name (`"max"`, `"plus"`); letters, digits, space and `-` only, cut to 20 characters, blank dropped. |
| `updatedAt` | long | no | Epoch milliseconds of these numbers (must be `>= 0`). |

A window object — **both fields are required inside it**, or the window is dropped (not the plan):

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `pct` | int | yes | Whole percent used, `0..100`. A plan without a single usable window is dropped whole. |
| `resetsAt` | long | yes | Epoch milliseconds when it resets, `> 0`. |

```json
{"type":"usage","claude":{"fiveHour":{"pct":63,"resetsAt":1710005700000},"sevenDay":{"pct":21,"resetsAt":1710259200000},"plan":"max","updatedAt":1710000000000},
 "codex":{"sevenDay":{"pct":31,"resetsAt":1710172800000},"resetCredits":2,"plan":"plus","updatedAt":1710000000000}}
```

---

### Desktop → phone: `services`

Read-only cards of the services the user ticked on the computer (Stripe, GitHub…). Only with `services`. An empty
array takes the cards away.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"services"` |
| `services` | array of objects | yes | At most **7** cards (`MAX_SERVICES`), each a known id, once. |

A card:

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `id` | string | yes | One of `integration_stripe`, `integration_github`, `integration_vercel`, `integration_n8n`, `integration_resend`, `integration_notion`, `integration_calcom` — the ids of the desktop's own catalogue. **Any other id is skipped by the phone**, and so is a repeated one (the first wins). |
| `title` | string | no | The service's name. |
| `headline` | string | no | The one number or state the pill shows (`"12.50 EUR"`, `"2"`). |
| `reason` | string | no | A short why (`"Payments"`, `"Recent"`); blank means absent. |
| `items` | array of objects | no | At most **3** lines: `label` (string, non-blank) and `detail` (string, may be empty). |

All texts are one line: **at most 80 characters** (`MAX_SERVICE_TEXT`), with control characters stripped by the
phone and the text trimmed. The desktop should send them that way already, so nothing is silently shortened.

```json
{"type":"services","services":[
 {"id":"integration_stripe","title":"Stripe","headline":"12.50 EUR","reason":"Payments","items":[{"label":"Payment","detail":"+9.00 · 2m"}]},
 {"id":"integration_notion","title":"Notion","headline":"2","reason":"Recent","items":[{"label":"Roadmap","detail":"5m"},{"label":"Notes","detail":"1h"}]}
]}
```

---

### Desktop → phone: `pong`

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"pong"` |

```json
{"type":"pong"}
```

The answer to `ping`, within 8 s. Nothing else is needed: the phone also counts any other inbound line as proof
that the connection works, so it needs no payload and no timestamp.

---

### Desktop → phone: `chatModels`, `chatDelta`, `chatDone`, `chatError`

Only with `chat`.

`chatModels` — what the phone may ask for. Answer to `chatModels`:

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"chatModels"` |
| `models` | array of objects | yes | `id` (`"provider/model"`, required: non-blank, at most 300 characters, with a `/` not at position 0 — an entry without one is dropped), `provider` (string, defaults to the part before `/`), `label` (string, defaults to `id`). An empty array is valid: chat is offered but there is nothing to write with. |

```json
{"type":"chatModels","models":[{"id":"openai/gpt-4o","provider":"openai","label":"OpenAI · gpt-4o"},{"id":"ollama/llama3"}]}
```

`chatDelta` — a piece of the answer being written, appended to what arrived before:

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"chatDelta"` |
| `id` | string | yes | The `id` of the `chatSend` being answered (1..64 of `[A-Za-z0-9_-]`; else the whole message is dropped). |
| `text` | string | yes | The next piece (the desktop cuts pieces at ~8 KiB so one line stays far under 64 KiB). |

```json
{"type":"chatDelta","id":"c1","text":"Fake answer "}
```

`chatDone` — the answer is complete:

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"chatDone"` |
| `id` | string | yes | The same id. |
| `text` | string | no | When present, **the full answer** and it replaces everything streamed (a provider that rewrote its output). Absent means “what streamed is the answer”. |

```json
{"type":"chatDone","id":"c1"}
```

```json
{"type":"chatDone","id":"c1","text":"Answer"}
```

`chatError` — the answer was refused or stopped:

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"chatError"` |
| `id` | string | yes | The same id. |
| `reason` | string | no | A **fixed code**, one of the list below. Missing is read as `"internal"`; an unknown code is kept as-is by the phone and shown as a generic failure. |
| `message` | string | no | A fixed sentence **written by the desktop** (it is shown to the user as the desktop's own words, not translated by the phone). Missing is empty. |

| `reason` | Meaning (the sentence the reference desktop sends) |
| --- | --- |
| `off` | Chat from the phone is turned off on the computer. |
| `not_allowed` | That model is not allowed for the phone. |
| `busy` | Another answer is still being written. |
| `rate` | Too many messages. Try again in a few minutes. |
| `too_long` | The message is too long. |
| `empty` | Nothing to send. |
| `auth` | The provider refused the computer's key. |
| `provider` | The provider returned an error. |
| `canceled` | Canceled. |
| `internal` | (the phone's default when the desktop sends no reason) |

```json
{"type":"chatError","id":"c1","reason":"busy","message":"Another answer is still being written."}
```

### Desktop → phone: `chatConversations`, `chatDeleted`

`chatConversations` is the whole list, never a patch, and it is also what tells the phone the engine holds
conversations: an engine that never sends it keeps the single chat of 0.1. Sent after the `welcome` and again whenever
a conversation is created, opened, renamed or deleted — by this phone or by another device.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"chatConversations"` |
| `items` | array of objects | yes | At most **200** entries; a longer list is cut by the phone. An empty array is valid: the engine holds none yet, and the next message creates the first one. |
| `open` | string | no | The id of the open conversation, when the engine names it once for the whole list instead of on the entry (its own local frame does). Both shapes are read. |

Each entry:

| Field | Type | Required | Default | Rules |
| --- | --- | --- | --- | --- |
| `id` | string | yes | — | 1 to 64 characters of `[A-Za-z0-9_-]`, the same alphabet as a chat message id, **never a path**. An entry without a usable id, or a repeated one, is dropped by the phone. |
| `title` | string | no | `""` | What the row shows; blank shows the phone's generic name. The phone drops control characters and cuts it to 120 characters. |
| `updatedAt` | long | no | `0` | Epoch milliseconds of the last change, `>= 0`. |
| `open` | bool | no | `false` | `true` on the conversation the engine has open. One engine child means **one open conversation**, so at most one entry carries it. |

```json
{"type":"chatConversations","items":[{"id":"c0","title":"Chat","updatedAt":1700000000000,"open":true},{"id":"c1","title":"Presupuesto","updatedAt":1700000100000,"open":false}]}
```

`chatDeleted` says one conversation is gone from the engine: this phone asked, or another device did, or the user
deleted it on the host. **The phone drops everything it held for that id** (DESIGN.md Q11); otherwise "delete the whole
conversation" would only be true on the host.

```json
{"type":"chatDeleted","id":"c1"}
```

What the phone does with these, and what this slice does not promise:

- The list is the whole truth about what exists: an id the phone once saw and does not see any more is not opened, and
  the phone never invents one. Its list is replaced whole every time, and what the phone held for a conversation the
  list no longer names is dropped with it — it cannot be opened again, so its messages never come back.
- The phone swaps the messages on screen when the open conversation changes on the engine (another device opened one).
  **It never swaps while an answer is streaming here**: such a list waits, and the latest one is applied when the turn
  ends, so a `chatDelta` of the running answer is never loaded over another conversation's messages.
- Deleting the conversation that is open while its answer is still streaming is refused by the phone before the frame
  is written (and by the engine as `busy`): the phone never orphans an answer it is showing.
- The first list is the exception that proves the rule: the phone was already writing in the engine's open conversation
  before it knew its id, so the messages on screen become that conversation's and the screen does not move.
- The phone keeps the messages of each conversation it opened in app-private storage (KN-01 AC-2), bounded: the 200
  conversations of the list, the 10 opened last, the newest 50 messages of each, the first 4000 characters of a message.
  It holds ids, titles, roles, text and a reason code and nothing else, so it can hold no token, address or key (AC-4).
- That copy is **what the phone reads when the engine does not answer**: with the link down the Chat says «Tu motor no
  responde» (English: "Your engine is not responding"), still opens the list and every conversation it kept, and cannot
  write — sending, creating and deleting are refused here before a frame is built, and the sheet's buttons say so.
- The copy is written when it changes structurally (a list, an open, a deletion, a Clear) and when an answer is final,
  never once per streamed piece. A `chatDeleted` and a fresh list still purge it: what the list no longer names keeps
  no copy, so it cannot come back from the dead, and forgetting the computer deletes the file.
- **A reconnect needs nothing from the desktop beyond the `welcome` and the list.** The device token is a file on the
  engine and the pairing stays on the phone, so the engine coming back is the same connection as before: `welcome`,
  `sessions`, `chatModels`, `chatConversations`, and the chat is usable again with no re-pairing (AC-3).
- A conversation is named on `chatSend` only after **that** connection listed its conversations: the phone's copy may be
  from an older run, and the engine would refuse the whole question for a conversation it no longer has. Until the list
  arrives the frame goes without `conversation`, which is exactly the 0.1 frame.
- There is **no history frame yet**: an engine that pages a conversation's history over the front does so with a frame
  this app does not know, and the phone drops it (unknown types are dropped, as always). DESIGN.md's engine unit T5-U4
  defines that frame; this document will name it when the phone reads it.
- A `chatDeleted` for an id the phone does not hold is harmless: nothing is shown and nothing is dropped.

Limits the reference desktop enforces (copy them; the phone relies on them being the desktop's job):

- one answer at a time per phone (`busy` for a second `chatSend` while one runs);
- **12 sends per 10 minutes** (`rate`);
- `text` of at most 4000 characters (`too_long`), non-blank after trim (`empty`);
- a `model` that was offered (`not_allowed`);
- `chatCancel` on the running id produces `chatError` with `canceled`; `chatReset` stops a running answer the
  same way and forgets the conversation.

---

### Desktop → phone: `error`

Something the desktop refuses outside a request.

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `type` | string | yes | `"error"` |
| `code` | string | no | A short code. `"auth"` is **final**: the phone stops retrying and says the pairing is wrong. `"version"` is the phone's own code for a `welcome.v` it cannot speak (it compares the value itself), so a desktop never needs to send it; every other code is shown and the link keeps retrying. Missing is empty. |
| `message` | string | no | A short sentence for the user (shown next to the code). |

```json
{"type":"error","code":"auth","message":"bad token or version"}
```

```json
{"type":"error","code":"protocol","message":"line too long"}
```

Codes the desktop sends today (`server.rs`, reproduced by the reference desktop):

| `code` | When | Then |
| --- | --- | --- |
| `auth` | wrong token or wrong `v` in `hello`, after a 400 ms pause | the line is written and the connection closed; the phone stops retrying |
| `protocol` | a line longer than 64 KiB, or no newline within the limit | the line is written and the connection closed; the phone reconnects |

The phone also reports its own failures through this code space as `send` (its `onError("send", …)` when a write
fails), which is why the desktop should not invent codes that collide with nothing: any new code must be
descriptive and short.

---

## 7. Sessions

### 7.1 What the phone draws

One row of the island = one agent pill = one `sessions` entry. `pillId` is the identity across messages:
`approval`, `approvalResolved`, `question` and `getDiff` all refer to it.

- `stepIndex`/`stepCount` drive the progress ring; `stepCount == 0` means no ring.
- `statusText` is the one line under the agent name. With the `details` capability Home also shows `steps`,
  `finalLine`, `project` and `color` on the session details screen.
- `updatedAt` is “updated 2 min ago”, so it must only move when the content did.
- `files` appear on the session details screen as a list with `+added −removed` per file; a `tooLarge` file
  shows a “too large” chip and is not asked for.

### 7.2 The eleven states

The phone's `BotState` keys, exactly:

| `state` | Meaning on the phone |
| --- | --- |
| `idle` | Nothing running. |
| `working` | Working on something. |
| `thinking` | Reasoning / deciding. |
| `searching` | Searching the code or the web. |
| `approval` | Waiting for the user to allow or deny a command. |
| `question` | Asking the user a question. |
| `error` | The agent hit an error. |
| `finished` | Done. |
| `ratelimit` | Throttled by the provider. |
| `sleeping` | Idle long enough to be considered asleep. |
| `dizzy` | The agent's process is gone or confused (a zombie). |

Any other value is drawn as `idle` (both the phone and the desktop's hub fall back to it). The desktop's
`STATES` list is this exact set; keep them in one place so they cannot diverge.

---

## 8. Approvals

### 8.1 The fingerprint

The fingerprint is the request's identity. Both sides must derive it identically, or a decision resolves nothing:

```
fingerprint = lowercase hex of SHA-256( UTF-8 bytes of  f1 U+001F f2 U+001F f3 U+001F f4 U+001F f5 )
```

where the five fields, in this exact order, are:

| # | Field | Value |
| --- | --- | --- |
| f1 | `pillId` | the agent's pill id (`integration_claude`, `agent_codex`, …) |
| f2 | `sessionId` | the agent's own session id on the desktop (not sent on the wire: it only enters the hash) |
| f3 | `tool` | the tool name as the agent gives it (`"Bash"`, `"AskUserQuestion"`, …) |
| f4 | `command` | the **full, untruncated** command text for an approval; the JSON of the questions for a question |
| f5 | `inputKey` | the desktop's own key for the tool input (a per-call id or the input's key); any stable string that keeps two identical commands apart |

Rules:

- The separator is **U+001F (unit separator)**, not `|`, not a space, not a comma. It is what makes the fields
  unambiguous: `("a","bc")` and `("ab","c")` must not collide.
- The output is exactly **64 lowercase hex characters**. The phone refuses an `approval` or `question` whose
  fingerprint is anything else, uppercase included.
- `command` in the hash is the whole command even when the wire field was cut to 500 characters: the fingerprint
  identifies the request, the wire text is only what the user is shown.
- Reference implementations: `tools/dev-desktop.mjs` (`fingerprint(pill, session, tool, command, inputKey)`) and
  `ProtocolTest.fingerprintMatchesTheMacDerivation`, whose vector is
  `("agent_codex","sess-1","Bash","rm -rf build","k1") → 0b2944f472c9dda6c1d369e7c161730641be8e67174c8eb67c887a2fe0ccd30d`.
  That vector is the acceptance test of the desktop's derivation: if it does not match, nothing else will.
- Questions use the same function with `tool = "AskUserQuestion"` and the questions' JSON as `command`
  (the reference desktop hashes `JSON.stringify(questions)`).

### 8.2 The TTL: 120 s on the phone, 115 s on the desktop

| Side | Number | What it means |
| --- | --- | --- |
| Phone | `Protocol.APPROVAL_TTL_MS = 120 000` (120 s) | The phone offers a request for decision at most 120 s after **it arrived on the phone's own clock**, and then drops it. The countdown the user sees runs from 2:00 at arrival. |
| Desktop | **115 s** | The desktop must dismiss the pending approval after 115 s: it is what Claude Code's own relay does, and a request the computer has already dropped must not still be offered on the phone — a tap on a dead request would be a decision that resolves nothing. |

Details the desktop must respect:

- The phone's clock is **its own** arrival time, never the request's `createdAt`: a computer whose clock is a few
  minutes off would otherwise make every request look expired (or immortal). `createdAt` is informational only.
- A request is decidable **once**: the phone removes it when it sends the decision (`ApprovalBook.claim`), and
  restores it (with the time it already used, not a fresh 120 s) only if it could not be written.
- A request that arrives twice on one connection (the desktop re-sent it) keeps its first arrival time.
- A link drop clears the phone's book: after the reconnect the desktop re-offers what is still pending.
- The same 120 s rule applies to questions (§9.4).
- The 5-second difference is intentional: the phone must always be the one that stops offering first.

### 8.3 What “decided” means on the wire

The user taps Allow or Deny → the phone sends `decision` → the desktop applies it and sends `approvalResolved`
with that fingerprint. Until `approvalResolved` arrives the phone shows its “Enviando / Permitido / Denegado”
state; if the write failed it shows “no se pudo enviar” and puts the request back so it can be decided again
while it is still valid. Silence is never a confirmation.

### 8.4 Truncated commands

A sender that cuts `command` (an older desktop at **500 characters**; jesscore does not cut) flags it `truncated`. The phone then shows the cut text
with a notice, and never treats the shown text as the whole command: it decides about a request whose fingerprint
was computed over the full one. A desktop may send `truncated:false` or omit the field; sending a cut command
**without** the flag is a bug the user cannot see through the phone.

---

## 9. Questions

### 9.1 What the phone offers

1 to 4 questions, each with 1 to 8 options, exactly as the agent asked them (word for word: the labels are the
agent's own words and the phone never translates or invents one). The user taps options; each answer is confirmed
with the phone's screen lock (“Confirm it's you”); then `answer` is sent and `approvalResolved` arrives.

### 9.2 The `picks` format and the validation the desktop must apply

`picks` is **one list per question, in the order the questions were sent**. Each list holds exact `label`s:

| Rule | Detail |
| --- | --- |
| Count | `picks.length == questions.length`. A short or long array is refused. |
| Non-empty | Every list has at least one label. |
| Distinct | No label repeats inside one list (`new Set(p).size == p.length`). |
| Only offered labels | Every entry is a string that appears in that question's `options[].label`. No near-misses, no case fixes, no trimming: exact string equality. |
| Single choice | Exactly one label. |
| Multiple choice | One or more labels, up to the number of options. |
| Empty question, no options | Never legal: the desktop must not send such a question (§“question” limits). |

The reference desktop's `validPicks` is that table verbatim, and it answers an invalid `answer` with **silence**
(it logs `ANSWER refused` and sends nothing), because a refusal is not a state the phone shows: the question
simply stays until it is answered or expires. Therefore:

- **On a valid answer the desktop MUST send `approvalResolved` with the question's fingerprint.** That is the only
  signal the phone accepts as “it is answered”.
- An answer for an unknown fingerprint, or for a question that was replaced by a newer one, is refused the same way.

### 9.3 Exact labels, in the offered order

The phone's rules (`QuestionFlow`), which the desktop may rely on:

- Tapping a single-choice option replaces the pick; tapping a multiple-choice option adds it, tapping it again
  removes it.
- The stored order of a pick is the order the **options were listed**, not the order of the taps.
- A pick can only be a label that was offered (`QuestionFlow.toggle` ignores anything else).
- `answer` is only sent when every question has at least one pick, at most as many picks as options, and
  `multiSelect` is respected (`single → 1`, `multi → 1..n`).
- Nothing the user types can ever become a label: there is no free-text field in an answer.

### 9.4 Expiry and the confirmation

- The phone stops offering a question after **120 s** from its arrival (`QuestionFlow.expired`), the same rule and
  value as an approval (§8.2), because the desktop's side stops accepting at 115 s.
- The desktop resolves a question it no longer accepts by sending `approvalResolved` for it (the user answered on
  the computer, the agent moved on, it timed out), or simply lets it expire.

---

## 10. Diffs

The session's `files` list says which files changed and by how much. The lines come only when asked for, one
`getDiff` at a time, answered by 1 to 4 `diff` parts.

Limits (both sides keep them; `Protocol`):

| Limit | Value |
| --- | --- |
| Lines of one file the desktop sends | **200** (`MAX_DIFF_LINES`); more is cut and `truncated:true` |
| Characters of one line | **400** (`MAX_DIFF_LINE_CHARS`), then the text is cut |
| Lines per part | **100** (`PART_LINES`) |
| Parts per file | **4** (`MAX_DIFF_PARTS`) |
| Files listed per session | **20** (`MAX_FILES`), the newest |
| File name length | **80** (`MAX_FILE_NAME_CHARS`) |

Line kinds:

| `kind` | Meaning |
| --- | --- |
| `"+"` | A line added |
| `"-"` | A line removed |
| `" "` | Context (an unchanged line between hunks) |
| `"@"` | A hunk header (e.g. `"@@ 10"`), or the start marker |

Anything else is a context line; the phone draws it as one, so an unknown kind never breaks the screen.

Special answers:

- **`gone`** — one part with `gone:true`, empty `lines`, `part:0`, `parts:1`. The desktop does not have the file
  any more (branch moved, session ended, unknown id). The phone shows “this file is no longer here”.
- **`tooLarge`** — the file is too big to display: `tooLarge:true` in the session's `files` entry, and a `diff`
  with no lines if one is asked for anyway. The phone shows the reason instead of text.
- **`truncated`** — the desktop had more than 200 lines and sent the first 200; the phone says “the diff was
  shortened”. It is not `tooLarge`: the text is there, only incomplete.

The reference desktop's behaviour is the model: a 250-line file becomes 200 lines in two parts of 100 with
`truncated:true`; a 5400-added-line file is `tooLarge` and carries no lines; a file id it does not know is `gone`.

---

## 11. Usage

`usage` carries both plans. The phone shows pills with the used percentage and the time until the reset, from
`fiveHour`/`sevenDay` and `resetsAt`. Both plans absent takes the display away (the user's switch is off, or
nothing is known). An unusable window is dropped rather than guessed (`pct` outside `0..100`, `resetsAt <= 0`).

The desktop's job: read the plans it can read, send whole percentages, send `updatedAt`, and send `usage` again
when the numbers move.

---

## 12. Services

`services` carries at most 7 read-only cards of the services the user ticked on the computer. Each card is a
headline plus up to 3 label/detail lines. There is nothing interactive: the phone never sends anything back
about a card. An empty array removes them.

The ids are the desktop catalogue's own (`integration_stripe` … `integration_calcom`): the phone skips a card
whose id it does not know, which is how a new service does not break an older app.

---

## 13. Chat

- The phone asks for the model list as soon as `chat` is offered by both sides, and again whenever the Chat screen
  opens.
- One answer at a time. The phone makes the id (1..64 of `[A-Za-z0-9_-]`), the desktop echoes it on every line
  of that answer.
- The answer streams as `chatDelta` pieces and ends with `chatDone` (or `chatError`). A `chatDone` that carries
  `text` replaces everything streamed — that is how a provider that rewrote its output is handled.
- Cancel and reset are the user's actions, and the desktop answers a cancel with `chatError`/`canceled`.
- The desktop's limits (4000 characters, 12 sends per 10 minutes, one answer at a time) exist on the desktop,
  not only in the phone: the phone refuses what it can see, and trusts the desktop for the rest.
- Nothing of the conversation is persisted on the phone except its own copy of what it read and of the engine's list
  (KN-01, §6 of this document): the desktop holds the conversation state, and the phone's copy is read-only material for
  when the engine does not answer.

---

## 14. Keep-alive summary

| Timing | Value | Who |
| --- | --- | --- |
| `ping` interval | 20 s | phone |
| Answer to `ping` | `pong`, immediately | desktop |
| Liveness check wait | 8 s for any inbound line | phone (screen on / new network / alarm) |
| Phone read timeout | 60 s of silence → reconnect | phone |
| Reference desktop socket timeout | 90 s of silence → destroy | `dev-desktop.mjs` |
| Desktop idle timeout | 20 min of silence | desktop (`server::IDLE_TIMEOUT`) |
| Phone periodic alarm | every 4 min (`KeepAlivePolicy.INTERVAL_MS`), 15 s wake lock | phone |
| Backoff | 1, 2, 4, 8, 16, 30 s | phone |

The desktop must answer pings promptly and must not keep a silent connection open beyond its own idle timeout:
a phone that reconnects is normal and costs nothing, a connection that is half dead costs the user a real
approval.

---

## 15. Security rules the desktop must keep

1. **Only paired phones.** A connection is served only after a `hello` with the stored token. There is no
   anonymous mode, no “pair later”, no trust-on-first-use. A phone the desktop does not know gets `auth` and is
   closed.
2. **The token check is constant time** (`constant_eq`), and the refusal is delayed 400 ms so guessing is slow.
   Tokens are 16..128 characters of `[A-Za-z0-9_-]`; the desktop generates 32 (192 bits) and keeps it in the OS
   keystore, never in a settings file, never in a log.
3. **Local network only.** The desktop refuses a peer whose address is not private, loopback or link-local — it
   must be dropped before a single byte is read. Admission is bounded so one noisy device cannot fill all slots
   (8 connections, at most 2 unauthenticated per address, at most 4 unauthenticated from strangers, 8 known
   addresses remembered).
4. **Pinning is the only authentication of the desktop.** The phone accepts exactly one certificate. The desktop
   must never regenerate its certificate casually: a new certificate is a new `fp` and every phone must pair again.
5. **Never send a secret.** The token never appears in a message the desktop sends, in a URL after pairing, in a
   log or in a diagnostics report. Nothing in the protocol carries a provider key, an API token, a password or a
   file's contents beyond the diff the user asked for. The mDNS announcement carries only the 16-hex-character
   fingerprint id and the version: no token, nothing that could be replayed.
6. **Lock-screen-safe fields.** The phone draws a public version of everything it can show on the lock screen:
   the approval notification, the quick-settings tile and the widget there show the agent and the state, never
   the command, never a file name, never a decision. The `command` is the sensitive field; the desktop must put
   the whole thing there (cut at 500 characters, `truncated` when cut) and must not hide another sensitive string
   somewhere else. A user's paths appear only inside `command`/`lines`/`steps` as text; `project` and file
   `name`s are **always last segments**, and the phone strips any path a desktop sends anyway.
7. **Length limits are a defence, not decoration.** 64 KiB per line, plus every cap in this document (16
   sessions, 20 files, 20 steps of 200 characters, 200 diff lines of 400 characters, 4 questions with 8 options,
   7 service cards of 3 lines of 80 characters, 4000 characters of chat). The phone drops what breaks the shape
   instead of growing a screen for it; the desktop must stay inside them so nothing is silently discarded.
8. **No polling, no relay, no account.** The protocol has no message that could carry a relay address, an
   access key or a room: the phone ignores any field it does not know (`relay`, `room`, `key`, `access`, `prefs`).
   Kenai removed its wardrobe and its relay: do not reintroduce them under new names.
9. **The phone knows only what it is told.** A `decision` is only possible for a request the phone holds, once,
   and inside 120 s; an `answer` only for a question that was offered. The desktop must apply the same discipline
   from the other side: a decision for an unknown or expired request is ignored, not “applied to the next one”.

---

## 16. Support today: phone vs desktop

Read from the code, not from the task log. “Today” is Koda `release/0.2` (`apps/desktop-tauri/src/phone_link/`)
next to Kenai `release/0.1`.

| Message / capability | Phone (Kenai 0.1) | Desktop (Koda `release/0.2` today) | Needed for the desktop |
| --- | --- | --- | --- |
| Pairing link + QR | Parses, scans, pastes, opens `abito://pair` intents; stores 5 fields encrypted | `pairing_link()` and `qr_svg()` implement the link and the QR, but **nothing calls them**: no command, no Settings surface | Show the link and the QR in the phone-link settings; call `load_or_create_identity` + `token` + `pairing_link` + `qr_svg` |
| `_abito._tcp` announcement | Browses, matches on `fp`(16)+`v`, probes for the full pin | **Not announced**: `ids::MDNS_SERVICE` exists and is only asserted in a test | Announce with TXT `fp` (16 hex) + `v` while the link is on; withdraw when off |
| TLS server + pinning | TLS 1.2/1.3, one pinned certificate, no client cert | Implemented and correct (`tls_acceptor`: ECDSA P-256 self-signed, TLS 1.3 and 1.2) | Keep as is |
| `serve()` | Connects whenever the link is on | Implemented and tested, **never spawned**: `paused.rs` lists `Feature::Phone` as paused, so no host starts it | Flip the switch: start the listener (and the announcement) when the user turns the phone link on |
| LAN-only admission | Assumes the LAN | Refuses non-local peers; 8 slots, 2 unauth per address, 4 stranger unauth, 8 known addresses, hello within 10 s (3 s hurried) | Keep as is |
| `hello` | Sends `v`, `token`, `device`, `caps` | Validates type/v/token in constant time, 400 ms penalty, `auth` + close | Keep; **read `caps`** |
| `welcome` | Reads `v`, `desktop`, `os`, `caps`; stops on a wrong `v` | Sends `v`, `desktop`, `os`; **never sends `caps`** (`Features` is an empty trait, `Shared::features` unused; a test asserts caps are absent) | Send `caps` for the features the user switched on, omitted when none |
| `sessions` | Reads every field, all 11 states, and details/files when offered | Sends `pillId`, `agent`, `state`, `statusText`, `stepIndex`, `stepCount`, `updatedAt` only (`project` is `#[serde(skip)]`); `stepIndex`/`stepCount` are always `0` (source maps rows without steps) and `statusText` is only `"N sessions"`; states only `working`/`sleeping`/`dizzy`/`finished`/`idle` | Feed step progress and real status texts; add `steps`/`finalLine`/`project`/`color` for `details`, `files` for `diffs` |
| `approval` | Sheet, 2:00 countdown, fingerprint, `truncated`, one decision | **Missing**: `hub.rs` has the `approval` state word only; `server.rs` ignores `decision` as an unknown type | Implement: send `approval`, compute the fingerprint, re-offer after a reconnect |
| Fingerprint | Derives and validates (64 lowercase hex, U+001F, 5 fields) | **Missing**: no `ApprovalRelay`/fingerprint function in `phone_link` | Implement the derivation and make the `ProtocolTest` vector match |
| `decision` | Sends once per held request | **Not handled** (unknown type → ignored) | Accept, look up by fingerprint, apply, confirm |
| `approvalResolved` | Removes the request, closes the sheet, lands on the result | **Missing** | Implement: send on every resolution (decided here, decided there, expired, question answered) |
| Desktop approval TTL | 120 s from arrival, then it stops offering | **Missing** | Dismiss pending approvals at **115 s** |
| `question` | Surfaced only when `answers` was offered; 1..4 questions, 1..8 options, progress, lock-screen confirm | **Missing** | Implement, with the exact limits |
| `answer` | `picks` per question, exact labels, single/multi validation | **Missing** | Implement validation (`validPicks` table) and answer with `approvalResolved` |
| `getDiff` / `diff` | Only when `diffs` was offered; 200 lines, 400 chars, 4 parts of 100 | **Missing** | Implement parts, `gone`, `tooLarge`, `truncated` |
| `usage` | Pills with percentages and resets | **Missing** | Implement (or do not offer `usage`) |
| `services` | Read-only cards, 7 known ids, 3 lines of 80 characters | **Missing** | Implement (or do not offer `services`) |
| `chat` (`chatModels`, `chatSend`, `chatCancel`, `chatReset`, deltas/done/error) | Full screen, 4000 characters, one answer | **Missing**: no `phone_link/chat.rs`; the reference desktop copies rules from one that is not in this tree | Implement, or do not offer `chat` |
| `ping` / `pong` | Ping every 20 s, expects pong, 8 s liveness wait | Answers `pong`; ignores unknown types | Keep as is |
| `bye` | Best effort on stop | Closes the connection | Keep as is |
| `error` | `auth` final, others retried; a wrong `welcome.v` is final as the phone's own `version` error | Sends `auth` (after 400 ms, also for a wrong `hello` version) and `protocol` (line too long) | Keep as is |
| `details` capability | Session details screen (steps, last line, project, colour) | Constant exists; cannot be offered | Offer `details` and send the fields |
| `prefs` / relay fields | Ignored on purpose (no wardrobe, LAN only) | Not ported (S5) | Nothing to do |

---

## 17. Testing a desktop against the phone

### 17.1 The phone's own interoperability suite

`app/src/test/kotlin/com/kenai/android/link/DevDesktopInteropTest.kt` runs the **real** `LinkClient` against
`tools/dev-desktop.mjs` — a separate implementation, with Node's TLS stack and its own certificate. It skips
itself when `node` or `openssl` is missing. What it proves, case by case:

| Test | Proves |
| --- | --- |
| `pairsReceivesSessionsAndApprovalAndDecides` | pairing link parses, welcome and sessions arrive, an approval is decided once, `approvalResolved` comes back, the desktop logs `DECISION allow` |
| `wrongTokenIsRejectedByTheNodeDesktop` | a bad token produces `auth` and no welcome |
| `aQuestionIsAnsweredAndRemovedOnTheNodeDesktop` | `answers` is offered, a question arrives, invalid picks are refused, a valid answer is accepted with `approvalResolved`, **the labels are never logged** |
| `theServiceCardsComeWithTheSwitchOnTheNodeDesktop` | `services` cards, ids and line details |
| `theUsageComesWithTheSwitchOnTheNodeDesktop` | `usage` windows and `resetCredits` |
| `filesAreListedAndTheirLinesComeInPartsOnTheNodeDesktop` | `files` in sessions, a 5-line diff, a 250→200-line file in 2 parts with `truncated`, lines cut to 400, an unknown file is `gone` |
| `withoutTheSwitchTheNodeDesktopListsNoFilesAndAnswersNoRequest` | nothing is offered, `getDiff` returns false, no files are listed |
| `withoutTheSwitchTheNodeDesktopOffersNoQuestion` | no question without `answers`; `answer` returns false |
| `chatWorksAgainstTheFakeProvider` | models, deltas, `done` (with and without `text`), and the `provider`/`auth`/`not_allowed`/`too_long` refusals |
| `oneAnswerAtATimeAndCancelStopsIt` | `busy` for a second send, `canceled` after `chatCancel`, and the next send works |
| `anOlderAppOrAnOlderComputerSeesNoChat` | a phone that asks for nothing is offered nothing and sends nothing |
| `detailsArriveWhenTheDesktopOffersThem` | `steps`, `project`, `color` with `details` |
| `anOlderAppGetsPlainSessionsFromADesktopThatHasDetails` | the same desktop sends plain v1 sessions to an app that did not ask |
| `aDesktopWithoutDetailsSendsNoneEvenToAnAppThatAsks` | a desktop without the feature sends none, even to a new app |

Run it from the repository root:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.kenai.android.link.DevDesktopInteropTest'
```

The protocol-level tests next to it are the field-by-field specification:
`ProtocolTest`, `ProtocolAnswersTest`, `ProtocolChatTest`, `ProtocolDetailsTest`, `ProtocolDiffsTest`,
`ProtocolServicesTest`, `ProtocolUsageTest`, `ProtocolPrefsTest`, `PairingTest`, `LanOnlyPairingTest`,
`ApprovalBookTest`, `DiscoveryTest`, `PinnedProbeTest`, `ReconnectPolicyTest`, `LinkRetryTest`,
`LinkClientTest`, `LinkAnswersTest`, `LinkDiffsTest`.

### 17.2 `tools/dev-desktop.mjs` as the reference desktop

```
node tools/dev-desktop.mjs [--host IP] [--port N] [--name "My PC"] [--step MS] [--once] \
     [--fake-chat] [--details] [--answers] [--diffs] [--usage] [--services]
```

It prints one JSON line (`{"listening":port,"fp":…,"link":"abito://pair?…"}`) and the pairing link, and needs
`openssl` on `PATH` (it makes its own certificate). What each flag turns on, and what it proves:

| Flag | What it does |
| --- | --- |
| (none) | Sessions only, plain v1: `welcome` without `caps`, no approval, no question, no files, no usage, no services, no chat |
| `--once` | Exits 200 ms after a decision or a valid answer, so a one-shot check terminates |
| `--step MS` | Pace of the scripted run (default 2500): `thinking` → `working` + `searching` → **approval** → (question) → `working` → `finished` + `error` → `sleeping` |
| `--details` | Sessions carry `steps`, `project`, `color` and a `finalLine` on `finished`, and `welcome` offers `details` |
| `--diffs` | Claude Code's session lists `app.ts` (small), `long-file.ts` (250 lines) and `generated.json` (`tooLarge`), and `getDiff` is answered like §10 |
| `--answers` | `welcome` offers `answers` and a two-question ask arrives (one single choice, one multi-select) |
| `--usage` | `welcome` offers `usage`; a Claude plan (5 h 63 %, week 21 %) and a Codex plan (week 31 %, 2 resets) are sent on connect and every few steps with moving percentages |
| `--services` | `welcome` offers `services` with fake Stripe and Notion cards |
| `--fake-chat` | `welcome` offers `chat` with `fake/echo` and `fake/other`; `/error`, `/auth`, `/slow`, `/long` and `/rewrite` exercise the error and streaming paths without spending anything |

Its log is the desktop-side evidence: `HELLO ok device=…`, `AUTH rejected`, `APPROVAL sent <fp>`,
`DECISION allow|deny <fp>`, `DECISION ignored <fp>`, `QUESTION sent <fp>`, `ANSWER accepted|refused <fp>`,
`GETDIFF <pillId> <fileId>`, `CHAT send …`, `CHAT canceled …`, `CHAT reset`, `BYE`. It never logs a token, and it
never logs the labels of an answer (that is a rule of the protocol, see §15).

### 17.3 A real desktop

A desktop implementing this document must pass, at minimum, the four checks a phone can perform:

1. **Pairing**: the QR/link parses; the connection is accepted over TLS with the pinned certificate; a wrong
   token is refused with `auth`.
2. **Sessions**: the island shows the agents; a session's progress and details appear when offered.
3. **Approvals**: the sheet appears with the agent, the tool, the command and a 2:00 countdown; Allow and Deny
   both reach the desktop and `approvalResolved` closes the sheet; a request decided on the desktop disappears
   from the phone.
4. **Recovery**: stopping the desktop, restarting it, changing the computer's address and switching Wi-Fi all end
   with the phone reconnected (or with the “not found” hint and the manual address sheet), never with a stuck
   screen.

### 17.4 The emulator

Android's emulator reaches the host's loopback as **`10.0.2.2`**, so the reference desktop is started with that
host and its link is opened on the phone:

```sh
# 1. the reference desktop, listening on all interfaces, printing a link that says 10.0.2.2
node tools/dev-desktop.mjs --host 10.0.2.2 --details --answers --diffs --usage --services --fake-chat

# 2. install the app (release build)
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. open the printed link on the phone: the QR scanned by a camera app, the pasted link,
#    or the intent below are the same path through the same parser
adb shell am start -a android.intent.action.VIEW -d 'abito://pair?v=1&host=10.0.2.2&port=47821&fp=<fp>&token=<token>&name=Alvaro%20PC'
```

The phone then shows “Pair with <name>?” → `HELLO ok` → “Connected · <name>”. Ready to check on the emulator:

- the approval sheet: Allow with the screen lock, Deny, the countdown from 2:00, the desktop's
  `DECISION allow`/`DECISION deny` lines, and `approvalResolved` closing the sheet;
- a question (“Question 2 of 2” with a multi-select) → `ANSWER accepted`;
- the Chat screen against `--fake-chat`, including `/error`, `/auth` and Cancel → `CHAT canceled`;
- session details (`project`, steps) and the `app.ts` diff → `GETDIFF`;
- usage pills and service cards on Home;
- the “address” sheet when mDNS is unavailable (the emulator has no announcement from the reference desktop:
  use the saved address or 10.0.2.2 by hand — this is the fallback path, not a bug);
- the widgets, the tile and the notification, which read the same link state.

A real desktop that implements this document should be checked the same way, plus what the reference cannot do:
a second phone being refused, the desktop rotating its token (the phone must be locked out until it pairs again),
the desktop turning each capability off in its settings (the next `welcome` must stop offering it and the phone
must stop showing it), and the desktop dismissing a pending approval at 115 s while the phone's countdown still
has five seconds left.

---

## 18. Open items

- The wire identifiers stay `abito` until K2-12; the desktop and the phone must both change together then, and
  every pairing in the field becomes invalid (a new certificate, a new link, a new pair).
- The desktop's `Feature::Phone` switch, the mDNS announcement and the capabilities are the first three things
  the desktop needs; everything else in §16 depends on them.
- `tools/dev-desktop.mjs` is the executable spec: when this document and the script disagree, the script is
  what the phone is tested against, and the document is what has to be fixed.
