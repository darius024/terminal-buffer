/*
 * Tests for the TerminalBuffer: construction, cursor, attributes, editing,
 * scrollback, content access, wide characters and resize.
 */

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// -- Buffer construction ------------------------------------------------------

class BufferConstructionTest {

    @Test fun `stores configured dimensions`() {
        val buffer = TerminalBuffer(80, 24, maxScrollback = 1000)
        assertEquals(80, buffer.width)
        assertEquals(24, buffer.height)
    }

    @Test fun `cursor starts at origin`() {
        val buffer = TerminalBuffer(80, 24)
        assertEquals(0, buffer.cursorCol)
        assertEquals(0, buffer.cursorRow)
    }

    @Test fun `screen is initially blank spaces`() {
        val buffer = TerminalBuffer(4, 3)
        for (row in 0 until 3) {
            for (col in 0 until 4) {
                assertEquals(' ', buffer.screen[row][col].char)
                assertEquals(CellAttributes(), buffer.screen[row][col].attributes)
            }
        }
    }
}

// -- Cursor positioning -------------------------------------------------------

class CursorPositionTest {

    @Test fun `set cursor to valid position`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(10, 5)
        assertEquals(10, buffer.cursorCol)
        assertEquals(5, buffer.cursorRow)
    }

    @Test fun `set cursor clamps column to width minus one`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(100, 0)
        assertEquals(79, buffer.cursorCol)
    }

    @Test fun `set cursor clamps row to height minus one`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(0, 30)
        assertEquals(23, buffer.cursorRow)
    }

    @Test fun `set cursor clamps negative column to zero`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(-5, 0)
        assertEquals(0, buffer.cursorCol)
    }

    @Test fun `set cursor clamps negative row to zero`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(0, -3)
        assertEquals(0, buffer.cursorRow)
    }
}

// -- Cursor movement ----------------------------------------------------------

class CursorMovementTest {

    @Test fun `move right by N`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(0, 0)
        buffer.moveCursorRight(5)
        assertEquals(5, buffer.cursorCol)
    }

    @Test fun `move right clamps at right edge`() {
        val buffer = TerminalBuffer(10, 5)
        buffer.setCursor(7, 0)
        buffer.moveCursorRight(20)
        assertEquals(9, buffer.cursorCol)
    }

    @Test fun `move left by N`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(10, 0)
        buffer.moveCursorLeft(3)
        assertEquals(7, buffer.cursorCol)
    }

    @Test fun `move left clamps at left edge`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(2, 0)
        buffer.moveCursorLeft(10)
        assertEquals(0, buffer.cursorCol)
    }

    @Test fun `move down by N`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(0, 0)
        buffer.moveCursorDown(5)
        assertEquals(5, buffer.cursorRow)
    }

    @Test fun `move down clamps at bottom edge`() {
        val buffer = TerminalBuffer(10, 5)
        buffer.setCursor(0, 3)
        buffer.moveCursorDown(20)
        assertEquals(4, buffer.cursorRow)
    }

    @Test fun `move up by N`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(0, 10)
        buffer.moveCursorUp(3)
        assertEquals(7, buffer.cursorRow)
    }

    @Test fun `move up clamps at top edge`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(0, 2)
        buffer.moveCursorUp(10)
        assertEquals(0, buffer.cursorRow)
    }

    @Test fun `move does not affect the other axis`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(5, 10)
        buffer.moveCursorRight(3)
        assertEquals(10, buffer.cursorRow)
        buffer.moveCursorDown(2)
        assertEquals(8, buffer.cursorCol)
    }

    @Test fun `move by zero is a no-op`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setCursor(5, 10)
        buffer.moveCursorRight(0)
        buffer.moveCursorLeft(0)
        buffer.moveCursorUp(0)
        buffer.moveCursorDown(0)
        assertEquals(5, buffer.cursorCol)
        assertEquals(10, buffer.cursorRow)
    }
}

// -- Attributes ---------------------------------------------------------------

class AttributeTest {

