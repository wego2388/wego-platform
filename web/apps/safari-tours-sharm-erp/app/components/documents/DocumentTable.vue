<script setup lang="ts">
import { docMessage, type DocumentLanguage } from "../../utils/documentMessages";

defineProps<{ lang: DocumentLanguage; label: string }>();
</script>

<template>
  <p class="doc-table-hint">{{ docMessage(lang, "doc.tableScroll") }}</p>
  <div class="doc-table-scroll" role="region" :aria-label="label" tabindex="0">
    <slot />
  </div>
</template>

<style>
.doc-table-scroll { max-width: 100%; overflow-x: auto; overscroll-behavior-inline: contain; }
.doc-table-scroll:focus-visible { outline: 2px solid var(--doc-accent, #0a2342); outline-offset: 2px; }
.doc-table-hint { display: none; }
@media screen and (max-width: 800px) {
  .doc-table-hint { display: block; margin: 2mm 0; color: var(--doc-muted, #4b5563); font-size: 9pt; }
}
@media print {
  .doc-table-scroll { overflow: visible; max-width: none; }
  .doc-table-hint { display: none; }
}
</style>
