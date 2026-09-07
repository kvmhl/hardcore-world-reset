# HardcoreWorldReset

[![Download Latest Release](https://img.shields.io/github/v/release/kvmhl/hardcore-world-reset?style=for-the-badge&logo=github&color=2ea44f)](https://github.com/kvmhl/hardcore-world-reset/releases/latest)
[![Java CI](https://github.com/kvmhl/hardcore-world-reset/actions/workflows/build.yml/badge.svg)](https://github.com/kvmhl/hardcore-world-reset/actions)

A lightweight, powerful plugin that **automatically resets the world** whenever a player dies in Hardcore mode. Ideal for speedrunning, collaborative challenges, and "You Die, World Resets" servers.

> **Requires:** Java 25+ and Paper/Minecraft 26.2+ running in `hardcore=true` mode.

---

## Features

*   **Instant World Resets** 
    *   Automatically generates a fresh new world set (Overworld, Nether, The End) upon death.
    *   No external dependencies (no Multiverse required).
*   **Speedrun Timer** 
    *   Live timer displayed in the player list (Tab).
    *   Pauses when no players are online.
    *   Persists elapsed time across server restarts and plugin reloads.
*   **End Goal Detection** 
    *   Detects Ender Dragon kills to complete the run.
    *   Announces the final time to the server.
*   **Automation** 
    *   **Swap Methods:** Use "Seamless" (teleport) or "Disconnect" (kick) logic.
    *   **Advancement Reset:** Automatically clears advancements for a fresh start.
    *   **Run Reset Cleanup:** Clears inventory, armor, offhand, Ender Chest, XP,
        effects, fire, and fall state between runs.

---

## 🚀 Installation

1.  Download the latest **[HardcoreWorldReset-2.6.2.jar](https://github.com/kvmhl/hardcore-world-reset/releases/latest)**.
2.  Place it in your server's `plugins/` folder.
3.  Open `server.properties` and ensure `hardcore=true`.
4.  Start the server.

---

## ⚙️ Configuration

The plugin is highly configurable via `plugins/HardcoreWorldReset/config.yml`.

### Core Settings
| Option | Default | Description |
| :--- | :--- | :--- |
| `plugin-enabled` | `true` | Master switch for the plugin. |
| `world-prefix` | `"hardcore_"` | Prefix for generated world folders (e.g., `hardcore_1`). |
| `swap-method` | `"SEAMLESS"` | `SEAMLESS` (waiting room, then teleport) or `DISCONNECT` (kick players). |
| `end-goal` | `"ENDER_DRAGON"` | Trigger for completing the run. |

### Gameplay Settings
| Option | Default | Description |
| :--- | :--- | :--- |
| `gameplay.auto-start-timer` | `true` | Starts timer automatically when players join. |
| `gameplay.min-players-to-start` | `1` | Players needed to start the timer. |
| `gameplay.announce-deaths` | `true` | Broadcasts death messages to chat. |
| `gameplay.show-timer-in-tab` | `true` | Toggles the Tab list timer. |
| `gameplay.preserve-inventory-on-swap` | `false` | **Debug only**: Keeps items across resets. |

The timer pauses only when the server has no online players. Its elapsed value is
persisted in the plugin state, so a normal server restart or plugin reload does
not reset the run; it resumes when players are online again.

### Performance
| Option | Default | Description |
| :--- | :--- | :--- |
| `performance.world-pregen-distance` | `16` | Chunk radius pre-generated around each dimension entry point before play starts. `16` creates a 33x33 area (1,089 chunks) per dimension. |

### Waiting room

| Option | Default | Description |
| :--- | :--- | :--- |
| `waiting-room.enabled` | `true` | Sends players to a protected void room during seamless preparation. Set to `false` only on a powerful server; generation then happens while players remain in the active world. |
| `waiting-room.world-name` | `"hardcore_waiting"` | Persistent world name for the waiting room. |
| `waiting-room.room-radius` | `4` | Half-size of the glass cage in blocks; `4` creates a 9x9 room. |

### Sound effects

| Option | Default | Description |
| :--- | :--- | :--- |
| `sounds.enabled` | `true` | Enables reset transition sounds. |
| `sounds.reset-start` | `BLOCK_PORTAL_TRIGGER` | Played when the lethal hit is intercepted and the reset starts. |
| `sounds.waiting-room` | `BLOCK_PORTAL_TRAVEL` | Played after a player reaches the waiting room. |
| `sounds.world-ready` | `ENTITY_PLAYER_LEVELUP` | Played after a player reaches the new active world. |
| `sounds.volume` / `sounds.pitch` | `1.0` / `1.0` | Shared volume and pitch for reset sounds. |

The default seamless swap first moves all online players into a persistent
void waiting world (`hardcore_waiting`). The room is a small glass cage and is
protected from PvP, all damage, explosions, interactions, and block changes. While players wait there, Paper's
asynchronous chunk API prepares a complete square around the actual entry
point in every dimension, one chunk at a time. The End is centered on the
plugin's obsidian entry platform at X=100/Z=0 rather than the default End
spawn. Only after the complete replacement set is ready are players
teleported into the new run and the timer allowed to start. World generation
therefore does not run during an active timed run. Larger radii reduce
first-visit chunk lag but increase preparation time and disk usage; failed
chunk requests are retried and never count as ready.

---

## 🛠️ Commands & Permissions

Currently, the plugin operates automatically without commands. Admin commands (like force reset) are planned for future updates.

*   `hardcoreworldreset.admin` - Default: `OP` (for future admin features).
*   `hardcoreworldreset.bypass` - Default: `false` (Bypass death resets).

---

## Versioning and supported versions

Releases follow semantic versioning and use matching Git tags with a `v`
prefix, for example `2.6.2` / `v2.6.2`. Minecraft compatibility is tracked
separately: this release targets Paper/Minecraft 26.2 and Java 25.

Plugin updates, reloads, and server restarts never clear inventories. Inventory
cleanup is guarded by a persisted reset marker and runs only after an actual
world reset has started. Do not delete or manually rewrite the `state` section
while a reset is in progress.

## Building from Source

```bash
git clone https://github.com/kvmhl/hardcore-world-reset.git
cd hardcore-world-reset
.\mvnw.cmd clean verify
```
The artifact will be created in `target/HardcoreWorldReset-2.6.2.jar`.
