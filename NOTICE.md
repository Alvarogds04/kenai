# Notice

This app starts from the source code of **Coucou for Android** (https://github.com/Louis-CFM/coucou, `android/`,
upstream commit `434d6a2`), © 2026 Rhytam Biswas, under the MIT License (see [LICENSE](LICENSE)).

Kenai does **not** use Coucou's name, the Mochi character (its design, drawings, expressions and animations, including
the code that draws them), the app icon, the sounds or the media. Those are © Louis Raillé and are not covered by the
MIT License; the permission granted to Coucou for Android is not transferable. They were left out of this import
on purpose:

| Left out | Upstream path |
| --- | --- |
| Mochi character code | `app/src/main/kotlin/com/coucou/android/mochi/` and its tests |
| Sounds | `app/src/main/res/raw/*.wav` |
| App icon | `app/src/main/res/mipmap-*/` |
| Mochi previews and widget art | 22 drawables in `app/src/main/res/drawable/` that draw Mochi |
| Cloud relay | `relay/` (Kenai is self-hosted, LAN only) |

Kenai replaces them with its own name, character, icon and sounds.
