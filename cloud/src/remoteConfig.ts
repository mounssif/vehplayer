// Remote config, per Tesla firmware version (ARCHITECTURE.md §6: "remote
// config/kill-switches per firmware version"). This is the mechanism behind
// Foundation §10 Failure Mode #2's mitigation: if a firmware update blocks a
// reachability tier or breaks WebCodecs, this lets us disable that tier for
// affected firmware versions without an app-store release cycle.

import type { Env } from './index';

export interface RemoteConfig {
  minClientVersion: number; // matches HELLO_VERSION in webclient/src/control.ts, forces a reload if the car's cached bundle is stale (ARCHITECTURE.md §6)
  reachabilityTiersEnabled: {
    ipv6Gua: boolean;
    cgnatVpn: boolean;
    publicRangeVpn: boolean;
  };
  webCodecsEnabled: boolean; // kill switch to force the MSE fallback path if a firmware update breaks WebCodecs decode
  killSwitch: boolean; // nuclear option: show a "temporarily unavailable" screen instead of streaming
  message: string | null; // shown to the user when killSwitch is true, or as a non-blocking banner otherwise
}

const DEFAULT_CONFIG: RemoteConfig = {
  minClientVersion: 1,
  reachabilityTiersEnabled: {
    ipv6Gua: true,
    cgnatVpn: true,
    publicRangeVpn: true,
  },
  webCodecsEnabled: true,
  killSwitch: false,
  message: null,
};

export async function handleRemoteConfig(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const firmwareVersion = url.searchParams.get('firmware') ?? 'unknown';

  // TODO(claude-code): this is a flat key lookup; once more than a couple of
  // firmware-specific overrides exist, consider range matching (e.g.
  // "2025.20.x and later") rather than exact-string keys. Not needed at
  // Gate 5 launch scale, flagged so it's not forgotten once the compat
  // matrix (GROWTH_SAAS.md §6) has real data to drive this from.
  const override = await env.REMOTE_CONFIG.get(`firmware:${firmwareVersion}`, 'json') as Partial<RemoteConfig> | null;
  const config: RemoteConfig = { ...DEFAULT_CONFIG, ...override };

  return new Response(JSON.stringify(config), {
    status: 200,
    headers: {
      'content-type': 'application/json',
      // Short cache: kill-switches need to propagate fast (Foundation §10
      // "compatibility telemetry to detect breakage within hours"), a stale
      // cached "everything fine" response defeats that.
      'cache-control': 'public, max-age=60',
    },
  });
}
