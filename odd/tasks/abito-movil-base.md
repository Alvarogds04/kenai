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
- [x] T2 (S2) Toolchain JDK 17 + Gradle 8.10.2 · inline · openjdk-17 instalado (L4)
- [x] T3 (S1,S3) Compilar: stub neutro del personaje + icono/sonidos placeholder Abito · inline (worker no disponible) · ver commit T3
- [ ] T4 (S3) Renombrar paquete/applicationId/strings a Abito (`com.abito.android`) · inline
- [ ] T5 (S4) Protocolo Abito (esquema, mDNS, CN) + interop con `phone_link` del escritorio; reapuntar ReferenceFiles/DevDesktopInteropTest al escritorio Abito; quitar relay (RelayCryptoTest saltado) · inline
- [ ] T6 (S1) Personaje Abito (clay-engine del escritorio portado) · por decidir
- [ ] T7 (S1) APK debug en emulador y móvil real; demo sin PC · inline

## Log
- L1: «Hola vamos a comenzar un repositorios y tambien un vault nuevo o en esto dime tu que seria lo mejor pero quiero empezar con la version de abito que estamos montando ahora pero para movil, podemos usar como base el coucou de movil y de ahi trabajar en el nuestro o si seria mejor empezar con soluciones nuestras con requerimientos compartidos con los de vault actual, ya que quiero que vayamos rapido de partida para luego podamos implemntar lo de ahora»
- L2: «dale me paarece correcto empecemos» (vault único + repo nuevo + base Coucou Android con identidad Abito)
- L3: Solo hay JDK 25 instalado; AGP 8.7.3/Gradle 8.10.2 necesitan JDK 17. SDK en ~/Android/Sdk (platform 35, build-tools 35/36).
- L4: Humano eligió opción 1 («1»): instalar openjdk-17 al lado del 25. Instalado (/usr/lib/jvm/java-17-openjdk-amd64); el Java por defecto sigue en 25. Gradle se corre con JAVA_HOME=…java-17….
- L5: T3 verde: `./gradlew testDebugUnitTest assembleDebug` → 803 tests, 0 fallos, 64 saltados (47 de paridad contra fuentes del escritorio Coucou vía ReferenceFiles + 17 RelayCryptoTest sin vectores). Quitado el armario (outfits de Mochi, cap `prefs`). Tests borrados: UserSettingsTest.theOutfitChoice*, anOutfitChoice*; RustDesktopInteropTest.theOutfitReachesAnApp…; AssetsTest.everyBundledSoundIsTheDesktopsOwnFile…; WidgetPreviewTest.theBadgesAreTheOnesOfTheStates; ProtocolPrefsTest reescrito (prefs se ignora). Debilitados: ScanScreenTest sin chequeo de .github/phone-link.yml; WidgetSurfacesTest sin docs/android.html ni README. APK sin .wav, PNG ni código Mochi.