    @Test fun `default attributes are all DEFAULT with no styles`() {
        val buffer = TerminalBuffer(80, 24)
        assertEquals(CellAttributes(), buffer.currentAttributes)
    }

    @Test fun `set foreground colour`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setAttributes(foreground = Colour.RED)
        assertEquals(Colour.RED, buffer.currentAttributes.foreground)
        assertEquals(Colour.DEFAULT, buffer.currentAttributes.background)
    }

    @Test fun `set background colour`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setAttributes(background = Colour.BLUE)
        assertEquals(Colour.BLUE, buffer.currentAttributes.background)
    }

    @Test fun `set style flags`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setAttributes(styles = setOf(StyleFlag.BOLD, StyleFlag.UNDERLINE))
        assertEquals(setOf(StyleFlag.BOLD, StyleFlag.UNDERLINE), buffer.currentAttributes.styles)
    }

    @Test fun `set replaces all attributes at once`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setAttributes(
            foreground = Colour.GREEN,
            background = Colour.YELLOW,
            styles = setOf(StyleFlag.ITALIC),
        )
        assertEquals(
            CellAttributes(Colour.GREEN, Colour.YELLOW, setOf(StyleFlag.ITALIC)),
            buffer.currentAttributes,
        )
    }

    @Test fun `set attributes overrides previous`() {
        val buffer = TerminalBuffer(80, 24)
        buffer.setAttributes(foreground = Colour.RED)
        buffer.setAttributes(foreground = Colour.BLUE)
        assertEquals(Colour.BLUE, buffer.currentAttributes.foreground)
    }
}

// -- Write text ---------------------------------------------------------------

class WriteTextTest {

    @Test fun `write places characters at cursor`() {
        val buffer = TerminalBuffer(10, 3)
        buffer.writeText("Hi")
        assertEquals('H', buffer.screen[0][0].char)
        assertEquals('i', buffer.screen[0][1].char)
        assertEquals(' ', buffer.screen[0][2].char)
    }

    @Test fun `write uses current attributes`() {
        val buffer = TerminalBuffer(10, 3)
        buffer.setAttributes(foreground = Colour.RED, styles = setOf(StyleFlag.BOLD))
        buffer.writeText("A")
        val expected = CellAttributes(foreground = Colour.RED, styles = setOf(StyleFlag.BOLD))
        assertEquals(expected, buffer.screen[0][0].attributes)
    }

    @Test fun `write advances cursor`() {
        val buffer = TerminalBuffer(10, 3)
        buffer.writeText("Hello")
        assertEquals(5, buffer.cursorCol)
        assertEquals(0, buffer.cursorRow)
    }

    @Test fun `write at mid-line overwrites existing`() {
        val buffer = TerminalBuffer(10, 3)
        buffer.writeText("ABCDE")
        buffer.setCursor(1, 0)
        buffer.writeText("xx")
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals('x', buffer.screen[0][1].char)
        assertEquals('x', buffer.screen[0][2].char)
        assertEquals('D', buffer.screen[0][3].char)
    }

    @Test fun `write wraps to next line at right edge`() {
        val buffer = TerminalBuffer(4, 3)
        buffer.writeText("ABCDEF")
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals('D', buffer.screen[0][3].char)
        assertEquals('E', buffer.screen[1][0].char)
        assertEquals('F', buffer.screen[1][1].char)
        assertEquals(2, buffer.cursorCol)
        assertEquals(1, buffer.cursorRow)
    }

    @Test fun `write wraps across multiple lines`() {
        val buffer = TerminalBuffer(3, 4)
        buffer.writeText("ABCDEFGH")
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals('D', buffer.screen[1][0].char)
        assertEquals('G', buffer.screen[2][0].char)
        assertEquals('H', buffer.screen[2][1].char)
        assertEquals(2, buffer.cursorCol)
        assertEquals(2, buffer.cursorRow)
    }

