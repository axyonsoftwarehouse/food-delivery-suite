// Validação da entrada da implantação inicial (implantacao-inicial.ts). Funções puras, testadas em
// ambiente.test.ts; ficam fora do script para o teste não executá-lo ao importar.

export type Administrador = { nome: string; email: string; senha: string };
export type Plano = { nome: string; precoCentavos: number; dias: number };

const DOMINIOS_PROIBIDOS = ['demo.local', 'carga.local'];

/** Lê ADMIN_NAME, ADMIN_EMAIL e ADMIN_PASSWORD. As mensagens de erro nunca mostram a senha. */
export function lerEntrada(env: NodeJS.ProcessEnv): Administrador {
  const nome = (env.ADMIN_NAME ?? '').trim();
  // Mesma normalização da API (AuthService: toLowerCase), para o login achar a conta.
  const email = (env.ADMIN_EMAIL ?? '').trim().toLowerCase();
  const senha = env.ADMIN_PASSWORD ?? '';

  if (!nome) throw new Error('ADMIN_NAME: informe o nome do administrador.');
  const [local, dominio, ...resto] = email.split('@');
  if (!local || !dominio || resto.length > 0 || !dominio.includes('.')) {
    throw new Error('ADMIN_EMAIL: informe um e-mail válido.');
  }
  if (DOMINIOS_PROIBIDOS.includes(dominio)) {
    throw new Error(`ADMIN_EMAIL: o domínio ${dominio} é de demonstração ou de carga.`);
  }
  if (senha.length < 12) throw new Error('ADMIN_PASSWORD: a senha precisa ter pelo menos 12 caracteres.');
  return { nome, email, senha };
}

/** Lê PLANOS_JSON: lista de { nome, precoCentavos, dias }. Sem a variável, nenhum plano. */
export function lerPlanos(json: string | undefined): Plano[] {
  if (!json || !json.trim()) return [];
  let lista: unknown;
  try {
    lista = JSON.parse(json);
  } catch {
    throw new Error('PLANOS_JSON: não é um JSON válido.');
  }
  if (!Array.isArray(lista)) throw new Error('PLANOS_JSON: tem de ser uma lista de planos.');

  const vistos = new Set<string>();
  return lista.map((item, i) => {
    const p = item as Partial<Record<keyof Plano, unknown>>;
    const nome = typeof p.nome === 'string' ? p.nome.trim() : '';
    if (!nome) throw new Error(`PLANOS_JSON: o plano ${i + 1} está sem nome.`);
    if (!Number.isInteger(p.precoCentavos) || (p.precoCentavos as number) <= 0) {
      throw new Error(`PLANOS_JSON: o preço do plano "${nome}" tem de ser um número inteiro de centavos maior que zero.`);
    }
    if (!Number.isInteger(p.dias) || (p.dias as number) <= 0) {
      throw new Error(`PLANOS_JSON: o período do plano "${nome}" tem de ser um número inteiro de dias maior que zero.`);
    }
    const chave = nome.toLowerCase();
    if (vistos.has(chave)) throw new Error(`PLANOS_JSON: o plano "${nome}" aparece mais de uma vez.`);
    vistos.add(chave);
    return { nome, precoCentavos: p.precoCentavos as number, dias: p.dias as number };
  });
}
