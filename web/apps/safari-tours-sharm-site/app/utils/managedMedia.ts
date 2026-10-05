const MANAGED_MEDIA_PATH = /^\/media\/(?:tours\/[a-z0-9-]+|categories\/(?:DESERT|SEA|CULTURAL|SHOWS|TRANSFERS))\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(?:jpg|png)$/;

export function isManagedMediaPath(path: string | null | undefined): boolean {
  return typeof path === "string" && MANAGED_MEDIA_PATH.test(path);
}

/** Only derivatives the backend actually creates, never upscaled widths. */
export function managedMediaSrcset(path: string, width: number | undefined): string | undefined {
  if (!isManagedMediaPath(path) || !width || !Number.isSafeInteger(width) || width < 1 || width > 16384) return undefined;
  return [...[360, 768, 1024, 1440].filter((size) => size < width).map((size) => `${path}?v=w${size} ${size}w`), `${path} ${width}w`].join(", ");
}

/** Managed assets must bypass IPX: every fetch must re-check backend rights. */
export function isManagedOptimizerRequest(path: string): boolean {
  let decoded = path;
  for (let i = 0; i < 3; i++) {
    try { decoded = decodeURIComponent(decoded); } catch { return false; }
  }
  if (!decoded.startsWith("/_ipx/")) return false;
  const media = decoded.substring(decoded.indexOf("/media/")).split(/[?#]/, 1)[0];
  return isManagedMediaPath(media);
}
