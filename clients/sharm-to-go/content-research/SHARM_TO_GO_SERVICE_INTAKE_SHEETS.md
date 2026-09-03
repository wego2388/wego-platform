# Sharm To Go — service intake sheets (ready to fill, 37 concepts)

- **Status:** `RESEARCH_ONLY — DRAFT — DO NOT PUBLISH`
- **Purpose:** every concept from `SHARM_TO_GO_PRIVATE_TOURS_DRAFT.md` and
  `SHARM_TO_GO_MISSING_SERVICES_DRAFT.md`, reformatted field-for-field into
  the exact structure `design/SERVICE_CONTENT_TEMPLATE.md` requires before a
  service can enter the real publication workflow. Nothing here is a real
  fact unless explicitly stated as such.
- **What is filled in already:** the name, description, category, duration,
  and price basis this research already established, plus the "critical
  check"/verification items an operator interview must resolve.
- **What is marked `TO CONFIRM` and why it is not guessed:** operator
  identity/contact, capacity, exact schedule, EGP price, cancellation
  wording, and media rights are exactly the fields `Service.publish()`
  enforces as mandatory in `products/travel-marketplace` — a fabricated
  value here would either block real customers with wrong information or,
  worse, misrepresent a real, uninvolved business as a Sharm To Go partner.
  A market-reference line is kept under each concept only as a competitor
  benchmark, never as a proposed Sharm To Go price.
- **How to use this:** fill in the `TO CONFIRM` lines for one concept (start
  with the owner-flagged priority below), then that block becomes the real
  input for creating the Provider/Category/Service records through the ERP
  dashboard or the staff API — the same Packet 1A workflow already proven
  live (DRAFT → REVIEW → APPROVED → PUBLISHED).

---

### STG-TRN-001 — Sharm Airport Private Transfer

**Owner-requested first launch target.**

```text
Internal service ID: STG-TRN-001
Publication status: DRAFT

Category: Transfers (production category)
English name: Sharm Airport Private Transfer
Arabic name: انتقال خاص من وإلى مطار شرم
English short description: Pre-arrange a private one-way transfer between Sharm El Sheikh Airport and a confirmed hotel or local zone, matched to the party and luggage size.
Arabic short description: احجز مسبقًا انتقالًا خاصًا في اتجاه واحد بين مطار شرم الشيخ وفندق أو منطقة محلية يتم تأكيدها، بسيارة تناسب عدد الركاب والأمتعة.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: About 1 hour, depending on zone and traffic.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_VEHICLE
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Airport arrival; airport departure; vehicle classes based on licensed passenger and luggage capacity.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Operator licence, exact zones/supplements, vehicle class, passenger/luggage capacity, terminal meeting point, name board, flight tracking, delay waiting window, departure lead time, child seats, accessibility, contact channel, no-show policy, round-trip treatment.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only, not a Sharm To Go price: source site shows EUR 17 for one guest or EUR 11 per adult for groups of 2-13, child EUR 10.
- **Rollout note:** Owner-requested first launch candidate — operationally simplest, no marine/animal/attraction dependency.
### STG-TRN-002 — Private Venue Return Transfer

```text
Internal service ID: STG-TRN-002
Publication status: DRAFT

Category: Transfers (production category)
English name: Private Venue Return Transfer
Arabic name: انتقال خاص ذهابًا وعودة إلى وجهة داخل شرم
English short description: Book private transport to a confirmed Sharm venue with either one-way travel or a pre-agreed return pickup time; venue entry and reservations remain separate unless explicitly included.
Arabic short description: احجز انتقالًا خاصًا إلى وجهة محددة داخل شرم في اتجاه واحد أو مع موعد عودة متفق عليه مسبقًا، ولا تشمل الخدمة الدخول أو الحجز في المكان إلا إذا تم النص على ذلك بوضوح.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: Route plus an agreed waiting/return window (a Farsha example in the source runs about 3 hours).
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_VEHICLE
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: One-way; round trip; additional waiting time.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Destination access/opening, reservation ownership, pickup/drop-off points, waiting duration, overtime, capacity, child seats, mobility needs, late guest/venue policy, venue-full contingency.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source Farsha example shows EUR 10/EUR 5 adult/child one-way and EUR 13/EUR 6 return.
- **Rollout note:** Second transfer concept, same operational family as STG-TRN-001.
### STG-SEA-003 — Glass-Bottom Reef Boat

```text
Internal service ID: STG-SEA-003
Publication status: DRAFT

Category: Sea adventures (production category)
English name: Glass-Bottom Reef Boat
Arabic name: قارب القاع الزجاجي لمشاهدة الشعاب
English short description: View coastal reefs from a glass-bottom boat on a short outing designed for guests who prefer to stay dry while enjoying the Red Sea scenery.
Arabic short description: شاهد الشعاب القريبة من الساحل عبر قاع زجاجي في جولة قصيرة تناسب من يفضلون الاستمتاع بمناظر البحر الأحمر من دون نزول الماء.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 2 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: TO CONFIRM

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Exact boat, viewing-window quality, route, sailing time versus transfer time, capacity, boarding accessibility, child/infant rules, pickup zones, cancellation for poor visibility/sea conditions.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 27.50 adult, EUR 16 child.
- **Rollout note:** Early launch candidate after vessel evidence.
### STG-NAT-001 — Ras Mohammed by Road

```text
Internal service ID: STG-NAT-001
Publication status: DRAFT

