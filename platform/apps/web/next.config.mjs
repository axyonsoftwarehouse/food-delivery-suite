import { withSentryConfig } from '@sentry/nextjs/config';

/** @type {import('next').NextConfig} */
const config = {
  async rewrites() {
    const api = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
    return [{ source: '/backend/:path*', destination: `${api}/:path*` }];
  },
};

export default withSentryConfig(config, {
  org: 'axyon-software-house',
  project: 'foodie-web',
  silent: true,
  sourcemaps: { disable: true },
});
