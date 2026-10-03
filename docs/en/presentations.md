# Presentations and media (format v1)

A presentation is a client-side timeline. Create it in a resource pack at
`assets/<namespace>/notify/presentations/<name>.json`. Its id is `<namespace>:<name>`. F3+T reloads presentations;
`/notify showcase <id> message="…"` plays them. Missing visuals fall back to the message text.

## A working text example

```json
{
  "format_version": 1,
  "duration": 7000,
  "background": { "type": "dim", "color": "#220000", "opacity": 0.6 },
  "letterbox": { "height": 0.1 },
  "tracks": [
    { "type": "sound", "time": 0, "sound": "minecraft:block.note_block.bell", "pitch": 0.8 },
    {
      "type": "text", "time": 500, "content": "{message}", "color": "#FF4040", "scale": 2.5,
      "anchor": "center", "offset": [0, 0.18],
      "animation_in": { "type": "slide_right", "easing": "ease_out_expo", "duration": 500 },
      "animation_out": { "type": "fade", "duration": 400 }
    }
  ],
  "docked": { "omit": [], "mute": false }
}
```

Save as `assets/mypack/notify/presentations/event.json` and run:

```mcfunction
/notify showcase mypack:event message="EVENT STARTS"
/notify showcase mypack:event message="Compact" placement=bottom_right
```

## Presentation fields

| Field | Behavior |
|---|---|
| `format_version` | 1 |
| `duration` | Milliseconds or `"7s"`; 500 ms–120 s; overrides the server notification duration |
| `background` | Optional `dim` background with `color`, `opacity` |
| `letterbox` | Cinema bars, `height` up to 0.25 and optional `color` |
| `tracks` | Up to 64 tracks |
| `preload` | `true` decodes images on resource reload; default `false` |
| `docked` | Compact layout: omit tracks by `name`, optionally `mute` audio |
| `_comment` | Free-form comment |

Background and cinema bars never render in compact placements.

## Track fields

| Field | Applies to | Behavior |
|---|---|---|
| `type` | All | `text`, `texture`, `shape`, `model`, `sound` |
| `time`, `duration` | All | Start and duration in milliseconds; duration defaults to remaining timeline |
| `name` | All | Track identifier for `docked.omit` |
| `z` | Visual | Higher values render above lower values |
| `anchor` | Visual | `center`, `top`, `top_left`, `top_right`, `left`, `right`, `bottom`, `bottom_left`, `bottom_right` |
| `offset` | Visual | `[x,y]` fractions of viewport width and height |
| `size` | Texture, shape, model | `[width,height]` fractions of viewport **height**; texture height `0` preserves aspect ratio |
| `opacity` | Visual | 0–1 |
| `animation_in`, `animation_out`, `loop` | Visual | Animation objects or short string forms |
| `content` | Text | Text with placeholders, translated object, or `{message}` |
| `color`, `scale`, `shadow`, `max_width` | Text | Color, scale, shadow and wrapping width |
| `file` | Texture | Resource id of PNG, GIF, WebP or Lottie JSON |
| `color`, `color2` | Shape | Solid color or vertical gradient |
| `item` or `entity` | Model | Item/block id or entity id, one per track |
| `sound`, `volume`, `pitch` | Sound | Sound id, volume 0–1, pitch 0.5–2 |

## Animation

Entrance/exit types: `fade`, `slide_left`, `slide_right`, `slide_up`, `slide_down`, `scale`, `pop`, `rotate`,
`typewriter`. Loops: `shake`, `pulse`, `spin`; loop periods are in milliseconds.
`"loop":"spin"` is shorthand for `{ "type":"spin" }` with defaults.

Easings: `linear`; `ease_in_*`, `ease_out_*`, `ease_in_out_*` for `sine`, `quad`, `cubic`, `quart`, `quint`,
`expo`, `circ`, `back`, `elastic`, `bounce`; aliases `ease_in`, `ease_out`, `ease_in_out`; and
`cubic_bezier(x1,y1,x2,y2)`. `amount` controls distance, initial scale or rotation for applicable animations.

## Spritesheets and models

Place `image.png.mcmeta` beside its PNG:

```json
{ "animation": { "frametime": 2, "width": 16, "height": 16, "frames": [0, 1, { "index": 2, "time": 10 }] } }
```

Frames are cut left-to-right, then top-to-bottom. Default frame size is the sheet's smaller dimension, default
`frametime` is one tick (50 ms), and default order includes every frame. Sheet dimensions can reach 8192 px; each
frame remains limited to 1024 px. Interpolation is unsupported; `.mcmeta` does not apply to GIF/WebP/Lottie.

```json
{ "type": "model", "time": 0, "entity": "minecraft:zombie", "anchor": "center", "size": [0.3, 0.3], "loop": "spin" }
```

Items and blocks render like inventory models. Mobs are local copies, never spawned into the world. Model transparency
is unsupported; visibility switches above 0.35 opacity. A failed model disables only that model.

## Limits and current scope

The client clock pauses with singleplayer pause and is independent of frame rate. Images decode off the render
thread and share a 128 MB cache budget. Per-file limits: 8 MB, 1024×1024 and 512 frames. `media_quality=low` reduces
images to 256 px and keeps every other animation frame while preserving duration.

WebP uses TwelveMonkeys. Lottie implements a documented subset, not arbitrary After Effects exports;
see the [media decision](../adr/0001-formatos-animados-fase-0.md). Unsupported fields warn; invalid tracks or resources
discard the presentation. Custom fonts, vignette, safe mode and a flash limiter remain pending.

Test presentations and sample media under `src/gametest` are not included in the normal release jar.
