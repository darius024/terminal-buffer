/*
 * Cell data model for the terminal buffer.
 *
 * Defines the types that describe a single character cell: its colour,
 * style flags, combined attributes and the cell itself. Also provides
 * a display-width function for wide (CJK / fullwidth / emoji) detection
 * that operates on Unicode code points to handle supplementary planes.
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
    val codePoint: Int = ' '.code,
    val attributes: CellAttributes = CellAttributes(),
) {
    /** BMP convenience accessor. For supplementary characters, use [codePoint]. */
    val char: Char get() = codePoint.toChar()
}

/** Display width of a Unicode code point: 2 for CJK / fullwidth / emoji, 1 otherwise. */
fun codePointDisplayWidth(codePoint: Int): Int = when (codePoint) {
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
    in 0xFFE0..0xFFE6,
    in 0x1F000..0x1FAFF,
    in 0x20000..0x3134F -> 2
    else -> 1
}
