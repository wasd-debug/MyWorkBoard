-- The legacy budget unique key also supports fk_ledger_budget_user.
-- V9 replaces that unique key, so preserve an explicit user_id index first.
ALTER TABLE ledger_budget
    ADD KEY idx_ledger_budget_user (user_id);
