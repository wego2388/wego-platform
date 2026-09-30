<template>
  <div class="sts-skeleton rounded-[var(--sts-radius-control)] bg-sts-border/60" aria-hidden="true" />
</template>

<style scoped>
.sts-skeleton {
  background-image: linear-gradient(90deg, transparent, rgb(255 255 255 / 0.35), transparent);
  background-size: 200% 100%;
  animation: sts-shimmer 1.4s linear infinite;
}
@keyframes sts-shimmer {
  from { background-position: 200% 0; }
  to { background-position: -200% 0; }
}
@media (prefers-reduced-motion: reduce) {
  .sts-skeleton { animation: none; }
}
</style>
