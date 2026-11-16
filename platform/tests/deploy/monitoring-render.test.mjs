// monitoring/render.sh: o alertmanager.rendered.yml é montado como secret e o Alertmanager roda como
// `nobody` (65534). Com o arquivo 600 do usuário deploy, o contêiner não lê ("permission denied") —
// aconteceu em 16/11. O script entrega o arquivo ao uid do contêiner (via `sudo -n chown`) e o troca
// por renomeação, para funcionar também quando o anterior já pertence ao 65534.
import assert from 'node:assert/strict';
import { chmodSync, existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { test } from 'node:test';
import { bash, pastaTemp, platform } from './_apoio.mjs';

const { equal: eq, match, notEqual, deepEqual } = assert;

/** Pasta com uma cópia do monitoring/ (render.sh em LF) e um `sudo` falso que registra a chamada. */
function cenario({ sudoFunciona = true } = {}) {
  const dir = pastaTemp();
  const lf = (f) => readFileSync(join(platform, 'monitoring', f), 'utf8').replace(/\r\n/g, '\n');
  writeFileSync(join(dir.node, 'render.sh'), lf('render.sh'));
  writeFileSync(join(dir.node, 'alertmanager.yml'), lf('alertmanager.yml'));
  writeFileSync(join(dir.node, '.env'), 'TELEGRAM_BOT_TOKEN=123:abc\nTELEGRAM_CHAT_ID=42\n');
  const bin = pastaTemp();
  const sudo = join(bin.node, 'sudo');
  writeFileSync(sudo, `#!/usr/bin/env bash\necho "$*" >> "${dir.bash}/sudo.log"\nexit ${sudoFunciona ? 0 : 1}\n`);
  chmodSync(sudo, 0o755);
  return { dir, env: { PATH: `${bin.node}${process.platform === 'win32' ? ';' : ':'}${process.env.PATH}` } };
}

const rodar = (c) => bash([`${c.dir.bash}/render.sh`], { env: c.env });
const temporarios = (c) => readdirSync(c.dir.node).filter((f) => f.startsWith('alertmanager.rendered.') && f !== 'alertmanager.rendered.yml');

test('gera o arquivo com os valores e entrega ao uid do Alertmanager', () => {
  const c = cenario();
  const r = rodar(c);
  eq(r.code, 0, r.err);
  const gerado = readFileSync(join(c.dir.node, 'alertmanager.rendered.yml'), 'utf8');
  match(gerado, /bot_token: '123:abc'/);
  match(gerado, /chat_id: 42/);
  match(readFileSync(join(c.dir.node, 'sudo.log'), 'utf8'), /^-n chown 65534:65534 /);
  deepEqual(temporarios(c), []);
});

test('sem sudo, falha com a instrução e não deixa arquivo pela metade', () => {
  const c = cenario({ sudoFunciona: false });
  writeFileSync(join(c.dir.node, 'alertmanager.rendered.yml'), 'anterior\n');
  const r = rodar(c);
  notEqual(r.code, 0);
  match(r.err, /chown 65534:65534/);
  eq(readFileSync(join(c.dir.node, 'alertmanager.rendered.yml'), 'utf8'), 'anterior\n');
  deepEqual(temporarios(c), []);
});

test('substitui um arquivo anterior sem permissão de escrita', () => {
  const c = cenario();
  const anterior = join(c.dir.node, 'alertmanager.rendered.yml');
  writeFileSync(anterior, 'anterior\n');
  chmodSync(anterior, 0o400);
  const r = rodar(c);
  eq(r.code, 0, r.err);
  match(readFileSync(anterior, 'utf8'), /bot_token: '123:abc'/);
  eq(existsSync(anterior), true);
});
