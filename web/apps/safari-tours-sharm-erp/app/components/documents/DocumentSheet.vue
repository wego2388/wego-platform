<script setup lang="ts">
import { computed, defineComponent, h } from "vue";
import type { DocumentStamp } from "@wego/api-contract";
import { COMPANY } from "../../utils/companyProfile";
import { docMessage, type DocumentLanguage } from "../../utils/documentMessages";
import { docDir, docInstant } from "../../utils/documentFormat";

/**
 * One A4 paper: logo and company header, the document's own identity (number,
 * version, COPY mark), an optional banner and watermark, and the company footer.
 * The language and direction belong to the paper, not to the dashboard.
 */
const props = defineProps<{
  lang: DocumentLanguage;
  title: string;
  stamp: DocumentStamp;
  banner?: string;
  watermark?: string;
}>();

/** Emits the @page rule that prints the running footer (company, document number, page x of y) in every page margin. */
const PageRule = defineComponent({
  props: { text: { type: String, required: true }, rtl: { type: Boolean, default: false } },
  setup: (p) => () => h("style", `@page { @bottom-center { content: ${JSON.stringify(p.text)} counter(page) " / " counter(pages); font: 7.5pt sans-serif; color: #4b5563; direction: ${p.rtl ? "rtl" : "ltr"}; unicode-bidi: plaintext; } }`),
});
const runningFooter = computed(() => `${COMPANY.brand[props.lang]} · ${docMessage(props.lang, "doc.taxId")} ${COMPANY.taxId} · ${COMPANY.phone} · ${props.stamp.number} · v${props.stamp.version} · ${docMessage(props.lang, "doc.page")} `);
const m = (key: Parameters<typeof docMessage>[1], params?: Record<string, string | number>) => docMessage(props.lang, key, params);
const dir = computed(() => docDir(props.lang));
const titleId = computed(() => `doc-title-${props.stamp.type}-${props.stamp.number}`.replace(/[^\w-]/g, "-"));
const brand = computed(() => COMPANY.brand[props.lang]);
const copyNotice = computed(() => m("doc.copyNotice", { version: props.stamp.version, date: docInstant(props.stamp.originalPrintedAt, props.lang) }));
const printedLine = computed(() => m("doc.printed", { at: docInstant(props.stamp.printedAt, props.lang), by: props.stamp.printedByEmail ?? "—" }));
</script>

<template>
  <article class="doc-sheet" :lang="lang" :dir="dir" :aria-labelledby="titleId" :data-document-type="stamp.type">
    <PageRule :text="runningFooter" :rtl="lang === 'ar'" />
    <div v-if="watermark" class="doc-watermark" aria-hidden="true"><span>{{ watermark }}</span></div>
        <header class="doc-header">
      <div class="doc-brand">
        <img src="/logo-full.webp" :alt="brand" width="180" height="auto" class="doc-logo" decoding="async">
      </div>
      <div class="doc-identity">
        <h1 :id="titleId" class="doc-title">{{ title }}</h1>
        <dl class="doc-meta">
          <div><dt>{{ m("doc.number") }}</dt><dd dir="ltr" class="doc-number">{{ stamp.number }}</dd></div>
          <div><dt>{{ m("doc.versionLabel") }}</dt><dd>{{ stamp.version }}{{ stamp.copy ? "" : " · " + m("doc.original") }}</dd></div>
        </dl>
        <p v-if="stamp.copy" class="doc-copy" role="note"><strong>{{ copyNotice }}</strong></p>
      </div>
    </header>

    <p v-if="banner" class="doc-banner" role="alert">{{ banner }}</p>

    <div class="doc-body">
      <slot />
    </div>

    <footer class="doc-footer">
      <p class="doc-footer-company">
        <strong>{{ brand }}</strong>
        · {{ m("doc.legalName") }}: <span dir="rtl" lang="ar">{{ COMPANY.legalName }}</span>
        · {{ m("doc.taxId") }}: <bdi dir="ltr">{{ COMPANY.taxId }}</bdi>
      </p>
      <p>
        {{ COMPANY.address[lang] }}
        · {{ m("doc.phone") }}: <bdi dir="ltr">{{ COMPANY.phone }}</bdi>
        · {{ m("doc.email") }}: <bdi dir="ltr">{{ COMPANY.email }}</bdi>
      </p>
      <p>{{ m("doc.support") }}: {{ m("doc.supportValue", { hours: COMPANY.supportHours[lang], languages: COMPANY.supportLanguages[lang] }) }}</p>
      <p class="doc-footer-print">{{ printedLine }} · <bdi dir="ltr">{{ stamp.number }}</bdi> · v{{ stamp.version }}</p>
    </footer>
  </article>
</template>

