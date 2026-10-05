<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { WegoAlert, WegoButton } from "@wego/ui";
import type { CategoryMedia, ContentLocale, TourCategory } from "@wego/api-contract";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { approveCategoryCover, getCategoryMedia, listCategoryMedia, saveCategoryAlt, uploadCategoryCover } from "../composables/useCategoryMediaApi";
import { COVER_CATEGORIES, useCategoryCoverEditor, validCategoryAlt } from "../composables/useCategoryCoverEditor";
import { useErpLocale } from "../composables/useErpLocale";
import { MAX_PHOTO_BYTES } from "../composables/useTourMediaApi";
import { ToursApiError } from "../composables/useToursApi";
import { categoryMessage, type CategoryMessageKey } from "../utils/categoryMessages";
import { contentErrorKey, contentMessage, type ContentMessageKey } from "../utils/contentMessages";
import { CONTENT_LANGUAGE_NAMES, CONTENT_LOCALES } from "../utils/tourContentEditor";
import StaffPhotoPreview from "../components/StaffPhotoPreview.vue";

const router = useRouter();
const { locale, t, instantLabel } = useErpLocale();
const cm = (key: CategoryMessageKey) => categoryMessage(locale.value, key);
const ct = (key: ContentMessageKey) => contentMessage(locale.value, key);
useHead(() => ({ title: `${cm("title")} · Safari Tours Sharm` }));
const session = ref<AuthSession | null>(null);
const canView = computed(() => hasPermission(session.value, "tours-operator.tour:view"));
const canManage = computed(() => hasPermission(session.value, "tours-operator.tour:manage"));
const canUpload = computed(() => canManage.value && hasPermission(session.value, "tours-operator.media:upload"));
const canPublish = computed(() => hasPermission(session.value, "tours-operator.content:publish"));
const token = () => session.value?.token ?? "";
const editor = useCategoryCoverEditor({
  list: () => listCategoryMedia(token()), read: category => getCategoryMedia(token(), category),
  save: (category, revision, alt) => saveCategoryAlt(token(), category, revision, alt),
  upload: async (category, file, revision, requestId, alt) => { await uploadCategoryCover(token(), category, file, revision, requestId, alt); },
  approve: (category, revision) => approveCategoryCover(token(), category, revision),
});
const { rows, drafts, conflicts, loading, pending, verified, error, notice, hasUnsaved } = editor;
const selectedCategory = ref<TourCategory>("DESERT");
const selectedFiles = ref<Partial<Record<TourCategory, File | null>>>({});
const fileErrors = ref<Partial<Record<TourCategory, boolean>>>({});
const uncertainUploads = ref<Partial<Record<TourCategory, boolean>>>({});
const fileElements = new Map<TourCategory, HTMLInputElement>();
const ready = ref<Partial<Record<TourCategory, boolean>>>({});
const review = ref<CategoryMedia | null>(null);
const reviewPanel = ref<HTMLElement | null>(null);
const reviewedImageReady = ref(false);
const rightsConfirmed = ref(false);
let trigger: HTMLElement | null = null;
const busy = computed(() => loading.value || pending.value !== null);
const allUnsaved = computed(() => hasUnsaved.value || Object.values(selectedFiles.value).some(Boolean));
const selectedRow = computed(() => editor.row(selectedCategory.value));
const writable = computed(() => canManage.value && verified.value && !busy.value && !review.value && !conflicts[selectedCategory.value]);

