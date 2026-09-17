-- Allow a monthly ledger-wide budget (category_id IS NULL).
-- A category_id may reference either a primary or secondary expense category.
ALTER TABLE ledger_budget
    MODIFY category_id BIGINT NULL,
    DROP INDEX uk_ledger_budget_user_category_month,
    ADD COLUMN budget_scope BIGINT GENERATED ALWAYS AS (COALESCE(category_id, 0)) STORED,
    ADD UNIQUE KEY uk_ledger_budget_book_scope_month (book_id, budget_scope, month_key);
