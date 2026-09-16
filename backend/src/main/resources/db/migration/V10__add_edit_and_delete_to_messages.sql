-- Edicao e exclusao da propria mensagem. Apagar preserva a linha no historico:
-- o conteudo e esvaziado e deleted_at marca o momento.
ALTER TABLE messages ADD COLUMN edited_at TIMESTAMPTZ;
ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE messages DROP CONSTRAINT ck_messages_content_not_blank;
ALTER TABLE messages ADD CONSTRAINT ck_messages_content CHECK (
    deleted_at IS NOT NULL OR length(trim(content)) > 0
);
