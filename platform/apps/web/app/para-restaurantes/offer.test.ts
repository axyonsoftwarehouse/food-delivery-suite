import assert from 'node:assert/strict';
import { test } from 'node:test';
import { contactLink, planPeriod, planPrice } from './offer.ts';

test('preço em reais', () => {
  assert.equal(planPrice(9900).replace(/\s/g, ' '), 'R$ 99,00');
  assert.equal(planPrice(0).replace(/\s/g, ' '), 'R$ 0,00');
});

test('período do plano', () => {
  assert.equal(planPeriod(30), 'por mês');
  assert.equal(planPeriod(365), 'por ano');
  assert.equal(planPeriod(90), 'a cada 90 dias');
});

test('WhatsApp das redes sociais vem primeiro, com DDI e mensagem pronta', () => {
  const link = contactLink({ social: { whatsapp: '(85) 99999-0000' }, business: { supportPhone: '85 3333-0000', supportEmail: 'a@b.com' } });
  assert.equal(link?.kind, 'whatsapp');
  assert.ok(link?.href.startsWith('https://wa.me/5585999990000?text='));
  assert.ok(link?.href.includes(encodeURIComponent('restaurante')));
});

test('número que já tem DDI não ganha outro 55', () => {
  assert.ok(contactLink({ social: { whatsapp: '+55 85 99999-0000' } })?.href.startsWith('https://wa.me/5585999990000?'));
});

test('sem WhatsApp, usa o telefone de suporte; sem os dois, o e-mail', () => {
  assert.ok(contactLink({ social: { whatsapp: '' }, business: { supportPhone: '85 3333-0000' } })?.href.startsWith('https://wa.me/558533330000?'));
  const mail = contactLink({ social: { whatsapp: '' }, business: { supportPhone: '', supportEmail: 'contato@foodie.com.br' } });
  assert.equal(mail?.kind, 'email');
  assert.ok(mail?.href.startsWith('mailto:contato@foodie.com.br?subject='));
});

test('sem contato válido, não há botão', () => {
  assert.equal(contactLink(null), null);
  assert.equal(contactLink({ social: { whatsapp: '123' }, business: { supportEmail: 'não é e-mail' } }), null);
});
