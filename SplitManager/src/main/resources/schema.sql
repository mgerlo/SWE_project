-- PULIZIA INIZIALE (Elimina le tabelle vecchie per ricrearle pulite)
DROP TABLE IF EXISTS settlements;
DROP TABLE IF EXISTS balances;
DROP TABLE IF EXISTS expense_participants;
DROP TABLE IF EXISTS expenses;
DROP TABLE IF EXISTS memberships;
DROP TABLE IF EXISTS groups;
DROP TABLE IF EXISTS users;

-- 1. Tabella USER
CREATE TABLE users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL
);

-- 2. Tabella GROUP
CREATE TABLE groups (
    group_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    currency VARCHAR(3) NOT NULL, -- Es: 'EUR', 'USD'
    invite_code VARCHAR(20),
    invite_code_expiry_date TIMESTAMP,
    creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (created_by_user_id) REFERENCES users(user_id)
);

-- 3. Tabella MEMBERSHIP (Collega Utenti e Gruppi)
CREATE TABLE memberships (
    membership_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,   -- 'ADMIN', 'MEMBER'
    status VARCHAR(20) NOT NULL, -- 'ACTIVE', 'WAITING_ACCEPTANCE', 'REMOVED'
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (group_id) REFERENCES groups(group_id)
);

-- 4. Tabella EXPENSE (Le spese)
CREATE TABLE expenses (
    expense_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    payer_membership_id BIGINT NOT NULL, -- Chi ha pagato
    created_by_membership BIGINT,        -- Chi ha registrato la spesa
    amount DECIMAL(10, 2) NOT NULL,      -- Usiamo DECIMAL per i soldi!
    description VARCHAR(255),
    category VARCHAR(50),
    expense_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,    -- Soft Delete
    FOREIGN KEY (group_id) REFERENCES groups(group_id),
    FOREIGN KEY (payer_membership_id) REFERENCES memberships(membership_id),
    FOREIGN KEY (created_by_membership) REFERENCES memberships(membership_id)
);

-- 5. Tabella EXPENSE_PARTICIPANT (Chi deve ridare i soldi per ogni spesa)
CREATE TABLE expense_participants (
    participant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id BIGINT NOT NULL,
    beneficiary_membership_id BIGINT NOT NULL, -- Chi beneficia della spesa
    share_amount DECIMAL(10, 2) NOT NULL,      -- La quota parte
    FOREIGN KEY (expense_id) REFERENCES expenses(expense_id),
    FOREIGN KEY (beneficiary_membership_id) REFERENCES memberships(membership_id)
);

-- 6. Tabella BALANCE (Snapshot dei saldi attuali)
CREATE TABLE balances (
    balance_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    membership_id BIGINT NOT NULL,
    net_balance DECIMAL(10, 2) DEFAULT 0.00,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (membership_id) REFERENCES memberships(membership_id)
);

-- 7. Tabella SETTLEMENT (I rimborsi per pareggiare i conti)
CREATE TABLE settlements (
    settlement_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    payer_membership_id BIGINT NOT NULL,    -- Chi invia i soldi
    receiver_membership_id BIGINT NOT NULL, -- Chi li riceve
    amount DECIMAL(10, 2) NOT NULL,
    settlement_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL,            -- 'PENDING', 'COMPLETED'
    FOREIGN KEY (group_id) REFERENCES groups(group_id),
    FOREIGN KEY (payer_membership_id) REFERENCES memberships(membership_id),
    FOREIGN KEY (receiver_membership_id) REFERENCES memberships(membership_id)
);