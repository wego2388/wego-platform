<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { WegoAlert, WegoButton } from "@wego/ui";
import type { ContentLocale, StaffTourContent, TourMediaInput } from "@wego/api-contract";
import { approveTourPhoto, MAX_PHOTO_BYTES, replaceTourMedia, uploadTourPhoto, type PhotoAlt } from "../composables/useTourMediaApi";
import { contentErrorKey, contentMessage, type ContentMessageKey } from "../utils/contentMessages";
import { CONTENT_LANGUAGE_NAMES, CONTENT_LOCALES } from "../utils/tourContentEditor";
import { erpMessage, type ErpLocale } from "../utils/erpLocale";
import StaffPhotoPreview from "./StaffPhotoPreview.vue";

type Photo = StaffTourContent["media"][number];
const props = defineProps<{ tourId: string; token: string; media: Photo[]; mediaRevision: string; staffLocale: ErpLocale; canManage: boolean; canUpload: boolean; canPublish: boolean; locked: boolean; verified: boolean; refresh: () => Promise<boolean> }>();
const emit = defineEmits<{ busy: [boolean]; dirty: [boolean] }>();
const ct = (key: ContentMessageKey) => contentMessage(props.staffLocale, key);
const inputs = (photos: Photo[]): TourMediaInput[] => photos.map(photo => ({ id: photo.id, path: photo.path, width: photo.width, height: photo.height, isCover: photo.isCover, alt: { ...photo.alt } }));
const entries = ref<TourMediaInput[]>(inputs(props.media));
const baseline = ref(JSON.stringify(entries.value));
const baselineRevision = ref(props.mediaRevision);
const dirty = computed(() => JSON.stringify(entries.value) !== baseline.value);
const pending = ref(false);
const metadataConflict = ref(false);
const error = ref<unknown>(null);
const notice = ref<ContentMessageKey | null>(null);
const selectedFile = ref<File | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const uploadAlt = ref<PhotoAlt>({});
const replacementId = ref("");
const uploadUncertain = ref(false);
const fileInvalid = ref(false);
const ready = ref<Record<string, boolean>>({});
const review = ref<Photo | null>(null);
const reviewPanel = ref<HTMLElement | null>(null);
const reviewReady = ref(false);
const confirmedRights = ref(false);
let alive = true;
let trigger: HTMLElement | null = null;
const locked = computed(() => props.locked || pending.value || !props.verified || review.value !== null);
const metadataValid = computed(() => entries.value.length <= 30 && entries.value.every(photo => Object.values(photo.alt ?? {}).every(text => text.trim().length > 0 && text.trim().length <= 200)));
watch(() => [props.media, props.mediaRevision] as const, ([photos, revision]) => {
  const preserve = dirty.value;
  const next = inputs(photos); const fingerprint = JSON.stringify(next);
  if (preserve && (fingerprint !== baseline.value || revision !== baselineRevision.value)) { metadataConflict.value = true; return; }
  baseline.value = fingerprint; baselineRevision.value = revision;
  if (!preserve) { entries.value = next; metadataConflict.value = false; }
}, { deep: true });
const hasLocalChanges = computed(() => dirty.value || selectedFile.value !== null);
watch(hasLocalChanges, value => emit("dirty", value), { immediate: true });
watch(pending, value => emit("busy", value));
watch(replacementId, id => { uploadAlt.value = { ...(props.media.find(photo => photo.id === id)?.alt ?? {}) }; });
onBeforeUnmount(() => { alive = false; });

function selectFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null;
  fileInvalid.value = file !== null && (file.size === 0 || file.size > MAX_PHOTO_BYTES || !["image/jpeg", "image/png"].includes(file.type));
  selectedFile.value = fileInvalid.value ? null : file;
  uploadUncertain.value = false;
}
function clearFile() { selectedFile.value = null; fileInvalid.value = false; uploadUncertain.value = false; if (fileInput.value) fileInput.value.value = ""; }
function setAlt(entry: TourMediaInput, language: ContentLocale, event: Event) {
  const value = (event.target as HTMLInputElement).value;
  if (!entry.alt) entry.alt = {};
  if (value === "") entry.alt = Object.fromEntries(Object.entries(entry.alt).filter(([code]) => code !== language)); else entry.alt[language] = value;
}
function move(index: number, direction: number) {
  const next = [...entries.value]; const to = index + direction;
  if (to < 0 || to >= next.length) return;
  [next[index], next[to]] = [next[to]!, next[index]!]; entries.value = next;
}
function setCover(index: number, event: Event) { const checked = (event.target as HTMLInputElement).checked; entries.value.forEach((entry, position) => { entry.isCover = position === index && checked; }); }
async function command(action: () => Promise<void>, success: ContentMessageKey): Promise<boolean> {
  if (locked.value) return false;
  pending.value = true; error.value = null; notice.value = null;
  try {
    await action();
    if (!await props.refresh()) throw new Error("verification_failed");
    await nextTick();
    if (!alive) return false;
    entries.value = inputs(props.media); baseline.value = JSON.stringify(entries.value); baselineRevision.value = props.mediaRevision; metadataConflict.value = false; notice.value = success;
    return true;
  } catch (cause) {
    if (!alive) return false;
    error.value = cause;
    // Re-read after every uncertain failure, without re-sending a file or publishing command.
    await props.refresh();
    return false;
  } finally { if (alive) pending.value = false; }
}
async function upload() {
  if (!props.canUpload || !props.canManage || dirty.value || !selectedFile.value || uploadUncertain.value) return;
  const previous = replacementId.value ? props.media.find(photo => photo.id === replacementId.value) : null;
  if (replacementId.value && !previous) return;
  const file = selectedFile.value;
  const requestId = crypto.randomUUID();
  const success = await command(async () => { await uploadTourPhoto(props.token, props.tourId, file, { ...uploadAlt.value }, requestId, previous ? { mediaId: previous.id, revision: previous.revision } : undefined); }, "uploaded");
  if (success) clearFile(); else uploadUncertain.value = true;
}
async function saveMetadata() {
  if (!props.canManage || !dirty.value || !metadataValid.value || metadataConflict.value) return;
  const snapshot = entries.value.map(entry => ({ ...entry, alt: { ...entry.alt } }));
  const reviewedRevision = baselineRevision.value;
  await command(() => replaceTourMedia(props.token, props.tourId, snapshot, reviewedRevision), "metadataSaved");
}
function discardPhotoEdits() { entries.value = inputs(props.media); baseline.value = JSON.stringify(entries.value); baselineRevision.value = props.mediaRevision; metadataConflict.value = false; }
async function openRights(photoId: string, event: Event) {
  if (locked.value || dirty.value || !props.canPublish || !ready.value[photoId]) return;
  const photo = props.media.find(item => item.id === photoId);
  if (!photo || !photo.alt.en?.trim()) return;
  review.value = { ...photo, alt: { ...photo.alt } }; reviewReady.value = false; confirmedRights.value = false;
  trigger = event.currentTarget as HTMLElement; await nextTick(); reviewPanel.value?.focus();
}
function dismissReview() { review.value = null; void nextTick(() => trigger?.focus()); }
async function approve() {
  if (!review.value || !props.canPublish || !confirmedRights.value || !reviewReady.value || props.locked || pending.value || !props.verified || dirty.value) return;
  const snapshot = review.value;
  review.value = null;
  await command(() => approveTourPhoto(props.token, props.tourId, snapshot.id, snapshot.revision), "rightsApproved");
  dismissReview();
}
</script>