Category: Sea adventures (production category)
English name: Ras Mohammed by Road
Arabic name: رحلة برية إلى محمية رأس محمد
English short description: Explore selected land stops inside Ras Mohammed National Park by road, with shore snorkeling offered only where access and sea conditions are suitable.
Arabic short description: استكشف محطات برية مختارة داخل محمية رأس محمد بالسيارة، مع إتاحة السنوركلينج من الشاطئ فقط عندما تسمح نقاط الدخول وحالة البحر بذلك.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 5 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Small group; private vehicle.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Exact permitted stops, park ticket/fee, vehicle and guide, pickup, shore access, swimming rules, equipment, changing facilities, environmental protections, weather policy. Never present the Magic Lake name as a health claim.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 12.50/EUR 9.50 adult/child (group); private tiers EUR 85-140 depending on party size.
- **Rollout note:** Early launch candidate after park evidence.
### STG-FAM-001 — Aqua Park Day

```text
Internal service ID: STG-FAM-001
Publication status: DRAFT

Category: Family activities (new category — not yet on the live site)
English name: Aqua Park Day
Arabic name: يوم في الأكوا بارك
English short description: Arrange a full aqua-park day with return hotel transport and a clearly selected ticket or meal package.
Arabic short description: استمتع بيوم كامل في الأكوا بارك مع انتقال من الفندق والعودة إليه، واختيار واضح بين تذكرة الدخول أو باقة تشمل الوجبة.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 7 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Entry and transfer; entry, transfer, buffet lunch, and soft drinks.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Named venue, ticket validity, opening/maintenance days, slide height/age rules, lifeguard and supervision policy, locker/towel costs, food/drink scope, swimwear rules, accessibility, pickup, cancellation.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 55/EUR 37 adult/child (entry+transfer), EUR 66/EUR 46 with meal.
- **Rollout note:** Launch candidate once a direct venue-ticket agreement exists.
### STG-SEA-001 — Ras Mohammed & White Island Snorkeling Cruise

```text
Internal service ID: STG-SEA-001
Publication status: DRAFT

Category: Sea adventures (production category)
English name: Ras Mohammed & White Island Snorkeling Cruise
Arabic name: رحلة سنوركلينج إلى رأس محمد والجزيرة البيضاء
English short description: Spend a full day on the Red Sea with a shared boat, guided snorkeling stops, time near White Island, and an onboard meal, subject to park access and sea conditions.
Arabic short description: اقضِ يومًا كاملًا في البحر الأحمر على متن قارب مشترك، مع محطات سنوركلينج بصحبة مرشد ووقت بالقرب من الجزيرة البيضاء ووجبة على القارب، وفق تصاريح المحمية وحالة البحر.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 7 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Equipment excluded; equipment included.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Named boat and marina, licensed capacity, passenger manifest, park permit/fee, guide and life-jacket provision, swimming ability, exact island-access wording, meal/allergies, pickup, weather cancellation. Do not attach any dive add-on — route diving through Sharm Divers Club.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 28/EUR 17 (no gear), EUR 29.90/EUR 18 (with gear); a EUR 5 park fee is listed separately.
- **Rollout note:** Sea cruise family — after permits, safety and capacity are documented.
### STG-SEA-002 — Tiran Snorkeling Cruise

```text
Internal service ID: STG-SEA-002
Publication status: DRAFT

Category: Sea adventures (production category)
English name: Tiran Snorkeling Cruise
Arabic name: رحلة سنوركلينج بحرية إلى تيران
English short description: Sail on a shared full-day cruise toward the Tiran area for guided snorkeling stops, onboard refreshments, and time to relax between stops, with the route confirmed for the day's conditions.
Arabic short description: انطلق في رحلة بحرية مشتركة ليوم كامل باتجاه منطقة تيران، مع محطات سنوركلينج بصحبة مرشد ومشروبات ووقت للاسترخاء، على أن يتم تأكيد المسار حسب ظروف اليوم.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 7 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Equipment excluded; equipment included.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Do not promise landing on Tiran Island. Confirm permitted sailing/snorkeling area, boat, capacity, equipment, guide, pickup, lunch, child/swimmer rules, marine fee, weather alternative.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 25.50/EUR 17 (no gear), EUR 29.50/EUR 20 (with gear); a EUR 5 marine-park fee is listed separately.
- **Rollout note:** Sea cruise family.
### STG-SEA-004 — Parasailing & Water Sports

```text
Internal service ID: STG-SEA-004
Publication status: DRAFT

Category: Family activities (new category — not yet on the live site)
English name: Parasailing & Water Sports
Arabic name: باراسيلنج وألعاب مائية
English short description: Choose a parasailing flight on its own or pair it with selected towable water activities and a short coastal boat program, subject to the marine operator's safety decision.
Arabic short description: اختر تجربة باراسيلنج مستقلة أو اجمعها مع ألعاب مائية مختارة وبرنامج بحري قصير، وفق قرار مشغّل النشاط بخصوص السلامة.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 2 hours for parasailing alone; up to 4 hours for a broader water-sports package.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Tandem parasailing; parasailing plus banana boat; broader glass-boat/water-sports package.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Minimum age and weight, maximum combined weight, flight duration, tandem/solo rules, life jackets, equipment, insurance, transfer, activity list, medical exclusions, photo policy, weather refund/reschedule.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 26.50/EUR 15 (parasailing), EUR 37.50/EUR 22 (with banana boat), EUR 34/EUR 30 (broader package).
- **Rollout note:** Sea cruise/water-sports family.
### STG-SEA-005 — Desert & Sea Adventure Combo

