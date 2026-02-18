/*
 * Test suite for the Terminal Buffer.
 *
 * Tests are organised by feature area, following the implementation order:
 * data model, buffer construction, cursor, attributes, editing, scrollback,
 * content access, wide characters and resize.
 */

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// -- Colour enum --------------------------------------------------------------

class ColourTest {

    @Test fun `has DEFAULT and all 16 standard ANSI colours`() {
        val expected = setOf(
            Colour.DEFAULT,
            Colour.BLACK, Colour.RED, Colour.GREEN, Colour.YELLOW,
            Colour.BLUE, Colour.MAGENTA, Colour.CYAN, Colour.WHITE,
            Colour.BRIGHT_BLACK, Colour.BRIGHT_RED, Colour.BRIGHT_GREEN, Colour.BRIGHT_YELLOW,
            Colour.BRIGHT_BLUE, Colour.BRIGHT_MAGENTA, Colour.BRIGHT_CYAN, Colour.BRIGHT_WHITE,
        )
        assertEquals(expected, Colour.entries.toSet())
    }

    @Test fun `has exactly 17 values`() {
        assertEquals(17, Colour.entries.size)
    }
}

// -- StyleFlag enum -----------------------------------------------------------

class StyleFlagTest {

    @Test fun `has BOLD, ITALIC and UNDERLINE`() {
        val expected = setOf(StyleFlag.BOLD, StyleFlag.ITALIC, StyleFlag.UNDERLINE)
        assertEquals(expected, StyleFlag.entries.toSet())
    }

    @Test fun `has exactly 3 values`() {
        assertEquals(3, StyleFlag.entries.size)
    }
}

// -- CellAttributes -----------------------------------------------------------

class CellAttributesTest {

    @Test fun `default foreground is DEFAULT`() {
        assertEquals(Colour.DEFAULT, CellAttributes().foreground)
    }

    @Test fun `default background is DEFAULT`() {
        assertEquals(Colour.DEFAULT, CellAttributes().background)
    }

    @Test fun `default styles is empty`() {
        assertTrue(CellAttributes().styles.isEmpty())
    }

    @Test fun `custom foreground and background`() {
        val attrs = CellAttributes(foreground = Colour.RED, background = Colour.BLUE)
        assertEquals(Colour.RED, attrs.foreground)
        assertEquals(Colour.BLUE, attrs.background)
    }

    @Test fun `custom style flags`() {
        val attrs = CellAttributes(styles = setOf(StyleFlag.BOLD, StyleFlag.ITALIC))
        assertEquals(setOf(StyleFlag.BOLD, StyleFlag.ITALIC), attrs.styles)
    }

    @Test fun `equal when same values`() {
        val a = CellAttributes(foreground = Colour.RED, styles = setOf(StyleFlag.BOLD))
        val b = CellAttributes(foreground = Colour.RED, styles = setOf(StyleFlag.BOLD))
        assertEquals(a, b)
    }

    @Test fun `not equal when different values`() {
        val a = CellAttributes(foreground = Colour.RED)
        val b = CellAttributes(foreground = Colour.GREEN)
        assertNotEquals(a, b)
    }
}

// -- Cell ---------------------------------------------------------------------

// -- Buffer construction ------------------------------------------------------

class BufferConstructionTest {

    @Test fun `stores configured dimensions`() {
        val buf = TerminalBuffer(80, 24, maxScrollback = 1000)
        assertEquals(80, buf.width)
        assertEquals(24, buf.height)
    }

    @Test fun `cursor starts at origin`() {
        val buf = TerminalBuffer(80, 24)
        assertEquals(0, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test fun `screen is initially blank spaces`() {
        val buf = TerminalBuffer(4, 3)
        for (row in 0 until 3) {
            for (col in 0 until 4) {
                assertEquals(' ', buf.screen[row][col].char)
                assertEquals(CellAttributes(), buf.screen[row][col].attributes)
            }
        }
    }
}

// -- Cursor positioning -------------------------------------------------------

class CursorPositionTest {

    @Test fun `set cursor to valid position`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(10, 5)
        assertEquals(10, buf.cursorCol)
        assertEquals(5, buf.cursorRow)
    }

    @Test fun `set cursor clamps column to width minus one`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(100, 0)
        assertEquals(79, buf.cursorCol)
    }

    @Test fun `set cursor clamps row to height minus one`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(0, 30)
        assertEquals(23, buf.cursorRow)
    }

    @Test fun `set cursor clamps negative column to zero`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(-5, 0)
        assertEquals(0, buf.cursorCol)
    }

    @Test fun `set cursor clamps negative row to zero`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(0, -3)
        assertEquals(0, buf.cursorRow)
    }
}

// -- Cursor movement ----------------------------------------------------------

class CursorMovementTest {

    @Test fun `move right by N`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(0, 0)
        buf.moveCursorRight(5)
        assertEquals(5, buf.cursorCol)
    }

    @Test fun `move right clamps at right edge`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursor(7, 0)
        buf.moveCursorRight(20)
        assertEquals(9, buf.cursorCol)
    }

    @Test fun `move left by N`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(10, 0)
        buf.moveCursorLeft(3)
        assertEquals(7, buf.cursorCol)
    }

    @Test fun `move left clamps at left edge`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(2, 0)
        buf.moveCursorLeft(10)
        assertEquals(0, buf.cursorCol)
    }

    @Test fun `move down by N`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(0, 0)
        buf.moveCursorDown(5)
        assertEquals(5, buf.cursorRow)
    }

    @Test fun `move down clamps at bottom edge`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursor(0, 3)
        buf.moveCursorDown(20)
        assertEquals(4, buf.cursorRow)
    }

    @Test fun `move up by N`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(0, 10)
        buf.moveCursorUp(3)
        assertEquals(7, buf.cursorRow)
    }

    @Test fun `move up clamps at top edge`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(0, 2)
        buf.moveCursorUp(10)
        assertEquals(0, buf.cursorRow)
    }

    @Test fun `move does not affect the other axis`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(5, 10)
        buf.moveCursorRight(3)
        assertEquals(10, buf.cursorRow)
        buf.moveCursorDown(2)
        assertEquals(8, buf.cursorCol)
    }

    @Test fun `move by zero is a no-op`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursor(5, 10)
        buf.moveCursorRight(0)
        buf.moveCursorLeft(0)
        buf.moveCursorUp(0)
        buf.moveCursorDown(0)
        assertEquals(5, buf.cursorCol)
        assertEquals(10, buf.cursorRow)
    }
}

// -- Cell ---------------------------------------------------------------------

class CellTest {

    @Test fun `default char is space`() {
        assertEquals(' ', Cell().char)
    }

    @Test fun `default attributes are default CellAttributes`() {
        assertEquals(CellAttributes(), Cell().attributes)
    }

    @Test fun `custom char and attributes`() {
        val attrs = CellAttributes(foreground = Colour.GREEN, styles = setOf(StyleFlag.UNDERLINE))
        val cell = Cell('X', attrs)
        assertEquals('X', cell.char)
        assertEquals(attrs, cell.attributes)
    }

    @Test fun `equal when same values`() {
        val a = Cell('A', CellAttributes(foreground = Colour.RED))
        val b = Cell('A', CellAttributes(foreground = Colour.RED))
        assertEquals(a, b)
    }

    @Test fun `not equal when different char`() {
        assertNotEquals(Cell('A'), Cell('B'))
    }

    @Test fun `not equal when different attributes`() {
        assertNotEquals(
            Cell('A', CellAttributes(foreground = Colour.RED)),
            Cell('A', CellAttributes(foreground = Colour.BLUE)),
        )
    }
}