<style>
/* A4 paper. Screen: a centred sheet. Print: the sheet is the page content. */
.doc-sheet {
  --doc-ink: #111827;
  --doc-muted: #4b5563;
  --doc-line: #d1d5db;
  --doc-accent: #0a2342;
  position: relative;
  box-sizing: border-box;
  width: 210mm;
  max-width: 100%;
  min-height: 297mm;
  margin: 0 auto;
  padding: 14mm 14mm 10mm;
  display: flex;
  flex-direction: column;
  background: #fff;
  color: var(--doc-ink);
  font-size: 10pt;
  line-height: 1.4;
  box-shadow: 0 1px 6px rgb(0 0 0 / 0.18);
  overflow: hidden;
}
.doc-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 8mm; border-bottom: 2px solid var(--doc-accent); padding-bottom: 3mm; }
.doc-logo { height: auto; width: 34mm; max-width: 100%; display: block; }
.doc-identity { text-align: end; min-width: 0; }
.doc-title { margin: 0 0 2mm; font-size: 18pt; line-height: 1.15; color: var(--doc-accent); }
.doc-meta { margin: 0; display: grid; gap: 1mm; font-size: 9.5pt; }
.doc-meta > div { display: flex; justify-content: flex-end; gap: 3mm; }
.doc-meta dt { color: var(--doc-muted); }
.doc-meta dd { margin: 0; font-weight: 600; }
.doc-number { font-variant-numeric: tabular-nums; letter-spacing: 0.02em; }
.doc-copy { margin: 2mm 0 0; padding: 1mm 2.5mm; display: inline-block; border: 1.5px solid #92400e; color: #92400e; font-size: 9pt; }
.doc-banner { margin: 5mm 0 0; padding: 3mm 4mm; border: 2px solid #b3261e; background: #fbdcd9; color: #7f1d1d; font-weight: 700; font-size: 13pt; text-align: center; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.doc-watermark { position: absolute; inset: 0; z-index: 0; overflow: hidden; pointer-events: none; display: flex; align-items: center; justify-content: center; }
.doc-watermark > span {
  font-size: 56pt; font-weight: 800; letter-spacing: 0.06em; color: rgb(179 38 30 / 0.16); transform: rotate(-28deg); text-align: center; white-space: nowrap;
  -webkit-print-color-adjust: exact; print-color-adjust: exact;
}
.doc-body { position: relative; z-index: 1; margin-top: 3mm; margin-bottom: 4mm; }
.doc-section { margin-top: 3mm; break-inside: avoid; }
.doc-section > h2 { margin: 0 0 2mm; font-size: 11.5pt; color: var(--doc-accent); border-bottom: 1px solid var(--doc-line); padding-bottom: 1mm; }
.doc-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 2.5mm 8mm; margin: 0; }
.doc-grid > div { min-width: 0; }
.doc-grid dt { font-size: 8.5pt; color: var(--doc-muted); }
.doc-grid dd { margin: 0; font-weight: 600; overflow-wrap: anywhere; }
.doc-grid .doc-wide { grid-column: 1 / -1; }
.doc-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 0 8mm; align-items: start; }
.doc-cols > * { min-width: 0; }
.doc-list { margin: 0; padding-inline-start: 5mm; }
.doc-list li { margin-bottom: 1mm; }
.doc-table { width: 100%; border-collapse: collapse; font-size: 9.5pt; }
.doc-table th, .doc-table td { border: 1px solid var(--doc-line); padding: 1.5mm 2mm; text-align: start; vertical-align: top; }
.doc-table th { background: #f3f4f6; font-weight: 700; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.doc-table thead { display: table-header-group; }
.doc-table tr { break-inside: avoid; }
.doc-num { font-variant-numeric: tabular-nums; }
.doc-total-row td { font-weight: 700; }
.doc-blank { display: inline-block; min-width: 45mm; border-bottom: 1px solid var(--doc-ink); height: 5mm; }
.doc-sign-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8mm; margin-top: 14mm; }
.doc-sign { border-top: 1px solid var(--doc-ink); padding-top: 1.5mm; font-size: 9pt; text-align: center; break-inside: avoid; }
.doc-note { color: var(--doc-muted); font-size: 9pt; margin: 2mm 0 0; }
.doc-pay { padding: 2.5mm 3mm; border: 1px solid var(--doc-line); border-inline-start: 4px solid var(--doc-accent); font-weight: 600; }
.doc-qr-box { display: flex; gap: 5mm; align-items: center; break-inside: avoid; }
.doc-qr { width: 30mm; height: 30mm; flex: none; border: 1px solid var(--doc-line); }
.doc-tour-block { break-inside: auto; }
.doc-tour-block + .doc-tour-block { break-before: page; }
.doc-footer { position: relative; z-index: 1; margin-top: auto; border-top: 1px solid var(--doc-line); padding-top: 2mm; font-size: 7.5pt; color: var(--doc-muted); }
.doc-footer p { margin: 0 0 0.5mm; }
.doc-footer-print { color: var(--doc-ink); }

/* Print: real A4 page margins. The bottom margin carries a running footer (@page margin box: company,
   number, page x of y); the full company block closes the document. */
@page { size: A4; margin: 10mm 12mm 16mm 12mm; }
@media print {
  html, body { background: #fff !important; margin: 0 !important; }
  .doc-sheet { box-shadow: none; width: auto; min-height: 262mm; margin: 0; padding: 0; overflow: visible; break-after: page; }
  .doc-sheet:last-of-type { break-after: auto; }
}
@media screen and (max-width: 800px) {
  .doc-sheet { padding: 8mm 5mm 36mm; min-height: 0; }
  .doc-header { flex-direction: column; }
  .doc-identity { text-align: start; }
  .doc-meta > div { justify-content: flex-start; }
  .doc-grid, .doc-cols { grid-template-columns: 1fr; }
  .doc-footer { margin-top: 8mm; }
}
</style>
