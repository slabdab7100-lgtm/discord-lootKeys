# Loot Key Discord

A RuneLite plugin that detects the Wilderness Loot Key interface, calculates the total Grand Exchange value of the key contents, captures the game canvas, and uploads the screenshot to a Discord webhook when the configured minimum value is reached.

## Configuration

- **Enable plugin** — turns sending on or off.
- **Minimum key value** — minimum total GP value required. Set to `0` to send every Loot Key.
- **Discord webhook** — the Discord webhook used for the upload.

## Third-party data disclosure

This plugin communicates with Discord. When a qualifying Loot Key is opened, the plugin sends a screenshot of the RuneLite game canvas and the calculated total key value to the configured Discord webhook. The webhook URL is stored as a secret RuneLite configuration value and is not logged by the plugin.

## Valuation

Key value is calculated using RuneLite's current item prices for the items in the PvP Loot Key containers. Items without a positive price contribute zero to the calculated value.
