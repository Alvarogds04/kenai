# Kenai for Android

Kenai is the phone app for Koda: it pairs with the Koda desktop island on your local network, shows your coding agents and lets
you answer them away from the computer.

> **Status:** starting point. It builds and its unit tests pass, with a placeholder character and icon and no sounds
> yet. Package `com.kenai.android`, pairing links `abito://pair`, discovery `_abito._tcp`. Part of the code comes from
> Coucou for Android (see [NOTICE.md](NOTICE.md)).

- Desktop side: `apps/desktop-tauri/src/phone_link` in the Koda repository (scheme `abito://pair`, mDNS `_abito._tcp`).
- LAN only, no cloud relay.
- Planning, backlog (HU-071, HU-037) and decisions live in the Koda vault (story K2-12).

## Build

Needs JDK 17 and the Android SDK (platform 35), with `sdk.dir` in `local.properties`:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug
node scripts/gen-strings.mjs --check   # res/values/strings.xml is generated from i18n/app-strings.json
```

Release signing reads `KENAI_KEYSTORE_FILE`, `KENAI_KEYSTORE_PASSWORD`, `KENAI_KEY_ALIAS` (and `KENAI_KEY_PASSWORD`).

## License

Code: MIT (see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md)).
