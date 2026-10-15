import assert from 'node:assert/strict';

// Contrato da lista de produtos da loja: precisa trazer TODOS os campos que o formulário do painel
// edita — estoque, combo e a janela de horário. Sem eles o formulário abria em branco e, ao salvar,
// apagava a janela/estoque: o "Executivo do almoço" (11:00–15:00) sumia do cardápio ao ser editado
// (15/10/2026).
const base = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
const password = process.env.DEMO_PASSWORD;
if (!password) throw new Error('DEMO_PASSWORD ausente');

async function call(path, { cookie, method = 'GET', body, expected = 200 } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { ...(cookie ? { Cookie: cookie } : {}), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await response.json().catch(() => null);
  assert.equal(response.status, expected, `${method} ${path}: ${JSON.stringify(data)}`);
  return data;
}

async function login(email) {
  const response = await fetch(`${base}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }),
  });
  assert.equal(response.status, 200, `Login falhou para ${email}`);
  return response.headers.get('set-cookie')?.split(';')[0];
}

function editBody(product, { from, until, stock } = {}) {
  return {
    categoryId: product.category_id, name: product.name, description: product.description ?? '',
    priceCents: product.price_cents, available: product.available,
    isCombo: Boolean(product.is_combo), stock: stock ?? product.stock ?? -1,
    availableFrom: from ?? (product.available_from ? String(product.available_from).slice(0, 5) : ''),
    availableUntil: until ?? (product.available_until ? String(product.available_until).slice(0, 5) : ''),
  };
}

const restaurant = await login('restaurante@demo.local');

const produtos = await call('/restaurant/products', { cookie: restaurant });
assert.ok(produtos.length > 0, 'a loja demo deveria ter produtos');

// 1) A lista precisa trazer os campos que o formulário edita (senão abrem em branco e o salvar apaga).
for (const produto of produtos) {
  for (const campo of ['is_combo', 'stock', 'available_from', 'available_until']) {
    assert.ok(Object.hasOwn(produto, campo), `produto #${produto.id} (${produto.name}) veio sem o campo "${campo}"`);
  }
}

// 2) Define uma janela/estoque e confere que a lista devolve os valores.
const alvo = produtos[0];
await call(`/restaurant/products/${alvo.id}`, { cookie: restaurant, method: 'PATCH', body: editBody(alvo, { from: '11:00', until: '15:00', stock: 7 }) });
const comJanela = (await call('/restaurant/products', { cookie: restaurant })).find((item) => item.id === alvo.id);
assert.equal(String(comJanela.available_from).slice(0, 5), '11:00', 'a lista deveria devolver a janela de início');
assert.equal(String(comJanela.available_until).slice(0, 5), '15:00', 'a lista deveria devolver a janela de fim');
assert.equal(comJanela.stock, 7, 'a lista deveria devolver o estoque');

// 3) Ida e volta: salvar os MESMOS valores que a lista devolveu não pode mudar nada.
await call(`/restaurant/products/${alvo.id}`, { cookie: restaurant, method: 'PATCH', body: editBody(comJanela) });
const depois = (await call('/restaurant/products', { cookie: restaurant })).find((item) => item.id === alvo.id);
assert.equal(String(depois.available_from).slice(0, 5), '11:00', 'editar sem mudar não pode apagar o início da janela');
assert.equal(String(depois.available_until).slice(0, 5), '15:00', 'editar sem mudar não pode apagar o fim da janela');
assert.equal(depois.stock, 7, 'editar sem mudar não pode apagar o estoque');

// 4) Devolve o produto ao estado "o dia todo / sem estoque".
await call(`/restaurant/products/${alvo.id}`, { cookie: restaurant, method: 'PATCH', body: editBody(comJanela, { from: '', until: '', stock: -1 }) });

console.log(`Catálogo: ${produtos.length} produtos com os campos do formulário; janela do produto #${alvo.id} ida-e-volta preservada (11:00–15:00).`);
