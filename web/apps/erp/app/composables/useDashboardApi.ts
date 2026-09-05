// Explicit imports/exports only — see useAuthSession.ts for why (no Nuxt
// auto-import runtime in the Vitest environment).

export interface DashboardMoney {
  amount: string;
  currencyCode: string;
}

export interface BookingsDashboard {
  bookingsToday: number;
  paidRevenueThisMonth: DashboardMoney[];
}

export interface UpcomingOffering {
  id: string;
  offeringType: string;
  title: string;
  startsOn: string;
}

export interface OfferingsDashboard {
  upcomingTrips: UpcomingOffering[];
}

export interface DiversDashboard {
  activeDivers: number;
}

export interface EquipmentDashboard {
  active: number;
  inMaintenance: number;
  retired: number;
}

export class DashboardApiError extends Error {
  constructor(public readonly status: number) {
    super(`dashboard request failed (${status})`);
  }
}

async function request<T>(path: string, token: string): Promise<T> {
  const response = await fetch(path, { headers: { Authorization: `Bearer ${token}` } });
  if (!response.ok) throw new DashboardApiError(response.status);
  return (await response.json()) as T;
}

export function getBookingsDashboard(token: string): Promise<BookingsDashboard> {
  return request<BookingsDashboard>("/api/v1/divers/dashboard/bookings", token);
}

export function getOfferingsDashboard(token: string): Promise<OfferingsDashboard> {
  return request<OfferingsDashboard>("/api/v1/divers/dashboard/offerings", token);
}

export function getDiversDashboard(token: string): Promise<DiversDashboard> {
  return request<DiversDashboard>("/api/v1/divers/dashboard/divers", token);
}

export function getEquipmentDashboard(token: string): Promise<EquipmentDashboard> {
  return request<EquipmentDashboard>("/api/v1/divers/dashboard/equipment", token);
}
