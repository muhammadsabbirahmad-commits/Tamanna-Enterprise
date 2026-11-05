package com.tamanna.enterprise.reports

import org.junit.Assert.*
import org.junit.Test

class DoubleEntryAccountingTest {
    @Test fun eachJournalEntryMustBalanceIndependently() {
        val good = JournalEntry("S-1", "2026-11-01",
            listOf(JournalLine("Cash", debit = 70.0), JournalLine("Receivable", debit = 30.0), JournalLine("Sales", credit = 100.0)), "Sales invoice")
        val bad = JournalEntry("S-2", "2026-11-02",
            listOf(JournalLine("Cash", debit = 50.0), JournalLine("Sales", credit = 45.0)), "Sales invoice")
        assertTrue(DoubleEntryAccounting.auditEntry(good).balanced)
        assertFalse(DoubleEntryAccounting.auditEntry(bad).balanced)
    }

    @Test fun openingPeriodAndClosingBalancesAreSeparate() {
        val opening = listOf(JournalEntry("OB", "2026-11-01",
            listOf(JournalLine("Cash", debit = 100.0), JournalLine("Capital", credit = 100.0)), "Opening"))
        val period = listOf(JournalEntry("SALE", "2026-11-02",
            listOf(JournalLine("Cash", debit = 40.0), JournalLine("Sales", credit = 40.0)), "Sale"))
        val report = DoubleEntryAccounting.trialBalance(opening, period)
        assertTrue(report.allEntriesBalanced)
        assertTrue(report.openingBalanced)
        assertTrue(report.periodBalanced)
        assertTrue(report.closingBalanced)
        val cash = report.accounts.single { it.account == "Cash" }
        assertEquals(100.0, cash.openingDebit, 0.001)
        assertEquals(40.0, cash.periodDebit, 0.001)
        assertEquals(140.0, cash.closingDebit, 0.001)
    }

    @Test fun openingImbalanceIsNotHiddenByPeriodMovement() {
        val opening = listOf(JournalEntry("OB", "2026-11-01",
            listOf(JournalLine("Cash", debit = 100.0), JournalLine("Capital", credit = 90.0)), "Opening"))
        val period = listOf(JournalEntry("P", "2026-11-02",
            listOf(JournalLine("Cash", debit = 10.0), JournalLine("Sales", credit = 10.0)), "Sale"))
        val report = DoubleEntryAccounting.trialBalance(opening, period)
        assertFalse(report.openingBalanced)
        assertTrue(report.periodBalanced)
        assertFalse(report.closingBalanced)
        assertTrue(report.errors.any { it.contains("Opening balances") })
    }

    @Test fun partnerMovementsRemainFourSeparateCategories() {
        val audit = DoubleEntryAccounting.auditPartnerCapital(listOf(
            PartnerCapitalMovement(PartnerCapitalKind.CONTRIBUTION, 1000.0),
            PartnerCapitalMovement(PartnerCapitalKind.CAPITAL_RETURN, 100.0),
            PartnerCapitalMovement(PartnerCapitalKind.DIVIDEND_WITHDRAWAL, 50.0),
            PartnerCapitalMovement(PartnerCapitalKind.PROFIT_CAPITALIZATION, 200.0)
        ))
        assertEquals(1000.0, audit.contribution, 0.001)
        assertEquals(100.0, audit.capitalReturn, 0.001)
        assertEquals(50.0, audit.dividendWithdrawal, 0.001)
        assertEquals(200.0, audit.profitCapitalization, 0.001)
        assertTrue(audit.errors.isEmpty())
    }

    @Test fun rejectsNegativeAndZeroJournalLines() {
        val entry = JournalEntry("X", "2026-11-03",
            listOf(JournalLine("Cash", debit = -1.0), JournalLine("Sales", credit = 0.0)), "Test")
        assertFalse(DoubleEntryAccounting.auditEntry(entry).balanced)
    }
}
