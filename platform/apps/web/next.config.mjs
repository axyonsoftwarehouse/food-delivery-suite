/** @type {import('next').NextConfig} */
const config = {
  async rewrites() {
    const api = process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4001';
    return [{ source: '/backend/:path*', destination: `${api}/:path*` }];
  },
};

export default config;
