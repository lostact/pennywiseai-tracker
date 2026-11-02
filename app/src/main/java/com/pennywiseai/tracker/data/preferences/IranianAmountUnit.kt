package com.pennywiseai.tracker.data.preferences

/**
 * Unit Iranian Rial amounts are **displayed** in.
 *
 * The parsers and the database always keep Rial, because that is what the bank
 * SMS quotes (`300,000,000 ریال`). Toman is the same money divided by ten, so
 * switching units never rewrites a stored amount — only how it is rendered.
 */
enum class IranianAmountUnit {
    /** Show the figure as the bank sent it, in Rial (﷼). */
    RIAL,

    /** Show the figure in Toman (Rial / 10) with the Latin symbol [TOMAN_SYMBOL]. */
    TOMAN;

    companion object {
        /** Latin "T" for Toman — never the Persian letters, even in the Rial glyph. */
        const val TOMAN_SYMBOL = "T"

        /** What a Rial figure shows when the user turned Persian digits off. */
        const val RIAL_LATIN_SYMBOL = "IRR"
    }
}
