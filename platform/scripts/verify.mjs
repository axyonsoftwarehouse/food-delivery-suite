import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const defaultJavaHome = process.env.JAVA_HOME ?? (existsSync('C:\\Users\\werne\\tools\\jdk-21') ? 'C:\\Users\\werne\\tools\\jdk-21' : undefined);
const integration = process.env.VERIFY_INTEGRATION === '1';
// No PC o Maven roda offline (-o), apoiado no cache local .m2-cache, que não é
// versionado. Num runner limpo esse cache nasce vazio, então o CI liga
// VERIFY_ONLINE=1 e o Maven baixa o que precisa.
const mvnOffline = process.env.VERIFY_ONLINE === '1' ? [] : ['-o'];
let failures = 0;

function run(name, command, args, { cwd = root, env = {} } = {}) {
  process.stdout.write(`\n▶ ${name}\n`);
  const result = spawnSync(command, args, { cwd, stdio: 'inherit', shell: true, env: { ...process.env, ...env } });
  if (result.status !== 0) {
    failures += 1;
    console.error(`\n✖ ${name} falhou (código ${result.status ?? 'desconhecido'})`);
  } else {
    console.log(`✔ ${name}`);
  }
}

function envFile(path) {
  const values = {};
  if (!existsSync(path)) return values;
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (match) values[match[1]] = match[2].replace(/^["']|["']$/g, '');
  }
  return values;
}

async function waitForReady(url, attempts = 60, timeoutMs = 5000) {
  // Sem prazo por tentativa, o fetch pendura para sempre quando a porta está
  // publicada mas o app ainda não responde (o caso do Docker durante a subida).
  // Cada tentativa agora tem prazo próprio, e a espera tem limite de tentativas.
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(timeoutMs) });
      if (response.ok) return true;
    } catch { /* ainda subindo, sem resposta, ou estourou o prazo da tentativa */ }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  return false;
}

// --- Verificação sempre executada (sem banco) ---
run('Testes Java (api-java)', 'mvn', [...mvnOffline, '-q', '-Dmaven.repo.local=.m2-cache', 'test'], { cwd: join(root, 'apps/api-java'), env: { JAVA_HOME: defaultJavaHome } });
run('Tipos do site (tsc)', 'npx', ['tsc', '--noEmit'], { cwd: join(root, 'apps/web') });
run('Build do site (next build)', 'npm', ['run', 'build'], { cwd: join(root, 'apps/web') });
run('Tipos do app da cozinha (tsc)', 'pnpm', ['--filter', '@foodie/kitchen', 'typecheck'], { cwd: root });
run('Testes do app da cozinha (jest)', 'pnpm', ['--filter', '@foodie/kitchen', 'test'], { cwd: root });
run('Tipos do cliente de API (tsc)', 'pnpm', ['--filter', '@foodie/api-client', 'typecheck'], { cwd: root });
run('Dockerfiles (COPY aponta para arquivo existente)', 'node', ['scripts/check-dockerfiles.mjs'], { cwd: root });

// --- Verificação integrada com banco efêmero (opt-in) ---
if (integration) {
  const platformEnv = envFile(join(root, '.env'));
  const toolsEnv = envFile(join(root, 'tools/.env'));
  const dbPassword = platformEnv.DB_PASSWORD;
  const dbRootPassword = platformEnv.DB_ROOT_PASSWORD;
  const demoPassword = toolsEnv.DEMO_PASSWORD ?? platformEnv.DEMO_PASSWORD;
  if (!dbPassword || !dbRootPassword || !demoPassword) {
    console.error('\n✖ VERIFY_INTEGRATION=1 exige DB_PASSWORD/DB_ROOT_PASSWORD em platform/.env e DEMO_PASSWORD em platform/tools/.env');
    process.exit(1);
  }
  const composeEnv = { ...process.env, DB_PASSWORD: dbPassword, DB_ROOT_PASSWORD: dbRootPassword };
  const base = 'http://127.0.0.1:4101';

  // Uma execução anterior morreu dentro da espera e deixou os contêineres de teste
  // de pé, sem limpeza. O handler de saída é síncrono (spawnSync) e roda em
  // qualquer saída, inclusive a anormal.
  let testeNoAr = false;
  const derrubarTeste = () => spawnSync('docker', ['compose', '-f', 'docker-compose.test.yml', 'down', '-v'],
    { cwd: root, stdio: 'ignore', shell: true, env: composeEnv });
  process.on('exit', (codigo) => {
    if (!testeNoAr) return;
    process.stderr.write(`\n(verificação saiu com ${codigo} — derrubando os contêineres de teste)\n`);
    derrubarTeste();
  });

  try {
    run('Subindo banco/API de teste efêmeros', 'docker', ['compose', '-f', 'docker-compose.test.yml', 'up', '-d', '--build'], { env: composeEnv });
    testeNoAr = true;
    if (!(await waitForReady(`${base}/ready`))) {
      failures += 1;
      console.error('\n✖ API de teste não ficou pronta — últimas linhas do contêiner:');
      spawnSync('docker', ['logs', '--tail', '30', 'foodie-test-test-api-1'],
        { cwd: root, stdio: 'inherit', shell: true, env: composeEnv });
    } else {
      run('Seed demonstrativo no banco de teste', 'pnpm', ['--filter', '@foodie/tools', 'seed'], {
        env: { DB_HOST: '127.0.0.1', DB_PORT: '3308', DB_USER: 'foodie', DB_PASSWORD: dbPassword, DB_NAME: 'foodie_platform', DEMO_PASSWORD: demoPassword },
      });
      const smokeEnv = { API_PORT: '4101', API_INTERNAL_URL: base, DEMO_PASSWORD: demoPassword };
      run('Smoke: fluxo completo', 'pnpm', ['--filter', '@foodie/tools', 'smoke'], { env: smokeEnv });
      run('Smoke: carrinho', 'pnpm', ['smoke:cart'], { env: smokeEnv });
      run('Smoke: exceções', 'pnpm', ['smoke:exceptions'], { env: smokeEnv });
      run('Smoke: contas', 'pnpm', ['smoke:auth'], { env: smokeEnv });
      run('Smoke: cobertura por CEP', 'pnpm', ['smoke:coverage'], { env: smokeEnv });
    }
  } finally {
    run('Derrubando banco de teste efêmero', 'docker', ['compose', '-f', 'docker-compose.test.yml', 'down', '-v'], { env: composeEnv });
    testeNoAr = false;
  }
}

if (failures > 0) {
  console.error(`\n${failures} verificação(ões) falharam.`);
  process.exit(1);
}
console.log('\nTodas as verificações passaram.');
