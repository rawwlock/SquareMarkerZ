# SquareMarkerZ

Dynmap-style marker management for [squaremap](https://github.com/jpenilla/squaremap): custom icons loaded
from URLs, and marker sets (categories) that each show up as their own toggleable layer in squaremap's web
UI.

## Target platform

- **Minecraft / Paper 26.3** ("Wilderness Bound"). Paper 26.3 is currently an **experimental/alpha** branch
  (the stable download page still defaults to 26.2) — you'll need a 26.3 Paper build (toggle "experimental
  builds" on the Paper downloads page) or a self-built 26.3 server to run this. If you're actually running
  26.2, lower `api-version` in `plugin.yml` to `'26.2'` and rebuild.
- Requires **Java 25** (both to compile and to run the server).
- Requires **squaremap 1.4.0+** installed and enabled (first squaremap release with 26.3 support). The
  plugin hard-depends on it (`depend: [squaremap]` in `plugin.yml`) and will refuse to enable without it.
- Built only against Bukkit-compatible API (classic `plugin.yml`, `CommandExecutor`/`TabCompleter`, no
  Brigadier-only registration, no NMS, no reflection), so it should run unmodified on Bukkit-based Paper
  forks too.

## Building

```bash
./gradlew build
```

The output jar is `build/libs/SquareMarkerZ-<version>.jar`. `squaremap-api` and `paper-api` are both
`compileOnly` — neither is shaded into the jar; squaremap provides its own API implementation at runtime,
and Paper provides its own API implementation as the server itself.

## Installation

1. Make sure squaremap 1.4.0+ is installed and working first.
2. Drop `SquareMarkerZ-<version>.jar` into `plugins/`.
3. Start the server. A `plugins/SquareMarkerZ/` folder is created with `config.yml`, `sets.yml`,
   `markers.yml` and an `icons/` cache directory.

> **Note on paths:** the plugin's data folder follows its actual name, `SquareMarkerZ`, since Bukkit always
> uses the `name:` field from `plugin.yml` for a plugin's data folder.

## Permissions

All commands require **`squaremarkers.admin`** (`default: op`). Even if that permission is granted to a
non-op player through a permissions plugin, the command executor additionally rejects any player who isn't
server-op — only the console bypasses the op check. Players without permission get a clear error message
and see no tab-completion suggestions at all.

## Commands

Base command: `/marker` (alias `/squaremarker`).

### Markers

| Command | Description |
|---|---|
| `/marker add <id> [iconUrl] [label...] [--set <setId>]` | Creates a marker at your location. `iconUrl` is optional only if the target set has a default icon. |
| `/marker addat <id> <world> <x> <z> [iconUrl] [label...] [--set <setId>]` | Same as above, from the console (or a player) at explicit coordinates. Y defaults to the highest block at that x/z. |
| `/marker remove <id>` | Deletes a marker. |
| `/marker move <id>` | Moves a marker to your current location. |
| `/marker moveset <id> <setId>` | Moves a marker into a different set. |
| `/marker label <id> <label...>` | Sets the hover tooltip text. |
| `/marker icon <id> <iconUrl>` | Changes a marker's icon. |
| `/marker desc <id> <text...>` | Sets the click popup text (basic HTML allowed). |
| `/marker list [world] [page] [--set <setId>]` | Paginated list with clickable `[tp]` entries. |
| `/marker info <id>` | Shows a marker's full details. |
| `/marker tp <id>` | Teleports you to a marker. |
| `/marker reload` | Reloads config, sets, markers and re-registers icons. |

An argument is treated as an icon URL only if it starts with `http://` or `https://`; `--set <setId>` can
appear anywhere after the required arguments and is stripped out before the rest is treated as the label.
Marker ids match `[a-z0-9_-]{1,32}` (case-insensitive) and are globally unique across every set.

### Marker sets

| Command | Description |
|---|---|
| `/marker set create <setId> [label...]` | Creates a new set/layer. |
| `/marker set delete <setId> [confirm] [--moveto <setId>]` | With `--moveto`, moves the set's markers there and deletes it immediately. Otherwise, run it once to get a 30-second confirmation prompt, then run `/marker set delete <setId> confirm` to actually delete the set and its markers. The default set can't be deleted. |
| `/marker set label <setId> <label...>` | |
| `/marker set hide <setId> <true\|false>` | Whether the layer starts hidden in the web UI. |
| `/marker set controls <setId> <true\|false>` | Whether the layer shows up in the web UI's layer toggle at all. |
| `/marker set priority <setId> <number>` | |
| `/marker set zindex <setId> <number>` | |
| `/marker set icon <setId> <iconUrl\|none>` | Default icon used by markers added to this set without an explicit icon. `none` clears it. |
| `/marker set list` | |
| `/marker set info <setId>` | |

A default set (id and label from `config.yml`, `markers`/"Markers" out of the box) always exists and can't
be deleted; markers created without `--set` go into it.

## Icons

- Only `http://`/`https://` URLs are accepted.
- Downloads run entirely off the main thread, with a 5s connect timeout, a 5s read timeout, and a 512 KB
  max size (all configurable). Anything that isn't a valid PNG, JPG or GIF is rejected.
- Accepted images are resized to fit a configurable square (16×16 by default), keeping aspect ratio, and
  cached to `plugins/SquareMarkerZ/icons/<sha1-of-url>.png`. Cached icons are reused on restart without
  re-downloading, and keep working even if the source URL later goes offline.
- Several markers/sets sharing the same URL share one cached file and one squaremap icon registration
  (reference-counted); the registration is dropped once nothing uses it anymore, but the disk cache stays.
- If a download/validation fails, the command sender gets the reason and the marker/set is left unchanged
  — nothing is created or modified on failure.

## squaremap integration

Each marker set becomes one `SimpleLayerProvider` per world (label, priority, z-index, default-hidden and
show-controls all taken from the set). Markers are `Marker.icon(...)` entries using the set's registered
icon key, with the label as hover tooltip and the description as click popup. Creating, editing or deleting
a marker or set updates the live map immediately, no restart needed. Worlds squaremap enables after this
plugin starts (including ones that load later) are picked up automatically via a `WorldLoadEvent` listener
plus a periodic catch-up check, and all layers are cleanly unregistered on plugin disable.

## Configuration (`config.yml`)

Covers the default set's id/label, icon size/download limits/timeouts, default layer properties for newly
created sets, and every chat message (with a prefix, either legacy `&` color codes or MiniMessage — toggle
`messages.minimessage`). See the shipped `config.yml` for the full, commented list. Changes take effect
after `/marker reload` — no restart needed.

## Storage

- `plugins/SquareMarkerZ/sets.yml` — marker sets.
- `plugins/SquareMarkerZ/markers.yml` — markers (`id`, `set`, `world`, `x`, `y`, `z`, `label`, `description`,
  `icon-url`, `created-by`, `created-at`).
- Every change is saved asynchronously by writing a temp file and renaming it into place, so a crash mid-write
  can't corrupt either file.
- Markers saved before marker sets existed (no `set` field) are migrated into the default set on load.

## License

MIT — see [LICENSE](LICENSE).
