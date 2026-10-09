// API client for the tours-operator product endpoints.
// API shapes are generated from the platform OpenAPI contract and shared with the public site.
import type {
  Booking,
  BookingStatus,
  Tour,
  TourCategory,
  TourSlot,
  PaymentLedgerEntry,
  ToursOperatorPaymentStatus,
  BookingHistoryEntry,
  PaymentHistoryEntry,
  CustomerNotification,
  NotificationStatus,
  SalesControl,
  CreateOfficeBookingPayload,
  OfficeCollection,
  OfficeCollectionOutcome,
  OfficeCollectionQuote,
  CollectionMethod,
  PaidCurrency,
  FxRate,
  FxRateToday,
  VoucherDocument,
  ReceiptDocument,
  RunSheetDocument,
  PickupManifestDocument,
  CancellationFormDocument,
  Supplier,
  SupplierRequest,
  Driver,
  DriverRequest,
  Vehicle,
  VehicleRequest,
  AssignmentView,
  AssignmentOptions,
  AssignmentRequest,
  DriverSheetDocument,
  SupplierOrderDocument,
  CostComponent, CostComponentRequest, ProfitReport, OfficeSummary, OfficeRefunds, OfficeRefundOutcome, RefundMethod,
  PartySummary, PartyStatement, SettlementPayment, SettlementApproval, PayableAdjustment, SettlementMethod, CashDay, CashBoxEvent,
  SettlementStatementDocument,
} from "@wego/api-contract";

export type {
  Supplier,
  SupplierRequest,
  Driver,
  DriverRequest,
  Vehicle,
  VehicleRequest,
  AssignmentView,
  AssignmentIssue,
  AssignmentOptions,
  DriverSheetDocument,
  SupplierOrderDocument,
  Booking,
  BookingCustomer,
  BookingStatus,
  Money,
  TimeSlot,
  Tour,
  TourCategory,
  TourSlot,
  PaymentLedgerEntry,
  ToursOperatorPaymentStatus,
  BookingHistoryEntry,
  PaymentHistoryEntry,
  CustomerNotification,
  NotificationStatus,
  SalesControl,
  OfficePayment,
  OfficePaymentState,
  OfficeCollection,
  OfficeCollectionOutcome,
  OfficeCollectionQuote,
  CollectionMethod,
  PaidCurrency,
  FxRate,
  FxRateToday,
  CreateOfficeBookingPayload,
  DocumentStamp,
  VoucherDocument,
  ReceiptDocument,
  RunSheetDocument,
  PickupManifestDocument,
  CancellationFormDocument,
} from "@wego/api-contract";
export { addMoney, divideMoney, formatMoney, minorUnitsToMoney, moneyToMinorUnits } from "@wego/api-contract";

export const PAGE_SIZE = 50;

/** Carries the API's own error code so callers can render a specific message. */
export class ToursApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly errorCode: string,
    /** The parsed error body, e.g. the conflicts of a refused assignment. */
    public readonly body: unknown = null,
  ) {
    super(errorCode);
  }
}

async function request<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { ...(init.headers ?? {}), Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const errorCode =
      body && typeof body === "object" && "error" in body
        ? String(body.error)
        : `http_${response.status}`;
    throw new ToursApiError(response.status, errorCode, body);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Same authenticated transport/error contract for the dedicated request inbox. */
export const staffRequest = request;

// ── Tours ──────────────────────────────────────────────────────────────────

export function listTours(
  token: string,
  params: { activeOnly?: boolean; category?: TourCategory; page?: number; size?: number } = {},
): Promise<Tour[]> {
  const q = new URLSearchParams();
  if (params.activeOnly !== undefined) q.set("activeOnly", String(params.activeOnly));
  if (params.category) q.set("category", params.category);
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? PAGE_SIZE));
  return request<Tour[]>(`/api/v1/tours-operator/tours?${q}`, token);
}

export function getTour(token: string, id: string): Promise<Tour> {
  return request<Tour>(`/api/v1/tours-operator/tours/${id}`, token);
}

