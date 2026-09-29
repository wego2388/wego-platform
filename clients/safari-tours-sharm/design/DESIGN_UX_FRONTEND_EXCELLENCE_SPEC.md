# Safari Tours Sharm — Design, UX & Frontend Excellence Specification

- **التاريخ:** 2026-09-29
- **الحالة:** `SPECIFICATION READY / IMPLEMENTATION NOT YET ACTIVE`
- **المرجع التنفيذي:** `SAFARI_TOURS_MASTER_DELIVERY_AND_GROWTH_PLAN.md`
- **قيد الحوكمة:** لا يبدأ redesign شامل قبل إغلاق `WEGO-016-E` وتفعيل
  packet تصميم/تسويق صريح.

## 1. الهدف

بناء تجربة تبدو عالمية وحديثة ومميزة، لكنها تظل سهلة وسريعة وصادقة. الانطباع
المطلوب ليس “زينة كثيرة”، بل منتج ذكي يختصر القرار:

> اكتشف التجربة المناسبة، افهم السعر والتفاصيل، اختر موعدًا حقيقيًا، واحجز
> بثقة من الهاتف في أقل عدد ممكن من الخطوات.

معايير النجاح:

- جمال بصري واضح من أول شاشة بدون ادعاءات وهمية.
- فهم فوري لما يقدمه النشاط وأين يعمل وكيف يتم الحجز.
- Mobile-first لأن أغلب زيارات السياحة والسوشيال تأتي من الهاتف.
- كل حركة لها وظيفة: توجيه الانتباه أو توضيح تغير الحالة.
- الحجز أسرع من التواصل اليدوي، مع WhatsApp كدعم لا كبديل للنظام.
- تصميم متكامل بين الموقع العام والـERP مع اختلاف السياق: الموقع عاطفي
  وتجاري، والـERP كثيف وواضح وعملي.

## 2. نتيجة المراجعة البصرية الحالية

تمت مراجعة Home وTours وERP Login على `1440×1000` و`390×844` داخل Compose.

### ما يعمل جيدًا

- [x] أساس ألوان Navy/Sand/Coral مناسب للبحر والصحراء.
- [x] typography hierarchy واضحة في الـhero.
- [x] desktop/mobile layouts لا تكسر أفقيًا في الصفحات المراجعة.
- [x] CTA الأساسية واضحة، والـWhatsApp fallback موجود.
- [x] `prefers-reduced-motion` موجود كأساس.
- [x] ERP login بسيط ومركز ولا يكشف تفاصيل زائدة.

### ما تم إصلاحه فورًا

- [x] `/` كان يعرض ERP Login بدل public Home؛ أصبح root للموقع العام.
- [x] `/login` ومسارات الإدارة تظل للـERP.
- [x] header كان أبيض فوق canvas فاتح قبل scroll؛ أصبح على خلفية Ocean واضحة.
- [x] أضيف Browser regression test للـroot وfavicon.

### الفجوات البصرية الحالية

- [ ] الهوية ما زالت تعتمد على initials وemoji category icons؛ ليست علامة
      تجارية مكتملة.
- [ ] لا توجد hero photography/video معتمدة الحقوق.
- [ ] المساحات بعد الأقسام تبدو فارغة في full-page capture قبل تحفيز scroll
      reveal؛ الحركة يجب أن تكون progressive وألا تخفي محتوى طويلًا.
- [ ] cards وظيفية لكن generic، ولا تعرض tour imagery/value/price/availability.
- [ ] لا يوجد featured tours أو itinerary storytelling أو social proof موثق.
- [ ] mobile locale controls مزدحمة؛ تحتاج selector أكثر أناقة ووصولًا.
- [ ] الصفحة الرئيسية لا تعرض المحتوى الكافي لإقناع المستخدم قبل النزول للحجز.
- [x] سبب اختفاء زر ERP login كان غياب Tailwind `@source` وsemantic Wego
      `@theme` bridge لمكتبة `@wego/ui`؛ أضيفا وأصبح اللون المرئي مغطى باختبار
      browser فعلي.
