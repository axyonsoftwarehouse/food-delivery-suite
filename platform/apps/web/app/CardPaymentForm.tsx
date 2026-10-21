'use client';

import { useEffect, useRef, useState } from 'react';
import type { CardChargeData } from './loja/customer-context';
import { brickErrorAction, type BrickError } from './online-payment';
type CardData = {
  token: string;
  payment_method_id?: string;
  installments?: number;
  payer?: { email?: string; identification?: { type?: string; number?: string } };
};
type BrickController = { unmount: () => void };
type Bricks = { create: (brick: 'cardPayment', target: string, settings: Record<string, unknown>) => Promise<BrickController> };
type MercadoPagoInstance = { bricks: () => Bricks };
type MercadoPagoConstructor = new (publicKey: string, options?: { locale?: string }) => MercadoPagoInstance;

declare global {
  interface Window { MercadoPago?: MercadoPagoConstructor }
}

let sdkPromessa: Promise<MercadoPagoConstructor> | null = null;

/** Carrega o SDK do Mercado Pago uma vez por sessão (o Brick vive dentro dele). */
function carregarSdk(): Promise<MercadoPagoConstructor> {
  if (typeof window === 'undefined') return Promise.reject(new Error('Sem navegador para carregar o Mercado Pago.'));
  if (window.MercadoPago) return Promise.resolve(window.MercadoPago);
  if (sdkPromessa) return sdkPromessa;
  sdkPromessa = new Promise<MercadoPagoConstructor>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://sdk.mercadopago.com/js/v2';
    script.async = true;
    script.onload = () => (window.MercadoPago ? resolve(window.MercadoPago) : reject(new Error('O Mercado Pago não ficou disponível.')));
    script.onerror = () => reject(new Error('Não foi possível carregar o Mercado Pago (verifique a conexão).'));
    document.head.appendChild(script);
  });
  return sdkPromessa;
}

/**
 * Formulário de cartão do checkout transparente.
 *
 * Quem desenha o formulário é o **Card Payment Brick** do Mercado Pago: é ele que mostra as parcelas,
 * pede o CPF e criptografa os dados do cartão no navegador com a public key da aplicação. O que sai
 * daqui para a nossa API é só o **token** — o número e o código de segurança do cartão nunca passam
 * pelo Foodie.
 */
