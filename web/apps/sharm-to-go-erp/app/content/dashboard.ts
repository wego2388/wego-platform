export type DashboardLocale = "ar" | "en";

interface ModuleStatus {
  name: string;
  status: string;
  detail: string;
}

interface DashboardCopy {
  languageName: string;
  foundationMode: string;
  title: string;
  subtitle: string;
  noticeTitle: string;
  noticeBody: string;
  nav: string[];
  readiness: string;
  modules: ModuleStatus[];
  decisions: string;
  decisionItems: string[];
  nextGate: string;
  nextGateBody: string;
}

export const dashboardCopy: Record<DashboardLocale, DashboardCopy> = {
  en: {
    languageName: "العربية",
    foundationMode: "Foundation mode · no live business data",
    title: "Sharm To Go operations",
    subtitle: "A simple control surface for services, calendar, bookings and payments as each real API becomes authorized and connected.",
    noticeTitle: "This dashboard is not deployed or connected yet",
    noticeBody: "It deliberately shows readiness instead of invented totals. Authentication scopes, marketplace APIs and real client data arrive in separately reviewed packets.",
    nav: ["Overview", "Services", "Calendar", "Bookings", "Payments"],
    readiness: "Marketplace readiness",
    modules: [
      { name: "Client composition", status: "Ready", detail: "Isolated Sharm To Go manifest and deterministic release lock." },
      { name: "Design foundation", status: "Ready", detail: "Repo-owned tokens, screen map and Arabic/English booking prototype." },
      { name: "Service catalog", status: "Not connected", detail: "Needs the first real service names, prices, schedules and approved media." },
      { name: "Booking requests", status: "Not connected", detail: "No availability, price or confirmation workflow is live." },
      { name: "Money & settlement", status: "Not connected", detail: "No checkout, commissions, payouts or refunds are implemented." },
    ],
    decisions: "Launch content still needed",
    decisionItems: ["First service names, categories and descriptions", "Price, duration, dates and available capacity", "Approved photos and cancellation wording", "Paymob/Fawry sandbox merchant access when payment work starts"],
    nextGate: "Next executable gate",
    nextGateBody: "Provide one complete real service data set. The next packet can then build the reusable catalog and dashboard editor without guessing business facts.",
  },
  ar: {
    languageName: "English",
    foundationMode: "وضع التأسيس · لا توجد بيانات أعمال فعلية",
    title: "إدارة Sharm To Go",
    subtitle: "واجهة تحكم بسيطة للخدمات والتقويم والحجوزات والمدفوعات عند اعتماد وربط كل API فعلي.",
    noticeTitle: "الداش بورد غير منشور وغير متصل حاليًا",
    noticeBody: "يعرض الجاهزية بدل أرقام وهمية. صلاحيات الدخول وواجهات السوق وبيانات العميل الفعلية ستصل في حزم مستقلة ومراجعة.",
    nav: ["نظرة عامة", "الخدمات", "التقويم", "الحجوزات", "المدفوعات"],
    readiness: "جاهزية السوق",
    modules: [
      { name: "تركيب العميل", status: "جاهز", detail: "Manifest مستقل لـSharm To Go وRelease Lock ثابت." },
      { name: "أساس التصميم", status: "جاهز", detail: "Tokens وخريطة شاشات ونموذج حجز عربي/إنجليزي مملوكة للمشروع." },
      { name: "كتالوج الخدمات", status: "غير متصل", detail: "يحتاج أسماء وأسعار ومواعيد وصورًا معتمدة لأول خدمات فعلية." },
      { name: "طلبات الحجز", status: "غير متصل", detail: "لا يوجد توفر أو سعر أو مسار تأكيد فعلي." },
      { name: "الأموال والتسويات", status: "غير متصل", detail: "لم يتم تنفيذ الدفع أو العمولات أو التحويلات أو الاسترداد." },
    ],
    decisions: "محتوى مطلوب للإطلاق",
    decisionItems: ["أسماء أول الخدمات وفئاتها ووصفها", "السعر والمدة والتواريخ والسعة المتاحة", "الصور المعتمدة ونص سياسة الإلغاء", "بيانات Sandbox لـPaymob وFawry عند بدء تنفيذ الدفع"],
    nextGate: "بوابة التنفيذ التالية",
    nextGateBody: "أرسل بيانات خدمة فعلية واحدة مكتملة، ثم تبني الحزمة التالية الكتالوج ومحرر الداش بورد القابلين لإعادة الاستخدام دون افتراض بيانات تجارية.",
  },
};

export function dashboardDirection(locale: DashboardLocale): "ltr" | "rtl" {
  return locale === "ar" ? "rtl" : "ltr";
}
