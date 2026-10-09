import type { PublicSalesStatus } from "@wego/api-contract";
import { onlineSalesAvailable } from "../utils/enquiry";
import { discoveryCopy, type DiscoveryCopy } from "./discovery";
import { enquiryCopy } from "./enquiry";
import { infoCopy, type InfoCopy } from "./info";
import type { StsLocale } from "./locales";

/** Current capability determines promises, not whether a marketing page loaded. */
export const officeCopy = {
  en: {
    hero: "Desert safaris, Red Sea adventures and day trips with clear catalog prices and local support in Sharm El Sheikh. Choose your experience and preferred date; our office confirms the details.",
    priceTitle: "Clear catalog prices", price: "Check each tour’s price basis and inclusions. Our office confirms your selection and final total before you agree to book.",
    availabilityTitle: "Your date, checked by our team", availability: "A preferred date is a request, not a scheduled departure or a reserved seat. Our office checks availability before confirming.",
    localTitle: "Local support in Sharm", contact: "Discuss your tour, pickup details and payment arrangements directly with our team on WhatsApp.",
    policyTitle: "Clear cancellation terms", pickup: "Pickup and inclusions vary by tour. Read the tour details and confirm your hotel and meeting arrangements with our office.",
    booking: "Choose your tour, preferred date and guests, complete the contact form on the tour page and send your request. It is saved directly to our office system and you receive a request reference. No account or online payment is needed. WhatsApp is for questions only.",
    confirmation: "Your request reference confirms receipt, not reserved seats or payment. Our office checks availability and agrees the departure and current price with you before confirming the booking.",
    privacy: "Your request and contact details are saved in our booking system for authorized staff to follow up and organise your trip. WhatsApp is optional for questions and its own terms apply. Online payment is currently inactive; the request collects no card details and sends none to Paymob.",
    catalogue: "Search, filter and compare catalog prices in euros. Check the price basis on each tour and confirm your selection with our office.",
  },
  ar: {
    hero: "سفاري الصحراء، مغامرات البحر الأحمر ورحلات اليوم الواحد، بأسعار كتالوج واضحة ودعم محلي في شرم الشيخ. اختر تجربتك والتاريخ المناسب لك؛ المكتب يؤكد التفاصيل معك.",
    priceTitle: "أسعار كتالوج واضحة", price: "راجع سعر كل رحلة وأساس حسابه وما يشمله. المكتب يؤكد اختياراتك والإجمالي النهائي قبل الاتفاق على الحجز.",
    availabilityTitle: "تاريخك يراجعه فريقنا", availability: "التاريخ الذي تختاره طلب، وليس موعد انطلاق أو مقعدًا محجوزًا. المكتب يراجع الإتاحة قبل التأكيد.",
    localTitle: "دعم محلي في شرم", contact: "ناقش الرحلة وتفاصيل الاستلام وطريقة الدفع مباشرة مع فريقنا على واتساب.",
    policyTitle: "شروط إلغاء واضحة", pickup: "الاستلام وما يشمله السعر يختلفان حسب الرحلة. اقرأ التفاصيل وأكد الفندق ومكان الالتقاء مع المكتب.",
    booking: "اختر الرحلة والتاريخ المطلوب والضيوف، وأكمل بيانات التواصل في صفحة الرحلة ثم أرسل الطلب. يُحفظ مباشرة في نظام المكتب ويظهر لك رقم طلب. لا تحتاج حسابًا أو دفعًا أونلاين. واتساب للاستفسارات فقط.",
    confirmation: "رقم الطلب يؤكد الاستلام وليس حجز مقاعد أو دفعًا. المكتب يراجع الإتاحة ويتفق معك على الانطلاق والسعر الحالي قبل تأكيد الحجز.",
    privacy: "يُحفظ طلبك وبيانات التواصل في نظام الحجز ليتمكن الموظفون المخوّلون من المتابعة وتنظيم رحلتك. واتساب اختياري للاستفسارات وتسري شروطه على الرسائل عبره. الدفع الإلكتروني غير مفعّل؛ الطلب لا يجمع بيانات البطاقة ولا يرسلها إلى Paymob.",
    catalogue: "ابحث وقارن أسعار الكتالوج باليورو. راجع أساس السعر في كل رحلة وأكد اختياراتك مع المكتب.",
  },
  ru: {
    hero: "Сафари в пустыне, приключения на Красном море и однодневные поездки с понятными ценами каталога и местной поддержкой в Шарм-эль-Шейхе. Выберите экскурсию и удобную дату; офис подтвердит детали.",
    priceTitle: "Понятные цены каталога", price: "Проверьте, за что указана цена и что включено в тур. Офис подтвердит ваш выбор и окончательную сумму до согласования бронирования.",
    availabilityTitle: "Мы проверим вашу дату", availability: "Выбранная дата — это запрос, а не подтверждённый выезд или зарезервированное место. Офис проверит доступность перед подтверждением.",
    localTitle: "Местная поддержка в Шарме", contact: "Обсудите экскурсию, место встречи и способ оплаты напрямую с нашей командой в WhatsApp.",
    policyTitle: "Понятные условия отмены", pickup: "Трансфер и включённые услуги зависят от экскурсии. Прочитайте описание и уточните отель и место встречи в офисе.",
    booking: "Выберите экскурсию, желаемую дату и гостей, заполните контакты на странице экскурсии и отправьте запрос. Он сохранится прямо в системе офиса, и вы получите номер запроса. Аккаунт и онлайн-оплата не нужны. WhatsApp — только для вопросов.",
    confirmation: "Номер запроса подтверждает получение, но не резервирование мест или оплату. Офис проверит наличие мест и согласует с вами выезд и текущую цену до подтверждения бронирования.",
    privacy: "Запрос и контакты сохраняются в системе бронирования для уполномоченных сотрудников, которые организуют поездку. WhatsApp необязателен и используется для вопросов по его условиям. Онлайн-оплата отключена; запрос не собирает данные карты и не передаёт их Paymob.",
    catalogue: "Ищите и сравнивайте цены каталога в евро. Проверьте, за что указана цена каждого тура, и согласуйте свой выбор с офисом.",
  },
  it: {
    hero: "Safari nel deserto, avventure sul Mar Rosso e gite di un giorno con prezzi di catalogo chiari e assistenza locale a Sharm El Sheikh. Scegli l’esperienza e la data che preferisci; l’ufficio confermerà i dettagli.",
    priceTitle: "Prezzi di catalogo chiari", price: "Controlla la base del prezzo e cosa include ogni escursione. L’ufficio confermerà la scelta e il totale finale prima dell’accordo di prenotazione.",
    availabilityTitle: "Verifichiamo la tua data", availability: "La data scelta è una richiesta, non una partenza confermata o un posto riservato. L’ufficio verificherà la disponibilità prima di confermare.",
    localTitle: "Assistenza locale a Sharm", contact: "Concorda l’escursione, i dettagli del prelievo e le modalità di pagamento direttamente con il nostro team su WhatsApp.",
    policyTitle: "Condizioni di cancellazione chiare", pickup: "Prelievo e servizi inclusi variano per escursione. Leggi i dettagli e conferma hotel e punto di incontro con l’ufficio.",
    booking: "Scegli escursione, data preferita e ospiti, compila i recapiti sulla pagina dell’escursione e invia la richiesta. Viene salvata direttamente nel sistema dell’ufficio e ricevi un numero di riferimento. Non servono account né pagamento online. WhatsApp è solo per domande.",
    confirmation: "Il numero della richiesta conferma la ricezione, non posti riservati o pagamento. L’ufficio verifica la disponibilità e concorda partenza e prezzo attuale prima di confermare la prenotazione.",
    privacy: "Richiesta e recapiti vengono salvati nel sistema di prenotazione per il personale autorizzato che organizza l’escursione. WhatsApp è facoltativo per domande e si applicano le sue condizioni. Pagamento online disattivato; la richiesta non raccoglie dati della carta né li invia a Paymob.",
    catalogue: "Cerca e confronta i prezzi di catalogo in euro. Controlla la base del prezzo di ogni escursione e conferma la tua scelta con l’ufficio.",
  },
} as const;

