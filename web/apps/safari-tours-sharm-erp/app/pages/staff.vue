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
import { groupPermissions, MIN_PASSWORD_LENGTH } from "../composables/useStaffAdmin";
import { useErpLocale } from "../composables/useErpLocale";
import { staffErrorMessage, staffGroupKey } from "../utils/staffMessages";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import ErpLanguageSwitch from "../components/ErpLanguageSwitch.vue";

const { t, count } = useErpLocale();
useHead(() => ({ title: `${t("staff.title")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const users = ref<StaffUser[]>([]);
const roles = ref<Role[]>([]);
const permissions = ref<Permission[]>([]);
const state = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error = ref<ErpMessageDescriptor | null>(null);

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
  error.value = null;
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
    error.value = staffErrorMessage(err);
    state.value = "error";
  }
}

// ── Accounts ────────────────────────────────────────────────────────────────

const rowState = ref<Record<string, "idle" | "submitting" | "error">>({});
const rowError = ref<Record<string, ErpMessageDescriptor | null>>({});
const rowNotice = ref<Record<string, ErpMessageDescriptor | null>>({});

function replaceUser(updated: StaffUser) {
  users.value = users.value.map((existing) => (existing.id === updated.id ? updated : existing));
}

async function runRowAction(user: StaffUser, action: () => Promise<void>) {
  rowState.value[user.id] = "submitting";
  rowError.value[user.id] = null;
  rowNotice.value[user.id] = null;
  try {
    await action();
    rowState.value[user.id] = "idle";
  } catch (err) {
    handleApiError(err);
    rowState.value[user.id] = "error";
    rowError.value[user.id] = staffErrorMessage(err);
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
    rowNotice.value[user.id] = { key: "staff.resetDone", params: { email: user.email } };
  });
}

const newUser = ref({ email: "", password: "", roleCodes: [] as string[] });
const createUserState = ref<"idle" | "submitting" | "error">("idle");
const createUserError = ref<ErpMessageDescriptor | null>(null);

async function submitNewUser() {
  if (!session.value) return;
  if (newUser.value.password.length < MIN_PASSWORD_LENGTH) {
    createUserState.value = "error";
    createUserError.value = { key: "staff.passwordLength", params: { count: MIN_PASSWORD_LENGTH } };
    return;
  }
  createUserState.value = "submitting";
  createUserError.value = null;
  try {
    const created = await createUser(session.value.token, { ...newUser.value });
    users.value = [created, ...users.value];
    newUser.value = { email: "", password: "", roleCodes: [] };
    createUserState.value = "idle";
  } catch (err) {
    handleApiError(err);
    createUserState.value = "error";
    createUserError.value = staffErrorMessage(err);
  }
}

// ── Roles ───────────────────────────────────────────────────────────────────

const editingPermissionsFor = ref<string | null>(null);
const editingPermissions = ref<string[]>([]);
const roleState = ref<Record<string, "idle" | "submitting" | "error">>({});
const roleError = ref<Record<string, ErpMessageDescriptor | null>>({});

function startEditPermissions(role: Role) {
  editingPermissionsFor.value = role.code;
  editingPermissions.value = [...role.permissions];
}

async function savePermissions(role: Role) {
  if (!session.value) return;
  roleState.value[role.code] = "submitting";
  roleError.value[role.code] = null;
  try {
    const updated = await updateRolePermissions(session.value.token, role.code, editingPermissions.value);
    roles.value = roles.value.map((existing) => (existing.code === updated.code ? updated : existing));
    editingPermissionsFor.value = null;
    roleState.value[role.code] = "idle";
  } catch (err) {
    handleApiError(err);
    roleState.value[role.code] = "error";
    roleError.value[role.code] = staffErrorMessage(err);
  }
}

const newRole = ref({ code: "", description: "", permissionCodes: [] as string[] });
const createRoleState = ref<"idle" | "submitting" | "error">("idle");
const createRoleError = ref<ErpMessageDescriptor | null>(null);

async function submitNewRole() {
  if (!session.value) return;
  createRoleState.value = "submitting";
  createRoleError.value = null;
  try {
    const created = await createRole(session.value.token, { ...newRole.value });
    roles.value = [...roles.value, created];
    newRole.value = { code: "", description: "", permissionCodes: [] };
    createRoleState.value = "idle";
  } catch (err) {
    handleApiError(err);
    createRoleState.value = "error";
    createRoleError.value = staffErrorMessage(err);
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
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">{{ t("staff.title") }}</h1>
          <p class="text-sm text-sts-muted">{{ t("staff.subtitle") }}</p>
        </div>
      </header>

      <WegoAlert v-if="state === 'error' && error" variant="danger" class="mt-6">{{ t(error.key, error.params) }}</WegoAlert>
      <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted" role="status">{{ t("common.loading") }}</p>

      <template v-else-if="state === 'loaded'">
        <!-- Accounts -->
        <section v-if="canViewUsers" class="mt-8" aria-labelledby="accounts-heading">
          <h2 id="accounts-heading" class="text-lg font-semibold">{{ t("staff.accounts") }}</h2>
          <p v-if="users.length === 0" class="mt-3 text-sm text-sts-muted">{{ t("staff.empty") }}</p>
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
                    <WegoBadge :tone="user.status === 'ACTIVE' ? 'success' : 'neutral'">{{ t(`staff.${user.status}`) }}</WegoBadge>
                  </div>
                  <p class="mt-1 break-all text-sm text-sts-muted">{{ user.roles.join(", ") || t("staff.noRoles") }}</p>
                </div>
                <div v-if="canManageUsers" class="flex w-full min-w-0 flex-wrap gap-2 sm:w-auto">
                  <WegoButton v-if="canViewRoles" type="button" variant="secondary" size="sm" @click="startEditRoles(user)">
                    {{ t("staff.changeRoles") }}
                  </WegoButton>
                  <WegoButton type="button" variant="secondary" size="sm" @click="resettingFor = user">{{ t("staff.resetPassword") }}</WegoButton>
                  <WegoButton
                    type="button"
                    variant="secondary"
                    size="sm"
                    :disabled="rowState[user.id] === 'submitting'"
                    @click="toggleStatus(user)"
                  >
                    {{ t(user.status === "ACTIVE" ? "staff.disable" : "staff.enable") }}
                  </WegoButton>
                </div>
              </div>
              <WegoAlert v-if="rowState[user.id] === 'error' && rowError[user.id]" variant="danger" class="mt-3">{{ t(rowError[user.id]!.key, rowError[user.id]!.params) }}</WegoAlert>
              <WegoAlert v-if="rowNotice[user.id]" variant="success" class="mt-3">{{ t(rowNotice[user.id]!.key, rowNotice[user.id]!.params) }}</WegoAlert>

              <fieldset v-if="editingRolesFor === user.id" class="mt-4 rounded-xl border border-sts-border p-4">
                <legend class="break-all px-1 text-sm font-medium text-sts-muted">{{ t("staff.rolesFor", { email: user.email }) }}</legend>
                <div class="flex flex-wrap gap-4">
                  <label v-for="role in roles" :key="role.code" class="flex items-center gap-2 text-sm">
                    <input v-model="editingRoles" type="checkbox" :value="role.code">
                    {{ role.code }}
                  </label>
                </div>
                <div class="mt-3 flex gap-2">
                  <WegoButton type="button" size="sm" :disabled="rowState[user.id] === 'submitting'" @click="saveRoles(user)">{{ t("staff.saveRoles") }}</WegoButton>
                  <WegoButton type="button" variant="secondary" size="sm" @click="editingRolesFor = null">{{ t("common.cancel") }}</WegoButton>
                </div>
              </fieldset>
            </li>
          </ul>

          <WegoDialog :open="resettingFor !== null" :title="t('staff.resetPassword')" @close="closeReset">
            <ErpLanguageSwitch class="mb-4" />
            <p class="text-sm text-sts-muted">
              {{ t("staff.resetHelp", { email: resettingFor?.email ?? "", count: count(MIN_PASSWORD_LENGTH) }) }}
            </p>
            <WegoInput
              id="resetPassword"
              v-model="newPassword"
              :label="t('staff.newPassword')"
              type="password"
              class="mt-3"
              autocomplete="new-password"
            />
            <template #actions>
              <WegoButton type="button" variant="secondary" @click="closeReset">{{ t("common.cancel") }}</WegoButton>
              <WegoButton type="button" :disabled="newPassword.length < MIN_PASSWORD_LENGTH" @click="confirmReset">
                {{ t("staff.resetPassword") }}
              </WegoButton>
            </template>
          </WegoDialog>

          <form
            v-if="canManageUsers"
            class="mt-6 space-y-4 rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm"
            @submit.prevent="submitNewUser"
          >
            <h3 class="font-semibold">{{ t("staff.newAccount") }}</h3>
            <WegoInput id="newUserEmail" v-model="newUser.email" :label="t('settings.email')" type="email" autocomplete="off" required />
            <WegoInput
              id="newUserPassword"
              v-model="newUser.password"
              :label="t('staff.passwordLabel', { count: count(MIN_PASSWORD_LENGTH) })"
              type="password"
              autocomplete="new-password"
              required
            />
            <p v-if="!canViewRoles" class="text-xs text-sts-muted">
              {{ t("staff.rolesLater") }}
            </p>
            <fieldset v-else>
              <legend class="text-sm font-medium text-sts-muted">{{ t("staff.roles") }}</legend>
              <div class="mt-2 flex flex-wrap gap-4">
                <label v-for="role in roles" :key="role.code" class="flex items-center gap-2 text-sm">
                  <input v-model="newUser.roleCodes" type="checkbox" :value="role.code">
                  {{ role.code }}
                </label>
              </div>
            </fieldset>
            <WegoAlert v-if="createUserState === 'error' && createUserError" variant="danger">{{ t(createUserError.key, createUserError.params) }}</WegoAlert>
            <WegoButton type="submit" size="sm" :disabled="createUserState === 'submitting'">{{ t("staff.createAccount") }}</WegoButton>
          </form>
        </section>

        <!-- Roles -->
        <section v-if="canViewRoles" class="mt-10" aria-labelledby="roles-heading">
          <h2 id="roles-heading" class="text-lg font-semibold">{{ t("staff.rolesPermissions") }}</h2>
          <p class="mt-2 text-xs text-sts-muted">{{ t("staff.technicalNote") }}</p>
          <ul class="mt-3 space-y-3">
            <li
              v-for="role in roles"
              :key="role.code"
              class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm"
            >
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="break-all font-mono font-semibold">{{ role.code }}</p>
                  <p class="break-words text-sm text-sts-muted">{{ role.description }}</p>
                  <p class="mt-1 text-xs text-sts-muted">{{ t("staff.permissionCount", { count: count(role.permissions.length) }) }}</p>
                </div>
                <WegoButton
                  v-if="canManageRoles && editingPermissionsFor !== role.code"
                  type="button"
                  variant="secondary"
                  size="sm"
                  @click="startEditPermissions(role)"
                >
                  {{ t("staff.editPermissions") }}
                </WegoButton>
              </div>
              <WegoAlert v-if="roleState[role.code] === 'error' && roleError[role.code]" variant="danger" class="mt-3">{{ t(roleError[role.code]!.key, roleError[role.code]!.params) }}</WegoAlert>

              <div v-if="editingPermissionsFor === role.code" class="mt-4 space-y-4">
                <fieldset v-for="group in permissionGroups" :key="group.label" class="rounded-xl border border-sts-border p-4">
                  <legend class="px-1 text-sm font-medium text-sts-muted">{{ staffGroupKey(group.label) ? t(staffGroupKey(group.label)!) : group.label }}</legend>
                  <label
                    v-for="permission in group.permissions"
                    :key="permission.code"
                    class="flex items-start gap-2 py-1 text-sm"
                  >
                    <input v-model="editingPermissions" type="checkbox" :value="permission.code" class="mt-1">
                    <span class="min-w-0 break-words">
                      {{ permission.description }}
                      <span class="block break-all font-mono text-xs text-sts-muted">{{ permission.code }}</span>
                    </span>
                  </label>
                </fieldset>
                <div class="flex gap-2">
                  <WegoButton type="button" size="sm" :disabled="roleState[role.code] === 'submitting'" @click="savePermissions(role)">
                    {{ t("staff.savePermissions") }}
                  </WegoButton>
                  <WegoButton type="button" variant="secondary" size="sm" @click="editingPermissionsFor = null">{{ t("common.cancel") }}</WegoButton>
                </div>
              </div>
            </li>
          </ul>

          <form
            v-if="canManageRoles"
            class="mt-6 space-y-4 rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm"
            @submit.prevent="submitNewRole"
          >
            <h3 class="font-semibold">{{ t("staff.newRole") }}</h3>
            <WegoInput id="newRoleCode" v-model="newRole.code" :label="t('staff.roleCode')" required />
            <WegoInput id="newRoleDescription" v-model="newRole.description" :label="t('staff.description')" required />
            <fieldset v-for="group in permissionGroups" :key="group.label" class="rounded-xl border border-sts-border p-4">
              <legend class="px-1 text-sm font-medium text-sts-muted">{{ staffGroupKey(group.label) ? t(staffGroupKey(group.label)!) : group.label }}</legend>
              <label
                v-for="permission in group.permissions"
                :key="permission.code"
                class="flex items-start gap-2 py-1 text-sm"
              >
                <input v-model="newRole.permissionCodes" type="checkbox" :value="permission.code" class="mt-1">
                <span class="min-w-0 break-words">{{ permission.description }}</span>
              </label>
            </fieldset>
            <WegoAlert v-if="createRoleState === 'error' && createRoleError" variant="danger">{{ t(createRoleError.key, createRoleError.params) }}</WegoAlert>
            <WegoButton type="submit" size="sm" :disabled="createRoleState === 'submitting'">{{ t("staff.createRole") }}</WegoButton>
          </form>
        </section>
      </template>
    </div>
  </main>
</template>
