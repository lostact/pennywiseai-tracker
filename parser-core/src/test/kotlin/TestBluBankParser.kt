import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BluBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BluBankParserTest {

    @TestFactory
    fun `blu parser handles common cases`(): List<DynamicTest> {
        val parser = BluBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "Withdrawal / debit (EXPENSE)",
                message = "بلو\nبرداشت پول\n<NAME> عزیز، 2,500,000 ریال از حساب شما پرید.\nموجودی: 488,152 ریال\n۷:۲۸\n۱۴۰۵.۰۳.۲۲",
                sender = "0999 998 7641",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("488152")
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
                    balance = BigDecimal("8151152")
                )
            ),
            ParserTestCase(
                name = "Pol transfer from a mobile sender (#blu) — EXPENSE",
                message = "بلو\nانتقال پل\nمحمد عزیز، 300,000,000 ریال از حساب استیم دی ال پرید.\n" +
                    "موجودی: 314,143,824 ریال\n۶:۴۱\n۱۴۰۵.۰۸.۱۰",
                sender = "0999 123 4567",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300000000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("314143824"),
                    // No account digits in a blu SMS: the app must fall back to its
                    // single service-level account, or blu never reaches Accounts.
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
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
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
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
                    balance = BigDecimal("71087723")
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
}
