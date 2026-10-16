'use client';

import { useParams } from 'next/navigation';
import SettingsPanel from '../../settings-panel';
import AdminAuditPanel from '../../admin-audit-panel';
import PaymentAccountCard from '../../payment-account-card';
import StoreContactCard from '../../store-contact-card';
import { Empty } from '../../kit';
import { useApp } from '../../../app-context';

/** Sem a permissão da seção, avisa em vez de deixar a página em branco (a regra continua a mesma). */
function NoAccess() {
  return <section className="pr-card"><Empty icon="lock">Você não tem permissão para ver esta seção.</Empty></section>;
}

export default function ConfiguracoesSectionPage() {
  const params = useParams<{ section: string }>();
  const section = Array.isArray(params?.section) ? params.section[0] : params?.section;
  const { user, permissions } = useApp();

  if (section === 'pagamentos') {
    if (!user) return null;
    return user.role === 'restaurant' && permissions.includes('payments.manage') ? <PaymentAccountCard /> : <NoAccess />;
  }

  if (section === 'loja') {
    if (!user) return null;
    return user.role === 'restaurant' && permissions.includes('settings.manage') ? <StoreContactCard /> : <NoAccess />;
  }

  if (section === 'auditoria') {
    if (!user) return null;
    return user.role === 'admin' && permissions.includes('audit.view') ? <AdminAuditPanel /> : <NoAccess />;
  }

  return <SettingsPanel section={section ?? 'conta'} />;
}
