<script setup lang="ts">
import { computed } from "vue";
import type { CancellationFormDocument } from "@wego/api-contract";
import { docMessage, type DocumentLanguage } from "../../utils/documentMessages";
import { docCount, docDate, docInstant, docMoney, docTourName } from "../../utils/documentFormat";
import { erpMessage } from "../../utils/erpLocale";
import DocumentSheet from "./DocumentSheet.vue";

/** Read-only money-to-return form for a cancelled office booking. It records no refund. */
const props = defineProps<{ doc: CancellationFormDocument; lang: DocumentLanguage }>();
const m = (key: Parameters<typeof docMessage>[1], params?: Record<string, string | number>) => docMessage(props.lang, key, params);
const d = computed(() => props.doc.data);
const hours = computed(() => Math.max(0, d.value.hoursBeforeTour));
</script>

<template>
  <DocumentSheet :lang="lang" :title="m('doc.cancel.title')" :stamp="doc.document">
    <p class="doc-note" style="margin: 0">{{ m("doc.cancel.internal") }}</p>

    <section class="doc-section">
      <dl class="doc-grid">
        <div><dt>{{ m("doc.cancel.reference") }}</dt><dd dir="ltr" class="doc-num">{{ d.reference }}</dd></div>
        <div><dt>{{ m("doc.cancel.customer") }}</dt><dd dir="auto">{{ d.customerName }}</dd></div>
        <div><dt>{{ m("doc.cancel.tour") }}</dt><dd dir="auto">{{ docTourName(d, lang) }}</dd></div>
        <div><dt>{{ m("doc.cancel.date") }}</dt><dd>{{ docDate(d.tourDate, lang) }} · {{ erpMessage(lang, `slot.${d.timeSlot}`) }}</dd></div>
        <div><dt>{{ m("doc.cancel.cancelledAt") }}</dt><dd>{{ docInstant(d.cancelledAt, lang) }}</dd></div>
        <div><dt>{{ m("doc.cancel.reason") }}</dt><dd dir="auto">{{ d.cancellationReason }}</dd></div>
      </dl>
    </section>

    <section class="doc-section">
      <h2>{{ m("doc.cancel.collections") }}</h2>
      <table class="doc-table">
        <thead>
          <tr>
            <th scope="col">{{ m("doc.cancel.when") }}</th>
            <th scope="col">{{ m("doc.cancel.method") }}</th>
            <th scope="col">{{ m("doc.cancel.paid") }}</th>
            <th scope="col">{{ m("doc.cancel.settled") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(line, i) in d.collections" :key="i">
            <td>{{ docInstant(line.recordedAt, lang) }}</td>
            <td>{{ erpMessage(lang, `office.collect.method.${line.method}`) }}<strong v-if="line.reversal"> · {{ m("doc.cancel.reversal") }}</strong></td>
            <td class="doc-num">{{ line.reversal ? "−" : "" }}{{ docMoney(line.amountPaid, lang) }}</td>
            <td class="doc-num">{{ line.reversal ? "−" : "" }}{{ docMoney(line.settledEur, lang) }}</td>
          </tr>
          <tr class="doc-total-row">
            <td colspan="3">{{ m("doc.cancel.net") }}</td>
            <td class="doc-num">{{ docMoney(d.collectedNet, lang) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="doc-section">
      <h2>{{ m("doc.cancel.policy") }}</h2>
      <p style="margin: 0">{{ m(`doc.policy.${d.policy}`) }}</p>
      <p style="margin: 1.5mm 0 0">{{ m("doc.cancel.timing", { hours: docCount(hours, lang), percent: docCount(d.refundPercent, lang) }) }}</p>
      <p class="doc-note">{{ m("doc.cancel.basis") }}</p>
      <p class="doc-pay" style="margin-top: 3mm">
        {{ m("doc.cancel.expected") }}: <span class="doc-num">{{ docMoney(d.expectedReturn, lang) }}</span>
      </p>
      <p v-if="d.todayRate && d.expectedReturnEgp" class="doc-note" data-testid="egp-equivalent">
        {{ m("doc.cancel.egp", { rate: d.todayRate, amount: docMoney(d.expectedReturnEgp, lang) }) }}
      </p>
      <p v-else class="doc-note" data-testid="no-rate">{{ m("doc.cancel.noRate") }}</p>
    </section>

    <section class="doc-section">
      <dl class="doc-grid">
        <div><dt>{{ m("doc.cancel.actual") }}</dt><dd><span class="doc-blank" /></dd></div>
        <div><dt>{{ m("doc.cancel.returnMethod") }}</dt><dd><span class="doc-blank" /></dd></div>
      </dl>
    </section>

    <div class="doc-sign-grid">
      <div class="doc-sign">{{ m("doc.cancel.customerSign") }}</div>
      <div class="doc-sign">{{ m("doc.cancel.staffSign") }}</div>
      <div class="doc-sign">{{ m("doc.cancel.managerSign") }}</div>
    </div>
  </DocumentSheet>
</template>
