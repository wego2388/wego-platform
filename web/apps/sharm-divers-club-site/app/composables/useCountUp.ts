import { onBeforeUnmount, onMounted, ref } from "vue";

export function useCountUp(target: number, durationMs = 900) {
  const el = ref<HTMLElement | null>(null);
  const value = ref(0);
  let observer: IntersectionObserver | undefined;
  let frame: number | undefined;

  function animate() {
    const start = performance.now();
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / durationMs);
      const eased = 1 - (1 - progress) ** 3;
      value.value = Math.round(target * eased);
      if (progress < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
  }

  onMounted(() => {
    // Checked before setting up the observer, not just inside animate() —
    // otherwise a reduced-motion visitor whose viewport never crosses the
    // 40% intersection threshold (this happened on mobile, where the hero
    // reflows taller and the stat card sits closer to the fold) sees a
    // permanent "0" instead of the real number, exactly the credibility
    // problem this counter exists to avoid. Mirrors useScrollReveal's own
    // SSR/reduced-motion-safe pattern.
    if (
      typeof window === "undefined" ||
      !("IntersectionObserver" in window) ||
      window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ) {
      value.value = target;
      return;
    }
    observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            animate();
            observer?.disconnect();
          }
        }
      },
      { threshold: 0.4 },
    );
    if (el.value) observer.observe(el.value);
  });

  onBeforeUnmount(() => {
    observer?.disconnect();
    if (frame !== undefined) cancelAnimationFrame(frame);
  });

  return { el, value };
}
