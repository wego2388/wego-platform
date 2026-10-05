import type { PublicSalesStatus } from "@wego/api-contract";
import { apiFetch } from "./useCatalog";
import { readSalesCapability } from "../utils/enquiry";

/** SSR-readable capability, shared by the layout and booking controls. Not cached across visitors. */
export function useSalesStatus() {
  return useAsyncData("sts-sales-status", async () => {
    const status = readSalesCapability(await apiFetch<PublicSalesStatus>("/api/v1/tours-operator/sales-status"));
    if (!status) throw new Error("sales_status_unavailable");
    return status;
  }, { dedupe: "defer" });
}
