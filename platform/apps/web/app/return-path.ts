// Aceita só caminhos internos do site; qualquer outra coisa (outro domínio,
// esquema, barra dupla ou invertida) vira null, para evitar redirecionamento aberto.
export function safeReturnPath(value: string | null | undefined): string | null {
  if (typeof value !== 'string' || value === '') return null;
  if (!value.startsWith('/')) return null;
  if (value.startsWith('//') || value.startsWith('/\\')) return null;
  if (/[\u0000-\u001f\\]/.test(value)) return null;
  return value;
}
