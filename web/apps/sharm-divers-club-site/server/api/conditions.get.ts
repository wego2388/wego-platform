/**
 * Live Sharm El Sheikh sea/weather conditions, proxied server-side from
 * Open-Meteo (free, keyless, no API key required — https://open-meteo.com).
 * Real coordinates for Sharm El Sheikh (27.9158°N, 34.3299°E). Never
 * fabricates a value: any field this route can't fetch comes back null,
 * and the client shows an honest "unavailable" state instead of a guess.
 */

const LATITUDE = 27.9158;
const LONGITUDE = 34.3299;
const CACHE_TTL_MS = 10 * 60 * 1000;
const FETCH_TIMEOUT_MS = 8000;

interface ConditionsResponse {
  fetchedAt: string;
  air: { tempC: number; windKph: number; weatherCode: number } | null;
  sea: { tempC: number; waveHeightM: number } | null;
}

let cache: { at: number; value: ConditionsResponse } | null = null;

/**
 * A stalled (accepted-but-never-completing) connection would otherwise hold
 * the request open indefinitely — a plain `fetch` has no default timeout,
 * so the "unavailable" fallback this route promises would never actually
 * be reached.
 */
async function fetchWithTimeout(url: string): Promise<Response> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    return await fetch(url, { signal: controller.signal });
  } finally {
    clearTimeout(timeout);
  }
}

async function fetchAir(): Promise<ConditionsResponse["air"]> {
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${LATITUDE}&longitude=${LONGITUDE}&current=temperature_2m,wind_speed_10m,weather_code&timezone=auto`;
  const response = await fetchWithTimeout(url);
  if (!response.ok) return null;
  const data = (await response.json()) as { current?: { temperature_2m?: number | null; wind_speed_10m?: number | null; weather_code?: number | null } };
  if (data.current?.temperature_2m == null || data.current.wind_speed_10m == null || data.current.weather_code == null) {
    return null;
  }
  return { tempC: data.current.temperature_2m, windKph: data.current.wind_speed_10m, weatherCode: data.current.weather_code };
}

async function fetchSea(): Promise<ConditionsResponse["sea"]> {
  const url = `https://marine-api.open-meteo.com/v1/marine?latitude=${LATITUDE}&longitude=${LONGITUDE}&current=wave_height,sea_surface_temperature&timezone=auto`;
  const response = await fetchWithTimeout(url);
  if (!response.ok) return null;
  const data = (await response.json()) as { current?: { wave_height?: number | null; sea_surface_temperature?: number | null } };
  if (data.current?.wave_height == null || data.current.sea_surface_temperature == null) return null;
  return { tempC: data.current.sea_surface_temperature, waveHeightM: data.current.wave_height };
}

export default defineEventHandler(async (): Promise<ConditionsResponse> => {
  if (cache && Date.now() - cache.at < CACHE_TTL_MS) {
    return cache.value;
  }

  const [air, sea] = await Promise.all([
    fetchAir().catch(() => null),
    fetchSea().catch(() => null),
  ]);

  const value: ConditionsResponse = { fetchedAt: new Date().toISOString(), air, sea };
  cache = { at: Date.now(), value };
  return value;
});
