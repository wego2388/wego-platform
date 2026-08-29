# Sharm Divers Club — Client Configuration Reference
# ══════════════════════════════════════════════════════════════════
# هذا الملف مرجع للـ Developer / Kiro
# المصدر الرسمي للبيانات التشغيلية: wego-platform API + PostgreSQL
# المصدر الرسمي للتسويق: /home/wego/projects/clients/sharm-divers-club/
# ══════════════════════════════════════════════════════════════════

## Client Identity

```
Client ID:     sharm-divers-club
Display Name:  Sharm Divers Club
Product:       wego-divers (v0.1.0)
Platform:      wego-platform (v0.1.0-SNAPSHOT)
Status:        ACTIVE — First Client
```

## Configuration (client.manifest.json)

```json
{
  "clientId": "sharm-divers-club",
  "organization": {
    "timezone": "Africa/Cairo",
    "defaultLocale": "en",
    "supportedLocales": ["ar", "en", "he"],
    "currency": "EUR"
  },
  "deploymentIsolation": "ISOLATED_INSTANCE"
}
```

## Locales in Use

| Locale | اللغة | الحالة |
|---|---|---|
| en | English | ✅ Default |
| ar | العربية | ✅ Active |
| he | עברית | ✅ Active — Israeli market |

## Business Context (للـ Domain Model)

```
النشاط:        PADI 5-Star Dive Center
الموقع:        Hadbet Um Sid, Sharm El Sheikh, Egypt
Timezone:      Africa/Cairo (UTC+2 / UTC+3 DST)
Currency:      EUR (primary) — Paymob يدعمها
```

## Product Capabilities in Use (WEGO-002+)

```
divers.offerings:
  - Shore Diving (SD*)
  - Boat Diving (BD*)
  - Multi-Day Packages (MP*)
  - Handpicked Packages (HP*)
  - World-Class Sites (WC*)
  - PADI Courses (PC*)
  - Water Sports (WS*)
  - Desert/Safari (DS*)
  - Excursions (EX*)
  - Transfers (TR*)
  - Snorkeling (SN*)

divers.bookings:
  - Confirmation: IMMEDIATE (no pending state)
  - Capacity: flat counter per offering
  - Payment: price + status only (Paymob TBD)
```

## Staff API Endpoints (WEGO-002 — Complete)

> كل المسارات التالية داخلية للموظفين وتحتاج Authentication وPermission مناسبة.
> لا يوجد منها Public lead endpoint، ولا يجوز لـMeta أوn8n استخدامها لتحويل Lead
> إلىBooking مباشرة.

```
POST   /api/v1/divers/offerings      ← إنشاء offering (staff)
GET    /api/v1/divers/offerings      ← قائمة offerings
GET    /api/v1/divers/offerings/{id} ← تفاصيل offering
POST   /api/v1/divers/offerings/{id}/close
POST   /api/v1/divers/bookings       ← إنشاء booking
GET    /api/v1/divers/bookings       ← قائمة bookings
GET    /api/v1/divers/bookings/{id}  ← تفاصيل booking
POST   /api/v1/divers/bookings/{id}/cancel
PATCH  /api/v1/divers/bookings/{id}/mark-paid
PATCH  /api/v1/divers/bookings/{id}/refund
```

## Future Channel Integration Guardrails — Not Implemented or Authorized

```
Lead Capture:
  Meta / WhatsApp / Messenger / Website
    → authenticated, minimized channel adapter
    → Inquiry intake use case (future WEGO-005)
    → staff qualification and follow-up
    → select a real dated Offering + check capacity + explicit customer intent
    → staff-only Booking command

Reminders:
  Future WEGO-003/004 work only, after reliable outbox delivery, consent,
  channel policy, idempotency and audit are implemented.

Review Requests:
  Future communications capability only; no review gating and no direct
  database access by n8n or a provider.

Weekly Report:
  Future read projection/export; no unrestricted SQL, credentials or raw PII
  are sent to n8n, Telegram or an AI model.
```

The Inquiry capability and endpoint do not exist yet, so this document deliberately
does not invent a URL for them. Until WEGO-003 through WEGO-005 are explicitly
authorized and completed, channel leads remain a manual operational workflow.

## Permissions Required (WEGO-002)

```
booking:create   — staff creates booking
booking:read     — staff reads bookings
booking:cancel   — staff cancels booking
booking:update-payment — staff updates payment status
offering:read    — public/staff reads offerings
```

## Marketing Reference

```
التسويق والمحتوى الجاهز:
  /home/wego/projects/clients/sharm-divers-club/

لا تستورد من هذا المجلد مباشرة في الكود.
أي بيانات تحتاجها في الـ Platform تمر عبر:
1. تصميم Domain Model مناسب
2. Flyway migration
3. Data seeding منفصل
4. مراجعة وموافقة
```

## Work Packet Status

```
WEGO-002: COMPLETE — staff offerings and bookings foundation
WEGO-003: NOT AUTHORIZED — reliable integration delivery/replay
WEGO-004: NOT AUTHORIZED — communications, consent and first channel
WEGO-005: NOT AUTHORIZED — inquiry, lead intake and follow-up
WEGO-010-A: ACTIVE — Sharm To Go composition; unrelated to Sharm Divers lead automation
```
