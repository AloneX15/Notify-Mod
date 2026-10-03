# Server templates

Templates define notification content and channels; presentations define visuals. Servers read `data/`, clients read
`assets/`. Keep server logic in datapacks and visuals in resource packs.

## Create a template

In a datapack compatible with your Minecraft version, create `data/mypack/notify/templates/welcome.json`:

```json
{
  "format_version": 1,
  "channels": ["showcase", "chat"],
  "presentation": "notifymod:motd",
  "placement": "center",
  "priority": "NORMAL",
  "message": "Welcome, {player}",
  "key": "welcome",
  "cooldown": "10s",
  "duration": "6s"
}
```

```mcfunction
/reload
/notify validate
/notify showcase mypack:welcome player=Alex
```

The file path determines its id; an `id` field inside JSON does not override it. Managed files such as
`config/notifymod/managed/templates/mypack/welcome.json` override datapacks after reload.

## Supported fields

| Field | Behavior |
|---|---|
| `format_version` | 1 |
| `channels` | Nonempty list of `chat`, `hud`, `showcase`; defaults to `showcase` |
| `presentation` | Id; defaults to `notifymod:motd` |
| `placement` | Seven placements; defaults to center for Showcase, top-left for HUD |
| `priority` | LOW, NORMAL, HIGH, CRITICAL; CRITICAL always uses center |
| `message` | Required: string, `{"text":"…"}`, or `{"translate":"key","with":["{arg}"]}` |
| `mirror_to_chat` | `true` adds chat |
| `key` | Optional deduplication/cancellation key, up to 64 characters |
| `cooldown` | Delay between template sends through the `showcase` command |
| `duration` | `"6s"` or milliseconds; 500 ms–120 s; loaded visual presentations override this duration |

Arguments are plain text: up to 16 names using `[a-z0-9_]` (1–32 characters) and values up to 256 characters.
Translation keys must exist in clients' resource packs. The shipped `notifymod:restart_warning` uses
`notifymod.restart.warning` with the `minutos` argument.

Template cooldowns apply to command sends; triggers use their own limits. Planned template fields `audience`,
`fallback` and custom permissions are **not interpreted yet**. `message` provides the current fallback when visuals
are missing; use trigger audiences to filter recipients.

For custom visuals, create `assets/mypack/notify/presentations/welcome.json`, reference it in `presentation`, and
distribute its resource pack to every player. See [presentations](presentations.md).