```text
Internal service ID: STG-SEA-005
Publication status: DRAFT

Category: Sea adventures (production category)
English name: Desert & Sea Adventure Combo
Arabic name: مغامرة تجمع بين الصحراء والبحر
English short description: Combine a guided quad or buggy session with a coordinated coastal program of parasailing, a glass-bottom boat, and selected water activities in one day.
Arabic short description: اجمع في يوم واحد بين جولة بالدراجة الرباعية أو الباجي وبرنامج بحري منسق يشمل الباراسيلنج وقارب القاع الزجاجي وألعابًا مائية مختارة.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 5-6 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Quad package; buggy package.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Whether one operator owns the complete service, transfer/handover between desert and marine teams, every age/weight rule, actual activity durations, meals, equipment, permits, insurance, weather-cancellation fallback for either half.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 41.99/EUR 41 (quad), EUR 49/EUR 47 (buggy); a combined listing shows EUR 37/EUR 36.
- **Rollout note:** Later combination — after both component services are proven independently.
### STG-DSR-001 — Desert Evening: Quad, Camel, Dinner & Show

```text
Internal service ID: STG-DSR-001
Publication status: DRAFT

Category: Desert and stargazing (production category)
English name: Desert Evening: Quad, Camel, Dinner & Show
Arabic name: أمسية صحراء بالدراجة والجمل والعشاء والعرض
English short description: Ride into the desert by quad, pause for a short camel experience, then finish at a Bedouin-style camp with dinner and an evening performance.
Arabic short description: انطلق بالدراجة الرباعية في الصحراء، وتوقف لتجربة قصيرة لركوب الجمل، ثم اختتم الجولة في مخيم بطابع بدوي مع العشاء وعرض مسائي.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 5 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Shared double quad; single quad; private trip.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Driver/passenger ages, helmet and goggle provision, vehicle maintenance, route permit, convoy ratio, camel welfare, named camp, menu/allergies, exact show content, tax/fees, pickup, safe return time.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows a range from EUR 18/EUR 15 to EUR 33/EUR 30 adult/child; scarf, goggles and meal upgrades are separate add-ons.
- **Rollout note:** Desert family — after permits, safety and capacity are documented.
### STG-DSR-002 — Buggy Desert Experience

```text
Internal service ID: STG-DSR-002
Publication status: DRAFT

Category: Desert and stargazing (production category)
English name: Buggy Desert Experience
Arabic name: مغامرة باجي في الصحراء
English short description: Travel across a guided desert route in a two- or four-seat buggy, with sunset, camel, camp, or meal elements included only in the selected confirmed option.
Arabic short description: استكشف مسارًا صحراويًا بصحبة قائد باستخدام باجي بمقعدين أو أربعة مقاعد، مع إضافة الغروب أو الجمل أو المخيم أو الوجبة فقط حسب الخيار المؤكد.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: Up to 5 hours for the sunset/dinner program.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_VEHICLE (seats/vehicle, not per rider) — confirm with operator before publishing.
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Shared family buggy; two-seat buggy; private buggy.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Price basis must reflect seats/vehicle honestly. Confirm licence, driver age, passenger limit, seat belts/helmets, maintenance, insurance, route, meal/camel elements, pickup, dust protection, damage liability.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: sunset variants show EUR 17/EUR 16 to EUR 27/EUR 18.50 adult/child.
- **Rollout note:** Desert family.
### STG-DSR-003 — Arabian Horse Riding

```text
Internal service ID: STG-DSR-003
Publication status: DRAFT

Category: Desert and stargazing (production category)
English name: Arabian Horse Riding
Arabic name: تجربة ركوب الخيل العربي
English short description: Take a guided horseback ride on an approved desert or coastal route, with the horse and pace matched to the rider's declared experience.
Arabic short description: استمتع بجولة خيل بصحبة مرشد على مسار صحراوي أو ساحلي معتمد، مع اختيار الحصان وسرعة الجولة بما يناسب خبرة الراكب المعلنة.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 1 or 2 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: One-hour desert ride; two-hour desert ride; coastal ride where legal access is confirmed.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Stable/operator identity, route permission, animal welfare, rider age/weight/experience, helmets, guide ratio, insurance, beach access, heat limits, transfer, no-riding health restrictions.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 15/EUR 13 (1h), EUR 27/EUR 24 (2h), EUR 25/EUR 22 (coastal).
- **Rollout note:** Desert family.
### STG-DSR-004 — Bedouin Dinner & Stargazing

```text
Internal service ID: STG-DSR-004
Publication status: DRAFT

Category: Desert and stargazing (production category)
English name: Bedouin Dinner & Stargazing
Arabic name: عشاء بدوي ومشاهدة النجوم
English short description: Spend an evening at a desert camp with a short camel experience, dinner, local-style entertainment, and guided stargazing, without requiring guests to drive a quad.
Arabic short description: اقضِ أمسية في مخيم صحراوي تشمل تجربة قصيرة لركوب الجمل والعشاء وفقرات ترفيهية بطابع محلي ومشاهدة النجوم بصحبة مرشد، من دون اشتراط قيادة دراجة رباعية.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 5 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Shared evening; upgraded table; private evening.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Camp identity, cultural accuracy, camel welfare, telescope/astronomer claim, menu/allergies, accessibility, toilets, temperature, performance content, transport, child rules, late return.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 25/EUR 23 (shared), EUR 29/EUR 27 (upgraded), EUR 39/EUR 28 (private).
- **Rollout note:** Desert family.
### STG-DSR-005 — Sunrise or Morning Quad Safari

```text
Internal service ID: STG-DSR-005
Publication status: DRAFT

