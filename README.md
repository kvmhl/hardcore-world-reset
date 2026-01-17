# HardcoreWorldReset

[![Download Latest Release](https://img.shields.io/badge/DOWNLOAD-v2.0.0-succcess?style=for-the-badge&logo=github&color=2ea44f)](https://github.com/kvmhl/hardcore-world-reset/releases/latest)
[![Java CI](https://github.com/kvmhl/hardcore-world-reset/actions/workflows/build.yml/badge.svg)](https://github.com/kvmhl/hardcore-world-reset/actions)

A lightweight, powerful plugin that **automatically resets the world** whenever a player dies in Hardcore mode. Ideal for speedrunning, collaborative challenges, and "You Die, World Resets" servers.

> **Requires:** Java 21+ and a server running in `hardcore=true` mode.

---

## ✨ Features

*   **Instant World Resets** 🔄
    *   Automatically generates a fresh new world set (Overworld, Nether, The End) upon death.
    *   No external dependencies (no Multiverse required).
*   **Speedrun Timer** ⏱️
    *   Live timer displayed in the player list (Tab).
    *   Pauses when no players are online.
*   **End Goal Detection** 🐉
    *   Detects Ender Dragon kills to complete the run.
    *   Announces the final time to the server.
*   **Smart Interactions** 🧠
    *   **Swap Methods:** Choose between "Seamless" (teleport) or "Disconnect" (kick) logic.
    *   **Advancement Reset:** Automatically clears advancements for a fresh start.
    *   **Nether Portals:** Custom handler ensures correct 8:1 scaling in custom worlds.

---

## 🚀 Installation

1.  Download the latest **[HardcoreWorldReset-2.0.0.jar](https://github.com/kvmhl/hardcore-world-reset/releases/latest)**.
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
| `swap-method` | `"DISCONNECT"` | `SEAMLESS` (teleport) or `DISCONNECT` (kick players). |
| `end-goal` | `"ENDER_DRAGON"` | Trigger for completing the run. |

### Gameplay Settings
| Option | Default | Description |
| :--- | :--- | :--- |
| `gameplay.auto-start-timer` | `true` | Starts timer automatically when players join. |
| `gameplay.min-players-to-start` | `1` | Players needed to start the timer. |
| `gameplay.announce-deaths` | `true` | Broadcasts death messages to chat. |
| `gameplay.show-timer-in-tab` | `true` | Toggles the Tab list timer. |
| `gameplay.preserve-inventory` | `false` | **Debug only**: Keeps items across resets. |

### Performance
| Option | Default | Description |
| :--- | :--- | :--- |
| `performance.world-pregen-distance` | `0` | Radius to pre-generate chunks (reduces lag, increases load time). |

---

## 🛠️ Commands & Permissions

Currently, the plugin operates automatically without commands. Admin commands (like force reset) are planned for future updates.

*   `hardcoreworldreset.admin` - Default: `OP` (for future admin features).
*   `hardcoreworldreset.bypass` - Default: `false` (Bypass death resets).

---

## 🏗️ Building from Source

```bash
git clone https://github.com/kvmhl/hardcore-world-reset.git
cd hardcore-world-reset
.\build.bat
```
The artifact will be created in `target/`.