- [ ] dashboard information density، responsive tables وmobile operations
      تحتاج audit منفصل بعد تفعيل F.

## 3. اتجاه الهوية البصرية

اسم الاتجاه المقترح: **Sinai Afterglow**.

الفكرة: هدوء البحر الأحمر + دفء الغروب الصحراوي + وضوح منتج تقني حديث.
النتيجة premium friendly وليست luxury متكلفًا ولا قالب booking عام.

### السمات

- Ocean navy للثقة والخلفيات العميقة.
- Coral/sunset كإشارة conversion وليس كلون يغطي الصفحة.
- Sand/canvas لمساحات التنفس والدفء.
- صور حقيقية كبيرة بطبقات نص هادئة وcontrast محسوب.
- typography editorial للعناوين، sans عملية للنصوص والأرقام.
- أشكال عضوية بسيطة مستوحاة من الشمس/الموج، بدون زخارف ثقيلة.

### محظورات الهوية

- لا صور stock عامة توحي بأنها ضيوف حقيقيون.
- لا أيقونات emoji في النسخة النهائية.
- لا gradients مفرطة أو glassmorphism على كل component.
- لا نص فوق صورة دون overlay وcontrast مثبت.
- لا dark patterns أو countdown أو fake scarcity.
- لا animation مستمر يشتت أو يستهلك البطارية.

## 4. Design system foundation

### 4.1 Tokens

- [ ] semantic colors: brand/background/surface/text/border/action/status.
- [ ] WCAG AA contrast لكل text/action state؛ AAA للنصوص الحرجة حيث عملي.
- [ ] 4px spacing grid مع responsive section/container tokens.
- [ ] type scale باستخدام `clamp()` لتدرج fluid بدون قفزات breakpoint.
- [ ] radius scale: controls/cards/media/pills، لا قيم عشوائية.
- [ ] shadow/elevation scale محدود: base/raised/overlay.
- [ ] z-index layers: base/sticky/dropdown/modal/toast.
- [ ] motion tokens: duration/easing/distance/stagger.
- [ ] focus ring موحد عالي الوضوح.

### 4.2 Typography

| Locale | Display | Body/UI | ملاحظات |
|---|---|---|---|
| EN | Playfair Display | Inter Variable | editorial + readable |
| IT | Playfair Display | Inter Variable | نفس الهيكل مع توسع النص |
| RU | خط Cyrillic مثبت بصريًا | Inter Variable | اختبار line breaks |
| AR | Cairo | Cairo | RTL، أوزان ومسافات سطر خاصة |

- [ ] subset/preload للخطوط اللازمة للـlocale الحالي فقط إن أمكن.
- [ ] لا font weight غير محمل.
- [ ] minimum 16px body و44×44px touch targets.

### 4.3 Core components

- [ ] BrandMark/LogoLockup.
- [ ] Header desktop/mobile + language selector + focus-managed menu.
- [ ] HeroMedia وHeroSearch/Explore CTA.
- [ ] TourCard: image, category, duration, factual price, availability, CTA.
- [ ] CategoryCard بدون emoji.
- [ ] TrustStrip ببيانات مثبتة فقط.
- [ ] AvailabilityPicker/Calendar/SlotChip.
- [ ] PriceSummary وPolicySummary.
- [ ] Stepper للحجز.
- [ ] FormField/PhoneField/Select/Checkbox/ErrorSummary.
- [ ] Gallery/Lightbox accessible.
- [ ] Accordion/FAQ/Breadcrumbs.
- [ ] Empty/Loading/Error/Offline states.
- [ ] Toast/Dialog/Drawer مع focus trap واسترجاع focus.
- [ ] ERP data table/filter bar/status badge/action menu/pagination.

## 5. Responsive strategy

لا نصمم لثلاث نقاط فقط؛ المكونات تتغير حسب المساحة المتاحة. مصفوفة الاختبار
الإلزامية:

