# Discord Loot Keys

RuneLite plugin that watches for PvP Loot Keys, calculates their total Grand Exchange value, captures the next game frame, and sends qualifying keys to a configured Discord webhook.

## Configuration

- **Enable plugin** — enables automatic sending.
- **Minimum key value** — total GP value required before a screenshot is sent. The default is 1,000,000 GP. Set it to 0 to send every key.
- **Discord webhook URL** — the Discord webhook that receives qualifying screenshots and the total key value.

The plugin only accepts Discord webhook URLs. Screenshots and the calculated total value are sent to the configured Discord webhook.

## Valuation

PvP Loot Keys use RuneLite's four current Deadman loot containers. Each item's current RuneLite price is multiplied by its quantity and summed to determine the threshold value.
