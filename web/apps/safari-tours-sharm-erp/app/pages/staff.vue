<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoDialog, WegoInput } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";
import {
  assignUserRoles,
  createRole,
  createUser,
  disableUser,
  enableUser,
  IdentityAdminApiError,
  listPermissions,
  listRoles,
  listUsers,
  resetUserPassword,
  updateRolePermissions,
  type Permission,
  type Role,
  type StaffUser,
} from "../composables/useIdentityAdminApi";
import { groupPermissions, MIN_PASSWORD_LENGTH, staffErrorText } from "../composables/useStaffAdmin";

useHead({ title: "Staff · Safari Tours Sharm" });

const router = useRouter();
const session = ref<AuthSession | null>(null);
const users = ref<StaffUser[]>([]);
const roles = ref<Role[]>([]);
const permissions = ref<Permission[]>([]);
const state = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error = ref("");

const canViewUsers = computed(() => hasPermission(session.value, "identity:user-view"));
const canManageUsers = computed(() => hasPermission(session.value, "identity:user-manage"));
const canViewRoles = computed(() => hasPermission(session.value, "identity:role-view"));
const canManageRoles = computed(() => hasPermission(session.value, "identity:role-manage"));
const permissionGroups = computed(() => groupPermissions(permissions.value));

function handleApiError(err: unknown) {
  if (err instanceof IdentityAdminApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value) return;
  const token = session.value.token;
  state.value = "loading";
  error.value = "";
  try {
    // GET /roles requires role-view on the server; without it, role
    // assignment is hidden instead of failing the whole page.
    const [loadedUsers, loadedRoles, loadedPermissions] = await Promise.all([
      canViewUsers.value ? listUsers(token) : Promise.resolve([] as StaffUser[]),
      canViewRoles.value ? listRoles(token) : Promise.resolve([] as Role[]),
      canViewRoles.value ? listPermissions(token) : Promise.resolve([] as Permission[]),
    ]);
    users.value = loadedUsers;
    roles.value = loadedRoles;
    permissions.value = loadedPermissions;
    state.value = "loaded";
  } catch (err) {
    handleApiError(err);
    error.value = staffErrorText(err);
    state.value = "error";
  }
}

// ── Accounts ────────────────────────────────────────────────────────────────

const rowState = ref<Record<string, "idle" | "submitting" | "error">>({});
const rowError = ref<Record<string, string>>({});
const rowNotice = ref<Record<string, string>>({});

function replaceUser(updated: StaffUser) {
  users.value = users.value.map((existing) => (existing.id === updated.id ? updated : existing));
}

async function runRowAction(user: StaffUser, action: () => Promise<void>) {
  rowState.value[user.id] = "submitting";
  rowError.value[user.id] = "";
  rowNotice.value[user.id] = "";
  try {
    await action();
    rowState.value[user.id] = "idle";
  } catch (err) {
    handleApiError(err);
    rowState.value[user.id] = "error";
    rowError.value[user.id] = staffErrorText(err);
  }
}

function toggleStatus(user: StaffUser) {
  const token = session.value?.token;
  if (!token) return;
  void runRowAction(user, async () => {
    replaceUser(user.status === "ACTIVE" ? await disableUser(token, user.id) : await enableUser(token, user.id));
  });
}

const editingRolesFor = ref<string | null>(null);
const editingRoles = ref<string[]>([]);

function startEditRoles(user: StaffUser) {
  editingRolesFor.value = user.id;
  editingRoles.value = [...user.roles];
}

function saveRoles(user: StaffUser) {
  const token = session.value?.token;
  if (!token) return;
  void runRowAction(user, async () => {
    replaceUser(await assignUserRoles(token, user.id, editingRoles.value));
    editingRolesFor.value = null;
  });
}

// The new password lives only while the dialog is open and is cleared on
// every close path; it is never logged or kept in page state afterwards.
const resettingFor = ref<StaffUser | null>(null);
const newPassword = ref("");

