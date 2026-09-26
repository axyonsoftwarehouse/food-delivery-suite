import type { Metadata } from 'next';
import './tokens.css';
import './styles.css';
import './ui.css';
import { AppProvider } from './app-context';
import { I18nProvider } from './i18n';

export const metadata: Metadata = {
  title: 'Foodie • Plataforma independente',
  description: 'Plataforma operacional para pedidos, restaurantes e entregadores.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR">
      <body>
        <I18nProvider>
          <AppProvider>{children}</AppProvider>
        </I18nProvider>
      </body>
    </html>
  );
}
