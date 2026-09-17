CREATE TABLE IF NOT EXISTS ledger_book (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    revision BIGINT NOT NULL DEFAULT 1,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_book_public_id (public_id),
    KEY idx_ledger_book_owner (owner_user_id, deleted),
    CONSTRAINT fk_ledger_book_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    book_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_role_public_id (public_id),
    UNIQUE KEY uk_ledger_role_book_code (book_id, code),
    CONSTRAINT fk_ledger_role_book FOREIGN KEY (book_id) REFERENCES ledger_book(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE ledger_role
    ADD COLUMN created_by BIGINT NULL AFTER deleted_at;
UPDATE ledger_role SET created_by = (SELECT owner_user_id FROM ledger_book WHERE ledger_book.id = ledger_role.book_id)
WHERE created_by IS NULL;
ALTER TABLE ledger_role
    MODIFY created_by BIGINT NOT NULL,
    ADD CONSTRAINT fk_ledger_role_creator FOREIGN KEY (created_by) REFERENCES app_user(id);

CREATE TABLE IF NOT EXISTS ledger_role_permission (
    role_id BIGINT NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_ledger_role_permission_role FOREIGN KEY (role_id) REFERENCES ledger_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_book_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    book_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_member_public_id (public_id),
    UNIQUE KEY uk_ledger_member_book_user (book_id, user_id),
    KEY idx_ledger_member_user (user_id, deleted),
    CONSTRAINT fk_ledger_member_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    CONSTRAINT fk_ledger_member_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_ledger_member_role FOREIGN KEY (role_id) REFERENCES ledger_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE ledger_book_member
    ADD COLUMN created_by BIGINT NULL AFTER deleted_at;
UPDATE ledger_book_member SET created_by = user_id WHERE created_by IS NULL;
ALTER TABLE ledger_book_member
    MODIFY created_by BIGINT NOT NULL,
    ADD CONSTRAINT fk_ledger_member_creator FOREIGN KEY (created_by) REFERENCES app_user(id);

CREATE TABLE IF NOT EXISTS ledger_merchant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    book_id BIGINT NOT NULL,
    name VARCHAR(180) NOT NULL,
    note VARCHAR(500) NOT NULL DEFAULT '',
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_merchant_public_id (public_id),
    KEY idx_ledger_merchant_book (book_id, deleted, hidden),
    CONSTRAINT fk_ledger_merchant_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    CONSTRAINT fk_ledger_merchant_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_project (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    book_id BIGINT NOT NULL,
    name VARCHAR(180) NOT NULL,
    color VARCHAR(32) NOT NULL DEFAULT '#0f5132',
    note VARCHAR(500) NOT NULL DEFAULT '',
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_project_public_id (public_id),
    KEY idx_ledger_project_book (book_id, deleted, hidden),
    CONSTRAINT fk_ledger_project_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    CONSTRAINT fk_ledger_project_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ledger_book (public_id, owner_user_id, name)
SELECT UUID(), u.id, '默认账本'
FROM app_user u
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_book b WHERE b.owner_user_id = u.id AND b.deleted = FALSE
);

INSERT INTO ledger_role (public_id, book_id, code, name, system_role, created_by)
SELECT UUID(), b.id, role_seed.code, role_seed.name, TRUE, b.owner_user_id
FROM ledger_book b
JOIN (
    SELECT 'OWNER' code, '账本主人' name
    UNION ALL SELECT 'ADMIN', '管理员'
    UNION ALL SELECT 'MEMBER', '成员'
) role_seed
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_role r WHERE r.book_id = b.id AND r.code = role_seed.code
);

INSERT INTO ledger_role_permission (role_id, permission_code)
SELECT r.id, permission_seed.permission_code
FROM ledger_role r
JOIN (
    SELECT 'OWNER' role_code, 'BOOK_DELETE' permission_code
    UNION ALL SELECT 'OWNER', 'RESOURCE_MANAGE'
    UNION ALL SELECT 'OWNER', 'MEMBER_MANAGE'
    UNION ALL SELECT 'OWNER', 'ROLE_MANAGE'
    UNION ALL SELECT 'OWNER', 'TRANSACTION_ANY_WRITE'
    UNION ALL SELECT 'OWNER', 'TRANSACTION_OWN_WRITE'
    UNION ALL SELECT 'OWNER', 'AUDIT_ALL_READ'
    UNION ALL SELECT 'OWNER', 'AUDIT_ALL_CLEAR'
    UNION ALL SELECT 'OWNER', 'AUDIT_SELF_READ'
    UNION ALL SELECT 'OWNER', 'RECYCLE_ALL'
    UNION ALL SELECT 'OWNER', 'RECYCLE_SELF'
    UNION ALL SELECT 'OWNER', 'IMPORT_EXPORT'
    UNION ALL SELECT 'ADMIN', 'RESOURCE_MANAGE'
    UNION ALL SELECT 'ADMIN', 'MEMBER_MANAGE'
    UNION ALL SELECT 'ADMIN', 'ROLE_MANAGE'
    UNION ALL SELECT 'ADMIN', 'TRANSACTION_ANY_WRITE'
    UNION ALL SELECT 'ADMIN', 'TRANSACTION_OWN_WRITE'
    UNION ALL SELECT 'ADMIN', 'AUDIT_ALL_READ'
    UNION ALL SELECT 'ADMIN', 'AUDIT_ALL_CLEAR'
    UNION ALL SELECT 'ADMIN', 'AUDIT_SELF_READ'
    UNION ALL SELECT 'ADMIN', 'RECYCLE_ALL'
    UNION ALL SELECT 'ADMIN', 'RECYCLE_SELF'
    UNION ALL SELECT 'ADMIN', 'IMPORT_EXPORT'
    UNION ALL SELECT 'MEMBER', 'TRANSACTION_OWN_WRITE'
    UNION ALL SELECT 'MEMBER', 'AUDIT_SELF_READ'
    UNION ALL SELECT 'MEMBER', 'RECYCLE_SELF'
) permission_seed ON permission_seed.role_code = r.code
LEFT JOIN ledger_role_permission existing
    ON existing.role_id = r.id AND existing.permission_code = permission_seed.permission_code
WHERE existing.role_id IS NULL;

INSERT INTO ledger_book_member (public_id, book_id, user_id, role_id, created_by)
SELECT UUID(), b.id, b.owner_user_id, r.id, b.owner_user_id
FROM ledger_book b
JOIN ledger_role r ON r.book_id = b.id AND r.code = 'OWNER'
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_book_member m WHERE m.book_id = b.id AND m.user_id = b.owner_user_id
);

ALTER TABLE ledger_account
    ADD COLUMN public_id CHAR(36) NULL AFTER id,
    ADD COLUMN book_id BIGINT NULL AFTER user_id,
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE AFTER revision,
    ADD COLUMN deleted_at TIMESTAMP NULL AFTER deleted,
    ADD COLUMN created_by BIGINT NULL AFTER deleted_at;

UPDATE ledger_account a
JOIN ledger_book b ON b.owner_user_id = a.user_id AND b.deleted = FALSE
SET a.public_id = COALESCE(a.public_id, UUID()),
    a.book_id = b.id,
    a.created_by = a.user_id
WHERE a.book_id IS NULL;

ALTER TABLE ledger_account
    MODIFY public_id CHAR(36) NOT NULL,
    MODIFY book_id BIGINT NOT NULL,
    MODIFY created_by BIGINT NOT NULL,
    ADD UNIQUE KEY uk_ledger_account_public_id (public_id),
    ADD KEY idx_ledger_account_book (book_id, deleted, hidden),
    ADD CONSTRAINT fk_ledger_account_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    ADD CONSTRAINT fk_ledger_account_creator FOREIGN KEY (created_by) REFERENCES app_user(id);

ALTER TABLE ledger_category
    ADD COLUMN public_id CHAR(36) NULL AFTER id,
    ADD COLUMN book_id BIGINT NULL AFTER user_id,
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE AFTER revision,
    ADD COLUMN deleted_at TIMESTAMP NULL AFTER deleted,
    ADD COLUMN created_by BIGINT NULL AFTER deleted_at;

UPDATE ledger_category c
JOIN ledger_book b ON b.owner_user_id = c.user_id AND b.deleted = FALSE
SET c.public_id = COALESCE(c.public_id, UUID()),
    c.book_id = b.id,
    c.created_by = c.user_id
WHERE c.book_id IS NULL;

ALTER TABLE ledger_category
    MODIFY public_id CHAR(36) NOT NULL,
    MODIFY book_id BIGINT NOT NULL,
    MODIFY created_by BIGINT NOT NULL,
    ADD UNIQUE KEY uk_ledger_category_public_id (public_id),
    ADD KEY idx_ledger_category_book (book_id, deleted, hidden),
    ADD CONSTRAINT fk_ledger_category_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    ADD CONSTRAINT fk_ledger_category_creator FOREIGN KEY (created_by) REFERENCES app_user(id);

ALTER TABLE ledger_budget
    ADD COLUMN public_id CHAR(36) NULL AFTER id,
    ADD COLUMN book_id BIGINT NULL AFTER user_id,
    ADD COLUMN deleted_at TIMESTAMP NULL AFTER deleted,
    ADD COLUMN created_by BIGINT NULL AFTER deleted_at;

UPDATE ledger_budget budget
JOIN ledger_book b ON b.owner_user_id = budget.user_id AND b.deleted = FALSE
SET budget.public_id = COALESCE(budget.public_id, UUID()),
    budget.book_id = b.id,
    budget.created_by = budget.user_id
WHERE budget.book_id IS NULL;

ALTER TABLE ledger_budget
    MODIFY public_id CHAR(36) NOT NULL,
    MODIFY book_id BIGINT NOT NULL,
    MODIFY created_by BIGINT NOT NULL,
    ADD UNIQUE KEY uk_ledger_budget_public_id (public_id),
    ADD KEY idx_ledger_budget_book (book_id, deleted),
    ADD CONSTRAINT fk_ledger_budget_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    ADD CONSTRAINT fk_ledger_budget_creator FOREIGN KEY (created_by) REFERENCES app_user(id);

ALTER TABLE ledger_transaction
    ADD COLUMN public_id CHAR(36) NULL AFTER id,
    ADD COLUMN book_id BIGINT NULL AFTER user_id,
    ADD COLUMN merchant_id BIGINT NULL AFTER category_id,
    ADD COLUMN member_id BIGINT NULL AFTER merchant_id,
    ADD COLUMN project_id BIGINT NULL AFTER member_id,
    ADD COLUMN deleted_at TIMESTAMP NULL AFTER deleted,
    ADD COLUMN created_by BIGINT NULL AFTER deleted_at;

UPDATE ledger_transaction t
JOIN ledger_book b ON b.owner_user_id = t.user_id AND b.deleted = FALSE
SET t.public_id = COALESCE(t.public_id, UUID()),
    t.book_id = b.id,
    t.created_by = t.user_id
WHERE t.book_id IS NULL;

INSERT INTO ledger_merchant (public_id, book_id, name, created_by)
SELECT UUID(), t.book_id, TRIM(t.payee), MIN(t.created_by)
FROM ledger_transaction t
WHERE TRIM(t.payee) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM ledger_merchant m
      WHERE m.book_id = t.book_id AND m.name = TRIM(t.payee) AND m.deleted = FALSE
  )
