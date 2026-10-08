<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { WegoAlert, WegoButton } from "@wego/ui";
import type { ContentLocale, Tour, TourContentDocument, TourFactsDocument } from "@wego/api-contract";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../../../composables/useAuthSession";
import { getStaffTour, ToursApiError } from "../../../composables/useToursApi";
import { getStaffTourContent, publishTourContent, publishTourFacts, saveTourContentDraft, saveTourFactsDraft, unpublishTourContent, unpublishTourFacts } from "../../../composables/useTourContentApi";
import { useTourContentEditor } from "../../../composables/useTourContentEditor";
import { useErpLocale } from "../../../composables/useErpLocale";
import { contentErrorKey, contentMessage, type ContentMessageKey } from "../../../utils/contentMessages";
import { CONTENT_LANGUAGE_NAMES, CONTENT_LOCALES, contentIsValid, contentRecord, factsAreValid, factsRecord, normalizeContent, normalizeFacts, splitContentLines } from "../../../utils/tourContentEditor";
import TourContentPreview from "../../../components/TourContentPreview.vue";
import TourFactsPreview from "../../../components/TourFactsPreview.vue";
import TourMediaEditor from "../../../components/TourMediaEditor.vue";

definePageMeta({ key: route => String(route.params.id) });
const route = useRoute();
const router = useRouter();
const tourId = String(route.params.id);
const { locale, t, instantLabel, count } = useErpLocale();
const ct = (key: ContentMessageKey) => contentMessage(locale.value, key);
useHead(() => ({ title: `${ct("title")} · Safari Tours Sharm` }));
const session = ref<AuthSession | null>(null);
const tour = ref<Tour | null>(null);
const selectedLocale = ref<ContentLocale>("en");
const canView = computed(() => hasPermission(session.value, "tours-operator.tour:view"));
const canManage = computed(() => hasPermission(session.value, "tours-operator.tour:manage"));
const canPublish = computed(() => hasPermission(session.value, "tours-operator.content:publish"));
const canUpload = computed(() => hasPermission(session.value, "tours-operator.media:upload"));
const mediaBusy = ref(false);
const mediaDirty = ref(false);
const token = () => session.value?.token ?? "";
const editor = useTourContentEditor({
  read: () => getStaffTourContent(token(), tourId),
  save: (language, document) => saveTourContentDraft(token(), tourId, language, document),
  publish: (language, revision) => publishTourContent(token(), tourId, language, revision),
  unpublish: language => unpublishTourContent(token(), tourId, language),
  saveFacts: document => saveTourFactsDraft(token(), tourId, document),
  publishFacts: revision => publishTourFacts(token(), tourId, revision),
  unpublishFacts: () => unpublishTourFacts(token(), tourId),
});
const { server, documents, facts, loading, pending, verified, error, notice, factsDirty, hasUnsavedChanges } = editor;
const document = computed(() => documents[selectedLocale.value]);
const draft = computed(() => contentRecord(server.value, selectedLocale.value, "DRAFT"));
const published = computed(() => contentRecord(server.value, selectedLocale.value, "PUBLISHED"));
const factsDraft = computed(() => factsRecord(server.value, "DRAFT"));
const publishedFacts = computed(() => factsRecord(server.value, "PUBLISHED"));
const busy = computed(() => loading.value || pending.value !== null || mediaBusy.value);
const allUnsaved = computed(() => hasUnsavedChanges.value || mediaDirty.value);
const writable = computed(() => canManage.value && verified.value && !busy.value);
const validContent = computed(() => contentIsValid(document.value));
const validFacts = computed(() => factsAreValid(facts.value));
const childChoice = computed({
  get: () => facts.value.childrenAllowed == null ? "" : String(facts.value.childrenAllowed),
  set: value => { facts.value.childrenAllowed = value === "" ? null : value === "true"; },
});
const minimumAge = computed({
  get: () => facts.value.minimumAge == null ? "" : String(facts.value.minimumAge),
  set: (value: string | number) => { facts.value.minimumAge = String(value).trim() === "" ? null : Number(value); },
});
const guideLanguages = computed({
  get: () => facts.value.guideLanguages?.join(", ") ?? "",
  set: value => { facts.value.guideLanguages = value === "" ? [] : value.split(",").map(code => code.trim()); },
});
type Review = { action: "publish" | "unpublish"; language: ContentLocale | "facts"; revision: string; text?: TourContentDocument; facts?: TourFactsDocument };
const review = ref<Review | null>(null);
const reviewPanel = ref<HTMLElement | null>(null);
let trigger: HTMLElement | null = null;
const disabledForm = computed(() => !writable.value || review.value !== null);

