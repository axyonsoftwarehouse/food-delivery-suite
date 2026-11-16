// Ferramentas que criam dados de demonstração ou de carga não rodam na produção.
// O docker-compose.yml de deploy repassa o ambiente ao serviço `seed` em FOODIE_ENVIRONMENT
// (vem do SENTRY_ENVIRONMENT do .env da árvore). Fora do Compose, a variável não existe e nada é recusado.

export function recusarEmProducao(ferramenta: string, env: NodeJS.ProcessEnv = process.env): void {
  if ((env.FOODIE_ENVIRONMENT ?? '').trim().toLowerCase() === 'production') {
    throw new Error(`${ferramenta} não roda na produção: ele cria dados de demonstração ou de carga.`);
  }
}
