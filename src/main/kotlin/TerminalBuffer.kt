/*
 * Terminal Text Buffer
 *
 * Core data structure for a terminal emulator: a grid of character cells with
 * colours, styles, a cursor, a visible screen region and a scrollback history.
 */

enum class Colour {
    DEFAULT,
    BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE,
    BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
    BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE,
}

enum class StyleFlag {
    BOLD, ITALIC, UNDERLINE,
}

data class CellAttributes(
    val foreground: Colour = Colour.DEFAULT,
    val background: Colour = Colour.DEFAULT,
    val styles: Set<StyleFlag> = emptySet(),
)

data class Cell(
    val char: Char = ' ',
    val attributes: CellAttributes = CellAttributes(),
)

class TerminalBuffer(
    val width: Int,
    val height: Int,
    val maxScrollback: Int = 1000,
) {
    val screen: Array<Array<Cell>> = Array(height) { Array(width) { Cell() } }

    var cursorCol: Int = 0
        private set
    var cursorRow: Int = 0
        private set

    fun setCursor(col: Int, row: Int) {
        cursorCol = col.coerceIn(0, width - 1)
        cursorRow = row.coerceIn(0, height - 1)
    }

    fun moveCursorUp(n: Int)    { cursorRow = (cursorRow - n).coerceAtLeast(0) }
    fun moveCursorDown(n: Int)  { cursorRow = (cursorRow + n).coerceAtMost(height - 1) }
    fun moveCursorLeft(n: Int)  { cursorCol = (cursorCol - n).coerceAtLeast(0) }
    fun moveCursorRight(n: Int) { cursorCol = (cursorCol + n).coerceAtMost(width - 1) }
}
