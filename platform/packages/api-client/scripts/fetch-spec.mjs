import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const base = process.env.API_BASE ?? 'http://127.0.0.1:4001';

const response = await fetch(`${base}/v3/api-docs`, { headers: { Accept: 'application/json' } });
if (!response.ok) {
  console.error(`Falha ao obter ${base}/v3/api-docs: ${response.status} ${response.statusText}`);
  process.exit(1);
}

const spec = await response.text();
const target = fileURLToPath(new URL('../openapi.json', import.meta.url));
writeFileSync(target, spec);
console.log(`openapi.json atualizado a partir de ${base}`);

// Nota: sem a API no ar, o spec pode ser regerado pelo teste Java:
//   (cd platform/apps/api-java && JAVA_HOME=... mvn -o "-Dopenapi.dump=true" -Dtest=OpenApiDumpTest test)
