import { mount, flushPromises } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AssignmentOptions, AssignmentView, DriverSheetDocument, DocumentStamp, SupplierOrderDocument } from "@wego/api-contract";
import VehiclesPage from "../app/pages/vehicles.vue";
import DriversPage from "../app/pages/drivers.vue";
import SuppliersPage from "../app/pages/suppliers.vue";
import AssignmentPanel from "../app/components/AssignmentPanel.vue";
import DriverSheetDoc from "../app/components/documents/DriverSheetDocument.vue";
import SupplierOrderDoc from "../app/components/documents/SupplierOrderDocument.vue";
import { writeAuthSession, type AuthSession } from "../app/composables/useAuthSession";
import * as api from "../app/composables/useToursApi";
import { ToursApiError } from "../app/composables/useToursApi";
import { en, ar, isLocalizedErpRoute, type ErpMessageKey } from "../app/utils/erpLocale";
import { documentMessages } from "../app/utils/documentMessages";
import { DOCUMENT_PERMISSION, documentPath, isValidSubject } from "../app/utils/documentFormat";
import { assignmentFailure, licenceState, registrySaveError, splitSupplierOrderSubject, supplierOrderSubject } from "../app/utils/opsRegistry";

vi.mock("../app/composables/useToursApi", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  listSuppliers: vi.fn(), saveSupplier: vi.fn(), listDrivers: vi.fn(), saveDriver: vi.fn(), listVehicles: vi.fn(), saveVehicle: vi.fn(),
  listAllStaffTours: vi.fn(), saveAssignment: vi.fn(), clearAssignment: vi.fn(),
}));

const session = (permissions: string[]): AuthSession => ({ token: "tok", email: "a@b.c", roles: [], permissions });
function setup(locale: "en" | "ar" = "en", permissions: string[] = ["tours-operator.fleet:manage", "tours-operator.supplier:manage", "tours-operator.tour:view"]) {
  sessionStorage.clear(); writeAuthSession(session(permissions));
  vi.stubGlobal("useRouter", () => ({ replace: vi.fn() })); vi.stubGlobal("useRoute", () => ({ query: {} }));
  vi.stubGlobal("useHead", vi.fn()); vi.stubGlobal("useCookie", () => ref(locale)); vi.stubGlobal("useState", (_k: string, f: () => unknown) => ref(f()));
}
beforeEach(() => setup());
afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); sessionStorage.clear(); });
const link = { global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } } };

describe("ops messages", () => {
  it("every ops, assignment and document key exists in English and Arabic with the same placeholders", () => {
    const holes = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((x) => x[1]).sort();
    const keys = Object.keys(en).filter((k) => k.startsWith("ops.") || k.startsWith("asg.") || k === "nav.suppliers" || k === "nav.drivers" || k === "nav.vehicles") as ErpMessageKey[];
    expect(keys.length).toBeGreaterThan(100);
    for (const key of keys) {
      expect(ar[key].length, key).toBeGreaterThan(0);
      expect(holes(ar[key]), key).toEqual(holes(en[key]));
    }
    expect(Object.keys(documentMessages.ar).sort()).toEqual(Object.keys(documentMessages.en).sort());
  });
  it("localises the new routes and document links", () => {
    for (const path of ["/suppliers", "/drivers", "/vehicles", "/documents/driver-sheet/x", "/documents/supplier-order/x"]) expect(isLocalizedErpRoute(path), path).toBe(true);
  });
});

