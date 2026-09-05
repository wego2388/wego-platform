<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoInput, WegoPageHeader, WegoPanel, WegoSelect } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  addServiceRecord,
  completeEquipmentMaintenance,
  createEquipment,
  type Equipment,
  type EquipmentStatus,
  type EquipmentType,
  DiversApiError,
  listEquipment,
  listRentals,
  listServiceRecords,
  recordRental,
  recordRentalReturn,
  type RentalRecord,
  retireEquipment,
  type ServiceRecord,
  startEquipmentMaintenance,
  updateEquipment,
} from "../composables/useDiversApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Equipment · Wego Platform" });

const session = ref<AuthSession | null>(null);
const equipment = ref<Equipment[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const statusFilter = ref<EquipmentStatus | "">("ACTIVE");
const typeFilter = ref<EquipmentType | "">("");
const search = ref("");
const qrLookup = ref("");

const equipmentTypes: EquipmentType[] = ["BCD", "REGULATOR", "TANK", "WETSUIT", "FIN", "MASK", "DIVE_COMPUTER", "OTHER"];

function blankForm() {
  return { equipmentType: "BCD" as EquipmentType, label: "", qrCode: "", itemSize: "", serialNumber: "" };
}

const form = ref(blankForm());
const editingEquipmentId = ref<string | null>(null);
const formState = ref<"idle" | "submitting" | "error">("idle");
const formError = ref("");

// Per-item async action state, keyed by equipment id.
const actionState = ref<Record<string, "idle" | "submitting" | "error">>({});
const actionError = ref<Record<string, string>>({});

// Which item's detail panel (service records + rentals) is expanded.
const expandedId = ref<string | null>(null);
const serviceRecords = ref<Record<string, ServiceRecord[]>>({});
const rentals = ref<Record<string, RentalRecord[]>>({});
const detailState = ref<Record<string, "idle" | "loading" | "loaded" | "error">>({});

const newService = ref({ servicedOn: "", description: "", performedBy: "" });
const newRental = ref({ customerName: "", rentedOn: "", notes: "" });
const returnDate = ref("");

const canManage = () => hasPermission(session.value, "equipment:manage");
const canView = () => hasPermission(session.value, "equipment:view");

function handleApiError(error: unknown) {
  if (error instanceof DiversApiError && error.status === 401) {
    clearAuthSession();
    session.value = null;
  }
}

function errorText(error: unknown): string {
  if (error instanceof DiversApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this.";
    if (error.errorCode === "duplicate_qr_code") return "Another item already uses that QR code.";
    if (error.errorCode === "not_active") return "That item isn't currently active.";
    if (error.errorCode === "not_in_maintenance") return "That item isn't currently in maintenance.";
    if (error.errorCode === "already_retired") return "That item is already retired.";
    if (error.errorCode === "has_open_rental") return "That item is still out on rental — return it first.";
    if (error.errorCode === "equipment_not_available") return "That item isn't available to rent right now.";
    if (error.errorCode === "already_out") return "That item already has an open rental.";
    if (error.errorCode === "no_open_rental") return "That item has no open rental to return.";
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadEquipment() {
  if (!session.value || !canView()) {
    equipment.value = [];
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    equipment.value = await listEquipment(session.value.token, {
      status: statusFilter.value || undefined,
      type: typeFilter.value || undefined,
      search: search.value || undefined,
    });
    listState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

async function runQrLookup() {
  if (!session.value || !qrLookup.value) return;
  listState.value = "loading";
  listError.value = "";
  try {
    equipment.value = await listEquipment(session.value.token, { qrCode: qrLookup.value });
    listState.value = "loaded";
    if (equipment.value.length === 0) listError.value = "No item has that QR code.";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

function runSearch() {
  qrLookup.value = "";
  loadEquipment();
}

function startEdit(item: Equipment) {
  editingEquipmentId.value = item.id;
  form.value = { equipmentType: item.equipmentType, label: item.label, qrCode: item.qrCode, itemSize: item.itemSize ?? "", serialNumber: item.serialNumber ?? "" };
  formState.value = "idle";
  formError.value = "";
}

function cancelEdit() {
  editingEquipmentId.value = null;
  form.value = blankForm();
  formState.value = "idle";
  formError.value = "";
}

async function submitForm() {
  if (!session.value) return;
  formState.value = "submitting";
  formError.value = "";
  try {
    if (editingEquipmentId.value) {
      const updated = await updateEquipment(session.value.token, editingEquipmentId.value, {
        label: form.value.label,
        itemSize: form.value.itemSize || undefined,
        serialNumber: form.value.serialNumber || undefined,
      });
      equipment.value = equipment.value.map((existing) => (existing.id === updated.id ? updated : existing));
    } else {
      const created = await createEquipment(session.value.token, {
        equipmentType: form.value.equipmentType,
        label: form.value.label,
        qrCode: form.value.qrCode,
        itemSize: form.value.itemSize || undefined,
        serialNumber: form.value.serialNumber || undefined,
      });
      equipment.value = [created, ...equipment.value];
    }
    cancelEdit();
  } catch (error) {
    handleApiError(error);
    formState.value = "error";
    formError.value = errorText(error);
  }
}

async function runAction(item: Equipment, action: "start-maintenance" | "complete-maintenance" | "retire") {
  if (!session.value) return;
  if (action === "retire" && !window.confirm(`Retire "${item.label}" permanently? This cannot be undone.`)) return;

  actionState.value[item.id] = "submitting";
  actionError.value[item.id] = "";
  try {
    const updated =
      action === "start-maintenance"
        ? await startEquipmentMaintenance(session.value.token, item.id)
        : action === "complete-maintenance"
          ? await completeEquipmentMaintenance(session.value.token, item.id)
          : await retireEquipment(session.value.token, item.id);
    equipment.value = equipment.value.map((existing) => (existing.id === updated.id ? updated : existing));
    actionState.value[item.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[item.id] = "error";
    actionError.value[item.id] = errorText(error);
  }
}

async function toggleDetails(item: Equipment) {
  if (expandedId.value === item.id) {
    expandedId.value = null;
    return;
  }
  expandedId.value = item.id;
  newService.value = { servicedOn: "", description: "", performedBy: "" };
  newRental.value = { customerName: "", rentedOn: "", notes: "" };
  returnDate.value = "";
  if (!session.value) return;

  detailState.value[item.id] = "loading";
  try {
    const [services, rentalHistory] = await Promise.all([
      listServiceRecords(session.value.token, item.id),
      listRentals(session.value.token, item.id),
    ]);
    serviceRecords.value[item.id] = services;
    rentals.value[item.id] = rentalHistory;
    detailState.value[item.id] = "loaded";
  } catch (error) {
    handleApiError(error);
    detailState.value[item.id] = "error";
  }
}

async function submitServiceRecord(item: Equipment) {
  if (!session.value) return;
  actionState.value[item.id] = "submitting";
  actionError.value[item.id] = "";
  try {
    const record = await addServiceRecord(session.value.token, item.id, {
      servicedOn: newService.value.servicedOn,
      description: newService.value.description,
      performedBy: newService.value.performedBy || undefined,
    });
    serviceRecords.value[item.id] = [record, ...(serviceRecords.value[item.id] ?? [])];
    newService.value = { servicedOn: "", description: "", performedBy: "" };
    actionState.value[item.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[item.id] = "error";
    actionError.value[item.id] = errorText(error);
  }
}

async function submitRental(item: Equipment) {
  if (!session.value) return;
  actionState.value[item.id] = "submitting";
  actionError.value[item.id] = "";
  try {
    const record = await recordRental(session.value.token, item.id, {
      customerName: newRental.value.customerName,
      rentedOn: newRental.value.rentedOn,
      notes: newRental.value.notes || undefined,
    });
    rentals.value[item.id] = [record, ...(rentals.value[item.id] ?? [])];
    newRental.value = { customerName: "", rentedOn: "", notes: "" };
    actionState.value[item.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[item.id] = "error";
    actionError.value[item.id] = errorText(error);
  }
}

async function submitReturn(item: Equipment) {
  if (!session.value || !returnDate.value) return;
  actionState.value[item.id] = "submitting";
  actionError.value[item.id] = "";
  try {
    const record = await recordRentalReturn(session.value.token, item.id, returnDate.value);
    rentals.value[item.id] = (rentals.value[item.id] ?? []).map((existing) => (existing.id === record.id ? record : existing));
    returnDate.value = "";
    actionState.value[item.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[item.id] = "error";
    actionError.value[item.id] = errorText(error);
  }
}

function openRentalFor(id: string): RentalRecord | undefined {
  return (rentals.value[id] ?? []).find((rental) => !rental.returnedOn);
}

function statusTone(status: EquipmentStatus): "success" | "warning" | "neutral" {
  if (status === "ACTIVE") return "success";
  if (status === "IN_MAINTENANCE") return "warning";
  return "neutral";
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadEquipment();
});
</script>

<template>
  <WegoPageHeader title="Equipment & Tanks" description="QR-tracked BCDs, regulators, tanks, and the rest of the shared inventory." />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view equipment.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel title="Inventory" class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to view equipment (equipment:view).
      </p>
      <template v-else>
        <div class="rounded-wego-control border border-wego-border p-4">
          <p class="text-sm font-semibold text-wego-muted">Scan or type a QR code</p>
          <div class="mt-2 flex flex-wrap items-end gap-3">
            <WegoInput id="qrLookup" v-model="qrLookup" label="QR code" class="min-w-0 flex-1" @keyup.enter="runQrLookup" />
            <WegoButton type="button" @click="runQrLookup">Look up</WegoButton>
          </div>
        </div>

        <div class="mt-4 flex flex-wrap items-end gap-3">
          <WegoInput id="search" v-model="search" label="Search label, QR, or serial" class="min-w-0 flex-1" @keyup.enter="runSearch" />
          <WegoSelect id="typeFilter" v-model="typeFilter" label="Type" @change="runSearch">
            <option value="">All</option>
            <option v-for="type in equipmentTypes" :key="type" :value="type">{{ type }}</option>
          </WegoSelect>
          <WegoSelect id="statusFilter" v-model="statusFilter" label="Status" @change="runSearch">
            <option value="ACTIVE">Active</option>
            <option value="IN_MAINTENANCE">In maintenance</option>
            <option value="RETIRED">Retired</option>
            <option value="">All</option>
          </WegoSelect>
          <WegoButton type="button" variant="secondary" @click="runSearch">Search</WegoButton>
        </div>

        <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
        <p v-else-if="listState === 'loaded' && equipment.length === 0" class="mt-3 text-sm text-wego-muted">No equipment found.</p>
        <ul v-else class="mt-4 space-y-3">
          <li v-for="item in equipment" :key="item.id" class="rounded-wego-control border border-wego-border p-4">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div class="flex items-center gap-2">
                  <p class="font-semibold">{{ item.label }}</p>
                  <WegoBadge :tone="statusTone(item.status)">{{ item.status }}</WegoBadge>
                </div>
                <p class="mt-1 text-sm text-wego-muted">
                  {{ item.equipmentType }} · <span class="font-mono">{{ item.qrCode }}</span>
                  <span v-if="item.itemSize"> · size {{ item.itemSize }}</span>
                  <span v-if="item.serialNumber"> · S/N {{ item.serialNumber }}</span>
                </p>
              </div>
              <div v-if="canManage()" class="flex shrink-0 flex-wrap gap-2">
                    <WegoButton type="button" variant="secondary" @click="startEdit(item)">Edit</WegoButton>
                    <WegoButton type="button" variant="secondary" @click="toggleDetails(item)">
                      {{ expandedId === item.id ? "Hide details" : "Details" }}
                    </WegoButton>
                    <WegoButton
                      v-if="item.status === 'ACTIVE'"
                      type="button"
                      variant="secondary"
                      :disabled="actionState[item.id] === 'submitting'"
                      @click="runAction(item, 'start-maintenance')"
                    >
                      Start maintenance
                    </WegoButton>
                    <WegoButton
                      v-if="item.status === 'IN_MAINTENANCE'"
                      type="button"
                      variant="secondary"
                      :disabled="actionState[item.id] === 'submitting'"
                      @click="runAction(item, 'complete-maintenance')"
                    >
                      Complete maintenance
                    </WegoButton>
                    <WegoButton
                      v-if="item.status !== 'RETIRED'"
                      type="button"
                      variant="secondary"
                      :disabled="actionState[item.id] === 'submitting'"
                      @click="runAction(item, 'retire')"
                    >
                      Retire
                    </WegoButton>
                  </div>
                </div>

                <WegoAlert v-if="actionState[item.id] === 'error'" variant="danger" class="mt-2">{{ actionError[item.id] }}</WegoAlert>

                <div v-if="expandedId === item.id" class="mt-4 grid gap-4 border-t border-wego-border pt-4 sm:grid-cols-2">
                  <div>
                    <h3 class="text-sm font-semibold">Maintenance log</h3>
                    <p v-if="detailState[item.id] === 'loading'" class="mt-2 text-xs text-wego-muted">Loading…</p>
                    <ul class="mt-2 space-y-2">
                      <li v-for="record in serviceRecords[item.id] ?? []" :key="record.id" class="text-xs text-wego-muted">
                        {{ record.servicedOn }} — {{ record.description }}<span v-if="record.performedBy"> ({{ record.performedBy }})</span>
                      </li>
                      <li v-if="detailState[item.id] === 'loaded' && (serviceRecords[item.id] ?? []).length === 0" class="text-xs text-wego-muted">
                        No service records yet.
                      </li>
                    </ul>
                    <form v-if="canManage()" class="mt-3 space-y-2" @submit.prevent="submitServiceRecord(item)">
                      <WegoInput :id="`svc-date-${item.id}`" v-model="newService.servicedOn" label="Serviced on" type="date" required />
                      <WegoInput :id="`svc-desc-${item.id}`" v-model="newService.description" label="Description" required />
                      <WegoInput :id="`svc-by-${item.id}`" v-model="newService.performedBy" label="Performed by (optional)" />
                      <WegoButton type="submit" variant="secondary" :disabled="actionState[item.id] === 'submitting'">Log service</WegoButton>
                    </form>
                  </div>

                  <div>
                    <h3 class="text-sm font-semibold">Rental history</h3>
                    <p v-if="detailState[item.id] === 'loading'" class="mt-2 text-xs text-wego-muted">Loading…</p>
                    <ul class="mt-2 space-y-2">
                      <li v-for="rental in rentals[item.id] ?? []" :key="rental.id" class="text-xs text-wego-muted">
                        {{ rental.customerName }} — {{ rental.rentedOn }}
                        <span v-if="rental.returnedOn"> → {{ rental.returnedOn }}</span>
                        <span v-else class="font-semibold text-wego-accent"> (still out)</span>
                      </li>
                      <li v-if="detailState[item.id] === 'loaded' && (rentals[item.id] ?? []).length === 0" class="text-xs text-wego-muted">
                        No rentals yet.
                      </li>
                    </ul>

                    <div v-if="canManage() && openRentalFor(item.id)" class="mt-3 flex items-end gap-2">
                      <WegoInput :id="`return-date-${item.id}`" v-model="returnDate" label="Return date" type="date" />
                      <WegoButton type="button" variant="secondary" :disabled="actionState[item.id] === 'submitting'" @click="submitReturn(item)">
                        Record return
                      </WegoButton>
                    </div>
                    <form v-else-if="canManage() && item.status === 'ACTIVE'" class="mt-3 space-y-2" @submit.prevent="submitRental(item)">
                      <WegoInput :id="`rental-name-${item.id}`" v-model="newRental.customerName" label="Customer name" required />
                      <WegoInput :id="`rental-date-${item.id}`" v-model="newRental.rentedOn" label="Rented on" type="date" required />
                      <WegoButton type="submit" variant="secondary" :disabled="actionState[item.id] === 'submitting'">Start rental</WegoButton>
                    </form>
                  </div>
                </div>
              </li>
            </ul>
          </template>
        </WegoPanel>

    <WegoPanel v-if="canManage()" :title="editingEquipmentId ? 'Edit equipment' : 'New equipment'" class="mt-8">
      <form class="space-y-5" @submit.prevent="submitForm">
        <WegoSelect id="equipmentType" v-model="form.equipmentType" label="Type" :disabled="editingEquipmentId !== null">
          <option v-for="type in equipmentTypes" :key="type" :value="type">{{ type }}</option>
        </WegoSelect>
        <WegoInput id="label" v-model="form.label" label="Label" required />
        <WegoInput id="qrCode" v-model="form.qrCode" label="QR code" required :disabled="editingEquipmentId !== null" />
        <div class="grid gap-5 sm:grid-cols-2">
          <WegoInput id="itemSize" v-model="form.itemSize" label="Size (optional)" />
          <WegoInput id="serialNumber" v-model="form.serialNumber" label="Serial number (optional)" />
        </div>

        <WegoAlert v-if="formState === 'error'" variant="danger">{{ formError }}</WegoAlert>

        <div class="flex gap-3">
          <WegoButton type="submit" :disabled="formState === 'submitting'" :loading="formState === 'submitting'">
            {{ editingEquipmentId ? "Save changes" : "Register equipment" }}
          </WegoButton>
          <WegoButton v-if="editingEquipmentId" type="button" variant="secondary" @click="cancelEdit">Cancel</WegoButton>
        </div>
      </form>
    </WegoPanel>
  </template>
</template>
