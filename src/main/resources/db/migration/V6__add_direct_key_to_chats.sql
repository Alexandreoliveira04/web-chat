-- direct_key identifica o par de usuarios da conversa individual, sempre com o
-- menor id primeiro (ex.: "3:4"). O UNIQUE garante no banco uma unica conversa por par.
ALTER TABLE chats ADD COLUMN direct_key VARCHAR(40);

UPDATE chats c
SET direct_key = (
    SELECT MIN(p.user_id) || ':' || MAX(p.user_id)
    FROM chat_participants p
    WHERE p.chat_id = c.id
);

-- Conversas sem participantes ou duplicadas para o mesmo par (possiveis antes desta
-- migration) sao removidas, mantendo a mais antiga. Ainda nao existem mensagens.
DELETE FROM chats WHERE direct_key IS NULL;

DELETE FROM chats c
USING chats older
WHERE c.direct_key = older.direct_key
  AND c.id > older.id;

ALTER TABLE chats ALTER COLUMN direct_key SET NOT NULL;
ALTER TABLE chats ADD CONSTRAINT uk_chats_direct_key UNIQUE (direct_key);
