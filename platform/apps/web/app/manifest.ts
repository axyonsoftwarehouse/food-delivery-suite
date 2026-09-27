import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Foodie',
    short_name: 'Foodie',
    description: 'Plataforma de delivery multirrestaurante.',
    start_url: '/',
    display: 'standalone',
    background_color: '#f8f8f4',
    theme_color: '#113c32',
    icons: [
      { src: '/favicon.ico', sizes: 'any', type: 'image/x-icon' },
    ],
  };
}
