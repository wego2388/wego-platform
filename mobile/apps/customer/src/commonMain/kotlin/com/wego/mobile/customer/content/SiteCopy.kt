package com.wego.mobile.customer.content

import com.wego.mobile.shared.catalog.LocalizedText

/**
 * Ported verbatim from
 * `web/apps/sharm-divers-club-site/app/content/locales.ts` — same real,
 * approved copy the website shows, so the app never says something the
 * site doesn't (or vice versa). Not every field on the website is needed
 * here (design-system preview, footer legal links, etc.) — only what the
 * app's screens actually render.
 */
object SiteCopy {
    const val WHATSAPP_URL = "https://wa.me/201066461010"
    const val PHONE_DISPLAY = "+20 10 6646 1010"
    const val EMAIL = "Sales@sharmdiversclub.com"
    const val TRIPADVISOR_URL =
        "https://www.tripadvisor.com/Attraction_Review-g297555-d27124030-Reviews-Sharm_Divers_Club-" +
            "Sharm_El_Sheikh_South_Sinai_Red_Sea_and_Sinai.html"

    val location =
        LocalizedText(
            en = "Royal Grand Sharm Hotel, Hadabet Um Sid, Sharm El Sheikh, Egypt",
            ar = "فندق Royal Grand Sharm، هضبة أم السيد، شرم الشيخ، مصر",
        )
    val hours = LocalizedText(en = "Daily 08:00–20:00", ar = "يوميًا 08:00–20:00")

    object Nav {
        val home = LocalizedText("Home", "الرئيسية")
        val discover = LocalizedText("Discover", "استكشف")
        val about = LocalizedText("About", "من نحن")
        val faq = LocalizedText("FAQ", "الأسئلة الشائعة")
        val contact = LocalizedText("Contact", "تواصل")
    }

    object Hero {
        val eyebrow = LocalizedText("Sharm El Sheikh · PADI 5 Star Dive Center", "شرم الشيخ · مركز PADI 5 نجوم")
        val title = LocalizedText("Red Sea confidence, personally guided.", "ثقة البحر الأحمر، بترتيب شخصي معاك.")
        val body =
            LocalizedText(
                "From a first breath underwater to Ras Mohammed, Tiran and the Thistlegorm — Sharm Divers " +
                    "Club plans it with you directly, one clear WhatsApp conversation at a time.",
                "من أول نفس تحت الماء لحد رأس محمد وتيران وThistlegorm — Sharm Divers Club بيرتب معاك بشكل " +
                    "مباشر، محادثة واحدة واضحة على واتساب.",
            )
        val browseCta = LocalizedText("Discover the categories", "استكشف الفئات")
        val whatsappCta = LocalizedText("Message us on WhatsApp", "راسلنا على واتساب")
    }

    val guarantees =
        listOf(
            LocalizedText("PADI 5 Star Dive Center", "مركز PADI 5 نجوم"),
            LocalizedText("CDWS accredited · #100601", "معتمد من CDWS · رقم 100601"),
            LocalizedText("Send an inquiry on WhatsApp", "ابعت طلبك على واتساب"),
            LocalizedText("Team speaks 5 languages", "الفريق بيتكلم 5 لغات"),
        )

    object Stats {
        val categoriesLabel = LocalizedText("Categories", "الفئات")
        val languagesLabel = LocalizedText("Team languages", "لغات الفريق")
        val padiLabel = LocalizedText("PADI star rating", "تصنيف PADI بالنجوم")
        const val CATEGORIES_VALUE = "7"
        const val LANGUAGES_VALUE = "5"
        const val PADI_VALUE = "5★"
    }

    object How {
        val heading = LocalizedText("A conversation first, a confirmation second", "الأول محادثة، وبعدها تأكيد")
        val body =
            LocalizedText(
                "There is no self-service checkout in this app yet — every booking is created by our team, " +
                    "so every request starts as a direct WhatsApp conversation.",
                "مفيش حجز ذاتي فوري في التطبيق لسه — كل حجز بيتعمل من فريقنا، فكل طلب بيبدأ بمحادثة مباشرة على " +
                    "واتساب.",
            )
        val steps: List<Pair<LocalizedText, LocalizedText>> =
            listOf(
                LocalizedText("1. Message us", "١. راسلنا") to
                    LocalizedText(
                        "Tell us what you're after — a first dive, a certification, or a multi-day plan.",
                        "قولنا محتاج إيه — غطسة أولى، شهادة، أو خطة عدة أيام.",
                    ),
                LocalizedText("2. We confirm details", "٢. نأكد التفاصيل") to
                    LocalizedText(
                        "Our team checks the date, group size and price with you before anything is booked.",
                        "فريقنا بيتأكد معاك من الموعد وعدد الأفراد والسعر قبل أي حجز.",
                    ),
                LocalizedText("3. You're booked", "٣. اتحجزت") to
                    LocalizedText(
                        "Once confirmed, your dive or course is on our schedule — no separate account needed.",
                        "بعد التأكيد، غطستك أو دورتك تبقى في جدولنا — من غير أي حساب إضافي.",
                    ),
            )
    }

