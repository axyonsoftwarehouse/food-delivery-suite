-- A V069 criou next_charge_at sem preencher as assinaturas que já existiam. Sem ele, o job nunca gerava a
-- cobrança, mas o atraso (pelo ends_at) corria: no staging, as três lojas ficaram em atraso desde 27/10/2026
-- sem nenhum Pix gerado. Preenche pelo mesmo critério do código: o trial é cobrado no fim dele
-- (ends_at == next_charge_at); a ativa, 3 dias antes do vencimento (SUBSCRIPTIONS_REMIND_BEFORE_DAYS padrão).
UPDATE restaurant_subscriptions
   SET next_charge_at = CASE
         WHEN status = 'trial' THEN COALESCE(trial_ends_at, ends_at, NOW())
         ELSE COALESCE(DATE_SUB(ends_at, INTERVAL 3 DAY), NOW())
       END
 WHERE next_charge_at IS NULL
   AND status IN ('trial', 'active', 'suspended');
