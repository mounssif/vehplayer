# Deploying via Cloudflare Pages (GitHub integration)

`webclient/probe/` is already a plain static folder (single `index.html`, zero deps, zero
build step), so it maps directly onto a CF Pages project without any pipeline config.

## Dashboard settings (Workers & Pages -> Create -> connect to Git)
- Framework preset: **None**
- Build command: *(leave empty)*
- Build output directory: `webclient/probe`
- Root directory: `/` (repo root, since the output dir path above already points into the repo)

No environment variables, no `wrangler.toml` needed for this folder as-is. If a build step gets
added later (bundler for `webclient/src`, once Gate 2's real car client exists), a `wrangler.toml`
or `package.json` build script belongs at that point, not before, same "don't build ahead of the
gate" discipline as everything else in this repo (Foundation §12).

## Suggested project naming
Domain is still TODO (Foundation §1), so use a throwaway CF Pages subdomain
(`vehplayer-probe.pages.dev` or similar) for now rather than wiring a custom domain. Swap in a
real subdomain once the domain/trademark question resolves; nothing here is load-bearing on
the final name.

## What this deploys
Only the S2 browser capability probe. It has no backend, the WebSocket round-trip box at the
bottom needs a `ws://` URL typed in manually (the phone's local address once `android/` has a
WS server, Gate 2). Nothing here talks to `cloud/` (control plane, not built yet) or handles
any real video/media, consistent with Foundation §6's "data plane is local" rule, this is a
pure static test page.
