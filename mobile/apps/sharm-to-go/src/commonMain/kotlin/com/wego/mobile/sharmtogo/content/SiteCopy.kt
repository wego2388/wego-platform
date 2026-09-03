package com.wego.mobile.sharmtogo.content

import com.wego.mobile.shared.catalog.LocalizedText

/**
 * Ported verbatim from `web/apps/sharm-to-go-site/app/content/locales.ts` —
 * the same real, approved copy the website shows, so the app never says
 * something the site doesn't. Only the fields this app's screens actually
 * render are ported (no search-bar copy, no design-system-preview links).
 */
object SiteCopy {
    object Nav {
        val home = LocalizedText("Sharm To Go", "Sharm To Go")
        val experiences = LocalizedText("Experiences", "التجارب")
    }

    object Hero {
        val eyebrow = LocalizedText("One clear starting point for Sharm El Sheikh", "نقطة بداية واحدة وواضحة لشرم الشيخ")
        val title =
            LocalizedText(
                "Find the right Sharm experience, with local coordination you can understand.",
                "اختار تجربة شرم المناسبة مع تنسيق محلي مفهوم وواضح.",
            )
        val body =
            LocalizedText(
                "Explore the sea, desert, transfers and local highlights. Availability and provider " +
                    "responsibility will always be shown before a request is confirmed.",
                "اكتشف البحر والصحراء والانتقالات وأهم الأماكن. سنوضح التوفر والجهة المسؤولة عن الخدمة قبل تأكيد أي طلب.",
            )
        val browseCta = LocalizedText("Explore categories", "استكشف الفئات")
    }

    object How {
        val heading = LocalizedText("A request first, a confirmation second", "الأول طلب، وبعد المراجعة تأكيد")
        val steps: List<Pair<LocalizedText, LocalizedText>> =
            listOf(
                LocalizedText("1. Choose", "١. اختار") to
                    LocalizedText(
                        "Select a category, date and group size without guessing the final availability.",
                        "حدد الفئة والموعد والعدد بدون افتراض إن التوفر نهائي.",
                    ),
                LocalizedText("2. We verify", "٢. نراجع") to
                    LocalizedText(
                        "Sharm To Go or the responsible approved provider checks capacity, pickup and price.",
                        "Sharm To Go أو مقدم الخدمة المعتمد يراجع السعة والانتقال والسعر.",
                    ),
                LocalizedText("3. You confirm", "٣. أكّد") to
                    LocalizedText(
                        "You receive one clear summary before payment or final confirmation.",
                        "يوصلك ملخص واضح قبل الدفع أو التأكيد النهائي.",
                    ),
            )
    }

    object Trust {
        val heading = LocalizedText("Built around clarity, not a wall of offers", "وضوح أكتر بدل زحمة عروض")
        val body =
            LocalizedText(
                "The marketplace foundation separates who provides the service, who coordinates the request and what is actually confirmed.",
                "أساس السوق بيفصل بين مقدم الخدمة ومنسق الطلب وما تم تأكيده فعليًا.",
            )
        val points: List<LocalizedText> =
            listOf(
                LocalizedText("Provider shown before confirmation", "توضيح مقدم الخدمة قبل التأكيد"),
                LocalizedText("Arabic and English operations", "تشغيل عربي وإنجليزي"),
                LocalizedText("No invented availability or ratings", "بدون توفر أو تقييمات وهمية"),
                LocalizedText("One support trail for every request", "مسار دعم واحد لكل طلب"),
            )
    }

    val marketplaceNotice =
        LocalizedText(
            "Important: some experiences will be provided by approved local partners, not directly by Sharm To Go. " +
                "The responsible provider will be identified before confirmation.",
            "مهم: بعض التجارب سيقدمها شركاء محليون معتمدون وليست خدمات تابعة مباشرةً لـSharm To Go. سنوضح مقدم الخدمة المسؤول قبل التأكيد.",
        )

    object Browse {
        val allCategories = LocalizedText("All categories", "كل الفئات")
        val emptyHeading = LocalizedText("No live experiences yet", "لا توجد تجارب منشورة بعد")
        val emptyBody =
            LocalizedText(
                "Nothing has been published to this catalog yet — services appear here only after an owner approves them in the dashboard.",
                "لم يتم نشر أي خدمة في هذا الكتالوج حتى الآن — تظهر الخدمات هنا فقط بعد اعتمادها من الداش بورد.",
            )
        val fromPrice = LocalizedText("From", "يبدأ من")
        val perPerson = LocalizedText("per person", "للفرد")
        val perGroup = LocalizedText("per group", "للمجموعة")
        val perVehicle = LocalizedText("per vehicle", "للسيارة")
        val viewDetails = LocalizedText("View details", "عرض التفاصيل")
        val operatedBy = LocalizedText("Operated by", "مقدَّمة من")

        fun photoCount(count: Int): LocalizedText =
            if (count == 1) {
                LocalizedText("1 photo", "صورة واحدة")
            } else {
                LocalizedText("$count photos", "$count صور")
            }
    }

    object Detail {
        val back = LocalizedText("Back to experiences", "العودة للتجارب")
        val notFoundHeading = LocalizedText("This experience isn't available", "هذه التجربة غير متاحة")
        val notFoundBody =
            LocalizedText(
                "It may have been unpublished, or the link may be incorrect. Browse the current live experiences instead.",
                "ربما تم إلغاء نشرها أو أن الرابط غير صحيح. تصفح التجارب المتاحة حاليًا بدلاً من ذلك.",
            )
        val optionsHeading = LocalizedText("Options & pricing", "الخيارات والأسعار")
        val cancellationHeading = LocalizedText("Cancellation policy", "سياسة الإلغاء")
        val pickupHeading = LocalizedText("Pickup", "الانتقال")
        val inclusionsHeading = LocalizedText("Included", "يشمل")
        val exclusionsHeading = LocalizedText("Not included", "لا يشمل")
        val contactHeading = LocalizedText("Interested?", "مهتم؟")
        val contactBody =
            LocalizedText(
                "Online booking for this experience isn't live yet. A direct contact channel will appear here once it is.",
                "الحجز الإلكتروني لهذه التجربة غير متاح بعد. سيظهر هنا وسيلة تواصل مباشرة بمجرد توفرها.",
            )
    }
}
