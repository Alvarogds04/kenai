# abito-movil-base
App base de Abito para Android a partir del código MIT de Coucou for Android, sin assets reservados.
Historia: HU-049 (vault Abito). Decisión: `🏗️ Architecture/📜 Decisions/2026-10-27-Abito-movil-base-Coucou-Android`.

## Specs
- S1: «quiero empezar con la version de abito que estamos montando ahora pero para movil, podemos usar como base el coucou de movil y de ahi trabajar en el nuestro»
- S2: «quiero que vayamos rapido de partida para luego podamos implemntar lo de ahora»
- S3: Sin nombre, personaje Mochi, icono, sonidos ni media de Coucou (LICENSE-ASSETS.md; permiso no transferible).
- S4: Protocolo del escritorio Abito: `abito://pair`, mDNS `_abito._tcp`; solo LAN, sin relé.

## Tasks
- [x] T1 (S1,S3) Importar código MIT sin assets reservados · inline · 065ff5f
- [ ] T2 (S2) Toolchain JDK 17 + Gradle 8.10.2 · bloqueada: decisión del humano (instalar JDK 17)
- [ ] T3 (S1,S3) Compilar: stub neutro del personaje + icono/sonidos placeholder Abito · inline
- [ ] T4 (S3) Renombrar paquete/applicationId/strings a Abito (`com.abito.android`) · inline
- [ ] T5 (S4) Protocolo Abito (esquema, mDNS, CN) + interop con `phone_link` del escritorio · inline
- [ ] T6 (S1) Personaje Abito (clay-engine del escritorio portado) · por decidir
- [ ] T7 (S1) APK debug en emulador y móvil real; demo sin PC · inline

## Log
- L1: «Hola vamos a comenzar un repositorios y tambien un vault nuevo o en esto dime tu que seria lo mejor pero quiero empezar con la version de abito que estamos montando ahora pero para movil, podemos usar como base el coucou de movil y de ahi trabajar en el nuestro o si seria mejor empezar con soluciones nuestras con requerimientos compartidos con los de vault actual, ya que quiero que vayamos rapido de partida para luego podamos implemntar lo de ahora»
- L2: «dale me paarece correcto empecemos» (vault único + repo nuevo + base Coucou Android con identidad Abito)
- L3: Solo hay JDK 25 instalado; AGP 8.7.3/Gradle 8.10.2 necesitan JDK 17. SDK en ~/Android/Sdk (platform 35, build-tools 35/36).