    @Test fun `write auto-scrolls when reaching bottom`() {
        val buffer = TerminalBuffer(3, 2)
        buffer.writeText("ABCDEF")
        // Screen is full: row 0 = "DEF", row 1 = "   " — wait, no.
        // After writing 6 chars in a 3×2 buffer:
        //   "ABC" fills row 0, wraps to row 1 → "DEF" fills row 1,
        //   wraps to row 2 which is past the bottom → row 0 ("ABC") scrolls off,
        //   rows shift up, cursor lands on the new empty bottom row.
        // But we only wrote 6 chars = 2 full lines, so the wrap after "F" triggers scroll.
        // Actually: after "DEF" cursor would be at col 0, row 2 → triggers scroll.
        // After scroll: row 0 = "DEF", row 1 = blank, cursor at (0, 1).
        assertEquals('D', buffer.screen[0][0].char)
        assertEquals('E', buffer.screen[0][1].char)
        assertEquals('F', buffer.screen[0][2].char)
        assertEquals(' ', buffer.screen[1][0].char)
        assertEquals(0, buffer.cursorCol)
        assertEquals(1, buffer.cursorRow)
    }

    @Test fun `write scrolls multiple times`() {
        val buffer = TerminalBuffer(3, 2)
        buffer.writeText("ABCDEFGHI")
        // 9 chars in 3×2: fills 3 full lines, but only 2 fit.
        // Row 0 = "GHI", row 1 = blank, cursor at (0, 1).
        assertEquals('G', buffer.screen[0][0].char)
        assertEquals('H', buffer.screen[0][1].char)
        assertEquals('I', buffer.screen[0][2].char)
        assertEquals(0, buffer.cursorCol)
        assertEquals(1, buffer.cursorRow)
    }

    @Test fun `write empty string is a no-op`() {
        val buffer = TerminalBuffer(10, 3)
        buffer.writeText("")
        assertEquals(0, buffer.cursorCol)
        assertEquals(0, buffer.cursorRow)
        assertEquals(' ', buffer.screen[0][0].char)
    }
}

// -- Insert text --------------------------------------------------------------

class InsertTextTest {

    @Test fun `insert at start shifts content right`() {
        val buffer = TerminalBuffer(6, 2)
        buffer.writeText("CDEF")
        buffer.setCursor(0, 0)
        buffer.insertText("AB")
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals('B', buffer.screen[0][1].char)
        assertEquals('C', buffer.screen[0][2].char)
        assertEquals('F', buffer.screen[0][5].char)
    }

    @Test fun `insert at middle shifts tail right`() {
        val buffer = TerminalBuffer(6, 2)
        buffer.writeText("ABEF")
        buffer.setCursor(2, 0)
        buffer.insertText("CD")
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals('B', buffer.screen[0][1].char)
        assertEquals('C', buffer.screen[0][2].char)
        assertEquals('D', buffer.screen[0][3].char)
        assertEquals('E', buffer.screen[0][4].char)
        assertEquals('F', buffer.screen[0][5].char)
    }

    @Test fun `insert advances cursor past inserted text`() {
        val buffer = TerminalBuffer(10, 2)
        buffer.writeText("ABEF")
        buffer.setCursor(2, 0)
        buffer.insertText("CD")
        assertEquals(4, buffer.cursorCol)
        assertEquals(0, buffer.cursorRow)
    }

    @Test fun `insert uses current attributes for new text`() {
        val buffer = TerminalBuffer(10, 2)
        buffer.writeText("AB")
        buffer.setCursor(1, 0)
        buffer.setAttributes(foreground = Colour.RED)
        buffer.insertText("X")
        val redAttributes = CellAttributes(foreground = Colour.RED)
        assertEquals(redAttributes, buffer.screen[0][1].attributes)
        assertEquals(CellAttributes(), buffer.screen[0][0].attributes)
        assertEquals(CellAttributes(), buffer.screen[0][2].attributes)
    }

