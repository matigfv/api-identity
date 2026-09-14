--liquibase formatted sql
--changeset matigfv:10

ALTER TABLE workspace_invitations
    ADD COLUMN expires_at DATETIME NULL;

-- Backfill para invitaciones existentes: creada + 7 días, mismo período que las nuevas.
UPDATE workspace_invitations
SET expires_at = DATE_ADD(created_at, INTERVAL 7 DAY)
WHERE expires_at IS NULL;

ALTER TABLE workspace_invitations
    MODIFY COLUMN expires_at DATETIME NOT NULL;
