# kenai-0.2
Kenai 0.2: Kenai and Koda as two independent applications that can connect to share chats, automations and mini-Kodas. Base: `release/0.1` at `9d25455`. Kenai 0.1 is not published yet; nothing here ships in it.
The vision is large and not yet cut into slices: the first slice is decided with the human (T1).

## Specs
- S1: Two applications, each working on its own: Kenai on the phone, Koda on the computer. They can connect to each other, for example to share chat information, automations and mini-Kodas, «pero cada uno trabaja a su forma y a su manera». Neither depends on the other. (Replaces the first reading of «2a», chat shared only through the link. It also answers the open question: the phone chats on its own.)
- S2: Other apps (Tasker, Automate, Samsung Routines) can react to Kenai and trigger it through Android intents and broadcasts («3a»). Kept; it is the smallest piece of phone-side automation.
- S3: The user automates from the computer and from the phone. The example Koda already has, the Telegram channel check about «mis acciones», must also be something the user can set up with Kenai: «Y también Kenai me lo tiene que permitir». (Replaces the first reading of «3c», scheduled tasks run only by Koda.)
- S4: Open. Whether an automation may allow a permission request by itself. The parent's default rule is that it never does (Allow always asks for the fingerprint or the screen lock, as in 0.1); the human has not confirmed it, and the new vision may want some autonomy.
- S5: Replaces «LAN only, no relay». For its own chat and automations, Kenai needs the internet on its own (AI providers, Telegram). To confirm with the human: where the secrets live (Android Keystore) and what Kenai may send out.
- S6: A central system, «un Jarvis de lo que sería Eineman»: the user sends things to it and it answers «en un lenguaje natural y neutral».
- S7: Mini-Kodas: specialised AI agents for specific processes. «Si un proceso no requiere una IA, pues que tenga su Chrome Job con sus scripts». A process that needs no AI is a cron job with its own scripts.

## Tasks
- [ ] T0 (S1, S3, S6, S7) Map what Koda already has: automations, the Telegram example, mini-Kodas, cron-like jobs, how chats and memory are shared (engram-koda-sync). · read-only explorer, running
- [ ] T1 (all) Cut the first slice with the human, from the T0 map. Candidates: the phone chats on its own with one provider, the key in the Android Keystore, and the history is shared with Koda over the link. · needs the human
- [ ] T2 (S2, S4) Android intents and broadcasts: Kenai announces what happens and accepts a few safe actions; it never decides a request unless S4 says so. · Kenai only; can start first
- [ ] T3 (S3, S7) Automations on the phone, including the Telegram example and cron-like jobs with scripts. · after T0 and T1
- [ ] T4 (S1, S7) The link: new messages in docs/ANDROID_LINK.md for chat history, automations and mini-Kodas. · coordinate with the Koda session; Koda 0.3

## Log
- L1: «Para Kenai es posible que pueda manejarse con chat y todo eso indipendiente? Y que se compartida con koda?»
- L2: «También el tema de automatizar En el móvil no se cómo lo podamos llevar que si con task manager o algo así». The parent offered four options: a) other apps react to Kenai, b) Kenai's own rules, c) scheduled agent tasks run by Koda, d) phone-side jobs with Android's scheduler.
- L3: «2a, 3a y 3c».
- L4: The parent asked whether the phone also chats on its own, and whether the rule in S4 is confirmed.
- L5: «La idea es que directamente las dos aplicaciones sean por separada. O sea, que nada sí es verdad que funcionaría a través de lo que es el móvil y coda funcionaría en su propia aplicación. Que se pueden conectar entre sí, por ejemplo, en compartir la información de los chats, en automatizaciones, en mini codas, en todas esas cuestiones, sí. Pero cada uno trabaja a su forma y a su manera. Sobre el tema de las conexiones y automatizaciones que yo pueda hacer. Un ejemplo, la automatización que está puesta de ejemplo para Kenai, perdón, para Koda. Esa automatización, que es el tema de lo de Telegram con el canal, de verificar si está bien o no para con mis acciones y todas esas cuestiones. Eso también tengo que verlo yo con Kenai. Y también Kenai me lo tiene que permitir. Así yo puedo automatizar desde lo que sería la computadora, como también puedo automatizar desde el teléfono. La idea es llegar como un sistema central. Si lo vemos a una perspectiva, tendría que llegar como un Jarvis de lo que sería Eineman, donde directamente yo le puedo mandar cosas y ahí me lo pueda responder en un lenguaje natural y neutral. Esa es la idea. Y que existan los mini codas, que son justamente los agentes de IA especializados para ciertos procesos. Por lo menos si un proceso no requiere una IA, pues que tenga su Chrome Job con su con sus scripts y sus cosas para poder automatizarse. Pero la implementación es esa. Los mini codas son justamente los puestos ahí para poder trabajar con ellos, pero que la idea es llevarlo de esa manera.»
