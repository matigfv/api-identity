--liquibase formatted sql
--changeset matigfv:11

CREATE TABLE households (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE household_members (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    household_id BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    level        INT NOT NULL,
    joined_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_household_members_household FOREIGN KEY (household_id) REFERENCES households (id) ON DELETE CASCADE,
    CONSTRAINT fk_household_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_household_members_user UNIQUE (user_id),
    CONSTRAINT uq_household_members_household_user UNIQUE (household_id, user_id),
    CONSTRAINT ck_household_members_level CHECK (level >= 0)
);
