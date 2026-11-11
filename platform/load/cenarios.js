// Teste de carga do Foodie — docs/PLANO_TESTE_CARGA.md.
//
//   k6 run -e NIVEL=0 -e LOAD_PASSWORD="$(cat ~/.foodie/carga-senha)" platform/load/cenarios.js
//
// NIVEL: 0 = validação (1 usuário por perfil, 1 min) · 1 = esperado · 2 = folga 3× · 3 = ruptura · 4 = resistência.
// BASE_URL: o site (padrão: staging). As chamadas passam pelo /backend do Next, como no navegador.
// LOAD_PASSWORD: a senha das contas de carga (arquivo ~/.foodie/carga-senha, fora do repositório).
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const BASE = (__ENV.BASE_URL || 'https://staging.2.29.42.104.sslip.io').replace(/\/$/, '');
const API = `${BASE}/backend`;
const PASSWORD = __ENV.LOAD_PASSWORD || '';
const NIVEL = String(__ENV.NIVEL ?? '0');
const STORES = Number(__ENV.LOAD_STORES || 20);
const CUSTOMERS = Number(__ENV.LOAD_CUSTOMERS || 500);
const COURIERS = Number(__ENV.LOAD_COURIERS || 90);
const DOMAIN = 'carga.local';
const STORE_PREFIX = 'carga-foodie-';
// Ritmo de `app/poll-plan.ts` (S18 etapa 1).
const POLL_SECONDS = 8; // FAST_POLL_MS
const IDLE_CUSTOMER_SECONDS = 30; // IDLE_CUSTOMER_POLL_MS
const SLOW_SECONDS = 180; // SLOW_REFRESH_MS

const pedidosCriados = new Counter('pedidos_criados');
const pedidosRecusados = new Counter('pedidos_recusados');

// ---------- Níveis (§3 do plano) ----------

function nivel(clientes, pedidosPorMinuto, telas, entregadores, subida, duracao) {
  const metade = Math.round(clientes / 2);
  return {
    visitantes: { executor: 'ramping-vus', exec: 'visitante', startVUs: 0, stages: [{ duration: subida, target: metade }, { duration: duracao, target: metade }, { duration: '1m', target: 0 }] },
    clientes_logados: { executor: 'ramping-vus', exec: 'clienteLogado', startVUs: 0, stages: [{ duration: subida, target: clientes - metade }, { duration: duracao, target: clientes - metade }, { duration: '1m', target: 0 }] },
    compradores: { executor: 'ramping-arrival-rate', exec: 'comprador', startRate: 0, timeUnit: '1m', preAllocatedVUs: Math.max(10, pedidosPorMinuto * 6), maxVUs: Math.max(20, pedidosPorMinuto * 15),
      stages: [{ duration: subida, target: pedidosPorMinuto }, { duration: duracao, target: pedidosPorMinuto }, { duration: '1m', target: 0 }] },
    telas_loja: { executor: 'constant-vus', exec: 'telaLoja', vus: telas, duration: addDurations(subida, duracao) },
    entregadores: { executor: 'constant-vus', exec: 'entregador', vus: entregadores, duration: addDurations(subida, duracao) },
  };
}

function ruptura() {
  // Sobe 150 clientes (e o proporcional dos outros perfis) a cada 3 min, até 10 degraus (1.500 clientes).
  const degraus = (porDegrau) => Array.from({ length: 10 }, (_, index) => ({ duration: '3m', target: porDegrau * (index + 1) }));
  return {
    visitantes: { executor: 'ramping-vus', exec: 'visitante', startVUs: 0, stages: degraus(75) },
    clientes_logados: { executor: 'ramping-vus', exec: 'clienteLogado', startVUs: 0, stages: degraus(75) },
    compradores: { executor: 'ramping-arrival-rate', exec: 'comprador', startRate: 0, timeUnit: '1m', preAllocatedVUs: 60, maxVUs: 600, stages: degraus(5) },
    telas_loja: { executor: 'ramping-vus', exec: 'telaLoja', startVUs: 0, stages: degraus(40) },
    entregadores: { executor: 'ramping-vus', exec: 'entregador', startVUs: 0, stages: degraus(30) },
  };
}

