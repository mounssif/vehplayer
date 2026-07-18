# vehplayer control plane (Cloudflare Workers)

Control plane only, never media (Foundation §6, ARCHITECTURE.md overview
diagram: "Cloud (control plane only, never media)"). Three routes:

- `/entitlements?deviceId=...` , Freemium/Pro feature flags (GROWTH_SAAS.md §4)
- `/config?firmware=...` , remote config + kill-switches per Tesla firmware
  version (ARCHITECTURE.md §6, Foundation §10 Failure Mode #2 mitigation)
- `/telemetry` (POST) , opt-in compatibility reports, feeds the public "does
  my car work" matrix (GROWTH_SAAS.md §6, Gate 5)

## Setup
```
npm install
npx wrangler kv namespace create ENTITLEMENTS
npx wrangler kv namespace create REMOTE_CONFIG
npx wrangler kv namespace create COMPAT_TELEMETRY
```
Paste the returned IDs into `wrangler.toml` (currently `REPLACE_ME`
placeholders), then:
```
npm run dev       # local dev server
npm run typecheck # tsc --noEmit, already verified clean in this session
npm run deploy    # wrangler deploy, needs a CF account, not runnable here
```

## What's real vs stubbed
- Routing, request/response shapes, and KV read/write calls are real code,
  typechecked clean against `@cloudflare/workers-types`.
- Auth on `/entitlements` is a placeholder (client-supplied `deviceId`, no
  signature/token verification), flagged inline in `src/entitlements.ts`.
  Do not treat Pro-gating as trustworthy until that's replaced with a real
  purchase-flow-issued token (Gate 5, GROWTH_SAAS.md §4).
- The public compat-matrix *page* that reads `COMPAT_TELEMETRY` back out
  doesn't exist yet, only the ingest side. That's explicitly Gate 5 work
  (GROWTH_SAAS.md §6).

## Deployment
Same platform as `webclient/probe` (see its `DEPLOY_CF_PAGES.md`), kept on
Cloudflare for the "near-zero marginal cost" cost structure decided in
Foundation §9 rather than introducing a second cloud vendor.
