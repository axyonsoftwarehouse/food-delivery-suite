-- Vinculação do Mercado Pago em duas etapas: o retorno do provedor (domínio da API, sem sessão) guarda o
-- código de autorização cifrado e um token de confirmação; a conexão só acontece quando o painel confirma
-- com a sessão do MESMO dono que iniciou. Sem isso, o link de autorização de uma loja aberto por outra
-- pessoa ligava a conta Mercado Pago dessa pessoa à loja de quem gerou o link.
ALTER TABLE payment_oauth_states
  ADD COLUMN auth_code_enc TEXT NULL AFTER code_verifier_enc,
  ADD COLUMN confirm_token_hash CHAR(64) NULL AFTER auth_code_enc,
  ADD COLUMN returned_at TIMESTAMP NULL AFTER confirm_token_hash,
  ADD UNIQUE KEY uq_payment_oauth_confirm_token (confirm_token_hash);
