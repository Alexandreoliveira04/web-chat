-- Foto do grupo. Conversa individual nao tem foto propria: usa a do outro participante.
ALTER TABLE chats ADD COLUMN avatar_url VARCHAR(500);

ALTER TABLE chats ADD CONSTRAINT ck_chats_avatar_only_group CHECK (
    avatar_url IS NULL OR type = 'GROUP'
);

-- A URL do Cloud Storage agora carrega o nome original do arquivo (codificado), e pode
-- passar de 255 caracteres.
ALTER TABLE users ALTER COLUMN avatar_url TYPE VARCHAR(500);
