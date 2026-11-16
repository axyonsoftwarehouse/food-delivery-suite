// Ferramentas na produção: o seed e o load:setup recusam; a implantação inicial valida a entrada.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { recusarEmProducao } from './ambiente.js';
import { lerEntrada, lerPlanos } from './implantacao.js';

const SENHA = 'x'.repeat(12);

test('recusa na produção', () => {
  assert.throws(() => recusarEmProducao('seed', { FOODIE_ENVIRONMENT: 'production' }), /seed não roda na produção/);
});

test('aceita no staging e sem variável', () => {
  recusarEmProducao('seed', { FOODIE_ENVIRONMENT: 'staging' });
  recusarEmProducao('seed', {});
});

test('a comparação ignora espaços e maiúsculas', () => {
  assert.throws(() => recusarEmProducao('load:setup', { FOODIE_ENVIRONMENT: ' Production ' }), /não roda na produção/);
});

test('entrada válida, com e-mail normalizado como a API faz', () => {
  const e = lerEntrada({ ADMIN_NAME: ' Werner ', ADMIN_EMAIL: ' Admin@Exemplo.com.br ', ADMIN_PASSWORD: SENHA });
  assert.deepEqual(e, { nome: 'Werner', email: 'admin@exemplo.com.br', senha: SENHA });
});

test('senha curta ou ausente', () => {
  assert.throws(() => lerEntrada({ ADMIN_NAME: 'A', ADMIN_EMAIL: 'a@exemplo.com.br', ADMIN_PASSWORD: 'curta' }), /ADMIN_PASSWORD/);
  assert.throws(() => lerEntrada({ ADMIN_NAME: 'A', ADMIN_EMAIL: 'a@exemplo.com.br' }), /ADMIN_PASSWORD/);
});

test('nome ausente', () => {
  assert.throws(() => lerEntrada({ ADMIN_NAME: '  ', ADMIN_EMAIL: 'a@exemplo.com.br', ADMIN_PASSWORD: SENHA }), /ADMIN_NAME/);
});

for (const email of ['admin@demo.local', 'x@carga.local', 'sem-arroba', '', 'a@', '@exemplo.com.br']) {
  test(`e-mail recusado: "${email}"`, () => {
    assert.throws(() => lerEntrada({ ADMIN_NAME: 'A', ADMIN_EMAIL: email, ADMIN_PASSWORD: SENHA }), /ADMIN_EMAIL/);
  });
}

test('mensagem de erro não mostra a senha', () => {
  try {
    lerEntrada({ ADMIN_NAME: '', ADMIN_EMAIL: 'a@exemplo.com.br', ADMIN_PASSWORD: 'senha-secreta-123' });
    assert.fail('deveria recusar');
  } catch (erro) {
    assert.doesNotMatch(String(erro), /senha-secreta-123/);
  }
});

test('planos: sem variável, nenhum plano', () => {
  assert.deepEqual(lerPlanos(undefined), []);
  assert.deepEqual(lerPlanos('  '), []);
});

test('planos: lista válida', () => {
  assert.deepEqual(lerPlanos('[{"nome":" Essencial ","precoCentavos":9900,"dias":30}]'),
    [{ nome: 'Essencial', precoCentavos: 9900, dias: 30 }]);
});

for (const [descricao, json] of [
  ['JSON inválido', '[{'],
  ['não é lista', '{"nome":"A","precoCentavos":1,"dias":30}'],
  ['nome vazio', '[{"nome":"","precoCentavos":9900,"dias":30}]'],
  ['preço zero', '[{"nome":"A","precoCentavos":0,"dias":30}]'],
  ['preço em reais', '[{"nome":"A","precoCentavos":99.9,"dias":30}]'],
  ['preço como texto', '[{"nome":"A","precoCentavos":"9900","dias":30}]'],
  ['dias zero', '[{"nome":"A","precoCentavos":9900,"dias":0}]'],
  ['nome repetido', '[{"nome":"A","precoCentavos":1,"dias":30},{"nome":"a","precoCentavos":2,"dias":30}]'],
]) {
  test(`planos recusados: ${descricao}`, () => {
    assert.throws(() => lerPlanos(json), /PLANOS_JSON/);
  });
}
