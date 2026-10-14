import { readFileSync, statSync } from "node:fs";
import { mount, flushPromises } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import HeroMedia from "../app/components/brand/HeroMedia.vue";
import { allowHeroVideo } from "../app/utils/heroPlayback";
vi.mock("../app/composables/useSiteLocale", () => ({ useSiteLocale: () => ref("en") }));
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

describe("lightweight Safari hero", () => {
  it("never autoloads on mobile, reduced motion or a constrained connection", () => {
    expect(allowHeroVideo(true, false)).toBe(true);
    expect(allowHeroVideo(false, false)).toBe(false);
    expect(allowHeroVideo(true, true)).toBe(false);
    expect(allowHeroVideo(true, false, { saveData: true })).toBe(false);
    for (const effectiveType of ["slow-2g", "2g", "3g"]) expect(allowHeroVideo(true, false, { effectiveType })).toBe(false);
  });
  it("renders the poster first, delays video, offers pause and returns to poster on failure", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("matchMedia", (query: string) => ({ matches: query.includes("min-width"), addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    const play = vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue();
    const pause = vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
    const wrapper = mount(HeroMedia, { global: { stubs: { Icon: true } } });
    expect(wrapper.find("img").attributes("fetchpriority")).toBe("high");
    expect(wrapper.find("video").exists()).toBe(false);
    await vi.advanceTimersByTimeAsync(1800);
    await flushPromises();
    const video = wrapper.find("video");
    expect(video.attributes("preload")).toBe("none");
    expect(video.attributes("playsinline")).toBeDefined();
    expect(play).toHaveBeenCalledOnce();
    await video.trigger("playing");
    expect(wrapper.find("button").attributes("aria-label")).toContain("Pause");
    await wrapper.find("button").trigger("click");
    expect(pause).toHaveBeenCalledOnce();
    await video.trigger("error");
    expect(wrapper.find("video").exists()).toBe(false);
    expect(wrapper.find("img").exists()).toBe(true);
    wrapper.unmount();
  });
  it("ships a small local video and real optimized poster without third-party embeds", () => {
    expect(statSync(`${process.cwd()}/public/videos/safari-hero-20261015.mp4`).size).toBeLessThan(3_000_000);
    for (const name of ["safari-hero-poster.webp", "safari-hero-poster-640.webp"]) {
      const bytes = readFileSync(`${process.cwd()}/public/videos/${name}`);
      expect(bytes.length).toBeLessThan(100_000);
      expect(bytes.subarray(8, 12).toString()).toBe("WEBP");
    }
  });
});