Category: Desert and stargazing (production category)
English name: Sunrise or Morning Quad Safari
Arabic name: سفاري بالدراجة وقت الشروق أو الصباح
English short description: Choose an early sunrise or morning quad route with a safety briefing and guided desert convoy, keeping optional camel and camp stops separate from the base ride.
Arabic short description: اختر مسارًا بالدراجة الرباعية وقت الشروق أو الصباح، مع تعليمات سلامة وقافلة صحراوية بصحبة قائد، مع فصل الجمل وتوقفات المخيم الاختيارية عن الجولة الأساسية.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 3 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Sunrise group; morning group. (The private version is the separate STG-PT-008 concept.)

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Driver/passenger ages, sunrise pickup window, lighting/visibility, helmet/goggles, route and convoy supervision, insurance, heat/dust policy, fees.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 14.50/EUR 13.90 group adult/child.
- **Rollout note:** Desert family.
### STG-PT-001 — Private Sharm City Essentials

```text
Internal service ID: STG-PT-001
Publication status: DRAFT

Category: City & culture (production category)
English name: Private Sharm City Essentials
Arabic name: جولة خاصة لأهم معالم شرم
English short description: Discover Sharm's best-known city landmarks at a flexible pace, with private transport and time for the Old Market, places of worship, viewpoints, and an optional Naama Bay stop.
Arabic short description: اكتشف أشهر معالم مدينة شرم الشيخ بإيقاع مرن، مع انتقال خاص ووقت لزيارة السوق القديم ودور العبادة ونقاط المشاهدة، مع إمكانية إضافة توقف في خليج نعمة.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 3 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Standard private tour; female-guide request.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Add-ons (Farsha transfer, SOHO transfer, museum admission, meal) each need their own operator cost, opening-hours check, and EGP price.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 10-12.50 adult / EUR 6.50-8.50 child (standard); EUR 12-15 / EUR 6-12 (female guide).
### STG-PT-002 — Sharm Museum and City Choices

```text
Internal service ID: STG-PT-002
Publication status: DRAFT

Category: City & culture (production category)
English name: Sharm Museum and City Choices
Arabic name: متحف شرم وخيارات جولة المدينة
English short description: Pair the Sharm El Sheikh Museum with a private city itinerary, choosing the Old Market, Farsha, SOHO Square, or a broader highlights route according to the selected package.
Arabic short description: اجمع بين زيارة متحف شرم الشيخ وجولة خاصة داخل المدينة، مع اختيار السوق القديم أو فرشة أو سوهو سكوير أو مسار أشمل لأهم المعالم حسب الباقة.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 3-5 hours, depending on the route.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Museum and Old Market; museum and Farsha; museum and SOHO; museum with full city highlights.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Museum ticket type, opening hours, guide licensing, whether drinks/table reservations at Farsha are excluded.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 18-25 adult / EUR 8-15 child.
### STG-PT-003 — Sharm City and Parasailing

```text
Internal service ID: STG-PT-003
Publication status: DRAFT

Category: Family activities (new category — not yet on the live site)
English name: Sharm City and Parasailing
Arabic name: جولة شرم وتجربة الباراسيلنج
English short description: Combine a private introduction to Sharm with a short parasailing flight above the Red Sea, subject to weather and the marine operator's safety decision.
Arabic short description: اجمع بين جولة خاصة للتعرف على شرم الشيخ وتجربة باراسيلنج قصيرة فوق البحر الأحمر، وفق حالة الطقس وقرار مشغّل النشاط البحري.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 4 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Tandem parasailing; solo flight as an approved upgrade.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Minimum age/weight, maximum combined weight, weather cancellation, insurance, equipment, whether photos/video cost extra.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 39 adult, EUR 34 child, EUR 10 solo upgrade.
### STG-PT-004 — City Sights and Desert Adventure

```text
Internal service ID: STG-PT-004
Publication status: DRAFT

Category: Desert and stargazing (production category)
English name: City Sights and Desert Adventure
Arabic name: معالم المدينة ومغامرة الصحراء
English short description: Split one private outing between Sharm's city highlights and an ATV ride into the nearby desert, with optional camel ride, dinner, and stargazing components.
Arabic short description: اقضِ جولة خاصة تجمع بين معالم شرم الشيخ وقيادة الدراجة الرباعية في الصحراء القريبة، مع خيارات لركوب الجمل والعشاء ومشاهدة النجوم.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 5 hours before optional extensions.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: TO CONFIRM

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: ATV driver/passenger ages, helmet/goggle inclusion, route permit, meal venue, show details, return time.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 23/EUR 15 (city+ATV), EUR 36/EUR 23 (with dinner/camel), EUR 8 (telescope add-on).
### STG-PT-005 — Sharm Highlights and Seafood Meal

```text
Internal service ID: STG-PT-005
Publication status: DRAFT

Category: City & culture (production category)
English name: Sharm Highlights and Seafood Meal
Arabic name: معالم شرم ووجبة مأكولات بحرية
English short description: Explore selected Sharm landmarks by private car, then sit down for a seafood lunch or dinner arranged as part of the itinerary.
Arabic short description: استكشف مجموعة من معالم شرم الشيخ بسيارة خاصة، ثم استمتع بوجبة غداء أو عشاء من المأكولات البحرية ضمن برنامج الرحلة.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 4 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: TO CONFIRM

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Named restaurant, fixed menu, drinks, allergies, child meal, restaurant reservation, who pays any difference.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 27 adult, EUR 21 child.
### STG-PT-006 — SOHO Square and Hollywood Park Transfer

```text
Internal service ID: STG-PT-006
Publication status: DRAFT

