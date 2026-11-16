// Apoio dos testes dos scripts de deploy: roda bash de verdade, com arquivos temporários.
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const platform = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

// No Windows, `bash` no PATH do PowerShell pode ser o do WSL, que não enxerga C:/…; preferimos o do Git.
function acharBash() {
  if (process.env.FOODIE_BASH) return process.env.FOODIE_BASH;
  if (process.platform === 'win32') {
    for (const candidato of ['C:/Program Files/Git/bin/bash.exe', 'C:/Program Files/Git/usr/bin/bash.exe']) {
      if (existsSync(candidato)) return candidato;
    }
  }
  return 'bash';
}
export const BASH = acharBash();

const pasta = mkdtempSync(join(tmpdir(), 'foodie-deploy-'));
let contador = 0;

/**
 * Caminho que o bash entende. No Windows vira /c/…: o GNU tar trata "C:" como host remoto.
 */
export const paraBash = (caminho) => {
  const barras = caminho.replace(/\\/g, '/');
  return process.platform === 'win32' ? barras.replace(/^([A-Za-z]):/, (_, letra) => `/${letra.toLowerCase()}`) : barras;
};

/** Cria uma pasta temporária e devolve o caminho no formato do bash e no do Node. */
export function pastaTemp(nome = `dir-${++contador}`) {
  const caminho = join(pasta, nome);
  mkdirSync(caminho, { recursive: true });
  return { node: caminho, bash: paraBash(caminho) };
}

/** Grava um arquivo temporário e devolve o caminho nativo (para programas que não são o bash, como o docker). */
export function arquivoNativo(conteudo, nome = `arq-${++contador}`) {
  const caminho = join(pasta, nome);
  writeFileSync(caminho, conteudo);
  return caminho;
}

/** Grava um arquivo temporário e devolve o caminho para usar no bash. */
export const arquivo = (conteudo, nome) => paraBash(arquivoNativo(conteudo, nome));

/** Monta o texto de um .env a partir de um objeto; chave com valor undefined fica de fora. */
export function envTexto(variaveis) {
  return Object.entries(variaveis)
    .filter(([, valor]) => valor !== undefined)
    .map(([chave, valor]) => `${chave}=${valor}`)
    .join('\n') + '\n';
}

/** Roda um script ou comando no bash, dentro de platform/. */
export function bash(argumentos, { env = {}, input = '' } = {}) {
  const r = spawnSync(BASH, argumentos, { cwd: platform, encoding: 'utf8', input, env: { ...process.env, ...env } });
  return { code: r.status, out: (r.stdout ?? '').trim(), err: (r.stderr ?? '').trim() };
}
