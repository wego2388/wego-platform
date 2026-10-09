# Rebuilding the Arabic operating guide

Authoritative copy: `ADMIN_OPERATING_GUIDE_AR.md`. The owner-facing PDF is an
export, not a second independently edited source. Update the explicit revision
date and verify instructions against the actual dashboard before regenerating.

From the repository root:

```bash
python3 scripts/safari-ops/test-admin-guide-pdf.py
python3 scripts/safari-ops/build-admin-guide-pdf.py \
  --output /home/wego/output/pdf/Safari_Tours_Sharm_Admin_Guide_AR_2026-10-09.pdf
```

Runtime dependencies are `reportlab`, `arabic-reshaper`, `python-bidi`, and the
Noto Naskh Arabic / Noto Sans / DejaVu Sans fonts at the paths listed in the
renderer. Missing glyphs or an unbreakable over-width string fail the export;
do not silently substitute a font or shrink the guide until it is unreadable.
The renderer does not access bookings, environment files or credentials.

Visual verification is required after every copy/layout change:

```bash
pdfinfo /home/wego/output/pdf/Safari_Tours_Sharm_Admin_Guide_AR_2026-10-09.pdf
pdffonts /home/wego/output/pdf/Safari_Tours_Sharm_Admin_Guide_AR_2026-10-09.pdf
pdftoppm -scale-to 1200 -png \
  /home/wego/output/pdf/Safari_Tours_Sharm_Admin_Guide_AR_2026-10-09.pdf \
  /home/wego/output/pdf/guide-page
```

Inspect every page: joined Arabic, list continuations, percentages, URLs,
toc page numbers/links, no clipped text/footer overlap, and no near-empty
orphan continuation. Verify printed cancellation percentages against the
approved source. Preserve the previous delivered PDF as an archive.

The 9 October export contains 16 A4 pages and 12 sections. The initial 8 October
11-page export is outdated for the latest office-booking/media/navigation UI.
Generated PDFs/PNGs and synthetic print evidence stay outside tracked source.