GROUP BY t.book_id, TRIM(t.payee);

INSERT INTO ledger_project (public_id, book_id, name, created_by)
SELECT UUID(), t.book_id, TRIM(t.project_name), MIN(t.created_by)
FROM ledger_transaction t
WHERE TRIM(t.project_name) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM ledger_project p
      WHERE p.book_id = t.book_id AND p.name = TRIM(t.project_name) AND p.deleted = FALSE
  )
GROUP BY t.book_id, TRIM(t.project_name);

UPDATE ledger_transaction t
JOIN ledger_merchant m ON m.book_id = t.book_id AND m.name = TRIM(t.payee) AND m.deleted = FALSE
SET t.merchant_id = m.id
WHERE TRIM(t.payee) <> '';

UPDATE ledger_transaction t
JOIN ledger_project p ON p.book_id = t.book_id AND p.name = TRIM(t.project_name) AND p.deleted = FALSE
SET t.project_id = p.id
WHERE TRIM(t.project_name) <> '';

UPDATE ledger_transaction t
JOIN ledger_book_member bm ON bm.book_id = t.book_id AND bm.deleted = FALSE
JOIN app_user u ON u.id = bm.user_id
SET t.member_id = bm.id
WHERE TRIM(t.member_name) <> ''
  AND (u.username = TRIM(t.member_name) OR u.nickname = TRIM(t.member_name));

