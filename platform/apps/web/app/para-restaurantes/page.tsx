import type { Metadata } from 'next';
import Link from 'next/link';
import { Icon, type IconName } from '../icons';
import LegalLinks from '../legal-links';
import { contactLink, planPeriod, planPrice, type ContactConfig, type ContactLink, type PublicPlan } from './offer';

// Os planos vêm do banco a cada visita: mudar um plano no admin muda a página. Também evita que o
// build dependa da API no ar.
export const dynamic = 'force-dynamic';

export const metadata: Metadata = {
  title: 'Para restaurantes • Foodie',
  description: 'Sua loja de delivery no Foodie, sem comissão sobre a venda: cardápio online, pedidos, cozinha e entregadores. Você recebe do cliente na entrega, do jeito que já trabalha.',
};

type Offer = { trial_days: number; packages: PublicPlan[] };

const BENEFITS: { icon: IconName; title: string; text: string }[] = [
  { icon: 'store', title: 'Loja própria no Foodie', text: 'Página e cardápio com fotos, horário de funcionamento e área de entrega.' },
  { icon: 'wallet', title: 'Sem comissão sobre a venda', text: 'Você paga só a assinatura. O valor dos pedidos é todo da loja.' },
  { icon: 'cash', title: 'Você recebe do seu jeito', text: 'Dinheiro, cartão ou Pix na entrega. Pagamento online pelo site é opcional.' },
  { icon: 'bike', title: 'Seus entregadores', text: 'Você cadastra os entregadores da loja e acompanha cada entrega.' },
  { icon: 'utensils', title: 'Pedidos e cozinha', text: 'Painel de pedidos, tela da cozinha e aviso de pedido novo.' },
  { icon: 'ticket', title: 'Promoções e financeiro', text: 'Cupons e cashback definidos por você, e relatórios de vendas.' },
];

const INCLUDED = ['Loja e cardápio online', 'Pedidos para entrega e retirada', 'Pagamento por Pix, cartão e dinheiro', 'Tela da cozinha', 'Entregadores da loja', 'Cupons, cashback e relatórios'];

const api = () => process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';

/** API fora do ar ou com erro: a página abre sem a parte que faltou, em vez de quebrar. */
async function read<T>(path: string): Promise<T | null> {
  try {
    const response = await fetch(`${api()}${path}`, { cache: 'no-store' });
    return response.ok ? await response.json() as T : null;
  } catch {
    return null;
  }
}

function ContactButton({ contact, className }: { contact: ContactLink; className: string }) {
  return <a className={className} href={contact.href} target="_blank" rel="noopener noreferrer">
    <Icon name={contact.kind === 'whatsapp' ? 'phone' : 'mail'} size={18} />{contact.label}
  </a>;
}

