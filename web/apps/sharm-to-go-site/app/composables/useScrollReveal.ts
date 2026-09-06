// Local Vue directive: reveals an element with a rise+fade the first time
// it scrolls into view, then stops observing — a real intersection check,
// not a fixed-delay CSS animation, so content already in view on load (no
// JS, slow connections) never gets stuck invisible. Pair with the
// `.sharm-reveal` class (app/assets/css/main.css), which also defines the
// `prefers-reduced-motion` override that skips straight to visible.
export const vReveal = {
  mounted(el: HTMLElement) {
    if (typeof IntersectionObserver === "undefined") {
      el.classList.add("is-visible");
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            el.classList.add("is-visible");
            observer.unobserve(el);
          }
        }
      },
      { threshold: 0.15 },
    );
    observer.observe(el);
  },
};
