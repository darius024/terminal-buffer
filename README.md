# Terminal Buffer

A terminal text buffer implementation in Kotlin — the core data structure that
terminal emulators use to store and manipulate displayed text.

When a shell sends output, the terminal emulator updates this buffer, and the
UI renders it. This project implements that buffer layer without any external
dependencies.

## What it does

The buffer manages a grid of character cells, each carrying a character, colors
(foreground/background from the 16 standard terminal colors), and style flags
(bold, italic, underline). A cursor tracks where the next write will land.

The buffer has two regions:

- **Screen** — the visible area (e.g. 80×24). Editable, rendered to the user.
- **Scrollback** — lines that scrolled off the top, preserved as read-only
  history with a configurable maximum size.

### Supported operations

- **Cursor** — get/set position, move in four directions with bounds clamping.
- **Attributes** — set foreground color, background color, and style flags that
  apply to subsequent writes.
- **Editing** — write text (overwrite), insert text (shift existing content),
  fill a line with a character, insert blank lines, clear screen, clear all.
- **Content access** — read characters, attributes, or full lines from both the
  screen and scrollback regions.
- **Wide characters** — correct handling of characters that occupy two cells
  (CJK ideographs, emoji).
- **Resize** — change screen dimensions at runtime.

## Build

Requires a JDK (21+) on the `PATH` or via `JAVA_HOME`.

```
./gradlew build
```

## Test

```
./gradlew test
```
