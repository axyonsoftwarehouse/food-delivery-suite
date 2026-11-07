// Identificação dos dados do teste de carga: tudo o que o `load:setup` cria e o `load:clean` apaga.
export const CARGA_DOMAIN = 'carga.local';
export const CARGA_ZONE_SLUG = 'carga-foodie';
export const CARGA_STORE_PREFIX = 'carga-foodie-';
export const CARGA_POSTAL_START = '69999000';
export const CARGA_POSTAL_END = '69999999';

export const storeSlug = (index: number) => `${CARGA_STORE_PREFIX}${String(index).padStart(2, '0')}`;
