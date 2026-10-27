# Abito for Android

The phone app of Abito: it pairs with the Abito desktop island on your local network, shows your coding agents and lets
you answer them away from the computer.

> **Status:** starting point. This is the imported code of Coucou for Android without its reserved assets
> (see [NOTICE.md](NOTICE.md)). It does **not** build yet: the character, icon and sounds are being replaced
> with Abito's own, and the package and protocol are being renamed to Abito.

- Desktop side: `apps/desktop-tauri/src/phone_link` in the Abito repository (scheme `abito://pair`, mDNS `_abito._tcp`).
- LAN only, no cloud relay.
- Planning, backlog (HU-049, HU-037) and decisions live in the Abito vault.

## Build (once it builds)

Needs JDK 17 and the Android SDK (platform 35):

```bash
./gradlew test assembleDebug
```

## License

Code: MIT (see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md)).