Category: Family activities (new category — not yet on the live site)
English name: SOHO Square and Hollywood Park Transfer
Arabic name: انتقال خاص إلى سوهو سكوير وهوليوود بارك
English short description: A private return transfer connecting two Sharm entertainment areas, with independent free time at each confirmed stop.
Arabic short description: انتقال خاص ذهابًا وعودة بين منطقتين ترفيهيتين في شرم الشيخ، مع وقت حر مستقل في كل محطة يتم تأكيدها.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: Source states 2 hours — verify against venue evening hours before use.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON (source model) — confirm whether PER_VEHICLE fits better.
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: TO CONFIRM

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Source daytime start conflicts with the venues' usual evening operation. Confirm venue status, opening hours, entry tickets, waiting time, and pricing basis.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 40 adult, EUR 20 child, minimum 2.
### STG-PT-007 — Private Car and Driver in Sharm

```text
Internal service ID: STG-PT-007
Publication status: DRAFT

Category: Transfers (production category)
English name: Private Car and Driver in Sharm
Arabic name: سيارة خاصة بسائق داخل شرم
English short description: Reserve a private vehicle and driver for up to three agreed stops inside Sharm, with the route confirmed before pickup.
Arabic short description: احجز سيارة خاصة بسائق لزيارة ما يصل إلى ثلاث محطات متفق عليها داخل شرم الشيخ، مع تأكيد المسار قبل موعد الاستلام.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 3 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_VEHICLE
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: TO CONFIRM

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Vehicle class, licensed passenger capacity, kilometre/area limits, waiting/overtime fee, attraction tickets, child seats, driver language. Should not silently become a guided city tour.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 24.90 for one person, EUR 11.50 per adult for 2-13, EUR 9 child.
### STG-PT-008 — Private ATV Desert Experience

```text
Internal service ID: STG-PT-008
Publication status: DRAFT

Category: Desert and stargazing (production category)
English name: Private ATV Desert Experience
Arabic name: مغامرة سفاري خاصة بالدراجات الرباعية
English short description: A private guided quad route through the Sharm desert with a safety briefing and a Bedouin stop, offered in morning, sunrise, or sunset windows when operationally available.
Arabic short description: مسار سفاري خاص بالدراجات الرباعية في صحراء شرم مع شرح للسلامة وتوقف بدوي، ويُتاح صباحًا أو وقت الشروق أو الغروب حسب التشغيل.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 3 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Sunrise; morning; sunset; private-transfer upgrade.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Infants conflict in the source and must be prohibited unless the operator supplies a safe, documented rule. Confirm driver age, passenger age, helmets/goggles, insurance, distance, environmental fees.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 29.90-40 adult, EUR 18-24 child.
### STG-PT-009 — Private Red Sea Yacht Day

```text
Internal service ID: STG-PT-009
Publication status: DRAFT

Category: Sea adventures (production category)
English name: Private Red Sea Yacht Day
Arabic name: يوم خاص على يخت في البحر الأحمر
English short description: Charter a private yacht for a full Red Sea day with snorkeling stops, onboard refreshments, and a meal selected with the operator.
Arabic short description: استأجر يختًا خاصًا ليوم كامل في البحر الأحمر، مع محطات للسنوركلينج ومشروبات على متن اليخت ووجبة يتم تحديدها مع المشغّل.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 7 hours — do not use a shorter 'half day' label until an operator confirms it.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_GROUP
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Standard lunch; seafood lunch.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Exact yacht identity, licensed capacity, marina, crew, route, snorkeling guide/equipment, national-park fees, menu, weather policy, passenger manifest, marine permits.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only, and internally conflicting across source pages: EUR 870 shown covering up to 10 people on one page and up to 20 on two others; another scale adds EUR 25/person after 10 up to 25; a premium benchmark starts at EUR 990.
### STG-PT-010 — Private Speedboat Snorkeling

```text
Internal service ID: STG-PT-010
Publication status: DRAFT

Category: Sea adventures (production category)
English name: Private Speedboat Snorkeling
Arabic name: سنوركلينج بقارب سريع خاص
English short description: Take a private speedboat to selected snorkeling areas, with the exact reefs and route confirmed according to sea conditions and permit access.
Arabic short description: انطلق بقارب سريع خاص إلى مناطق سنوركلينج يتم تحديدها حسب حالة البحر وتصاريح الوصول إلى مواقع الشعاب.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 2 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_GROUP
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Two local reef stops; Tiran route for up to 5; larger Tiran boat for up to 11.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Tiran access, boat/captain licence, capacity, life jackets, equipment condition, guide, hotel transfer, exact reef promise, weather cancellation.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 145 group (up to 5) or EUR 170 (up to 11); per-person tiers EUR 130 solo, EUR 60 each for 2, ~EUR 55 each for 3-10.
### STG-PT-011 — Desert ATV and Speedboat Combo