function listText(field: "includes" | "excludes" | "knowBeforeYouGo") { return document.value[field]?.join("\n") ?? ""; }
function updateList(field: "includes" | "excludes" | "knowBeforeYouGo", event: Event) {
  const value = (event.target as HTMLTextAreaElement).value;
  // Preserve a just-typed newline/caret while editing; normalize only when leaving the field.
  document.value[field] = value === "" ? [] : value.split(/\r?\n/);
}
function finishList(field: "includes" | "excludes" | "knowBeforeYouGo", event: Event) { document.value[field] = splitContentLines((event.target as HTMLTextAreaElement).value); }
function addTextStop() { document.value.stops = [...(document.value.stops ?? []), { stopKey: "", name: "", description: null }]; }
function addFactsStop() { facts.value.stops = [...(facts.value.stops ?? []), { key: "", kind: "STOP", latitude: Number.NaN, longitude: Number.NaN }]; }

async function openReview(action: Review["action"], language: Review["language"], event: Event) {
  if (!canPublish.value || busy.value || !verified.value) return;
  const record = language === "facts" ? factsRecord(server.value, action === "publish" ? "DRAFT" : "PUBLISHED") : contentRecord(server.value, language, action === "publish" ? "DRAFT" : "PUBLISHED");
  if (!record || (action === "publish" && (language === "facts" ? factsDirty.value : editor.dirty(language)))) return;
  trigger = event.currentTarget as HTMLElement;
  review.value = language === "facts"
    ? { action, language, revision: record.revision, facts: normalizeFacts(record.document as TourFactsDocument) }
    : { action, language, revision: record.revision, text: normalizeContent(record.document as TourContentDocument) };
  await nextTick();
  reviewPanel.value?.focus();
}

function dismissReview() { review.value = null; void nextTick(() => trigger?.focus()); }
async function confirmReview() {
  if (!review.value || !canPublish.value) return;
  const snapshot = review.value;
  const success = snapshot.language === "facts"
    ? await (snapshot.action === "publish" ? editor.publishFacts(snapshot.revision) : editor.unpublishFacts())
    : await (snapshot.action === "publish" ? editor.publish(snapshot.language, snapshot.revision) : editor.unpublish(snapshot.language));
  // Failure is displayed outside the review; never keep a stale confirmation ready to retry.
  dismissReview();
  if (!success) handleSessionFailure();
}

function handleSessionFailure() {
  if (error.value instanceof ToursApiError && error.value.status === 401) {
    clearAuthSession();
    if (!hasUnsavedChanges.value) void router.replace("/login");
  }
}
async function refresh() { await editor.load(); handleSessionFailure(); }
async function saveText() { if (!canManage.value) return; await editor.save(selectedLocale.value); handleSessionFailure(); }
async function saveFacts() { if (!canManage.value) return; await editor.saveFacts(); handleSessionFailure(); }
function beforeUnload(event: BeforeUnloadEvent) { if (allUnsaved.value || pending.value || mediaBusy.value) { event.preventDefault(); event.returnValue = ""; } }
onBeforeRouteLeave(() => (!allUnsaved.value && !pending.value && !mediaBusy.value) || window.confirm(ct("leave")));
onMounted(async () => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  if (!canView.value) return;
  window.addEventListener("beforeunload", beforeUnload);
  // Metadata is optional presentation; the authoritative content request owns its own failure state.
  void getStaffTour(token(), tourId).then(result => { tour.value = result; }).catch(() => {});
  await refresh();
});
onBeforeUnmount(() => { editor.dispose(); window.removeEventListener("beforeunload", beforeUnload); });
</script>