/** Unknown/paused paid checkout must not advertise a request form that is not rendered. */
export const unavailableCopy = {
  en: { booking: "Booking submission is currently unavailable. Browse the tours and use WhatsApp for questions; do not treat a message as a confirmed booking.", confirmation: "No booking or payment is confirmed by this page. Contact our office to check your arrangements." },
  ar: { booking: "إرسال الحجز غير متاح حاليًا. تصفح الرحلات واستخدم واتساب للاستفسار؛ الرسالة ليست حجزًا مؤكدًا.", confirmation: "هذه الصفحة لا تؤكد حجزًا أو دفعًا. تواصل مع المكتب للاستفسار عن ترتيباتك." },
  ru: { booking: "Отправка бронирования сейчас недоступна. Посмотрите экскурсии и задайте вопросы в WhatsApp; сообщение не подтверждает бронирование.", confirmation: "Эта страница не подтверждает бронирование или оплату. Уточните детали в офисе." },
  it: { booking: "L’invio della prenotazione non è disponibile. Consulta le escursioni e usa WhatsApp per domande; un messaggio non conferma la prenotazione.", confirmation: "Questa pagina non conferma prenotazioni o pagamenti. Contatta l’ufficio per informazioni." },
} as const;

function requestAwareCopy(locale: StsLocale, sales: PublicSalesStatus | null | undefined) {
  return sales?.bookingMode === "ENQUIRY_ONLY" ? officeCopy[locale] : { ...officeCopy[locale], ...unavailableCopy[locale] };
}

