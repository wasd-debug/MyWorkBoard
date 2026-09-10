ALTER TABLE ledger_transaction
    ADD COLUMN counterparty_account_id BIGINT NULL AFTER account_id,
    ADD COLUMN transfer_group_id VARCHAR(64) NULL AFTER counterparty_account_id,
    ADD COLUMN member_name VARCHAR(120) NOT NULL DEFAULT '' AFTER payee,
    ADD COLUMN project_name VARCHAR(180) NOT NULL DEFAULT '' AFTER member_name,
    ADD KEY idx_ledger_transaction_transfer_group (user_id, transfer_group_id),
    ADD KEY idx_ledger_transaction_counterparty (counterparty_account_id),
    ADD CONSTRAINT fk_ledger_transaction_counterparty
        FOREIGN KEY (counterparty_account_id) REFERENCES ledger_account(id);