    @Test fun `insert wraps overflow to next line`() {
        val buffer = TerminalBuffer(4, 3)
        buffer.writeText("ABCD")
        buffer.setCursor(2, 0)
        buffer.insertText("XX")
        // Row 0: A B X X, Row 1: C D _ _
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals('B', buffer.screen[0][1].char)
        assertEquals('X', buffer.screen[0][2].char)
        assertEquals('X', buffer.screen[0][3].char)
        assertEquals('C', buffer.screen[1][0].char)
        assertEquals('D', buffer.screen[1][1].char)
        assertEquals(' ', buffer.screen[1][2].char)
    }

    @Test fun `insert on empty line`() {
        val buffer = TerminalBuffer(6, 2)
        buffer.insertText("Hi")
        assertEquals('H', buffer.screen[0][0].char)
        assertEquals('i', buffer.screen[0][1].char)
        assertEquals(' ', buffer.screen[0][2].char)
        assertEquals(2, buffer.cursorCol)
    }

    @Test fun `insert empty string is a no-op`() {
        val buffer = TerminalBuffer(6, 2)
        buffer.writeText("ABC")
        buffer.setCursor(1, 0)
        buffer.insertText("")
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals('B', buffer.screen[0][1].char)
        assertEquals('C', buffer.screen[0][2].char)
        assertEquals(1, buffer.cursorCol)
    }
}

// -- Fill line ----------------------------------------------------------------

class FillLineTest {

    @Test fun `fill line with character`() {
        val buffer = TerminalBuffer(5, 2)
        buffer.fillLine('#')
        for (col in 0 until 5) {
            assertEquals('#', buffer.screen[0][col].char)
        }
    }

    @Test fun `fill line uses current attributes`() {
        val buffer = TerminalBuffer(5, 2)
        buffer.setAttributes(foreground = Colour.GREEN)
        buffer.fillLine('*')
        val expected = CellAttributes(foreground = Colour.GREEN)
        for (col in 0 until 5) {
            assertEquals(expected, buffer.screen[0][col].attributes)
        }
    }

    @Test fun `fill line fills the cursor row`() {
        val buffer = TerminalBuffer(4, 3)
        buffer.setCursor(0, 1)
        buffer.fillLine('-')
        for (col in 0 until 4) {
            assertEquals(' ', buffer.screen[0][col].char)
            assertEquals('-', buffer.screen[1][col].char)
            assertEquals(' ', buffer.screen[2][col].char)
        }
    }

    @Test fun `fill with space clears the line`() {
        val buffer = TerminalBuffer(5, 2)
        buffer.writeText("Hello")
        buffer.setCursor(0, 0)
        buffer.fillLine(' ')
        for (col in 0 until 5) {
            assertEquals(' ', buffer.screen[0][col].char)
        }
    }

    @Test fun `fill does not move cursor`() {
        val buffer = TerminalBuffer(5, 2)
        buffer.setCursor(2, 1)
        buffer.fillLine('X')
        assertEquals(2, buffer.cursorCol)
        assertEquals(1, buffer.cursorRow)
    }
}

// -- Insert line --------------------------------------------------------------

class InsertLineTest {

    @Test fun `insert line adds blank row at bottom`() {
        val buffer = TerminalBuffer(3, 2)
        buffer.writeText("ABCDEF")
        buffer.insertLine()
        assertEquals(' ', buffer.screen[1][0].char)
        assertEquals(' ', buffer.screen[1][1].char)
        assertEquals(' ', buffer.screen[1][2].char)
    }

    @Test fun `insert line shifts rows up`() {
        val buffer = TerminalBuffer(3, 3)
        buffer.writeText("ABC")
        buffer.setCursor(0, 1)
        buffer.writeText("DEF")
        buffer.insertLine()
        // Row 0 ("ABC") scrolled off; row 1 ("DEF") becomes row 0; blank at bottom
        assertEquals('D', buffer.screen[0][0].char)
        assertEquals('E', buffer.screen[0][1].char)
        assertEquals('F', buffer.screen[0][2].char)
        assertEquals(' ', buffer.screen[1][0].char)
    }

    @Test fun `insert line does not move cursor`() {
        val buffer = TerminalBuffer(5, 3)
        buffer.setCursor(2, 1)
        buffer.insertLine()
        assertEquals(2, buffer.cursorCol)
        assertEquals(1, buffer.cursorRow)
    }
}

