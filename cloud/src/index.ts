// Control plane only, never media (Foundation §6: "Cloud never sees a video
// frame"). Three concerns, kept in one small Worker rather than three
// services, per Foundation §9's "near-zero marginal cost" thesis, this is a
// licensing API, not infrastructure that needs to scale like a video relay.

import { handleEntitlements } from './entitlements';
import { handleRemoteConfig } from './remoteConfig';
import { handleTelemetry } from './telemetry';

export interface Env {
  ENTITLEMENTS: KVNamespace;
  REMOTE_CONFIG: KVNamespace;
  COMPAT_TELEMETRY: KVNamespace;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname.startsWith('/entitlements')) {
      return handleEntitlements(request, env);
    }
    if (url.pathname.startsWith('/config')) {
      return handleRemoteConfig(request, env);
    }
    if (url.pathname.startsWith('/telemetry')) {
      return handleTelemetry(request, env);
    }

    return new Response('vehplayer control plane. See docs/GROWTH_SAAS.md / ARCHITECTURE.md for what this does and does not do.', {
      status: 200,
      headers: { 'content-type': 'text/plain' },
    });
  },
};
