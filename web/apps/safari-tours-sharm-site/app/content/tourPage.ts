import type { StsLocale } from "./locales";

/**
 * Interface copy for the tour page and its booking card (UX-3). EN and AR are
 * final drafts; RU and IT await native review before launch. Everything about
 * the tour itself (story, inclusions, meeting point…) comes from published
 * tour content, never from here.
 */
export interface TourPageCopy {
  breadcrumbHome: string;
  about: string;
  facts: {
    heading: string; duration: string; times: string; children: string; languages: string; pickup: string;
    childrenYes: string; childrenNo: string; minimumAge: (age: number) => string;
    pickupIncluded: string; pickupNotIncluded: string; pickupSomeAreas: string;
  };
  itinerary: string;
  meetingPoint: string;
  includes: string;
  excludes: string;
  know: string;
  gallery: { heading: string; open: (n: number) => string; close: string; previous: string; next: string; counter: (i: number, n: number) => string };
  policy: { heading: string; STANDARD: string[]; FLEXIBLE: string[]; NON_REFUNDABLE: string[] };
  similar: string;
  contentPending: string;
  booking: {
    heading: string; from: string; perPerson: string; perVehicle: string;
    date: string; time: string; guests: string; adults: string; children: string;
    childPriceOnRequest: string; childrenNotAllowed: string;
    left: (n: number) => string; soldOut: string;
    previousMonth: string; nextMonth: string; available: string;
    loading: string; loadError: string; retry: string;
    noDatesTitle: string; noDatesBody: string;
    pickDate: string; pickTime: string;
    adultLine: (n: number) => string; childLine: (n: number) => string;
    total: string; continue: string; askWhatsapp: string; mobileBar: string; close: string;
    whatsappMessage: (tour: string, date: string | null, adults: number, children: number) => string;
  };
  requestOnly: { heading: string; body: string; cta: string };
  notFound: { title: string; body: string; back: string };
}

const en: TourPageCopy = {
  breadcrumbHome: "Home",
  about: "About this tour",
  facts: {
    heading: "At a glance", duration: "Duration", times: "Departures", children: "Children", languages: "Guide languages", pickup: "Hotel pickup",
    childrenYes: "Welcome", childrenNo: "Adults only", minimumAge: (age) => `From age ${age}`,
    pickupIncluded: "Included", pickupNotIncluded: "Not included", pickupSomeAreas: "Included from some areas",
  },
  itinerary: "Itinerary",
  meetingPoint: "Meeting point",
  includes: "What's included",
  excludes: "Not included",
  know: "Know before you go",
  gallery: {
    heading: "Photos", open: (n) => `Open photo ${n}`, close: "Close", previous: "Previous photo", next: "Next photo",
    counter: (i, n) => `${i} of ${n}`,
  },
  policy: {
    heading: "Cancellation policy",
    STANDARD: ["Full refund if you cancel at least 48 hours before the tour.", "50% refund if you cancel 24–48 hours before.", "No refund for later cancellations or no-shows."],
    FLEXIBLE: ["Free cancellation up to 24 hours before the tour.", "No refund for later cancellations or no-shows."],
    NON_REFUNDABLE: ["This tour cannot be refunded once booked."],
  },
  similar: "You may also like",
  contentPending: "The full description of this tour is being prepared. Ask us anything on WhatsApp — we reply quickly.",
  booking: {
    heading: "Book this tour", from: "From", perPerson: "per person", perVehicle: "per vehicle",
    date: "Choose a date", time: "Choose a time", guests: "Guests", adults: "Adults", children: "Children",
    childPriceOnRequest: "Child price on request — ask us on WhatsApp.", childrenNotAllowed: "This tour is for adults only.",
    left: (n) => (n === 1 ? "1 seat left" : `${n} seats left`), soldOut: "Full",
    previousMonth: "Previous month", nextMonth: "Next month", available: "Available",
    loading: "Checking availability…", loadError: "We could not load the dates.", retry: "Try again",
    noDatesTitle: "No online dates in the next 60 days", noDatesBody: "Message us on WhatsApp — we can often still arrange it.",
    pickDate: "Pick a highlighted day to see departure times.", pickTime: "Choose a departure time.",
    adultLine: (n) => (n === 1 ? "1 adult" : `${n} adults`), childLine: (n) => (n === 1 ? "1 child" : `${n} children`),
    total: "Total", continue: "Continue to booking", askWhatsapp: "Ask on WhatsApp", mobileBar: "Check dates", close: "Close",
    whatsappMessage: (tour, date, adults, children) =>
      `Hello! I'd like to book "${tour}"${date ? ` on ${date}` : ""} for ${adults} adult(s)${children ? ` and ${children} child(ren)` : ""}.`,
  },
  requestOnly: { heading: "Booked on request", body: "Tell us your date and group size and we will confirm availability and price.", cta: "Request on WhatsApp" },
  notFound: { title: "Tour not found", body: "This tour is not available right now.", back: "See all tours" },
};

