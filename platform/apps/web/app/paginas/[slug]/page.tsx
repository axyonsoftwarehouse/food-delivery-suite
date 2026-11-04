import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { Icon } from '../../icons';
import LegalLinks from '../../legal-links';

type ContentPage = { slug: string; title: string; body: string };

type Props = { params: Promise<{ slug: string }> };

async function contentPage(slug: string): Promise<ContentPage> {
  if (!/^[a-z0-9-]{2,120}$/.test(slug)) notFound();
  const api = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
  const response = await fetch(`${api}/public/pages/${slug}`, { cache: 'no-store' });
  if (response.status === 404) notFound();
  if (!response.ok) throw new Error('Não foi possível carregar a página.');
  return response.json() as Promise<ContentPage>;
}

/** Texto simples do painel: bloco separado por linha em branco; "## " abre um subtítulo e "- " um item de lista. Sem HTML. */
function blocks(body: string) {
  return body.split(/\n\s*\n/).map((block) => block.trim()).filter(Boolean);
}

function Block({ text }: { text: string }) {
  const lines = text.split('\n');
  if (lines[0].startsWith('## ')) {
    return <><h2>{lines[0].slice(3)}</h2>{lines.length > 1 && <Block text={lines.slice(1).join('\n')} />}</>;
  }
  if (lines.every((line) => line.startsWith('- '))) return <ul>{lines.map((line, index) => <li key={index}>{line.slice(2)}</li>)}</ul>;
  return <p>{text}</p>;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const page = await contentPage((await params).slug);
  return { title: `${page.title} • Foodie` };
}

export default async function ContentPageView({ params }: Props) {
  const page = await contentPage((await params).slug);
  return <main className="home legal-page">
    <header className="home-header"><Link href="/" className="home-brand" aria-label="Foodie, início"><span className="home-brand-mark"><Icon name="sparkle" size={18} /></span>foodie<span>.</span></Link><nav><Link className="home-login" href="/entrar">{'Entrar'}</Link></nav></header>
    <article className="legal-body">
      <h1>{page.title}</h1>
      {blocks(page.body).map((block, index) => <Block key={index} text={block} />)}
    </article>
    <footer className="home-footer"><LegalLinks /><span>{'© 2026 Axyon Software House'}</span></footer>
  </main>;
}
