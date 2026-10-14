import type { StsLocale } from "./locales";

interface ReviewRequestCopy {
  eyebrow: string;
  title: string;
  body: string;
  note: string;
  google: string;
  tripadvisor: string;
}

export const reviewRequestCopy: Record<StsLocale, ReviewRequestCopy> = {
  en: {
    eyebrow: "After your trip",
    title: "Share your experience",
    body: "If you travelled with us, your honest feedback helps other guests make an informed choice and helps us improve.",
    note: "Positive or negative, your experience is yours to tell. There is no reward for leaving a review.",
    google: "Review on Google",
    tripadvisor: "Review on Tripadvisor",
  },
  ar: {
    eyebrow: "بعد رحلتك",
    title: "شاركنا تجربتك",
    body: "لو سافرت معانا، رأيك الصادق يساعد ضيوفًا آخرين يختاروا بوضوح ويساعدنا نتحسن.",
    note: "سواء كانت تجربتك إيجابية أو سلبية، اكتبها كما حدثت. لا توجد مكافأة مقابل التقييم.",
    google: "قيّمنا على جوجل",
    tripadvisor: "قيّمنا على تريب أدفايزر",
  },
  ru: {
    eyebrow: "После поездки",
    title: "Расскажите о своей поездке",
    body: "Если вы путешествовали с нами, ваш честный отзыв поможет другим гостям выбрать поездку, а нам — стать лучше.",
    note: "Расскажите о впечатлениях такими, какими они были. Вознаграждения за отзыв нет.",
    google: "Оставить отзыв в Google",
    tripadvisor: "Оставить отзыв в Tripadvisor",
  },
  it: {
    eyebrow: "Dopo l'escursione",
    title: "Racconta la tua esperienza",
    body: "Se hai viaggiato con noi, la tua recensione sincera aiuta altri ospiti a scegliere e noi a migliorare.",
    note: "Positiva o negativa, racconta la tua esperienza com'è stata. Non offriamo ricompense per le recensioni.",
    google: "Recensisci su Google",
    tripadvisor: "Recensisci su Tripadvisor",
  },
};
