#!/usr/bin/env node
/**
 * Confere que todo COPY dos Dockerfiles aponta para algo que existe no contexto de build.
 *
 * Motivo: em 01/10 o deploy quebrou na VPS porque `apps/web/Dockerfile` copiava
 * `apps/api/package.json` de uma pasta que tinha sido removida do repositório. O build do
 * site roda no host (`next build`), então a suíte inteira passava e o erro só aparecia no
 * deploy — depois do backup, no meio da janela de publicação. Este script antecipa isso
 * para a verificação local, em milissegundos.
 *
 * O contexto de cada Dockerfile é lido dos docker-compose do repositório (arquivo `context:`
 * ao lado do `dockerfile:`); quando o compose não declara um Dockerfile (caso do
 * `build: ../apps/api-java`), vale o padrão do Docker: a pasta do próprio Dockerfile.
 */
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const COMPOSES = ['docker-compose.yml', 'docker-compose.test.yml', 'deploy/docker-compose.yml'];

function contextosDoCompose() {
  const mapa = new Map();
  for (const nome of COMPOSES) {
    const caminho = join(root, nome);
    if (!existsSync(caminho)) continue;
    const pastaDoCompose = dirname(caminho);
    let contexto = null;
    for (const linha of readFileSync(caminho, 'utf8').split(/\r?\n/)) {
      const achouContexto = linha.match(/^\s*context:\s*(\S+)\s*$/);
      if (achouContexto) contexto = resolve(pastaDoCompose, achouContexto[1]);
      const achouDockerfile = linha.match(/^\s*dockerfile:\s*(\S+)\s*$/);
      if (achouDockerfile) {
        mapa.set(achouDockerfile[1].replace(/^\.\//, ''), contexto ?? pastaDoCompose);
      }
    }
  }
  return mapa;
}

function acharDockerfiles(pasta, achados = []) {
  for (const entrada of readdirSync(pasta, { withFileTypes: true })) {
    if (entrada.name === 'node_modules' || entrada.name === '.git' || entrada.name === '.next') continue;
    const caminho = join(pasta, entrada.name);
    if (entrada.isDirectory()) acharDockerfiles(caminho, achados);
    else if (/^Dockerfile(\..+)?$/.test(entrada.name)) achados.push(caminho);
  }
  return achados;
}

const contextos = contextosDoCompose();
const problemas = [];
let copiasConferidas = 0;

for (const arquivo of acharDockerfiles(root)) {
  const relArquivo = relative(root, arquivo).replace(/\\/g, '/');
  const contexto = contextos.get(relArquivo) ?? dirname(arquivo);

  for (const [numero, linha] of readFileSync(arquivo, 'utf8').split(/\r?\n/).entries()) {
    const copia = linha.match(/^\s*COPY\s+(.+)$/i);
    if (!copia) continue;
    const partes = copia[1].trim().split(/\s+/);
    if (partes[0].startsWith('--')) continue; // COPY --from=<estágio>: vem de outra etapa, não do contexto
    if (partes.length < 2) continue;          // sem destino declarado: o Docker já reclama

    for (const fonte of partes.slice(0, -1)) {
      if (/[*?[\]]/.test(fonte)) continue;    // glob: só o build confirma
      copiasConferidas += 1;
      if (!existsSync(join(contexto, fonte))) {
        const onde = (relative(root, contexto) || '.').replace(/\\/g, '/');
        problemas.push(`${relArquivo}:${numero + 1} — COPY ${fonte} não existe em ${onde}/`);
      }
    }
  }
}

if (problemas.length) {
  console.error('\n✖ Dockerfiles: COPY apontando para arquivo que não existe');
  for (const problema of problemas) console.error(`    ${problema}`);
  console.error('\n    (é assim que o build da imagem quebra no deploy — corrija antes de publicar)');
  process.exit(1);
}
console.log(`✔ Dockerfiles: ${copiasConferidas} COPY(s) apontam para arquivos existentes`);