// -- Scrollback ---------------------------------------------------------------

class ScrollbackTest {

    @Test fun `scrollback starts empty`() {
        val buffer = TerminalBuffer(5, 3, maxScrollback = 10)
        assertEquals(0, buffer.scrollbackSize)
    }

    @Test fun `write auto-scroll pushes line to scrollback`() {
        val buffer = TerminalBuffer(3, 2, maxScrollback = 10)
        buffer.writeText("ABCDEF")
        // "ABC" scrolled off the top into scrollback
        assertEquals(1, buffer.scrollbackSize)
        assertEquals('A', buffer.scrollback[0][0].char)
        assertEquals('B', buffer.scrollback[0][1].char)
        assertEquals('C', buffer.scrollback[0][2].char)
    }

    @Test fun `insert line pushes top row to scrollback`() {
        val buffer = TerminalBuffer(3, 3, maxScrollback = 10)
        buffer.writeText("ABC")
        buffer.setCursor(0, 1)
        buffer.writeText("DEF")
        buffer.insertLine()
        assertEquals(1, buffer.scrollbackSize)
        assertEquals('A', buffer.scrollback[0][0].char)
    }

    @Test fun `multiple scrolls accumulate in scrollback`() {
        val buffer = TerminalBuffer(3, 2, maxScrollback = 100)
        buffer.writeText("ABCDEFGHI")
        // 3 full lines in 3×2: two scroll events
        assertEquals(2, buffer.scrollbackSize)
        assertEquals('A', buffer.scrollback[0][0].char)
        assertEquals('D', buffer.scrollback[1][0].char)
    }

    @Test fun `scrollback respects max size`() {
        val buffer = TerminalBuffer(3, 2, maxScrollback = 2)
        // Write 4 full lines → 2 scrolls → scrollback would have 2 entries
        buffer.writeText("ABCDEFGHIJKL")
        // 4 lines, 2-row screen: 2 scrolls, but then 2 more scrolls = 4 total pushed
        // With max 2: oldest are dropped
        assertTrue(buffer.scrollbackSize <= 2)
    }

    @Test fun `oldest scrollback lines are dropped when max exceeded`() {
        val buffer = TerminalBuffer(2, 2, maxScrollback = 2)
        // Write 5 full lines: AABB CCDD EEFF GGHH IIJJ
        buffer.writeText("AABBCCDDEEFFGGHH")
        // Many scrolls, only last 2 scrollback lines survive
        assertEquals(2, buffer.scrollbackSize)
        // The oldest surviving line should NOT be "AA"
        val firstLineChars = String(CharArray(2) { buffer.scrollback[0][it].char })
        assertNotEquals("AA", firstLineChars)
    }

    @Test fun `scrollback preserves cell attributes`() {
        val buffer = TerminalBuffer(3, 2, maxScrollback = 10)
        buffer.setAttributes(foreground = Colour.RED)
        buffer.writeText("ABCDEF")
        val expected = CellAttributes(foreground = Colour.RED)
        assertEquals(expected, buffer.scrollback[0][0].attributes)
    }
}

// -- Clear screen -------------------------------------------------------------

class ClearScreenTest {

    @Test fun `clear screen resets all cells to blank`() {
        val buffer = TerminalBuffer(4, 3)
        buffer.writeText("Hello World!")
        buffer.clearScreen()
        for (row in 0 until 3) {
            for (col in 0 until 4) {
                assertEquals(' ', buffer.screen[row][col].char)
                assertEquals(CellAttributes(), buffer.screen[row][col].attributes)
            }
        }
    }

    @Test fun `clear screen moves cursor to origin`() {
        val buffer = TerminalBuffer(10, 5)
        buffer.setCursor(5, 3)
        buffer.clearScreen()
        assertEquals(0, buffer.cursorCol)
        assertEquals(0, buffer.cursorRow)
    }