function closeReset() {
  resettingFor.value = null;
  newPassword.value = "";
}

function confirmReset() {
  const token = session.value?.token;
  const user = resettingFor.value;
  if (!token || !user || newPassword.value.length < MIN_PASSWORD_LENGTH) return;
  const password = newPassword.value;
  closeReset();
  void runRowAction(user, async () => {
    await resetUserPassword(token, user.id, password);
    rowNotice.value[user.id] = `Password reset. Tell ${user.email} the new password directly, not by email.`;
  });
}

const newUser = ref({ email: "", password: "", roleCodes: [] as string[] });
const createUserState = ref<"idle" | "submitting" | "error">("idle");
const createUserError = ref("");

async function submitNewUser() {
  if (!session.value) return;
  if (newUser.value.password.length < MIN_PASSWORD_LENGTH) {
    createUserState.value = "error";
    createUserError.value = `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`;
    return;
  }
  createUserState.value = "submitting";
  createUserError.value = "";
  try {
    const created = await createUser(session.value.token, { ...newUser.value });
    users.value = [created, ...users.value];
    newUser.value = { email: "", password: "", roleCodes: [] };
    createUserState.value = "idle";
  } catch (err) {
    handleApiError(err);
    createUserState.value = "error";
    createUserError.value = staffErrorText(err);
  }
}

// ── Roles ───────────────────────────────────────────────────────────────────

const editingPermissionsFor = ref<string | null>(null);
const editingPermissions = ref<string[]>([]);
const roleState = ref<Record<string, "idle" | "submitting" | "error">>({});
const roleError = ref<Record<string, string>>({});

function startEditPermissions(role: Role) {
  editingPermissionsFor.value = role.code;
  editingPermissions.value = [...role.permissions];
}

async function savePermissions(role: Role) {
  if (!session.value) return;
  roleState.value[role.code] = "submitting";
  roleError.value[role.code] = "";
  try {
    const updated = await updateRolePermissions(session.value.token, role.code, editingPermissions.value);
    roles.value = roles.value.map((existing) => (existing.code === updated.code ? updated : existing));
    editingPermissionsFor.value = null;
    roleState.value[role.code] = "idle";
  } catch (err) {
    handleApiError(err);
    roleState.value[role.code] = "error";
    roleError.value[role.code] = staffErrorText(err);
  }
}

const newRole = ref({ code: "", description: "", permissionCodes: [] as string[] });
const createRoleState = ref<"idle" | "submitting" | "error">("idle");
const createRoleError = ref("");

async function submitNewRole() {
  if (!session.value) return;
  createRoleState.value = "submitting";
  createRoleError.value = "";
  try {
    const created = await createRole(session.value.token, { ...newRole.value });
    roles.value = [...roles.value, created];
    newRole.value = { code: "", description: "", permissionCodes: [] };
    createRoleState.value = "idle";
  } catch (err) {
    handleApiError(err);
    createRoleState.value = "error";
    createRoleError.value = staffErrorText(err);
  }
}


onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  if (!canViewUsers.value && !canViewRoles.value) { void router.replace("/"); return; }
  void load();
});
</script>

