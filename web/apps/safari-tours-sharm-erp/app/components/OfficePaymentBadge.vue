<script setup lang="ts">
import { computed } from "vue";
import { WegoBadge } from "@wego/ui";
import type { Booking } from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import { officeBadge } from "../utils/officeBooking";

const props = defineProps<{ booking: Pick<Booking, "channel" | "status" | "officePayment"> }>();
const { t, money } = useErpLocale();
const badge = computed(() => officeBadge(props.booking));
</script>

<template>
  <span v-if="badge" class="office-payment inline-flex flex-wrap items-center gap-x-2 gap-y-1">
    <WegoBadge :tone="badge.tone">{{ t(badge.labelKey) }}</WegoBadge>
    <span v-if="badge.outstanding" class="text-xs font-semibold tabular-nums">{{ t("office.outstanding", { amount: money(badge.outstanding) }) }}</span>
    <span v-else-if="badge.cashToReturn" class="text-xs font-semibold tabular-nums">{{ t("office.cashToReturn", { amount: money(badge.cashToReturn) }) }}</span>
  </span>
</template>
