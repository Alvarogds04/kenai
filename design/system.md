# Abito design system (synced)

Read-only sync of the Abito desktop app as it is built today. Source tree:
`worktrees/abito-release/apps/desktop-tauri/frontend/` (branch `release/abito`, commit `135fc33d`).
Abbreviation used below: `S` = `frontend/style.css`, `I` = `frontend/vendor/coucou/island/island.css`,
`H` = `frontend/vendor/coucou/hub/hub.css`. Only values found in those files are recorded.

The palette has two themes painted on `<html data-theme>` by `theme.js` (default `system`).
Role colours, Dot identity colours, radii and motion are **never themed** (S:1145-1150).

## Colors — role (theme-independent)

| Token | Value | Usage | Source |
| --- | --- | --- | --- |
| `--go` | `#b8ff2e` | Primary control / selected tab, "go" | S:11 |
| `--sun` | `#ffe100` | Waiting for your yes (pending approval) | S:13 |
| `--red-11` | `#ff5c75` | Real error | S:14 |
| `--island-live` | `#2eb4ff` | In progress | S:15 |
| `--faint` | `#6f6d66` | Off / inert — shapes only, never words | S:16 |
| `--focus-ring` | `rgba(255,255,255,.55)` | Text-field focus edge | S:12 |

## Colors — Dot identity (never carry a state)

| Family | 1 / 2 / 3 | Source |
| --- | --- | --- |
| green (default) | `#d8ff7a` / `#8dff2b` / `#3fe00f` | S:21 |
| blue | `#a8d8ff` / `#2ea8ff` / `#0a6dff` | S:22 |
| mint | `#b4ffe3` / `#2dffac` / `#00d482` | S:23 |
| orange | `#ffd08a` / `#ff8a00` / `#e05a00` | S:24 |
| purple | `#e3c4ff` / `#b04dff` / `#7000ff` | S:25 |

## Colors — surfaces and ink

| Token | Dark | Light | Usage | Source |
| --- | --- | --- | --- | --- |
| `--island-bg` | `#000` | `#ece4d2` | Island body | S:777, S:1176 |
| `--island-bg-glass` | `rgba(0,0,0,.74)` | `rgba(236,228,210,.76)` | Glass island | S:778, S:1177 |
| `--card-bg` | `#141518` | `#f7f2e7` | Cards | S:779, S:1179 |
| `--surface` | `#18181b` | `#fbf8f1` | Pills, code, kbd, options | S:1155, S:1178 |
| `--tab-on` | `#1D1F23` | `#e7e0d0` | Selected tab fill | S:786, S:1180 |
| `--ink` | `#F5F6F8` | `#1a1915` | Primary text | S:781, S:1182 |
| `--ink-2` | `#F1F2F4` | `#2a2823` | Secondary strong text | S:782, S:1183 |
| `--muted` | `#8E939C` | `#5f5c54` | Muted text | S:783, S:1185 |
| `--muted-2` | `#9398A1` | `#55524b` | Muted text 2 | S:784, S:1186 |
| `--hdr-muted` | `#7B8089` | `#6b675e` | Header muted | S:785, S:1187 |
| `--hdr-ink` | `#C9CCD2` | `#4d4a42` | Header action label | S:1169, S:1189 |
| `--primary-bg` | `#F5F6F8` | `#201f1b` | Primary button | S:789, light block |
| `--primary-ink` | `#0B0C0E` | `#f7f4ec` | Primary button text | S:790, light block |
| `--primary-hover` | `#fff` | `#000` | Primary hover | S:1170, light block |
| `--btn-bg` / hover | `rgba(255,255,255,.09)` / `.15` | `rgba(0,0,0,.06)` / `.12` | Secondary button | S:787-788 |
| `--card-line` | `rgba(255,255,255,.035)` | `rgba(0,0,0,.07)` | Card hairline | S:780, S:1191 |
| `--line` | `rgba(255,255,255,.25)` | `rgba(0,0,0,.28)` | Control borders | S:1156, S:1192 |
| `--line-soft` | `rgba(255,255,255,.22)` | `rgba(0,0,0,.22)` | Inputs, method cards | S:1157 |
| `--line-faint` | `rgba(255,255,255,.08)` | `rgba(0,0,0,.08)` | Row separators | S:1162 |
| `--fill` | `rgba(255,255,255,.06)` | `rgba(0,0,0,.05)` | Text fields | S:1163 |
| `--fill-alt` | `rgba(255,255,255,.05)` | `rgba(0,0,0,.05)` | List rows | S:1165 |
| `--fill-hover` | `rgba(255,255,255,.08)` | `rgba(0,0,0,.07)` | Hover | S:1167 |
| `--bubble` | `rgba(255,255,255,.1)` | `rgba(0,0,0,.07)` | Your own chat message | S:1168 |

Inherited Coucou island extras (I:11-27): `--red #f4505e`, `--green #22c55e`, `--green-2 #34d399`,
`--amber #f5a524`, `--cyan #22d3ee`, `--indigo #6366f1`, `--pink #f472b6`. Hub accent (H:15) `--hub-accent #fb923c`.

## Typography

- Family: `--font-ui: ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif` (S:26). System stack on purpose.
- Mono (Coucou): `"Cascadia Mono", "Consolas", ui-monospace, monospace` (I:27).
- Scale (S, T6.1): `--fs-title 16px` (600), `--fs-body 14px`, `--fs-label 13px`, `--fs-caption 12px`.
  Section labels: 12/600 uppercase. Rows 13, hints 12, fields 14, buttons 13 (frontend/DESIGN.md §T6.1).

## Spacing

`--space-4 4` · `--space-8 8` · `--space-12 12` · `--space-16 16` · `--space-20 20` · `--space-24 24` · `--space-32 32` (S, T6.1).
Rows 40 high, row gap 8, group gap 20; 44px hit areas around 32px visual controls.

## Radius

`--radius-xl .75rem (12px)` · `--radius-2xl 1.125rem (18px)` · `--radius-full 999px` (S:27-29). Tabs r10 (DESIGN.md).

## Shadows

No shadow tokens are declared in the synced sources. Depth comes from surface steps (`#000 < #141518 < #18181b`).

## Motion

`--ease-out cubic-bezier(.23,1,.32,1)`, `--spring cubic-bezier(.25,.9,.35,1.03)`, `--ease-in cubic-bezier(.4,0,1,1)`;
press 120ms, state 160ms, view in 400ms after 160ms delay, view out 160ms, control 300ms (S:30-43). Only opacity/transform.

## Component rules

- **Island**: compact Dot 66px; overview 640×160; chat 400×640 docked to the edge; Ajustes 720×min(680, work area) (DESIGN.md geometry).
- **Ajustes**: one panel, rail 184 with tabs Cuentas, Conectores, Modelos, Especialistas, Pills, Apariencia, General; selected tab uses `--go`.
- **Buttons**: 32 visual in 44 hit area, pill drawn on `::after`; primary = `--primary-bg`/`--primary-ink`.
- **Fields**: `--fill`, 6px transparent border, focus `--focus-ring`.
- **Status**: progress `--island-live`, approval `--sun`, error `--red-11`; inert shapes `--faint`.
- **Cards** (`.scard`): `--fill-soft`, no new palette.
