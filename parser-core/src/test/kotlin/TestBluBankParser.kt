import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BluBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BluBankParserTest {

    private val parser = BluBankParser()

    @TestFactory
    fun `blu parser handles common cases`(): List<DynamicTest> {

        val cases = listOf(
            ParserTestCase(
                name = "Withdrawal / debit (EXPENSE)",
                message = "بلو\nبرداشت پول\n<NAME> عزیز، 2,500,000 ریال از حساب شما پرید.\nموجودی: 488,152 ریال\n۷:۲۸\n۱۴۰۵.۰۳.۲۲",
                sender = "0999 998 7641",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("488152"),
                    // "حساب شما" with no name is the personal account.
                    accountLast4 = BluBankParser.PERSONAL_ACCOUNT
                )
            ),
            ParserTestCase(
                name = "Second withdrawal / debit (EXPENSE)",
                message = "بلو\nبرداشت پول\n<NAME> عزیز، 3,000,000 ریال از حساب شما پرید.\nموجودی: 8,151,152 ریال\n۲۲:۱۴\n۱۴۰۵.۰۳.۲۰",
                sender = "+989999987641",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("8151152"),
                    accountLast4 = BluBankParser.PERSONAL_ACCOUNT
                )
            ),
            ParserTestCase(
                name = "Pol transfer out of the business account — EXPENSE",
                message = "بلو\nانتقال پل\nمحمد عزیز، 300,000,000 ریال از حساب استیم دی ال پرید.\n" +
                    "موجودی: 314,143,824 ریال\n۶:۴۱\n۱۴۰۵.۰۸.۱۰",
                sender = "0999 123 4567",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300000000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("314143824"),
                    // The account the user named, which must not merge with "Personal".
                    accountLast4 = "استیم دی ال"
                )
            ),
            ParserTestCase(
                name = "Deposit into the business account — INCOME",
                message = "بلو\nواریز پول\nمحمد عزیز، 155,100,180ریال به حساب استیم دی ال نشست.\n" +
                    "موجودی: 469,244,004 ریال\n۴:۳۱\n۱۴۰۵.۰۸.۱۱",
                sender = "0999 998 7641",
                expected = ExpectedTransaction(
                    amount = BigDecimal("155100180"),
                    currency = "IRR",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("469244004"),
                    accountLast4 = "استیم دی ال"
                )
            ),
            ParserTestCase(
                name = "Deposit into the personal account — INCOME",
                message = "بلو\nواریز پول\nمحمد عزیز، 2,000,000ریال به حساب شخصی شما نشست.\n" +
                    "موجودی: 109,005,455 ریال\n۲۳:۵۹\n۱۴۰۵.۰۸.۰۸",
                sender = "0999 998 7641",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000000"),
                    currency = "IRR",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("109005455"),
                    accountLast4 = BluBankParser.PERSONAL_ACCOUNT
                )
            ),
            ParserTestCase(
                name = "Card spend withdrawal from the personal account — EXPENSE",
                message = "بلو\nبرداشت پول\nمحمد عزیز، 3,353,975 ریال از حساب شخصی شما پرید.\n" +
                    "موجودی: 54,310,480 ریال\n۱۱:۱۹\n۱۴۰۵.۰۸.۱۱",
                sender = "0999 998 7641",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3353975"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("54310480"),
                    accountLast4 = BluBankParser.PERSONAL_ACCOUNT
                )
            ),
            ParserTestCase(
                name = "OTP prompt for an online card payment must NOT parse",
                message = "بلو\nبفرمایید رمز پویا\nخرید\nایرانسل\nمبلغ: 3,353,975 ریال\nرمز: 439429\n۱۱:۱۹",
                sender = "0999 998 7641",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Deposit / credit (INCOME)",
                message = "بلو\nواریز پول\n<NAME> عزیز، 70,000,000 ریال به حساب شما نشست.\nموجودی: 71,087,723 ریال\n۱۱:۴۹\n۱۴۰۵.۰۳.۱۸",
                sender = "98300087641",
                expected = ExpectedTransaction(
                    amount = BigDecimal("70000000"),
                    currency = "IRR",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("71087723"),
                    accountLast4 = BluBankParser.PERSONAL_ACCOUNT
                )
            )
        )

        val handleChecks = listOf(
            "0999 998 7641" to true,
            "+989999987641" to true,
            "98300087641" to true,
            // Rotating senders blu also pushes from: Iranian mobile + 9800 shortcode.
            "0999 123 4567" to true,
            "+989121234567" to true,
            "98300012345" to true,
            "AD-ICICIB" to false,
            "HDFC" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "blu Bank Parser")
    }

    /**
     * blu runs a personal and a business account behind one sender. Two deposits that
     * differ only in the account phrase must land on two accounts, or both balances
     * collapse into one and every total is wrong.
     */
    @Test
    fun `personal and business accounts stay separate`() {
        val business = parser.parse(
            "بلو\nواریز پول\nمحمد عزیز، 155,100,180ریال به حساب استیم دی ال نشست.\n" +
                "موجودی: 469,244,004 ریال",
            "0999 998 7641",
            0L
        )
        val personal = parser.parse(
            "بلو\nواریز پول\nمحمد عزیز، 2,000,000ریال به حساب شخصی شما نشست.\n" +
                "موجودی: 109,005,455 ریال",
            "0999 998 7641",
            0L
        )

        assertEquals("استیم دی ال", business?.accountLast4)
        assertEquals(BluBankParser.PERSONAL_ACCOUNT, personal?.accountLast4)
        assertNotEquals(business?.accountLast4, personal?.accountLast4)
        // Balances belong to the account each SMS names, so they cannot overwrite
        // each other: 469,244,004 is the business account's, 109,005,455 the personal one.
        assertEquals(BigDecimal("469244004"), business?.balance)
        assertEquals(BigDecimal("109005455"), personal?.balance)
    }
}