export function discoveryForSales(locale: StsLocale, sales: PublicSalesStatus | null | undefined): DiscoveryCopy {
  const base = discoveryCopy[locale];
  if (onlineSalesAvailable(sales)) return base;
  const c = requestAwareCopy(locale, sales);
  return {
    ...base,
    hero: { ...base.hero, body: c.hero },
    categories: { ...base.categories, body: c.pickup },
    why: { ...base.why, items: [
      { icon: "lucide:badge-euro", title: c.priceTitle, body: c.price },
      { icon: "lucide:calendar-check", title: c.availabilityTitle, body: c.availability },
      { icon: "lucide:message-circle", title: c.localTitle, body: c.contact },
      { icon: "lucide:clock", title: c.policyTitle, body: base.faq.items[2]!.body },
    ] },
    faq: { ...base.faq, items: base.faq.items.map((item, i) => ({ ...item, body: i === 0 ? c.pickup : i === 1 ? c.confirmation : item.body })) },
    tours: { ...base.tours, body: c.catalogue },
  };
}

export function infoForSales(locale: StsLocale, sales: PublicSalesStatus | null | undefined): InfoCopy {
  const base = infoCopy[locale];
  if (onlineSalesAvailable(sales)) return base;
  const c = requestAwareCopy(locale, sales);
  return {
    ...base,
    about: { ...base.about, sections: [
      { heading: base.about.sections[0]!.heading, body: [c.contact] },
      { heading: c.priceTitle, body: [c.price] },
      { heading: c.availabilityTitle, body: [c.availability, c.confirmation] },
    ] },
    faq: { ...base.faq, items: base.faq.items.map((item, i) => ({ ...item, body: i === 1 ? c.booking : i === 2 ? c.availability : i === 4 ? c.confirmation : item.body })) },
    terms: { ...base.terms, sections: base.terms.sections.map((section, i) =>
      i === 1 ? { ...section, body: [c.price, sales?.bookingMode === "ENQUIRY_ONLY" ? enquiryCopy[locale].notice : unavailableCopy[locale].booking] }
        : i === 2 ? { ...section, body: [c.booking, c.confirmation, c.pickup] } : section,
    ) },
    privacy: { ...base.privacy, sections: base.privacy.sections.map((section, i) => i === 2 ? { ...section, body: [c.privacy] } : section) },
  };
}
