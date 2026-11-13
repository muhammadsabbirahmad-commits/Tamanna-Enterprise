# Tamanna Enterprise — Shop Management System (Web)

A complete bilingual (Bengali/English) shop management, POS, inventory, due ledger, double-entry accounting, and partner profit-distribution web application built with **React, TypeScript, Vite, and Tailwind CSS**.

Rewritten from the Android (Kotlin/Jetpack Compose) repository `muhammadsabbirahmad-commits/Tamanna-Enterprise`.

## Features Ported

- **Dashboard**: Real-time KPI summary (Today's Sales, Purchases, Net Profit, Total Stock Value), quick module navigation, recent sales, low-stock overview, and one-click sample business data seeding.
- **Products & Inventory (`P-228622+`)**: Sequential product code generation, add/edit/delete (with optional Admin Delete PIN), Stock In/Out adjustments, sorting (`Newest`, `Oldest`, `Stock Out`, `Most Sold`), batch/expiry/low-stock limits, and stock history.
- **POS & Sales Management**: Cart-based POS checkout with barcode/code Enter entry, discount/payment method (`Cash`, `bKash`, `Bank`, `Due`), automatic stock validation & rollback on error, invoice editing (for fully paid/unreturned invoices), printable invoice view, and full **Sale Return & Exchange** workflow.
- **Purchases & Memo Parser**: New purchase recording with supplier payable settlement, date range presets, and text/CSV memo parser for bulk stock intake.
- **Customer & Supplier Dues**: Complete customer & supplier directories, balance tracking, payment collection/settlement with overpayment validation, and ledger histories.
- **Operating Expenses & Damage Loss**: Daily expense tracking and stock damage recording with largest-remainder paisa allocation across partners (`100%` rate validation).
- **Partners & Equity**: Partner directory, capital contributions, effective-dated 100% profit distribution snapshots, dividend withdrawals, and undistributed profit capitalization.
- **Double-Entry Accounting & Trial Balance**: Balanced journal ledger, opening/period/closing Trial Balance verification, opening balance editor, daily Cash/Bank manual entries (`SET`/`ADD`/`SUB`), manual cash accounts, and reconciliation warnings.
- **Profit Analytics, Charts & Calendar**: Daily profit & expense-recovery calculation (`BusinessProfitCalculator`), SVG multi-line financial trend chart, daily financial calendar, configurable printable/CSV reports, global search, themes (`green`, `blue`, `purple`, `dark`), Admin/Partner role switching, activity log, and JSON backup/restore.

## Web Adaptations

- **Local Persistence**: Uses browser `localStorage` scoped by business ID (replaces Android `SharedPreferences` & Firebase Cloud Sync; full JSON backup export/import is available in Settings).
- **Scanner & Memo Input**: Replaces Android ZXing camera & MLKit/Tesseract OCR with keyboard/scanner code entry in POS and a structured Memo Text/File Parser in Purchases.
- **Invoices & Reports**: Replaces Android `PdfDocument` with browser Print-to-PDF views and UTF-8 BOM CSV exports.

## Development & Build

```bash
npm install
npm run dev      # Starts Vite dev server on http://0.0.0.0:3000
npm run lint     # TypeScript type-check (tsc --noEmit)
npm run build    # Production build to dist/
```