/** A stock/activity photo is not proof of the actual boat, vehicle or guests. */
export function isIllustrativeTourPhoto(alt: string | null | undefined): boolean {
  if (!alt) return false;
  return /\b(?:illustrative|illustrativa|illustrativo)\b|صورة توضيحية|Иллюстративн/iu.test(alt);
}

export const illustrativePhotoLabel = {
  en: "Illustrative photo",
  ar: "صورة توضيحية",
  ru: "Иллюстративное фото",
  it: "Foto illustrativa",
} as const;
