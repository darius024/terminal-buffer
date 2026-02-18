# Terminal Buffer

A terminal text buffer implementation in Kotlin — the core data structure that
terminal emulators use to store and manipulate displayed text.

When a shell sends output, the terminal emulator updates this buffer, and the
UI renders it. This project implements that buffer layer without any external
dependencies.

## What it does

The buffer manages a grid of character cells, each carrying a character, colours
(foreground/background from the 16 standard terminal colours), and style flags
(bold, italic, underline). A cursor tracks where the next write will land.

The buffer has two regions:

- **Screen** — the visible area (e.g. 80×24). Editable, rendered to the user.
- **Scrollback** — lines that scrolled off the top, preserved as read-only
  history with a configurable maximum size.

### Supported operations

- **Cursor** — get/set position, move in four directions with bounds clamping.
- **Attributes** — set foreground colour, background colour, and style flags
  that apply to subsequent writes.
- **Editing** — write text (overwrite), insert text (shift existing content),
  fill a line with a character, insert blank lines, clear screen, clear all.
- **Content access** — read characters, attributes, or full lines from both the
  screen and scrollback regions.
- **Wide characters** — correct handling of characters that occupy two cells
  (CJK ideographs, emoji).
- **Resize** — change screen dimensions at runtime.

## Architecture

The implementation is split into two source files:

- **`CellModel.kt`** — the immutable data model. Defines `Colour` (17 standard
  ANSI values), `StyleFlag` (bold, italic, underline), `CellAttributes`
  (foreground + background + styles) and `Cell` (a character with its
  attributes). Also provides `charDisplayWidth` for wide-character detection.
- **`TerminalBuffer.kt`** — the mutable buffer itself: screen grid, scrollback
  history, cursor, attribute state, and all editing / access operations.

### Data model

Every position in the grid is a `Cell`: an immutable data class holding a `Char`
and a `CellAttributes`. The screen is a `Array<Array<Cell>>` — a row-major grid
where each row is a fixed-width array. Scrollback is an `ArrayDeque<Array<Cell>>`
acting as a bounded FIFO queue.

Immutable cells mean that writing a character always creates a fresh `Cell`
instance rather than mutating in place. This keeps the model simple, avoids
aliasing bugs, and makes scrollback lines safely shareable.

### Coordinate system

Content access uses **unified row coordinates** across scrollback and screen:

```
row 0                    → oldest scrollback line
row scrollbackSize - 1   → newest scrollback line
row scrollbackSize       → top screen line
row scrollbackSize + height - 1 → bottom screen line
```

This lets `getChar`, `getAttributes`, `getLine`, and `getAllContent` address the
entire history with a single integer row index.

### Cursor and bounds clamping

The cursor is always within `[0, width)` × `[0, height)`. Every `setCursor` and
`moveCursor*` call clamps the result to the screen bounds — no exceptions, no
error codes, just silent clamping. This mirrors how real terminals behave: a
cursor movement request beyond the edge stops at the edge.

### Writing and auto-scroll

`writeText` overwrites cells left-to-right from the cursor, advancing by one
column per narrow character and two per wide character. When the cursor passes
the right edge, it wraps to column 0 of the next row. When it passes the bottom
edge, the screen scrolls up: the top row is pushed to scrollback, every row
shifts up by one, and a blank row appears at the bottom. This can happen
multiple times for long strings that span many lines.

`insertText` is more complex: it captures the tail of the current line from the
cursor onwards, writes the new text (which may wrap and scroll), then replays
the captured tail cells. The cursor is restored to the position just after the
inserted text.

### Wide character handling

Characters with a display width of 2 (CJK ideographs, fullwidth punctuation)
occupy two adjacent cells. The left cell stores the actual character; the right
cell stores a `'\u0000'` continuation marker. When a wide character would land
on the last column (only one cell available), that column is blanked and the
character wraps to the start of the next line.

Overwriting either half of a wide character clears the other half to a space,
preventing orphaned continuation markers or headless wide characters.
`lineToString` skips continuation markers to produce the correct string output.

### Resize strategy

`resize` handles four independent concerns:

- **Width change** — each line is truncated or padded with blank cells.
- **Height shrink** — excess top rows are pushed to scrollback.
- **Height grow** — lines are pulled back from scrollback if available, otherwise
  blank rows are appended.
- **Cursor** — clamped to the new bounds after resizing.

This is a pragmatic trade-off: it does not attempt to re-flow wrapped lines
(which would require tracking soft vs hard line breaks), but it preserves
content as faithfully as possible.

## Build

Requires a JDK (21+) on the `PATH` or via `JAVA_HOME`.

```
./gradlew build
```

## Test

```
./gradlew test
```
