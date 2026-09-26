import type { Metadata } from 'next';
import './tokens.css';
import './styles.css';
import './ui.css';
import { AppProvider } from './app-context';

export const metadata: Metadata = {
  title: 'Foodie • Plataforma independente',
  description: 'Plataforma operacional para pedidos, restaurantes e entregadores.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="pt-BR"><body><AppProvider>{children}</AppProvider></body></html>;
}