describe("pure helpers", () => {
  it("flags a licence as expired, ending soon (30 days) or fine", () => {
    expect(licenceState("2027-03-09", "2027-03-10")).toBe("expired");
    expect(licenceState("2027-03-10", "2027-03-10")).toBe("soon");
    expect(licenceState("2027-04-08", "2027-03-10")).toBe("soon");
    expect(licenceState("2027-04-09", "2027-03-10")).toBe("ok");
  });
  it("translates save failures and keeps a stale revision distinct", () => {
    expect(registrySaveError(new ToursApiError(409, "revision_conflict"))).toEqual({ key: "ops.revisionConflict" });
    expect(registrySaveError(new ToursApiError(409, "supplier_code_taken"))).toEqual({ key: "ops.codeTaken" });
    expect(registrySaveError(new ToursApiError(409, "vehicle_plate_taken"))).toEqual({ key: "ops.plateTaken" });
    expect(registrySaveError(new ToursApiError(403, "forbidden"))).toEqual({ key: "common.forbidden" });
    expect(registrySaveError(new ToursApiError(500, "boom"))).toEqual({ key: "ops.saveFailed", params: { code: "boom" } });
    expect(registrySaveError(new Error("offline"))).toEqual({ key: "common.connectionFailed" });
  });
  it("reads the conflicts of a refused assignment and flags a stale revision for reload", () => {
    const conflict = assignmentFailure(new ToursApiError(409, "assignment_conflict", { conflicts: [{ kind: "DRIVER", otherSlotId: "s2", otherTimeSlot: "MORNING" }] }));
    expect(conflict.conflicts).toEqual([{ kind: "DRIVER", otherSlotId: "s2", otherTimeSlot: "MORNING" }]);
    expect(conflict.message.key).toBe("asg.conflictTitle");
    expect(assignmentFailure(new ToursApiError(409, "revision_conflict")).revisionConflict).toBe(true);
    expect(assignmentFailure(new ToursApiError(422, "driver_licence_expired")).message.key).toBe("asg.err.driver_licence_expired");
    expect(assignmentFailure(new ToursApiError(500, "weird")).message).toEqual({ key: "asg.err.generic", params: { code: "weird" } });
  });
  it("builds and validates the driver sheet and supplier order links", () => {
    const slot = "3f2b8c3e-1a2b-4c5d-8e9f-0a1b2c3d4e5f"; const supplier = "4a2b8c3e-1a2b-4c5d-8e9f-0a1b2c3d4e5f";
    const subject = supplierOrderSubject(slot, supplier);
    expect(splitSupplierOrderSubject(subject)).toEqual([slot, supplier]);
    expect(isValidSubject("supplier-order", subject)).toBe(true);
    expect(isValidSubject("supplier-order", slot)).toBe(false);
    expect(isValidSubject("driver-sheet", slot)).toBe(true);
    expect(isValidSubject("driver-sheet", "STR-1")).toBe(false);
    expect(documentPath("driver-sheet", slot)).toBe(`/documents/driver-sheet/${slot}`);
    expect(DOCUMENT_PERMISSION["driver-sheet"]).toBe("tours-operator.document:print-ops");
    expect(DOCUMENT_PERMISSION["supplier-order"]).toBe("tours-operator.document:print-ops");
  });
});

