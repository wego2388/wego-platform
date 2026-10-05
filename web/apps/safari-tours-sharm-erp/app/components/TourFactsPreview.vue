<script setup lang="ts">
import type { TourFactsDocument } from "@wego/api-contract";
import { contentMessage } from "../utils/contentMessages";
import type { ErpLocale } from "../utils/erpLocale";
const props = defineProps<{ document: TourFactsDocument; staffLocale: ErpLocale }>();
const label = (key: Parameters<typeof contentMessage>[1]) => contentMessage(props.staffLocale, key);
</script>

<template>
  <div class="space-y-4 text-sm">
    <dl class="grid gap-3 sm:grid-cols-2">
      <div><dt class="text-sts-muted">{{ label('children') }}</dt><dd class="font-medium">{{ label(document.childrenAllowed == null ? 'unknown' : document.childrenAllowed ? 'yes' : 'no') }}</dd></div>
      <div><dt class="text-sts-muted">{{ label('minimumAge') }}</dt><dd class="font-medium">{{ document.minimumAge ?? label('unknown') }}</dd></div>
      <div><dt class="text-sts-muted">{{ label('pickup') }}</dt><dd class="font-medium">{{ label(document.hotelPickup === 'INCLUDED' ? 'pickupIncluded' : document.hotelPickup === 'NOT_INCLUDED' ? 'pickupExcluded' : document.hotelPickup === 'SOME_AREAS' ? 'pickupSome' : 'unknown') }}</dd></div>
      <div><dt class="text-sts-muted">{{ label('languages') }}</dt><dd dir="ltr" class="font-medium">{{ document.guideLanguages?.join(', ') || label('unknown') }}</dd></div>
    </dl>
    <ol v-if="document.stops?.length" class="space-y-2">
      <li v-for="stop in document.stops" :key="stop.key" class="rounded-lg border border-sts-border p-3">
        <div class="font-medium">{{ stop.key }} · {{ label(stop.kind === 'MEETING_POINT' ? 'meetingStop' : 'stop') }}</div>
        <div dir="ltr" class="mt-1 break-all font-mono">{{ stop.latitude }}, {{ stop.longitude }}</div>
      </li>
    </ol>
  </div>
</template>
