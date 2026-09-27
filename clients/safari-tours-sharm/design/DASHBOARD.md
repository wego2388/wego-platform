# Staff dashboard — Safari Tours Sharm

The dashboard is an internal operations tool for Safari Tours Sharm staff.
Access is role-gated server-side (Admin / Staff) — UI visibility is not
authorization. Staff role sees bookings and tours read-only; Admin has full
write access including finance, settings, and user management.

---

## Navigation structure

```
Sidebar:
├── Overview
├── Tours
│   ├── All Tours
│   └── Add New Tour
├── Bookings
│   ├── All Bookings
│   ├── Today
│   └── Upcoming
├── Finance
│   ├── Revenue
│   └── Refunds
├── Customers
├── Reviews
├── Notifications
└── Settings
    ├── Company Info
    ├── Payment (Paymob)
    ├── WhatsApp
    ├── Cancellation Policy
    └── Users & Permissions
```

On mobile: sidebar collapses to bottom tab bar for top-level sections.

---

## Overview

KPI cards (4):
- Today's Bookings: count + EUR revenue
- This Month: count + EUR revenue + % change vs previous month
- Pending Payments: count + EUR amount outstanding
- Average Rating: X.X ⭐ (from published reviews)

Charts:
- Revenue last 30 days (line, daily, ApexCharts)
- Bookings by category (donut, ApexCharts)
- Top 5 tours by revenue (horizontal bar, ApexCharts)

Today's schedule:
- List of today's confirmed bookings grouped by time slot
- Each row: tour name | time | pax count | status badge

Recent bookings:
- Last 10 bookings with: ref | customer name | tour | status badge | amount

---

## Tours management

List view:
- Columns: # | thumbnail | name | category | adult price | capacity | status | actions
- Filter: category dropdown, status toggle (active/inactive)
- Search: by name (client-side)
- [+ New Tour] button (Admin only)
- Inline toggle active/inactive (Admin only)

Tour editor (create / edit):
- Tabs: Basic Info | Pricing | Content | Media | SEO
- Content tab has language sub-tabs: EN | RU | AR | IT
- AR tab renders with `dir="rtl"` inside the editor only
- [Save Draft] saves without publishing
- [Publish] makes tour visible on public site
- Unsaved changes prompt on navigation away

---

## Bookings management

Filters:
- Date range picker
- Status: All | New | Confirmed | Pending Payment | Cancelled | Completed | Expired
- Tour dropdown
- Search: by customer name, booking reference, or phone

Table columns: ref | customer | tour | date + time | pax | total EUR | payment status | booking status | actions

Row click → opens booking detail drawer (right side panel, 480px wide).

Booking detail drawer contains:
- Full booking info (all fields from domain model)
- Customer: name, nationality flag, phone, hotel + room, special requests
- Payment: method, Paymob transaction ref, status, paid/refunded amounts
- Action buttons (role and state dependent):
  - [Confirm] — available when status is NEW and payment is PAID
  - [Mark Completed] — available after tour date has passed
  - [Cancel Booking] — opens reason picker (weather / customer request / other)
  - [Issue Refund] — available when payment is PAID and booking is CANCELLED
  - [Send WhatsApp Reminder] — manual trigger, 24h-before template
  - [Print Voucher] — generates PDF voucher

Bulk actions (on selected rows):
- Export to Excel
- Send WhatsApp reminders

---

## Finance

Revenue overview (Admin only):
- This month vs last month: EUR totals + % change
- This year total
- Average booking value

Revenue by tour — table:
- Tour name | confirmed bookings | EUR revenue | % of total revenue

Monthly bar chart — 12 months (ApexCharts)

Pending payments:
- List of NEW bookings with no completed payment
- [Send Payment Reminder on WhatsApp] per row

Refunds:
- All REFUNDED payments with reason and amounts
- Monthly total refunded

Export:
- [Export to Excel] with date range picker
- [Export to PDF] monthly summary

---

## Settings

All settings sections are Admin only.

Paymob:
- API Key (displayed masked, reveal on explicit click)
- Integration IDs for: Card, Vodafone Cash, Fawry
- Test mode toggle (disables live transactions)
- Credentials stored as environment variables — never in database or logs

WhatsApp templates (4 language tabs each):
- Booking confirmation template
- Cancellation confirmation template
- Refund notification template
- 24h reminder template
- Variables: {{customerName}}, {{tourName}}, {{date}}, {{time}}, {{ref}}, {{total}}

Users & Permissions:
- User list: name | email | role | last login
- [Invite User] by email — sends invite link
- Role assignment: Admin or Staff
- [Revoke Access] with confirmation dialog