const ar: TourPageCopy = {
  breadcrumbHome: "الرئيسية",
  about: "عن الرحلة",
  facts: {
    heading: "نظرة سريعة", duration: "المدة", times: "مواعيد الانطلاق", children: "الأطفال", languages: "لغات المرشد", pickup: "الاستلام من الفندق",
    childrenYes: "مرحّب بهم", childrenNo: "للبالغين فقط", minimumAge: (age) => `من عمر ${age} سنة`,
    pickupIncluded: "مشمول", pickupNotIncluded: "غير مشمول", pickupSomeAreas: "مشمول من بعض المناطق",
  },
  itinerary: "برنامج الرحلة",
  meetingPoint: "نقطة التجمّع",
  includes: "يشمل",
  excludes: "لا يشمل",
  know: "معلومات قبل الرحلة",
  gallery: {
    heading: "الصور", open: (n) => `فتح الصورة ${n}`, close: "إغلاق", previous: "الصورة السابقة", next: "الصورة التالية",
    counter: (i, n) => `${i} من ${n}`,
  },
  policy: {
    heading: "سياسة الإلغاء",
    STANDARD: ["استرداد كامل عند الإلغاء قبل الرحلة بـ48 ساعة على الأقل.", "استرداد 50% عند الإلغاء قبل الرحلة بين 24 و48 ساعة.", "لا استرداد للإلغاء المتأخر أو عدم الحضور."],
    FLEXIBLE: ["إلغاء مجاني حتى 24 ساعة قبل الرحلة.", "لا استرداد للإلغاء المتأخر أو عدم الحضور."],
    NON_REFUNDABLE: ["لا يمكن استرداد قيمة هذه الرحلة بعد الحجز."],
  },
  similar: "رحلات قد تعجبك",
  contentPending: "الوصف الكامل لهذه الرحلة قيد التجهيز. اسألنا أي سؤال على واتساب — نرد بسرعة.",
  booking: {
    heading: "احجز الرحلة", from: "ابتداءً من", perPerson: "للفرد", perVehicle: "للسيارة",
    date: "اختر اليوم", time: "اختر الموعد", guests: "عدد الأفراد", adults: "بالغون", children: "أطفال",
    childPriceOnRequest: "سعر الأطفال عند الطلب — اسألنا على واتساب.", childrenNotAllowed: "هذه الرحلة للبالغين فقط.",
    left: (n) => (n === 1 ? "باقي مكان واحد" : n === 2 ? "باقي مكانان" : n <= 10 ? `باقي ${n} أماكن` : `باقي ${n} مكانًا`), soldOut: "مكتمل",
    previousMonth: "الشهر السابق", nextMonth: "الشهر التالي", available: "متاح",
    loading: "جارٍ التحقق من الأماكن المتاحة…", loadError: "تعذّر تحميل المواعيد.", retry: "حاول مرة أخرى",
    noDatesTitle: "لا توجد مواعيد متاحة أونلاين خلال 60 يومًا", noDatesBody: "راسلنا على واتساب — غالبًا يمكننا ترتيبها لك.",
    pickDate: "اختر يومًا من الأيام المميّزة لتظهر المواعيد.", pickTime: "اختر موعد الانطلاق.",
    adultLine: (n) => (n === 1 ? "بالغ واحد" : n === 2 ? "بالغان" : `${n} بالغين`), childLine: (n) => (n === 1 ? "طفل واحد" : n === 2 ? "طفلان" : `${n} أطفال`),
    total: "الإجمالي", continue: "متابعة الحجز", askWhatsapp: "اسألنا على واتساب", mobileBar: "اعرض المواعيد", close: "إغلاق",
    whatsappMessage: (tour, date, adults, children) =>
      `مرحبًا! أريد حجز «${tour}»${date ? ` يوم ${date}` : ""} لعدد ${adults} بالغ${children ? ` و${children} طفل` : ""}.`,
  },
  requestOnly: { heading: "الحجز عند الطلب", body: "أخبرنا بالتاريخ وعدد الأفراد، ونؤكد لك التوفر والسعر.", cta: "اطلبها على واتساب" },
  notFound: { title: "الرحلة غير موجودة", body: "هذه الرحلة غير متاحة حاليًا.", back: "كل الرحلات" },
};

