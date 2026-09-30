<script setup lang="ts">
import { TabsContent, TabsIndicator, TabsList, TabsRoot, TabsTrigger } from "reka-ui";

const model = defineModel<string>();
defineProps<{ tabs: { value: string; label: string }[]; label: string }>();
</script>

<template>
  <TabsRoot v-model="model" :default-value="tabs[0]?.value">
    <TabsList :aria-label="label" class="relative flex gap-1 overflow-x-auto border-b border-sts-border">
      <TabsTrigger
        v-for="tab in tabs"
        :key="tab.value"
        :value="tab.value"
        class="min-h-11 whitespace-nowrap px-4 text-sm font-semibold text-sts-muted data-[state=active]:text-sts-ocean-bright"
      >
        {{ tab.label }}
      </TabsTrigger>
      <TabsIndicator class="absolute bottom-0 h-0.5 w-[var(--reka-tabs-indicator-size)] translate-x-[var(--reka-tabs-indicator-position)] rounded-full bg-sts-sunset transition-[width,transform] duration-[var(--sts-dur-base)]" />
    </TabsList>
    <TabsContent v-for="tab in tabs" :key="tab.value" :value="tab.value" class="pt-5">
      <slot :name="tab.value" />
    </TabsContent>
  </TabsRoot>
</template>
