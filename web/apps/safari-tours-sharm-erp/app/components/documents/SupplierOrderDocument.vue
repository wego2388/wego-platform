<script setup lang="ts">
import { computed } from "vue";
import type { SupplierOrderDocument } from "@wego/api-contract";
import { docMessage, type DocumentLanguage } from "../../utils/documentMessages";
import { docCount, docDate, docTourName } from "../../utils/documentFormat";
import { erpMessage } from "../../utils/erpLocale";
import { COMPANY } from "../../utils/companyProfile";
import DocumentSheet from "./DocumentSheet.vue";

/**
 * Order sent to a supplier for one departure: service, date and slot, guest counts, the staff-written
 * supplier note and the company contact to confirm with. Privacy decision (OPS2-E): no customer name,
 * phone, e-mail or hotel, never the customers' own special requests (free text that can carry phone
 * numbers or health details; they stay on the internal run sheet), and no agreed price (OPS2-F).
 */
const props = defineProps<{ doc: SupplierOrderDocument; lang: DocumentLanguage }>();
const m = (key: Parameters<typeof docMessage>[1], params?: Record<string, string | number>) => docMessage(props.lang, key, params);
const d = computed(() => props.doc.data);
const channel = computed(() => (d.value.supplier.confirmationChannel ? erpMessage(props.lang, `ops.ch.${d.value.supplier.confirmationChannel}`) : "—"));
</script>

<template>
  <DocumentSheet :lang="lang" :title="m('doc.sup.title')" :stamp="doc.document" internal>
    <section class="doc-section">
      <dl class="doc-grid">
        <div class="doc-wide"><dt>{{ m("doc.sup.supplier") }}</dt><dd dir="auto">{{ d.supplier.name }} · <bdi dir="ltr">{{ d.supplier.code }}</bdi></dd></div>
        <div><dt>{{ m("doc.sup.service") }}</dt><dd>{{ erpMessage(lang, `ops.st.${d.supplier.serviceType}`) }}</dd></div>
        <div class="doc-wide"><dt>{{ m("doc.sup.tour") }}</dt><dd dir="auto">{{ docTourName(d, lang) }}</dd></div>
        <div><dt>{{ m("doc.sup.date") }}</dt><dd>{{ docDate(d.date, lang, true) }}</dd></div>
        <div><dt>{{ m("doc.sup.slot") }}</dt><dd>{{ erpMessage(lang, `slot.${d.timeSlot}`) }}</dd></div>
        <div>
          <dt>{{ m("doc.sup.guests") }}</dt>
          <dd class="doc-num">{{ docCount(d.totalGuests, lang) }} <span class="doc-note">({{ m("doc.sup.split", { adults: docCount(d.adults, lang), children: docCount(d.children, lang) }) }})</span></dd>
        </div>
        <div v-if="d.units.length">
          <dt>{{ m("doc.sup.units") }}</dt>
          <dd><span v-for="(u, i) in d.units" :key="u.optionLabel" dir="auto">{{ i ? " · " : "" }}{{ docCount(u.unitCount, lang) }} × {{ u.optionLabel }}</span></dd>
        </div>
      </dl>
    </section>

    <section class="doc-section">
      <h2>{{ m("doc.sup.note") }}</h2>
      <p v-if="!d.supplierNote" class="doc-note">{{ m("doc.sup.noNote") }}</p>
      <p v-else dir="auto">{{ d.supplierNote }}</p>
    </section>

    <section class="doc-section">
      <h2>{{ m("doc.sup.confirm") }}</h2>
      <dl class="doc-grid">
        <div class="doc-wide"><dt>{{ m("doc.sup.confirmTo") }}</dt><dd><span dir="auto">{{ COMPANY.brand[lang] }}</span> · <bdi dir="ltr" class="doc-num">{{ COMPANY.phone }}</bdi></dd></div>
        <div v-if="d.supplier.contactPerson"><dt>{{ m("doc.sup.confirmPerson") }}</dt><dd dir="auto">{{ d.supplier.contactPerson }}</dd></div>
        <div><dt>{{ m("doc.sup.confirmVia") }}</dt><dd>{{ channel }}</dd></div>
        <div v-if="d.supplier.noticeHours !== null"><dt>&nbsp;</dt><dd>{{ m("doc.sup.notice", { hours: docCount(d.supplier.noticeHours, lang) }) }}</dd></div>
      </dl>
      <div class="doc-sign-grid" style="grid-template-columns: 1fr 1fr 1fr">
        <div class="doc-sign" style="grid-column: 1 / span 2">{{ m("doc.sup.confirmLine") }}</div>
      </div>
      <p class="doc-note">{{ m("doc.sup.privacy") }}</p>
    </section>
  </DocumentSheet>
</template>
