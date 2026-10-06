<script setup lang="ts">
import { computed } from "vue";
import type { PickupManifestDocument } from "@wego/api-contract";
import { docMessage, type DocumentLanguage } from "../../utils/documentMessages";
import { docCount, docDate, docTourName } from "../../utils/documentFormat";
import { erpMessage } from "../../utils/erpLocale";
import DocumentSheet from "./DocumentSheet.vue";

/** Pickup manifest of one departure. It carries phone numbers; driver and vehicle stay blank until OPS2-E. */
const props = defineProps<{ doc: PickupManifestDocument; lang: DocumentLanguage }>();
const m = (key: Parameters<typeof docMessage>[1], params?: Record<string, string | number>) => docMessage(props.lang, key, params);
const d = computed(() => props.doc.data);
</script>

<template>
  <DocumentSheet :lang="lang" :title="m('doc.pickup.title')" :stamp="doc.document">
    <section class="doc-section">
      <dl class="doc-grid">
        <div class="doc-wide"><dt>{{ m("doc.pickup.tour") }}</dt><dd dir="auto">{{ docTourName(d, lang) }}</dd></div>
        <div><dt>{{ m("doc.pickup.date") }}</dt><dd>{{ docDate(d.date, lang, true) }}</dd></div>
        <div><dt>{{ m("doc.pickup.slot") }}</dt><dd>{{ erpMessage(lang, `slot.${d.timeSlot}`) }}</dd></div>
        <div><dt>{{ m("doc.pickup.totalGuests") }}</dt><dd class="doc-num">{{ docCount(d.totalGuests, lang) }}</dd></div>
        <div><dt>{{ m("doc.pickup.driver") }}</dt><dd><span v-if="d.driver" dir="auto">{{ d.driver }}</span><span v-else class="doc-blank" /></dd></div>
        <div><dt>{{ m("doc.pickup.vehicle") }}</dt><dd><span v-if="d.vehicle" dir="auto">{{ d.vehicle }}</span><span v-else class="doc-blank" /></dd></div>
      </dl>
      <p class="doc-note">{{ m("doc.pickup.privacy") }}</p>
    </section>

    <p v-if="d.lines.length === 0" class="doc-note">{{ m("doc.pickup.empty") }}</p>
    <table v-else class="doc-table">
      <thead>
        <tr>
          <th scope="col">{{ m("doc.pickup.order") }}</th>
          <th scope="col">{{ m("doc.pickup.hotel") }}</th>
          <th scope="col">{{ m("doc.pickup.room") }}</th>
          <th scope="col">{{ m("doc.pickup.lead") }}</th>
          <th scope="col">{{ m("doc.pickup.phone") }}</th>
          <th scope="col">{{ m("doc.pickup.guests") }}</th>
          <th scope="col">{{ m("doc.pickup.ref") }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="line in d.lines" :key="line.reference">
          <td class="doc-num">{{ docCount(line.order, lang) }}</td>
          <td dir="auto">{{ line.hotelName }}</td>
          <td dir="ltr">{{ line.hotelRoom ?? "—" }}</td>
          <td dir="auto">{{ line.leadName }}</td>
          <td dir="ltr" class="doc-num">{{ line.phone }}</td>
          <td class="doc-num">{{ docCount(line.guests, lang) }}</td>
          <td dir="ltr" class="doc-num">{{ line.reference }}</td>
        </tr>
      </tbody>
    </table>

    <div class="doc-sign-grid" style="grid-template-columns: 1fr 1fr 1fr">
      <div class="doc-sign" style="grid-column: 3">{{ m("doc.pickup.signature") }}</div>
    </div>
  </DocumentSheet>
</template>