function setAlt(category: TourCategory, language: ContentLocale, event: Event) {
  const value = (event.target as HTMLInputElement).value;
  drafts[category] = value === "" ? Object.fromEntries(Object.entries(drafts[category]).filter(([code]) => code !== language)) : { ...drafts[category], [language]: value };
}
function setFileElement(category: TourCategory, element: unknown) { if (element instanceof HTMLInputElement) fileElements.set(category, element); }
function selectFile(category: TourCategory, event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null;
  fileErrors.value[category] = file !== null && (file.size === 0 || file.size > MAX_PHOTO_BYTES || !["image/jpeg", "image/png"].includes(file.type));
  selectedFiles.value[category] = fileErrors.value[category] ? null : file;
  uncertainUploads.value[category] = false;
}
function clearFile(category: TourCategory) {
  selectedFiles.value[category] = null; fileErrors.value[category] = false; uncertainUploads.value[category] = false;
  const input = fileElements.get(category); if (input) input.value = "";
}
function handleSessionFailure() {
  if (error.value instanceof ToursApiError && error.value.status === 401) { clearAuthSession(); if (!allUnsaved.value) void router.replace("/login"); }
}
async function refresh() { await editor.load(); handleSessionFailure(); }
async function save(category: TourCategory) { if (!writable.value) return; await editor.save(category); handleSessionFailure(); }
async function upload(category: TourCategory) {
  const file = selectedFiles.value[category];
  if (!writable.value || !canUpload.value || !file || uncertainUploads.value[category] || fileErrors.value[category]) return;
  const success = await editor.upload(category, file, crypto.randomUUID());
  if (success) clearFile(category); else uncertainUploads.value[category] = true;
  handleSessionFailure();
}
async function openRights(category: TourCategory, event: Event) {
  const item = editor.row(category);
  if (!canPublish.value || !verified.value || busy.value || editor.dirty(category) || conflicts[category] || !ready.value[category]
    || !item?.assetId || !item.path || !item.width || !item.height || !item.alt.en?.trim()) return;
  trigger = event.currentTarget as HTMLElement;
  review.value = { ...item, alt: { ...item.alt } }; reviewedImageReady.value = false; rightsConfirmed.value = false;
  await nextTick(); reviewPanel.value?.focus();
}
function dismissReview() { review.value = null; void nextTick(() => trigger?.focus()); }
async function confirmRights() {
  if (!review.value || !rightsConfirmed.value || !reviewedImageReady.value || !canPublish.value || busy.value || !verified.value) return;
  const snapshot = review.value;
  await editor.approve(snapshot.category, snapshot.revision);
  dismissReview(); handleSessionFailure();
}
function beforeUnload(event: BeforeUnloadEvent) { if (allUnsaved.value || pending.value) { event.preventDefault(); event.returnValue = ""; } }
onBeforeRouteLeave(() => (!allUnsaved.value && !pending.value) || window.confirm(cm("leave")));
onMounted(async () => {
  session.value = readAuthSession(); if (!session.value) { void router.replace("/login"); return; }
  if (!canView.value) return;
  window.addEventListener("beforeunload", beforeUnload); await refresh();
});
onBeforeUnmount(() => { editor.dispose(); fileElements.clear(); window.removeEventListener("beforeunload", beforeUnload); });
</script>