ALTER TABLE ledger_transaction
    MODIFY public_id CHAR(36) NOT NULL,
    MODIFY book_id BIGINT NOT NULL,
    MODIFY created_by BIGINT NOT NULL,
    ADD UNIQUE KEY uk_ledger_transaction_public_id (public_id),
    ADD KEY idx_ledger_transaction_book_date (book_id, occurred_on, deleted),
    ADD KEY idx_ledger_transaction_merchant (merchant_id),
    ADD KEY idx_ledger_transaction_member (member_id),
    ADD KEY idx_ledger_transaction_project (project_id),
    ADD CONSTRAINT fk_ledger_transaction_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    ADD CONSTRAINT fk_ledger_transaction_merchant FOREIGN KEY (merchant_id) REFERENCES ledger_merchant(id),
    ADD CONSTRAINT fk_ledger_transaction_member FOREIGN KEY (member_id) REFERENCES ledger_book_member(id),
    ADD CONSTRAINT fk_ledger_transaction_project FOREIGN KEY (project_id) REFERENCES ledger_project(id),
    ADD CONSTRAINT fk_ledger_transaction_creator FOREIGN KEY (created_by) REFERENCES app_user(id);

ALTER TABLE ledger_balance_snapshot
    ADD COLUMN book_id BIGINT NULL AFTER user_id,
    ADD COLUMN snapshot_cursor BIGINT NOT NULL DEFAULT 0 AFTER balance,
    ADD COLUMN recalculated_at TIMESTAMP NULL AFTER calculated_at;

