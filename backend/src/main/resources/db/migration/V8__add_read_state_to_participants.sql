-- Leitura por participante: cada um guarda a ultima mensagem que leu.
-- Em grupo, messages.read_at nao serve: o primeiro que abrisse a conversa marcaria
-- as mensagens como lidas para todos os demais.
ALTER TABLE chat_participants ADD COLUMN last_read_message_id BIGINT;
ALTER TABLE chat_participants ADD COLUMN joined_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE chat_participants ADD CONSTRAINT fk_chat_participants_last_read
    FOREIGN KEY (last_read_message_id) REFERENCES messages (id) ON DELETE SET NULL;

-- Converte o estado atual: a ultima mensagem recebida e ja lida vira o marcador.
UPDATE chat_participants p
SET last_read_message_id = (
    SELECT MAX(m.id)
    FROM messages m
    WHERE m.chat_id = p.chat_id
      AND m.sender_id <> p.user_id
      AND m.read_at IS NOT NULL
);

ALTER TABLE messages DROP COLUMN read_at;
ALTER TABLE chat_participants ALTER COLUMN joined_at DROP DEFAULT;
