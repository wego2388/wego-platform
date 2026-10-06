<script setup lang="ts">
import { computed } from "vue";
import type { VoucherDocument } from "@wego/api-contract";
import { COMPANY } from "../../utils/companyProfile";
import { docMessage, type DocumentLanguage } from "../../utils/documentMessages";
import { docCount, docDate, docMoney, docTourName } from "../../utils/documentFormat";
import { erpMessage } from "../../utils/erpLocale";
import DocumentSheet from "./DocumentSheet.vue";
import QrCode from "./QrCode.vue";

/** Customer booking voucher. A cancelled booking prints a banner and watermark and never a QR. */
const props = defineProps<{ doc: VoucherDocument; lang: DocumentLanguage }>();
const m = (key: Parameters<typeof docMessage>[1], params?: Record<string, string | number>) => docMessage(props.lang, key, params);
const d = computed(() => props.doc.data);
const guests = computed(() => m("doc.guestsValue", { adults: docCount(d.value.adultsCount, props.lang), children: docCount(d.value.childrenCount, props.lang) }));
const cancelled = computed(() => !d.value.valid);
const payText = computed(() => {
  const p = d.value.payment;
  if (p.kind === "OFFICE") return m(`doc.pay.${p.state as "UNPAID" | "PARTIALLY_PAID" | "PAID"}`);
  if (p.state === "PAID" || p.state === "PENDING" || p.state === "REFUNDED") return m(`doc.pay.online.${p.state}`);
  return m("doc.pay.online.other");
});
const contentFallback = computed(() => d.value.contentLanguage !== props.lang && (d.value.includes.length || d.value.excludes.length || d.value.knowBeforeYouGo.length || d.value.meetingPoint));
</script>

<template>
  <DocumentSheet
    :lang="lang" :title="m('doc.voucher.title')" :stamp="doc.document"
    :banner="cancelled ? m('doc.banner.cancelled') : undefined"
    :watermark="cancelled ? m('doc.watermark.cancelled') : undefined"
  >
    <section class="doc-section" :aria-label="m('doc.voucher.reference')">
      <dl class="doc-grid">
        <div><dt>{{ m("doc.voucher.reference") }}</dt><dd dir="ltr" class="doc-num">{{ d.reference }}</dd></div>
        <div><dt>{{ m("doc.voucher.lead") }}</dt><dd dir="auto">{{ d.customerName }}</dd></div>
        <div class="doc-wide"><dt>{{ m("doc.voucher.tour") }}</dt><dd dir="auto">{{ docTourName(d, lang) }}</dd></div>
        <div><dt>{{ m("doc.voucher.date") }}</dt><dd>{{ docDate(d.tourDate, lang, true) }}</dd></div>
        <div><dt>{{ m("doc.voucher.slot") }}</dt><dd>{{ erpMessage(lang, `slot.${d.timeSlot}`) }}</dd></div>
        <div>
          <dt>{{ m("doc.voucher.guests") }}</dt>
          <dd>{{ guests }}<span v-if="d.unit"> · {{ m("doc.units", { count: docCount(d.unit.unitCount, lang), label: d.unit.optionLabel }) }}</span></dd>
        </div>
        <div>
          <dt>{{ m("doc.voucher.hotel") }}</dt>
          <dd dir="auto">{{ d.hotelName }}<span v-if="d.hotelRoom"> · {{ m("doc.voucher.room") }} <bdi dir="ltr">{{ d.hotelRoom }}</bdi></span></dd>
        </div>
      </dl>
    </section>

    <section class="doc-section" :aria-label="m('doc.voucher.payment')">
      <h2>{{ m("doc.voucher.payment") }}</h2>
      <p class="doc-pay">{{ payText }}</p>
      <dl class="doc-grid" style="margin-top: 2.5mm">
        <div><dt>{{ m("doc.voucher.total") }}</dt><dd class="doc-num">{{ docMoney(d.totalPrice, lang) }}</dd></div>
        <template v-if="d.payment.kind === 'OFFICE' && d.payment.collected && d.payment.outstanding">
          <div><dt>{{ m("doc.voucher.collected") }}</dt><dd class="doc-num">{{ docMoney(d.payment.collected, lang) }}</dd></div>
          <div><dt>{{ m("doc.voucher.balance") }}</dt><dd class="doc-num">{{ docMoney(d.payment.outstanding, lang) }}</dd></div>
        </template>
      </dl>
    </section>

    <template v-if="!cancelled">
      <div class="doc-cols">
        <div>
          <section v-if="d.includes.length" class="doc-section">
            <h2>{{ m("doc.voucher.included") }}</h2>
            <ul class="doc-list"><li v-for="item in d.includes" :key="item" dir="auto">{{ item }}</li></ul>
          </section>
          <section v-if="d.excludes.length" class="doc-section">
            <h2>{{ m("doc.voucher.excluded") }}</h2>
            <ul class="doc-list"><li v-for="item in d.excludes" :key="item" dir="auto">{{ item }}</li></ul>
          </section>
        </div>
        <div>
          <section v-if="d.meetingPoint" class="doc-section">
            <h2>{{ m("doc.voucher.meeting") }}</h2>
            <p dir="auto" style="margin: 0">{{ d.meetingPoint }}</p>
          </section>
          <section v-if="d.knowBeforeYouGo.length" class="doc-section">
            <h2>{{ m("doc.voucher.know") }}</h2>
            <ul class="doc-list"><li v-for="item in d.knowBeforeYouGo" :key="item" dir="auto">{{ item }}</li></ul>
          </section>
        </div>
      </div>
      <p v-if="contentFallback" class="doc-note">{{ m("doc.voucher.contentFallback") }}</p>

      <div class="doc-cols">
        <section class="doc-section">
          <h2>{{ m("doc.voucher.instructions") }}</h2>
          <ul class="doc-list">
            <li>{{ m("doc.voucher.instruction1") }}</li>
            <li>{{ m("doc.voucher.instruction2") }}</li>
            <li>{{ m("doc.voucher.instruction3") }}</li>
            <li>{{ m("doc.voucher.instruction4") }}</li>
          </ul>
        </section>
        <section v-if="d.myBookingUrl" class="doc-section doc-qr-box">
          <QrCode :value="d.myBookingUrl" :label="m('doc.voucher.qrLabel')" />
          <div>
            <strong>{{ m("doc.voucher.qr") }}</strong>
            <p class="doc-note" style="margin-top: 1mm">{{ m("doc.voucher.qrHelp") }}</p>
            <p class="doc-note" dir="ltr" style="margin-top: 1mm; overflow-wrap: anywhere">{{ d.myBookingUrl }}</p>
          </div>
        </section>
      </div>
    </template>

    <p class="doc-note" style="margin-top: 3mm">{{ m("doc.voucher.contact") }}: <bdi dir="ltr">{{ COMPANY.phone }}</bdi> · <bdi dir="ltr">{{ COMPANY.email }}</bdi></p>
  </DocumentSheet>
</template>
