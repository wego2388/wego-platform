import { describe, expect, it, vi, afterEach } from "vitest";
import { mount } from "@vue/test-utils";
import { ref } from "vue";
import StaffNavigation from "../app/components/StaffNavigation.vue";
import { activeStaffLink, groupedStaffLinks, STAFF_GROUPS } from "../app/utils/staffNavigation";

afterEach(() => vi.unstubAllGlobals());
describe("organized staff navigation", () => {
  it("places every known page exactly once without generating unauthorized links", () => {
    const paths = STAFF_GROUPS.flatMap(group => group.paths);
    expect(new Set(paths).size).toBe(paths.length);
    const supplied = [{ to: "/tours", label: "Tours" }, { to: "/bookings", label: "Bookings" }];
    expect(groupedStaffLinks(supplied).flatMap(group => group.links)).toHaveLength(2);
    expect(groupedStaffLinks(supplied).flatMap(group => group.links).map(link => link.to).sort()).toEqual(["/bookings", "/tours"]);
  });
  it("searches visible names only, handles whitespace and removes empty groups", () => {
    expect(groupedStaffLinks([{ to: "/finance", label: "Finance" }], " FIN ")[0]!.links).toHaveLength(1);
    expect(groupedStaffLinks([{ to: "/today", label: "اليوم" }], "اليوم")[0]!.key).toBe("workspace.operations");
    expect(groupedStaffLinks([{ to: "/today", label: "Today" }], "<script>")).toEqual([]);
  });
  it("matches descendants without prefix collisions or activating overview everywhere", () => {
    expect(activeStaffLink("/bookings/new", "/bookings")).toBe(true);
    expect(activeStaffLink("/bookings-other", "/bookings")).toBe(false);
    expect(activeStaffLink("/tours/id/content", "/tours")).toBe(true);
    expect(activeStaffLink("/today", "/")).toBe(false);
  });
  for (const locale of ["en", "ar"]) it(`${locale}: provides a labeled search, current page and reversible empty state`, async () => {
    vi.stubGlobal("useCookie", () => ref(locale)); vi.stubGlobal("useState", (_: string, f: () => unknown) => ref(f()));
    vi.stubGlobal("useRoute", () => ({ path: "/today" }));
    const wrapper = mount(StaffNavigation, { props: { links: [{ to: "/today", label: locale === "ar" ? "اليوم" : "Today" }], searchId: "test-search" }, global: { stubs: { NuxtLink: { props: ["to"], template: '<a :href="to"><slot /></a>' } } } });
    expect(wrapper.get("a").attributes("aria-current")).toBe("page");
    expect(wrapper.get("label").attributes("for")).toBe("test-search");
    await wrapper.get("input").setValue("unknown"); expect(wrapper.findAll("a")).toHaveLength(0);
    expect(wrapper.get('[role="status"]').text()).toBeTruthy();
    await wrapper.get("button").trigger("click"); expect(wrapper.findAll("a")).toHaveLength(1);
  });
});
