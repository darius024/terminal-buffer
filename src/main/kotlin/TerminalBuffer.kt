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

    // -- Cursor ---------------------------------------------------------------

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

    // -- Attributes -----------------------------------------------------------

    var currentAttributes: CellAttributes = CellAttributes()
        private set

    fun setAttributes(
        foreground: Colour = Colour.DEFAULT,
        background: Colour = Colour.DEFAULT,
        styles: Set<StyleFlag> = emptySet(),
    ) {
        currentAttributes = CellAttributes(foreground, background, styles)
    }

    // -- Editing --------------------------------------------------------------

    fun writeText(text: String) {
        for (ch in text) putCell(Cell(ch, currentAttributes))
    }

    /**
     * Inserts text at the cursor, shifting existing content to the right.
     * Overflow beyond the line width wraps to the next line.
     */
    fun insertText(text: String) {
        if (text.isEmpty()) return

        val tail = screen[cursorRow].slice(cursorCol until width)
        writeText(text)

        val resumeCol = cursorCol
        val resumeRow = cursorRow
        for (cell in tail) putCell(cell)

        cursorCol = resumeCol
        cursorRow = resumeRow
    }

    fun fillLine(ch: Char) {
        val cell = Cell(ch, currentAttributes)
        for (col in 0 until width) screen[cursorRow][col] = cell
    }

    // -- Line operations ------------------------------------------------------

    /** Shifts all rows up, pushes the top row to scrollback, blank row at bottom. */
    fun insertLine() {
        scrollUp()
    }

    fun clearScreen() {
        for (row in 0 until height) screen[row] = blankLine()
        cursorCol = 0
        cursorRow = 0
    }

    fun clearAll() {
        clearScreen()
        scrollback.clear()
    }

    // -- Scrollback -----------------------------------------------------------

    val scrollback: ArrayDeque<Array<Cell>> = ArrayDeque()

    val scrollbackSize: Int get() = scrollback.size

    // -- Content access -------------------------------------------------------
    //
    // Unified row coordinates: row 0 = oldest scrollback line,
    // row scrollbackSize = top screen line.

    fun getChar(col: Int, row: Int): Char = cellAt(col, row)?.char ?: ' '

    fun getAttributes(col: Int, row: Int): CellAttributes =
        cellAt(col, row)?.attributes ?: CellAttributes()

    fun getLine(row: Int): String {
        val line = lineAt(row) ?: return ""
        return String(CharArray(width) { line[it].char })
    }

    fun getScreenContent(): String =
        (0 until height).joinToString("\n") { row ->
            String(CharArray(width) { col -> screen[row][col].char })
        }

    fun getAllContent(): String {
        val sb = StringBuilder()
        val totalRows = scrollbackSize + height
        for (i in 0 until totalRows) {
            if (i > 0) sb.append('\n')
            sb.append(getLine(i))
        }
        return sb.toString()
    }

    private fun cellAt(col: Int, row: Int): Cell? {
        val line = lineAt(row) ?: return null
        if (col < 0 || col >= width) return null
        return line[col]
    }

    private fun lineAt(row: Int): Array<Cell>? {
        if (row < 0) return null
        if (row < scrollbackSize) return scrollback[row]
        val screenRow = row - scrollbackSize
        if (screenRow >= height) return null
        return screen[screenRow]
    }

    // -- Internal helpers -----------------------------------------------------

    private fun blankLine(): Array<Cell> = Array(width) { Cell() }

    /** Places a cell at the cursor, advances right, wraps and scrolls as needed. */
    private fun putCell(cell: Cell) {
        if (cursorRow >= height) scrollUp()
        screen[cursorRow][cursorCol] = cell
        cursorCol++
        if (cursorCol >= width) {
            cursorCol = 0
            cursorRow++
        }
        if (cursorRow >= height) scrollUp()
    }

    private fun scrollUp() {
        scrollback.addLast(screen[0])
        if (scrollback.size > maxScrollback) scrollback.removeFirst()
        for (i in 1 until height) screen[i - 1] = screen[i]
        screen[height - 1] = blankLine()
        cursorRow = cursorRow.coerceAtMost(height - 1)
    }
}
