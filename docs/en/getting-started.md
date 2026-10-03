# Getting started

## Installation

1. Install Fabric Loader and Fabric API for Minecraft **26.1.2, 26.2 or 26.3**, using Java 25.
2. Download the matching jar from [GitHub releases](https://github.com/AloneX15/Notify-Mod/releases).
3. Install it in the server's and each client's `mods/` directory. By default, the server rejects clients missing
   Notify Mod or its compatible protocol. The `dev` release is a development build, not a stable release.
4. Start the game/server and run `/notify validate` as an operator.

Builds and server gametests have passed locally on all three versions. The latest local client check is on 26.3;
CI checks clients and the compat pack per version. LuckPerms still needs an integration test.

## First notification

With OP 2:

```mcfunction
/notify send Welcome to the server
/notify hud Restart in 5 minutes placement=top_right chat=true
/notify showcase notifymod:motd message="The event starts now" duration=10s
/notify showcase notifymod:restart_warning minutos=5
/notify clear
```

`send` writes to chat; `hud` displays a card; `showcase` plays a presentation or template. The built-in
`notifymod:motd` only needs text. Current send commands target all connected players; filtered audiences are available
for [triggers](triggers.md).

Options use `key=value`; quote values containing spaces. Placements: `center`, `top_left`, `top_right`, `bottom_left`,
`bottom_right`, `top`, `bottom`. Priorities: `low`, `normal`, `high`, `critical`. HIGH requires OP 2; CRITICAL requires
OP 3 and always uses the center.

## Available commands

| Command | Purpose |
|---|---|
| `/notify send <message>` | Chat |
| `/notify hud <message> [options]` | HUD card |
| `/notify showcase <id> [options] [arg=value]` | Presentation or template |
| `/notify cancel <key>` | Cancel by key; `all` cancels everything |
| `/notify clear` | Clear connected clients' queues |
| `/notify reload` | Reload server settings, templates and triggers |
| `/notify validate` | Report template/trigger errors; does not validate client visual resources |
| `/notify trigger …` | [Manage triggers](triggers.md) |
| `/notify help` | Command help |

HUD/showcase options: `placement`, `priority`, `key`, `duration`, `chat`, `message`.
`/notify gui`, `/notify var`, `/notify schedule` and `/notify scene` are not implemented.

## Player controls and troubleshooting

Bind **Hide corner notifications** in Controls → Notify Mod; it is unbound by default. This hides every placement
except the center, including `top` and `bottom`. Central and CRITICAL notices remain visible.

If a message is missing, use `message="…"` for presentations without templates. Check `/notify validate` for
server content errors, and F3+T plus the client log for visual errors. Distribute the resource pack to every player.
Death delivery waits for respawn and expires after 60 seconds; check activation, audience, permissions and cooldowns.
For rejected clients, install the matching mod and Fabric API. Setting `require_client=false` permits chat fallback
for unmodded clients but does not give them visual notifications. See [configuration](configuration.md).
