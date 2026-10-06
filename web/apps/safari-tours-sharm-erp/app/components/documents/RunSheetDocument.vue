<script setup lang="ts">
import { computed } from "vue";
import type { RunSheetDocument } from "@wego/api-contract";
import { docMessage, type DocumentLanguage } from "../../utils/documentMessages";
import { docCount, docDate, docTourName } from "../../utils/documentFormat";
import { erpMessage } from "../../utils/erpLocale";
import DocumentSheet from "./DocumentSheet.vue";

/** Daily run sheet: minimal personal data (name, hotel, room, guests) and no phone numbers. */
const props = defineProps<{ doc: RunSheetDocument; lang: DocumentLanguage }>();
const m = (key: Parameters<typeof docMessage>[1], params?: Record<string, string | number>) => docMessage(props.lang, key, params);
const d = computed(() => props.doc.data);
</script>

<template>
  <DocumentSheet :lang="lang" :title="m('doc.run.title')" :stamp="doc.document">
    <section class="doc-section">
      <dl class="doc-grid">
        <div><dt>{{ m("doc.run.date") }}</dt><dd>{{ docDate(d.date, lang, true) }}</dd></div>
        <div><dt>{{ m("doc.run.totalGuests") }}</dt><dd class="doc-num">{{ docCount(d.totalGuests, lang) }}</dd></div>
      </dl>
      <p class="doc-note">{{ m("doc.run.phones") }}</p>
    </section>

    <p v-if="d.tours.length === 0" class="doc-note">{{ m("doc.run.empty") }}</p>

    <section v-for="tour in d.tours" :key="tour.tourId" class="doc-tour-block doc-section" :aria-label="docTourName(tour, lang)">
      <h2 dir="auto">{{ docTourName(tour, lang) }} · {{ m("doc.run.tourGuests", { count: docCount(tour.guests, lang) }) }}</h2>
      <div v-for="dep in tour.departures" :key="dep.timeSlot" style="margin-bottom: 4mm">
        <h3 style="margin: 2mm 0 1mm; font-size: 10.5pt">
          {{ erpMessage(lang, `slot.${dep.timeSlot}`) }} · {{ m("doc.run.tourGuests", { count: docCount(dep.guests, lang) }) }}
        </h3>
        <p style="margin: 0 0 1.5mm">
          <strong>{{ m("doc.run.hotels") }}:</strong>
          <span v-for="(h, i) in dep.hotels" :key="h.hotelName" dir="auto">{{ i ? " · " : " " }}{{ m("doc.run.hotelGuests", { hotel: h.hotelName, count: docCount(h.guests, lang) }) }}</span>
        </p>
        <table class="doc-table">
          <thead>
            <tr>
              <th scope="col">{{ m("doc.run.ref") }}</th>
              <th scope="col">{{ m("doc.run.lead") }}</th>
              <th scope="col">{{ m("doc.run.hotel") }}</th>
              <th scope="col">{{ m("doc.run.room") }}</th>
              <th scope="col">{{ m("doc.run.guests") }}</th>
              <th scope="col">{{ m("doc.run.due") }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in dep.lines" :key="line.reference">
              <td dir="ltr" class="doc-num">{{ line.reference }}</td>
              <td dir="auto">{{ line.leadName }}</td>
              <td dir="auto">{{ line.hotelName }}</td>
              <td dir="ltr">{{ line.hotelRoom ?? "—" }}</td>
              <td class="doc-num">{{ docCount(line.guests, lang) }}</td>
              <td>{{ line.paymentDue ? m("doc.run.dueYes") : "" }}</td>
            </tr>
          </tbody>
        </table>
        <p style="margin: 1.5mm 0 0"><strong>{{ m("doc.run.notes") }}:</strong>
          <span v-if="dep.notes.length === 0"> {{ m("doc.run.noNotes") }}</span>
        </p>
        <ul v-if="dep.notes.length" class="doc-list">
          <li v-for="note in dep.notes" :key="note.reference"><bdi dir="ltr" class="doc-num">{{ note.reference }}</bdi>: <span dir="auto">{{ note.text }}</span></li>
        </ul>
      </div>
    </section>
  </DocumentSheet>
</template>