// ── Slots ──────────────────────────────────────────────────────────────────

export function listSlotsByDate(
  token: string,
  tourId: string,
  date: string,
): Promise<TourSlot[]> {
  const q = new URLSearchParams({ date });
  return request<TourSlot[]>(`/api/v1/tours-operator/tours/${tourId}/slots/by-date?${q}`, token);
}

export function listSlotsByRange(
  token: string,
  tourId: string,
  from: string,
  to: string,
): Promise<TourSlot[]> {
  const q = new URLSearchParams({ from, to });
  return request<TourSlot[]>(`/api/v1/tours-operator/tours/${tourId}/slots?${q}`, token);
}

// ── Office bookings (staff-created) and office payments ──────────────────────

const JSON_HEADERS = { "Content-Type": "application/json" };

/** Re-sending the same clientRequestId returns the booking already created, never a second one. */
export function createOfficeBooking(token: string, payload: CreateOfficeBookingPayload): Promise<Booking> {
  return request<Booking>("/api/v1/tours-operator/staff/bookings", token, {
    method: "POST", headers: JSON_HEADERS, body: JSON.stringify(payload),
  });
}

export function listOfficeCollections(token: string, bookingId: string): Promise<OfficeCollection[]> {
  return request<OfficeCollection[]>(`/api/v1/tours-operator/staff/bookings/${bookingId}/collections`, token);
}

export function recordOfficeCollection(
  token: string,
  bookingId: string,
  payload: { clientRequestId: string; method: CollectionMethod; amount: number; currency: PaidCurrency; reference?: string; fxRateId?: string; correctsCollectionId?: string },
): Promise<OfficeCollectionOutcome> {
  return request<OfficeCollectionOutcome>(`/api/v1/tours-operator/staff/bookings/${bookingId}/collections`, token, {
    method: "POST", headers: JSON_HEADERS, body: JSON.stringify(payload),
  });
}

export function reverseOfficeCollection(
  token: string,
  bookingId: string,
  collectionId: string,
  payload: { clientRequestId: string; reason: string },
): Promise<OfficeCollectionOutcome> {
  return request<OfficeCollectionOutcome>(
    `/api/v1/tours-operator/staff/bookings/${bookingId}/collections/${collectionId}/reverse`,
    token,
    { method: "POST", headers: JSON_HEADERS, body: JSON.stringify(payload) },
  );
}

/** The EUR a payment would settle at today's manager-set rate; writes nothing. */
export function quoteOfficeCollection(
  token: string,
  bookingId: string,
  currency: PaidCurrency,
  amount: string,
): Promise<OfficeCollectionQuote> {
  const q = new URLSearchParams({ currency, amount });
  return request<OfficeCollectionQuote>(`/api/v1/tours-operator/staff/bookings/${bookingId}/collections/quote?${q}`, token);
}

export function getFxRateToday(token: string): Promise<FxRateToday> {
  return request<FxRateToday>("/api/v1/tours-operator/staff/fx-rate/today", token);
}

export function setFxRate(token: string, egpPerEur: number): Promise<FxRate> {
  return request<FxRate>("/api/v1/tours-operator/staff/fx-rate", token, {
    method: "POST", headers: JSON_HEADERS, body: JSON.stringify({ egpPerEur }),
  });
}

// ── Bookings ───────────────────────────────────────────────────────────────

export function listBookings(
  token: string,
  params: {
    tourId?: string;
    status?: BookingStatus;
    date?: string;
    page?: number;
    size?: number;
  } = {},
): Promise<Booking[]> {
  const q = new URLSearchParams();
  if (params.tourId) q.set("tourId", params.tourId);
  if (params.status) q.set("status", params.status);
  if (params.date)   q.set("date", params.date);
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? PAGE_SIZE));
  return request<Booking[]>(`/api/v1/tours-operator/bookings?${q}`, token);
}

export function getBooking(token: string, id: string): Promise<Booking> {
  return request<Booking>(`/api/v1/tours-operator/bookings/${id}`, token);
}

