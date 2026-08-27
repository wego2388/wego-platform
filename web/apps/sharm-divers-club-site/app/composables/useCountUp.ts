import { onBeforeUnmount, onMounted, ref } from "vue";

export function useCountUp(target: number, durationMs = 900) {
  const el = ref<HTMLElement | null>(null);
  const value = ref(0);
  let observer: IntersectionObserver | undefined;
  let frame: number | undefined;

  function animate() {
    if (typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      value.value = target;
      return;
    }
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
    if (typeof window === "undefined" || !("IntersectionObserver" in window)) {
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