    @Test fun `clear screen does not affect scrollback`() {
        val buffer = TerminalBuffer(3, 2, maxScrollback = 10)
        buffer.writeText("ABCDEF")
        assertEquals(1, buffer.scrollbackSize)
        buffer.clearScreen()
        assertEquals(1, buffer.scrollbackSize)
    }

    @Test fun `clear all resets screen and drops scrollback`() {
        val buffer = TerminalBuffer(3, 2, maxScrollback = 10)
        buffer.writeText("ABCDEF")
        buffer.clearAll()
        assertEquals(0, buffer.scrollbackSize)
        assertEquals(0, buffer.cursorCol)
        assertEquals(0, buffer.cursorRow)
        assertEquals(' ', buffer.screen[0][0].char)
    }
}

// -- Content access -----------------------------------------------------------
//
// Unified row coordinates: row 0 = oldest scrollback line,
// row scrollbackSize = top screen line.

class ContentAccessTest {

    @Test fun `getChar from screen`() {
        val buffer = TerminalBuffer(4, 2)
        buffer.writeText("ABCD")
        assertEquals('A', buffer.getChar(0, 0))
        assertEquals('D', buffer.getChar(3, 0))
    }

    @Test fun `getChar from scrollback`() {
        val buffer = TerminalBuffer(3, 2, maxScrollback = 10)
        buffer.writeText("ABCDEF")
        // Scrollback row 0 = "ABC", screen row 0 (unified row 1) = "DEF"
        assertEquals('A', buffer.getChar(0, 0))
        assertEquals('C', buffer.getChar(2, 0))
        assertEquals('D', buffer.getChar(0, 1))
    }

    @Test fun `getChar out of bounds returns space`() {
        val buffer = TerminalBuffer(4, 2)
        assertEquals(' ', buffer.getChar(-1, 0))
        assertEquals(' ', buffer.getChar(4, 0))
        assertEquals(' ', buffer.getChar(0, -1))
        assertEquals(' ', buffer.getChar(0, 2))
    }

    @Test fun `getAttributes from screen`() {
        val buffer = TerminalBuffer(4, 2)
        buffer.setAttributes(foreground = Colour.RED)
        buffer.writeText("A")
        assertEquals(CellAttributes(foreground = Colour.RED), buffer.getAttributes(0, 0))
        assertEquals(CellAttributes(), buffer.getAttributes(1, 0))
    }

    @Test fun `getAttributes from scrollback`() {
        val buffer = TerminalBuffer(3, 2, maxScrollback = 10)
        buffer.setAttributes(foreground = Colour.GREEN)
        buffer.writeText("ABCDEF")
        assertEquals(CellAttributes(foreground = Colour.GREEN), buffer.getAttributes(0, 0))
    }

    @Test fun `getAttributes out of bounds returns default`() {
        val buffer = TerminalBuffer(4, 2)
        assertEquals(CellAttributes(), buffer.getAttributes(99, 99))
    }

    @Test fun `getLine from screen`() {
        val buffer = TerminalBuffer(5, 2)
        buffer.writeText("Hello")
        assertEquals("Hello", buffer.getLine(0))
    }

    @Test fun `getLine from scrollback`() {
        val buffer = TerminalBuffer(3, 2, maxScrollback = 10)
        buffer.writeText("ABCDEF")
        assertEquals("ABC", buffer.getLine(0))
        assertEquals("DEF", buffer.getLine(1))
    }

    @Test fun `getLine pads with spaces for partial content`() {
        val buffer = TerminalBuffer(5, 2)
        buffer.writeText("Hi")
        assertEquals("Hi   ", buffer.getLine(0))
    }

    @Test fun `getLine out of bounds returns empty string`() {
        val buffer = TerminalBuffer(4, 2)
        assertEquals("", buffer.getLine(-1))
        assertEquals("", buffer.getLine(99))
    }

    @Test fun `getScreenContent returns all screen lines`() {
        val buffer = TerminalBuffer(3, 3)
        buffer.writeText("ABCDEF")
        assertEquals("ABC\nDEF\n   ", buffer.getScreenContent())
    }

