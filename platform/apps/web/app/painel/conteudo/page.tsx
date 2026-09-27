'use client';

import ContentPanel from '../conteudo-panel';
import { useApp } from '../../app-context';

export default function ConteudoPage() {
  const { user } = useApp();
  if (!user || user.role !== 'admin') return <section className="panel"><div className="empty-state">Conteúdo disponível para a administração.</div></section>;
  return <ContentPanel />;
}