export function cancelBooking(token: string, id: string, reason: string): Promise<Booking> {
  return request<Booking>(`/api/v1/tours-operator/bookings/${id}/cancel`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
}

export function completeBooking(token: string, id: string): Promise<Booking> {
  return request<Booking>(`/api/v1/tours-operator/bookings/${id}/complete`, token, {
    method: "POST",
  });
}

export function getBookingHistory(token: string, id: string): Promise<BookingHistoryEntry[]> {
  return request<BookingHistoryEntry[]>(`/api/v1/tours-operator/bookings/${id}/history`, token);
}

export function getPaymentHistory(token: string, bookingId: string): Promise<PaymentHistoryEntry[]> {
  return request<PaymentHistoryEntry[]>(`/api/v1/tours-operator/staff/bookings/${bookingId}/payment-history`, token);
}

export function listNotifications(
  token: string,
  params: { status?: NotificationStatus; page?: number; size?: number } = {},
): Promise<CustomerNotification[]> {
  const q = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? PAGE_SIZE),
  });
  if (params.status) q.set("status", params.status);
  return request<CustomerNotification[]>(`/api/v1/tours-operator/staff/notifications?${q}`, token);
}

export function resendNotification(token: string, id: string): Promise<undefined> {
  return request<undefined>(`/api/v1/tours-operator/staff/notifications/${id}/resend`, token, { method: "POST" });
}

// ── Emergency sales control ────────────────────────────────────────────────

export function getSalesControl(token: string): Promise<SalesControl> {
  return request<SalesControl>("/api/v1/tours-operator/staff/sales-control", token);
}

export function updateSalesControl(
  token: string,
  payload: { bookingsPaused: boolean; paymentsPaused: boolean; reason: string | null },
): Promise<SalesControl> {
  return request<SalesControl>("/api/v1/tours-operator/staff/sales-control", token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

/** Public flags, readable by any staff member (no permission needed). */
export async function getPublicSalesStatus(): Promise<import("@wego/api-contract").PublicSalesStatus> {
  const response = await fetch("/api/v1/tours-operator/sales-status");
  if (!response.ok) throw new ToursApiError(response.status, `http_${response.status}`);
  return response.json();
}

export function listPaymentLedger(
  token: string,
  params: {
    from: string;
    to: string;
    status?: ToursOperatorPaymentStatus;
    /** Keyset cursor: the last paymentId of the previous page. */
    after?: string;
    size?: number;
  },
): Promise<PaymentLedgerEntry[]> {
  const q = new URLSearchParams({
    from: params.from,
    to: params.to,
    size: String(params.size ?? PAGE_SIZE),
  });
  if (params.status) q.set("status", params.status);
  if (params.after) q.set("after", params.after);
  return request<PaymentLedgerEntry[]>(`/api/v1/tours-operator/staff/payments?${q}`, token);
}

// ── Staff tours ────────────────────────────────────────────────────────────

export type CreateTourPayload = {
  slug: string;
  category: TourCategory;
  durationText: string;
  priceAdultCents: number;
  priceChildCents?: number | null;
  capacity: number;
  availableTimeSlots: string[];
  sortOrder: number;
  nameEn?: string | null;
  tourType?: string | null;
  imageUrl?: string | null;
  cancellationPolicy?: string | null;
  pricingNote?: string | null;
};

export type UpdateTourPayload = Omit<CreateTourPayload, "slug">;

export function listStaffTours(
  token: string,
  params: { activeOnly?: boolean; category?: TourCategory; page?: number; size?: number } = {},
): Promise<Tour[]> {
  const q = new URLSearchParams();
  if (params.activeOnly !== undefined) q.set("activeOnly", String(params.activeOnly));
  if (params.category) q.set("category", params.category);
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? PAGE_SIZE));
  return request<Tour[]>(`/api/v1/tours-operator/staff/tours?${q}`, token);
}

/** Server-side cap for tour list pages (`@Max(100)` on both tour list endpoints). */
const TOUR_PAGE_MAX = 100;

/**
 * Loads every tour for id→name lookups, including deactivated tours that still
 * own historical bookings and payments. Requires `tours-operator.tour:view`.
 */
