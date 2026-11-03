#!/usr/bin/env node
/**
 * Confere que as variáveis lidas pela API estão declaradas no serviço da API dos dois
 * docker-compose (local e deploy). O `.env` só chega ao container pelo mapeamento
 * `environment:`: uma variável ausente ali fica no valor padrão para sempre, mesmo que
 * esteja preenchida no `.env`.
 *
 * Foi assim que a cobrança real da assinatura ficou desligada no staging: as
 * SUBSCRIPTIONS_* existiam no application.yml, mas o compose não as repassava.
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const appYml = readFileSync(join(root, 'apps/api-java/src/main/resources/application.yml'), 'utf8');

const COMPOSES = [
  { file: 'docker-compose.yml', service: 'api-java' },
  { file: 'deploy/docker-compose.yml', service: 'api' },
];

// Lida só pelo contêiner de migration, não pela API.
const FIXED_ELSEWHERE = new Set(['MIGRATE_ONLY']);

// Lidas direto pelo Spring Boot (SMTP), fora do application.yml.
const REQUIRED_EXTRA = [
  'SPRING_MAIL_HOST',
  'SPRING_MAIL_PORT',
  'SPRING_MAIL_USERNAME',
  'SPRING_MAIL_PASSWORD',
  'SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH',
  'SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE',
];

function serviceBlock(text, service) {
  const lines = text.split(/\r?\n/);
  const start = lines.findIndex((line) => new RegExp(`^  ${service}:\\s*$`).test(line));
  if (start < 0) return null;
  let end = lines.length;
  for (let i = start + 1; i < lines.length; i += 1) {
    if (/^  [A-Za-z0-9_-]+:\s*$/.test(lines[i])) {
      end = i;
      break;
    }
  }
  return lines.slice(start, end).join('\n');
}

const declared = [...new Set([...appYml.matchAll(/\$\{([A-Z0-9_]+)(?::[^}]*)?\}/g)].map((match) => match[1]))]
  .filter((name) => !FIXED_ELSEWHERE.has(name))
  .sort();

const problems = [];
let checked = 0;

for (const { file, service } of COMPOSES) {
  const text = readFileSync(join(root, file), 'utf8');
  const block = serviceBlock(text, service);
  if (block === null) {
    problems.push(`${file}: serviço "${service}" não encontrado`);
    continue;
  }
  const required = [...new Set([...declared, ...REQUIRED_EXTRA])].sort();
  for (const name of required) {
    checked += 1;
    if (!new RegExp(`^\\s+${name}:`, 'm').test(block)) {
      problems.push(`${file}: ${service} não repassa ${name}`);
    }
  }
}

if (problems.length) {
  console.error('\n✖ Compose: variável lida pela API fora do serviço');
  for (const problem of problems) console.error(`    ${problem}`);
  console.error('\n    (o .env não chega ao container se o compose não declarar a variável)');
  process.exit(1);
}

console.log(`Compose: ${checked} variáveis da API declaradas nos dois arquivos.`);
