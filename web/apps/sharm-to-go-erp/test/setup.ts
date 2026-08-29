import { beforeEach, vi } from "vitest";

beforeEach(() => {
  vi.stubGlobal("useHead", () => {});
});
