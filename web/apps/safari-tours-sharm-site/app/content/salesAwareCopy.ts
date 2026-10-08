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
    booking: "Choose the tour, preferred date and number of guests, then send the prepared WhatsApp request. No account or online payment is needed. Our office confirms availability, the final price and payment arrangements; sending a message does not reserve places.",
    confirmation: "Your request is confirmed only when our office has checked availability and explicitly confirmed the booking details with you. Sending a WhatsApp message alone is not a confirmed or paid booking.",
    privacy: "Our staff use the details you provide to organise your trip. WhatsApp’s own terms apply to messages sent through WhatsApp. Online payment is currently inactive; this request flow does not collect card details or send them to Paymob.",
    catalogue: "Search, filter and compare catalog prices in euros. Check the price basis on each tour and confirm your selection with our office.",
  },
  ar: {
    hero: "سفاري الصحراء، مغامرات البحر الأحمر ورحلات اليوم الواحد، بأسعار كتالوج واضحة ودعم محلي في شرم الشيخ. اختر تجربتك والتاريخ المناسب لك؛ المكتب يؤكد التفاصيل معك.",
    priceTitle: "أسعار كتالوج واضحة", price: "راجع سعر كل رحلة وأساس حسابه وما يشمله. المكتب يؤكد اختياراتك والإجمالي النهائي قبل الاتفاق على الحجز.",
    availabilityTitle: "تاريخك يراجعه فريقنا", availability: "التاريخ الذي تختاره طلب، وليس موعد انطلاق أو مقعدًا محجوزًا. المكتب يراجع الإتاحة قبل التأكيد.",
    localTitle: "دعم محلي في شرم", contact: "ناقش الرحلة وتفاصيل الاستلام وطريقة الدفع مباشرة مع فريقنا على واتساب.",
    policyTitle: "شروط إلغاء واضحة", pickup: "الاستلام وما يشمله السعر يختلفان حسب الرحلة. اقرأ التفاصيل وأكد الفندق ومكان الالتقاء مع المكتب.",
    booking: "اختر الرحلة والتاريخ المطلوب وعدد الأشخاص، ثم أرسل طلب واتساب الجاهز. لا تحتاج حسابًا أو دفعًا أونلاين. المكتب يؤكد الإتاحة والسعر النهائي وطريقة الدفع؛ إرسال الرسالة لا يحجز أماكن.",
    confirmation: "يتأكد الطلب بعد مراجعة المكتب للإتاحة وتأكيد تفاصيل الحجز معك صراحةً. إرسال رسالة واتساب وحده ليس حجزًا مؤكدًا أو مدفوعًا.",
    privacy: "يستخدم موظفونا البيانات التي تقدمها لتنظيم رحلتك. تسري شروط واتساب على الرسائل عبره. الدفع الإلكتروني غير مفعّل حاليًا؛ مسار الطلب لا يجمع بيانات البطاقة ولا يرسلها إلى Paymob.",
    catalogue: "ابحث وقارن أسعار الكتالوج باليورو. راجع أساس السعر في كل رحلة وأكد اختياراتك مع المكتب.",
  },
  ru: {
    hero: "Сафари в пустыне, приключения на Красном море и однодневные поездки с понятными ценами каталога и местной поддержкой в Шарм-эль-Шейхе. Выберите экскурсию и удобную дату; офис подтвердит детали.",
    priceTitle: "Понятные цены каталога", price: "Проверьте, за что указана цена и что включено в тур. Офис подтвердит ваш выбор и окончательную сумму до согласования бронирования.",
    availabilityTitle: "Мы проверим вашу дату", availability: "Выбранная дата — это запрос, а не подтверждённый выезд или зарезервированное место. Офис проверит доступность перед подтверждением.",
    localTitle: "Местная поддержка в Шарме", contact: "Обсудите экскурсию, место встречи и способ оплаты напрямую с нашей командой в WhatsApp.",
    policyTitle: "Понятные условия отмены", pickup: "Трансфер и включённые услуги зависят от экскурсии. Прочитайте описание и уточните отель и место встречи в офисе.",
    booking: "Выберите экскурсию, желаемую дату и количество гостей, затем отправьте подготовленный запрос в WhatsApp. Аккаунт и онлайн-оплата не нужны. Офис подтвердит наличие мест, окончательную цену и способ оплаты; сообщение не резервирует места.",
    confirmation: "Запрос подтверждён только после проверки наличия мест офисом и явного подтверждения деталей бронирования. Одно сообщение в WhatsApp не означает подтверждённое или оплаченное бронирование.",
    privacy: "Наши сотрудники используют предоставленные вами данные для организации поездки. К переписке в WhatsApp применяются его условия. Онлайн-оплата сейчас отключена; запрос не собирает данные карты и не передаёт их Paymob.",
    catalogue: "Ищите и сравнивайте цены каталога в евро. Проверьте, за что указана цена каждого тура, и согласуйте свой выбор с офисом.",
  },
  it: {
    hero: "Safari nel deserto, avventure sul Mar Rosso e gite di un giorno con prezzi di catalogo chiari e assistenza locale a Sharm El Sheikh. Scegli l’esperienza e la data che preferisci; l’ufficio confermerà i dettagli.",
    priceTitle: "Prezzi di catalogo chiari", price: "Controlla la base del prezzo e cosa include ogni escursione. L’ufficio confermerà la scelta e il totale finale prima dell’accordo di prenotazione.",
    availabilityTitle: "Verifichiamo la tua data", availability: "La data scelta è una richiesta, non una partenza confermata o un posto riservato. L’ufficio verificherà la disponibilità prima di confermare.",
    localTitle: "Assistenza locale a Sharm", contact: "Concorda l’escursione, i dettagli del prelievo e le modalità di pagamento direttamente con il nostro team su WhatsApp.",
    policyTitle: "Condizioni di cancellazione chiare", pickup: "Prelievo e servizi inclusi variano per escursione. Leggi i dettagli e conferma hotel e punto di incontro con l’ufficio.",
    booking: "Scegli escursione, data preferita e numero di ospiti, poi invia la richiesta WhatsApp preparata. Non servono account né pagamento online. L’ufficio confermerà disponibilità, prezzo finale e modalità di pagamento; il messaggio non riserva posti.",
    confirmation: "La richiesta è confermata solo dopo la verifica della disponibilità da parte dell’ufficio e la conferma esplicita dei dettagli. Un messaggio WhatsApp da solo non è una prenotazione confermata o pagata.",
    privacy: "Il nostro personale usa i dati forniti per organizzare l’escursione. Alla conversazione WhatsApp si applicano le sue condizioni. Il pagamento online è attualmente disattivato; la richiesta non raccoglie dati della carta né li invia a Paymob.",
    catalogue: "Cerca e confronta i prezzi di catalogo in euro. Controlla la base del prezzo di ogni escursione e conferma la tua scelta con l’ufficio.",
  },
} as const;

export function discoveryForSales(locale: StsLocale, sales: PublicSalesStatus | null | undefined): DiscoveryCopy {
  const base = discoveryCopy[locale];
  if (onlineSalesAvailable(sales)) return base;
  const c = officeCopy[locale];
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
  const c = officeCopy[locale];
  return {
    ...base,
    about: { ...base.about, sections: [
      { heading: base.about.sections[0]!.heading, body: [c.contact] },
      { heading: c.priceTitle, body: [c.price] },
      { heading: c.availabilityTitle, body: [c.availability, c.confirmation] },
    ] },
    faq: { ...base.faq, items: base.faq.items.map((item, i) => ({ ...item, body: i === 1 ? c.booking : i === 2 ? c.availability : i === 4 ? c.confirmation : item.body })) },
    terms: { ...base.terms, sections: base.terms.sections.map((section, i) =>
      i === 1 ? { ...section, body: [c.price, enquiryCopy[locale].notice] }
        : i === 2 ? { ...section, body: [c.booking, c.confirmation, c.pickup] } : section,
    ) },
    privacy: { ...base.privacy, sections: base.privacy.sections.map((section, i) => i === 2 ? { ...section, body: [c.privacy] } : section) },
  };
}