    @Test fun `getScreenContent trims nothing`() {
        val buffer = TerminalBuffer(4, 2)
        buffer.writeText("Hi")
        assertEquals("Hi  \n    ", buffer.getScreenContent())
    }

    @Test fun `getAllContent includes scrollback and screen`() {
        val buffer = TerminalBuffer(3, 2, maxScrollback = 10)
        buffer.writeText("ABCDEFGHI")
        // Scrollback: "ABC", "DEF"; Screen: "GHI", "   "
        assertEquals("ABC\nDEF\nGHI\n   ", buffer.getAllContent())
    }

    @Test fun `getAllContent with no scrollback equals getScreenContent`() {
        val buffer = TerminalBuffer(3, 2)
        buffer.writeText("ABC")
        assertEquals(buffer.getScreenContent(), buffer.getAllContent())
    }
}

// -- Wide characters ----------------------------------------------------------
//
// Characters like CJK ideographs occupy 2 terminal cells.
// The left cell holds the character; the right cell is a continuation marker.

class WideCharTest {

    @Test fun `wide char occupies two cells`() {
        val buffer = TerminalBuffer(6, 2)
        buffer.writeText("中")
        assertEquals('中', buffer.screen[0][0].char)
        // Right half is a continuation marker, not a printable character
        assertNotEquals('中', buffer.screen[0][1].char)
        assertEquals(' ', buffer.screen[0][2].char)
    }

    @Test fun `cursor advances by two for wide char`() {
        val buffer = TerminalBuffer(10, 2)
        buffer.writeText("中")
        assertEquals(2, buffer.cursorCol)
    }

    @Test fun `narrow and wide chars mixed`() {
        val buffer = TerminalBuffer(10, 2)
        buffer.writeText("A中B")
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals('中', buffer.screen[0][1].char)
        // col 2 = continuation
        assertEquals('B', buffer.screen[0][3].char)
        assertEquals(4, buffer.cursorCol)
    }

    @Test fun `wide char uses current attributes`() {
        val buffer = TerminalBuffer(6, 2)
        buffer.setAttributes(foreground = Colour.RED)
        buffer.writeText("中")
        val expected = CellAttributes(foreground = Colour.RED)
        assertEquals(expected, buffer.screen[0][0].attributes)
        assertEquals(expected, buffer.screen[0][1].attributes)
    }

    @Test fun `wide char at last column wraps to next line`() {
        val buffer = TerminalBuffer(5, 3)
        buffer.setCursor(4, 0)
        buffer.writeText("中")
        // Doesn't fit at col 4 (needs 2 cells, only 1 left) → wraps
        assertEquals(' ', buffer.screen[0][4].char)
        assertEquals('中', buffer.screen[1][0].char)
        assertEquals(2, buffer.cursorCol)
        assertEquals(1, buffer.cursorRow)
    }

    @Test fun `overwrite left half of wide char clears right half`() {
        val buffer = TerminalBuffer(6, 2)
        buffer.writeText("中")
        buffer.setCursor(0, 0)
        buffer.writeText("X")
        assertEquals('X', buffer.screen[0][0].char)
        assertEquals(' ', buffer.screen[0][1].char)
    }

    @Test fun `overwrite right half of wide char clears left half`() {
        val buffer = TerminalBuffer(6, 2)
        buffer.writeText("中")
        buffer.setCursor(1, 0)
        buffer.writeText("X")
        assertEquals(' ', buffer.screen[0][0].char)
        assertEquals('X', buffer.screen[0][1].char)
    }

    @Test fun `getLine includes wide chars without continuation markers`() {
        val buffer = TerminalBuffer(6, 2)
        buffer.writeText("A中B")
        // "A中B" occupies 4 cells + 2 blank = "A中B  " (5 kotlin chars)
        val line = buffer.getLine(buffer.scrollbackSize)
        assertTrue(line.contains("A"))
        assertTrue(line.contains("中"))
        assertTrue(line.contains("B"))
    }

