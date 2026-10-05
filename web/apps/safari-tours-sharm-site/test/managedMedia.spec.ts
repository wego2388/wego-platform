import { describe, expect, it } from "vitest";
import { isManagedMediaPath, isManagedOptimizerRequest, managedMediaSrcset } from "../app/utils/managedMedia";

const path = "/media/tours/test-tour/12345678-1234-1234-1234-123456789abc.jpg";
describe("managed image rights boundary", () => {
  it("recognizes only exact local generated images", () => {
    expect(isManagedMediaPath(path)).toBe(true);
    expect(isManagedMediaPath(path.replace("tours/test-tour", "categories/SEA"))).toBe(true);
    for (const invalid of [null, "https://example.test" + path, path + "?token=secret", path + "/..", path.replace(".jpg", ".svg"), "/media/tours/test-tour/legacy-cover.jpg"]) expect(isManagedMediaPath(invalid)).toBe(false);
  });
  it("does not ask for upscaled or nonexistent derivatives", () => {
    expect(managedMediaSrcset(path, 600)).toBe(`${path}?v=w360 360w, ${path} 600w`);
    expect(managedMediaSrcset(path, 360)).toBe(`${path} 360w`);
    expect(managedMediaSrcset(path, undefined)).toBeUndefined();
    expect(managedMediaSrcset(path, 20000)).toBeUndefined();
  });
  it("blocks managed IPX routes including encoded forms without blocking static images", () => {
    expect(isManagedOptimizerRequest("/_ipx/w_360" + path)).toBe(true);
    expect(isManagedOptimizerRequest("/_ipx/w_360/" + encodeURIComponent(path))).toBe(true);
    expect(isManagedOptimizerRequest("/_ipx/w_360/" + encodeURIComponent(encodeURIComponent(path)))).toBe(true);
    for (const source of [path + "?v=w360", path + "#image", "https://example.test" + path + "?v=w360"]) {
      expect(isManagedOptimizerRequest("/_ipx/w_360/" + encodeURIComponent(source))).toBe(true);
    }
    expect(isManagedOptimizerRequest(path)).toBe(false);
    expect(isManagedOptimizerRequest("/_ipx/w_360/media/tours/test/legacy-cover.jpg")).toBe(false);
  });
});