describe("registry pages", () => {
  it.each([["", null], ["0", 0], ["24", 24], ["720", 720]])("suppliers: native notice input %s saves without freezing", async (input, expected) => {
    vi.mocked(api.listAllStaffTours).mockResolvedValue([]);
    vi.mocked(api.listSuppliers).mockResolvedValue([]);
    vi.mocked(api.saveSupplier).mockResolvedValue({} as never);
    const w = mount(SuppliersPage, link); await flushPromises();
    await w.findAll("button").find((b) => b.text() === "Add")!.trigger("click");
    const fields = w.findAll("form input:not([type])");
    await fields[0]!.setValue("TEST-01"); await fields[1]!.setValue("Synthetic supplier");
    await w.find("input[type=number]").setValue(input);
    await w.find("form").trigger("submit"); await flushPromises();
    expect(api.saveSupplier).toHaveBeenCalledWith("tok", expect.objectContaining({ code: "TEST-01", noticeHours: expected }), undefined);
    expect(w.find("form").exists()).toBe(false);
    expect(w.find("[role=status]").text()).toBe("Saved.");
    w.unmount();
  });
  it.each(["-1", "24.5", "721"])("suppliers: invalid notice %s is refused without a stuck save button", async (input) => {
    vi.mocked(api.listAllStaffTours).mockResolvedValue([]); vi.mocked(api.listSuppliers).mockResolvedValue([]);
    const w = mount(SuppliersPage, link); await flushPromises();
    await w.findAll("button").find((b) => b.text() === "Add")!.trigger("click");
    await w.find("input[type=number]").setValue(input);
    await w.find("form").trigger("submit"); await flushPromises();
    expect(api.saveSupplier).not.toHaveBeenCalled();
    expect(w.find("[role=alert]").exists()).toBe(true);
    expect(w.find("button[type=submit]").attributes("disabled")).toBeUndefined();
    w.unmount();
  });
  it("suppliers: a refused save retains populated notice and permits correction/retry", async () => {
    vi.mocked(api.listAllStaffTours).mockResolvedValue([]); vi.mocked(api.listSuppliers).mockResolvedValue([]);
    vi.mocked(api.saveSupplier).mockRejectedValueOnce(new ToursApiError(409, "supplier_code_taken")).mockResolvedValue({} as never);
    const w = mount(SuppliersPage, link); await flushPromises();
    await w.findAll("button").find((b) => b.text() === "Add")!.trigger("click");
    await w.find("input[type=number]").setValue("24");
    await w.find("form").trigger("submit"); await flushPromises();
    expect((w.find("input[type=number]").element as HTMLInputElement).value).toBe("24");
    expect(w.find("button[type=submit]").attributes("disabled")).toBeUndefined();
    expect(w.find("[role=alert]").text()).toContain("code");
    await w.find("form").trigger("submit"); await flushPromises();
    expect(api.saveSupplier).toHaveBeenCalledTimes(2);
    expect(w.find("form").exists()).toBe(false);
    w.unmount();
  });
  it("vehicles: an empty registry explains that the owner has not provided vehicles", async () => {
    vi.mocked(api.listVehicles).mockResolvedValue([]); vi.mocked(api.listSuppliers).mockResolvedValue([]);
    const w = mount(VehiclesPage, link); await flushPromises();
    expect(w.find("[data-testid='vehicles-empty']").text()).toContain("The owner has not provided vehicle details");
    expect(w.text()).not.toContain("Could not load");
  });
  it("vehicles: the empty state is Arabic in Arabic", async () => {
    setup("ar");
    vi.mocked(api.listVehicles).mockResolvedValue([]); vi.mocked(api.listSuppliers).mockResolvedValue([]);
    const w = mount(VehiclesPage, link); await flushPromises();
    expect(w.find("[data-testid='vehicles-empty']").text()).toContain("المالك لم يقدّم بيانات المركبات");
  });
  it("vehicles: needs a label or a plate and sends nothing otherwise", async () => {
    vi.mocked(api.listVehicles).mockResolvedValue([]); vi.mocked(api.listSuppliers).mockResolvedValue([]);
    const w = mount(VehiclesPage, link); await flushPromises();
    await w.findAll("button").find((b) => b.text() === "Add")!.trigger("click");
    await w.find("input[type=number]").setValue("14");
    await w.find("form").trigger("submit"); await flushPromises();
    expect(w.text()).toContain("Give a label or a plate");
    expect(api.saveVehicle).not.toHaveBeenCalled();
  });
  it("vehicles: saves with the revision and tells the user when someone saved first", async () => {
    const vehicle = { id: "v1", label: "Hiace", plate: null, display: "Hiace", vehicleType: "VAN", seats: 14, ownership: "OWNED", hiredFromSupplierId: null, active: true, revision: 3, createdAt: "", updatedAt: "" };
    vi.mocked(api.listVehicles).mockResolvedValue([vehicle] as never); vi.mocked(api.listSuppliers).mockResolvedValue([]);
    vi.mocked(api.saveVehicle).mockRejectedValue(new ToursApiError(409, "revision_conflict"));
    const w = mount(VehiclesPage, link); await flushPromises();
    await w.findAll("button").find((b) => b.text() === "Edit")!.trigger("click");
    await w.find("form").trigger("submit"); await flushPromises();
    expect(vi.mocked(api.saveVehicle).mock.calls[0]![1]).toMatchObject({ label: "Hiace", seats: 14, expectedRevision: 3 });
    expect(vi.mocked(api.saveVehicle).mock.calls[0]![2]).toBe("v1");
    expect(w.text()).toContain("Someone else saved this record first");
    expect(w.text()).toContain("Reload");
  });
  it("drivers: shows expired and soon-ending licences and withholds the page without the permission", async () => {
    const day = (offset: number) => { const d = new Date(); d.setUTCDate(d.getUTCDate() + offset); return d.toISOString().slice(0, 10); };
    const mk = (id: string, name: string, until: string) => ({ id, name, workPhone: "+201028215951", engagementType: "PER_TRIP", licenceValidUntil: until, active: true, revision: 1, createdAt: "", updatedAt: "" });
    vi.mocked(api.listDrivers).mockResolvedValue([mk("d1", "Amr", day(-5)), mk("d2", "Yasser", day(10)), mk("d3", "Hossam", day(400))] as never);
    const w = mount(DriversPage, link); await flushPromises();
    expect(w.text()).toContain("Licence expired"); expect(w.text()).toContain("Licence ends soon");
    expect(w.findAll("tbody tr")).toHaveLength(3);
    setup("en", ["tours-operator.booking:view"]);
    const denied = mount(DriversPage, link); await flushPromises();
    expect(denied.text()).toContain("You don't have permission");
    expect(api.listDrivers).toHaveBeenCalledTimes(1);
  });
  it("suppliers: lists with contact details, and the form has labelled fields and a phone hint", async () => {
    vi.mocked(api.listAllStaffTours).mockResolvedValue([{ id: "t1", slug: "quad", nameEn: "Quad" }] as never);
    vi.mocked(api.listSuppliers).mockResolvedValue([{ id: "s1", code: "S01", name: "Panorama", serviceType: "QUAD_BUGGY_SAFARI", contactPerson: "Hamed", businessPhone: "+201015048400", confirmationChannel: "WHATSAPP", noticeHours: 24, pricingBasis: "PER_PERSON", currency: "EGP", settlementCadence: "AFTER_EACH_TRIP", paymentMethod: "INSTAPAY", cancellationTerms: null, active: true, tourIds: ["t1"], revision: 2, createdAt: "", updatedAt: "" }] as never);
    const w = mount(SuppliersPage, link); await flushPromises();
    expect(w.text()).toContain("Panorama"); expect(w.text()).toContain("WhatsApp"); expect(w.text()).toContain("Quad");
    await w.findAll("button").find((b) => b.text() === "Edit")!.trigger("click");
    expect(w.find("#supplier-phone-hint").exists()).toBe(true);
    for (const input of w.findAll("form input[type=text], form input:not([type]), form select, form textarea")) expect(input.element.closest("label"), input.html()).not.toBeNull();
  });
});

