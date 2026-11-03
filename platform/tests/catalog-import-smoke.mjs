import assert from 'node:assert/strict';

// Contrato da importação em massa (E23): dry-run, criação, atualização e erros sem escrita.
const base = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
const password = process.env.DEMO_PASSWORD;
if (!password) throw new Error('DEMO_PASSWORD ausente');

async function call(path, { cookie, method = 'GET', body, expected = 200 } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: {
      ...(cookie ? { Cookie: cookie } : {}),
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
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

const header = 'categoria,produto,descricao,preco,disponivel,estoque,disponivel_de,disponivel_ate';
const restaurant = await login('restaurante@demo.local');
const suffix = Date.now().toString(36);
const category = `Importação ${suffix}`;
const product = `Prato ${suffix}`;
const row = `${category},${product},Teste de importação,12.90,sim,5,11:00,15:00`;

const preview = await call('/restaurant/catalog/import?dryRun=true', {
  cookie: restaurant, method: 'POST', body: { csv: `${header}\n${row}\n` },
});
assert.equal(preview.dryRun, true);
assert.equal(preview.applied, false);
assert.deepEqual(preview.errors, []);
assert.ok(preview.lines.some((line) => line.product === product && line.action === 'create'));

const applied = await call('/restaurant/catalog/import', {
  cookie: restaurant, method: 'POST', body: { csv: `${header}\n${row}\n` },
});
assert.equal(applied.applied, true);
assert.equal(applied.categoriesCreated, 1);
assert.equal(applied.productsCreated, 1);
assert.equal(applied.productsUpdated, 0);

const catalog = await call('/restaurant/catalog', { cookie: restaurant });
const createdCategory = catalog.categories.find((item) => item.name === category);
assert.ok(createdCategory, 'categoria importada não apareceu');
const createdProduct = catalog.products.find((item) => item.name === product && item.category_id === createdCategory.id);
assert.ok(createdProduct, 'produto importado não apareceu');
assert.equal(createdProduct.price_cents, 1290);
assert.equal(createdProduct.stock, 5);
assert.equal(Boolean(createdProduct.available), true);
assert.ok(String(createdProduct.available_from).startsWith('11:00'));

const update = await call('/restaurant/catalog/import', {
  cookie: restaurant, method: 'POST', body: { csv: `${header}\n${category},${product},Atualizado,15.50,nao,,\n` },
});
assert.equal(update.applied, true);
assert.equal(update.productsCreated, 0);
assert.equal(update.productsUpdated, 1);

const after = await call('/restaurant/catalog', { cookie: restaurant });
const updated = after.products.find((item) => item.id === createdProduct.id);
assert.equal(updated.price_cents, 1550);
assert.equal(Boolean(updated.available), false);
assert.equal(updated.stock, null);
assert.equal(updated.available_from, null);

const invalid = await call('/restaurant/catalog/import?dryRun=true', {
  cookie: restaurant, method: 'POST', body: { csv: `${header}\n${category},${product},X,1.00\n${category},${product},Y,2.00\n` },
});
assert.equal(invalid.applied, false);
assert.ok(invalid.errors.length > 0, 'duplicata deveria ser recusada');

await call(`/restaurant/products/${createdProduct.id}`, { cookie: restaurant, method: 'DELETE' });
await call(`/restaurant/categories/${createdCategory.id}`, { cookie: restaurant, method: 'DELETE' });

console.log(`Importação: dry-run, criação, atualização e erro de duplicata validados (${category}).`);