UPDATE ledger_balance_snapshot snapshot
JOIN ledger_account account ON account.id = snapshot.account_id
SET snapshot.book_id = account.book_id
WHERE snapshot.book_id IS NULL;

ALTER TABLE ledger_balance_snapshot
    MODIFY book_id BIGINT NOT NULL,
    ADD KEY idx_ledger_balance_book_month (book_id, month_key),
    ADD CONSTRAINT fk_ledger_balance_book FOREIGN KEY (book_id) REFERENCES ledger_book(id);

ALTER TABLE ledger_sync_oplog
    ADD COLUMN book_id BIGINT NULL AFTER user_id,
    ADD COLUMN actor_user_id BIGINT NULL AFTER book_id;

UPDATE ledger_sync_oplog oplog
JOIN ledger_book b ON b.owner_user_id = oplog.user_id AND b.deleted = FALSE
SET oplog.book_id = b.id,
    oplog.actor_user_id = oplog.user_id
WHERE oplog.book_id IS NULL;

ALTER TABLE ledger_sync_oplog
    MODIFY book_id BIGINT NOT NULL,
    MODIFY actor_user_id BIGINT NOT NULL,
    ADD KEY idx_ledger_sync_book_cursor (book_id, id),
    ADD CONSTRAINT fk_ledger_sync_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    ADD CONSTRAINT fk_ledger_sync_actor FOREIGN KEY (actor_user_id) REFERENCES app_user(id);

CREATE TABLE IF NOT EXISTS ledger_transaction_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    payload_json JSON NOT NULL,
    actor_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_transaction_version (transaction_id, revision),
    KEY idx_ledger_transaction_version_book (book_id, created_at),
    CONSTRAINT fk_ledger_version_transaction FOREIGN KEY (transaction_id) REFERENCES ledger_transaction(id),
    CONSTRAINT fk_ledger_version_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    CONSTRAINT fk_ledger_version_actor FOREIGN KEY (actor_user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ledger_transaction_version (
    transaction_id, book_id, revision, operation, payload_json, actor_user_id, created_at
)
SELECT t.id, t.book_id, t.revision,
       CASE WHEN t.deleted THEN 'DELETE' ELSE 'CREATE' END,
       JSON_OBJECT(
           'id', t.public_id,
           'accountId', a.public_id,
           'targetAccountId', target.public_id,
           'categoryId', c.public_id,
           'merchantId', m.public_id,
           'memberId', bm.public_id,
           'projectId', p.public_id,
           'kind', t.kind,
           'amount', t.amount,
           'currency', t.currency,
           'occurredOn', DATE_FORMAT(t.occurred_on, '%Y-%m-%d'),
           'payee', t.payee,
           'member', t.member_name,
           'project', t.project_name,
           'note', t.note,
           'deleted', t.deleted
       ),
       t.created_by,
       t.created_at
FROM ledger_transaction t
JOIN ledger_account a ON a.id = t.account_id
LEFT JOIN ledger_account target ON target.id = t.counterparty_account_id
LEFT JOIN ledger_category c ON c.id = t.category_id
LEFT JOIN ledger_merchant m ON m.id = t.merchant_id
LEFT JOIN ledger_book_member bm ON bm.id = t.member_id
LEFT JOIN ledger_project p ON p.id = t.project_id
LEFT JOIN ledger_transaction_version existing
    ON existing.transaction_id = t.id AND existing.revision = t.revision
WHERE existing.id IS NULL;

CREATE TABLE IF NOT EXISTS ledger_import_batch (
    id CHAR(36) NOT NULL,
    book_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    template_type VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PREVIEW',
    payload_json JSON NOT NULL,
    valid_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    duplicate_count INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    committed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ledger_import_book (book_id, created_at),
    CONSTRAINT fk_ledger_import_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    CONSTRAINT fk_ledger_import_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    book_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_public_id VARCHAR(120) NOT NULL DEFAULT '',
    before_json JSON NULL,
    after_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ledger_audit_book_created (book_id, created_at),
    KEY idx_ledger_audit_actor_created (actor_user_id, created_at),
    CONSTRAINT fk_ledger_audit_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    CONSTRAINT fk_ledger_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_ai_draft (
    id CHAR(36) NOT NULL,
    book_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PREVIEW',
    idempotency_key VARCHAR(160) NULL,
    expires_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_ai_idempotency (book_id, user_id, idempotency_key),
    KEY idx_ledger_ai_book_created (book_id, created_at),
    CONSTRAINT fk_ledger_ai_book FOREIGN KEY (book_id) REFERENCES ledger_book(id),
    CONSTRAINT fk_ledger_ai_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
