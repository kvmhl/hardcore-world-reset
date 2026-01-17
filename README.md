# HardcoreWorldReset

A lightweight, powerful plugin that resets the world whenever a player dies in Hardcore mode. Ideal for speedrunning, challenges, and "You Die, World Resets" servers.

## Features

- **Instant World Resets**: Automatically generates a fresh new world (Overworld, Nether, The End) upon death.
- **Smart World Management**: Handles complete world lifecycles without any external dependencies like Multiverse.
- **Speedrun Timer**: Integrated live timer in the player list (Tab) to track run duration.
- **End Goal Detection**: Detects Ender Dragon kills to complete the run and announce final time.
- **Seamless & Kick Modes**: Choose between "Seamless" teleportation (pre-generating worlds) or "Disconnect" (kick on death) logic.
- **Advance Revocation**: Automatically clears player advancements on reset for a fresh start.

## Installation

1.  Download the latest `HardcoreWorldReset-x.x.x.jar` from Releases.
2.  Drop it into your server's `plugins/` folder.
3.  Ensure `hardcore=true` is set in your `server.properties`.
4.  Restart the server.

> **Note**: This plugin requires **Java 21**.

## Configuration

The plugin is ready to go out of the box, but highly configurable via `config.yml`:

```yaml
# Toggle plugin functionality
plugin-enabled: true

# Naming convention for generated world folders
world-prefix: "hardcore_"

# How to handle world changes: "SEAMLESS" (teleport) or "DISCONNECT" (kick)
swap-method: "DISCONNECT"

# Gameplay settings
announce-deaths: true
auto-start-timer: true
min-players-to-start: 1
```

## Building from Source

To build this project locally, ensure you have JDK 21 installed.

```bash
git clone https://github.com/kvmhl/hardcore-world-reset.git
cd hardcore-world-reset
.\build.bat
```

The artifact will be located in `target/HardcoreWorldReset-2.0.0.jar`.
