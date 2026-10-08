export function galleryStep(index: number, delta: number, count: number): number {
  return count > 0 ? ((index + delta) % count + count) % count : 0;
}
export function gallerySwipe(dx: number, dy: number, rtl: boolean): number {
  if (Math.abs(dx) < 48 || Math.abs(dx) <= Math.abs(dy) * 1.25) return 0;
  const direction = dx < 0 ? 1 : -1;
  return rtl ? -direction : direction;
}
