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

interface ConditionsResponse {
  fetchedAt: string;
  air: { tempC: number; windKph: number; weatherCode: number } | null;
  sea: { tempC: number; waveHeightM: number } | null;
}

let cache: { at: number; value: ConditionsResponse } | null = null;

async function fetchAir(): Promise<ConditionsResponse["air"]> {
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${LATITUDE}&longitude=${LONGITUDE}&current=temperature_2m,wind_speed_10m,weather_code&timezone=auto`;
  const response = await fetch(url);
  if (!response.ok) return null;
  const data = (await response.json()) as { current?: { temperature_2m?: number; wind_speed_10m?: number; weather_code?: number } };
  if (data.current?.temperature_2m === undefined || data.current.wind_speed_10m === undefined || data.current.weather_code === undefined) {
    return null;
  }
  return { tempC: data.current.temperature_2m, windKph: data.current.wind_speed_10m, weatherCode: data.current.weather_code };
}

async function fetchSea(): Promise<ConditionsResponse["sea"]> {
  const url = `https://marine-api.open-meteo.com/v1/marine?latitude=${LATITUDE}&longitude=${LONGITUDE}&current=wave_height,sea_surface_temperature&timezone=auto`;
  const response = await fetch(url);
  if (!response.ok) return null;
  const data = (await response.json()) as { current?: { wave_height?: number; sea_surface_temperature?: number } };
  if (data.current?.wave_height === undefined || data.current.sea_surface_temperature === undefined) return null;
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
