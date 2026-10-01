'use client';

import { useParams } from 'next/navigation';
import SupportProfile from '../../support-profile';
import { useApp } from '../../../app-context';
import { useI18n } from '../../../i18n';

export default function SuporteLojaPage() {
  const { user, permissions } = useApp();
  const { t } = useI18n();
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  if (!user || user.role !== 'admin' || !permissions.includes('support.view')) {
    return <section className="panel"><div className="empty-state">{t('support.noAccess')}</div></section>;
  }
  if (!Number.isInteger(id) || id < 1) return <section className="panel"><div className="empty-state">{t('support.invalidStore')}</div></section>;
  return <SupportProfile restaurantId={id} />;
}
