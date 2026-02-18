/*
 * Tests for the cell data model: Colour, StyleFlag, CellAttributes and Cell.
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
        val attributes = CellAttributes(foreground = Colour.RED, background = Colour.BLUE)
        assertEquals(Colour.RED, attributes.foreground)
        assertEquals(Colour.BLUE, attributes.background)
    }

    @Test fun `custom style flags`() {
        val attributes = CellAttributes(styles = setOf(StyleFlag.BOLD, StyleFlag.ITALIC))
        assertEquals(setOf(StyleFlag.BOLD, StyleFlag.ITALIC), attributes.styles)
    }

    @Test fun `equal when same values`() {
        val first = CellAttributes(foreground = Colour.RED, styles = setOf(StyleFlag.BOLD))
        val second = CellAttributes(foreground = Colour.RED, styles = setOf(StyleFlag.BOLD))
        assertEquals(first, second)
    }

    @Test fun `not equal when different values`() {
        val first = CellAttributes(foreground = Colour.RED)
        val second = CellAttributes(foreground = Colour.GREEN)
        assertNotEquals(first, second)
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
        val cellAttributes = CellAttributes(foreground = Colour.GREEN, styles = setOf(StyleFlag.UNDERLINE))
        val cell = Cell('X'.code, cellAttributes)
        assertEquals('X', cell.char)
        assertEquals(cellAttributes, cell.attributes)
    }

    @Test fun `equal when same values`() {
        val first = Cell('A'.code, CellAttributes(foreground = Colour.RED))
        val second = Cell('A'.code, CellAttributes(foreground = Colour.RED))
        assertEquals(first, second)
    }

    @Test fun `not equal when different char`() {
        assertNotEquals(Cell('A'.code), Cell('B'.code))
    }

    @Test fun `not equal when different attributes`() {
        assertNotEquals(
            Cell('A'.code, CellAttributes(foreground = Colour.RED)),
            Cell('A'.code, CellAttributes(foreground = Colour.BLUE)),
        )
    }
}
