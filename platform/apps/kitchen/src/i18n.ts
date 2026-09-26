import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

const resources = {
  'pt-BR': {
    translation: {
      signIn: {
        title: 'Foodie Cozinha',
        subtitle: 'Entre com um acesso de cozinha ou do restaurante.',
        email: 'Email',
        password: 'Senha',
        submit: 'Entrar',
        loading: 'Entrando...',
        invalidRole: 'Este acesso não é de cozinha nem do restaurante.',
      },
      board: {
        new: 'Novos',
        preparing: 'Em preparo',
        ready: 'Prontos',
        empty: 'Nenhum pedido',
        late: 'ATRASADO',
        refresh: 'Atualizar',
        lastSync: 'Atualizado às {{time}}',
        offline: 'Sem conexão. Mostrando o último estado.',
        signOut: 'Sair',
        order: 'Pedido',
        minutes: '{{count}} min',
      },
      ticket: {
        items: 'Itens',
        address: 'Endereço',
        scheduled: 'Agendado',
        placed: 'Recebido',
        accept: 'Aceitar',
        ready: 'Marcar pronto',
        reject: 'Recusar',
        rejectReason: 'Motivo da recusa',
        send: 'Confirmar recusa',
        cancel: 'Cancelar',
        waiting: 'Aguardando a cozinha',
        history: 'Histórico',
        print: 'Imprimir cupom',
        printing: 'Abrindo impressão...',
        printError: 'Não foi possível imprimir',
      },
      status: {
        placed: 'Novo',
        accepted: 'Em preparo',
        ready: 'Pronto',
        assigned: 'Com entregador',
        picked_up: 'Saiu para entrega',
        delivered: 'Entregue',
        rejected: 'Recusado',
        cancelled: 'Cancelado',
        expired: 'Expirado',
        failed: 'Falhou',
      },
    },
  },
} as const;

void i18n.use(initReactI18next).init({
  resources,
  lng: 'pt-BR',
  fallbackLng: 'pt-BR',
  interpolation: { escapeValue: false },
});

export default i18n;
