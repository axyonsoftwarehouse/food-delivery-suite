'use client';

import { useState } from 'react';

/** O Pix que o Mercado Pago devolve — mesmo formato ao cobrar e ao reabrir o pedido depois. */
export type PixImage = { qr_code?: string | null; qr_code_base64?: string | null; ticket_url?: string | null };

const comoImagem = (base64?: string | null) => {
  if (!base64) return null;
  return base64.startsWith('data:') ? base64 : `data:image/png;base64,${base64}`;
};

/**
 * Painel do Pix: QR Code, copia e cola e o link do Mercado Pago.
 *
 * Serve a dois momentos com o mesmo desenho de dados: na hora de pagar (resposta da cobrança) e ao
 * reabrir o pedido (detalhe do pedido). Sem ele, o pedido ficava "Pix · a receber" sem o QR na tela.
 */
export default function PixPayment({ image, title = 'Finalize o pagamento', hint }: { image: PixImage | null | undefined; title?: string; hint?: string }) {
  const [copiado, setCopiado] = useState(false);
  const codigo = image?.qr_code ?? null;
  const desenho = comoImagem(image?.qr_code_base64);
  const link = image?.ticket_url ?? null;
  if (!codigo && !desenho && !link) return null;

  async function copiar() {
    if (!codigo) return;
    try {
      await navigator.clipboard?.writeText(codigo);
      setCopiado(true);
      window.setTimeout(() => setCopiado(false), 4000);
    } catch {
      setCopiado(false);
    }
  }

  return <section className="customer-card customer-online-payment">
    <div className="customer-card-title"><h2>{title}</h2></div>
    {desenho && <img className="customer-qr" src={desenho} alt="QR Code do Pix deste pedido" />}
    {codigo && <>
      <label>Pix copia e cola<textarea readOnly rows={3} value={codigo} /></label>
      <button className="customer-solid-button" type="button" onClick={() => void copiar()}>{copiado ? 'Código copiado' : 'Copiar código Pix'}</button>
    </>}
    {link && <a className="customer-solid-button" href={link} target="_blank" rel="noreferrer">Abrir no Mercado Pago</a>}
    <p className="form-help">{hint ?? 'A confirmação é automática.'}</p>
  </section>;
}
