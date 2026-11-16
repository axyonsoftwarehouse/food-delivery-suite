// Implantação inicial de um ambiente limpo (a produção): cria o administrador e, se informados, os
// planos de assinatura. Não cria nada de demonstração. Pode rodar de novo sem duplicar: um
// administrador que já existe não tem a senha trocada.
//
// Entrada (variáveis de ambiente):
//   ADMIN_NAME, ADMIN_EMAIL  — o administrador
//   ADMIN_PASSWORD           — a senha, com pelo menos 12 caracteres. Na VPS ela chega pela entrada
//                              padrão do SSH (`read -r ADMIN_PASSWORD`), nunca pela linha de comando.
//   PLANOS_JSON              — opcional: [{"nome":"…","precoCentavos":9900,"dias":30}]
//
// O comando completo para a VPS está em docs/RUNBOOK_VPS.md ("Dois ambientes").
import 'dotenv/config';
import { db } from './db.js';
import { lerEntrada, lerPlanos } from './implantacao.js';
import { hashPassword } from './password.js';

type Linha = Record<string, unknown>;

async function main() {
  const admin = lerEntrada(process.env);
  const planos = lerPlanos(process.env.PLANOS_JSON);

  const papeis = (await db.query('SELECT id FROM admin_roles WHERE full_access = TRUE AND active = TRUE ORDER BY id LIMIT 1')) as Linha[];
  const papelId = papeis[0]?.id;
  if (papelId == null) throw new Error('Papel de acesso total não encontrado; rode as migrações antes da implantação inicial.');

  const existentes = (await db.query('SELECT id, role FROM users WHERE email = ?', [admin.email])) as Linha[];
  if (existentes.length > 0) {
    if (existentes[0].role !== 'admin') {
      throw new Error(`ADMIN_EMAIL: já existe uma conta com esse e-mail e ela não é de administrador (papel ${String(existentes[0].role)}).`);
    }
    console.log(`Administrador ${admin.email} já existe; a senha não foi alterada.`);
  } else {
    await db.query(
      "INSERT INTO users (name, email, password_hash, role, admin_role_id, email_verified_at) VALUES (?, ?, ?, 'admin', ?, NOW())",
      [admin.nome, admin.email, hashPassword(admin.senha), papelId],
    );
    console.log(`Administrador ${admin.email} criado com o papel de acesso total.`);
  }

  if (planos.length === 0) {
    console.log('PLANOS_JSON vazio: nenhum plano de assinatura criado. As lojas só assinam depois que houver plano.');
  }
  for (const plano of planos) {
    await db.query(
      'INSERT INTO subscription_packages (name, price_cents, period_days, commission_percent) VALUES (?, ?, ?, 0) '
        + 'ON DUPLICATE KEY UPDATE price_cents = VALUES(price_cents), period_days = VALUES(period_days)',
      [plano.nome, plano.precoCentavos, plano.dias],
    );
    console.log(`Plano "${plano.nome}": ${(plano.precoCentavos / 100).toFixed(2)} a cada ${plano.dias} dias.`);
  }
}

try {
  await main();
} catch (erro) {
  console.error(erro instanceof Error ? erro.message : erro);
  process.exitCode = 1;
} finally {
  await db.end();
}
