<script setup lang="ts">
import { computed } from "vue";
import type { SettlementStatementDocument } from "@wego/api-contract";
import { docMessage, type DocumentLanguage, type DocumentMessageKey } from "../../utils/documentMessages";
import { docCount, docDate } from "../../utils/documentFormat";
import { erpMessage, formatErpSignedMoney } from "../../utils/erpLocale";
import DocumentSheet from "./DocumentSheet.vue";

/**
 * Supplier / driver settlement statement (OPS2-F): opening, owed, paid and closing per currency,
 * then every movement of the period. No customer data: departures show tour, window and guests.
 */
const props = defineProps<{ doc: SettlementStatementDocument; lang: DocumentLanguage }>();
const m = (key: DocumentMessageKey, params?: Record<string, string | number>) => docMessage(props.lang, key, params);
const d = computed(() => props.doc.data);
const amount = (a: { amount: string; currencyCode: string }) => formatErpSignedMoney(a, props.lang);
const tourName = (l: { tourNameEn: string | null; tourNameAr: string | null }) => (props.lang === "ar" && l.tourNameAr ? l.tourNameAr : l.tourNameEn ?? "");
</script>

<template>
  <DocumentSheet :lang="lang" :title="m('doc.stl.title')" :stamp="doc.document" internal>
    <section class="doc-section">
      <dl class="doc-grid">
        <div class="doc-wide"><dt>{{ m("doc.stl.party") }}</dt><dd dir="auto">{{ m(`doc.stl.${d.partyType}`) }} · {{ d.partyName }} <bdi v-if="d.partyCode" dir="ltr">{{ d.partyCode }}</bdi></dd></div>
        <div class="doc-wide"><dt>{{ m("doc.stl.period") }}</dt><dd>{{ m("doc.stl.periodValue", { from: docDate(d.from, lang), to: docDate(d.to, lang) }) }}</dd></div>
      </dl>
    </section>

    <section class="doc-section">
      <h2>{{ m("doc.stl.summary") }}</h2>
      <table class="doc-table">
        <thead>
          <tr>
            <th scope="col">{{ m("doc.stl.currency") }}</th>
            <th scope="col">{{ m("doc.stl.opening") }}</th>
            <th scope="col">{{ m("doc.stl.owed") }}</th>
            <th scope="col">{{ m("doc.stl.paid") }}</th>
            <th scope="col">{{ m("doc.stl.closing") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in d.balances" :key="b.currency">
            <td>{{ b.currency }}</td>
            <td class="doc-num">{{ amount(b.opening) }}</td>
            <td class="doc-num">{{ amount(b.owed) }}</td>
            <td class="doc-num">{{ amount(b.paid) }}</td>
            <td class="doc-num"><strong>{{ amount(b.closing) }}</strong></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="doc-section">
      <h2>{{ m("doc.stl.lines") }}</h2>
      <p v-if="!d.lines.length" class="doc-note">{{ m("doc.stl.none") }}</p>
      <table v-else class="doc-table">
        <thead>
          <tr>
            <th scope="col">{{ m("doc.stl.date") }}</th>
            <th scope="col">{{ m("doc.stl.detail") }}</th>
            <th scope="col">{{ m("doc.stl.amount") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(l, i) in d.lines" :key="i">
            <td>{{ docDate(l.date, lang) }}</td>
            <td dir="auto">
              {{ m(`doc.stl.k.${l.kind}`) }}
              <template v-if="l.kind === 'DEPARTURE'"> · {{ tourName(l) }} · {{ l.timeSlot ? erpMessage(lang, `slot.${l.timeSlot}`) : "" }} · {{ m("doc.stl.guests", { count: docCount(l.guests ?? 0, lang) }) }}</template>
              <template v-if="l.method"> · {{ erpMessage(lang, `fops.sm.${l.method}` as Parameters<typeof erpMessage>[1]) }}</template>
              <bdi v-if="l.reference" dir="ltr"> · {{ l.reference }}</bdi>
              <span v-if="l.text" class="doc-note"> · {{ l.text }}</span>
            </td>
            <td class="doc-num">{{ amount(l.amount) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="d.openIssues" class="doc-note" data-testid="statement-issues">{{ m("doc.stl.issues", { count: docCount(d.openIssues, lang) }) }}</p>
      <p class="doc-note">{{ m("doc.stl.note") }}</p>
    </section>

    <div class="doc-sign-grid">
      <div class="doc-sign">{{ m("doc.stl.partySign") }}</div>
      <div class="doc-sign">{{ m("doc.stl.managerSign") }}</div>
    </div>
  </DocumentSheet>
</template>
