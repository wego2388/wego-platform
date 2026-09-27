import { onMounted, onBeforeUnmount, ref } from "vue";

export function useScrolled(threshold = 60) {
  const scrolled = ref(false);

  function onScroll() {
    scrolled.value = window.scrollY > threshold;
  }

  onMounted(() => {
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
  });
  onBeforeUnmount(() => window.removeEventListener("scroll", onScroll));

  return { scrolled };
}
