# diseno-movil-isla
Implementar la dirección A «Isla en el bolsillo» (design/movil-identidad-pantallas-base/01-isla-en-el-bolsillo.html) en la app Compose. Sin historia en el vault aún (HU 🎨 móvil pendiente de autorización).

## Specs
- S1: «abito actual de descritorio tiene a koda y tiene unos colores de diseño es importante mantener eso» → Koda reemplaza a la arcilla; colores de rol de design/system.md (`--go #b8ff2e`, `--sun #ffe100`, `--island-live #2eb4ff`, `--red-11 #ff5c75`, `--faint #6f6d66`).
- S2: «en español todo» → texto de la UI en español (voseo).
- S3: Tema oscuro y claro; objetivos de 48 dp; AA; movimiento reducido.
- S4: Sin Mochi/nombre/icono/sonidos de Coucou (NOTICE.md).
- S5: lámina A: tarjeta-isla con Koda y agente en foco, lista de agentes, barra Inicio/Chat/Ajustes, hoja de aprobación con Koda, Emparejar con Koda, Chat.

## Tasks
- [x] T1 (S1,S4) Koda: sprites + modelo puro + KodaView; roles de color · KodaModelTest verde; suite 713 tests, 0 fallos · sin commit
- [~] T2 (S1,S5) Inicio: HeroCard y AgentRows con Koda y color de rol (hecho, visto en pixel7); faltan velo en degradado, chips de estado, tarjetas de la lámina
- [ ] T3 (S1,S5) Hoja de aprobación y Emparejar con Koda
- [ ] T4 (S3) Tema claro (Tokens.LIGHT) siguiendo el sistema
- [ ] T5 (S2) Textos en español (values-es o strings.xml)
- [ ] T6 (S1) Widgets y notificación: fuera de esta lámina; pendiente decisión del humano

## Log
- L1: «Implement the design artboard design/movil-identidad-pantallas-base/01-isla-en-el-bolsillo.html» (handoff de gentle_design).
- L2: «omite lo ultimo que te dije y sigue» → no se envía nada a la sesión abito-movil; se sigue implementando.
- L3: Hallazgo: EnglishOnlyTest y i18n/app-strings.json fijan «solo inglés»; S2 (español) los contradice. Decisión del humano pendiente antes de T5. koda_description añadida en inglés.
- L4: Captura: design/abito-movil-actual/img/impl-inicio-koda.png (Inicio con Koda en modo demo).
