<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  assignUserRoles,
  createUser,
  disableUser,
  enableUser,
  IdentityAdminApiError,
  listRoles,
  listUsers,
  resetUserPassword,
  type Role,
  type StaffUser,
} from "../composables/useIdentityAdminApi";

useHead({ title: "Accounts · Wego Platform" });

const session = ref<AuthSession | null>(null);
const users = ref<StaffUser[]>([]);
const roles = ref<Role[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");

const canView = () => hasPermission(session.value, "identity:user-view");
const canManage = () => hasPermission(session.value, "identity:user-manage");

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
    if (error.errorCode === "email_already_in_use") return "That email is already used by another account.";
    if (error.errorCode === "cannot_disable_self") return "You can't disable your own account.";
    if (error.errorCode === "cannot_change_own_roles") return "You can't change your own roles — ask another admin.";
    if (error.errorCode.startsWith("unknown_role:")) return `Unknown role: ${error.errorCode.split(":")[1]}`;
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadAll() {
  if (!session.value) return;
  if (!canView()) {
    users.value = [];
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const [loadedUsers, loadedRoles] = await Promise.all([listUsers(session.value.token), listRoles(session.value.token)]);
    users.value = loadedUsers;
    roles.value = loadedRoles;
    listState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

const createForm = ref({ email: "", password: "", roleCodes: [] as string[] });
const createState = ref<"idle" | "submitting" | "error">("idle");
const createError = ref("");

async function submitCreate() {
  if (!session.value) return;
  createState.value = "submitting";
  createError.value = "";
  try {
    const created = await createUser(session.value.token, {
      email: createForm.value.email,
      password: createForm.value.password,
      roleCodes: createForm.value.roleCodes,
    });
    users.value = [created, ...users.value];
    createForm.value = { email: "", password: "", roleCodes: [] };
    createState.value = "idle";
  } catch (error) {
    handleApiError(error);
    createState.value = "error";
    createError.value = errorText(error);
  }
}

const rowState = ref<Record<string, "idle" | "submitting" | "error">>({});
const rowError = ref<Record<string, string>>({});

async function toggleStatus(user: StaffUser) {
  if (!session.value) return;
  rowState.value[user.id] = "submitting";
  rowError.value[user.id] = "";
  try {
    const updated = user.status === "ACTIVE" ? await disableUser(session.value.token, user.id) : await enableUser(session.value.token, user.id);
    users.value = users.value.map((existing) => (existing.id === updated.id ? updated : existing));
    rowState.value[user.id] = "idle";
  } catch (error) {
    handleApiError(error);
    rowState.value[user.id] = "error";
    rowError.value[user.id] = errorText(error);
  }
}

async function promptResetPassword(user: StaffUser) {
  if (!session.value) return;
  // A prompt(), not a full form: matches divers.vue's window.confirm() for archive — a
  // right-sized, no-extra-screen action for a small staff, same discipline as the admin
  // reset flow itself (no email/token round trip either — see ResetUserPasswordService).
  const newPassword = window.prompt(`New password for ${user.email} (at least 12 characters):`);
  if (!newPassword) return;
  rowState.value[user.id] = "submitting";
  rowError.value[user.id] = "";
  try {
    await resetUserPassword(session.value.token, user.id, newPassword);
    rowState.value[user.id] = "idle";
    window.alert(`Password reset. Tell ${user.email} their new password directly.`);
  } catch (error) {
    handleApiError(error);
    rowState.value[user.id] = "error";
    rowError.value[user.id] = errorText(error);
  }
}

const editingRolesFor = ref<string | null>(null);
const editingRoles = ref<string[]>([]);

function startEditRoles(user: StaffUser) {
  editingRolesFor.value = user.id;
  editingRoles.value = [...user.roles];
}

function cancelEditRoles() {
  editingRolesFor.value = null;
}

async function submitRoles(user: StaffUser) {
  if (!session.value) return;
  rowState.value[user.id] = "submitting";
  rowError.value[user.id] = "";
  try {
    const updated = await assignUserRoles(session.value.token, user.id, editingRoles.value);
    users.value = users.value.map((existing) => (existing.id === updated.id ? updated : existing));
    rowState.value[user.id] = "idle";
    editingRolesFor.value = null;
  } catch (error) {
    handleApiError(error);
    rowState.value[user.id] = "error";
    rowError.value[user.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadAll();
});
</script>

<template>
  <main class="min-h-screen bg-wego-canvas px-6 py-12 text-wego-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-4xl">
      <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">Wego Platform</p>
      <h1 class="mt-4 text-3xl font-semibold tracking-tight">Accounts</h1>

      <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
        <p>You need to sign in to view staff accounts.</p>
        <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
      </div>

      <template v-else>
        <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

        <section class="mt-8">
          <h2 class="text-xl font-semibold">Staff accounts</h2>
          <p v-if="!canView()" class="mt-3 text-sm text-wego-muted">
            Your account doesn't have permission to view staff accounts (identity:user-view).
          </p>
          <template v-else>
            <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
            <p v-else-if="listState === 'loaded' && users.length === 0" class="mt-3 text-sm text-wego-muted">No staff accounts yet.</p>
            <ul v-else class="mt-4 space-y-3">
              <li v-for="user in users" :key="user.id" class="rounded-wego-card border border-wego-border bg-wego-surface p-4">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p class="font-semibold">{{ user.email }}</p>
                    <p class="mt-1 text-sm text-wego-muted">{{ user.status }} · {{ user.roles.join(", ") || "no roles" }}</p>
                  </div>
                  <div v-if="canManage()" class="flex shrink-0 flex-wrap gap-2">
                    <WegoButton type="button" variant="secondary" @click="startEditRoles(user)">Change roles</WegoButton>
                    <WegoButton type="button" variant="secondary" @click="promptResetPassword(user)">Reset password</WegoButton>
                    <WegoButton
                      type="button"
                      variant="secondary"
                      :disabled="rowState[user.id] === 'submitting'"
                      @click="toggleStatus(user)"
                    >
                      {{ user.status === "ACTIVE" ? "Disable" : "Enable" }}
                    </WegoButton>
                  </div>
                </div>
                <WegoAlert v-if="rowState[user.id] === 'error'" variant="danger" class="mt-2">{{ rowError[user.id] }}</WegoAlert>

                <div v-if="editingRolesFor === user.id" class="mt-4 rounded-wego-control border border-wego-border p-4">
                  <p class="text-sm font-medium text-wego-muted">Roles for {{ user.email }}</p>
                  <div class="mt-2 flex flex-wrap gap-3">
                    <label v-for="role in roles" :key="role.code" class="flex items-center gap-2 text-sm">
                      <input v-model="editingRoles" type="checkbox" :value="role.code" >
                      {{ role.code }}
                    </label>
                  </div>
                  <div class="mt-3 flex gap-2">
                    <WegoButton type="button" :disabled="rowState[user.id] === 'submitting'" @click="submitRoles(user)">Save roles</WegoButton>
                    <WegoButton type="button" variant="secondary" @click="cancelEditRoles">Cancel</WegoButton>
                  </div>
                </div>
              </li>
            </ul>
          </template>
        </section>

        <section v-if="canManage()" class="mt-10 rounded-wego-card border border-wego-border bg-wego-surface p-6">
          <h2 class="text-xl font-semibold">New staff account</h2>
          <form class="mt-6 space-y-5" @submit.prevent="submitCreate">
            <WegoInput id="newUserEmail" v-model="createForm.email" label="Email" type="email" required />
            <WegoInput id="newUserPassword" v-model="createForm.password" label="Password (at least 12 characters)" type="password" required />
            <div>
              <span class="block text-sm font-medium text-wego-muted">Roles</span>
              <div class="mt-2 flex flex-wrap gap-3">
                <label v-for="role in roles" :key="role.code" class="flex items-center gap-2 text-sm">
                  <input v-model="createForm.roleCodes" type="checkbox" :value="role.code" >
                  {{ role.code }}
                </label>
              </div>
            </div>
            <WegoAlert v-if="createState === 'error'" variant="danger">{{ createError }}</WegoAlert>
            <WegoButton type="submit" :disabled="createState === 'submitting'" :loading="createState === 'submitting'">
              Create account
            </WegoButton>
          </form>
        </section>
      </template>
    </div>
  </main>
</template>