    object Why {
        val heading = LocalizedText("Clear before. Personal during. Memorable after.", "قبل الرحلة وضوح. أثناءها اهتمام شخصي. وبعدها ذكرى.")
        val body =
            LocalizedText(
                "Sharm Divers Club's own promise to every diver, not a marketing slogan invented for this app.",
                "وعد Sharm Divers Club لكل غواص، مش شعار تسويقي مختلق لأجل التطبيق ده.",
            )
        val items: List<Pair<LocalizedText, LocalizedText>> =
            listOf(
                LocalizedText("Before the dive", "قبل الرحلة") to
                    LocalizedText("No ambiguity about the plan or what's expected of you.", "مفيش غموض في البرنامج ولا في المطلوب منك."),
                LocalizedText("During the experience", "أثناء التجربة") to
                    LocalizedText(
                        "You always know who's responsible for you and what happens next.",
                        "دايمًا عارف مين المسؤول عنك والخطوة الجاية إيه.",
                    ),
                LocalizedText("After you surface", "بعد ما تطلع") to
                    LocalizedText(
                        "We follow up after your dive — and give you a real reason to come back.",
                        "بنتابع معاك بعد الغطسة — وبنديك سبب حقيقي للرجوع.",
                    ),
            )
    }

    object Personas {
        val heading = LocalizedText("Built around who you are, not a generic offer list", "مبني حول شخصيتك، مش قائمة عروض عامة")
        val body =
            LocalizedText(
                "Sharm Divers Club's own brand direction defines who this experience is designed for.",
                "توجه العلامة التجارية لـSharm Divers Club بيحدد لمين مصممة التجربة دي.",
            )
        val items: List<Pair<LocalizedText, LocalizedText>> =
            listOf(
                LocalizedText("First Breath", "First Breath") to
                    LocalizedText(
                        "Never dived before, and a little nervous about it. Gets a simple explanation, not a wall of jargon.",
                        "ماغطسش قبل كده وحاسس بتوتر شوية. بياخد شرح بسيط، مش مصطلحات معقدة.",
                    ),
                LocalizedText("Certified Explorer", "Certified Explorer") to
                    LocalizedText(
                        "Has a certification and limited time. Wants clear logistics and honest availability.",
                        "معاه شهادة ووقته محدود. عايز لوجستيات واضحة وتوفر حقيقي.",
                    ),
                LocalizedText("Red Sea Collector", "Red Sea Collector") to
                    LocalizedText(
                        "A repeat diver chasing Tiran, Ras Mohammed, Thistlegorm and Dahab, plus a real dive record.",
                        "غواص متكرر بيدور على تيران ورأس محمد وThistlegorm ودهب، وسجل غطسات حقيقي.",
                    ),
                LocalizedText("Partner Planner", "Partner Planner") to
                    LocalizedText(
                        "A hotel, agent or group organizer who needs speed, clear terms and one responsible contact.",
                        "فندق أو وكيل أو منظم مجموعات محتاج سرعة وشروط واضحة ومسؤول واحد.",
                    ),
            )
    }

    val tripadvisorLabel = LocalizedText("Find us on TripAdvisor", "شوفنا على TripAdvisor")

    object Discover {
        val heading = LocalizedText("Real 2026 prices, one inquiry away", "أسعار 2026 حقيقية، على بعد رسالة واحدة")
        val body =
            LocalizedText(
                "Categories, sites, courses and prices shown here are real, approved 2026 rates. Dates, " +
                    "availability and final details are always confirmed by our team on WhatsApp before " +
                    "anything is booked.",
                "الفئات والمواقع والدورات والأسعار الظاهرة هنا حقيقية ومعتمدة لسنة 2026. المواعيد والتوفر " +
                    "والتفاصيل النهائية دايمًا بيأكدها فريقنا على واتساب قبل أي حجز.",
            )
        val pricingNotice = LocalizedText("2026 price · confirm dates on WhatsApp", "سعر 2026 · أكّد الموعد على واتساب")
        val filterAll = LocalizedText("All categories", "كل الفئات")
        val moreInCategory = LocalizedText("More in this category", "المزيد في نفس الفئة")
        val whatsappCta = LocalizedText("Send an inquiry on WhatsApp", "ابعت طلبك على واتساب")
        val back = LocalizedText("Back to the overview", "العودة للنظرة العامة")
        val notFound = LocalizedText("We couldn't find that offering.", "معلش، مش لاقيين الخدمة دي.")
    }

