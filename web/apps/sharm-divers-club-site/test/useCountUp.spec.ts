import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import { defineComponent, h } from "vue";
import { useCountUp } from "../app/composables/useCountUp";

function mountCountUp(target: number) {
  let exposed!: ReturnType<typeof useCountUp>;
  const wrapper = mount(
    defineComponent({
      setup() {
        exposed = useCountUp(target);
        return () => h("div", { ref: exposed.el });
      },
    }),
  );
  return { wrapper, get value() {
    return exposed.value.value;
  } };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("useCountUp", () => {
  it("resolves to the target immediately under prefers-reduced-motion, without needing the element to ever intersect", () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({ matches: true })));
    let observeCalled = false;
    vi.stubGlobal(
      "IntersectionObserver",
      class {
        observe() {
          observeCalled = true;
        }
        disconnect() {}
      },
    );

    const { value } = mountCountUp(7);

    // The bug this fixes: the old code only checked reduced-motion inside
    // animate(), which only ran once an intersection fired — a reduced-motion
    // visitor whose element never crossed the 40% threshold (e.g. mobile's
    // taller hero reflow) was stuck at 0 forever. It must resolve to the
    // real target on mount, and never even need to set up the observer.
    expect(value).toBe(7);
    expect(observeCalled).toBe(false);
  });

  it("starts at 0 and waits for intersection when motion is not reduced", () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({ matches: false })));
    vi.stubGlobal(
      "IntersectionObserver",
      class {
        observe() {}
        disconnect() {}
      },
    );

    const { value } = mountCountUp(7);

    expect(value).toBe(0);
  });
});
