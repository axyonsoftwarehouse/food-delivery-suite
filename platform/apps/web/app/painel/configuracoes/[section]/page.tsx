'use client';

import { useParams } from 'next/navigation';
import SettingsPanel from '../../settings-panel';
import AdminAuditPanel from '../../admin-audit-panel';
import PaymentAccountCard from '../../payment-account-card';
import StoreContactCard from '../../store-contact-card';
import { useApp } from '../../../app-context';

export default function ConfiguracoesSectionPage() {
  const params = useParams<{ section: string }>();
  const section = Array.isArray(params?.section) ? params.section[0] : params?.section;
  const { user, permissions } = useApp();

  if (section === 'pagamentos') {
    return user?.role === 'restaurant' && permissions.includes('payments.manage') ? <PaymentAccountCard /> : null;
  }

  if (section === 'loja') {
    return user?.role === 'restaurant' && permissions.includes('settings.manage') ? <StoreContactCard /> : null;
  }

  if (section === 'auditoria') {
    return user?.role === 'admin' && permissions.includes('audit.view') ? <AdminAuditPanel /> : null;
  }

  return <SettingsPanel section={section ?? 'conta'} />;
}