    object About {
        val heading = LocalizedText("A PADI 5 Star center, run directly", "مركز PADI 5 نجوم، بإدارة مباشرة")
        val body =
            LocalizedText(
                "Sharm Divers Club is a PADI 5 Star Dive Center in Sharm El Sheikh, accredited with CDWS. " +
                    "Every fact on this page is approved for publication — nothing here is invented.",
                "Sharm Divers Club مركز PADI 5 نجوم في شرم الشيخ، معتمد من CDWS. كل معلومة في الصفحة دي " +
                    "معتمدة للنشر — مفيش حاجة مختلقة.",
            )
        val factsHeading = LocalizedText("What we can confirm today", "اللي نقدر نأكده النهاردة")
        val languagesHeading = LocalizedText("Team languages", "لغات الفريق")
        val languagesBody =
            LocalizedText(
                "Our PADI profile lists Arabic, English, Russian, German and Italian as team languages. " +
                    "This app is only available in Arabic and English for now — a full translation isn't " +
                    "published yet.",
                "ملف PADI بتاعنا بيذكر العربي والإنجليزي والروسي والألماني والإيطالي كلغات الفريق. التطبيق ده " +
                    "متاح بالعربي والإنجليزي فقط حاليًا — الترجمة الكاملة لسه مش منشورة.",
            )
        val facts: List<Pair<LocalizedText, LocalizedText>> =
            listOf(
                LocalizedText("Positioning", "التوجه") to
                    LocalizedText("PADI 5 Star Dive Center in Sharm El Sheikh", "مركز PADI 5 نجوم في شرم الشيخ"),
                LocalizedText("Accreditation", "الاعتماد") to LocalizedText("CDWS #100601", "CDWS رقم 100601"),
                LocalizedText("Location", "الموقع") to location,
                LocalizedText("Hours", "المواعيد") to hours,
            )
    }

    object Contact {
        val heading = LocalizedText("Talk to us directly", "تواصل معنا مباشرة")
        val body =
            LocalizedText(
                "Every channel below is the real, approved contact for Sharm Divers Club.",
                "كل قناة تحت دي معتمدة وحقيقية لـSharm Divers Club.",
            )
        val whatsappLabel = LocalizedText("WhatsApp", "واتساب")
        val whatsappBody =
            LocalizedText(
                "The fastest way to reach us and the only way to actually book today.",
                "أسرع طريقة توصلنا بيها، والطريقة الوحيدة للحجز فعليًا النهاردة.",
            )
        val phoneLabel = LocalizedText("Phone", "الهاتف")
        val emailLabel = LocalizedText("Email", "البريد الإلكتروني")
        val locationLabel = LocalizedText("Location", "الموقع")
        val hoursLabel = LocalizedText("Hours", "المواعيد")
    }

    object DiveSites {
        val heading = LocalizedText("Real Red Sea sites, real trips to them", "مواقع حقيقية في البحر الأحمر، ورحلات حقيقية ليها")
        val body =
            LocalizedText(
                "These are the named sites our real, approved boat trips actually visit. Always confirm the " +
                    "day's plan with our team before you go.",
                "دي المواقع اللي رحلاتنا الحقيقية والمعتمدة بالفعل بتروحلها. دايمًا أكّد خطة اليوم مع فريقنا قبل " +
                    "ما تنزل.",
            )
        val offeringsHeading = LocalizedText("Real trips to this site", "رحلات حقيقية لهذا الموقع")
        val whatsappCta = LocalizedText("Send an inquiry on WhatsApp", "ابعت طلبك على واتساب")
        val buildPackageCta = LocalizedText("Build a package", "كوّن باقتك")
        val back = LocalizedText("Back to dive sites", "العودة لمواقع الغوص")
        val navLabel = LocalizedText("Dive Sites", "مواقع الغوص")
    }

    object PackageBuilder {
        val heading = LocalizedText("Build your own package", "كوّن باقتك بنفسك")
        val body =
            LocalizedText(
                "Add any of our real, approved offerings to see a running total, then send the whole list to " +
                    "our team on WhatsApp — nothing here is booked or charged automatically.",
                "ضيف أي من خدماتنا الحقيقية والمعتمدة عشان تشوف الإجمالي أول بأول، وبعدين ابعت القائمة كلها " +
                    "لفريقنا على واتساب — مفيش هنا أي حجز أو دفع تلقائي.",
            )
        val addLabel = LocalizedText("Add", "ضيف")
        val addedLabel = LocalizedText("Added", "مضاف")
        val removeLabel = LocalizedText("Remove", "احذف")
        val totalLabel = LocalizedText("Estimated total", "الإجمالي التقديري")
        val emptyLabel =
            LocalizedText(
                "Nothing added yet — pick a few offerings below to start building a package.",
                "لسه مفيش حاجة مضافة — اختار كام خدمة تحت عشان تبدأ تكوّن باقتك.",
            )
        val whatsappCta = LocalizedText("Send this package on WhatsApp", "ابعت الباقة دي على واتساب")
        val navLabel = LocalizedText("Package", "الباقة")
    }

