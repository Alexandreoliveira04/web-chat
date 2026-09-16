-- Conversas em grupo: nome, dono e tipo. As conversas existentes viram DIRECT.
ALTER TABLE chats ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'DIRECT';
ALTER TABLE chats ADD COLUMN name VARCHAR(100);
ALTER TABLE chats ADD COLUMN owner_id BIGINT;

ALTER TABLE chats ADD CONSTRAINT fk_chats_owner FOREIGN KEY (owner_id) REFERENCES users (id);

-- direct_key identifica o par e so existe em conversa individual; no grupo e nulo
-- (varios NULL convivem com o UNIQUE no PostgreSQL).
ALTER TABLE chats ALTER COLUMN direct_key DROP NOT NULL;

ALTER TABLE chats ADD CONSTRAINT ck_chats_type CHECK (
    (type = 'DIRECT' AND direct_key IS NOT NULL AND name IS NULL AND owner_id IS NULL)
    OR (type = 'GROUP' AND direct_key IS NULL AND name IS NOT NULL AND owner_id IS NOT NULL)
);