function ruPlural(n: number, one: string, few: string, many: string): string {
  if (n % 10 === 1 && n % 100 !== 11) return one;
  if (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14)) return few;
  return many;
}

const ru: TourPageCopy = {
  breadcrumbHome: "Главная",
  about: "О туре",
  facts: {
    heading: "Коротко", duration: "Длительность", times: "Отправление", children: "Дети", languages: "Языки гида", pickup: "Трансфер из отеля",
    childrenYes: "Можно с детьми", childrenNo: "Только для взрослых", minimumAge: (age) => `С ${age} лет`,
    pickupIncluded: "Включён", pickupNotIncluded: "Не включён", pickupSomeAreas: "Включён из некоторых районов",
  },
  itinerary: "Программа",
  meetingPoint: "Место встречи",
  includes: "Включено",
  excludes: "Не включено",
  know: "Полезно знать",
  gallery: {
    heading: "Фото", open: (n) => `Открыть фото ${n}`, close: "Закрыть", previous: "Предыдущее фото", next: "Следующее фото",
    counter: (i, n) => `${i} из ${n}`,
  },
  policy: {
    heading: "Условия отмены",
    STANDARD: ["Полный возврат при отмене не позднее чем за 48 часов.", "Возврат 50% при отмене за 24–48 часов.", "Более поздняя отмена и неявка не возвращаются."],
    FLEXIBLE: ["Бесплатная отмена не позднее чем за 24 часа.", "Более поздняя отмена и неявка не возвращаются."],
    NON_REFUNDABLE: ["Стоимость этого тура не возвращается после бронирования."],
  },
  similar: "Вам также может понравиться",
  contentPending: "Полное описание тура готовится. Задайте любой вопрос в WhatsApp — мы быстро ответим.",
  booking: {
    heading: "Забронировать тур", from: "От", perPerson: "за человека", perVehicle: "за автомобиль",
    date: "Выберите дату", time: "Выберите время", guests: "Гости", adults: "Взрослые", children: "Дети",
    childPriceOnRequest: "Цена для детей по запросу — напишите нам в WhatsApp.", childrenNotAllowed: "Этот тур только для взрослых.",
    left: (n) => `Осталось ${n} ${ruPlural(n, "место", "места", "мест")}`, soldOut: "Мест нет",
    previousMonth: "Предыдущий месяц", nextMonth: "Следующий месяц", available: "Есть места",
    loading: "Проверяем наличие мест…", loadError: "Не удалось загрузить даты.", retry: "Повторить",
    noDatesTitle: "Нет онлайн-дат на ближайшие 60 дней", noDatesBody: "Напишите нам в WhatsApp — часто это можно организовать.",
    pickDate: "Выберите отмеченный день, чтобы увидеть время.", pickTime: "Выберите время отправления.",
    adultLine: (n) => `${n} ${ruPlural(n, "взрослый", "взрослых", "взрослых")}`, childLine: (n) => `${n} ${ruPlural(n, "ребёнок", "ребёнка", "детей")}`,
    total: "Итого", continue: "Перейти к бронированию", askWhatsapp: "Спросить в WhatsApp", mobileBar: "Выбрать дату", close: "Закрыть",
    whatsappMessage: (tour, date, adults, children) =>
      `Здравствуйте! Хочу забронировать «${tour}»${date ? ` на ${date}` : ""}: взрослых — ${adults}${children ? `, детей — ${children}` : ""}.`,
  },
  requestOnly: { heading: "Бронирование по запросу", body: "Сообщите дату и количество человек — мы подтвердим наличие и цену.", cta: "Запросить в WhatsApp" },
  notFound: { title: "Тур не найден", body: "Этот тур сейчас недоступен.", back: "Все туры" },
};