| الفئة | المقاسات الأساسية | المطلوب |
|---|---|---|
| Small mobile | 320×568, 360×800 | لا قص، CTA كاملة، لا horizontal scroll |
| Modern mobile | 390×844, 430×932 | المسار الأساسي المثالي |
| Tablet portrait | 768×1024 | navigation/cards/booking split منطقي |
| Tablet landscape | 1024×768 | sticky summary بلا تغطية المحتوى |
| Laptop | 1280×800, 1366×768 | above-fold متوازن |
| Desktop | 1440×900, 1920×1080 | max-width، لا فراغات بلا وظيفة |
| Zoom | 200% عند 1280px | لا فقد محتوى أو controls |

قواعد:

- [ ] mobile-first CSS، container queries للمكونات المناسبة.
- [ ] safe-area insets للـiPhone وsticky CTAs.
- [ ] RTL يعكس الاتجاه حيث يلزم، وليس مجرد `text-align:right`.
- [ ] الصور تستخدم aspect-ratio ثابت لمنع CLS.
- [ ] tables تتحول إلى cards أو horizontal region معلّم، لا تصغير غير مقروء.
- [ ] sticky elements لا تغطي آخر form field أو cookie/consent UI.

## 6. Public website page blueprints

### 6.1 Home

1. Header هادئ مع logo/navigation/language/WhatsApp.
2. Hero بصورة/فيديو rights-approved، H1، value proposition وCTA مزدوجة.
3. Quick discovery: Sea / Desert / Culture / Family / Transfer.
4. Featured tours من catalog/availability، لا قائمة يدوية مضللة.
5. “Choose your Sharm day” decision cards حسب الوقت/المجموعة/الاهتمام.
6. How booking works: choose → secure payment → confirmed booking.
7. Trust section بالحقائق المعتمدة وسياسة الإلغاء المختصرة.
8. Local guide teaser لمحتوى مفيد حقيقي.
9. Verified reviews فقط؛ القسم يختفي لو dataset فارغ.
10. FAQ وfooter غني بدون تكرار CTA مزعج.

### 6.2 Tours index

- sticky but compact filter/sort.
- category/date/duration/type/family/private filters من بيانات حقيقية فقط.
- result count وclear filters.
- cards تعرض `from` فقط عندما contract يثبت معناه.
- skeleton مطابق لشكل card لتجنب layout shift.
- empty state يقترح إزالة filter أو WhatsApp، لا نتائج وهمية.

### 6.3 Category page

- editorial header + concise intent copy.
- top factual FAQ/guide links.
- catalog list مع availability hints حقيقية.
- related categories/navigation.

### 6.4 Tour detail

- media gallery responsive.
- فوق الطية: title, factual summary, duration/type, price, next availability.
- desktop: content + sticky booking rail.
- mobile: sticky bottom CTA يحترم safe area.
- sections: highlights، itinerary، included/excluded، pickup، restrictions،
  safety/weather، what-to-bring، cancellation، FAQs.
- أي قسم بلا بيانات لا يظهر، ويظل content-readiness issue في ERP.
- related tours من rules موثقة، لا random.

### 6.5 Booking

- stepper مختصر: slot/party → details → review/pay → result.
- السعر النهائي server-owned ويتغير بوضوح عند party changes.
- inline validation + error summary + preserve safe form state.
- لا account creation إجباري.
- payment handoff يوضح ماذا يحدث بدون ادعاء security غير مثبت.
- recovery/retry بدون duplicate booking/payment.

### 6.6 Confirmation / My Booking

- status أولًا مع reference قابل للنسخ.
- لا وعد pickup/driver غير مثبت.
- واضح ما تم دفعه وما يزال pending.
- actions: view booking، support، add to calendar فقط إن كانت facts كاملة.
- noindex، no PII في URL، لا analytics PII.

### 6.7 About / Contact / Legal

- About story/team/history بعد owner verification فقط.
- Contact channel expectations and support hours بعد الاعتماد.
- Privacy/Terms/Cancellation صفحات قابلة للقراءة والطباعة.
- effective date/version/contact/legal entity واضحة.

## 7. ERP UX blueprint

الـERP ليس نسخة داكنة من الموقع؛ الأولوية للسرعة والدقة وتقليل أخطاء التشغيل.