<template>
  <main class="px-4 py-7 text-sts-ink sm:px-8 lg:px-12">
    <div class="mx-auto max-w-6xl space-y-6">
      <header class="flex flex-wrap items-start justify-between gap-4">
        <div class="min-w-0">
          <NuxtLink to="/tours" class="text-sm font-semibold text-sts-ocean hover:underline">{{ t('nav.tours') }}</NuxtLink>
          <h1 class="mt-2 text-2xl font-semibold">{{ ct('title') }}</h1>
          <p v-if="tour" class="mt-1 break-words font-medium">{{ tour.nameEn || tour.slug }} <span class="ms-2 rounded-full bg-sts-canvas px-2 py-1 text-xs text-sts-muted">{{ tour.isActive ? t('common.active') : t('common.inactive') }}</span></p>
          <p class="mt-2 max-w-2xl text-sm text-sts-muted">{{ ct('subtitle') }}</p>
        </div>
        <WegoButton v-if="canView" type="button" variant="secondary" :disabled="busy || !!review" @click="refresh">{{ ct('refresh') }}</WegoButton>
      </header>
      <WegoAlert v-if="!canView" variant="danger">{{ t('tours.noPermission') }}</WegoAlert>
      <template v-else>
        <WegoAlert v-if="error" variant="danger" role="alert">{{ ct(contentErrorKey(error)) }}</WegoAlert>
        <p v-if="notice" role="status" class="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">{{ ct(notice === 'published' ? 'publishedNotice' : notice) }}</p>
        <p v-if="loading && !server" role="status" class="text-sm text-sts-muted">{{ t('common.loading') }}</p>
        <p v-if="!canManage" class="text-sm text-sts-muted">{{ ct('readOnly') }}</p>
        <p v-else-if="!canPublish" class="text-sm text-sts-muted">{{ ct('cannotPublish') }}</p>
        <template v-if="server">
          <p class="text-xs text-sts-muted">{{ ct('refreshHelp') }}</p>
          <div class="flex flex-wrap gap-2" role="group" :aria-label="ct('language')">
            <button
