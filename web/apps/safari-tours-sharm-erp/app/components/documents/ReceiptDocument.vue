<script setup lang="ts">
import { computed } from "vue";
import type { ReceiptDocument } from "@wego/api-contract";
import { docMessage, type DocumentLanguage } from "../../utils/documentMessages";
import { docDate, docInstant, docMoney, docTourName } from "../../utils/documentFormat";
import { erpMessage } from "../../utils/erpLocale";
import DocumentSheet from "./DocumentSheet.vue";

/** One office payment. A reversed payment is stamped VOID and says so in words. Card data is never shown. */
const props = defineProps<{ doc: ReceiptDocument; lang: DocumentLanguage }>();
const m = (key: Parameters<typeof docMessage>[1], params?: Record<string, string | number>) => docMessage(props.lang, key, params);
const d = computed(() => props.doc.data);
const reversedNotice = computed(() => d.value.reversal
  ? m("doc.receipt.reversed", { date: docInstant(d.value.reversal.recordedAt, props.lang), ref: d.value.reversal.entryRef })
  : "");
</script>

<template>
  <DocumentSheet
    :lang="lang" :title="m('doc.receipt.title')" :stamp="doc.document"
    :banner="d.reversed ? reversedNotice : undefined"
    :watermark="d.reversed ? m('doc.watermark.void') : undefined"
  >
    <section class="doc-section">
      <dl class="doc-grid">
        <div><dt>{{ m("doc.receipt.booking") }}</dt><dd dir="ltr" class="doc-num">{{ d.bookingReference }}</dd></div>
        <div><dt>{{ m("doc.receipt.customer") }}</dt><dd dir="auto">{{ d.customerName }}</dd></div>
        <div><dt>{{ m("doc.receipt.tour") }}</dt><dd dir="auto">{{ docTourName(d, lang) }}</dd></div>
        <div><dt>{{ m("doc.receipt.date") }}</dt><dd>{{ docDate(d.tourDate, lang) }}</dd></div>
      </dl>
    </section>

    <section class="doc-section">
      <table class="doc-table">
        <tbody>
          <tr><th scope="row">{{ m("doc.receipt.method") }}</th><td>{{ erpMessage(lang, `office.collect.method.${d.method}`) }}</td></tr>
          <tr><th scope="row">{{ m("doc.receipt.paid") }}</th><td class="doc-num"><strong>{{ docMoney(d.amountPaid, lang) }}</strong></td></tr>
          <tr><th scope="row">{{ m("doc.receipt.settled") }}</th><td class="doc-num">{{ docMoney(d.settledEur, lang) }}</td></tr>
          <tr v-if="d.fxRate"><th scope="row">{{ m("doc.receipt.rate") }}</th><td class="doc-num" dir="ltr">{{ m("doc.receipt.rateValue", { rate: d.fxRate }) }}</td></tr>
          <tr v-if="d.referenceMasked">
            <th scope="row">{{ m("doc.receipt.reference") }}</th>
            <td><bdi dir="ltr" class="doc-num">{{ d.referenceMasked }}</bdi> <span class="doc-note">{{ m("doc.receipt.referenceHint") }}</span></td>
          </tr>
          <tr><th scope="row">{{ m("doc.receipt.received") }}</th><td>{{ docInstant(d.recordedAt, lang) }}</td></tr>
          <tr v-if="d.collectedBy"><th scope="row">{{ m("doc.receipt.by") }}</th><td dir="ltr">{{ d.collectedBy }}</td></tr>
        </tbody>
      </table>
    </section>

    <section class="doc-section">
      <table class="doc-table">
        <tbody>
          <tr><th scope="row">{{ m("doc.receipt.total") }}</th><td class="doc-num">{{ docMoney(d.bookingTotal, lang) }}</td></tr>
          <tr><th scope="row">{{ m("doc.receipt.toDate") }}</th><td class="doc-num">{{ docMoney(d.collectedToDate, lang) }}</td></tr>
          <tr class="doc-total-row"><th scope="row">{{ m("doc.receipt.remaining") }}</th><td class="doc-num">{{ docMoney(d.remainingAfter, lang) }}</td></tr>
        </tbody>
      </table>
      <p class="doc-note">{{ m("doc.receipt.noCard") }}</p>
    </section>

    <div class="doc-sign-grid" style="grid-template-columns: 1fr 1fr 1fr">
      <div class="doc-sign" style="grid-column: 3">{{ m("doc.receipt.signature") }}</div>
    </div>
  </DocumentSheet>
</template>
