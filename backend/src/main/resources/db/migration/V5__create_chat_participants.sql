-- A chave primaria composta impede que o mesmo usuario participe duas vezes da
-- mesma conversa. O indice em user_id atende a listagem "minhas conversas".
CREATE TABLE chat_participants (
    chat_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_chat_participants PRIMARY KEY (chat_id, user_id),
    CONSTRAINT fk_chat_participants_chat FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_participants_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_chat_participants_user_id ON chat_participants (user_id);
