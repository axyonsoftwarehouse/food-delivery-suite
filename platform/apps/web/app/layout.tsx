import type { Metadata } from 'next';
import './styles.css';

export const metadata: Metadata = {
  title: 'Foodie • Plataforma independente',
  description: 'Protótipo operacional para pedidos, restaurantes e entregadores.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="pt-BR"><body>{children}</body></html>;
}
