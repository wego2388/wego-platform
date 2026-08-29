// Explicit imports/exports only — see useAuthSession.ts for why (no Nuxt
// auto-import runtime in the Vitest environment).

export interface Money {
  amount: string;
  currencyCode: string;
}

export type OfferingType = "DIVE_TRIP" | "COURSE" | "EQUIPMENT_RENTAL" | "PACKAGE";
export type OfferingStatus = "ACTIVE" | "CLOSED";
export type BookingStatus = "CONFIRMED" | "CANCELLED";
export type PaymentStatus = "UNPAID" | "PAID" | "REFUNDED";
export type PricingBasis = "PER_PARTICIPANT" | "FLAT";

export const PAGE_SIZE = 50;

export interface Offering {
  id: string;
  offeringType: OfferingType;
  title: string;
  description?: string;
  startsOn: string;
  endsOn?: string;
  capacity?: number;
  pricingBasis: PricingBasis;
  unitPrice: Money;
  status: OfferingStatus;
  createdAt: string;
  closedAt?: string;
}

export interface Booking {
  id: string;
  offeringId: string;
  partySize: number;
  customerName: string;
  customerEmail?: string;
  customerPhone?: string;
  status: BookingStatus;
  paymentStatus: PaymentStatus;
  pricingBasis: PricingBasis;
  unitPrice: Money;
  billableQuantity: number;
  totalPrice: Money;
  createdAt: string;
  cancelledAt?: string;
  cancellationReason?: string;
}

/** Carries the API's own `error` code (e.g. "capacity_exceeded") so callers can show a specific message. */
export class DiversApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly errorCode: string,
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
    const errorCode = (body && typeof body === "object" && "error" in body ? String(body.error) : null) ?? `http_${response.status}`;
    throw new DiversApiError(response.status, errorCode);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function listOfferings(
  token: string,
  params: { type?: OfferingType; status?: OfferingStatus; page?: number; size?: number } = {},
): Promise<Offering[]> {
  const query = new URLSearchParams();
  if (params.type) query.set("type", params.type);
  if (params.status) query.set("status", params.status);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<Offering[]>(`/api/v1/divers/offerings?${query.toString()}`, token);
}

export function getOffering(token: string, id: string): Promise<Offering> {
  return request<Offering>(`/api/v1/divers/offerings/${id}`, token);
}

export function createOffering(
  token: string,
  body: {
    offeringType: OfferingType;
    title: string;
    description?: string;
    startsOn: string;
    endsOn?: string;
    capacity?: number;
    pricingBasis: PricingBasis;
    unitPrice: Money;
  },
): Promise<Offering> {
  return request<Offering>("/api/v1/divers/offerings", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function closeOffering(token: string, id: string, reason: string): Promise<Offering> {
  return request<Offering>(`/api/v1/divers/offerings/${id}/close`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason: reason || undefined }),
  });
}

export function listBookings(
  token: string,
  params: { offeringId?: string; status?: BookingStatus; page?: number; size?: number } = {},
): Promise<Booking[]> {
  const query = new URLSearchParams();
  if (params.offeringId) query.set("offeringId", params.offeringId);
  if (params.status) query.set("status", params.status);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<Booking[]>(`/api/v1/divers/bookings?${query.toString()}`, token);
}

export function createBooking(
  token: string,
  idempotencyKey: string,
  body: {
    offeringId: string;
    partySize: number;
    customerName: string;
    customerEmail?: string;
    customerPhone?: string;
  },
): Promise<Booking> {
  return request<Booking>("/api/v1/divers/bookings", token, {
    method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(body),
  });
}

