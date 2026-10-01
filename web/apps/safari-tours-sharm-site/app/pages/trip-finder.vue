<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useCatalog } from "../composables/useCatalog";
import { useDiscoveryCopy } from "../composables/useDiscoveryCopy";
import { useSiteLocale } from "../composables/useSiteLocale";
import { infoCopy } from "../content/info";
import { whatsappUrl } from "../content/locales";
import { CATEGORY_VISUAL } from "../utils/categoryVisual";
import {
  FINDER_BUDGET,
  FINDER_LIKES,
  FINDER_TIME,
  FINDER_WHO,
  answersFromQuery,
  answersToQuery,
  suggestTours,
  type FinderAnswers,
} from "../utils/tripFinder";

/** "Help me choose": answers live in the URL, so a result can be shared. */
const route = useRoute();
const router = useRouter();
const locale = useSiteLocale();
const copy = computed(() => infoCopy[locale.value].finder);
const { data: catalog, status, refresh } = useCatalog();

const fromUrl = computed(() => answersFromQuery(route.query));
const draft = ref<FinderAnswers>({ ...fromUrl.value });
watch(fromUrl, (value) => (draft.value = { ...value }));
const submitted = computed(() => route.query.go === "1");
const discovery = useDiscoveryCopy();
const suggestions = computed(() => (submitted.value ? suggestTours(catalog.value ?? [], fromUrl.value) : []));

function submit() {
  void router.replace({ query: { ...answersToQuery(draft.value), go: "1" } });
}
function reset() {
  draft.value = { like: null, who: null, time: "any", budget: "any" };
  void router.replace({ query: {} });
}

useSeoMeta({ title: () => `${copy.value.title} — Safari Tours Sharm`, description: () => copy.value.intro });

const chip =
  "inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-full border px-4 text-sm font-semibold transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-sts-ocean-bright";
const on = "border-sts-ocean bg-sts-ocean text-white";
const off = "border-sts-border bg-sts-surface hover:border-sts-ocean-bright";
</script>

<template>
  <main id="main-content" tabindex="-1">
    <div class="bg-sts-ocean px-4 pt-12 pb-10 text-white sm:px-6 lg:px-10">
      <div class="mx-auto max-w-4xl">
        <h1 class="font-display text-3xl font-semibold sm:text-4xl">{{ copy.title }}</h1>
        <p class="mt-4 max-w-2xl text-lg text-white/85">{{ copy.intro }}</p>
      </div>
    </div>

    <div class="mx-auto max-w-4xl px-4 py-10 sm:px-6">
      <form class="grid gap-7" @submit.prevent="submit">
        <fieldset>
          <legend class="mb-3 font-semibold">{{ copy.like }}</legend>
          <div class="flex flex-wrap gap-2">
            <label v-for="like in FINDER_LIKES" :key="like" :class="[chip, draft.like === like ? on : off]">
              <input v-model="draft.like" type="radio" name="like" :value="like" class="sr-only">
              <Icon :name="CATEGORY_VISUAL[like].icon" class="size-4" aria-hidden="true" />{{ copy.likes[like] }}
            </label>
          </div>
        </fieldset>
        <fieldset>
          <legend class="mb-3 font-semibold">{{ copy.who }}</legend>
          <div class="flex flex-wrap gap-2">
            <label v-for="who in FINDER_WHO" :key="who" :class="[chip, draft.who === who ? on : off]">
              <input v-model="draft.who" type="radio" name="who" :value="who" class="sr-only">{{ copy.whos[who] }}
            </label>
          </div>
        </fieldset>
        <div class="grid gap-7 sm:grid-cols-2">
          <fieldset>
            <legend class="mb-3 font-semibold">{{ copy.time }}</legend>
            <div class="flex flex-wrap gap-2">
              <label v-for="time in FINDER_TIME" :key="time" :class="[chip, draft.time === time ? on : off]">
                <input v-model="draft.time" type="radio" name="time" :value="time" class="sr-only">{{ copy.times[time] }}
              </label>
            </div>
          </fieldset>
          <fieldset>
            <legend class="mb-3 font-semibold">{{ copy.budget }}</legend>
            <div class="flex flex-wrap gap-2">
              <label v-for="budget in FINDER_BUDGET" :key="budget" :class="[chip, draft.budget === budget ? on : off]">
                <input v-model="draft.budget" type="radio" name="budget" :value="budget" class="sr-only">{{ copy.budgets[budget] }}
              </label>
            </div>
          </fieldset>
        </div>
        <div class="flex flex-wrap gap-3">
          <UiButton type="submit" size="lg" icon="lucide:sparkles">{{ copy.submit }}</UiButton>
          <UiButton v-if="submitted" variant="ghost" size="lg" @click="reset">{{ copy.reset }}</UiButton>
        </div>
      </form>

      <section class="mt-12" aria-labelledby="results-heading">
        <p class="sr-only" aria-live="polite">{{ submitted && status !== 'pending' ? discovery.tours.results(suggestions.length) : '' }}</p>
        <template v-if="submitted">
          <h2 id="results-heading" class="font-display text-2xl font-semibold">{{ copy.results }}</h2>
          <div v-if="status === 'error' || (status !== 'pending' && !catalog?.length)" class="mt-6">
            <UiEmptyState icon="lucide:cloud-off" :title="discovery.errors.load">
              <UiButton variant="secondary" icon="lucide:refresh-cw" @click="refresh()">{{ discovery.errors.retry }}</UiButton>
            </UiEmptyState>
          </div>
          <div v-else-if="suggestions.length === 0" class="mt-6">
            <UiEmptyState icon="lucide:compass" :title="copy.none" :body="copy.noneBody">
              <UiButton :href="whatsappUrl" icon="lucide:message-circle">WhatsApp</UiButton>
            </UiEmptyState>
          </div>
          <ul v-else class="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            <li v-for="suggestion in suggestions" :key="suggestion.entry.tour.id" class="grid gap-2">
              <TourCard :entry="suggestion.entry" />
              <p v-if="suggestion.reasons.length" class="flex flex-wrap gap-1.5 text-xs">
                <span v-for="reason in suggestion.reasons" :key="reason" class="inline-flex items-center gap-1 rounded-full bg-sts-success-soft px-2 py-0.5 font-semibold text-sts-success">
                  <Icon name="lucide:check" class="size-3" aria-hidden="true" />{{ copy.reasons[reason] }}
                </span>
              </p>
            </li>
          </ul>
        </template>
      </section>
    </div>
  </main>
</template>
