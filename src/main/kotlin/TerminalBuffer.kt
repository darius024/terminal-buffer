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
        for (ch in text) {
            if (charDisplayWidth(ch) == 2) putWideChar(ch) else putNarrowChar(ch)
        }
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
        return lineToString(line)
    }

    fun getScreenContent(): String =
        (scrollbackSize until scrollbackSize + height)
            .joinToString("\n") { getLine(it) }

    fun getAllContent(): String =
        (0 until scrollbackSize + height)
            .joinToString("\n") { getLine(it) }

    // -- Internal helpers -----------------------------------------------------

    companion object {
        const val CONTINUATION = '\u0000'
    }

    private fun lineToString(line: Array<Cell>): String {
        val sb = StringBuilder()
        for (cell in line) {
            if (cell.char != CONTINUATION) sb.append(cell.char)
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

    private fun blankLine(): Array<Cell> = Array(width) { Cell() }

    private fun putNarrowChar(ch: Char) {
        if (cursorRow >= height) scrollUp()
        clearPartialWideChar(cursorCol, cursorRow)
        screen[cursorRow][cursorCol] = Cell(ch, currentAttributes)
        advanceCursor(1)
    }

    private fun putWideChar(ch: Char) {
        if (cursorRow >= height) scrollUp()
        if (cursorCol == width - 1) {
            screen[cursorRow][cursorCol] = Cell()
            advanceCursor(1)
            if (cursorRow >= height) scrollUp()
        }
        clearPartialWideChar(cursorCol, cursorRow)
        clearPartialWideChar(cursorCol + 1, cursorRow)
        screen[cursorRow][cursorCol] = Cell(ch, currentAttributes)
        screen[cursorRow][cursorCol + 1] = Cell(CONTINUATION, currentAttributes)
        advanceCursor(2)
    }

    /** Raw cell placement for insertText tail replay. */
    private fun putCell(cell: Cell) {
        if (cursorRow >= height) scrollUp()
        screen[cursorRow][cursorCol] = cell
        advanceCursor(1)
    }

    private fun advanceCursor(n: Int) {
        cursorCol += n
        if (cursorCol >= width) {
            cursorCol = 0
            cursorRow++
        }
        if (cursorRow >= height) scrollUp()
    }

    /** If the cell at (col, row) is part of a wide character, clear the other half. */
    private fun clearPartialWideChar(col: Int, row: Int) {
        if (col < 0 || col >= width || row < 0 || row >= height) return
        val cell = screen[row][col]
        if (cell.char == CONTINUATION && col > 0) {
            screen[row][col - 1] = Cell()
        } else if (charDisplayWidth(cell.char) == 2 && col + 1 < width) {
            screen[row][col + 1] = Cell()
        }
    }

    private fun scrollUp() {
        scrollback.addLast(screen[0])
        if (scrollback.size > maxScrollback) scrollback.removeFirst()
        for (i in 1 until height) screen[i - 1] = screen[i]
        screen[height - 1] = blankLine()
        cursorRow = cursorRow.coerceAtMost(height - 1)
    }
}

/** Display width of a character: 2 for CJK / fullwidth, 1 otherwise. */
fun charDisplayWidth(ch: Char): Int = when (ch.code) {
    in 0x1100..0x115F,
    in 0x2E80..0x303F,
    in 0x3040..0x30FF,
    in 0x3100..0x312F,
    in 0x3130..0x318F,
    in 0x3200..0x33FF,
    in 0x3400..0x4DBF,
    in 0x4E00..0x9FFF,
    in 0xAC00..0xD7AF,
    in 0xF900..0xFAFF,
    in 0xFE30..0xFE4F,
    in 0xFF01..0xFF60,
    in 0xFFE0..0xFFE6 -> 2
    else -> 1
}
