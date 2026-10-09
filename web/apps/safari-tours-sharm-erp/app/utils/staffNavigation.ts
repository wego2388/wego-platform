import type { ErpMessageKey } from "./erpLocale";

/** Organization only. The shell filters permission-gated links before grouping. */
export const STAFF_GROUPS = [
  { key: "workspace.operations", paths: ["/", "/today", "/requests", "/bookings"] },
  { key: "workspace.catalog", paths: ["/tours", "/categories"] },
  { key: "workspace.finance", paths: ["/finance", "/profitability", "/costs", "/settlements", "/cash-box"] },
  { key: "workspace.relationships", paths: ["/suppliers", "/drivers", "/vehicles", "/customers"] },
  { key: "workspace.admin", paths: ["/reviews", "/notifications", "/staff", "/sales", "/settings"] },
] satisfies { key: ErpMessageKey; paths: string[] }[];

export type StaffLink = { to: string; label: string };
export function groupedStaffLinks(links: readonly StaffLink[], query = "") {
  const needle = query.trim().toLocaleLowerCase();
  return STAFF_GROUPS.map(group => ({
    key: group.key,
    links: group.paths.flatMap(path => links.filter(link => link.to === path && (!needle || link.label.toLocaleLowerCase().includes(needle)))),
  })).filter(group => group.links.length > 0);
}
export function activeStaffLink(path: string, to: string): boolean {
  return to === "/" ? path === "/" : path === to || path.startsWith(`${to}/`);
}
