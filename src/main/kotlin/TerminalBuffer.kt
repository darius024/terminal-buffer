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
        cursorCol = col
        cursorRow = row
    }

    fun moveCursorUp(n: Int) {}
    fun moveCursorDown(n: Int) {}
    fun moveCursorLeft(n: Int) {}
    fun moveCursorRight(n: Int) {}
}
