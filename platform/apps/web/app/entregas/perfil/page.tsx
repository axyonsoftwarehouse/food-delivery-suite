'use client';

import { useEffect, useState } from 'react';
import { api } from '../../app-context';
import { useLocationStatus } from '../use-location-sharing';
import ReputationCard from '../reputation-card';

type InstallEvent = Event & { prompt: () => Promise<void> };
type Profile = { name: string; email: string; restaurant_name: string | null; vehicle_type: string | null; vehicle_plate: string | null };
const VEHICLE: Record<string, string> = { moto: 'Moto', bike: 'Bicicleta', carro: 'Carro', van: 'Van', a_pe: 'A pé' };

export default function PerfilPage() {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [profileFailed, setProfileFailed] = useState(false);
  const [ios, setIos] = useState(false);
  const status = useLocationStatus();
  const [install, setInstall] = useState<InstallEvent | null>(null);
  const [notifications, setNotifications] = useState<string>('default');
  useEffect(() => {
    const capture = (event: Event) => { event.preventDefault(); setInstall(event as InstallEvent); };
    const readPermission = () => setNotifications(typeof Notification === 'undefined' ? 'unsupported' : Notification.permission);
    // a permissão pode mudar nas configurações do navegador: relê quando a aba volta
    const onVisible = () => { if (document.visibilityState === 'visible') readPermission(); };
    window.addEventListener('beforeinstallprompt', capture);
    document.addEventListener('visibilitychange', onVisible);
    readPermission();
    setIos(/iphone|ipad|ipod/i.test(navigator.userAgent) && !(navigator as Navigator & { standalone?: boolean }).standalone);
    api<Profile>('/courier/profile').then(setProfile).catch(() => setProfileFailed(true));
    return () => { window.removeEventListener('beforeinstallprompt', capture); document.removeEventListener('visibilitychange', onVisible); };
  }, []);
  return <>
    <h1 className="courier-section-title">{profile?.name}</h1>
    <div className="courier-queue">
      {!profile && <p className="courier-empty">{profileFailed ? 'Não foi possível carregar seus dados. Tente de novo.' : 'Carregando…'}</p>}
      {profile && <>
      <div className="courier-row"><div><strong>{profile?.restaurant_name ?? 'Sem loja'}</strong><small>{profile?.restaurant_name ? 'Você entrega para esta loja.' : 'Peça à loja ou ao suporte para ligar seu acesso a uma loja.'}</small></div></div>
      <div className="courier-row"><div><strong>{'Veículo'}</strong><small>{profile?.vehicle_type ? `${VEHICLE[profile.vehicle_type] ?? profile.vehicle_type}${profile.vehicle_plate ? ` · ${profile.vehicle_plate}` : ''}` : 'Não informado'}</small></div></div>
      <div className="courier-row"><div><strong>{'E-mail'}</strong><small>{profile?.email}</small></div></div>
      </>}
      <ReputationCard />
      <div className="courier-row"><div><strong>{'Localização'}</strong><small>{status === 'blocked'
        ? 'Bloqueada. No Chrome: cadeado na barra de endereço → Permissões → Localização → Permitir. No Safari: Ajustes → Safari → Localização → Permitir.'
        : status === 'unsupported' ? 'Este navegador não oferece localização; o cliente não verá o rastreio.'
        : 'Compartilhada só durante as entregas.'}</small></div></div>
      <div className="courier-row"><div><strong>{'Notificações'}</strong><small>{notifications === 'granted' ? 'Ativas: você recebe aviso de entrega nova com a tela fechada.'
        : notifications === 'denied' ? 'Bloqueadas no navegador. Libere nas permissões do site para receber aviso de entrega nova.'
        : notifications === 'unsupported' ? 'Este navegador não oferece notificações.' : 'Toque no sino, no topo, para ativar o aviso de entrega nova.'}</small></div></div>
      {install ? <button type="button" className="courier-primary" onClick={() => { const event = install; setInstall(null); void event.prompt(); }}>{'Instalar na tela inicial'}</button>
        : ios && <div className="courier-row"><div><strong>{'Instalar'}</strong><small>{'No Safari: toque em Compartilhar → Adicionar à Tela de Início'}</small></div></div>}
    </div>
  </>;
}
