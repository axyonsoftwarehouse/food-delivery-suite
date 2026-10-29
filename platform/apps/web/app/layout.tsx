import type { Metadata } from 'next';
import './tokens.css';
import './styles.css';
import './ui.css';
import './motion.css';
import './auth.css';
import './loja.css';
import './loja-conta.css';
import './loja-vitrine.css';
import './entregas.css';
import './painel.css';
import './painel-vitrine.css';
import './painel/css/horarios-mesas-pdv.css';
import './painel/css/fin-promo-pagina.css';
import './painel/css/equipe-config.css';
import './painel/css/visao-pedidos.css';
import './painel/css/catalogo-estoque.css';
import './painel/css/admin.css';
import './home.css';
import './cozinha.css';
import './order-chat.css';
import { AppProvider } from './app-context';

export const metadata: Metadata = {
  title: 'Foodie • Plataforma independente',
  description: 'Plataforma operacional para pedidos, restaurantes e entregadores.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <head>
        {/* Aplica o tema salvo antes da primeira pintura; num useEffect a tela piscava clara no tema escuro. */}
        <script dangerouslySetInnerHTML={{ __html: "try{document.documentElement.dataset.theme=localStorage.getItem('foodie_theme')==='dark'?'dark':'light'}catch(e){}" }} />
      </head>
      <body>
        <AppProvider>{children}</AppProvider>
      </body>
    </html>
  );
}
