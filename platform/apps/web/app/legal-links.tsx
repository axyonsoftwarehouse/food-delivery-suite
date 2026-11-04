import Link from 'next/link';

/** Páginas legais publicadas pelo painel (Conteúdo → Páginas), servidas em /paginas/[slug]. */
export const LEGAL_PAGES = [
  { slug: 'termos', label: 'Termos de uso' },
  { slug: 'privacidade', label: 'Privacidade' },
  { slug: 'reembolso', label: 'Reembolso' },
] as const;

export default function LegalLinks({ className }: { className?: string }) {
  return <nav className={`legal-links${className ? ` ${className}` : ''}`} aria-label="Termos e políticas">
    {LEGAL_PAGES.map((page) => <Link key={page.slug} href={`/paginas/${page.slug}`}>{page.label}</Link>)}
  </nav>;
}
