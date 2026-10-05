'use client';

import { useEffect, useRef, useState } from 'react';
import type { CardChargeData } from './loja/customer-context';

type BrickError = { type?: string; message?: string; cause?: string };
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
  onSubmit: (data: CardChargeData) => Promise<{ status: string; message: string }>;
}) {
  const containerId = 'foodie-card-form';
  const controle = useRef<BrickController | null>(null);
  const enviar = useRef(onSubmit);
  enviar.current = onSubmit;
  const [estado, setEstado] = useState<'carregando' | 'pronto' | 'erro'>(publicKey ? 'carregando' : 'erro');
  const [mensagem, setMensagem] = useState(publicKey ? '' : 'O pagamento com cartão não está configurado nesta instalação.');

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
    }, 15000);
    carregarSdk()
      .then((MercadoPago) => {
        const mp = new MercadoPago(publicKey, { locale: 'pt-BR' });
        return mp.bricks().create('cardPayment', containerId, {
          initialization: { amount: amountCents / 100, ...(payerEmail ? { payer: { email: payerEmail } } : {}) },
          customization: { paymentMethods: { minInstallments: 1, maxInstallments: 12 } },
          callbacks: {
            onReady: () => { respondeu = true; if (vivo) setEstado('pronto'); },
            onError: (erro: BrickError) => {
              if (!vivo) return;
              respondeu = true;
              // `already_initialized` acontece quando o React monta o componente duas vezes em
              // desenvolvimento: o formulário que já está na tela funciona, então isso não é erro
              // para quem está comprando.
              if (erro?.cause === 'already_initialized') return;
              setEstado('erro');
              setMensagem(erro?.message ?? 'Não foi possível carregar o formulário do cartão.');
            },
            onSubmit: async (dados: CardData) => {
              try {
                const resultado = await enviar.current({
                  token: dados.token,
                  paymentMethodId: dados.payment_method_id,
                  installments: dados.installments,
                  docType: dados.payer?.identification?.type,
                  docNumber: dados.payer?.identification?.number,
                });
                setMensagem(resultado.message);
                // Recusado: rejeitar devolve o formulário para o cliente tentar outro cartão.
                // Aprovado ou em análise: resolver deixa o Mercado Pago mostrar o resultado.
                if (resultado.status === 'rejected') throw new Error(resultado.message);
              } catch (erro: unknown) {
                // O motivo tem de aparecer: o aviso genérico do Mercado Pago ("tente novamente mais
                // tarde") esconderia, por exemplo, um cartão recusado por saldo.
                const texto = erro instanceof Error ? erro.message : 'Não foi possível concluir o pagamento com cartão.';
                setMensagem(texto);
                throw erro instanceof Error ? erro : new Error(texto);
              }
              return;
            },
          },
        });
      })
      .then((criado) => { controle.current = criado; })
      .catch((erro: unknown) => {
        if (!vivo) return;
        respondeu = true;
        setEstado('erro');
        setMensagem(erro instanceof Error ? erro.message : 'Não foi possível carregar o pagamento com cartão.');
      });
    return () => {
      vivo = false;
      window.clearTimeout(semResposta);
      // Sem desmontar, o Brick ficaria preso no container antigo e a próxima tentativa falharia.
      try { controle.current?.unmount(); } catch { controle.current = null; }
      controle.current = null;
    };
  }, [amountCents, publicKey, payerEmail]);

  return <section className="customer-card customer-online-payment">
    <div className="customer-card-title"><div><span className="customer-kicker">PAGAMENTO COM CARTÃO</span><h2>Informe os dados do cartão</h2></div></div>
    {estado === 'carregando' && <p className="form-help">Carregando o formulário seguro do Mercado Pago...</p>}
    <div id={containerId} />
    {mensagem && <p role="status" className={estado === 'erro' ? 'customer-minimum' : 'form-help'}>{mensagem}</p>}
    <p className="form-help">O número e o código de segurança do cartão são criptografados pelo Mercado Pago no seu navegador — eles não passam pelo Foodie.</p>
  </section>;
}
