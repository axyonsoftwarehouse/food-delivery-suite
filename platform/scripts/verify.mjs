import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const defaultJavaHome = process.env.JAVA_HOME ?? (existsSync('C:\\Users\\werne\\tools\\jdk-21') ? 'C:\\Users\\werne\\tools\\jdk-21' : undefined);
const integration = process.env.VERIFY_INTEGRATION === '1';
let failures = 0;

function run(name, command, args, { cwd = root, env = {} } = {}) {
  process.stdout.write(`\nâ–¶ ${name}\n`);
  const result = spawnSync(command, args, { cwd, stdio: 'inherit', shell: true, env: { ...process.env, ...env } });
  if (result.status !== 0) {
    failures += 1;
    console.error(`\nâœ– ${name} falhou (cÃ³digo ${result.status ?? 'desconhecido'})`);
  } else {
    console.log(`âœ” ${name}`);
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

async function waitForReady(url, attempts = 60) {
  for (let attempt = 0; attempt < attempts; attempt++) {
    try {
      const response = await fetch(url);
      if (response.ok) return true;
    } catch { /* ainda subindo */ }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  return false;
}

// --- VerificaÃ§Ã£o sempre executada (sem banco) ---
run('TypeScript do domÃ­nio (@foodie/api)', 'pnpm', ['--filter', '@foodie/api', 'test']);
run('Testes Java (api-java)', 'mvn', ['-o', '-q', '-Dmaven.repo.local=.m2-cache', 'test'], { cwd: join(root, 'apps/api-java'), env: { JAVA_HOME: defaultJavaHome } });
run('Tipos do site (tsc)', 'npx', ['tsc', '--noEmit'], { cwd: join(root, 'apps/web') });
run('Build do site (next build)', 'npm', ['run', 'build'], { cwd: join(root, 'apps/web') });

// --- VerificaÃ§Ã£o integrada com banco efÃªmero (opt-in) ---
if (integration) {
  const platformEnv = envFile(join(root, '.env'));
  const apiEnv = envFile(join(root, 'apps/api/.env'));
  const dbPassword = platformEnv.DB_PASSWORD;
  const dbRootPassword = platformEnv.DB_ROOT_PASSWORD;
  const demoPassword = apiEnv.DEMO_PASSWORD ?? platformEnv.DEMO_PASSWORD;
  if (!dbPassword || !dbRootPassword || !demoPassword) {
    console.error('\nâœ– VERIFY_INTEGRATION=1 exige DB_PASSWORD/DB_ROOT_PASSWORD em platform/.env e DEMO_PASSWORD em platform/apps/api/.env');
    process.exit(1);
  }
  const composeEnv = { ...process.env, DB_PASSWORD: dbPassword, DB_ROOT_PASSWORD: dbRootPassword };
  const base = 'http://127.0.0.1:4101';
  try {
    run('Subindo banco/API de teste efÃªmeros', 'docker', ['compose', '-f', 'docker-compose.test.yml', 'up', '-d', '--build'], { env: composeEnv });
    if (!(await waitForReady(`${base}/ready`))) {
      failures += 1;
      console.error('\nâœ– API de teste nÃ£o ficou pronta');
    } else {
      run('Seed demonstrativo no banco de teste', 'pnpm', ['--filter', '@foodie/api', 'seed'], {
        env: { DB_HOST: '127.0.0.1', DB_PORT: '3308', DB_USER: 'foodie', DB_PASSWORD: dbPassword, DB_NAME: 'foodie_platform', DEMO_PASSWORD: demoPassword },
      });
      const smokeEnv = { API_PORT: '4101', API_INTERNAL_URL: base, DEMO_PASSWORD: demoPassword };
      run('Smoke: fluxo completo', 'pnpm', ['--filter', '@foodie/api', 'smoke'], { env: smokeEnv });
      run('Smoke: carrinho', 'pnpm', ['smoke:cart'], { env: smokeEnv });
      run('Smoke: exceÃ§Ãµes', 'pnpm', ['smoke:exceptions'], { env: smokeEnv });
      run('Smoke: contas', 'pnpm', ['smoke:auth'], { env: smokeEnv });
    }
  } finally {
    run('Derrubando banco de teste efÃªmero', 'docker', ['compose', '-f', 'docker-compose.test.yml', 'down', '-v'], { env: composeEnv });
  }
}

if (failures > 0) {
  console.error(`\n${failures} verificaÃ§Ã£o(Ãµes) falharam.`);
  process.exit(1);
}
console.log('\nTodas as verificaÃ§Ãµes passaram.');
