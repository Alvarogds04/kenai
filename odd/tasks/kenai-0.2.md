# kenai-0.2
Kenai 0.2: chat shared with Koda, other apps that react to or trigger Kenai, and scheduled agent tasks. Base: `release/0.1` at `9d25455`. Kenai 0.1 is not published yet; nothing here ships in it.

## Specs
- S1: «Para Kenai es posible que pueda manejarse con chat y todo eso indipendiente? Y que se compartida con koda?» → «2a»: the phone's chat and Koda's chat share the conversation through the existing phone link, while they are connected. Not decided: whether the phone also chats on its own, with its own keys, when the computer is off.
- S2: «También el tema de automatizar En el móvil no se cómo lo podamos llevar que si con task manager o algo así» → «3a»: other apps (Tasker, Automate, Samsung Routines) can react to Kenai and trigger it (Android intents and broadcasts).
- S3: «3c»: scheduled tasks of the agents, cron-like. Koda runs them on the computer; the phone creates and watches them. Needs the computer on.
- S4: An automation never allows a permission request by itself: Allow always asks for the fingerprint or the screen lock, as in 0.1. This is my rule, proposed in the same message as the options and not contradicted; the human has not confirmed it explicitly.
- S5: LAN only, like 0.1: no relay, no cloud account.

## Tasks
- [ ] T1 (S2, S4) Android intents and broadcasts: Kenai announces what happens (a request arrives, an agent finishes, the link goes down) and accepts a few safe actions. It never decides a request. · Kenai only; can start first
- [ ] T2 (S1) Chat shared over the link. Needs Koda to serve chat (planned for Koda 0.3) and new messages in docs/ANDROID_LINK.md. · blocked on Koda 0.3
- [ ] T3 (S3) Scheduled agent tasks. Needs a scheduler in Koda and new messages in the link contract; the phone gets a screen to create, list and cancel them. · blocked on Koda
- [ ] T4 (S1) Decide whether the phone also chats independently (own keys, history on the phone, first use of the internet). · needs the human

## Log
- L1: «Para Kenai es posible que pueda manejarse con chat y todo eso indipendiente? Y que se compartida con koda?»
- L2: «También el tema de automatizar En el móvil no se cómo lo podamos llevar que si con task manager o algo así». The parent offered four options: a) other apps react to Kenai, b) Kenai's own rules, c) scheduled agent tasks run by Koda, d) phone-side jobs with Android's scheduler.
- L3: «2a, 3a y 3c».
- L4: Open for the human: T4. 2a alone leaves the chat depending on the computer. Also pending: whether S4 is confirmed.
