import { describe, expect, it } from "vitest";
import { illustrativePhotoLabel, isIllustrativeTourPhoto } from "../app/utils/tourPhotoDisclosure";

describe("truthful tour-photo disclosure", () => {
  it("recognizes illustrative copy in all four launch languages", () => {
    expect(isIllustrativeTourPhoto("Illustrative aerial view of a speedboat.")).toBe(true);
    expect(isIllustrativeTourPhoto("صورة توضيحية لقارب سريع.")).toBe(true);
    expect(isIllustrativeTourPhoto("Иллюстративный вид на катер.")).toBe(true);
    expect(isIllustrativeTourPhoto("Vista aerea illustrativa di un motoscafo.")).toBe(true);
    expect(Object.keys(illustrativePhotoLabel)).toEqual(["en", "ar", "ru", "it"]);
  });

  it("does not label verified destination images as stock illustrations", () => {
    expect(isIllustrativeTourPhoto("Ancient columns at Karnak Temple in Luxor.")).toBe(false);
    expect(isIllustrativeTourPhoto("")).toBe(false);
    expect(isIllustrativeTourPhoto(null)).toBe(false);
  });
});
