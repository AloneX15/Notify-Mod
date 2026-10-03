# Configuration and permissions

Files are created in `config/notifymod/`. Back them up before editing. Numeric values outside supported ranges are
normalized; corrupt JSON falls back to defaults and attempts to preserve a `.bak` copy.

## Server: `server.json`

| Field | Default | Range or behavior |
|---|---|---|
| `format_version` | `1` | Format metadata |
| `require_client` | `true` | Reject missing/incompatible clients |
| `missing_client_message` | Empty | Custom message, up to 512 characters |
| `download_url` | GitHub releases URL | Up to 256 characters |
| `default_mirror_to_chat` | `false` | Chat copy for sends without a template |
| `max_commands_per_minute` | `30` | 1–600 player actions; shared between sends and trigger commands |

Run `/notify reload` after editing. Templates use their own channels, without automatically inheriting
`default_mirror_to_chat`. Console commands do not use the player command limiter; trigger-wide limits still apply.

## Client: `client.json`

| Field | Default | Range or behavior |
|---|---|---|
| `format_version` | `1` | Format metadata |
| `hide_corner_notifications` | `false` | Hide every placement except center |
| `card_scale` | `1.0` | 0.5–2.0 |
| `max_visible_cards` | `3` | 1–8 |
| `media_quality` | `high` | `low` reduces resolution and frames |

Restart the client after manual edits. The corner-toggle key changes that setting during play. A custom settings
screen, safe mode, flash limiter and full accessibility controls remain planned; prefer gentle animations without flashing.

## Permission reference

The implementation uses Fabric API 26.x's included permission API. Internal ids such as `notifymod:command` map to
`notifymod.command` in LuckPerms; without a provider, checks fall back to operator level. The legacy
`me.lucko:fabric-permissions-api` library is not bundled. LuckPerms integration has not yet been tested on a real server.

| LuckPerms node | OP | Action |
|---|---|---|
| `notifymod.command` | 2 | General access, required alongside action-specific nodes |
| `notifymod.send`, `notifymod.hud`, `notifymod.showcase` | 2 | Send through each channel |
| `notifymod.showcase.placement` | 2 | Override Showcase placement |
| `notifymod.priority.high` | 2 | HIGH priority |
| `notifymod.priority.critical` | 3 | CRITICAL priority |
| `notifymod.cancel`, `notifymod.clear` | 2 | Cancel |
| `notifymod.reload` | 3 | Reload |
| `notifymod.validate` | 2 | Diagnostics |
| `notifymod.trigger.list`, `notifymod.trigger.info` | 2 | Read; `audience` uses `.info` |
| `notifymod.trigger.manage`, `.test`, `.fire` | 3 | Manage, preview and fire |
| `notifymod.receive.chat`, `.hud`, `.showcase` | Everyone | Receive each channel |
| `notifymod.receive.<namespace>.<template>` | Everyone | Template reception through triggers |
| `notifymod.bypass.trigger.<namespace>.<trigger>` | Nobody | Trigger exemption, not granted by OP |

The shipped death trigger uses `notifymod.bypass.trigger.notifymod.player_death`; its template reception node is
`notifymod.receive.notifymod.player_death`. The repeated namespace reflects the current implementation. Template
reception nodes do not yet apply to the `showcase` command.

## Persistence

Trigger overrides live in `triggers_state.json`; managed definitions remain under `managed/triggers/`.
`audit.jsonl` records actor, action and timestamp, rather than every automatic event. Writes use a serialized queue
of 256 tasks. Busy queues reject edits before applying them; rejected audit entries produce a log warning.