function validacao() {
  const um = (fn) => ({ executor: 'constant-vus', exec: fn, vus: 1, duration: '1m' });
  return {
    visitantes: um('visitante'),
    clientes_logados: um('clienteLogado'),
    compradores: { executor: 'shared-iterations', exec: 'comprador', vus: 1, iterations: 1, maxDuration: '3m' },
    telas_loja: um('telaLoja'),
    entregadores: um('entregador'),
  };
}

function addDurations(...values) {
  const minutes = values.reduce((sum, value) => sum + Number(String(value).replace('m', '')), 0);
  return `${minutes}m`;
}

const NIVEIS = {
  0: validacao,
  1: () => nivel(150, 5, 40, 30, '3m', '15m'),
  2: () => nivel(450, 15, 120, 90, '5m', '15m'),
  3: ruptura,
  4: () => nivel(150, 5, 40, 30, '3m', '60m'),
};
if (!NIVEIS[NIVEL]) throw new Error(`NIVEL inválido: ${NIVEL} (use 0 a 4).`);
const SO = (__ENV.SO || '').split(',').filter(Boolean); // ex.: SO=visitantes,compradores
const allScenarios = NIVEIS[NIVEL]();
const scenarios = SO.length ? Object.fromEntries(Object.entries(allScenarios).filter(([name]) => SO.includes(name))) : allScenarios;

// ---------- Metas (§4 do plano) ----------

const thresholds = {
  'http_req_duration{tipo:leitura}': ['p(95)<800'],
  'http_req_duration{tipo:pagina}': ['p(95)<1500'],
  'http_req_duration{tipo:checkout}': ['p(95)<2000'],
  http_req_failed: [NIVEL === '3' ? { threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '1m' } : 'rate<0.01'],
  checks: ['rate>0.99'],
};

export const options = {
  scenarios,
  thresholds,
  noCookiesReset: true, // a sessão (cookie foodie_session) vale pelo teste inteiro, como numa aba aberta
  userAgent: 'foodie-teste-de-carga/1.0 (k6)',
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: false,
};

// ---------- Ajudantes ----------

const writeHeaders = { 'Content-Type': 'application/json', Origin: BASE };
const okOr404 = http.expectedStatuses({ min: 200, max: 399 }, 404);

function get(path, name, tipo = 'leitura', params = {}) {
  return http.get(`${API}${path}`, { tags: { name, tipo }, ...params });
}

function send(method, path, body, name, tipo = 'escrita') {
  return http.request(method, `${API}${path}`, body === undefined ? null : JSON.stringify(body), { headers: writeHeaders, tags: { name, tipo } });
}

