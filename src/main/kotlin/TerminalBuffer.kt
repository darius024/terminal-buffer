/*
 * Terminal Text Buffer
 *
 * Core data structure for a terminal emulator: a grid of character cells with
 * colours, styles, a cursor, a visible screen region and a scrollback history.
 */

class TerminalBuffer(
    width: Int,
    height: Int,
    val maxScrollback: Int = 1000,
) {
    init {
        require(width >= 1) { "width must be at least 1" }
        require(height >= 1) { "height must be at least 1" }
        require(maxScrollback >= 0) { "maxScrollback must not be negative" }
    }

    var width: Int = width
        private set
    var height: Int = height
        private set
    var screen: Array<Array<Cell>> = Array(height) { blankLine() }
        private set

    // -- Cursor ---------------------------------------------------------------

    var cursorCol: Int = 0
        private set
    var cursorRow: Int = 0
        private set

    fun setCursor(col: Int, row: Int) {
        cursorCol = col.coerceIn(0, width - 1)
        cursorRow = row.coerceIn(0, height - 1)
    }

    fun moveCursorUp(steps: Int)    { cursorRow = (cursorRow - steps).coerceAtLeast(0) }
    fun moveCursorDown(steps: Int)  { cursorRow = (cursorRow + steps).coerceAtMost(height - 1) }
    fun moveCursorLeft(steps: Int)  { cursorCol = (cursorCol - steps).coerceAtLeast(0) }
    fun moveCursorRight(steps: Int) { cursorCol = (cursorCol + steps).coerceAtMost(width - 1) }

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
        for (character in text) {
            if (charDisplayWidth(character) == 2) putWideChar(character) else putNarrowChar(character)
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

    fun fillLine(character: Char) {
        val cell = Cell(character, currentAttributes)
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

    private val scrollback: ArrayDeque<Array<Cell>> = ArrayDeque()

    val scrollbackSize: Int get() = scrollback.size

    fun getScrollbackLine(index: Int): Array<Cell> = scrollback[index]

    // -- Resize ---------------------------------------------------------------

    fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth >= 1) { "width must be at least 1" }
        require(newHeight >= 1) { "height must be at least 1" }
        if (newWidth == width && newHeight == height) return

        val oldScreen = screen.map { resizeLine(it, newWidth) }
        val oldHeight = height
        width = newWidth
        height = newHeight

        screen = if (newHeight <= oldHeight) {
            val excess = oldHeight - newHeight
            for (row in 0 until excess) pushToScrollback(oldScreen[row])
            Array(newHeight) { oldScreen[it + excess] }
        } else {
            val extra = newHeight - oldHeight
            val fromScrollback = minOf(extra, scrollbackSize)
            val pulled = ArrayDeque<Array<Cell>>()
            repeat(fromScrollback) { pulled.addFirst(resizeLine(scrollback.removeLast(), newWidth)) }
            Array(newHeight) { row ->
                when {
                    row < fromScrollback -> pulled[row]
                    row < fromScrollback + oldHeight -> oldScreen[row - fromScrollback]
                    else -> blankLine()
                }
            }
        }

        cursorCol = cursorCol.coerceIn(0, width - 1)
        cursorRow = cursorRow.coerceIn(0, height - 1)
    }

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
        val builder = StringBuilder()
        for (cell in line) {
            if (cell.char != CONTINUATION) builder.append(cell.char)
        }
        return builder.toString()
    }

    private fun cellAt(col: Int, row: Int): Cell? {
        val line = lineAt(row) ?: return null
        if (col < 0 || col >= line.size) return null
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

    private fun putNarrowChar(character: Char) {
        if (cursorRow >= height) scrollUp()
        clearPartialWideChar(cursorCol, cursorRow)
        screen[cursorRow][cursorCol] = Cell(character, currentAttributes)
        advanceCursor(1)
    }

    private fun putWideChar(character: Char) {
        if (cursorRow >= height) scrollUp()
        if (cursorCol == width - 1) {
            screen[cursorRow][cursorCol] = Cell()
            advanceCursor(1)
            if (cursorRow >= height) scrollUp()
        }
        clearPartialWideChar(cursorCol, cursorRow)
        clearPartialWideChar(cursorCol + 1, cursorRow)
        screen[cursorRow][cursorCol] = Cell(character, currentAttributes)
        screen[cursorRow][cursorCol + 1] = Cell(CONTINUATION, currentAttributes)
        advanceCursor(2)
    }

    /** Raw cell placement for insertText tail replay. */
    private fun putCell(cell: Cell) {
        if (cursorRow >= height) scrollUp()
        screen[cursorRow][cursorCol] = cell
        advanceCursor(1)
    }

    private fun advanceCursor(steps: Int) {
        cursorCol += steps
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

    private fun pushToScrollback(line: Array<Cell>) {
        scrollback.addLast(line)
        if (scrollback.size > maxScrollback) scrollback.removeFirst()
    }

    private fun resizeLine(line: Array<Cell>, newWidth: Int): Array<Cell> {
        if (newWidth == line.size) return line
        return Array(newWidth) { col -> if (col < line.size) line[col] else Cell() }
    }

    private fun scrollUp() {
        pushToScrollback(screen[0])
        for (row in 1 until height) screen[row - 1] = screen[row]
        screen[height - 1] = blankLine()
        cursorRow = cursorRow.coerceAtMost(height - 1)
    }
}