- [ ] responsive app shell وkeyboard navigation.
- [ ] Overview: actionable alerts قبل vanity KPIs.
- [ ] Bookings queue حسب الحالة/التاريخ مع saved filters لاحقًا.
- [ ] Booking detail يجمع customer/tour/payment/audit timeline بوضوح.
- [ ] dangerous actions تحتاج confirmation + reason + permission.
- [ ] Tours readiness column يوضح missing content/translations/media.
- [ ] Slots calendar/list مع capacity booked/remaining وconflict states.
- [ ] Finance من payment ledger مع date/currency/status explainers.
- [ ] roles/permissions بأقل صلاحية وبدون UI-only enforcement.
- [ ] notifications/outbox health عند تفعيل G.
- [ ] desktop dense tables، tablet usable، mobile supports urgent operations.

## 8. Motion and animation system

الهدف “احترافي وذكي”، وليس “كل شيء يتحرك”.

### قواعد الزمن

| الحركة | المدة | الاستخدام |
|---|---:|---|
| Instant feedback | 80–120ms | press/toggle/focus |
| Small component | 160–220ms | menu/chip/tooltip |
| Card/state | 220–320ms | filter/card/accordion |
| Page/hero reveal | 400–650ms | أول دخول فقط |

### حركات معتمدة

- fade + 12–20px translate لعناصر المحتوى، مرة واحدة.
- stagger محدود 40–70ms لثلاثة إلى ستة عناصر فقط.
- image scale 1.02–1.04 عند hover على pointer devices.
- CTA press scale بسيط مع focus state مستقل.
- skeleton shimmer خفيف ويتوقف مع reduced motion.
- accordion height/opacity بدون قفز layout.
- page transition subtle ولا يؤخر navigation أو back button.
- success state يوضح اكتمال الحجز بدون confetti متكرر.

### ممنوع

- parallax ثقيل على mobile.
- animation على width/height/top/left في المسارات المتكررة.
- autoplay video بصوت.
- scroll hijacking.
- إخفاء SSR content إذا فشل JavaScript.
- انتظار animation قبل إتاحة CTA.

### بوابة الحركة

- [ ] `prefers-reduced-motion` يجعل المحتوى ظاهرًا والحركة شبه فورية.
- [ ] animation يعتمد transform/opacity ويحافظ على 60fps على هاتف متوسط.
- [ ] IntersectionObserver يفصل نفسه بعد reveal.
- [ ] screenshots/SEO لا تلتقط صفحة فارغة بسبب hidden sections.
- [ ] no CLS من دخول الصور أو sticky bars.

## 9. Micro-interactions and states

لكل component أربع حالات على الأقل: default/hover-focus/disabled/loading.
ولكل API surface: loading/success/empty/validation/network/server/rate-limited.

- booking lookup يعرض 429 برسالة ووقت إعادة المحاولة.
- slot أصبح ممتلئًا: refresh availability ولا يحتفظ بسعر/slot وهمي.
- payment pending: polling محدود مع زر retry آمن.
- duplicate submit: button pending وbackend idempotency.
- offline: لا يدعي الحجز، ويحافظ على مدخلات غير حساسة بقدر آمن.
- image failure: branded fallback + alt/label، لا broken icon.
- locale switch يحافظ على equivalent route عندما تتوفر ترجمة.

## 10. Accessibility definition

- [ ] WCAG 2.2 AA كحد أدنى.
- [ ] semantic landmarks وواحد H1 لكل صفحة.
- [ ] skip link يعمل ويصل إلى `main`.
- [ ] logical tab order وvisible focus.
- [ ] labels/errors/descriptions مرتبطة برمجيًا.
- [ ] screen-reader announcements للحجز والدفع والحالة.
- [ ] dialogs/menus trap and restore focus.
- [ ] target size 44×44px أو ما يعادله.
- [ ] لا اعتماد على اللون وحده للحالة.
- [ ] contrast في light/dark/over-image states.
- [ ] EN/AR/RU/IT screen-reader and zoom smoke tests.

