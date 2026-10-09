'use client';

import { useEffect, useState } from 'react';
import { api } from '../../app-context';
import { useLocationStatus } from '../use-location-sharing';

type InstallEvent = Event & { prompt: () => Promise<void> };
type Profile = { name: string; email: string; restaurant_name: string | null; vehicle_type: string | null; vehicle_plate: string | null };
const VEHICLE: Record<string, string> = { moto: 'Moto', bike: 'Bicicleta', carro: 'Carro', van: 'Van', a_pe: 'A pé' };

export default function PerfilPage() {
  const [profile, setProfile] = useState<Profile | null>(null);
  const status = useLocationStatus();
  const [install, setInstall] = useState<InstallEvent | null>(null);
  const [notifications, setNotifications] = useState<string>('default');
  useEffect(() => {
    const capture = (event: Event) => { event.preventDefault(); setInstall(event as InstallEvent); };
    window.addEventListener('beforeinstallprompt', capture);
    setNotifications(typeof Notification === 'undefined' ? 'unsupported' : Notification.permission);
    api<Profile>('/courier/profile').then(setProfile).catch(() => {});
    return () => window.removeEventListener('beforeinstallprompt', capture);
  }, []);
  return <>
    <h1 className="courier-section-title">{profile?.name}</h1>
    <div className="courier-queue">
      <div className="courier-row"><div><strong>{profile?.restaurant_name ?? 'Sem loja'}</strong><small>{profile?.restaurant_name ? 'Você entrega para esta loja.' : 'Peça à loja ou ao suporte para ligar seu acesso a uma loja.'}</small></div></div>
      <div className="courier-row"><div><strong>{'Veículo'}</strong><small>{profile?.vehicle_type ? `${VEHICLE[profile.vehicle_type] ?? profile.vehicle_type}${profile.vehicle_plate ? ` · ${profile.vehicle_plate}` : ''}` : 'Não informado'}</small></div></div>
      <div className="courier-row"><div><strong>{'E-mail'}</strong><small>{profile?.email}</small></div></div>
      <div className="courier-row"><div><strong>{'Localização'}</strong><small>{status === 'blocked'
        ? 'Bloqueada. No Chrome: cadeado na barra de endereço → Permissões → Localização → Permitir. No Safari: Ajustes → Safari → Localização → Permitir.'
        : status === 'unsupported' ? 'Este navegador não oferece localização; o cliente não verá o rastreio.'
        : 'Compartilhada só durante as entregas.'}</small></div></div>
      <div className="courier-row"><div><strong>{'Notificações'}</strong><small>{notifications === 'granted' ? 'Ativas: você recebe aviso de entrega nova com a tela fechada.'
        : notifications === 'denied' ? 'Bloqueadas no navegador. Libere nas permissões do site para receber aviso de entrega nova.'
        : notifications === 'unsupported' ? 'Este navegador não oferece notificações.' : 'Toque no sino, no topo, para ativar o aviso de entrega nova.'}</small></div></div>
      {install && <button type="button" className="courier-primary" onClick={() => void install.prompt()}>{'Instalar na tela inicial'}</button>}
    </div>
  </>;
}
