import { onBeforeUnmount, onMounted, ref } from "vue";

/**
 * Defaults to visible so server-rendered content is never hidden without
 * client JavaScript. Only opts into a hidden-then-reveal transition once
 * mounted client-side, and only for elements not already in the viewport.
 */
export function useScrollReveal() {
  const el = ref<HTMLElement | null>(null);
  const visible = ref(true);
  let observer: IntersectionObserver | undefined;

  onMounted(() => {
    if (typeof window === "undefined" || !("IntersectionObserver" in window) || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      visible.value = true;
      return;
    }
    if (!el.value) return;
    const rect = el.value.getBoundingClientRect();
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
      { threshold: 0.15 },
    );
    observer.observe(el.value);
  });

  onBeforeUnmount(() => observer?.disconnect());

  return { el, visible };
}
