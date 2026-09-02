<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  approveService,
  archiveService,
  type Category,
  type ConfirmationType,
  createService,
  type FulfilmentModel,
  getService,
  listCategories,
  listProviders,
  listServices,
  type Provider,
  publishService,
  type Service,
  type ServiceMedia,
  type ServiceOption,
  type ServiceStatus,
  submitServiceForReview,
  suspendService,
  TravelMarketplaceApiError,
  updateService,
  type UpsertServiceBody,
} from "../composables/useTravelMarketplaceApi";

useHead({ title: "Services · Sharm To Go" });

const session = ref<AuthSession | null>(null);
const services = ref<Service[]>([]);
const categories = ref<Category[]>([]);
const providers = ref<Provider[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const page = ref(0);
const hasNextPage = ref(false);
const statusFilter = ref<ServiceStatus | "">("");

const canManage = () => hasPermission(session.value, "service:manage");
const canView = () => hasPermission(session.value, "service:view");

function categoryName(categoryId: string): string {
  return categories.value.find((category) => category.id === categoryId)?.name.en ?? categoryId;
}

function providerName(providerId?: string): string {
  if (!providerId) return "—";
  return providers.value.find((provider) => provider.id === providerId)?.name ?? providerId;
}

function handleApiError(error: unknown) {
  if (error instanceof TravelMarketplaceApiError && error.status === 401) {
    clearAuthSession();
    session.value = null;
  }
}

function errorText(error: unknown): string {
  if (error instanceof TravelMarketplaceApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this.";
    if (error.errorCode === "already_archived") return "That service is already archived.";
    if (error.errorCode === "category_not_found") return "That category doesn't exist.";
    if (error.errorCode === "provider_not_found") return "That provider doesn't exist.";
    if (error.errorCode === "invalid_transition") return "That transition isn't valid from the service's current status.";
    if (error.errorCode === "missing_publishable_option") return "Add at least one option before publishing.";
    if (error.errorCode === "missing_rights_cleared_media") return "Add at least one photo with rights evidence before publishing.";
    if (error.errorCode === "would_invalidate_published_content") {
      return "This service is live — it can't be saved with no options or no media while published/suspended.";
    }
    if (error.status === 404) return "Not found.";
    if (error.status === 400) return "Check the form for an invalid value.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadReferenceData() {
  if (!session.value) return;
  try {
    categories.value = await listCategories(session.value.token, { status: "ACTIVE" });
  } catch {
    categories.value = [];
  }
  try {
    providers.value = await listProviders(session.value.token, { status: "ACTIVE" });
  } catch {
    providers.value = [];
  }
}

async function loadServices() {
  if (!session.value) return;
  if (!canView()) {
    services.value = [];
    hasNextPage.value = false;
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const result = await listServices(session.value.token, { status: statusFilter.value || undefined, page: page.value });
    services.value = result;
    hasNextPage.value = result.length === 50;
    listState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

function nextPage() {
  page.value += 1;
  loadServices();
}

function previousPage() {
  if (page.value === 0) return;
  page.value -= 1;
  loadServices();
}

function runFilter() {
  page.value = 0;
  loadServices();
}

function blankOption(): ServiceOption {
  return { label: { en: "", ar: "" }, durationMinutes: undefined, maxParticipants: 1, priceAmount: "0.00", priceCurrency: "EGP", priceBasis: "PER_PERSON" };
}

function blankMedia(): ServiceMedia {
  return { assetReference: "", rightsEvidence: "", locale: "en" };
}

function blankForm() {
  return {
    categoryId: "",
    nameEn: "",
    nameAr: "",
    descriptionEn: "",
    descriptionAr: "",
    fulfilmentModel: "DIRECT" as FulfilmentModel,
    providerId: "",
    confirmationType: "INSTANT" as ConfirmationType,
    cancellationPolicyEn: "",
    cancellationPolicyAr: "",
    pickupInfoEn: "",
    pickupInfoAr: "",
    inclusionsEn: "",
    inclusionsAr: "",
    exclusionsEn: "",
    exclusionsAr: "",
    options: [blankOption()] as ServiceOption[],
    media: [blankMedia()] as ServiceMedia[],
  };
}

const form = ref(blankForm());
const editingServiceId = ref<string | null>(null);
const formState = ref<"idle" | "submitting" | "error">("idle");
const formError = ref("");

const actionState = ref<Record<string, "idle" | "submitting" | "error">>({});
const actionError = ref<Record<string, string>>({});

function addOption() {
  form.value.options = [...form.value.options, blankOption()];
}

function removeOption(index: number) {
  form.value.options = form.value.options.filter((_, i) => i !== index);
}

function addMedia() {
  form.value.media = [...form.value.media, blankMedia()];
}

function removeMedia(index: number) {
  form.value.media = form.value.media.filter((_, i) => i !== index);
}

async function startEdit(summary: Service) {
  if (!session.value) return;
  const service = await getService(session.value.token, summary.id);
  editingServiceId.value = service.id;
  form.value = {
    categoryId: service.categoryId,
    nameEn: service.name.en,
    nameAr: service.name.ar,
    descriptionEn: service.description.en,
    descriptionAr: service.description.ar,
    fulfilmentModel: service.fulfilmentModel,
    providerId: service.providerId ?? "",
    confirmationType: service.confirmationType,
    cancellationPolicyEn: service.cancellationPolicy.en,
    cancellationPolicyAr: service.cancellationPolicy.ar,
    pickupInfoEn: service.pickupInfo?.en ?? "",
    pickupInfoAr: service.pickupInfo?.ar ?? "",
    inclusionsEn: service.inclusions?.en ?? "",
    inclusionsAr: service.inclusions?.ar ?? "",
    exclusionsEn: service.exclusions?.en ?? "",
    exclusionsAr: service.exclusions?.ar ?? "",
    options: service.options.length > 0 ? service.options : [blankOption()],
    media: service.media.length > 0 ? service.media : [blankMedia()],
  };
  formState.value = "idle";
  formError.value = "";
  window.scrollTo({ top: document.body.scrollHeight, behavior: "smooth" });
}

function cancelEdit() {
  editingServiceId.value = null;
  form.value = blankForm();
  formState.value = "idle";
  formError.value = "";
}

function optionalText(en: string, ar: string): { en: string; ar: string } | undefined {
  return en.trim() !== "" || ar.trim() !== "" ? { en, ar } : undefined;
}

function buildRequestBody(): UpsertServiceBody {
  return {
    categoryId: form.value.categoryId,
    name: { en: form.value.nameEn, ar: form.value.nameAr },
    description: { en: form.value.descriptionEn, ar: form.value.descriptionAr },
    fulfilmentModel: form.value.fulfilmentModel,
    providerId: form.value.fulfilmentModel === "PARTNER" ? form.value.providerId || undefined : undefined,
    confirmationType: form.value.confirmationType,
    cancellationPolicy: { en: form.value.cancellationPolicyEn, ar: form.value.cancellationPolicyAr },
    pickupInfo: optionalText(form.value.pickupInfoEn, form.value.pickupInfoAr),
    inclusions: optionalText(form.value.inclusionsEn, form.value.inclusionsAr),
    exclusions: optionalText(form.value.exclusionsEn, form.value.exclusionsAr),
    options: form.value.options.filter((option) => option.label.en && option.label.ar),
    media: form.value.media.filter((media) => media.assetReference && media.rightsEvidence),
  };
}

async function submitForm() {
  if (!session.value) return;
  formState.value = "submitting";
  formError.value = "";
  try {
    const body = buildRequestBody();
    if (editingServiceId.value) {
      const updated = await updateService(session.value.token, editingServiceId.value, body);
      services.value = services.value.map((existing) => (existing.id === updated.id ? updated : existing));
    } else {
      const created = await createService(session.value.token, body);
      services.value = [created, ...services.value];
    }
    cancelEdit();
  } catch (error) {
    handleApiError(error);
    formState.value = "error";
    formError.value = errorText(error);
  }
}

type Transition = "submit-for-review" | "approve" | "publish" | "suspend" | "archive";

const transitionFns: Record<Transition, (token: string, id: string) => Promise<Service>> = {
  "submit-for-review": submitServiceForReview,
  approve: approveService,
  publish: publishService,
  suspend: suspendService,
  archive: archiveService,
};

function availableTransitions(status: ServiceStatus): Transition[] {
  if (status === "DRAFT") return ["submit-for-review", "archive"];
  if (status === "REVIEW") return ["approve", "archive"];
  if (status === "APPROVED") return ["publish", "archive"];
  if (status === "PUBLISHED") return ["suspend", "archive"];
  if (status === "SUSPENDED") return ["publish", "archive"];
  return [];
}

async function runTransition(service: Service, transition: Transition) {
  if (!session.value) return;
  if (transition === "archive" && !window.confirm(`Archive "${service.name.en}"? This is terminal.`)) return;

  actionState.value[service.id] = "submitting";
  actionError.value[service.id] = "";
  try {
    const updated = await transitionFns[transition](session.value.token, service.id);
    services.value = services.value.map((existing) => (existing.id === updated.id ? updated : existing));
    actionState.value[service.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[service.id] = "error";
    actionError.value[service.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) {
    loadReferenceData();
    loadServices();
  }
});
</script>

<template>
  <main class="min-h-screen bg-wego-canvas px-6 py-12 text-wego-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-5xl">
      <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">Sharm To Go</p>
      <h1 class="mt-4 text-3xl font-semibold tracking-tight">Services</h1>
      <p class="mt-2 text-sm text-wego-muted">Draft → Review → Approved → Published, with suspend/republish/archive.</p>

      <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
        <p>You need to sign in to view services.</p>
        <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
      </div>

      <template v-else>
        <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

        <section class="mt-8">
          <p v-if="!canView()" class="mt-3 text-sm text-wego-muted">
            Your account doesn't have permission to list services (service:view).
          </p>
          <template v-else>
            <div class="flex flex-wrap items-end gap-3">
              <div>
                <label for="statusFilter" class="block text-sm font-medium text-wego-muted">Status</label>
                <select
                  id="statusFilter"
                  v-model="statusFilter"
                  class="mt-2 rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
                  @change="runFilter"
                >
                  <option value="">All</option>
                  <option value="DRAFT">Draft</option>
                  <option value="REVIEW">Review</option>
                  <option value="APPROVED">Approved</option>
                  <option value="PUBLISHED">Published</option>
                  <option value="SUSPENDED">Suspended</option>
                  <option value="ARCHIVED">Archived</option>
                </select>
              </div>
            </div>

            <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
            <p v-else-if="listState === 'loaded' && services.length === 0 && page === 0" class="mt-3 text-sm text-wego-muted">
              No services yet.
            </p>
            <ul v-else-if="services.length > 0" class="mt-4 space-y-3">
              <li v-for="service in services" :key="service.id" class="rounded-wego-card border border-wego-border bg-wego-surface p-4">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p class="font-semibold">{{ service.name.en }} <span dir="rtl" class="text-wego-muted">· {{ service.name.ar }}</span></p>
                    <p class="mt-1 text-sm text-wego-muted">
                      {{ categoryName(service.categoryId) }} · {{ service.fulfilmentModel }}
                      <span v-if="service.fulfilmentModel === 'PARTNER'"> ({{ providerName(service.providerId) }})</span>
                      · {{ service.options.length }} option(s) · {{ service.media.length }} photo(s) · {{ service.status }}
                    </p>
                  </div>
                  <div v-if="canManage()" class="flex shrink-0 flex-wrap gap-2">
                    <WegoButton type="button" variant="secondary" @click="startEdit(service)">Edit</WegoButton>
                    <WegoButton
                      v-for="transition in availableTransitions(service.status)"
                      :key="transition"
                      type="button"
                      variant="secondary"
                      :disabled="actionState[service.id] === 'submitting'"
                      @click="runTransition(service, transition)"
                    >
                      {{ transition === "submit-for-review" ? "Submit for review" : transition }}
                    </WegoButton>
                  </div>
                </div>
                <WegoAlert v-if="actionState[service.id] === 'error'" variant="danger" class="mt-2">
                  {{ actionError[service.id] }}
                </WegoAlert>
              </li>
            </ul>

            <div class="mt-4 flex items-center gap-3">
              <WegoButton type="button" variant="secondary" :disabled="page === 0" @click="previousPage">Previous</WegoButton>
              <span class="text-sm text-wego-muted">Page {{ page + 1 }}</span>
              <WegoButton type="button" variant="secondary" :disabled="!hasNextPage" @click="nextPage">Next</WegoButton>
            </div>
          </template>
        </section>

        <section v-if="canManage()" class="mt-10 rounded-wego-card border border-wego-border bg-wego-surface p-6">
          <h2 class="text-xl font-semibold">{{ editingServiceId ? "Edit service" : "New service" }}</h2>
          <form class="mt-6 space-y-5" @submit.prevent="submitForm">
            <div>
              <label for="categoryId" class="block text-sm font-medium text-wego-muted">Category</label>
              <select
                id="categoryId"
                v-model="form.categoryId"
                required
                class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
              >
                <option value="" disabled>Select a category</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name.en }}</option>
              </select>
            </div>

            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="nameEn" v-model="form.nameEn" label="Name (English)" required />
              <WegoInput id="nameAr" v-model="form.nameAr" label="Name (Arabic)" required dir="rtl" />
            </div>
            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="descriptionEn" v-model="form.descriptionEn" label="Description (English)" required />
              <WegoInput id="descriptionAr" v-model="form.descriptionAr" label="Description (Arabic)" required dir="rtl" />
            </div>

            <div class="grid gap-5 sm:grid-cols-2">
              <div>
                <label for="fulfilmentModel" class="block text-sm font-medium text-wego-muted">Fulfilment model</label>
                <select
                  id="fulfilmentModel"
                  v-model="form.fulfilmentModel"
                  class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
                >
                  <option value="DIRECT">Direct (Sharm To Go)</option>
                  <option value="PARTNER">Partner</option>
                </select>
              </div>
              <div v-if="form.fulfilmentModel === 'PARTNER'">
                <label for="providerId" class="block text-sm font-medium text-wego-muted">Provider</label>
                <select
                  id="providerId"
                  v-model="form.providerId"
                  required
                  class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
                >
                  <option value="" disabled>Select a provider</option>
                  <option v-for="provider in providers" :key="provider.id" :value="provider.id">{{ provider.name }}</option>
                </select>
              </div>
            </div>

            <div>
              <label for="confirmationType" class="block text-sm font-medium text-wego-muted">Confirmation</label>
              <select
                id="confirmationType"
                v-model="form.confirmationType"
                class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
              >
                <option value="INSTANT">Instant</option>
                <option value="STAFF_REVIEW">Staff review</option>
              </select>
            </div>

            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="cancellationPolicyEn" v-model="form.cancellationPolicyEn" label="Cancellation policy (English)" required />
              <WegoInput id="cancellationPolicyAr" v-model="form.cancellationPolicyAr" label="Cancellation policy (Arabic)" required dir="rtl" />
            </div>
            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="pickupInfoEn" v-model="form.pickupInfoEn" label="Pickup info (English, optional)" />
              <WegoInput id="pickupInfoAr" v-model="form.pickupInfoAr" label="Pickup info (Arabic, optional)" dir="rtl" />
            </div>
            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="inclusionsEn" v-model="form.inclusionsEn" label="Inclusions (English, optional)" />
              <WegoInput id="inclusionsAr" v-model="form.inclusionsAr" label="Inclusions (Arabic, optional)" dir="rtl" />
            </div>
            <div class="grid gap-5 sm:grid-cols-2">
              <WegoInput id="exclusionsEn" v-model="form.exclusionsEn" label="Exclusions (English, optional)" />
              <WegoInput id="exclusionsAr" v-model="form.exclusionsAr" label="Exclusions (Arabic, optional)" dir="rtl" />
            </div>

            <div>
              <div class="flex items-center justify-between">
                <span class="block text-sm font-medium text-wego-muted">Options (at least one needed to publish)</span>
                <WegoButton type="button" variant="secondary" @click="addOption">Add option</WegoButton>
              </div>
              <div v-for="(option, index) in form.options" :key="index" class="mt-3 grid gap-3 rounded-wego-control border border-wego-border p-3 sm:grid-cols-6">
                <WegoInput :id="`opt-label-en-${index}`" v-model="option.label.en" label="Label (EN)" />
                <WegoInput :id="`opt-label-ar-${index}`" v-model="option.label.ar" label="Label (AR)" dir="rtl" />
                <WegoInput
                  :id="`opt-duration-${index}`"
                  :model-value="option.durationMinutes ? String(option.durationMinutes) : ''"
                  label="Duration (min, optional)"
                  type="number"
                  @update:model-value="(value) => (option.durationMinutes = value ? Number(value) : undefined)"
                />
                <WegoInput
                  :id="`opt-max-${index}`"
                  :model-value="String(option.maxParticipants)"
                  label="Max participants"
                  type="number"
                  @update:model-value="(value) => (option.maxParticipants = Number(value || 1))"
                />
                <WegoInput :id="`opt-price-${index}`" v-model="option.priceAmount" label="Price (EGP)" />
                <div class="flex items-end gap-2">
                  <select v-model="option.priceBasis" class="w-full rounded-wego-control border border-wego-border bg-wego-surface px-3 py-2.5 text-wego-ink">
                    <option value="PER_PERSON">Per person</option>
                    <option value="PER_GROUP">Per group</option>
                    <option value="PER_VEHICLE">Per vehicle</option>
                    <option value="FLAT">Flat</option>
                  </select>
                  <WegoButton type="button" variant="secondary" @click="removeOption(index)">Remove</WegoButton>
                </div>
              </div>
            </div>

            <div>
              <div class="flex items-center justify-between">
                <span class="block text-sm font-medium text-wego-muted">Media (at least one rights-cleared photo needed to publish)</span>
                <WegoButton type="button" variant="secondary" @click="addMedia">Add photo</WegoButton>
              </div>
              <div v-for="(media, index) in form.media" :key="index" class="mt-3 grid gap-3 rounded-wego-control border border-wego-border p-3 sm:grid-cols-4">
                <WegoInput :id="`media-ref-${index}`" v-model="media.assetReference" label="Asset reference" />
                <WegoInput :id="`media-rights-${index}`" v-model="media.rightsEvidence" label="Rights evidence" />
                <WegoInput :id="`media-locale-${index}`" v-model="media.locale" label="Locale" />
                <WegoButton type="button" variant="secondary" @click="removeMedia(index)">Remove</WegoButton>
              </div>
            </div>

            <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

            <div class="flex gap-3">
              <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
                {{ editingServiceId ? "Save changes" : "Create service" }}
              </WegoButton>
              <WegoButton v-if="editingServiceId" type="button" variant="secondary" @click="cancelEdit">Cancel</WegoButton>
            </div>
          </form>
        </section>
      </template>
    </div>
  </main>
</template>