export async function listAllStaffTours(token: string): Promise<Tour[]> {
  const tours: Tour[] = [];
  for (let page = 0; ; page++) {
    const batch = await listStaffTours(token, { page, size: TOUR_PAGE_MAX });
    tours.push(...batch);
    if (batch.length < TOUR_PAGE_MAX) return tours;
  }
}

export function getStaffTour(token: string, id: string): Promise<Tour> {
  return request<Tour>(`/api/v1/tours-operator/staff/tours/${id}`, token);
}

export function createTour(token: string, payload: CreateTourPayload): Promise<Tour> {
  return request<Tour>("/api/v1/tours-operator/staff/tours", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateTour(token: string, id: string, payload: UpdateTourPayload) {
  return request<undefined>(`/api/v1/tours-operator/staff/tours/${id}`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function activateTour(token: string, id: string) {
  return request<undefined>(`/api/v1/tours-operator/staff/tours/${id}/activate`, token, {
    method: "PATCH",
  });
}

export function deactivateTour(token: string, id: string) {
  return request<undefined>(`/api/v1/tours-operator/staff/tours/${id}/deactivate`, token, {
    method: "PATCH",
  });
}

// ── Staff slots ────────────────────────────────────────────────────────────

export function createSlot(
  token: string,
  tourId: string,
  payload: { date: string; timeSlot: string; capacity: number },
): Promise<TourSlot> {
  return request<TourSlot>(`/api/v1/tours-operator/staff/tours/${tourId}/slots`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function blockSlot(token: string, tourId: string, slotId: string) {
  return request<undefined>(
    `/api/v1/tours-operator/staff/tours/${tourId}/slots/${slotId}/block`,
    token,
    { method: "PATCH" },
  );
}

export function unblockSlot(token: string, tourId: string, slotId: string) {
  return request<undefined>(
    `/api/v1/tours-operator/staff/tours/${tourId}/slots/${slotId}/unblock`,
    token,
    { method: "PATCH" },
  );
}

// ── Office documents (OPS2-D) ────────────────────────────────────────────────
// Each call records one print server-side (original, then reprints) and returns
// the data the browser renders and prints. Nothing is stored as a PDF.

export type DocumentLanguageCode = "en" | "ar";

function printRequest<T>(token: string, path: string, body: Record<string, string>): Promise<T> {
  return request<T>(`/api/v1/tours-operator/documents/${path}`, token, {
    method: "POST", headers: JSON_HEADERS, body: JSON.stringify(body),
  });
}

export const printVoucher = (token: string, bookingId: string, language: DocumentLanguageCode) =>
  printRequest<VoucherDocument>(token, `bookings/${bookingId}/voucher`, { language });
export const printReceipt = (token: string, collectionId: string, language: DocumentLanguageCode) =>
  printRequest<ReceiptDocument>(token, `collections/${collectionId}/receipt`, { language });
export const printCancellationForm = (token: string, bookingId: string, language: DocumentLanguageCode) =>
  printRequest<CancellationFormDocument>(token, `bookings/${bookingId}/cancellation-form`, { language });
export const printRunSheet = (token: string, date: string, language: DocumentLanguageCode) =>
  printRequest<RunSheetDocument>(token, "run-sheet", { date, language });
export const printPickupManifest = (token: string, slotId: string, language: DocumentLanguageCode) =>
  printRequest<PickupManifestDocument>(token, `slots/${slotId}/pickup-manifest`, { language });

export const printDriverSheet = (token: string, slotId: string, language: DocumentLanguageCode) =>
  printRequest<DriverSheetDocument>(token, `slots/${slotId}/driver-sheet`, { language });
export const printSupplierOrder = (token: string, slotId: string, supplierId: string, language: DocumentLanguageCode) =>
  printRequest<SupplierOrderDocument>(token, `slots/${slotId}/suppliers/${supplierId}/supplier-order`, { language });

// ── Suppliers, drivers, vehicles (OPS2-E) ───────────────────────────────────
// Records are never deleted; every update echoes the revision that was read.

const STAFF = "/api/v1/tours-operator/staff";
const write = <T>(token: string, method: "POST" | "PUT", path: string, body: unknown) =>
  request<T>(path, token, { method, headers: JSON_HEADERS, body: JSON.stringify(body) });

export const listSuppliers = (token: string) => request<Supplier[]>(`${STAFF}/suppliers`, token);
export const saveSupplier = (token: string, payload: SupplierRequest, id?: string) =>
  write<Supplier>(token, id ? "PUT" : "POST", id ? `${STAFF}/suppliers/${id}` : `${STAFF}/suppliers`, payload);
export const listDrivers = (token: string) => request<Driver[]>(`${STAFF}/drivers`, token);
export const saveDriver = (token: string, payload: DriverRequest, id?: string) =>
  write<Driver>(token, id ? "PUT" : "POST", id ? `${STAFF}/drivers/${id}` : `${STAFF}/drivers`, payload);
export const listVehicles = (token: string) => request<Vehicle[]>(`${STAFF}/vehicles`, token);
export const saveVehicle = (token: string, payload: VehicleRequest, id?: string) =>
  write<Vehicle>(token, id ? "PUT" : "POST", id ? `${STAFF}/vehicles/${id}` : `${STAFF}/vehicles`, payload);

// ── Daily assignment ────────────────────────────────────────────────────────

export const listAssignments = (token: string, date: string) => request<AssignmentView[]>(`${STAFF}/assignments?date=${encodeURIComponent(date)}`, token);
export const getAssignmentOptions = (token: string, date: string) =>
  request<AssignmentOptions>(`${STAFF}/assignment-options?date=${encodeURIComponent(date)}`, token);
export const saveAssignment = (token: string, slotId: string, payload: AssignmentRequest) =>
  write<AssignmentView>(token, "PUT", `${STAFF}/slots/${slotId}/assignment`, payload);
export const clearAssignment = (token: string, slotId: string, expectedRevision: number) =>
  request<AssignmentView>(`${STAFF}/slots/${slotId}/assignment?expectedRevision=${expectedRevision}`, token, { method: "DELETE" });

// ── Costs, profitability, settlements, cash box, refunds (OPS2-F) ───────────
// Money records are append-only: corrections are reversals with a reason.

export type {
  CostComponent, CostComponentRequest, ProfitReport, ProfitGroup, ProfitTotals, OfficeSummary, OfficeRefunds, OfficeRefund, OfficeRefundOutcome,
  RefundMethod, PartySummary, PartyStatement, StatementMovement, SettlementPayment, SettlementApproval, PayableAdjustment, SettlementMethod,
  CashDay, CashBoxEvent, CostCategory, CostBasis, FinanceAmount, CurrencyBalance, SettlementStatementDocument,
} from "@wego/api-contract";

export type PartyPath = "suppliers" | "drivers";
export type ProfitGrouping = "BOOKING" | "DEPARTURE" | "TOUR" | "MONTH";

export function listCosts(token: string, includeEnded: boolean): Promise<CostComponent[]> {
  return request<CostComponent[]>(`${STAFF}/costs?includeEnded=${includeEnded}`, token);
}
export const createCost = (token: string, payload: CostComponentRequest) => write<CostComponent>(token, "POST", `${STAFF}/costs`, payload);
export const replaceCost = (token: string, id: string, payload: CostComponentRequest) =>
  write<CostComponent>(token, "POST", `${STAFF}/costs/${id}/replace`, payload);
export const endCost = (token: string, id: string, lastDay: string) => write<CostComponent>(token, "POST", `${STAFF}/costs/${id}/end`, { lastDay });

export function getProfitability(token: string, from: string, to: string, groupBy: ProfitGrouping): Promise<ProfitReport> {
  const q = new URLSearchParams({ from, to, groupBy });
  return request<ProfitReport>(`${STAFF}/finance/profitability?${q}`, token);
}
export function getOfficeSummary(token: string, from: string, to: string): Promise<OfficeSummary> {
  const q = new URLSearchParams({ from, to });
  return request<OfficeSummary>(`${STAFF}/finance/office-summary?${q}`, token);
}

export const listRefunds = (token: string, bookingId: string) => request<OfficeRefunds>(`${STAFF}/bookings/${bookingId}/refunds`, token);
export const recordRefund = (
  token: string,
  bookingId: string,
  payload: { clientRequestId: string; method: RefundMethod; amount: number; currency: PaidCurrency; reference?: string; fxRateId?: string; reason: string },
) => write<OfficeRefundOutcome>(token, "POST", `${STAFF}/bookings/${bookingId}/refunds`, payload);
export const reverseRefund = (token: string, bookingId: string, refundId: string, payload: { clientRequestId: string; reason: string }) =>
  write<OfficeRefundOutcome>(token, "POST", `${STAFF}/bookings/${bookingId}/refunds/${refundId}/reverse`, payload);

export const listPayables = (token: string) => request<PartySummary[]>(`${STAFF}/payables`, token);
export function getStatement(token: string, party: PartyPath, id: string, from: string, to: string): Promise<PartyStatement> {
  const q = new URLSearchParams({ from, to });
  return request<PartyStatement>(`${STAFF}/payables/${party}/${id}/statement?${q}`, token);
}
export const paySettlement = (
  token: string,
  party: PartyPath,
  id: string,
  payload: { clientRequestId: string; method: SettlementMethod; currency: PaidCurrency; amount: number; reference?: string; note?: string; approvalId?: string },
) => write<SettlementPayment>(token, "POST", `${STAFF}/payables/${party}/${id}/payments`, payload);
export const approveSettlement = (
  token: string,
  party: PartyPath,
  id: string,
  payload: { clientRequestId: string; currency: PaidCurrency; amount: number; note?: string },
) => write<SettlementApproval>(token, "POST", `${STAFF}/payables/${party}/${id}/approvals`, payload);
export const adjustPayable = (
  token: string,
  party: PartyPath,
  id: string,
  payload: { clientRequestId: string; kind: "CHARGE" | "DEDUCTION"; currency: PaidCurrency; amount: number; slotId?: string; serviceDate?: string; reason: string },
) => write<PayableAdjustment>(token, "POST", `${STAFF}/payables/${party}/${id}/adjustments`, payload);
export const reverseSettlementEntry = (
  token: string,
  party: PartyPath,
  id: string,
  entry: "payments" | "adjustments",
  entryId: string,
  payload: { clientRequestId: string; reason: string },
) => write<SettlementPayment | PayableAdjustment>(token, "POST", `${STAFF}/payables/${party}/${id}/${entry}/${entryId}/reverse`, payload);

export function getCashDay(token: string, date: string, currency: PaidCurrency): Promise<CashDay> {
  const q = new URLSearchParams({ date, currency });
  return request<CashDay>(`${STAFF}/cash-box?${q}`, token);
}
export const listRecentCashDays = (token: string) => request<CashBoxEvent[]>(`${STAFF}/cash-box/recent?days=14`, token);
export const countCash = (token: string, date: string, currency: PaidCurrency, counted: number, note?: string) =>
  write<CashDay>(token, "POST", `${STAFF}/cash-box/${date}/${currency}/count`, { counted, ...(note ? { note } : {}) });
export const confirmCash = (token: string, date: string, currency: PaidCurrency) =>
  request<CashDay>(`${STAFF}/cash-box/${date}/${currency}/confirm`, token, { method: "POST" });
export const reopenCash = (token: string, date: string, currency: PaidCurrency, reason: string) =>
  write<CashDay>(token, "POST", `${STAFF}/cash-box/${date}/${currency}/reopen`, { reason });

export const printSettlementStatement = (token: string, party: PartyPath, id: string, from: string, to: string, language: DocumentLanguageCode) =>
  request<SettlementStatementDocument>(`/api/v1/tours-operator/documents/payables/${party}/${id}/statement`, token, {
    method: "POST", headers: JSON_HEADERS, body: JSON.stringify({ from, to, language }),
  });