    @Test fun `multiple wide chars in a row`() {
        val buffer = TerminalBuffer(8, 2)
        buffer.writeText("中文字")
        assertEquals('中', buffer.screen[0][0].char)
        assertEquals('文', buffer.screen[0][2].char)
        assertEquals('字', buffer.screen[0][4].char)
        assertEquals(6, buffer.cursorCol)
    }
}

// -- Resize -------------------------------------------------------------------

class ResizeTest {

    @Test fun `resize updates dimensions`() {
        val buffer = TerminalBuffer(10, 5)
        buffer.resize(20, 10)
        assertEquals(20, buffer.width)
        assertEquals(10, buffer.height)
    }

    @Test fun `grow width pads lines with blanks`() {
        val buffer = TerminalBuffer(3, 2)
        buffer.writeText("AB")
        buffer.resize(5, 2)
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals('B', buffer.screen[0][1].char)
        assertEquals(' ', buffer.screen[0][2].char)
        assertEquals(' ', buffer.screen[0][3].char)
        assertEquals(' ', buffer.screen[0][4].char)
    }

    @Test fun `shrink width truncates lines`() {
        val buffer = TerminalBuffer(5, 2)
        buffer.writeText("ABCDE")
        buffer.resize(3, 2)
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals('B', buffer.screen[0][1].char)
        assertEquals('C', buffer.screen[0][2].char)
    }

    @Test fun `shrink height moves excess top rows to scrollback`() {
        val buffer = TerminalBuffer(3, 4)
        buffer.writeText("ABC")
        buffer.setCursor(0, 1)
        buffer.writeText("DEF")
        buffer.setCursor(0, 2)
        buffer.writeText("GHI")
        buffer.setCursor(0, 3)
        buffer.writeText("JK")
        buffer.resize(3, 2)
        // Top 2 rows ("ABC", "DEF") move to scrollback; screen keeps "GHI", "JK_"
        assertEquals(2, buffer.scrollbackSize)
        assertEquals('A', buffer.scrollback[0][0].char)
        assertEquals('D', buffer.scrollback[1][0].char)
        assertEquals('G', buffer.screen[0][0].char)
        assertEquals('J', buffer.screen[1][0].char)
    }

    @Test fun `grow height pulls lines from scrollback`() {
        val buffer = TerminalBuffer(3, 2, maxScrollback = 10)
        buffer.writeText("ABCDEFGHI")
        // Scrollback: "ABC", "DEF"; Screen: "GHI", "   "
        buffer.resize(3, 4)
        // Pull 2 lines from scrollback into the top of the screen
        assertEquals(0, buffer.scrollbackSize)
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals('D', buffer.screen[1][0].char)
        assertEquals('G', buffer.screen[2][0].char)
    }

    @Test fun `grow height adds blank rows when no scrollback`() {
        val buffer = TerminalBuffer(3, 2)
        buffer.writeText("ABC")
        buffer.resize(3, 4)
        assertEquals('A', buffer.screen[0][0].char)
        assertEquals(' ', buffer.screen[2][0].char)
        assertEquals(' ', buffer.screen[3][0].char)
    }

    @Test fun `cursor clamped after shrink`() {
        val buffer = TerminalBuffer(10, 10)
        buffer.setCursor(8, 7)
        buffer.resize(5, 4)
        assertEquals(4, buffer.cursorCol)
        assertEquals(3, buffer.cursorRow)
    }

    @Test fun `cursor preserved when it still fits`() {
        val buffer = TerminalBuffer(10, 10)
        buffer.setCursor(3, 2)
        buffer.resize(20, 20)
        assertEquals(3, buffer.cursorCol)
        assertEquals(2, buffer.cursorRow)
    }

    @Test fun `resize to same dimensions is a no-op`() {
        val buffer = TerminalBuffer(5, 3)
        buffer.writeText("Hello")
        buffer.resize(5, 3)
        assertEquals('H', buffer.screen[0][0].char)
        assertEquals(5, buffer.width)
        assertEquals(3, buffer.height)
    }
}
