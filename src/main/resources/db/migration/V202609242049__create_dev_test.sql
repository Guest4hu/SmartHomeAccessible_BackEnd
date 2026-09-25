-- 1. Criar o Utilizador de Teste (Gustavo)
-- O banco de dados atribuirá o ID 1 a este utilizador
INSERT INTO app_user (name, email, password_hash, active, created_at)
VALUES ('Gustavo (Dev Tester)', 'gustavo@email.com', '$2a$10$C4EcVzENmxLZD7v8J1O0PuodvyfpxTTdKidImoywN2.QRAt2vD4xm', true, UTC_TIMESTAMP());

-- 2. Criar a Casa de Teste
-- O banco de dados atribuirá o ID 1 a esta casa
INSERT INTO home (name, created_at) 
VALUES ('Laboratório de Automação', UTC_TIMESTAMP());

-- 3. Criar o Vínculo (HomeMember): O Gustavo (ID 1) é ADMIN da Casa (ID 1)
INSERT INTO home_member (home_id, user_id, role, joined_at) 
VALUES (1, 1, 'ADMIN', UTC_TIMESTAMP());

-- 4. Criar um Dispositivo de Teste para poder testar a Automação e Eventos
-- O banco de dados atribuirá o ID 1 a este dispositivo, ligado à Casa 1
INSERT INTO device (external_id, name, room, status, home_id, created_at) 
VALUES ('esp32-sala-01', 'Sensor Principal', 'Sala de Estar', 'OFFLINE', 1, UTC_TIMESTAMP());
