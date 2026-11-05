# Double-Entry Accounting Audit — Tamanna Enterprise

Status: audit findings only; no accounting implementation in this commit.

## Critical findings in the current ledger implementation

1. `LedgerRow` stores one debit account, one credit account and one amount. The UI sums the same row amount once on each side. Therefore total debit and total credit are equal by construction; this equality is not an independent proof that source transactions or account balances are correct.
2. The report mixes period activity with balances. Most transactions are filtered to the selected date range, while current stock, total expenses, current partner capital and manual cash accounts are lifetime/current values. Those figures cannot be added together as if they were one period trial balance.
3. Current partner capital (`Partner.shareAmount`) is displayed separately. Historical contributions have legacy migration dates that may be estimates. A report must distinguish gross historical deposits, capital returned, retained profit transferred to capital, opening capital and closing capital.
4. The previous opening-balance patch used a generic “opening asset adjustment” as a balancing account. That is not evidence of actual cash, stock, receivables or other assets. It must not be presented as a real asset balance. If a suspense account is used temporarily, it must be explicitly named, isolated and accompanied by a reconciliation warning.
5. Purchase entries are inferred by grouping purchases by date and supplier, then allocating supplier due proportionally. This can misstate invoice-level cash paid/payables when multiple purchases, partial payments, returns or adjustments exist.
6. Sales, customer collections, returns, supplier payments, expenses, damages and partner withdrawals are synthesized from separate stores. Each source must be reconciled by stable transaction ID and amount; a balanced pair of rows alone does not validate the source data.
7. Manual cash/bank account balances are stored separately and are not linked to the transaction journal. They must not be silently added to journal cash.

## Required accounting model before implementation

- Use journal entries with one or more debit postings and one or more credit postings, not a single pair of account labels.
- Validate every journal entry independently: total debits = total credits, amounts are finite/non-negative, and source IDs are unique/idempotent.
- Build an account ledger and trial balance with three distinct columns: opening balance, period debit/credit movement, and closing balance.
- Partner cash contribution: Dr Cash/Bank; Cr Partner Capital.
- Partner capital returned: Dr Partner Capital; Cr Cash/Bank.
- Retained profit capitalized: Dr Retained Earnings; Cr Partner Capital. This does not increase cash.
- Inventory bought: Dr Inventory; Cr Cash/Bank and/or Supplier Payable according to the actual payment record.
- Sale: Dr Cash/Bank and Customer Receivable; Cr Sales Revenue. Separately, Dr Cost of Goods Sold; Cr Inventory.
- Return: reverse revenue against actual cash refund/customer receivable reduction; restore inventory and reverse the historical cost of goods sold only for returned units.
- Expense: Dr Expense; Cr Cash/Bank or Payable according to actual settlement.
- Damage: Dr Damage Loss; Cr Inventory.
- Partner drawings/withdrawals must be distinguished from capital returns and from profit distributions.
- Opening balances must be entered once as of a defined opening date and balanced against verified opening assets, liabilities and equity. Do not invent an asset counterpart from partner capital alone.
- Do not mutate existing product, stock, sale, purchase, expense, partner or sync records as part of a reporting-only correction.

## Required test matrix

- Initial capital for one and multiple partners; subsequent deposits; capital returns; retained-profit capitalization.
- Current month, custom range, all-time, and date boundaries; no duplicate opening balance.
- Missing/partial legacy contribution history and estimated dates; deleted partner history.
- Cash purchase, supplier-credit purchase, partial supplier payment, multiple same-day invoices.
- Cash sale, partial credit sale, later customer collection, partial return, full return, exchange.
- Expense, damage, partner profit withdrawal, and cash/bank account reconciliation.
- Every journal entry balanced; trial-balance closing balances reconcile to source records; no duplicate source IDs; totals use integer paisa/decimal-safe arithmetic.

## Acceptance criteria

Do not merge until the implementation includes independent journal validation, opening/period/closing trial-balance logic, source reconciliation warnings, automated tests for the matrix above, and a successful signed Android Release build plus APK signature verification.