export function cancelBooking(token: string, id: string, reason: string): Promise<Booking> {
  return request<Booking>(`/api/v1/divers/bookings/${id}/cancel`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
}

export function markBookingPaid(token: string, id: string): Promise<Booking> {
  return request<Booking>(`/api/v1/divers/bookings/${id}/mark-paid`, token, { method: "PATCH" });
}

export function refundBooking(token: string, id: string, reason: string): Promise<Booking> {
  return request<Booking>(`/api/v1/divers/bookings/${id}/refund`, token, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
}

export type DiverStatus = "ACTIVE" | "ARCHIVED";

export interface DiverCertification {
  id?: string;
  agency: string;
  level: string;
  certificationNumber?: string;
  issuedOn?: string;
}

export interface DiverCertificationSummary {
  agency: string;
  level: string;
  issuedOn?: string;
}

/**
 * The roster/list projection returned by GET /divers — deliberately
 * omits email, phone, emergency contact, medical notes and certification
 * numbers (a real over-exposure fix, not a trimmed-down convenience type).
 * Fetch a single diver by id (getDiver) for the full record.
 */
export interface DiverSummary {
  id: string;
  fullName: string;
  nationality?: string;
  primaryLanguage?: string;
  totalLoggedDives: number;
  maxDepthMeters?: string;
  lastDiveOn?: string;
  certifications: DiverCertificationSummary[];
  status: DiverStatus;
  createdAt: string;
  archivedAt?: string;
}

export interface Diver {
  id: string;
  fullName: string;
  nationality?: string;
  primaryLanguage?: string;
  email?: string;
  phone?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  medicalNotes?: string;
  totalLoggedDives: number;
  maxDepthMeters?: string;
  lastDiveOn?: string;
  bcdSize?: string;
  finSize?: string;
  wetsuitSize?: string;
  certifications: DiverCertification[];
  status: DiverStatus;
  createdAt: string;
  archivedAt?: string;
}

export interface UpsertDiverBody {
  fullName: string;
  nationality?: string;
  primaryLanguage?: string;
  email?: string;
  phone?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  medicalNotes?: string;
  totalLoggedDives: number;
  maxDepthMeters?: number;
  lastDiveOn?: string;
  bcdSize?: string;
  finSize?: string;
  wetsuitSize?: string;
  certifications: DiverCertification[];
}

export function listDivers(
  token: string,
  params: { status?: DiverStatus; search?: string; page?: number; size?: number } = {},
): Promise<DiverSummary[]> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<DiverSummary[]>(`/api/v1/divers/divers?${query.toString()}`, token);
}

export function getDiver(token: string, id: string): Promise<Diver> {
  return request<Diver>(`/api/v1/divers/divers/${id}`, token);
}

export function createDiver(token: string, body: UpsertDiverBody): Promise<Diver> {
  return request<Diver>("/api/v1/divers/divers", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function updateDiver(token: string, id: string, body: UpsertDiverBody): Promise<Diver> {
  return request<Diver>(`/api/v1/divers/divers/${id}`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function archiveDiver(token: string, id: string): Promise<Diver> {
  return request<Diver>(`/api/v1/divers/divers/${id}`, token, { method: "DELETE" });
}

export type EquipmentType = "BCD" | "REGULATOR" | "TANK" | "WETSUIT" | "FIN" | "MASK" | "DIVE_COMPUTER" | "OTHER";
export type EquipmentStatus = "ACTIVE" | "IN_MAINTENANCE" | "RETIRED";

export interface Equipment {
  id: string;
  equipmentType: EquipmentType;
  label: string;
  qrCode: string;
  itemSize?: string;
  serialNumber?: string;
  status: EquipmentStatus;
  createdAt: string;
  retiredAt?: string;
}

export interface ServiceRecord {
  id: string;
  equipmentId: string;
  servicedOn: string;
  description: string;
  performedBy?: string;
  createdAt: string;
}

export interface RentalRecord {
  id: string;
  equipmentId: string;
  customerName: string;
  rentedOn: string;
  returnedOn?: string;
  notes?: string;
  createdAt: string;
}

export function listEquipment(
  token: string,
  params: { type?: EquipmentType; status?: EquipmentStatus; search?: string; qrCode?: string; page?: number; size?: number } = {},
): Promise<Equipment[]> {
  const query = new URLSearchParams();
  if (params.type) query.set("type", params.type);
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  if (params.qrCode) query.set("qrCode", params.qrCode);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<Equipment[]>(`/api/v1/divers/equipment?${query.toString()}`, token);
}

export function createEquipment(
  token: string,
  body: { equipmentType: EquipmentType; label: string; qrCode: string; itemSize?: string; serialNumber?: string },
): Promise<Equipment> {
  return request<Equipment>("/api/v1/divers/equipment", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function updateEquipment(
  token: string,
  id: string,
  body: { label: string; itemSize?: string; serialNumber?: string },
): Promise<Equipment> {
  return request<Equipment>(`/api/v1/divers/equipment/${id}`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function startEquipmentMaintenance(token: string, id: string): Promise<Equipment> {
  return request<Equipment>(`/api/v1/divers/equipment/${id}/start-maintenance`, token, { method: "POST" });
}

export function completeEquipmentMaintenance(token: string, id: string): Promise<Equipment> {
  return request<Equipment>(`/api/v1/divers/equipment/${id}/complete-maintenance`, token, { method: "POST" });
}

export function retireEquipment(token: string, id: string): Promise<Equipment> {
  return request<Equipment>(`/api/v1/divers/equipment/${id}/retire`, token, { method: "POST" });
}

export function listServiceRecords(token: string, equipmentId: string): Promise<ServiceRecord[]> {
  return request<ServiceRecord[]>(`/api/v1/divers/equipment/${equipmentId}/service-records`, token);
}

export function addServiceRecord(
  token: string,
  equipmentId: string,
  body: { servicedOn: string; description: string; performedBy?: string },
): Promise<ServiceRecord> {
  return request<ServiceRecord>(`/api/v1/divers/equipment/${equipmentId}/service-records`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function listRentals(token: string, equipmentId: string): Promise<RentalRecord[]> {
  return request<RentalRecord[]>(`/api/v1/divers/equipment/${equipmentId}/rentals`, token);
}

export function recordRental(
  token: string,
  equipmentId: string,
  body: { customerName: string; rentedOn: string; notes?: string },
): Promise<RentalRecord> {
  return request<RentalRecord>(`/api/v1/divers/equipment/${equipmentId}/rentals`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function recordRentalReturn(token: string, equipmentId: string, returnedOn: string): Promise<RentalRecord> {
  return request<RentalRecord>(`/api/v1/divers/equipment/${equipmentId}/rentals/return`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ returnedOn }),
  });
}

export type CharterType = "STANDING" | "DAILY" | "SAFARI";
export type CharterStatus = "ACTIVE" | "ENDED";

export interface BoatCharter {
  id: string;
  boatName: string;
  charterType: CharterType;
  licensedCapacity: number;
  startsOn: string;
  endsOn?: string;
  notes?: string;
  status: CharterStatus;
  createdAt: string;
  endedAt?: string;
}

export interface OfferingBoatCharterLink {
  offeringId: string;
  boatCharterId: string;
  linkedAt: string;
}

export function listBoatCharters(
  token: string,
  params: { type?: CharterType; status?: CharterStatus; search?: string; page?: number; size?: number } = {},
): Promise<BoatCharter[]> {
  const query = new URLSearchParams();
  if (params.type) query.set("type", params.type);
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<BoatCharter[]>(`/api/v1/divers/boat-charters?${query.toString()}`, token);
}

export function createBoatCharter(
  token: string,
  body: { boatName: string; charterType: CharterType; licensedCapacity: number; startsOn: string; endsOn?: string; notes?: string },
): Promise<BoatCharter> {
  return request<BoatCharter>("/api/v1/divers/boat-charters", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function updateBoatCharter(
  token: string,
  id: string,
  body: { boatName: string; licensedCapacity: number; startsOn: string; endsOn?: string; notes?: string },
): Promise<BoatCharter> {
  return request<BoatCharter>(`/api/v1/divers/boat-charters/${id}`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function endBoatCharter(token: string, id: string): Promise<BoatCharter> {
  return request<BoatCharter>(`/api/v1/divers/boat-charters/${id}/end`, token, { method: "POST" });
}

export async function getOfferingBoatCharter(token: string, offeringId: string): Promise<OfferingBoatCharterLink | null> {
  try {
    return await request<OfferingBoatCharterLink>(`/api/v1/divers/offerings/${offeringId}/boat-charter`, token);
  } catch (error) {
    if (error instanceof DiversApiError && error.status === 404) return null;
    throw error;
  }
}

export function linkOfferingBoatCharter(token: string, offeringId: string, boatCharterId: string): Promise<OfferingBoatCharterLink> {
  return request<OfferingBoatCharterLink>(`/api/v1/divers/offerings/${offeringId}/boat-charter`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ boatCharterId }),
  });
}

export async function unlinkOfferingBoatCharter(token: string, offeringId: string): Promise<void> {
  await request<unknown>(`/api/v1/divers/offerings/${offeringId}/boat-charter`, token, { method: "DELETE" });
}

export type EnrollmentStage = "LEAD" | "THEORY" | "POOL" | "OPEN_WATER" | "CERTIFIED" | "WITHDRAWN";

export interface CourseEnrollment {
  id: string;
  diverId: string;
  offeringId: string;
  instructorUserId?: string;
  stage: EnrollmentStage;
  startedAt: string;
  certifiedAt?: string;
  withdrawnAt?: string;
  createdAt: string;
}

export interface SkillEvaluation {
  id: string;
  enrollmentId: string;
  skillName: string;
  passed: boolean;
  evaluatedByUserId?: string;
  evaluatedOn: string;
  notes?: string;
  createdAt: string;
}

export function listCourseEnrollments(
  token: string,
  params: { diverId?: string; offeringId?: string; stage?: EnrollmentStage; page?: number; size?: number } = {},
): Promise<CourseEnrollment[]> {
  const query = new URLSearchParams();
  if (params.diverId) query.set("diverId", params.diverId);
  if (params.offeringId) query.set("offeringId", params.offeringId);
  if (params.stage) query.set("stage", params.stage);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<CourseEnrollment[]>(`/api/v1/divers/course-enrollments?${query.toString()}`, token);
}

export function enrollDiverInCourse(token: string, diverId: string, offeringId: string): Promise<CourseEnrollment> {
  return request<CourseEnrollment>("/api/v1/divers/course-enrollments", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ diverId, offeringId }),
  });
}

export function assignCourseInstructor(token: string, enrollmentId: string, instructorUserId: string): Promise<CourseEnrollment> {
  return request<CourseEnrollment>(`/api/v1/divers/course-enrollments/${enrollmentId}/instructor`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ instructorUserId }),
  });
}

export function advanceCourseEnrollment(token: string, enrollmentId: string): Promise<CourseEnrollment> {
  return request<CourseEnrollment>(`/api/v1/divers/course-enrollments/${enrollmentId}/advance`, token, { method: "POST" });
}

export function withdrawCourseEnrollment(token: string, enrollmentId: string): Promise<CourseEnrollment> {
  return request<CourseEnrollment>(`/api/v1/divers/course-enrollments/${enrollmentId}/withdraw`, token, { method: "POST" });
}

export function listSkillEvaluations(token: string, enrollmentId: string): Promise<SkillEvaluation[]> {
  return request<SkillEvaluation[]>(`/api/v1/divers/course-enrollments/${enrollmentId}/skill-evaluations`, token);
}

export function recordSkillEvaluation(
  token: string,
  enrollmentId: string,
  body: { skillName: string; passed: boolean; evaluatedOn: string; notes?: string },
): Promise<SkillEvaluation> {
  return request<SkillEvaluation>(`/api/v1/divers/course-enrollments/${enrollmentId}/skill-evaluations`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}
