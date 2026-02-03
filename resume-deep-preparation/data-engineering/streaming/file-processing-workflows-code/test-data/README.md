# Test Data (Flow Start)

Sample Equilend files for the file-processing workflow. In production, these would land in S3 (e.g. `s3://bucket/positions/`, `s3://bucket/mtm_pnl/`) via Equilend drop.

## Layout

- **positions/** – Daily loan positions (Equilend Positions Report)
  - Naming: `equilend_positions_YYYYMMDD.csv`
  - Schema: loan_id, trade_date, settle_date, security_id, security_name, borrower_id, borrower_name, quantity, loan_value_usd, rebate_rate_bps, fee_rate_bps, collateral_type, collateral_value_usd, book_id, desk_id, source_system, file_date
- **mtm_pnl/** – Daily mark-to-market / P&L summary
  - Naming: `equilend_mtm_pnl_YYYYMMDD.csv`
  - Schema: file_date, book_id, desk_id, loan_id, mtm_value_prev_usd, mtm_value_current_usd, pnl_usd, rebate_income_usd, fee_income_usd, source_system

## Rows

- **positions**: 15 rows (file_date=2025-02-01)
- **mtm_pnl**: 15 rows (file_date=2025-02-01), keyed by (file_date, book_id, desk_id, loan_id)

## Validation Rules (Reference)

- Positions: no duplicate loan_id; quantity > 0; loan_value_usd >= 0; settle_date >= trade_date; non-empty security_id, borrower_id, book_id, desk_id.
- P&L: no duplicate (file_date, book_id, desk_id, loan_id); numerics parseable; non-empty file_date, book_id, desk_id, loan_id.

Next step in flow: **01-landing** (landing path config).