const it: TourPageCopy = {
  breadcrumbHome: "Home",
  about: "L'escursione",
  facts: {
    heading: "In breve", duration: "Durata", times: "Partenze", children: "Bambini", languages: "Lingue della guida", pickup: "Prelievo in hotel",
    childrenYes: "Benvenuti", childrenNo: "Solo adulti", minimumAge: (age) => `Dai ${age} anni`,
    pickupIncluded: "Incluso", pickupNotIncluded: "Non incluso", pickupSomeAreas: "Incluso da alcune zone",
  },
  itinerary: "Programma",
  meetingPoint: "Punto d'incontro",
  includes: "Cosa è incluso",
  excludes: "Non incluso",
  know: "Da sapere prima di partire",
  gallery: {
    heading: "Foto", open: (n) => `Apri foto ${n}`, close: "Chiudi", previous: "Foto precedente", next: "Foto successiva",
    counter: (i, n) => `${i} di ${n}`,
  },
  policy: {
    heading: "Politica di cancellazione",
    STANDARD: ["Rimborso totale se cancelli almeno 48 ore prima.", "Rimborso del 50% se cancelli tra 24 e 48 ore prima.", "Nessun rimborso per cancellazioni successive o mancata presentazione."],
    FLEXIBLE: ["Cancellazione gratuita fino a 24 ore prima.", "Nessun rimborso per cancellazioni successive o mancata presentazione."],
    NON_REFUNDABLE: ["Questa escursione non è rimborsabile dopo la prenotazione."],
  },
  similar: "Potrebbe piacerti anche",
  contentPending: "La descrizione completa di questa escursione è in preparazione. Chiedici tutto su WhatsApp — rispondiamo in fretta.",
  booking: {
    heading: "Prenota l'escursione", from: "Da", perPerson: "a persona", perVehicle: "a veicolo",
    date: "Scegli la data", time: "Scegli l'orario", guests: "Partecipanti", adults: "Adulti", children: "Bambini",
    childPriceOnRequest: "Prezzo bambini su richiesta — chiedici su WhatsApp.", childrenNotAllowed: "Questa escursione è solo per adulti.",
    left: (n) => (n === 1 ? "1 posto rimasto" : `${n} posti rimasti`), soldOut: "Completo",
    previousMonth: "Mese precedente", nextMonth: "Mese successivo", available: "Disponibile",
    loading: "Verifichiamo la disponibilità…", loadError: "Impossibile caricare le date.", retry: "Riprova",
    noDatesTitle: "Nessuna data online nei prossimi 60 giorni", noDatesBody: "Scrivici su WhatsApp — spesso possiamo organizzarla comunque.",
    pickDate: "Scegli un giorno evidenziato per vedere gli orari.", pickTime: "Scegli l'orario di partenza.",
    adultLine: (n) => (n === 1 ? "1 adulto" : `${n} adulti`), childLine: (n) => (n === 1 ? "1 bambino" : `${n} bambini`),
    total: "Totale", continue: "Continua la prenotazione", askWhatsapp: "Chiedi su WhatsApp", mobileBar: "Vedi le date", close: "Chiudi",
    whatsappMessage: (tour, date, adults, children) =>
      `Ciao! Vorrei prenotare "${tour}"${date ? ` il ${date}` : ""} per ${adults === 1 ? "1 adulto" : `${adults} adulti`}${children ? ` e ${children === 1 ? "1 bambino" : `${children} bambini`}` : ""}.`,
  },
  requestOnly: { heading: "Su richiesta", body: "Dicci data e numero di persone: ti confermiamo disponibilità e prezzo.", cta: "Richiedi su WhatsApp" },
  notFound: { title: "Escursione non trovata", body: "Questa escursione non è disponibile al momento.", back: "Tutte le escursioni" },
};

export const tourPageCopy: Record<StsLocale, TourPageCopy> = { en, ar, ru, it };
