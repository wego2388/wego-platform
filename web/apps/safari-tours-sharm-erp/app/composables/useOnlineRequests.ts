import type { OnlineRequest, OnlineRequestStatus } from "@wego/api-contract";
import { staffRequest } from "./useToursApi";

const base = "/api/v1/tours-operator/staff/booking-requests";
export const listOnlineRequests = (token: string, status: OnlineRequestStatus | "", page: number) =>
  staffRequest<OnlineRequest[]>(`${base}?${new URLSearchParams({ ...(status ? { status } : {}), page: String(page), size: "50" })}`, token);
export const getOnlineRequest = (token: string, id: string) => staffRequest<OnlineRequest>(`${base}/${id}`, token);
export const getOnlineRequestCount = (token: string) => staffRequest<{ count: number }>(`${base}/open-count`, token);
export const getOnlineRequestHistory = (token: string, id: string) =>
  staffRequest<{ status: OnlineRequestStatus; actorUserId: string | null; occurredAt: string }[]>(`${base}/${id}/history`, token);
export const actOnOnlineRequest = (token: string, id: string, action: "follow-up" | "convert", payload: unknown) =>
  staffRequest<OnlineRequest>(`${base}/${id}/${action}`, token, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
  });