```text
Internal service ID: STG-PT-011
Publication status: DRAFT

Category: Sea adventures (production category)
English name: Desert ATV and Speedboat Combo
Arabic name: مغامرة صحراء وقارب سريع
English short description: Combine a guided ATV route with a private speedboat session and snorkeling stops in one coordinated itinerary.
Arabic short description: اجمع بين مسار سفاري بالدراجة الرباعية وجولة بقارب سريع خاص ومحطات للسنوركلينج ضمن برنامج واحد منسق.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 5 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: TO CONFIRM

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: The child price is an outlier and must not be reused without written confirmation. Confirm every desert and marine safety rule, transfer between operators, admissions, permits, weather fallback.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 120 adult, EUR 10 child (outlier — must not be reused without written confirmation), minimum 3.
### STG-PT-012 — Private Dahab and Canyon Adventure

```text
Internal service ID: STG-PT-012
Publication status: DRAFT

Category: Desert and stargazing (production category)
English name: Private Dahab and Canyon Adventure
Arabic name: رحلة خاصة إلى دهب والوديان
English short description: Travel privately from Sharm toward Dahab for a canyon and coastal day, choosing either a sightseeing-focused route or a full program with ATV and camel components.
Arabic short description: انطلق في رحلة خاصة من شرم الشيخ إلى دهب ليوم يجمع بين الوديان والساحل، مع اختيار برنامج مشاهدة أساسي أو برنامج كامل يشمل الدراجة الرباعية وركوب الجمل.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 8 hours.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: TO CONFIRM

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Actual canyon and Blue Hole/Abu Galum access and fees, snorkeling equipment, meal, camel/ATV safety, road permits, guide role.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 95/EUR 74 (without rides), EUR 110/EUR 84 (full program), minimum 2.
### STG-PT-013 — Private Cairo Pyramids and Museum Day

```text
Internal service ID: STG-PT-013
Publication status: DRAFT

Category: Heritage and day trips (research-only category — no production fit yet)
English name: Private Cairo Pyramids and Museum Day
Arabic name: يوم خاص إلى أهرامات القاهرة والمتحف
English short description: A private long-distance day from Sharm to the Giza Plateau and a confirmed Cairo museum, with transport mode and admission plan agreed before booking.
Arabic short description: يوم خاص طويل من شرم الشيخ إلى منطقة أهرامات الجيزة ومتحف يتم تأكيده في القاهرة، مع الاتفاق مسبقًا على وسيلة الانتقال وتذاكر الدخول.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: Full day; source road start 01:00.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: TO CONFIRM

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Exact museum (Cairo museum routing has changed over time), road/flight mode, permits, entry requirements, visa applicability, Egyptologist licence, meal, return time.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 190 adult, EUR 180 child, minimum 2; pyramid interior EUR 35; a source visa add-on EUR 38.
- **Rollout note:** Later long-distance — after the simpler local workflow is proven.
### STG-PT-014 — Giza, Saqqara, Memphis and Khan El-Khalili

```text
Internal service ID: STG-PT-014
Publication status: DRAFT

Category: Heritage and day trips (research-only category — no production fit yet)
English name: Giza, Saqqara, Memphis and Khan El-Khalili
Arabic name: الجيزة وسقارة وممفيس وخان الخليلي
English short description: Cover several major Cairo heritage areas in a private day from Sharm, with road and flight versions treated as separate operational options.
Arabic short description: زر مجموعة من أهم المناطق التاريخية في القاهرة خلال يوم خاص من شرم الشيخ، مع التعامل مع السفر برًا والطيران كخيارين تشغيليين منفصلين.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: Full day.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Road; road with pyramid interior and camel; plane.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Flight inventory and baggage, exact road timings, every admission, visa applicability, Egyptologist licence, meal, feasibility of the full route without rushed or misleading promises.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 240/EUR 180 (road), EUR 279/EUR 210 (road+extras), EUR 339/EUR 269 (plane), minimum 2.
- **Rollout note:** Later long-distance.
### STG-NAT-002 — Colored Canyon, Blue Hole & Dahab Day

```text
Internal service ID: STG-NAT-002
Publication status: DRAFT

Category: Desert and stargazing (production category)
English name: Colored Canyon, Blue Hole & Dahab Day
Arabic name: يوم إلى الوادي الملون والبلو هول ودهب
English short description: Join a group day from Sharm toward Dahab for a confirmed canyon route, coastal time, and selected snorkeling or camel elements, with every admission stated before booking.
Arabic short description: انضم إلى رحلة جماعية من شرم باتجاه دهب تشمل مسار وادٍ يتم تأكيده ووقتًا على الساحل وخيارات محددة للسنوركلينج أو ركوب الجمل، مع توضيح جميع رسوم الدخول قبل الحجز.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 8 hours; source start 08:30.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Group without admissions; group with confirmed Blue Hole admission. (The private route is the separate STG-PT-012 concept.)

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Exact canyon and route, Blue Hole/Abu Galum permits and fees, snorkeling safety, camel welfare, lunch, road permits, vehicle, guide, accessibility, pickup, realistic return time.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 15/EUR 11 adult/child (without entrance), EUR 34/EUR 19 (with entry); ATV and snorkeling equipment are separate add-ons.
- **Rollout note:** Desert/nature family.
### STG-CUL-001 — Mount Sinai Sunrise & St Catherine

```text
Internal service ID: STG-CUL-001
Publication status: DRAFT

