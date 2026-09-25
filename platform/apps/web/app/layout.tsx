import type { Metadata } from 'next';
import './styles.css';

export const metadata: Metadata = {
  title: 'Foodie â€¢ Plataforma independente',
  description: 'ProtÃ³tipo operacional para pedidos, restaurantes e entregadores.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="pt-BR"><body>{children}</body></html>;
}
