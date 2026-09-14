ALTER TABLE ledger_transaction
    ADD KEY idx_ledger_transaction_book_active_date (book_id, deleted, kind, occurred_on, id),
    ADD KEY idx_ledger_transaction_book_account (book_id, account_id, deleted, occurred_on),
    ADD KEY idx_ledger_transaction_book_counterparty (book_id, counterparty_account_id, deleted, occurred_on),
    ADD KEY idx_ledger_transaction_book_category (book_id, category_id, deleted, occurred_on),
    ADD KEY idx_ledger_transaction_book_merchant (book_id, merchant_id, deleted, occurred_on),
    ADD KEY idx_ledger_transaction_book_member (book_id, member_id, deleted, occurred_on),
    ADD KEY idx_ledger_transaction_book_project (book_id, project_id, deleted, occurred_on);
