<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoInput, WegoPageHeader, WegoPanel } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  archiveCategory,
  type Category,
  type CategoryStatus,
  createCategory,
  listCategories,
  TravelMarketplaceApiError,
  updateCategory,
  type UpsertCategoryBody,
} from "../composables/useTravelMarketplaceApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Categories · Sharm To Go" });

const session = ref<AuthSession | null>(null);
const categories = ref<Category[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const statusFilter = ref<CategoryStatus | "">("ACTIVE");

function blankForm() {
  return { code: "", nameEn: "", nameAr: "", descriptionEn: "", descriptionAr: "", displayOrder: "0" };
}

const form = ref(blankForm());
// null while creating (code is editable); the category's id while editing
// (code becomes read-only — see UpdateCategoryService's own code-immutable rule).
const editingCategoryId = ref<string | null>(null);
const formState = ref<"idle" | "submitting" | "error">("idle");
const formError = ref("");

const archiveState = ref<Record<string, "idle" | "submitting" | "error">>({});
const archiveError = ref<Record<string, string>>({});

const canManage = () => hasPermission(session.value, "service:manage");
const canView = () => hasPermission(session.value, "service:view");

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
    if (error.errorCode === "already_archived") return "That category is already archived.";
    if (error.errorCode === "duplicate_code") return "A category with this code already exists.";
    if (error.status === 404) return "Not found.";
    if (error.status === 400) return "Check the form — the code must be lowercase-kebab-case, and both English and Arabic names are required.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadCategories() {
  if (!session.value) return;
  if (!canView()) {
    categories.value = [];
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    categories.value = await listCategories(session.value.token, { status: statusFilter.value || undefined });
    listState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

function runFilter() {
  loadCategories();
}

function startEdit(category: Category) {
  editingCategoryId.value = category.id;
  form.value = {
    code: category.code,
    nameEn: category.name.en,
    nameAr: category.name.ar,
    descriptionEn: category.description?.en ?? "",
    descriptionAr: category.description?.ar ?? "",
    displayOrder: String(category.displayOrder),
  };
  formState.value = "idle";
  formError.value = "";
}

function cancelEdit() {
  editingCategoryId.value = null;
  form.value = blankForm();
  formState.value = "idle";
  formError.value = "";
}

function buildRequestBody(): UpsertCategoryBody {
  const hasDescription = form.value.descriptionEn.trim() !== "" || form.value.descriptionAr.trim() !== "";
  return {
    code: form.value.code,
    name: { en: form.value.nameEn, ar: form.value.nameAr },
    description: hasDescription ? { en: form.value.descriptionEn, ar: form.value.descriptionAr } : undefined,
    displayOrder: Number(form.value.displayOrder || 0),
  };
}

async function submitForm() {
  if (!session.value) return;
  formState.value = "submitting";
  formError.value = "";
  try {
    const body = buildRequestBody();
    if (editingCategoryId.value) {
      const updated = await updateCategory(session.value.token, editingCategoryId.value, body);
      categories.value = categories.value.map((existing) => (existing.id === updated.id ? updated : existing));
    } else {
      const created = await createCategory(session.value.token, body);
      categories.value = [...categories.value, created];
    }
    cancelEdit();
  } catch (error) {
    handleApiError(error);
    formState.value = "error";
    formError.value = errorText(error);
  }
}

async function submitArchive(category: Category) {
  if (!session.value) return;
  if (!window.confirm(`Archive "${category.name.en}"? It stops appearing in the active list, but nothing is deleted.`)) return;

  archiveState.value[category.id] = "submitting";
  archiveError.value[category.id] = "";
  try {
    const updated = await archiveCategory(session.value.token, category.id);
    if (statusFilter.value === "ACTIVE") {
      categories.value = categories.value.filter((existing) => existing.id !== updated.id);
    } else {
      categories.value = categories.value.map((existing) => (existing.id === updated.id ? updated : existing));
    }
    archiveState.value[category.id] = "idle";
  } catch (error) {
    handleApiError(error);
    archiveState.value[category.id] = "error";
    archiveError.value[category.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadCategories();
});
</script>

<template>
  <WegoPageHeader
    eyebrow="Sharm To Go"
    title="Categories"
    description="Staff-defined navigation groupings for services. None are pre-seeded."
  />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view categories.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to list categories (service:view).
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
              <option value="ACTIVE">Active</option>
              <option value="ARCHIVED">Archived</option>
              <option value="">All</option>
            </select>
          </div>
        </div>

        <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
        <p v-else-if="listState === 'loaded' && categories.length === 0" class="mt-3 text-sm text-wego-muted">
          No categories yet.
        </p>
        <ul v-else-if="categories.length > 0" class="mt-4 space-y-3">
          <li v-for="category in categories" :key="category.id" class="rounded-wego-control border border-wego-border p-4">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="font-semibold">{{ category.name.en }} <span dir="rtl" class="text-wego-muted">· {{ category.name.ar }}</span></p>
                <p class="mt-1 text-sm text-wego-muted">
                  <code>{{ category.code }}</code> · order {{ category.displayOrder }}
                </p>
                <WegoBadge :tone="category.status === 'ACTIVE' ? 'success' : 'neutral'" class="mt-2">{{ category.status }}</WegoBadge>
              </div>
              <div v-if="canManage()" class="flex shrink-0 gap-2">
                <WegoButton type="button" variant="secondary" @click="startEdit(category)">Edit</WegoButton>
                <WegoButton
                  v-if="category.status === 'ACTIVE'"
                  type="button"
                  variant="secondary"
                  :disabled="archiveState[category.id] === 'submitting'"
                  @click="submitArchive(category)"
                >
                  Archive
                </WegoButton>
              </div>
            </div>
            <WegoAlert v-if="archiveState[category.id] === 'error'" variant="danger" class="mt-2">
              {{ archiveError[category.id] }}
            </WegoAlert>
          </li>
        </ul>
      </template>
    </WegoPanel>

    <WegoPanel v-if="canManage()" class="mt-10" :title="editingCategoryId ? 'Edit category' : 'New category'">
      <form class="space-y-5" @submit.prevent="submitForm">
        <WegoInput
          id="code"
          v-model="form.code"
          label="Code (lowercase-kebab-case)"
          required
          :disabled="editingCategoryId !== null"
        />
        <p v-if="editingCategoryId" class="text-xs text-wego-muted">The code is immutable after creation.</p>
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="nameEn" v-model="form.nameEn" label="Name (English)" required />
          <WegoInput id="nameAr" v-model="form.nameAr" label="Name (Arabic)" required dir="rtl" />
        </div>
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="descriptionEn" v-model="form.descriptionEn" label="Description (English, optional)" />
          <WegoInput id="descriptionAr" v-model="form.descriptionAr" label="Description (Arabic, optional)" dir="rtl" />
        </div>
        <WegoInput id="displayOrder" v-model="form.displayOrder" label="Display order" type="number" />

        <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

        <div class="flex gap-3">
          <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
            {{ editingCategoryId ? "Save changes" : "Create category" }}
          </WegoButton>
          <WegoButton v-if="editingCategoryId" type="button" variant="secondary" @click="cancelEdit">Cancel</WegoButton>
        </div>
      </form>
    </WegoPanel>
  </template>
</template>
