import { onBeforeUnmount, onMounted, ref } from "vue";

/**
 * Reveals element when it enters the viewport.
 * Defaults to visible (server-rendered content never hidden without JS).
 * Skipped entirely under prefers-reduced-motion.
 */
export function useScrollReveal() {
  const el = ref<HTMLElement | null>(null);
  const visible = ref(true);
  let observer: IntersectionObserver | undefined;

  onMounted(() => {
    if (
      typeof window === "undefined" ||
      !("IntersectionObserver" in window) ||
      window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ) {
      visible.value = true;
      return;
    }
    if (!el.value) return;
    const rect = el.value.getBoundingClientRect();
    // already in viewport on mount — reveal immediately
    if (rect.top < window.innerHeight && rect.bottom > 0) {
      visible.value = true;
      return;
    }
    visible.value = false;
    observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            visible.value = true;
            observer?.disconnect();
          }
        }
      },
      { threshold: 0.12 },
    );
    observer.observe(el.value);
  });

  onBeforeUnmount(() => observer?.disconnect());

  return { el, visible };
}
