import type { Role } from './app-context.tsx';

// Aceita só caminhos internos do site; qualquer outra coisa (outro domínio,
// esquema, barra dupla ou invertida) vira null, para evitar redirecionamento aberto.
export function safeReturnPath(value: string | null | undefined): string | null {
  if (typeof value !== 'string' || value === '') return null;
  if (!value.startsWith('/')) return null;
  if (value.startsWith('//') || value.startsWith('/\\')) return null;
  if (/[\u0000-\u001f\\]/.test(value)) return null;
  return value;
}

/** Tela inicial de cada papel depois do login (fica aqui para os testes rodarem sem React). */
export function roleHome(role: Role): string {
  if (role === 'customer') return '/loja';
  // A cozinha tem tela própria, em tela cheia (KDS), fora do painel.
  if (role === 'kitchen') return '/cozinha';
  // O entregador tem área própria para o celular (área do entregador, parte A).
  if (role === 'courier') return '/entregas';
  return '/painel';
}

// Destino depois de qualquer login: caminho de retorno seguro; senão o carrinho de
// visitante (só cliente); senão a tela inicial do papel.
export function loginDestination(role: Role, voltar: string | null, hasGuestCart: boolean): string {
  if (role !== 'customer') return roleHome(role);
  const safe = safeReturnPath(voltar);
  if (safe) return safe;
  if (hasGuestCart) return '/loja/carrinho';
  return roleHome(role);
}
