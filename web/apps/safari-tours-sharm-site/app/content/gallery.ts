import type { StsLocale } from "./locales";

/** A photo-led route, not a promise that every pictured moment occurs on every departure. */
export const galleryCopy: Record<StsLocale, {
  title: string; intro: string; note: string; tour: string; category: string; all: string; empty: string;
}> = {
  en: {
    title: "Moments from Sharm & Sinai",
    intro: "Explore the desert, Red Sea and day trips through photographs from our experience library. Open a tour for its current details and price.",
    note: "Photos show the place or type of experience. The exact route, services and departure are described on each tour page and confirmed by the office.",
    tour: "Explore this tour", category: "See category", all: "Browse all tours", empty: "The gallery is being prepared. Browse current tours in the meantime.",
  },
  ar: {
    title: "لحظات من شرم وسيناء",
    intro: "شاهد الصحراء والبحر الأحمر والرحلات اليومية من خلال صور مكتبة تجاربنا. افتح صفحة الرحلة لمعرفة التفاصيل والسعر الحاليين.",
    note: "الصور تبيّن المكان أو نوع التجربة؛ المسار والخدمات والانطلاق الفعلي موضّحة في صفحة كل رحلة ويؤكدها المكتب.",
    tour: "اكتشف هذه الرحلة", category: "شاهد التصنيف", all: "تصفح كل الرحلات", empty: "يجري تجهيز المعرض. يمكنك تصفح الرحلات الحالية الآن.",
  },
  ru: {
    title: "Моменты Шарма и Синая",
    intro: "Пустыня, Красное море и поездки на день — в фотографиях из нашей библиотеки впечатлений. Откройте тур, чтобы узнать актуальные детали и цену.",
    note: "Фото показывают место или вид впечатления. Точный маршрут, услуги и выезд указаны на странице тура и подтверждаются офисом.",
    tour: "Смотреть тур", category: "Все в категории", all: "Все туры", empty: "Галерея готовится. Пока вы можете посмотреть действующие туры.",
  },
  it: {
    title: "Momenti da Sharm e dal Sinai",
    intro: "Scopri il deserto, il Mar Rosso e le gite di un giorno attraverso le foto della nostra raccolta. Apri un tour per i dettagli e il prezzo attuali.",
    note: "Le foto mostrano il luogo o il tipo di esperienza. Itinerario, servizi e partenza effettivi sono descritti nella pagina del tour e confermati dall'ufficio.",
    tour: "Scopri il tour", category: "Vedi categoria", all: "Esplora tutti i tour", empty: "La galleria è in preparazione. Intanto puoi esplorare i tour disponibili.",
  },
};
