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
    val screen: Array<Array<Cell>> = Array(height) { blankLine() }

    private fun blankLine(): Array<Cell> = Array(width) { Cell() }

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

    var currentAttributes: CellAttributes = CellAttributes()
        private set

    fun setAttributes(
        foreground: Colour = Colour.DEFAULT,
        background: Colour = Colour.DEFAULT,
        styles: Set<StyleFlag> = emptySet(),
    ) {
        currentAttributes = CellAttributes(foreground, background, styles)
    }

    fun writeText(text: String) {
        for (ch in text) {
            if (cursorRow >= height) scrollUp()
            screen[cursorRow][cursorCol] = Cell(ch, currentAttributes)
            cursorCol++
            if (cursorCol >= width) {
                cursorCol = 0
                cursorRow++
            }
        }
        if (cursorRow >= height) scrollUp()
    }

    /**
     * Inserts text at the cursor, shifting existing content to the right.
     * Overflow beyond the line width wraps to the next line.
     */
    fun insertText(text: String) {
        if (text.isEmpty()) return

        // Collect the tail of the current line (from cursor to end)
        val tail = mutableListOf<Cell>()
        for (col in cursorCol until width) tail.add(screen[cursorRow][col])

        // Write the new text at the cursor (overwrites in place, wraps/scrolls)
        val savedRow = cursorRow
        writeText(text)

        // Re-insert the saved tail at the cursor's new position
        val insertCol = cursorCol
        val insertRow = cursorRow
        for (cell in tail) {
            if (cursorRow >= height) scrollUp()
            screen[cursorRow][cursorCol] = cell
            cursorCol++
            if (cursorCol >= width) {
                cursorCol = 0
                cursorRow++
            }
        }
        if (cursorRow >= height) scrollUp()

        // Restore cursor to just after the inserted text
        cursorCol = insertCol
        cursorRow = insertRow
    }

    fun fillLine(ch: Char) {
        val cell = Cell(ch, currentAttributes)
        for (col in 0 until width) screen[cursorRow][col] = cell
    }

    /** Shifts all screen rows up by one, discarding the top row. */
    private fun scrollUp() {
        for (i in 1 until height) screen[i - 1] = screen[i]
        screen[height - 1] = blankLine()
        cursorRow = height - 1
    }
}