export default function CardPaymentForm({ amountCents, publicKey, payerEmail, onSubmit }: {
  amountCents: number;
  publicKey: string;
  payerEmail?: string;
  onSubmit: (data: CardChargeData) => Promise<'paid' | 'rejected' | 'pending'>;
}) {
  const containerId = 'foodie-card-form';
  const controle = useRef<BrickController | null>(null);
  // A criação do Brick em andamento: a próxima tentativa espera por ela (ver o efeito).
  const criacaoAnterior = useRef<Promise<unknown>>(Promise.resolve());
  const enviar = useRef(onSubmit);
  enviar.current = onSubmit;
  const [estado, setEstado] = useState<'carregando' | 'pronto' | 'erro'>(publicKey ? 'carregando' : 'erro');
  const [mensagem, setMensagem] = useState(publicKey ? '' : 'O pagamento com cartão não está configurado nesta instalação.');
  // "Recarregar o formulário": cada clique monta um Brick novo, como a troca de aba fazia no #59 (spec de
  // 14/10/2026, regra 11). Nunca automático: apagaria o que o cliente digitou.
  const [tentativa, setTentativa] = useState(0);
  const [podeRecarregar, setPodeRecarregar] = useState(false);

  useEffect(() => {
    if (!publicKey) return;
    // O Mercado Pago recusa tokenizar cartão fora de HTTPS ("SSL certificate is required to
    // operate") — não é limitação do Foodie, é regra do provedor. Em desenvolvimento (http://127.0.0.1)
    // o formulário do cartão não tem como funcionar; o Pix continua funcionando, porque quem gera o
    // QR é a nossa API. Melhor dizer isso do que deixar o aviso genérico do Brick na tela.
    if (typeof window !== 'undefined' && window.location.protocol !== 'https:') {
      setEstado('erro');
      setMensagem('O Mercado Pago só aceita os dados do cartão em conexão segura (HTTPS). Neste endereço de desenvolvimento, pague com Pix — ou use o endereço publicado.');
      return;
    }
    setEstado('carregando');
    setMensagem('');
    setPodeRecarregar(false);
    let vivo = true;
    // Quando o navegador bloqueia os domínios do Mercado Pago (proteção contra rastreamento rígida do
    // Firefox, Brave, bloqueadores), o SDK quebra por dentro sem chamar onError nem rejeitar a promessa,
    // e a tela ficava para sempre em "Carregando". Medido em 05/10/2026 no Firefox: CORS bloqueado em
    // secure-fields.mercadopago.com e "t is undefined" dentro do SDK.
    let respondeu = false;
    const semResposta = window.setTimeout(() => {
      if (!vivo || respondeu) return;
      setEstado('erro');
      setMensagem('O formulário do cartão não carregou. Se o seu navegador bloqueia rastreadores (Firefox em modo rígido, Brave, extensões de bloqueio), libere este site ou use outro navegador. O pedido já foi criado e continua aguardando o pagamento.');
      setPodeRecarregar(true);
    }, 15000);
    // "Recarregar o formulário" com o Brick anterior ainda sendo criado: espera ele terminar (e ser desmontado
    // abaixo) antes de criar o novo. Senão os dois disputam o mesmo container e o formulário some ou fica em
    // "Carregando…" (`already_initialized`).
    criacaoAnterior.current = criacaoAnterior.current
      .then(() => carregarSdk())
      .then((MercadoPago) => {
        if (!vivo) return null;
        const mp = new MercadoPago(publicKey, { locale: 'pt-BR' });
        return mp.bricks().create('cardPayment', containerId, {
          initialization: { amount: amountCents / 100, ...(payerEmail ? { payer: { email: payerEmail } } : {}) },
          customization: { paymentMethods: { minInstallments: 1, maxInstallments: 12 } },
          callbacks: {
            onReady: () => { respondeu = true; if (vivo) setEstado('pronto'); },
            onError: (erro: BrickError) => {
              // O motivo vai para o console para descobrir a causa no próximo roteiro (o #59 não deixou
              // rastro). O erro do Brick não traz dados do cartão.
              console.warn('Formulário do cartão do Mercado Pago:', erro?.type, erro?.cause);
              if (!vivo) return;
              const acao = brickErrorAction(erro);
              // Erro ignorado não conta como resposta: se o formulário não ficar pronto, o aviso dos 15 s aparece.
              if (!acao.show) return;
              respondeu = true;
              setEstado('erro');
              setMensagem(acao.message);
              setPodeRecarregar(true);
            },
            onSubmit: async (dados: CardData) => {
              // O cliente conseguiu enviar: o aviso de um erro anterior não vale mais (regra 9).
              if (vivo) { setEstado('pronto'); setMensagem(''); setPodeRecarregar(false); }
              const resultado = await enviar.current({
                token: dados.token,
                paymentMethodId: dados.payment_method_id,
                installments: dados.installments,
                docType: dados.payer?.identification?.type,
                docNumber: dados.payer?.identification?.number,
                payerEmail: dados.payer?.email,
              });
              // Recusado: rejeitar devolve o formulário para o cliente tentar outro cartão. O motivo é
              // mostrado pela página, em destaque e acima do formulário (spec de 11/10, regra 10).
              if (resultado === 'rejected') throw new Error('Pagamento recusado.');
            },
          },
        });
      })
      .then((criado) => {
        if (!criado) return;
        // Trocou de aba antes de o Brick ficar pronto: desmonta o que chegou atrasado, senão a volta
        // para "Cartão" encontra o container ocupado (dois formulários ou `already_initialized`).
        if (!vivo) { try { criado.unmount(); } catch { /* já desmontado */ } return; }
        controle.current = criado;
      })
      .catch((erro: unknown) => {
        if (!vivo) return;
        respondeu = true;
        setEstado('erro');
        setMensagem(erro instanceof Error ? erro.message : 'Não foi possível carregar o pagamento com cartão.');
        setPodeRecarregar(true);
      });
    return () => {
      vivo = false;
      window.clearTimeout(semResposta);
      // Sem desmontar, o Brick ficaria preso no container antigo e a próxima tentativa falharia.
      try { controle.current?.unmount(); } catch { controle.current = null; }
      controle.current = null;
    };
  }, [amountCents, publicKey, payerEmail, tentativa]);

  return <section className="customer-card customer-online-payment">
    {estado === 'carregando' && <p className="form-help">Carregando…</p>}
    <div id={containerId} />
    {estado === 'erro' && mensagem && <div role="alert" className="customer-minimum">
      <p>{mensagem}</p>
      {podeRecarregar && <button type="button" className="orders-cancel" onClick={() => setTentativa((n) => n + 1)}>{'Recarregar o formulário'}</button>}
    </div>}
    <p className="form-help">O número e o código de segurança do cartão são criptografados pelo Mercado Pago no seu navegador — eles não passam pelo Foodie.</p>
  </section>;
}