    object Faq {
        val heading = LocalizedText("Frequently asked questions", "الأسئلة الشائعة")
        val body =
            LocalizedText(
                "Straight answers where we have them — and an honest “ask us” where we don't, " +
                    "instead of a guess.",
                "إجابات واضحة لما نعرفها — وصراحة “اسأل فريقنا” لما لسه محسومة، بدل ما نخمّن.",
            )
        val knownHeading = LocalizedText("What we can tell you now", "اللي نقدر نقولك عليه دلوقتي")
        val unknownHeading = LocalizedText("What we confirm with you directly", "اللي بيتأكد معاك مباشرة")
        val unknownIntro =
            LocalizedText(
                "These depend on your exact trip, group and dates, so our team confirms them with you on " +
                    "WhatsApp rather than publishing a one-size-fits-all answer.",
                "دي بتعتمد على تفاصيل رحلتك ومجموعتك ومواعيدك، فريقنا بيأكدها معاك على واتساب بدل إجابة عامة " +
                    "واحدة للكل.",
            )
        val whatsappCta = LocalizedText("Ask us on WhatsApp", "اسأل فريقنا على واتساب")

        val known: List<Pair<LocalizedText, LocalizedText>> =
            listOf(
                LocalizedText("Do I need diving experience to try it?", "محتاج خبرة سابقة في الغوص عشان أجرب؟") to
                    LocalizedText(
                        "No — our intro dives are designed for complete first-timers, with a simple " +
                            "briefing before you go in.",
                        "لأ — الغطسات التجريبية مصممة للمبتدئين تمامًا، مع شرح بسيط قبل ما تنزل.",
                    ),
                LocalizedText("What languages does your team speak?", "الفريق بيتكلم إيه من اللغات؟") to
                    LocalizedText("Arabic, English, Russian, German and Italian.", "عربي، إنجليزي، روسي، ألماني، وإيطالي."),
                LocalizedText("How do I actually book a dive?", "أحجز غطسة إزاي فعليًا؟") to
                    LocalizedText(
                        "Message us on WhatsApp with what you're after. Our team confirms the date, group " +
                            "size and price with you directly before anything is booked.",
                        "راسلنا على واتساب بكل اللي محتاجه. فريقنا بيأكد معاك الموعد وعدد الأفراد والسعر قبل " +
                            "أي حجز.",
                    ),
                LocalizedText("Where are you located?", "أنتم فين بالظبط؟") to location,
                LocalizedText("What are your hours?", "مواعيد شغلكم إيه؟") to hours,
                LocalizedText("Are you PADI accredited?", "أنتم معتمدين من PADI؟") to
                    LocalizedText(
                        "Yes — a PADI 5 Star Dive Center, accredited with CDWS (#100601).",
                        "آه — مركز PADI 5 نجوم، ومعتمد من CDWS برقم 100601.",
                    ),
            )

        val unknown: List<Pair<LocalizedText, LocalizedText>> =
            listOf(
                LocalizedText("What's your cancellation policy?", "سياسة الإلغاء عندكم إيه؟") to
                    LocalizedText("Confirmed directly with you on WhatsApp before booking.", "بتتأكد معاك مباشرة على واتساب قبل الحجز."),
                LocalizedText("What payment methods do you accept?", "بتقبلوا وسائل دفع إيه؟") to
                    LocalizedText(
                        "Ask us on WhatsApp — payment options are confirmed per booking.",
                        "اسألنا على واتساب — وسائل الدفع بتتأكد لكل حجز.",
                    ),
                LocalizedText("Is there a minimum age for diving?", "فيه سن أدنى للغوص؟") to
                    LocalizedText(
                        "Age requirements depend on the specific activity — our team confirms this with you directly.",
                        "بيعتمد على النشاط بالظبط — فريقنا بيأكدلك ده مباشرة.",
                    ),
                LocalizedText("What's included in a boat trip?", "رحلة القارب بتشمل إيه؟") to
                    LocalizedText(
                        "Exact inclusions are confirmed with you on WhatsApp for your specific trip.",
                        "التفاصيل الدقيقة بتتأكد معاك على واتساب حسب رحلتك.",
                    ),
                LocalizedText("Do I need to pay a deposit?", "محتاج أدفع مقدم؟") to
                    LocalizedText(
                        "Deposit requirements, if any, are confirmed directly with our team before booking.",
                        "لو مطلوب مقدم، بيتأكد معاك مباشرة قبل الحجز.",
                    ),
            )
    }
}
