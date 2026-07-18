import { defineConfig } from 'vite';

// Deployed the same way as webclient/probe (Cloudflare Pages, GitHub integration,
// see webclient/probe/DEPLOY_CF_PAGES.md), just with a build step now: output
// directory `dist`, build command `npm run build`.
export default defineConfig({
  build: {
    target: 'es2022',
    sourcemap: true,
  },
  server: {
    // car browser needs to reach this from another device during dev;
    // run `vite --host` or set this true when testing against a real car.
    host: true,
  },
});
