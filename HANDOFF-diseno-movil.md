# HANDOFF · diseño de Abito para Android (Gentle Design)

Lo deja la sesión `01a17eaf` (repo `~/Apps/abito-movil/repo`, nombre «subagent-chat-01a17eaf»). Reportá por intercom
a esa sesión.

## Objetivo

Diseñar la app de Abito para Android con **Gentle Design**. Palabras del humano (27/28-oct, textuales):

> «pasemos en otra sesion nueva a la parte de diseño de abito, con gentle desing»
> «una nueva sesion ya que la app de movil se comparte la informacion pero el diseño y la implemntacion es independiente»

Interpretación (no la amplíes sin preguntar): el móvil **comparte la información** con el escritorio (vault, backlog,
historias, decisiones, sistema de diseño e identidad de Abito), pero **su diseño y su implementación son independientes**.
Tienen pantallas, carpeta de diseño, historias 🎨 y código propios.

## Primer paso (obligatorio, antes de diseñar)

Seguí la **Regla 0 del vault**: primero el backlog. Leé `Reglas.md` y `📋 Backlog/Backlog-Diseño.md` del vault. Después
proponé al humano la o las historias 🎨 Diseño del móvil (por ejemplo «identidad móvil / pantallas base»), con sus
opciones. **No crees ni apruebes historias por tu cuenta**: el humano elige. Antes de crear un `HU-NNN`, comprobá con
`test ! -e` que el número esté libre. Otra sesión ya reutilizó un número (HU-049).

## Contexto (leelo, no lo copies entero)

| Qué | Dónde |
|---|---|
| Vault (único, compartido) | `~/Apps/sb-second-brain/odd/mapa/abito-vault` → `Reglas.md`, `📋 Backlog/Backlog-Diseño.md`, `📦 Product/Telefono-Android.md` |
| Historia de la app base móvil | `📋 Backlog/Historias/HU-053.md` (+ HU-037: aprobar desde el móvil) |
| Decisión de base | `🏗️ Architecture/📜 Decisions/2026-10-27-Abito-movil-base-Coucou-Android.md` |
| Gentle Design de Abito (escritorio) | `~/Apps/sb-second-brain/design/README.md` (reglas: leelas), `design/system.md` (tokens), `design/abito-actual/` (as-is) |
| Identidad en curso | **HU-055** (identidad visual, personaje **Koda**, `design/hu-055-koda/`) y **HU-057** (isla con Koda) |
| App móvil (estado real) | este worktree: `odd/tasks/abito-movil-base.md` (Log L1–L11), `docs/screenshots/demo-home.png` y `paired-home.png` |
| Código de pantallas | `app/src/main/kotlin/com/abito/android/ui/` (Compose), tokens en `core/Tokens.kt`, personaje en `character/` |

Estado de la app: compila y pasan 706 tests. Funciona en el emulador `pixel7` (modo demo y emparejada con
`tools/dev-desktop.mjs`). Tiene el personaje de arcilla del escritorio portado (`clay-engine.js`), widgets con discos
de colores provisorios y ningún sonido. Todo el texto está en inglés, heredado de Coucou: el idioma de la UI es una
decisión del humano.

## Qué tenés que hacer

1. Backlog primero (arriba). Esperá la elección del humano.
2. Montá el diseño móvil **en este repo**, en `design/` (este worktree), con las mismas reglas que
   `~/Apps/sb-second-brain/design/README.md`: as-is, después propuestas `hu-NNN-<slug>/` con 2–3 direcciones que
   difieran en estructura, elegir en el canvas.
   - El as-is del móvil: capturas reales del emulador (`~/Android/Sdk/emulator/emulator -avd pixel7 -no-window
     -gpu swiftshader_indirect`; APK con `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug`).
     Las pantallas de emparejamiento, chat y aprobación son `FLAG_SECURE`: salen negras en una captura a propósito.
3. **Tokens e identidad:** usá los de `design/system.md` y Koda (HU-055) **leyéndolos**. Si el móvil necesita un token
   que no existe, proponelo al humano. **No edites** `~/Apps/sb-second-brain/design/` ni el código del escritorio.
4. **Personaje:** hoy el móvil dibuja la arcilla del escritorio. Si HU-055 cambia a Koda, el móvil tiene que seguirlo.
   Preguntá al humano antes de decidir. Respetá la licencia: nada de Mochi, nombre, icono ni sonidos de Coucou
   (`NOTICE.md`).
5. Requisitos de cada diseño: Android (gestos, barras del sistema, tamaños, widgets), WCAG AA, objetivos de 48 dp,
   reducir movimiento, tema oscuro **y** claro, contenido real.

## Restricciones

- Trabajá **solo en este worktree** (`~/Apps/abito-movil/worktrees/diseno-movil`, rama `feat/diseno-movil`). Nunca en
  `~/Apps/abito-movil/repo` ni en otros worktrees.
- **No hagas commits** salvo que el humano lo pida explícitamente.
- Fuera de tu worktree, el vault y `sb-second-brain/design/` son **solo lectura**, salvo que el humano te autorice a
  escribir. Si te autoriza a escribir en el vault, las historias 🎨 del móvil van a `📋 Backlog/Backlog-Diseño.md`.
- Archivos de otras sesiones (no los toques): `sb-second-brain/design/**` y `worktrees/koda-personaje`, que son de la
  sesión de diseño «Abito-Desing» (`01a17ec6`) y la de Koda (`01a1831d`); el código del escritorio en `sb-second-brain`
  y `abito-public`.
- No reinicies servicios ni toques recursos compartidos sin avisar. El emulador `pixel7` es compartido: apagalo
  (`adb emu kill`) cuando termines.
- Sin coste: nada de servicios de pago, fuentes ni CDNs.
- La revisión nativa (RDD) puede no estar disponible. Si es así, decilo y usá como evidencia las capturas y los tests.

## Preguntas

Todo lo que sea alcance, producto, identidad o trade-off **no lo decidas vos**: frená y mandalo por intercom a la sesión
`01a17eaf`, o preguntáselo al humano si está en tu pane. Si algo es ambiguo, preguntá en vez de elegir una
interpretación.

## Cómo reportar

Por intercom a `01a17eaf`, al terminar cada paso o cuando necesites una decisión: qué hiciste, rutas, qué verificaste
y qué no.
