ALTER TABLE ledger_account
    ADD COLUMN icon VARCHAR(40) NOT NULL DEFAULT 'wallet' AFTER name;

ALTER TABLE ledger_category
    ADD COLUMN icon VARCHAR(40) NOT NULL DEFAULT 'tag' AFTER name;

ALTER TABLE ledger_merchant
    ADD COLUMN icon VARCHAR(40) NOT NULL DEFAULT 'shop' AFTER name;

ALTER TABLE ledger_project
    ADD COLUMN icon VARCHAR(40) NOT NULL DEFAULT 'folder' AFTER name;

ALTER TABLE ledger_book_member
    ADD COLUMN icon VARCHAR(40) NOT NULL DEFAULT 'user' AFTER role_id;