<template>
  <main class="px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-5xl">
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">Staff</h1>
          <p class="text-sm text-sts-muted">Sign-in accounts, roles and what each role may do.</p>
        </div>
      </header>

      <WegoAlert v-if="state === 'error'" variant="danger" class="mt-6">{{ error }}</WegoAlert>
      <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted">Loading…</p>

      <template v-else-if="state === 'loaded'">
        <!-- Accounts -->
        <section v-if="canViewUsers" class="mt-8" aria-labelledby="accounts-heading">
          <h2 id="accounts-heading" class="text-lg font-semibold">Accounts</h2>
          <p v-if="users.length === 0" class="mt-3 text-sm text-sts-muted">No staff accounts yet.</p>
          <ul v-else class="mt-3 space-y-3">
            <li
              v-for="user in users"
              :key="user.id"
              class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm"
            >
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div class="min-w-0">
                  <div class="flex flex-wrap items-center gap-2">
                    <p class="break-all font-semibold">{{ user.email }}</p>
                    <WegoBadge :tone="user.status === 'ACTIVE' ? 'success' : 'neutral'">{{ user.status }}</WegoBadge>
                  </div>
                  <p class="mt-1 text-sm text-sts-muted">{{ user.roles.join(", ") || "No roles — cannot do anything yet" }}</p>
                </div>
                <div v-if="canManageUsers" class="flex shrink-0 flex-wrap gap-2">
                  <WegoButton v-if="canViewRoles" type="button" variant="secondary" size="sm" @click="startEditRoles(user)">
                    Change roles
                  </WegoButton>
                  <WegoButton type="button" variant="secondary" size="sm" @click="resettingFor = user">Reset password</WegoButton>
                  <WegoButton
                    type="button"
                    variant="secondary"
                    size="sm"
                    :disabled="rowState[user.id] === 'submitting'"
                    @click="toggleStatus(user)"
                  >
                    {{ user.status === "ACTIVE" ? "Disable" : "Enable" }}
                  </WegoButton>
                </div>
              </div>
              <WegoAlert v-if="rowState[user.id] === 'error'" variant="danger" class="mt-3">{{ rowError[user.id] }}</WegoAlert>
              <WegoAlert v-if="rowNotice[user.id]" variant="success" class="mt-3">{{ rowNotice[user.id] }}</WegoAlert>

              <fieldset v-if="editingRolesFor === user.id" class="mt-4 rounded-xl border border-sts-border p-4">
                <legend class="px-1 text-sm font-medium text-sts-muted">Roles for {{ user.email }}</legend>
                <div class="flex flex-wrap gap-4">
                  <label v-for="role in roles" :key="role.code" class="flex items-center gap-2 text-sm">
                    <input v-model="editingRoles" type="checkbox" :value="role.code">
                    {{ role.code }}
                  </label>
                </div>
                <div class="mt-3 flex gap-2">
                  <WegoButton type="button" size="sm" :disabled="rowState[user.id] === 'submitting'" @click="saveRoles(user)">Save roles</WegoButton>
                  <WegoButton type="button" variant="secondary" size="sm" @click="editingRolesFor = null">Cancel</WegoButton>
                </div>
              </fieldset>
            </li>
          </ul>

          <WegoDialog :open="resettingFor !== null" title="Reset password" @close="closeReset">
            <p class="text-sm text-sts-muted">
              New password for {{ resettingFor?.email }} (at least {{ MIN_PASSWORD_LENGTH }} characters).
            </p>
            <WegoInput
              id="resetPassword"
              v-model="newPassword"
              label="New password"
              type="password"
              class="mt-3"
              autocomplete="new-password"
            />
            <template #actions>
              <WegoButton type="button" variant="secondary" @click="closeReset">Cancel</WegoButton>
              <WegoButton type="button" :disabled="newPassword.length < MIN_PASSWORD_LENGTH" @click="confirmReset">
                Reset password
              </WegoButton>
            </template>
          </WegoDialog>

          <form
            v-if="canManageUsers"
            class="mt-6 space-y-4 rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm"
            @submit.prevent="submitNewUser"
          >
            <h3 class="font-semibold">New staff account</h3>
            <WegoInput id="newUserEmail" v-model="newUser.email" label="Email" type="email" autocomplete="off" required />
            <WegoInput
              id="newUserPassword"
              v-model="newUser.password"
              :label="`Password (at least ${MIN_PASSWORD_LENGTH} characters)`"
              type="password"
              autocomplete="new-password"
              required
            />
            <p v-if="!canViewRoles" class="text-xs text-sts-muted">
              Roles can be assigned by someone with role-view permission after the account exists.
            </p>
            <fieldset v-else>
              <legend class="text-sm font-medium text-sts-muted">Roles</legend>
              <div class="mt-2 flex flex-wrap gap-4">
                <label v-for="role in roles" :key="role.code" class="flex items-center gap-2 text-sm">
                  <input v-model="newUser.roleCodes" type="checkbox" :value="role.code">
                  {{ role.code }}
                </label>
              </div>
            </fieldset>
            <WegoAlert v-if="createUserState === 'error'" variant="danger">{{ createUserError }}</WegoAlert>
            <WegoButton type="submit" size="sm" :disabled="createUserState === 'submitting'">Create account</WegoButton>
          </form>
        </section>

        <!-- Roles -->
        <section v-if="canViewRoles" class="mt-10" aria-labelledby="roles-heading">
          <h2 id="roles-heading" class="text-lg font-semibold">Roles & permissions</h2>
          <ul class="mt-3 space-y-3">
            <li
              v-for="role in roles"
              :key="role.code"
              class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm"
            >
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="font-mono font-semibold">{{ role.code }}</p>
                  <p class="text-sm text-sts-muted">{{ role.description }}</p>
                  <p class="mt-1 text-xs text-sts-muted">{{ role.permissions.length }} permissions</p>
                </div>
                <WegoButton
                  v-if="canManageRoles && editingPermissionsFor !== role.code"
                  type="button"
                  variant="secondary"
                  size="sm"
                  @click="startEditPermissions(role)"
                >
                  Edit permissions
                </WegoButton>
              </div>
              <WegoAlert v-if="roleState[role.code] === 'error'" variant="danger" class="mt-3">{{ roleError[role.code] }}</WegoAlert>

              <div v-if="editingPermissionsFor === role.code" class="mt-4 space-y-4">
                <fieldset v-for="group in permissionGroups" :key="group.label" class="rounded-xl border border-sts-border p-4">
                  <legend class="px-1 text-sm font-medium text-sts-muted">{{ group.label }}</legend>
                  <label
                    v-for="permission in group.permissions"
                    :key="permission.code"
                    class="flex items-start gap-2 py-1 text-sm"
                  >
                    <input v-model="editingPermissions" type="checkbox" :value="permission.code" class="mt-1">
                    <span>
                      {{ permission.description }}
                      <span class="block font-mono text-xs text-sts-muted">{{ permission.code }}</span>
                    </span>
                  </label>
                </fieldset>
                <div class="flex gap-2">
                  <WegoButton type="button" size="sm" :disabled="roleState[role.code] === 'submitting'" @click="savePermissions(role)">
                    Save permissions
                  </WegoButton>
                  <WegoButton type="button" variant="secondary" size="sm" @click="editingPermissionsFor = null">Cancel</WegoButton>
                </div>
              </div>
            </li>
          </ul>

          <form
            v-if="canManageRoles"
            class="mt-6 space-y-4 rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm"
            @submit.prevent="submitNewRole"
          >
            <h3 class="font-semibold">New role</h3>
            <WegoInput id="newRoleCode" v-model="newRole.code" label="Code (e.g. front-desk)" required />
            <WegoInput id="newRoleDescription" v-model="newRole.description" label="Description" required />
            <fieldset v-for="group in permissionGroups" :key="group.label" class="rounded-xl border border-sts-border p-4">
              <legend class="px-1 text-sm font-medium text-sts-muted">{{ group.label }}</legend>
              <label
                v-for="permission in group.permissions"
                :key="permission.code"
                class="flex items-start gap-2 py-1 text-sm"
              >
                <input v-model="newRole.permissionCodes" type="checkbox" :value="permission.code" class="mt-1">
                <span>{{ permission.description }}</span>
              </label>
            </fieldset>
            <WegoAlert v-if="createRoleState === 'error'" variant="danger">{{ createRoleError }}</WegoAlert>
            <WegoButton type="submit" size="sm" :disabled="createRoleState === 'submitting'">Create role</WegoButton>
          </form>
        </section>
      </template>
    </div>
  </main>
</template>