<template>
  <main class="px-4 py-7 text-sts-ink sm:px-8 lg:px-12">
    <div class="mx-auto max-w-5xl space-y-6">
      <header class="flex flex-wrap items-start justify-between gap-4">
        <div class="min-w-0"><NuxtLink to="/tours" class="text-sm font-semibold text-sts-ocean hover:underline">{{ t('nav.tours') }}</NuxtLink><h1 class="mt-2 text-2xl font-semibold">{{ cm('title') }}</h1><p class="mt-2 max-w-2xl text-sm text-sts-muted">{{ cm('subtitle') }}</p></div>
        <WegoButton v-if="canView" type="button" variant="secondary" :disabled="busy || !!review" @click="refresh">{{ cm('refresh') }}</WegoButton>
      </header>
      <WegoAlert v-if="!canView" variant="danger">{{ t('tours.noPermission') }}</WegoAlert>
      <template v-else>
        <WegoAlert v-if="error" variant="danger">{{ ct(contentErrorKey(error)) }}</WegoAlert>
        <p v-if="notice" role="status" class="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">{{ t(`category.${notice.category}`) }} · {{ cm(notice.action) }}</p>
        <p v-if="loading && rows.length === 0" role="status" class="text-sm text-sts-muted">{{ t('common.loading') }}</p>
        <p v-if="!canManage" class="text-sm text-sts-muted">{{ ct('readOnly') }}</p>
        <div class="flex flex-wrap gap-2" role="group" :aria-label="cm('select')">
          <button v-for="category in COVER_CATEGORIES" :key="category" type="button" :aria-pressed="selectedCategory === category" :disabled="busy || !!review" class="min-h-11 rounded-xl border px-4 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sts-ocean disabled:opacity-50" :class="selectedCategory === category ? 'border-sts-ocean bg-sts-ocean text-white' : 'border-sts-border bg-sts-surface'" @click="selectedCategory = category">{{ t(`category.${category}`) }}<span v-if="editor.dirty(category) || selectedFiles[category]" aria-hidden="true"> *</span><span v-if="editor.dirty(category) || selectedFiles[category]" class="sr-only">{{ ct('unsaved') }}</span></button>
        </div>
        <p class="text-xs text-sts-muted">{{ cm('ready') }}</p>
        <WegoAlert v-if="!loading && !selectedRow" variant="danger">{{ cm('missing') }}</WegoAlert>
        <section v-if="selectedRow" class="rounded-2xl border border-sts-border bg-sts-surface p-4 sm:p-6" :aria-label="t(`category.${selectedCategory}`)">
          <div class="flex flex-wrap items-start justify-between gap-3"><h2 class="text-xl font-semibold">{{ t(`category.${selectedCategory}`) }}</h2><span class="rounded-full bg-sts-canvas px-3 py-1 text-xs font-semibold">{{ selectedRow.rightsStatus === 'APPROVED' ? ct('approved') : ct('photoDraft') }}</span></div>
          <StaffPhotoPreview v-if="selectedRow.assetId && selectedRow.path && selectedRow.width && selectedRow.height" :key="selectedRow.assetId" class="mt-4 max-w-xl" :path="selectedRow.path" :asset-id="selectedRow.assetId" :token="token()" :width="selectedRow.width" :height="selectedRow.height" :alt="selectedRow.alt[locale] || selectedRow.alt.en || ''" :failure-label="ct('previewFailed')" @ready="ready[selectedRow!.category] = $event" />
          <p v-else class="mt-4 rounded-xl border border-dashed border-sts-border p-6 text-sm text-sts-muted">{{ cm('noCover') }}</p>
          <p class="mt-3 text-xs text-sts-muted">{{ ct('updated') }}: {{ instantLabel(selectedRow.updatedAt) }}</p>
          <p class="mt-2 break-all font-mono text-xs text-sts-muted" dir="ltr">{{ cm('revision') }}: {{ selectedRow.revision }}</p>
          <WegoAlert v-if="conflicts[selectedCategory]" variant="danger" class="mt-4">{{ cm('conflict') }}<button type="button" class="mt-2 block min-h-11 text-start font-semibold underline" :disabled="busy || !!review" @click="editor.discard(selectedCategory)">{{ cm('discard') }}</button></WegoAlert>
          <p class="mt-4 text-sm text-sts-muted">{{ ct('altHelp') }} {{ cm('draftHelp') }}</p>
          <form class="mt-4" @submit.prevent="save(selectedCategory)">
            <fieldset :disabled="!writable" class="grid gap-3 sm:grid-cols-2">
              <label v-for="language in CONTENT_LOCALES" :key="language" class="grid gap-1 text-sm" :for="`category-alt-${language}`"><span>{{ ct('alt') }} · <span :lang="language">{{ CONTENT_LANGUAGE_NAMES[language] }}</span></span><input :id="`category-alt-${language}`" :value="drafts[selectedCategory][language] || ''" :lang="language" :dir="language === 'ar' ? 'rtl' : 'ltr'" class="category-input" maxlength="200" @input="setAlt(selectedCategory, language, $event)"></label>
              <p v-if="editor.dirty(selectedCategory)" class="text-sm text-sts-muted sm:col-span-2">{{ ct('unsaved') }}</p>
              <WegoButton v-if="canManage" type="submit" variant="primary" class="justify-self-start" :disabled="!editor.dirty(selectedCategory) || !validCategoryAlt(drafts[selectedCategory])">{{ pending ? ct('saving') : cm('save') }}</WegoButton>
            </fieldset>
          </form>
          <form v-if="canUpload" class="mt-6 border-t border-sts-border pt-5" @submit.prevent="upload(selectedCategory)">
            <h3 class="font-semibold">{{ ct('upload') }}</h3><p id="category-upload-help" class="mt-2 text-xs text-sts-muted">{{ ct('uploadHelp') }}</p>
            <fieldset :disabled="!writable" class="mt-4 space-y-3">
              <label class="grid gap-1 text-sm" for="category-file">{{ ct('choosePhoto') }}<input id="category-file" :key="selectedCategory" :ref="element => setFileElement(selectedCategory, element)" type="file" accept="image/jpeg,image/png" aria-describedby="category-upload-help" class="min-w-0 w-full rounded-xl border border-sts-border px-3 py-2 text-sm" @change="selectFile(selectedCategory, $event)"></label>
              <p v-if="selectedFiles[selectedCategory]" class="break-words text-xs text-sts-muted">{{ selectedFiles[selectedCategory]?.name }}</p>
              <p v-if="fileErrors[selectedCategory]" role="alert" class="text-sm text-sts-danger">{{ ct('fileInvalid') }}</p>
              <p v-if="uncertainUploads[selectedCategory]" role="alert" class="text-sm text-sts-danger">{{ ct('uploadUncertain') }}</p>
              <div class="flex flex-wrap gap-3"><WegoButton type="submit" variant="primary" :disabled="!selectedFiles[selectedCategory] || fileErrors[selectedCategory] || uncertainUploads[selectedCategory] || !validCategoryAlt(drafts[selectedCategory])">{{ pending ? ct('saving') : ct('upload') }}</WegoButton><WegoButton type="button" variant="secondary" @click="clearFile(selectedCategory)">{{ ct('clearFile') }}</WegoButton></div>
            </fieldset>
          </form>
          <div v-if="canPublish" class="mt-6 border-t border-sts-border pt-5">
            <WegoButton v-if="selectedRow.rightsStatus !== 'APPROVED'" type="button" variant="primary" :disabled="busy || !verified || !!review || editor.dirty(selectedCategory) || !!conflicts[selectedCategory] || !selectedRow.assetId || !selectedRow.alt.en?.trim() || !ready[selectedCategory]" @click="openRights(selectedCategory, $event)">{{ cm('review') }}</WegoButton>
            <p v-if="editor.dirty(selectedCategory)" class="mt-2 text-xs text-sts-muted">{{ ct('photosDirty') }}</p>
          </div>
        </section>
        <section v-if="review" ref="reviewPanel" role="dialog" aria-labelledby="category-review-title" aria-describedby="category-review-help" tabindex="-1" class="rounded-2xl border-2 border-sts-ocean bg-sts-surface p-4 focus:outline-none sm:p-6" @keydown.esc="!busy && dismissReview()">
          <h2 id="category-review-title" class="text-lg font-semibold">{{ cm('review') }} · {{ t(`category.${review.category}`) }}</h2><p id="category-review-help" class="mt-2 text-sm text-sts-muted">{{ ct('rightsHelp') }}</p>
          <StaffPhotoPreview v-if="review.assetId && review.path && review.width && review.height" class="mt-4 max-w-xl" :path="review.path" :asset-id="review.assetId" :token="token()" :width="review.width" :height="review.height" :alt="review.alt[locale] || review.alt.en || ''" :failure-label="ct('previewFailed')" @ready="reviewedImageReady = $event" />
          <dl class="mt-4 grid gap-3 text-sm sm:grid-cols-2"><div v-for="language in CONTENT_LOCALES" :key="language"><dt :lang="language" class="font-semibold">{{ CONTENT_LANGUAGE_NAMES[language] }}</dt><dd :lang="language" :dir="language === 'ar' ? 'rtl' : 'ltr'">{{ review.alt[language] || '—' }}</dd></div></dl>
          <p class="mt-3 break-all font-mono text-xs" dir="ltr">{{ cm('revision') }}: {{ review.revision }}</p>
          <label class="mt-4 flex min-h-11 items-start gap-2 text-sm"><input v-model="rightsConfirmed" type="checkbox" class="mt-1" :disabled="busy">{{ ct('rightsHelp') }}</label>
          <div class="mt-4 flex flex-wrap gap-3"><WegoButton type="button" variant="primary" :disabled="busy || !verified || !rightsConfirmed || !reviewedImageReady" @click="confirmRights">{{ ct('rightsConfirm') }}</WegoButton><WegoButton type="button" variant="secondary" :disabled="busy" @click="dismissReview">{{ t('common.cancel') }}</WegoButton></div>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
@reference "../assets/css/main.css";
.category-input { @apply min-w-0 w-full rounded-xl border border-sts-border bg-sts-canvas px-3 py-2.5 text-sm text-sts-ink focus:outline-2 focus:outline-offset-2 focus:outline-sts-ocean disabled:opacity-60; }
</style>