function json(response) {
  try { return response.json(); } catch { return null; }
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const pause = (min, max) => sleep(min + Math.random() * (max - min));
const pad = (value, size) => String(value).padStart(size, '0');

// Login uma vez por usuário virtual (o scrypt custa CPU de propósito); o cookie fica no jar do VU.
const session = { email: null };
function login(email) {
  if (session.email === email) return true;
  if (!PASSWORD) exec.test.abort('Defina LOAD_PASSWORD (a senha das contas de carga).');
  const response = send('POST', '/auth/login', { email, password: PASSWORD }, 'POST /auth/login', 'login');
  const ok = check(response, { 'login 200': (r) => r.status === 200 });
  if (ok) session.email = email;
  else console.error(`login falhou para ${email}: ${response.status} ${String(response.body).slice(0, 160)}`);
  return ok;
}

// Faixas de contas: clientes logados usam o começo da lista, compradores o fim, para não dividirem o carrinho.
const vu = () => exec.vu.idInTest;
const customerEmail = (offset, size) => `carga-cliente-${pad(offset + ((vu() - 1) % size) + 1, 4)}@${DOMAIN}`;
const BROWSING_ACCOUNTS = Math.floor(CUSTOMERS * 0.6);

// ---------- Dados de partida (uma vez, antes da carga) ----------

export function setup() {
  // CEP da zona de carga (load-common.ts do platform/tools): 69999000–69999999.
  const zone = json(get('/zones/resolve?postalCode=69999000', 'GET /zones/resolve', 'leitura', { responseCallback: okOr404 }));
  if (!zone || !zone.id) exec.test.abort('Zona de carga não encontrada: rode o load:setup antes (platform/load/README.md).');
  const meta = json(get('/catalog/meta', 'GET /catalog/meta')) || { restaurants: [] };
  const stores = (meta.restaurants || []).filter((item) => String(item.slug || '').startsWith(STORE_PREFIX))
    .sort((a, b) => String(a.slug).localeCompare(String(b.slug)));
  if (!stores.length) exec.test.abort('Lojas de carga não encontradas no /catalog/meta: rode o load:setup antes.');
  const menus = {};
  for (const store of stores) {
    const page = json(get(`/catalog/search?restaurantId=${store.id}&limit=30`, 'GET /catalog/search')) || { items: [] };
    menus[store.id] = (page.items || []).map((item) => ({ id: item.id, variations: item.variation_count || 0, price: item.price_cents }));
  }
  console.log(`Nível ${NIVEL}: ${stores.length} lojas de carga, zona #${zone.id}, ${BASE}`);
  return { zoneId: zone.id, stores: stores.map((store) => store.id), menus };
}

// ---------- Perfis ----------

// Consulta automática do app-context, por papel (S18 etapa 1, `app/poll-plan.ts`): a cada tique vão
// só os pedidos (e os entregadores, para a loja); catálogo, zonas, endereços, permissões e módulos
// vão na entrada e depois a cada SLOW_SECONDS. O estado é por usuário virtual, como a aba aberta.
const slow = { last: 0 };
function slowDue() {
  if (Date.now() - slow.last < SLOW_SECONDS * 1000) return false;
  slow.last = Date.now();
  return true;
}

function pollCustomer() {
  if (slowDue()) {
    get('/catalog/meta', 'GET /catalog/meta');
    get('/zones', 'GET /zones');
    get('/addresses', 'GET /addresses');
    get('/me/permissions', 'GET /me/permissions');
  }
  const orders = json(get('/orders', 'GET /orders'));
  return Array.isArray(orders) ? orders : [];
}

function pollRestaurant() {
  if (slowDue()) {
    get('/catalog', 'GET /catalog');
    get('/zones', 'GET /zones');
    get('/me/permissions', 'GET /me/permissions');
    get('/restaurant/modules', 'GET /restaurant/modules');
  }
  const orders = json(get('/orders', 'GET /orders'));
  get('/restaurant/couriers', 'GET /restaurant/couriers');
  return Array.isArray(orders) ? orders : [];
}

function browseMenu(data, storeId) {
  get(`/catalog/search?restaurantId=${storeId}&limit=12`, 'GET /catalog/search');
  get(`/catalog/tags?restaurantId=${storeId}`, 'GET /catalog/tags');
  const menu = data.menus[storeId] || [];
  if (menu.length) get(`/catalog/products/${pick(menu).id}`, 'GET /catalog/products/{id}');
}

// 1. Visitante: home, página pública da loja, cardápio e CEP, sem login.
export function visitante(data) {
  const home = http.get(`${BASE}/`, { tags: { name: 'GET / (home)', tipo: 'pagina' } });
  check(home, { 'home 200': (r) => r.status === 200 });
  // Metade dos visitantes chega sem cache: baixa o JavaScript e o CSS da página, como um navegador novo.
  if (Math.random() < 0.5 && home.body) {
    // No JSON embutido da página os caminhos vêm colados; o "?" para cada um no fim do próprio arquivo.
    const assets = [...new Set(String(home.body).match(/\/_next\/static\/[\w\-/.~]+?\.(?:js|css|woff2)/g) || [])].slice(0, 25);
    if (assets.length) http.batch(assets.map((path) => ['GET', `${BASE}${path}`, null, { tags: { name: 'GET /_next/static', tipo: 'estatico' } }]));
  }
  pause(3, 8);
  const storeId = pick(data.stores);
  const page = http.get(`${BASE}/restaurantes/${storeId}`, { tags: { name: 'GET /restaurantes/{id}', tipo: 'pagina' } });
  check(page, { 'loja 200': (r) => r.status === 200 });
  pause(3, 8);
  http.get(`${BASE}/loja/restaurantes/${storeId}`, { tags: { name: 'GET /loja/restaurantes/{id}', tipo: 'pagina' } });
  get('/zones', 'GET /zones');
  get(`/zones/resolve?postalCode=69999${pad(Math.floor(Math.random() * 1000), 3)}`, 'GET /zones/resolve', 'leitura', { responseCallback: okOr404 });
  for (let step = 0; step < 3; step += 1) {
    browseMenu(data, storeId);
    pause(3, 10);
  }
}

// 2. Cliente logado navegando, sem pedido em andamento: a consulta do app-context a cada 30 s
// (IDLE_CUSTOMER_SECONDS), as mensagens não lidas a cada 8 s, o sino a cada 15 s e uma olhada no cardápio.
export function clienteLogado(data) {
  if (!login(customerEmail(0, BROWSING_ACCOUNTS))) { sleep(30); return; }
  const storeId = pick(data.stores);
  const appEvery = Math.round(IDLE_CUSTOMER_SECONDS / POLL_SECONDS);
  for (let tick = 0; tick < 8; tick += 1) {
    if (tick % appEvery === 0) pollCustomer();
    get('/orders/chat/unread', 'GET /orders/chat/unread');
    if (tick % 2 === 0) get('/notifications', 'GET /notifications');
    if (tick % 3 === 0) browseMenu(data, storeId);
    sleep(POLL_SECONDS);
  }
}

// 3. Comprador: monta o carrinho, fecha em dinheiro e acompanha o pedido por alguns minutos.
export function comprador(data) {
  const accounts = Math.max(1, CUSTOMERS - BROWSING_ACCOUNTS);
  if (!login(customerEmail(BROWSING_ACCOUNTS, accounts))) { sleep(10); return; }
  const storeId = pick(data.stores);
  const menu = data.menus[storeId] || [];
  if (!menu.length) return;

  send('DELETE', '/cart', undefined, 'DELETE /cart');
  browseMenu(data, storeId);
  pause(2, 5);
  for (const item of [pick(menu), pick(menu)]) {
    const payload = { delta: 1 };
    if (item.variations) {
      const detail = json(get(`/catalog/products/${item.id}`, 'GET /catalog/products/{id}'));
      const variation = detail && (detail.variations || [])[0];
      if (variation) payload.variationId = variation.id;
    }
    const added = send('PATCH', `/cart/items/${item.id}`, payload, 'PATCH /cart/items/{id}');
    check(added, { 'item no carrinho': (r) => r.status === 200 });
    pause(1, 4);
  }

  const cart = json(get('/cart', 'GET /cart')) || { items: [] };
  const addresses = json(get('/addresses', 'GET /addresses')) || [];
  const address = addresses.find((item) => Number(item.zone_id ?? item.zoneId) === data.zoneId) || addresses[0];
  if (!address || !cart.items || !cart.items.length) { check(null, { 'carrinho e endereço prontos': () => false }); return; }
  const estimate = json(get(`/delivery/estimate?addressId=${address.id}&restaurantId=${storeId}`, 'GET /delivery/estimate')) || {};
  const campaign = json(get('/cart/campaign', 'GET /cart/campaign')) || { discountCents: 0 };
  const subtotal = cart.items.reduce((sum, item) => sum + item.unitPriceCents * item.quantity, 0);
  const discount = Math.min(subtotal, campaign.discountCents || 0);
  const body = {
    expectedTotalCents: subtotal + (estimate.feeCents ?? 0) - discount,
    expectedVersion: cart.version,
    idempotencyKey: `carga-${vu()}-${exec.scenario.iterationInTest}-${Date.now()}`,
    paymentMethod: 'cash',
    orderType: 'delivery',
    addressId: address.id,
    contactPhone: '85999990000',
  };
  pause(2, 6);
  const placed = send('POST', '/cart/checkout', body, 'POST /cart/checkout', 'checkout');
  const order = json(placed);
  if (!check(placed, { 'pedido criado (201)': (r) => r.status === 201 })) {
    pedidosRecusados.add(1);
    console.warn(`checkout ${placed.status}: ${String(placed.body).slice(0, 200)}`);
    return;
  }
  pedidosCriados.add(1);

  // Acompanhamento: a consulta de 8 s, o código de entrega e a conversa com a loja.
  for (let tick = 0; tick < 15; tick += 1) {
    pollCustomer();
    get('/orders/chat/unread', 'GET /orders/chat/unread');
    get(`/orders/${order.id}/delivery-code`, 'GET /orders/{id}/delivery-code', 'leitura', { responseCallback: okOr404 });
    get(`/orders/${order.id}/chat?with=store`, 'GET /orders/{id}/chat', 'leitura', { responseCallback: okOr404 });
    sleep(POLL_SECONDS);
  }
}

// 4. Tela da loja: a consulta de 6 chamadas a cada 8 s. A primeira tela de cada loja aceita e prepara os pedidos.
export function telaLoja(data) {
  const index = vu() % (data.stores.length * 2);
  const store = (index % data.stores.length) + 1;
  const accepting = index < data.stores.length;
  if (!login(`carga-loja-${pad(store, 2)}${accepting ? 'a' : 'b'}@${DOMAIN}`)) { sleep(30); return; }
  for (let tick = 0; tick < 8; tick += 1) {
    const orders = pollRestaurant();
    if (tick % 2 === 0) get('/notifications', 'GET /notifications');
    if (accepting) {
      for (const order of orders.filter((item) => item.status === 'placed').slice(0, 3)) {
        send('PATCH', `/orders/${order.id}/status`, { action: 'accept' }, 'PATCH /orders/{id}/status (accept)');
      }
      for (const order of orders.filter((item) => item.status === 'accepted').slice(0, 3)) {
        send('PATCH', `/orders/${order.id}/status`, { action: 'ready' }, 'PATCH /orders/{id}/status (ready)');
      }
    }
    sleep(POLL_SECONDS);
  }
}

// 5. Entregador: turno aberto, a consulta da área de entregas e a posição a cada 15–30 s.
const courier = { shiftOpen: false, lastLocation: 0 };
export function entregador() {
  const number = ((vu() - 1) % COURIERS) + 1;
  if (!login(`carga-entregador-${pad(number, 3)}@${DOMAIN}`)) { sleep(30); return; }
  if (!courier.shiftOpen) {
    const shift = send('POST', '/courier/shift', {}, 'POST /courier/shift');
    courier.shiftOpen = check(shift, { 'turno aberto': (r) => r.status === 200 || r.status === 201 });
  }
  const interval = number % 2 === 0 ? 15 : 30; // metade "em entrega" (15 s), metade parada em turno (30 s)
  const started = Date.now();
  let tick = 0;
  while (Date.now() - started < 60_000) {
    // A área de entregas também está dentro do app-context: pedidos a cada 8 s, o lento a cada SLOW_SECONDS.
    if (slowDue()) {
      get('/catalog', 'GET /catalog');
      get('/zones', 'GET /zones');
      get('/me/permissions', 'GET /me/permissions');
    }
    get('/orders', 'GET /orders');
    if (tick++ % 2 === 0) get('/courier/deliveries/active', 'GET /courier/deliveries/active');
    get('/courier/shift', 'GET /courier/shift');
    if (Date.now() - courier.lastLocation >= interval * 1000) {
      const latitude = -3.73 + (Math.random() - 0.5) * 0.05;
      const longitude = -38.52 + (Math.random() - 0.5) * 0.05;
      const location = send('POST', '/courier/location', { latitude, longitude }, 'POST /courier/location');
      check(location, { 'posição aceita': (r) => r.status === 200 });
      courier.lastLocation = Date.now();
    }
    sleep(POLL_SECONDS);
  }
}
