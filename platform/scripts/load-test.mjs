import { randomUUID } from 'node:crypto';

const base = process.env.LOAD_BASE_URL ?? process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
const concurrency = Number(process.env.LOAD_CONCURRENCY ?? 10);
const durationMs = Number(process.env.LOAD_DURATION_MS ?? 10000);
const p95Budget = Number(process.env.LOAD_P95_MS ?? 800);
const errorBudget = Number(process.env.LOAD_ERROR_RATE ?? 0.01);

async function zones() {
  try {
    const response = await fetch(`${base}/zones`);
    const data = await response.json();
    return Array.isArray(data) && data.length ? data[0].id : 1;
  } catch { return 1; }
}

const zoneId = await zones();
const scenarios = [
  { name: 'GET /health', path: '/health', expected: 200 },
  { name: 'GET /catalog', path: '/catalog', expected: 200 },
  { name: 'GET /zones', path: '/zones', expected: 200 },
  { name: 'GET /catalog/search', path: `/catalog/search?zoneId=${zoneId}&limit=12`, expected: 200 },
  { name: 'GET /zones/resolve', path: '/zones/resolve?postalCode=60000001', expected: [200, 404] },
];

const latencies = [];
const counts = new Map(scenarios.map((scenario) => [scenario.name, { ok: 0, error: 0 }]));
let errors = 0;
let requests = 0;
const deadline = Date.now() + durationMs;

async function worker(workerId) {
  let index = workerId;
  while (Date.now() < deadline) {
    const scenario = scenarios[index++ % scenarios.length];
    const started = performance.now();
    let status = 0;
    try {
      const response = await fetch(`${base}${scenario.path}`, { headers: { 'X-Request-Id': `load-${randomUUID().slice(0, 12)}` } });
      status = response.status;
      await response.arrayBuffer();
    } catch {
      status = -1;
    }
    const elapsed = performance.now() - started;
    requests += 1;
    latencies.push(elapsed);
    const expected = Array.isArray(scenario.expected) ? scenario.expected : [scenario.expected];
    const counter = counts.get(scenario.name);
    if (expected.includes(status)) counter.ok += 1;
    else { counter.error += 1; errors += 1; }
  }
}

function percentile(values, p) {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))] ?? 0;
}

await Promise.all(Array.from({ length: concurrency }, (_, workerId) => worker(workerId)));

const errorRate = requests === 0 ? 1 : errors / requests;
const p95 = percentile(latencies, 95);
console.log(`\nCarga: ${requests} requisições em ${(durationMs / 1000).toFixed(1)}s · concorrência ${concurrency}`);
console.log(`Latência p50=${percentile(latencies, 50).toFixed(1)}ms p95=${p95.toFixed(1)}ms p99=${percentile(latencies, 99).toFixed(1)}ms · ${(requests / (durationMs / 1000)).toFixed(0)} req/s`);
for (const [name, counter] of counts) console.log(`  ${name}: ok=${counter.ok} erro=${counter.error}`);
console.log(`Erros: ${(errorRate * 100).toFixed(2)}% (meta < ${(errorBudget * 100).toFixed(2)}%) · p95 (meta < ${p95Budget}ms)`);

if (p95 > p95Budget || errorRate > errorBudget) {
  console.error('\n✖ Carga fora da meta.');
  process.exit(1);
}
console.log('\n✔ Carga dentro da meta.');