## 11. Performance budgets

Budgets أولية تُثبت بقياس حقيقي:

- LCP ≤ 2.5s على mobile lab profile للصفحات الأساسية.
- CLS ≤ 0.1.
- INP ≤ 200ms target.
- critical route JS يبقى محدودًا؛ لا slider/chart library ثقيلة بلا مبرر.
- hero media responsive AVIF/WebP مع poster وwidth/height.
- below-fold media lazy، أول LCP image ليست lazy.
- fonts subset/preload محدود وتستخدم `font-display` مناسبًا.
- marketing tags بعد consent ولا تعطل main thread.

## 12. Visual and browser QA

- [ ] component tests للحالات الأساسية.
- [ ] Playwright user journeys على Chromium + Firefox + WebKit عندما تتوفر.
- [ ] screenshots للـmatrix المحددة مع LTR/RTL.
- [ ] axe scan للـHome/Tours/Tour/Booking/Confirmation/My Booking/Login.
- [ ] keyboard-only checkout.
- [ ] touch/mobile menu/filter/gallery/form checks.
- [ ] slow 4G وCPU throttling على الصفحات الرئيسية.
- [ ] visual regression baselines بعد اعتماد التصميم، لا قبل استقرار المحتوى.
- [ ] Lighthouse evidence محفوظ لكل release candidate.

## 13. مراحل التنفيذ المقترحة

لا تتفعل تلقائيًا؛ تُسجل على Board بعد إغلاق الحزمة الحالية.

### UX-0 — Research and approved facts

- [ ] user intents/personas بلا اختراع بيانات.
- [ ] content/media inventory وrights approval.
- [ ] navigation and conversion map.

### UX-1 — Foundations

- [ ] tokens, typography, grid, motion, accessibility primitives.
- [ ] Story/demo route داخلي للمكونات أو اختبارات components؛ لا platform جديد.

### UX-2 — Public discovery

- [ ] Home, Tours, Categories, Tour Detail.
- [ ] responsive/RTL/SEO integration.

### UX-3 — Booking conversion

- [ ] Booking, payment states, confirmation, lookup.
- [ ] Tier 1 review لأن المسار يتعامل مع payment/PII.

### UX-4 — ERP operations

- [ ] shell, bookings, tours/slots, finance, states and permissions.
- [ ] ينسق مع WEGO-016-F/G، لا يسبق الحقيقة المالية.

### UX-5 — Polish and proof

- [ ] motion polish, cross-browser, accessibility, performance, Lighthouse.
- [ ] owner visual acceptance + UAT.

## 14. مدخلات المالك اللازمة

- [!] logo master SVG/wordmark أو تفويض تصميم علامة جديدة.
- [!] rights-approved photos/videos لكل category/tour.
- [!] قرار ظهور أشخاص/ضيوف وموافقات الاستخدام.
- [!] business/legal/contact facts.
- [!] tour content facts والترجمات البشرية.
- [!] verified reviews provenance.

غياب هذه المدخلات لا يوقف بناء النظام؛ لكنه يوقف نشر محتوى يوحي بحقائق غير
موجودة. أثناء التنفيذ تستخدم placeholders محايدة وموسومة داخليًا، لا صورًا أو
نصوصًا تبدو production-approved.

## 15. Definition of Done للتصميم النهائي

- [ ] لا component أساسي خارج design system.
- [ ] لا breakpoint رئيسي مكسور أو horizontal scroll غير مقصود.
- [ ] RTL وLTR صحيحان وظيفيًا وبصريًا.
- [ ] كل animation له reduced-motion fallback ولا يضر Core Web Vitals.
- [ ] كل route له loading/empty/error/permission/rate-limit states المناسبة.
- [ ] الأسعار والسياسات والصور من source of truth المعتمد فقط.
- [ ] checkout وERP actions يمران بالـAPI والصلاحيات الحقيقية.
- [ ] automated functional/accessibility/visual checks خضراء.
- [ ] Lighthouse budgets مقبولة ومثبتة.
- [ ] owner UAT واعتماد بصري ومحتوى قبل deployment.
