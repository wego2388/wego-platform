<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoInput, WegoPageHeader, WegoPanel } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  createRole,
  IdentityAdminApiError,
  listPermissions,
  listRoles,
  type Permission,
  type Role,
  updateRolePermissions,
} from "../composables/useIdentityAdminApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Roles & Permissions · Wego Platform" });

const session = ref<AuthSession | null>(null);
const roles = ref<Role[]>([]);
const permissions = ref<Permission[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");

const canView = () => hasPermission(session.value, "identity:role-view");
const canManage = () => hasPermission(session.value, "identity:role-manage");

function handleApiError(error: unknown) {
  if (error instanceof IdentityAdminApiError && error.status === 401) {
    clearAuthSession();
    session.value = null;
  }
}

function errorText(error: unknown): string {
  if (error instanceof IdentityAdminApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this.";
    if (error.errorCode === "role_already_exists") return "A role with that code already exists.";
    if (error.errorCode.startsWith("unknown_permission:")) return `Unknown permission: ${error.errorCode.split(":")[1]}`;
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadAll() {
  if (!session.value) return;
  if (!canView()) {
    roles.value = [];
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const [loadedRoles, loadedPermissions] = await Promise.all([listRoles(session.value.token), listPermissions(session.value.token)]);
    roles.value = loadedRoles;
    permissions.value = loadedPermissions;
    listState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

const editingCodeFor = ref<string | null>(null);
const editingPermissions = ref<string[]>([]);
const rowState = ref<Record<string, "idle" | "submitting" | "error">>({});
const rowError = ref<Record<string, string>>({});

function startEditPermissions(role: Role) {
  editingCodeFor.value = role.code;
  editingPermissions.value = [...role.permissions];
}

function cancelEditPermissions() {
  editingCodeFor.value = null;
}

async function submitPermissions(role: Role) {
  if (!session.value) return;
  rowState.value[role.code] = "submitting";
  rowError.value[role.code] = "";
  try {
    const updated = await updateRolePermissions(session.value.token, role.code, editingPermissions.value);
    roles.value = roles.value.map((existing) => (existing.code === updated.code ? updated : existing));
    rowState.value[role.code] = "idle";
    editingCodeFor.value = null;
  } catch (error) {
    handleApiError(error);
    rowState.value[role.code] = "error";
    rowError.value[role.code] = errorText(error);
  }
}

const createForm = ref({ code: "", description: "", permissionCodes: [] as string[] });
const createState = ref<"idle" | "submitting" | "error">("idle");
const createError = ref("");

async function submitCreate() {
  if (!session.value) return;
  createState.value = "submitting";
  createError.value = "";
  try {
    const created = await createRole(session.value.token, {
      code: createForm.value.code,
      description: createForm.value.description,
      permissionCodes: createForm.value.permissionCodes,
    });
    roles.value = [...roles.value, created];
    createForm.value = { code: "", description: "", permissionCodes: [] };
    createState.value = "idle";
  } catch (error) {
    handleApiError(error);
    createState.value = "error";
    createError.value = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadAll();
});
</script>

<template>
  <WegoPageHeader title="Roles &amp; Permissions" description="What each role can do across the platform." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view roles.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel title="Roles" class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to view roles (identity:role-view).
      </p>
      <template v-else>
        <p v-if="listState === 'loading'" class="text-sm text-wego-muted">Loading…</p>
        <ul v-else class="space-y-3">
          <li v-for="role in roles" :key="role.code" class="rounded-wego-control border border-wego-border p-4">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="font-semibold">{{ role.code }}</p>
                <p class="mt-1 text-sm text-wego-muted">{{ role.description }}</p>
                <p class="mt-2 flex flex-wrap gap-2">
                  <WegoBadge v-for="permission in role.permissions" :key="permission" tone="neutral">{{ permission }}</WegoBadge>
                </p>
              </div>
              <WegoButton v-if="canManage()" type="button" variant="secondary" @click="startEditPermissions(role)">
                Edit permissions
              </WegoButton>
            </div>
            <WegoAlert v-if="rowState[role.code] === 'error'" variant="danger" class="mt-2">{{ rowError[role.code] }}</WegoAlert>

            <div v-if="editingCodeFor === role.code" class="mt-4 rounded-wego-control border border-wego-border p-4">
              <p class="text-sm font-medium text-wego-muted">Permissions for {{ role.code }}</p>
              <div class="mt-2 grid gap-2 sm:grid-cols-2">
                <label v-for="permission in permissions" :key="permission.code" class="flex items-center gap-2 text-sm">
                  <input v-model="editingPermissions" type="checkbox" :value="permission.code" >
                  {{ permission.code }}
                </label>
              </div>
              <div class="mt-3 flex gap-2">
                <WegoButton type="button" :disabled="rowState[role.code] === 'submitting'" @click="submitPermissions(role)">
                  Save permissions
                </WegoButton>
                <WegoButton type="button" variant="secondary" @click="cancelEditPermissions">Cancel</WegoButton>
              </div>
            </div>
          </li>
        </ul>
      </template>
    </WegoPanel>

    <WegoPanel v-if="canManage()" title="New role" class="mt-8">
      <form class="space-y-5" @submit.prevent="submitCreate">
        <WegoInput id="newRoleCode" v-model="createForm.code" label="Code (lowercase-with-hyphens)" required />
        <WegoInput id="newRoleDescription" v-model="createForm.description" label="Description" required />
        <div>
          <span class="block text-sm font-medium text-wego-muted">Permissions</span>
          <div class="mt-2 grid gap-2 sm:grid-cols-2">
            <label v-for="permission in permissions" :key="permission.code" class="flex items-center gap-2 text-sm">
              <input v-model="createForm.permissionCodes" type="checkbox" :value="permission.code" >
              {{ permission.code }}
            </label>
          </div>
        </div>
        <WegoAlert v-if="createState === 'error'" variant="danger">{{ createError }}</WegoAlert>
        <WegoButton type="submit" :disabled="createState === 'submitting'" :loading="createState === 'submitting'">Create role</WegoButton>
      </form>
    </WegoPanel>
  </template>
</template>