<template>
  <section class="rounded-2xl border border-sts-border bg-sts-surface p-4 sm:p-6" :aria-label="ct('photos')">
    <h2 class="font-semibold">{{ ct('photos') }} · {{ media.length }}</h2>
    <p class="mt-2 text-sm text-sts-muted">{{ ct('altHelp') }}</p>
    <WegoAlert v-if="error" variant="danger" class="mt-4">{{ ct(contentErrorKey(error)) }}</WegoAlert>
    <p v-if="notice" role="status" class="mt-4 text-sm text-emerald-800">{{ ct(notice) }}</p>
    <p v-if="dirty" class="mt-4 text-sm text-sts-muted">{{ ct('photosDirty') }}</p>
    <WegoAlert v-if="metadataConflict" variant="danger" class="mt-4">{{ ct('photoConflict') }}<button type="button" :disabled="pending || props.locked" class="mt-2 block min-h-11 text-start font-semibold underline" @click="discardPhotoEdits">{{ ct('discardPhotoEdits') }}</button></WegoAlert>
    <p v-if="entries.length === 0" class="mt-4 text-sm text-sts-muted">{{ ct('noPhotos') }}</p>
    <ol v-else class="mt-4 grid gap-4 lg:grid-cols-2">
      <li v-for="(photo, index) in entries" :key="photo.id || photo.path" class="min-w-0 space-y-3 rounded-xl border border-sts-border p-3">
        <StaffPhotoPreview :path="photo.path" :token="token" :width="photo.width" :height="photo.height" :alt="photo.alt?.[staffLocale] || photo.alt?.en || ''" :failure-label="ct('previewFailed')" @ready="ready[photo.id!] = $event" />
        <p class="text-xs font-semibold">{{ media.find(item => item.id === photo.id)?.rightsStatus === 'APPROVED' ? ct('approved') : ct('photoDraft') }}</p>
        <p class="break-all font-mono text-xs text-sts-muted" dir="ltr">{{ photo.path }}</p>
        <fieldset :disabled="locked || !canManage" class="grid gap-2 sm:grid-cols-2">
          <label v-for="language in CONTENT_LOCALES" :key="language" class="grid gap-1 text-xs" :for="`photo-alt-${photo.id}-${language}`"><span>{{ ct('alt') }} · <span :lang="language">{{ CONTENT_LANGUAGE_NAMES[language] }}</span></span><input :id="`photo-alt-${photo.id}-${language}`" :value="photo.alt?.[language] || ''" :lang="language" :dir="language === 'ar' ? 'rtl' : 'ltr'" maxlength="200" class="photo-input" @input="setAlt(photo, language, $event)"></label>
          <label class="flex min-h-11 items-center gap-2 text-sm sm:col-span-2"><input type="checkbox" :checked="photo.isCover === true" @change="setCover(index, $event)">{{ ct('cover') }}</label>
          <div v-if="canManage" class="flex flex-wrap gap-3 sm:col-span-2">
            <button type="button" :disabled="index === 0" class="min-h-11 text-sm font-semibold text-sts-ocean disabled:opacity-40" @click="move(index, -1)">{{ ct('moveUp') }}</button>
            <button type="button" :disabled="index === entries.length - 1" class="min-h-11 text-sm font-semibold text-sts-ocean disabled:opacity-40" @click="move(index, 1)">{{ ct('moveDown') }}</button>
            <button type="button" class="min-h-11 text-sm font-semibold text-sts-danger" @click="entries.splice(index, 1)">{{ ct('removePhoto') }}</button>
          </div>
        </fieldset>
        <WegoButton v-if="canPublish && media.find(item => item.id === photo.id)?.rightsStatus !== 'APPROVED'" type="button" variant="secondary" :disabled="locked || dirty || !photo.alt?.en?.trim() || !ready[photo.id!]" @click="openRights(photo.id!, $event)">{{ ct('approveRights') }}</WegoButton>
      </li>
    </ol>
    <p v-if="canManage && entries.length" class="mt-3 text-xs text-sts-muted">{{ ct('removeHelp') }}</p>
    <WegoButton v-if="canManage" type="button" variant="primary" class="mt-4" :disabled="locked || !dirty || !metadataValid || metadataConflict" @click="saveMetadata">{{ pending ? ct('saving') : ct('savePhotos') }}</WegoButton>
    <form v-if="canUpload && canManage" class="mt-6 border-t border-sts-border pt-5" @submit.prevent="upload">
      <h3 class="font-semibold">{{ ct('upload') }}</h3><p id="photo-upload-help" class="mt-2 text-xs text-sts-muted">{{ ct('uploadHelp') }} {{ ct('photoLimit') }}</p>
      <fieldset :disabled="locked || dirty" class="mt-4 grid gap-3 sm:grid-cols-2">
        <label class="grid gap-1 text-sm" for="photo-replacement">{{ ct('replacePhoto') }}<select id="photo-replacement" v-model="replacementId" class="photo-input"><option value="">{{ ct('newPhoto') }}</option><option v-for="photo in media" :key="photo.id" :value="photo.id">{{ photo.alt[staffLocale] || photo.alt.en || photo.id }}</option></select></label>
        <label class="grid gap-1 text-sm" for="photo-file">{{ ct('choosePhoto') }}<input id="photo-file" ref="fileInput" type="file" accept="image/jpeg,image/png" aria-describedby="photo-upload-help" class="min-w-0 w-full rounded-xl border border-sts-border px-3 py-2 text-sm" @change="selectFile"></label>
        <label v-for="language in CONTENT_LOCALES" :key="language" class="grid gap-1 text-xs" :for="`upload-alt-${language}`"><span>{{ ct('alt') }} · <span :lang="language">{{ CONTENT_LANGUAGE_NAMES[language] }}</span></span><input :id="`upload-alt-${language}`" v-model="uploadAlt[language]" :lang="language" :dir="language === 'ar' ? 'rtl' : 'ltr'" class="photo-input" maxlength="200"></label>
        <p v-if="fileInvalid" role="alert" class="text-sm text-sts-danger sm:col-span-2">{{ ct('fileInvalid') }}</p>
        <p v-if="uploadUncertain" role="alert" class="text-sm text-sts-danger sm:col-span-2">{{ ct('uploadUncertain') }}</p>
        <div class="flex flex-wrap gap-3 sm:col-span-2"><WegoButton type="submit" variant="primary" :disabled="!selectedFile || fileInvalid || uploadUncertain || (!replacementId && media.length >= 30)">{{ pending ? ct('saving') : ct('upload') }}</WegoButton><WegoButton type="button" variant="secondary" @click="clearFile">{{ ct('clearFile') }}</WegoButton></div>
      </fieldset>
    </form>
    <section v-if="review" ref="reviewPanel" role="dialog" aria-labelledby="photo-rights-title" tabindex="-1" class="mt-6 rounded-xl border-2 border-sts-ocean p-4 focus:outline-none" @keydown.esc="dismissReview">
      <h3 id="photo-rights-title" class="font-semibold">{{ ct('approveRights') }}</h3><p class="mt-2 text-sm text-sts-muted">{{ ct('rightsHelp') }}</p>
      <StaffPhotoPreview class="mt-4" :path="review.path" :token="token" :width="review.width" :height="review.height" :alt="review.alt[staffLocale] || review.alt.en || ''" :failure-label="ct('previewFailed')" @ready="reviewReady = $event" />
      <dl class="mt-3 space-y-2 text-sm"><div v-for="language in CONTENT_LOCALES" :key="language"><dt :lang="language" class="font-semibold">{{ CONTENT_LANGUAGE_NAMES[language] }}</dt><dd :lang="language" :dir="language === 'ar' ? 'rtl' : 'ltr'">{{ review.alt[language] || '—' }}</dd></div></dl>
      <p class="mt-3 break-all font-mono text-xs" dir="ltr">{{ ct('revision') }}: {{ review.revision }}</p>
      <label class="mt-4 flex min-h-11 items-start gap-2 text-sm"><input v-model="confirmedRights" type="checkbox" class="mt-1">{{ ct('rightsHelp') }}</label>
      <div class="mt-4 flex flex-wrap gap-3"><WegoButton type="button" variant="primary" :disabled="!confirmedRights || !reviewReady || pending || props.locked || !verified" @click="approve">{{ ct('rightsConfirm') }}</WegoButton><WegoButton type="button" variant="secondary" @click="dismissReview">{{ erpMessage(staffLocale, 'common.cancel') }}</WegoButton></div>
    </section>
  </section>
</template>

<style scoped>
@reference "../assets/css/main.css";
.photo-input { @apply min-w-0 w-full rounded-xl border border-sts-border bg-sts-canvas px-3 py-2.5 text-sm text-sts-ink focus:outline-2 focus:outline-offset-2 focus:outline-sts-ocean disabled:opacity-60; }
</style>
