<script setup lang="ts">
import { AccordionContent, AccordionHeader, AccordionItem, AccordionRoot, AccordionTrigger } from "reka-ui";
defineProps<{ items: { value: string; title: string; body: string }[] }>();
</script>

<template>
  <AccordionRoot type="multiple" class="divide-y divide-sts-border rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface">
    <AccordionItem v-for="item in items" :key="item.value" :value="item.value">
      <AccordionHeader>
        <AccordionTrigger class="group flex min-h-13 w-full items-center justify-between gap-4 px-5 text-start text-[0.95rem] font-semibold text-sts-ink">
          {{ item.title }}
          <Icon name="lucide:chevron-down" class="size-5 shrink-0 text-sts-muted transition-transform duration-[var(--sts-dur-base)] group-data-[state=open]:rotate-180" aria-hidden="true" />
        </AccordionTrigger>
      </AccordionHeader>
      <AccordionContent class="sts-accordion overflow-hidden text-sm text-sts-muted">
        <p class="px-5 pb-4">{{ item.body }}</p>
      </AccordionContent>
    </AccordionItem>
  </AccordionRoot>
</template>

<style scoped>
.sts-accordion[data-state="open"] { animation: sts-open var(--sts-dur-base) var(--sts-ease); }
.sts-accordion[data-state="closed"] { animation: sts-close var(--sts-dur-quick) var(--sts-ease); }
@keyframes sts-open { from { height: 0; } to { height: var(--reka-accordion-content-height); } }
@keyframes sts-close { from { height: var(--reka-accordion-content-height); } to { height: 0; } }
</style>