const options: AssignmentOptions = {
  drivers: [{ id: "d1", name: "Amr", licenceValidUntil: "2030-01-01", licenceCoversDate: true }, { id: "d2", name: "Old", licenceValidUntil: "2020-01-01", licenceCoversDate: false }],
  vehicles: [{ id: "v1", display: "Hiace", seats: 14 }],
  suppliers: [{ id: "s1", code: "S01", name: "Panorama", serviceType: "QUAD_BUGGY_SAFARI", tourIds: ["t1"] }, { id: "s2", code: "S02", name: "Sindbad", serviceType: "BOAT", tourIds: [] }],
};
const view = (over: Partial<AssignmentView> = {}): AssignmentView => ({
  slotId: "slot1", tourId: "t1", tourNameEn: "Quad", date: "2027-03-10", timeSlot: "MORNING", guests: 9, slotBlocked: false, assignment: null, issues: [],
  suggestedSuppliers: [{ id: "s1", code: "S01", name: "Panorama" }], ...over,
});
const panel = (v: AssignmentView | null, canAssign = true) => mount(AssignmentPanel, {
  props: { token: "tok", slotId: "slot1", view: v, options, canAssign, canPrint: true, slotLabel: (id: string) => `Other ${id}` },
  global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
});

describe("assignment panel", () => {
  it("shows nothing assigned, then offers the suggested suppliers first and saves revision 0", async () => {
    vi.mocked(api.saveAssignment).mockResolvedValue(view());
    const w = panel(view());
    expect(w.text()).toContain("Nothing assigned yet.");
    await w.findAll("button").find((b) => b.text() === "Assign")!.trigger("click");
    expect(w.text()).toContain("Suggested for this tour");
    expect(w.text()).toContain("Old — Licence expired");
    await w.findAll("select")[0]!.setValue("d1");
    await w.find("input[type=checkbox]").setValue(true);
    await w.find("form").trigger("submit"); await flushPromises();
    expect(api.saveAssignment).toHaveBeenCalledWith("tok", "slot1", { driverId: "d1", vehicleId: null, supplierIds: ["s1"], supplierNote: null, expectedRevision: 0 });
    expect(w.emitted("saved")).toHaveLength(1);
  });
  it("refuses an empty selection without calling the API", async () => {
    const w = panel(view());
    await w.findAll("button").find((b) => b.text() === "Assign")!.trigger("click");
    await w.find("form").trigger("submit"); await flushPromises();
    expect(w.text()).toContain("Choose a driver, a vehicle or a supplier.");
    expect(api.saveAssignment).not.toHaveBeenCalled();
  });
  it("shows a conflict clearly with the other departure and keeps the form open", async () => {
    vi.mocked(api.saveAssignment).mockRejectedValue(new ToursApiError(409, "assignment_conflict", { conflicts: [{ kind: "DRIVER", resourceId: "d1", otherSlotId: "slotX", otherTourId: "t2", otherTimeSlot: "MORNING" }] }));
    const w = panel(view());
    await w.findAll("button").find((b) => b.text() === "Assign")!.trigger("click");
    await w.findAll("select")[0]!.setValue("d1");
    await w.find("form").trigger("submit"); await flushPromises();
    const alert = w.find("[role=alert]");
    expect(alert.text()).toContain("Conflict — nothing was saved");
    expect(alert.text()).toContain("This driver is already on another departure in the same time window.");
    expect(alert.text()).toContain("Other slotX");
    expect(w.find("form").exists()).toBe(true);
    expect(w.emitted("saved")).toBeUndefined();
  });
  it("asks the day to reload when the revision is stale", async () => {
    vi.mocked(api.saveAssignment).mockRejectedValue(new ToursApiError(409, "revision_conflict"));
    const w = panel(view());
    await w.findAll("button").find((b) => b.text() === "Assign")!.trigger("click");
    await w.findAll("select")[0]!.setValue("d1");
    await w.find("form").trigger("submit"); await flushPromises();
    expect(w.emitted("stale")).toHaveLength(1);
    expect(w.text()).toContain("Someone changed this assignment first");
  });
  it("lists warnings and blocks as badges, and links to the driver sheet and supplier orders", () => {
    const assigned = view({
      assignment: { revision: 2, driver: { id: "d1", name: "Amr" }, vehicle: { id: "v1", display: "Hiace", seats: 4 }, suppliers: [{ id: "s1", code: "S01", name: "Panorama" }], supplierNote: null, assignedByEmail: "a@b.c", assignedAt: "2027-03-01T08:00:00Z", updatedByEmail: "m@b.c", updatedAt: "2027-03-02T08:00:00Z" },
      issues: [{ code: "vehicle_seats_below_guests", resourceId: "v1", value: 9, limit: 4, blocking: false }, { code: "driver_licence_expired", resourceId: "d1", value: null, limit: null, blocking: true }],
    });
    const w = panel(assigned);
    expect(w.text()).toContain("The vehicle has 4 seats but 9 guests are booked.");
    expect(w.text()).toContain("The driver's licence has expired for this day.");
    expect(w.text()).toContain("Warning"); expect(w.text()).toContain("Blocked");
    expect(w.text()).toContain("Revision 2"); expect(w.text()).toContain("Assigned by a@b.c");
    expect(w.text()).toContain("Driver sheet"); expect(w.text()).toContain("Order for Panorama");
    expect(w.find("[role=status]").exists()).toBe(true);
  });
  it("hides the editing controls from someone who may only look", () => {
    const w = panel(view(), false);
    expect(w.findAll("button")).toHaveLength(0);
  });
  it("keeps an assigned but deactivated driver, vehicle and supplier visible and removable", async () => {
    vi.mocked(api.saveAssignment).mockResolvedValue(view());
    const assigned = view({
      assignment: { revision: 3, driver: { id: "dX", name: "Gone Driver" }, vehicle: { id: "vX", display: "Old Bus", seats: 30 }, suppliers: [{ id: "s1", code: "S01", name: "Panorama" }, { id: "sX", code: "SX", name: "Closed Co" }], supplierNote: "Two child life jackets", assignedByEmail: null, assignedAt: "2027-03-01T08:00:00Z", updatedByEmail: null, updatedAt: "2027-03-01T08:00:00Z" },
      issues: [{ code: "driver_inactive", resourceId: "dX", value: null, limit: null, blocking: true }],
    });
    const w = panel(assigned);
    expect(w.text()).toContain("Note for the supplier: Two child life jackets");
    await w.findAll("button").find((b) => b.text() === "Change")!.trigger("click");
    const [driverSelect, vehicleSelect] = w.findAll("select");
    expect((driverSelect!.element as HTMLSelectElement).value).toBe("dX");
    expect(driverSelect!.text()).toContain("Gone Driver — inactive — remove");
    expect(vehicleSelect!.text()).toContain("Old Bus — inactive — remove");
    const inactive = w.find("[data-test=inactive-suppliers]");
    expect(inactive.text()).toContain("Closed Co — inactive — remove");
    expect(w.find("[data-test=inactive-notice]").text()).toContain("has been deactivated");
    expect((w.find("input[type=text]").element as HTMLInputElement).value).toBe("Two child life jackets");
    expect(w.text()).toContain("Do not include customer phone numbers or health details.");

    await driverSelect!.setValue("d1");
    await vehicleSelect!.setValue("");
    await inactive.find("input[type=checkbox]").setValue(false);
    expect(w.find("[data-test=inactive-notice]").exists()).toBe(false);
    await w.find("input[type=text]").setValue("  One vegetarian lunch ");
    await w.find("form").trigger("submit"); await flushPromises();
    expect(api.saveAssignment).toHaveBeenCalledWith("tok", "slot1", { driverId: "d1", vehicleId: null, supplierIds: ["s1"], supplierNote: "One vegetarian lunch", expectedRevision: 3 });
  });
  it("clears with the current revision", async () => {
    vi.mocked(api.clearAssignment).mockResolvedValue(view());
    const assigned = view({ assignment: { revision: 4, driver: { id: "d1", name: "Amr" }, vehicle: null, suppliers: [], supplierNote: null, assignedByEmail: null, assignedAt: "2027-03-01T08:00:00Z", updatedByEmail: null, updatedAt: "2027-03-01T08:00:00Z" } });
    const w = panel(assigned);
    await w.findAll("button").find((b) => b.text() === "Change")!.trigger("click");
    await w.findAll("button").find((b) => b.text() === "Clear assignment")!.trigger("click"); await flushPromises();
    expect(api.clearAssignment).toHaveBeenCalledWith("tok", "slot1", 4);
  });
});

