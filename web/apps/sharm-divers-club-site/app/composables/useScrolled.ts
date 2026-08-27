import { onBeforeUnmount, onMounted, ref } from "vue";

export function useScrolled(thresholdPx = 24) {
  const scrolled = ref(false);

  function onScroll() {
    scrolled.value = window.scrollY > thresholdPx;
  }

  onMounted(() => {
    if (typeof window === "undefined") return;
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
  });

  onBeforeUnmount(() => {
    if (typeof window === "undefined") return;
    window.removeEventListener("scroll", onScroll);
  });

  return { scrolled };
}
