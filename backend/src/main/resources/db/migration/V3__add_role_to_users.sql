-- Papel do usuario para controle de acesso (USER ou ADMIN).
--
-- O DEFAULT 'USER' preenche os usuarios ja existentes e garante que nenhum
-- cadastro nasca com privilegio de administrador.
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
