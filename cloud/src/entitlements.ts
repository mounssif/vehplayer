// Entitlements (Foundation §9/§13): free tier needs no server round trip at
// all (mirror + touch works fully offline, Foundation principle 3 "Local"
// applies to entitlement checks too, a free user should never be blocked by
// this Worker being unreachable). Only Pro-gated features call this.

import type { Env } from './index';

export interface Entitlement {
  deviceId: string;
  tier: 'free' | 'pro';
  // GROWTH_SAAS.md §4 Pro contents: Power Mode, low-latency audio, split
  // view, auto-connect, multi-car profiles. Flags rather than a single
  // "isPro" boolean, in case pricing experiments (Gate 4 beta survey) end up
  // unbundling these rather than shipping one flat Pro tier.
  features: {
    powerMode: boolean;
    lowLatencyAudio: boolean;
    splitView: boolean;
    autoConnect: boolean;
    multiCarProfiles: boolean;
  };
  expiresAt: string | null; // ISO 8601, null = perpetual (one-time+updates purchase, GROWTH_SAAS.md §4 pricing shape still TBD)
}

const FREE_ENTITLEMENT: Omit<Entitlement, 'deviceId'> = {
  tier: 'free',
  features: {
    powerMode: false,
    lowLatencyAudio: false,
    splitView: false,
    autoConnect: false,
    multiCarProfiles: false,
  },
  expiresAt: null,
};

export async function handleEntitlements(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const deviceId = url.searchParams.get('deviceId');
  if (!deviceId) {
    return jsonResponse({ error: 'missing deviceId' }, 400);
  }

  // TODO(claude-code): deviceId as the sole key is a placeholder, needs a
  // real auth story (Gate 5, licensing) before this is trustworthy, e.g. a
  // server-issued signed token from a purchase flow rather than a
  // client-supplied identifier anyone could forge to claim Pro features free.
  const stored = await env.ENTITLEMENTS.get(deviceId, 'json') as Entitlement | null;
  const entitlement: Entitlement = stored ?? { deviceId, ...FREE_ENTITLEMENT };

  return jsonResponse(entitlement, 200);
}

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}
