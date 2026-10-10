import { TravelMarketplaceApiError } from "./useTravelMarketplaceApi";

export interface SalesControl {
  requestsPaused: boolean;
  version: number;
  reason: string | null;
  updatedByUserId: string | null;
  updatedAt: string | null;
}

export async function getSalesControl(token: string): Promise<SalesControl> {
  return controlRequest(token);
}

export async function updateSalesControl(token: string, requestsPaused: boolean, expectedVersion: number, reason: string): Promise<SalesControl> {
  return controlRequest(token, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ requestsPaused, expectedVersion, reason }) });
}

async function controlRequest(token: string, init: RequestInit = {}): Promise<SalesControl> {
  const response = await fetch("/api/v1/travel-marketplace/sales-control", {
    ...init, cache: "no-store", headers: { ...init.headers, Authorization: `Bearer ${token}` },
  });
  const body = await response.json();
  if (!response.ok) throw new TravelMarketplaceApiError(response.status, body.error ?? `http_${response.status}`, body.currentVersion);
  return body as SalesControl;
}

export async function getPublicSalesStatus(): Promise<boolean> {
  const response = await fetch("/api/v1/travel-marketplace/public/sales-status", { cache: "no-store" });
  if (!response.ok) throw new Error("sales_status_unavailable");
  const body = await response.json();
  if (typeof body?.requestsOpen !== "boolean") throw new Error("invalid_sales_status");
  return body.requestsOpen;
}
