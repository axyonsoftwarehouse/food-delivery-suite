export type SettingsSection = { slug: string; group: string; label: string };

export const SETTINGS_GROUP_SECTIONS: SettingsSection[] = [
  { slug: 'negocio', group: 'Negócio', label: 'Negócio' },
  { slug: 'operacao', group: 'Operação', label: 'Operação' },
  { slug: 'politicas', group: 'Políticas', label: 'Políticas' },
  { slug: 'manutencao', group: 'Manutenção', label: 'Manutenção' },
  { slug: 'programa', group: 'Programa', label: 'Programa' },
  { slug: 'analytics', group: 'Analytics', label: 'Analytics' },
  { slug: 'integracoes', group: 'Integrações', label: 'Integrações' },
];

export const CONFIG_SECTIONS = {
  conta: '/painel/configuracoes/conta',
  pagamentos: '/painel/configuracoes/pagamentos',
  auditoria: '/painel/configuracoes/auditoria',
} as const;

export function settingsGroupFromSlug(slug: string): string | undefined {
  return SETTINGS_GROUP_SECTIONS.find((section) => section.slug === slug)?.group;
}