Category: Heritage and day trips (research-only category — no production fit yet)
English name: Mount Sinai Sunrise & St Catherine
Arabic name: شروق جبل موسى ودير سانت كاترين
English short description: Depart Sharm at night for a guided Mount Sinai ascent before sunrise, followed by a monastery visit when opening conditions allow.
Arabic short description: غادر شرم ليلًا لصعود جبل موسى بصحبة مرشد قبل الشروق، ثم زيارة الدير عندما تسمح مواعيد الفتح وظروف التشغيل.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: Overnight/full-day pattern; source departure 20:00.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Shared group; private vehicle/guide.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Licensed mountain guide, physical difficulty and medical warnings, route and emergency plan, cold-weather preparation, lighting, toilets, camel segment boundaries, monastery schedule/dress code, passport requirements, food, transport, cancellation for unsafe conditions.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 24/EUR 14 (group), EUR 110/EUR 70 private base with lower per-person tiers from two guests.
- **Rollout note:** Specialist operator required.
### STG-CUL-002 — St Catherine & Dahab Day

```text
Internal service ID: STG-CUL-002
Publication status: DRAFT

Category: Heritage and day trips (research-only category — no production fit yet)
English name: St Catherine & Dahab Day
Arabic name: يوم إلى سانت كاترين ودهب
English short description: Visit St Catherine by road, then continue to a confirmed Dahab stop, with the monastery program and free-time arrangements stated before departure.
Arabic short description: زر سانت كاترين برًا ثم واصل إلى محطة يتم تحديدها في دهب، مع توضيح برنامج الدير وترتيبات الوقت الحر قبل الانطلاق.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 9 hours; source departure 05:00.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Shared coach; private vehicle and guide.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Monastery opening days/hours, exact accessible areas, museum ticket, dress code, licensed guide, vehicle/road permit, meal, Dahab stop, accessibility, pickup, return time.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 27/EUR 16 (group), EUR 129/EUR 89 (private, minimum 2); a EUR 10 monastery-museum entry is listed separately.
- **Rollout note:** Specialist operator required.
### STG-DAY-001 — Cairo by Coach

```text
Internal service ID: STG-DAY-001
Publication status: DRAFT

Category: Heritage and day trips (research-only category — no production fit yet)
English name: Cairo by Coach
Arabic name: رحلة إلى القاهرة بالأتوبيس
English short description: Travel overnight by coach from Sharm for a guided Cairo day covering the Giza Plateau and one specifically named museum, with admissions and optional stops stated in advance.
Arabic short description: سافر ليلًا بالأتوبيس من شرم لقضاء يوم بصحبة مرشد في القاهرة يشمل منطقة أهرامات الجيزة ومتحفًا يتم تحديد اسمه، مع توضيح التذاكر والمحطات الاختيارية مسبقًا.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: Full day; source departure 01:00.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Egyptian Museum route; Grand Egyptian Museum route.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Exact museum/date, coach licence/capacity, two-driver and rest plan, road/security permissions, guide licence, every admission, meal, pickup, visa applicability, optional sales stops, accessibility, return time, delay/cancellation handling.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 53/EUR 39.90 (Egyptian Museum), EUR 82/EUR 64.90 (GEM route).
- **Rollout note:** Later long-distance.
### STG-DAY-002 — Cairo by Air

```text
Internal service ID: STG-DAY-002
Publication status: DRAFT

Category: Heritage and day trips (research-only category — no production fit yet)
English name: Cairo by Air
Arabic name: رحلة إلى القاهرة بالطائرة
English short description: Fly from Sharm for a guided Cairo day at the Giza Plateau and a confirmed museum, with flight details and all timed entries confirmed before the booking becomes final.
Arabic short description: سافر بالطائرة من شرم لقضاء يوم بصحبة مرشد في القاهرة لزيارة أهرامات الجيزة ومتحف يتم تأكيده، مع تثبيت تفاصيل الرحلة الجوية ومواعيد الدخول قبل أن يصبح الحجز نهائيًا.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: Full day; source pickup/departure pattern starts 04:00.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Shared group; private guide/vehicle in Cairo.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Real-time flight inventory and fare validity, names and ID/passport data, baggage, change/refund rules, airport transfers, missed connection, exact museum and tickets, licensed guide, meal, visa applicability, accessibility, return disruption support.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 279 (group, per adult or child), EUR 329 (private, minimum 2).
- **Rollout note:** Later long-distance.
### STG-DAY-003 — Luxor by Air

```text
Internal service ID: STG-DAY-003
Publication status: DRAFT

Category: Heritage and day trips (research-only category — no production fit yet)
English name: Luxor by Air
Arabic name: رحلة إلى الأقصر بالطائرة
English short description: Fly from Sharm for a guided day among confirmed Luxor sites, with the flight, ground route, admissions, and optional Nile crossing stated before confirmation.
Arabic short description: سافر بالطائرة من شرم لقضاء يوم بصحبة مرشد في مواقع يتم تحديدها بالأقصر، مع توضيح الرحلة الجوية والمسار البري والتذاكر وخيار عبور النيل قبل التأكيد.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: Full day; source pickup/departure pattern starts 04:00.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Shared guided day; private ground tour.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Flight inventory and fare expiry, baggage, airport transfers, named east/west-bank sites, tickets/tombs, guide licence, heat and mobility limits, meal, Nile-boat safety, child/infant treatment, disrupted-flight support.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 245/EUR 240 (shared), EUR 330/EUR 325 (private); an infant price on the source needs explanation before use.
- **Rollout note:** Later long-distance.
### STG-DAY-004 — Petra by Ferry & Coach

