-- Relogio (xadrez e damas) e proposta de empate.
--
-- O relogio guarda o tempo restante de cada jogador no inicio da vez atual e quando a vez
-- comecou. turn_deadline_at e derivado (inicio da vez + tempo de quem joga), mas fica gravado
-- para o verificador de tempo esgotado achar as partidas vencidas por indice.
ALTER TABLE games ADD COLUMN time_control_ms BIGINT;
ALTER TABLE games ADD COLUMN challenger_time_ms BIGINT;
ALTER TABLE games ADD COLUMN opponent_time_ms BIGINT;
ALTER TABLE games ADD COLUMN turn_started_at TIMESTAMPTZ;
ALTER TABLE games ADD COLUMN turn_deadline_at TIMESTAMPTZ;
ALTER TABLE games ADD COLUMN draw_offered_by BIGINT;

ALTER TABLE games ADD CONSTRAINT fk_games_draw_offered_by FOREIGN KEY (draw_offered_by) REFERENCES users (id);
ALTER TABLE games ADD CONSTRAINT ck_games_time_control CHECK (time_control_ms IS NULL OR time_control_ms > 0);

CREATE INDEX idx_games_turn_deadline ON games (turn_deadline_at) WHERE turn_deadline_at IS NOT NULL;