v-for="language in CONTENT_LOCALES" :key="language" type="button" :aria-pressed="selectedLocale === language" :disabled="busy || !!review"
              class="min-h-11 rounded-xl border px-4 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sts-ocean disabled:opacity-50"
              :class="selectedLocale === language ? 'border-sts-ocean bg-sts-ocean text-white' : 'border-sts-border bg-sts-surface'" @click="selectedLocale = language">
              <span :lang="language">{{ CONTENT_LANGUAGE_NAMES[language] }}</span><span v-if="editor.dirty(language)" aria-hidden="true"> *</span>
              <span v-if="editor.dirty(language)" class="sr-only">{{ ct('unsaved') }}</span>
            </button>
          </div>
          <section class="rounded-2xl border border-sts-border bg-sts-surface p-4 sm:p-6" :aria-label="ct('draft')">
            <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
              <h2 class="font-semibold">{{ ct('draft') }} · <span :lang="selectedLocale">{{ CONTENT_LANGUAGE_NAMES[selectedLocale] }}</span></h2>
              <span class="rounded-full bg-sts-canvas px-3 py-1 text-xs">{{ editor.dirty(selectedLocale) ? ct('unsaved') : draft ? ct('clean') : ct('missing') }}</span>
            </div>
            <p class="mb-4 text-sm text-sts-muted">{{ ct('requiredHelp') }}</p>
            <form @submit.prevent="saveText">
              <fieldset :disabled="disabledForm" class="grid min-w-0 gap-4">
                <label class="grid gap-1 text-sm font-medium" for="content-name">{{ ct('name') }}<input id="content-name" v-model="document.name" :lang="selectedLocale" :dir="selectedLocale === 'ar' ? 'rtl' : 'ltr'" class="content-input" required maxlength="120"></label>
                <label class="grid gap-1 text-sm font-medium" for="content-short">{{ ct('shortDescription') }}<textarea id="content-short" v-model="document.shortDescription" :lang="selectedLocale" :dir="selectedLocale === 'ar' ? 'rtl' : 'ltr'" class="content-input" required maxlength="300" rows="3" /></label>
                <label class="grid gap-1 text-sm font-medium" for="content-description">{{ ct('description') }}<textarea id="content-description" v-model="document.description" :lang="selectedLocale" :dir="selectedLocale === 'ar' ? 'rtl' : 'ltr'" class="content-input" required maxlength="6000" rows="8" /></label>
                <p id="content-list-help" class="text-xs text-sts-muted">{{ ct('listHelp') }}</p>
                <div class="grid gap-4 md:grid-cols-3">
                  <label v-for="field in (['includes', 'excludes', 'knowBeforeYouGo'] as const)" :key="field" class="grid gap-1 text-sm font-medium" :for="`content-${field}`">{{ ct(field) }}
                    <textarea :id="`content-${field}`" :value="listText(field)" :lang="selectedLocale" :dir="selectedLocale === 'ar' ? 'rtl' : 'ltr'" aria-describedby="content-list-help" class="content-input" rows="5" @input="updateList(field, $event)" @blur="finishList(field, $event)" />
                  </label>
                </div>
                <label class="grid gap-1 text-sm font-medium" for="content-meeting">{{ ct('meetingPoint') }}<input id="content-meeting" v-model="document.meetingPoint" :lang="selectedLocale" :dir="selectedLocale === 'ar' ? 'rtl' : 'ltr'" class="content-input" maxlength="300"></label>
                <section class="space-y-3">
                  <h3 class="font-semibold">{{ ct('stops') }}</h3><p class="text-xs text-sts-muted">{{ ct('stopHelp') }}</p>
                  <div v-for="(stop, index) in document.stops" :key="index" class="grid gap-3 rounded-xl border border-sts-border p-3 sm:grid-cols-2">
                    <label class="grid gap-1 text-sm" :for="`text-stop-key-${index}`">{{ ct('stopKey') }}<input :id="`text-stop-key-${index}`" v-model="stop.stopKey" class="content-input" dir="ltr" required maxlength="40" pattern="[a-z0-9][a-z0-9-]{0,39}"></label>
                    <label class="grid gap-1 text-sm" :for="`text-stop-name-${index}`">{{ ct('stopName') }}<input :id="`text-stop-name-${index}`" v-model="stop.name" class="content-input" :lang="selectedLocale" :dir="selectedLocale === 'ar' ? 'rtl' : 'ltr'" required maxlength="120"></label>
                    <label class="grid gap-1 text-sm sm:col-span-2" :for="`text-stop-description-${index}`">{{ ct('stopDescription') }}<textarea :id="`text-stop-description-${index}`" v-model="stop.description" class="content-input" :lang="selectedLocale" :dir="selectedLocale === 'ar' ? 'rtl' : 'ltr'" maxlength="500" rows="2" /></label>
                    <button type="button" class="min-h-11 text-start text-sm font-semibold text-sts-danger" @click="document.stops?.splice(index, 1)">{{ ct('removeStop') }} {{ count(index + 1) }}</button>
                  </div>
                  <WegoButton type="button" variant="secondary" :disabled="(document.stops?.length ?? 0) >= 12" @click="addTextStop">{{ ct('addStop') }}</WegoButton>
                </section>
                <p v-if="!validContent && editor.dirty(selectedLocale)" class="text-sm text-sts-danger">{{ ct('invalid') }}</p>
                <WegoButton v-if="canManage" type="submit" variant="primary" class="justify-self-start" :disabled="!validContent || !editor.dirty(selectedLocale)">{{ pending ? ct('saving') : ct('save') }}</WegoButton>
              </fieldset>
            </form>
            <div class="mt-5 flex flex-wrap items-center gap-3 border-t border-sts-border pt-4">
              <WegoButton v-if="canPublish" type="button" variant="primary" :disabled="busy || !verified || !draft || editor.dirty(selectedLocale) || !!review" @click="openReview('publish', selectedLocale, $event)">{{ ct('publish') }}</WegoButton>
              <WegoButton v-if="canPublish && published" type="button" variant="secondary" :disabled="busy || !verified || !!review" @click="openReview('unpublish', selectedLocale, $event)">{{ ct('unpublish') }}</WegoButton>
              <p v-if="editor.dirty(selectedLocale)" class="text-xs text-sts-muted">{{ ct('saveFirst') }}</p>
              <p v-if="draft" class="min-w-0 break-all text-xs text-sts-muted">{{ ct('updated') }}: {{ instantLabel(draft.updatedAt) }}</p>
            </div>
          </section>
          <details class="rounded-2xl border border-sts-border bg-sts-surface p-4 sm:p-6">
            <summary class="cursor-pointer font-semibold">{{ ct('preview') }} · {{ published ? ct('published') : ct('missing') }}</summary>
            <TourContentPreview v-if="published" class="mt-4" :document="published.document" :language="selectedLocale" :staff-locale="locale" />
            <p v-else class="mt-4 text-sm text-sts-muted">{{ ct('noPublished') }}</p>
          </details>
          <section class="rounded-2xl border border-sts-border bg-sts-surface p-4 sm:p-6" :aria-label="ct('facts')">
            <h2 class="font-semibold">{{ ct('facts') }} <span v-if="factsDirty" class="text-sm text-sts-muted">· {{ ct('unsaved') }}</span></h2>
            <p class="mt-2 text-sm text-sts-muted">{{ ct('factsHelp') }}</p>
            <form class="mt-4" @submit.prevent="saveFacts">
              <fieldset :disabled="disabledForm" class="grid min-w-0 gap-4 sm:grid-cols-2">
                <label class="grid gap-1 text-sm font-medium" for="facts-children">{{ ct('children') }}<select id="facts-children" v-model="childChoice" class="content-input"><option value="">{{ ct('unknown') }}</option><option value="true">{{ ct('yes') }}</option><option value="false">{{ ct('no') }}</option></select></label>
                <label class="grid gap-1 text-sm font-medium" for="facts-age">{{ ct('minimumAge') }}<input id="facts-age" v-model="minimumAge" class="content-input" type="number" min="0" max="99" step="1" :placeholder="ct('unknown')"></label>
                <label class="grid gap-1 text-sm font-medium" for="facts-pickup">{{ ct('pickup') }}<select id="facts-pickup" v-model="facts.hotelPickup" class="content-input"><option :value="null">{{ ct('unknown') }}</option><option value="INCLUDED">{{ ct('pickupIncluded') }}</option><option value="NOT_INCLUDED">{{ ct('pickupExcluded') }}</option><option value="SOME_AREAS">{{ ct('pickupSome') }}</option></select></label>
                <label class="grid gap-1 text-sm font-medium" for="facts-languages">{{ ct('languages') }}<input id="facts-languages" v-model="guideLanguages" dir="ltr" class="content-input" aria-describedby="facts-language-help"><span id="facts-language-help" class="text-xs font-normal text-sts-muted">{{ ct('languageHelp') }}</span></label>
                <section class="space-y-3 sm:col-span-2">
                  <h3 class="font-semibold">{{ ct('stops') }}</h3><p class="text-xs text-sts-muted">{{ ct('coordinatesHelp') }}</p>
                  <div v-for="(stop, index) in facts.stops" :key="index" class="grid gap-3 rounded-xl border border-sts-border p-3 sm:grid-cols-2">
                    <label class="grid gap-1 text-sm" :for="`fact-stop-key-${index}`">{{ ct('stopKey') }}<input :id="`fact-stop-key-${index}`" v-model="stop.key" class="content-input" dir="ltr" required maxlength="40" pattern="[a-z0-9][a-z0-9-]{0,39}"></label>
                    <label class="grid gap-1 text-sm" :for="`fact-stop-kind-${index}`">{{ ct('kind') }}<select :id="`fact-stop-kind-${index}`" v-model="stop.kind" class="content-input"><option value="STOP">{{ ct('stop') }}</option><option value="MEETING_POINT">{{ ct('meetingStop') }}</option></select></label>
                    <label class="grid gap-1 text-sm" :for="`fact-stop-lat-${index}`">{{ ct('latitude') }}<input :id="`fact-stop-lat-${index}`" v-model.number="stop.latitude" type="number" min="-90" max="90" step="any" required class="content-input" dir="ltr"></label>
                    <label class="grid gap-1 text-sm" :for="`fact-stop-lon-${index}`">{{ ct('longitude') }}<input :id="`fact-stop-lon-${index}`" v-model.number="stop.longitude" type="number" min="-180" max="180" step="any" required class="content-input" dir="ltr"></label>
                    <button type="button" class="min-h-11 text-start text-sm font-semibold text-sts-danger" @click="facts.stops?.splice(index, 1)">{{ ct('removeStop') }} {{ count(index + 1) }}</button>
                  </div>
                  <WegoButton type="button" variant="secondary" :disabled="(facts.stops?.length ?? 0) >= 12" @click="addFactsStop">{{ ct('addStop') }}</WegoButton>
                </section>
                <p v-if="!validFacts" class="text-sm text-sts-danger sm:col-span-2">{{ ct('invalid') }}</p>
                <WegoButton v-if="canManage" type="submit" variant="primary" class="justify-self-start" :disabled="!validFacts || !factsDirty">{{ pending ? ct('saving') : ct('save') }}</WegoButton>
              </fieldset>
            </form>
            <div class="mt-5 flex flex-wrap gap-3 border-t border-sts-border pt-4">
              <WegoButton v-if="canPublish" type="button" variant="primary" :disabled="busy || !verified || !factsDraft || factsDirty || !!review" @click="openReview('publish', 'facts', $event)">{{ ct('publish') }}</WegoButton>
              <WegoButton v-if="canPublish && publishedFacts" type="button" variant="secondary" :disabled="busy || !verified || !!review" @click="openReview('unpublish', 'facts', $event)">{{ ct('unpublish') }}</WegoButton>
            </div>
            <details class="mt-4 border-t border-sts-border pt-4"><summary class="cursor-pointer text-sm font-semibold">{{ ct('factsPreview') }}</summary><TourFactsPreview v-if="publishedFacts" class="mt-4" :document="publishedFacts.document" :staff-locale="locale" /><p v-else class="mt-4 text-sm text-sts-muted">{{ ct('noPublishedFacts') }}</p></details>
          </section>
          <TourMediaEditor :tour-id="tourId" :token="token()" :media="server.media" :media-revision="server.mediaRevision" :staff-locale="locale" :can-manage="canManage" :can-upload="canUpload" :can-publish="canPublish" :verified="verified" :locked="loading || pending !== null || !!review" :refresh="editor.load" @busy="mediaBusy = $event" @dirty="mediaDirty = $event" />
          <section v-if="review" ref="reviewPanel" role="dialog" aria-labelledby="content-review-title" aria-describedby="content-review-help" tabindex="-1" class="rounded-2xl border-2 border-sts-ocean bg-sts-surface p-4 focus:outline-none sm:p-6" @keydown.esc="!pending && dismissReview()">
            <h2 id="content-review-title" class="text-lg font-semibold">{{ review.action === 'publish' ? ct('review') : ct('confirmUnpublish') }}</h2>
            <p id="content-review-help" class="mt-2 text-sm text-sts-muted">{{ review.action === 'publish' ? ct('reviewHelp') : ct('confirmUnpublishHelp') }}</p>
            <p class="mt-3 break-all font-mono text-xs" dir="ltr">{{ ct('revision') }}: {{ review.revision }}</p>
            <TourContentPreview v-if="review.text && review.language !== 'facts'" class="mt-5" :document="review.text" :language="review.language" :staff-locale="locale" />
            <TourFactsPreview v-if="review.facts" class="mt-5" :document="review.facts" :staff-locale="locale" />
            <div class="mt-6 flex flex-wrap gap-3"><WegoButton type="button" variant="primary" :disabled="busy || !verified" @click="confirmReview">{{ pending ? ct('saving') : review.action === 'publish' ? ct('confirmPublish') : ct('unpublish') }}</WegoButton><WegoButton type="button" variant="secondary" :disabled="busy" @click="dismissReview">{{ t('common.cancel') }}</WegoButton></div>
          </section>
        </template>
      </template>
    </div>
  </main>
</template>

<style scoped>
@reference "../../../assets/css/main.css";
.content-input { @apply min-w-0 w-full rounded-xl border border-sts-border bg-sts-canvas px-3 py-2.5 text-sm text-sts-ink focus:outline-2 focus:outline-offset-2 focus:outline-sts-ocean disabled:opacity-60; }
</style>
