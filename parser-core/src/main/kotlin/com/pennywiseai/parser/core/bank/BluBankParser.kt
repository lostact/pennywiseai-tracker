package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for blu Bank (بلو), Iran.
 *
 * Handles line-based Persian SMS such as:
 *   بلو
 *   برداشت پول
 *   <NAME> عزیز، 2,500,000 ریال از حساب شما پرید.
 *   موجودی: 488,152 ریال
 *   ۷:۲۸
 *   ۱۴۰۵.۰۳.۲۲
 *
 * Notes:
 * - Amounts use Western digits with comma grouping; only date/time use Persian
 *   digits, which we do not parse (timestamp comes from SMS metadata).
 * - Type signals: "برداشت پول" / "پرید" => EXPENSE (money left the account);
 *   "واریز پول" / "نشست" => INCOME (money landed in the account).
 * - blu samples carry no card/account number, so accountLast4 is always null.
 * - Currency is Iranian Rial (IRR).
 *
 * NOTE: blu sender IDs vary widely with no reliable shared prefix (e.g.
 * "0999 998 7641", "+989999987641", "98300087641"). The only stable core
 * across observed samples is the "87641" suffix, plus the "9999987641" core.
 * canHandle matches those; additional sender IDs may need to be added as more
 * samples surface (known limitation).
 */
class BluBankParser : BankParser() {

    override fun getBankName() = "blu Bank"

    companion object {
        /** Key for blu's personal account ("حساب شخصی شما" — it carries no user-given name). */
        const val PERSONAL_ACCOUNT = "Personal"

        /** "شخصی" = personal. */
        private const val PERSONAL_PHRASE = "شخصی"
    }

    override fun getCurrency() = "IRR"

    // "بلو" = blu (bank-name line; strongest in-body signal).
    private val bankNameMarker = "بلو"

    // "ریال" = Rial. Main sentence amount: a comma-grouped number before "ریال".
    private val amountPattern = Regex("""([0-9][0-9,]*)\s*ریال""")

    // An OTP prompt ("رمز پویا" = dynamic password) quotes the amount, the payee and
    // "رمز: <digits>", but moves no money — booking it double-counts the purchase it
    // authorises, at the same amount. Its intent word ("خرید") is never a signal.
    private val otpPattern = Regex("""رمز\s*پویا|رمز\s*یک\s*بار|رمز\s*[:：]|کد\s*ت(أ|ا)یید""")

    // "موجودی:" = balance, followed by a comma-grouped number before "ریال".
    private val balancePattern = Regex("""موجودی:\s*([0-9][0-9,]*)\s*ریال""")

    override fun canHandle(sender: String): Boolean {
        val digits = sender.filter { it.isDigit() }
        if (digits.endsWith("87641") || digits.contains("9999987641")) return true
        // blu pushes from rotating senders: ordinary Iranian mobiles (09xxxxxxxxx)
        // and 9800… bank shortcodes. Matching them here is safe because parse() is
        // content-gated on "بلو" plus an action verb, so another bank's SMS still
        // parses as null and the factory moves on to its own parser.
        var national = digits
        when {
            national.startsWith("0098") -> national = national.drop(4)   // 0098…
            national.length > 10 && national.startsWith("98") -> national = national.drop(2)  // 98…
        }
        national = national.removePrefix("0")                            // 09… → 9…
        if (national.length == 10 && national[0] == '9') return true      // mobile
        return digits.length in 10..12 && digits.startsWith("98")        // 9800… shortcode
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (!message.contains(bankNameMarker)) return false
        // A promo quoting "خرید" and an amount must never book a fake expense, and an
        // OTP prompt must not be booked as the payment it authorises.
        if (isNonTransactionMessage(message) || otpPattern.containsMatchIn(message)) return false
        // Only wording that says the money actually moved. blu's intent words
        // ("خرید", "انتقال پل") are always accompanied by "پرید" / "نشست" in the
        // sentence that carries the balance, so they are not signals on their own.
        return message.contains("برداشت پول") || message.contains("پرید") ||
                message.contains("واریز پول") || message.contains("نشست")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // First "<number> ریال" is the transaction amount (balance line comes later).
        amountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        // Strongest signals first: "پرید" / "برداشت پول" say money left the account.
        if (message.contains("برداشت پول") || message.contains("پرید")) {
            return TransactionType.EXPENSE
        }
        // "واریز" / "نشست" say money landed in it.
        if (message.contains("واریز") || message.contains("نشست")) {
            return TransactionType.INCOME
        }
        // Remaining money-out wording: card spend, bill pay, and "انتقال" transfers
        // such as blu's "انتقال پل", which no longer need their own branch.
        if (message.contains("انتقال") || message.contains("خرید") ||
            message.contains("پرداخت") || message.contains("کسر")
        ) {
            return TransactionType.EXPENSE
        }
        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        balancePattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    /**
     * blu keeps a personal and a business account behind ONE sender and one card-less
     * SMS format. The only thing that tells them apart is the account it names in the
     * sentence — the business one under whatever name the user gave it:
     *
     *   "به حساب استیم دی ال نشست."    → the account called "استیم دی ال"
     *   "به حساب شخصی شما نشست."        → the personal account ("your personal account")
     *   "از حساب شخصی شما پرید."
     *
     * With no card digits to key on, that phrase becomes the account key, so the two
     * accounts stay separate. A sentence we can't read returns null, which keeps the
     * wallet fallback in [isMobileWallet] for older shapes.
     */
    override fun extractAccountLast4(message: String): String? {
        val phrase = accountPhrasePattern.find(message)?.groupValues?.get(1) ?: return null
        return accountKey(phrase)
    }

    // Fallback only: when the sentence names no account we can read, the app derives a
    // single "blu Bank" account from the running balance instead of dropping it.
    override fun isMobileWallet() = true

    /** "حساب <label> نشست|پرید|واریز|برداشت" — the account a line moved in. */
    private val accountPhrasePattern =
        Regex("""\u062D\u0633\u0627\u0628\s+(\S[^\n]{0,40}?)\s+(?:نشست|پرید|واریز|برداشت)""")

    /** The personal account has no user-given name; everything else keeps its own wording. */
    private fun accountKey(phrase: String): String {
        val normalized = phrase.replace(Regex("""\s+"""), " ").trim()
        return if (normalized.contains(PERSONAL_PHRASE) || normalized == "شما") {
            PERSONAL_ACCOUNT
        } else {
            normalized.take(24)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // blu SMS carries no merchant/payee field.
        return null
    }
}