const stamp = (over: Partial<DocumentStamp>): DocumentStamp => ({ type: "DRIVER_SHEET", number: "DRV-2027-000001", version: 1, language: "en", copy: false, revised: false, originalPrintedAt: "2027-03-10T05:00:00Z", printedAt: "2027-03-10T05:00:00Z", printedByEmail: "ops@example.com", ...over });
describe("driver sheet and supplier order documents", () => {
  const driverSheet: DriverSheetDocument = {
    document: stamp({}),
    data: {
      slotId: "slot1", date: "2027-03-10", timeSlot: "MORNING", tourNameEn: "Quad Safari", tourNameAr: "سفاري كواد", driverName: "Captain Amr", vehicle: { display: "Hiace 14", seats: 14 }, totalGuests: 5,
      stops: [{ order: 1, hotelName: "Domina", guests: 2, parties: [{ reference: "STR-1", leadName: "Maria Rossi", hotelRoom: "7", guests: 2 }] }, { order: 2, hotelName: "Hilton", guests: 3, parties: [{ reference: "STR-2", leadName: "Ahmed Hassan", hotelRoom: null, guests: 3 }] }],
    },
  };
  it("driver sheet (EN): route, names, rooms, ops contact, and no phone, price or e-mail wording", () => {
    const w = mount(DriverSheetDoc, { props: { doc: driverSheet, lang: "en" } });
    const text = w.text();
    expect(text).toContain("Driver sheet"); expect(text).toContain("Captain Amr"); expect(text).toContain("Hiace 14");
    expect(text).toContain("Stop 1"); expect(text).toContain("Domina"); expect(text).toContain("Maria Rossi"); expect(text).toContain("Operations contact");
    expect(text).toContain("Guest phone numbers are not printed on this sheet.");
    expect(text).toContain("DRV-2027-000001");
    expect(w.findAll("th").map((h) => h.text().toLowerCase()).join(" ")).not.toMatch(/phone|price|total|payment/);
    expect(w.find("article").attributes("dir")).toBe("ltr");
  });
  it("driver sheet (AR): right-to-left with the Arabic title and a blank vehicle line when none", () => {
    const w = mount(DriverSheetDoc, { props: { doc: { ...driverSheet, data: { ...driverSheet.data, vehicle: null } }, lang: "ar" } });
    expect(w.find("article").attributes("dir")).toBe("rtl");
    expect(w.text()).toContain("ورقة السائق"); expect(w.text()).toContain("سفاري كواد");
    expect(w.find(".doc-blank").exists()).toBe(true);
  });
  it("marks a changed reprint REVISED and an unchanged one COPY", () => {
    expect(mount(DriverSheetDoc, { props: { doc: { ...driverSheet, document: stamp({ version: 2, revised: true }) }, lang: "en" } }).text()).toContain("REVISED");
    expect(mount(DriverSheetDoc, { props: { doc: { ...driverSheet, document: stamp({ version: 2, copy: true }) }, lang: "en" } }).text()).toContain("COPY");
  });
  const order: SupplierOrderDocument = {
    document: stamp({ type: "SUPPLIER_ORDER", number: "SUP-2027-000004" }),
    data: {
      slotId: "slot1", date: "2027-03-10", timeSlot: "MORNING", tourNameEn: "Quad Safari", tourNameAr: null,
      supplier: { code: "S01", name: "Panorama", serviceType: "QUAD_BUGGY_SAFARI", contactPerson: "Hamed", confirmationChannel: "WHATSAPP", noticeHours: 24 },
      totalGuests: 4, adults: 3, children: 1, units: [{ optionLabel: "Buggy", unitCount: 2 }], supplierNote: "One vegetarian lunch",
    },
  };
  it("supplier order (EN): service, guests, staff note and company confirmation contact; no customer data or price wording", () => {
    const w = mount(SupplierOrderDoc, { props: { doc: order, lang: "en" } });
    const text = w.text();
    expect(text).toContain("Supplier order"); expect(text).toContain("Panorama"); expect(text).toContain("Quad / buggy / safari");
    expect(text).toContain("Adults 3 · Children 1"); expect(text).toContain("Note from operations"); expect(text).toContain("One vegetarian lunch"); expect(text).toContain("Hamed");
    expect(text).toContain("Confirm to"); expect(text).toContain("+20 111 129 2690"); expect(text).not.toContain("Special requests"); expect(text).toContain("WhatsApp");
    expect(text).toContain("Notice required: 24 h"); expect(text).toContain("2 × Buggy");
    expect(text).toContain("This order carries no customer names, contact details, requests or prices.");
    expect(w.find(".doc-body").text().toLowerCase()).not.toMatch(/€|egp|price:|phone/);
  });
  it("supplier order (AR) and an order with no supplier note", () => {
    const w = mount(SupplierOrderDoc, { props: { doc: { ...order, data: { ...order.data, supplierNote: null, units: [] } }, lang: "ar" } });
    expect(w.find("article").attributes("dir")).toBe("rtl");
    expect(w.text()).toContain("طلب مورد"); expect(w.text()).toContain("لا يوجد"); expect(w.text()).toContain("واتساب");
  });
});
