<script setup lang="ts">
import { computed } from "vue";
import type { DriverSheetDocument } from "@wego/api-contract";
import { docMessage, type DocumentLanguage } from "../../utils/documentMessages";
import { docCount, docDate, docTourName } from "../../utils/documentFormat";
import { erpMessage } from "../../utils/erpLocale";
import { COMPANY } from "../../utils/companyProfile";
import DocumentSheet from "./DocumentSheet.vue";

/**
 * The driver's sheet of one departure: route in pickup order with customer name, hotel and room.
 * Privacy decision (OPS2-E): no customer phone, e-mail, price or payment state; the driver calls the
 * operations contact (company phone) instead of the guest.
 */
const props = defineProps<{ doc: DriverSheetDocument; lang: DocumentLanguage }>();
const m = (key: Parameters<typeof docMessage>[1], params?: Record<string, string | number>) => docMessage(props.lang, key, params);
const d = computed(() => props.doc.data);
</script>

<template>
  <DocumentSheet :lang="lang" :title="m('doc.drv.title')" :stamp="doc.document" internal>
    <section class="doc-section">
      <dl class="doc-grid">
        <div class="doc-wide"><dt>{{ m("doc.drv.tour") }}</dt><dd dir="auto">{{ docTourName(d, lang) }}</dd></div>
        <div><dt>{{ m("doc.drv.date") }}</dt><dd>{{ docDate(d.date, lang, true) }}</dd></div>
        <div><dt>{{ m("doc.drv.slot") }}</dt><dd>{{ erpMessage(lang, `slot.${d.timeSlot}`) }}</dd></div>
        <div><dt>{{ m("doc.drv.driver") }}</dt><dd dir="auto">{{ d.driverName }}</dd></div>
        <div>
          <dt>{{ m("doc.drv.vehicle") }}</dt>
          <dd><span v-if="d.vehicle" dir="auto">{{ d.vehicle.display }} · {{ m("doc.drv.seats", { seats: docCount(d.vehicle.seats, lang) }) }}</span><span v-else class="doc-blank" /></dd>
        </div>
        <div><dt>{{ m("doc.drv.totalGuests") }}</dt><dd class="doc-num">{{ docCount(d.totalGuests, lang) }}</dd></div>
        <div><dt>{{ m("doc.drv.contact") }}</dt><dd dir="ltr" class="doc-num">{{ COMPANY.phone }}</dd></div>
      </dl>
      <p class="doc-note">{{ m("doc.drv.contactNote") }}</p>
    </section>

    <section class="doc-section" style="break-inside: auto">
    <h2>{{ m("doc.drv.route") }}</h2>
    <p v-if="d.stops.length === 0" class="doc-note">{{ m("doc.drv.empty") }}</p>
    <section v-for="stop in d.stops" :key="stop.order" style="break-inside: avoid" :aria-label="stop.hotelName">
      <h3 style="margin: 2mm 0 1mm; font-size: 10.5pt">
        {{ m("doc.drv.stop", { n: docCount(stop.order, lang) }) }} · <span dir="auto">{{ stop.hotelName }}</span> · {{ m("doc.drv.hotelGuests", { count: docCount(stop.guests, lang) }) }}
      </h3>
      <table class="doc-table">
        <thead>
          <tr>
            <th scope="col">{{ m("doc.drv.lead") }}</th>
            <th scope="col">{{ m("doc.drv.room") }}</th>
            <th scope="col">{{ m("doc.drv.guests") }}</th>
            <th scope="col">{{ m("doc.drv.ref") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="party in stop.parties" :key="party.reference">
            <td dir="auto">{{ party.leadName }}</td>
            <td dir="ltr">{{ party.hotelRoom ?? "—" }}</td>
            <td class="doc-num">{{ docCount(party.guests, lang) }}</td>
            <td dir="ltr" class="doc-num">{{ party.reference }}</td>
          </tr>
        </tbody>
      </table>
    </section>
    </section>

    <div class="doc-sign-grid" style="grid-template-columns: 1fr 1fr 1fr">
      <div class="doc-sign" style="grid-column: 3">{{ m("doc.drv.signature") }}</div>
    </div>
  </DocumentSheet>
</template>