```text
Internal service ID: STG-DAY-004
Publication status: DRAFT

Category: Heritage and day trips (research-only category — no production fit yet)
English name: Petra by Ferry & Coach
Arabic name: رحلة إلى البتراء بالعبّارة والأتوبيس
English short description: Make a cross-border day journey from Sharm to Petra using confirmed road and ferry connections, with immigration documents, timings, and inclusions checked for the guest before confirmation.
Arabic short description: انطلق من شرم في رحلة يوم عبر الحدود إلى البتراء باستخدام انتقالات برية وعبّارة يتم تأكيدها، مع مراجعة مستندات السفر والمواعيد وما تشمله الرحلة لكل ضيف قبل التأكيد.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: Full day; source departure 01:00.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: One tightly defined ferry/coach itinerary only — no instant-confirmation variant.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Nationality-specific passport/visa/entry rules, border fees, ferry ticket and weather cancellation, coach and guide licences in each jurisdiction, Petra admission, meal, walking difficulty, horse/buggy boundary, schedule feasibility, emergency support, refund ownership across suppliers.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 220 adult, EUR 195 child.
- **Rollout note:** Later cross-border.
### STG-MUL-001 — Two-Day Cairo by Air

```text
Internal service ID: STG-MUL-001
Publication status: DRAFT

Category: Heritage and day trips (research-only category — no production fit yet)
English name: Two-Day Cairo by Air
Arabic name: رحلة يومين إلى القاهرة بالطائرة
English short description: Combine return flights from Sharm with a two-day guided Cairo program and one clearly contracted overnight hotel, stating room, meal, and admission terms before confirmation.
Arabic short description: اجمع بين رحلات الطيران ذهابًا وعودة من شرم وبرنامج سياحي ليومين في القاهرة مع فندق واحد متعاقد عليه بوضوح، مع تحديد الغرفة والوجبات والتذاكر قبل التأكيد.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 2 days; source start/pickup pattern 01:00.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Shared 4-star package; private 4-star package.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Live flights, hotel identity/classification, room basis and occupancy, child bed, meals, check-in, single supplement, full itinerary, admissions, guide, transfers, baggage, cancellation split across flight/hotel, overnight customer support. Do not publish a named hotel until a live contract and room inventory exist.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 279/EUR 209 (shared), EUR 349/EUR 269 (private), a premium tier EUR 449/EUR 359; single-room supplement EUR 60.
- **Rollout note:** Later overnight.
### STG-MUL-002 — Two-Day Dahab & Canyon Camp

```text
Internal service ID: STG-MUL-002
Publication status: DRAFT

Category: Heritage and day trips (research-only category — no production fit yet)
English name: Two-Day Dahab & Canyon Camp
Arabic name: رحلة يومين إلى دهب والوادي مع إقامة مخيم
English short description: Travel from Sharm for a two-day Dahab and canyon program with a contracted camp stay, confirmed meals, and clearly defined land and snorkeling activities.
Arabic short description: انطلق من شرم في برنامج ليومين إلى دهب والوادي مع إقامة في مخيم متعاقد عليه ووجبات مؤكدة وأنشطة برية وبحرية محددة بوضوح.
English full description: TO CONFIRM
Arabic full description: TO CONFIRM

Duration: 2 days; source start 08:00.
Confirmation: STAFF_REVIEW (default until a real workflow proves INSTANT is safe)
Maximum people: TO CONFIRM
Available weekdays/dates: TO CONFIRM
Start times: TO CONFIRM

Price basis: PER_PERSON
Currency: EGP
Adult price: TO CONFIRM
Child age range and price: TO CONFIRM
Infant rule: TO CONFIRM
Add-ons and prices: Shared group only until a private overnight operation is independently proven.

Pickup included: TO CONFIRM
Pickup areas/hotels: TO CONFIRM
Meeting point: TO CONFIRM
Pickup timing wording: TO CONFIRM

Included: TO CONFIRM
Excluded: TO CONFIRM
What to bring: TO CONFIRM
Restrictions/accessibility: Camp identity/licence, room or tent type, bedding, sanitation, electricity, security, gender/family arrangements, meals/water, exact canyon and coastal sites, permits, guide, snorkeling/camel/ATV safety, luggage, accessibility, weather, overnight emergency support.
Cancellation/refund wording: TO CONFIRM

Operated by: TO CONFIRM (fulfilmentModel=PARTNER expected until Sharm To Go operates it directly)
Customer support owner: TO CONFIRM
Emergency/operational contact (internal only): TO CONFIRM

Photo asset IDs and rights evidence: NONE YET — blocks publish() until real, rights-cleared media exists
Arabic reviewer: TO CONFIRM
English reviewer: TO CONFIRM
Commercial approver: TO CONFIRM
Approval date and expiry: TO CONFIRM
```

- **Market reference (not a Sharm To Go price):** Reference only: source shows EUR 220 adult, EUR 180 child; snorkeling equipment a separate EUR 10 add-on.
- **Rollout note:** Later overnight.

## Recommended order (unchanged from the coverage audit)

1. `STG-TRN-001` — owner-requested first target, operationally simplest.
2. `STG-TRN-002`.
3. `STG-SEA-003` or `STG-NAT-001`, once vessel/park evidence exists.
4. `STG-FAM-001`, once a direct venue-ticket agreement exists.
5. Remaining sea cruises and desert services, after permits/safety/capacity
   are documented.
6. Private-tour city/desert/sea concepts (`STG-PT-*`).
7. Heritage/day-trip/long-distance/overnight concepts, only after the
   simpler local workflow is proven end to end — and only once a real
   category decision is made for `Heritage and day trips`, which does not
   exist in production yet.
