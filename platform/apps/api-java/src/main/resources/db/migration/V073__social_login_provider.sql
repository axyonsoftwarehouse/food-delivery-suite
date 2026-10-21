-- Login social explícito: guarda qual provedor reivindicou a conta. Sem isso, uma conta
-- criada com email/senha podia ser vinculada silenciosamente ao provedor social pelo email.
-- Contas sociais antigas não têm como ser identificadas com segurança: elas entram com a senha
-- (ou redefinem) e, da próxima vez, o vínculo explícito é gravado.
ALTER TABLE users
  ADD COLUMN social_provider VARCHAR(16) NULL DEFAULT NULL;