export default async function ForRestaurantsPage() {
  const [offer, config] = await Promise.all([read<Offer>('/public/subscription-packages'), read<ContactConfig>('/public/config')]);
  const plans = offer?.packages ?? [];
  const trialDays = offer?.trial_days ?? 0;
  const contact = contactLink(config);
  const channel = contact?.kind === 'email' ? 'por e-mail' : contact ? 'pelo WhatsApp' : 'com o nosso atendimento';

  return <main className="home partner">
    <header className="home-header">
      <Link className="home-brand" href="/" aria-label="Foodie, início"><span className="home-brand-mark"><Icon name="sparkle" size={18} /></span>foodie<span>.</span></Link>
      <nav aria-label="Navegação da página"><a href="#planos">{'Planos'}</a><a href="#duvidas">{'Dúvidas'}</a><Link className="home-login" href="/entrar">{'Entrar'}</Link></nav>
    </header>

    <section className="partner-hero">
      <span className="partner-eyebrow">{'Para restaurantes'}</span>
      <h1>{'Sua loja de delivery, sem comissão sobre a venda'}<span>.</span></h1>
      <p>{'Cardápio online, pedidos, cozinha e entregadores num lugar só. O cliente paga na entrega, em dinheiro, cartão ou Pix, e o valor do pedido é todo da loja.'}</p>
      <div className="home-actions">
        {contact && <ContactButton contact={contact} className="home-primary" />}
        <a className="home-secondary" href="#planos">{'Ver planos'}</a>
      </div>
      {trialDays > 0 && <p className="partner-trial"><Icon name="gift" size={16} />{`${trialDays} dias grátis para testar. A primeira cobrança só vem no fim do teste.`}</p>}
    </section>

    <section className="home-section" aria-labelledby="partner-benefits-title">
      <h2 id="partner-benefits-title">{'O que a sua loja ganha'}</h2>
      <ul className="home-steps partner-benefits">{BENEFITS.map((item) => <li key={item.title}><span><Icon name={item.icon} size={22} /></span><strong>{item.title}</strong><p>{item.text}</p></li>)}</ul>
    </section>

    <section className="home-section" id="planos" aria-labelledby="partner-plans-title">
      <h2 id="partner-plans-title">{'Planos'}</h2>
      {plans.length ? <>
        <ul className="partner-plans">{plans.map((plan) => <li key={plan.name}>
          <strong className="partner-plan-name">{plan.name}</strong>
          <p className="partner-plan-price"><span>{planPrice(plan.price_cents)}</span> {planPeriod(plan.period_days)}</p>
          {trialDays > 0 && <p className="partner-plan-trial">{`${trialDays} dias grátis antes da primeira cobrança`}</p>}
        </li>)}</ul>
        <div className="partner-included">
          <h3>{plans.length > 1 ? 'Todos os planos incluem' : 'O plano inclui'}</h3>
          <ul>{INCLUDED.map((item) => <li key={item}><Icon name="check" size={16} />{item}</li>)}</ul>
        </div>
      </> : <p className="home-empty">{`Os planos ainda não estão publicados. Fale ${channel} para saber os valores.`}</p>}
    </section>

    <section className="home-section" id="duvidas" aria-labelledby="partner-faq-title">
      <h2 id="partner-faq-title">{'Dúvidas frequentes'}</h2>
      <div className="partner-faq">
        <details><summary>{'Quem faz as entregas?'}</summary><p>{'A sua loja, com os seus entregadores. Você os cadastra no painel e acompanha cada entrega. O cliente também pode retirar o pedido na loja.'}</p></details>
        <details><summary>{'Como eu recebo pelos pedidos?'}</summary><p>{'O cliente paga na entrega, em dinheiro, cartão (na sua maquininha) ou Pix, direto para a loja. Se quiser oferecer também o pagamento online pelo site, é só conectar a sua conta Mercado Pago no painel: é opcional. O Foodie não fica com nenhuma parte do pedido.'}</p></details>
        <details><summary>{'O Foodie cobra comissão?'}</summary><p>{'Não. Você paga só a assinatura do plano. Se usar o pagamento online, as tarifas de recebimento do Mercado Pago são as da sua própria conta.'}</p></details>
        <details><summary>{'Quem faz o estorno quando o cliente pede o dinheiro de volta?'}</summary><p>{'A loja. O pagamento é feito direto para você, então o estorno também sai da loja. No pagamento online, a loja também responde pelas contestações de compra no cartão (chargeback).'}</p></details>
        {trialDays > 0 && <details><summary>{'Como funciona o teste grátis?'}</summary><p>{`Você usa a plataforma por ${trialDays} dias sem pagar. No fim do teste vem a primeira cobrança da assinatura, que você paga no painel, no cartão ou no Pix.`}</p></details>}
        <details><summary>{'Como eu cancelo?'}</summary><p>{`Fale ${channel}. Por enquanto, o cancelamento é feito pelo nosso atendimento.`}</p></details>
      </div>
    </section>

    <section className="home-section partner-cta" aria-labelledby="partner-cta-title">
      <h2 id="partner-cta-title">{'Vamos colocar a sua loja no ar?'}</h2>
      {contact ? <>
        <p>{'Conte um pouco sobre o seu restaurante e a gente ajuda você a começar.'}</p>
        <ContactButton contact={contact} className="home-primary" />
      </> : <p>{'O cadastro de novas lojas abre em breve.'}</p>}
    </section>

    <footer className="home-footer"><Link className="home-brand" href="/"><span className="home-brand-mark"><Icon name="sparkle" size={14} /></span>foodie<span>.</span></Link><LegalLinks /><span>{'© 2026 Axyon Software House'}</span></footer>
  </main>;
}
