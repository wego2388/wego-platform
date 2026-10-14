#!/usr/bin/env bash
# Render the owner's editable HTML workbook as a selectable, linked PDF.
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source_html="$repository_root/clients/safari-tours-sharm/marketing/SAFARI_BROWSER_MARKETING_WORKBOOK_AR_2026-10-15.html"
output_pdf="${SAFARI_MARKETING_PDF_OUTPUT:-/home/wego/output/pdf/Safari_Tours_Sharm_Browser_Marketing_Workbook_AR_2026-10-15.pdf}"

command -v google-chrome >/dev/null || { echo "google-chrome is required" >&2; exit 1; }
command -v pdfinfo >/dev/null || { echo "pdfinfo is required" >&2; exit 1; }
command -v pdftotext >/dev/null || { echo "pdftotext is required" >&2; exit 1; }
mkdir -p "$(dirname "$output_pdf")"
google-chrome --headless --no-sandbox --disable-gpu --no-pdf-header-footer \
  "--print-to-pdf=$output_pdf" "file://$source_html"
pdfinfo "$output_pdf" | rg '^(Pages|File size):'
pdf_text="$(pdftotext "$output_pdf" -)"
rg -q 'خريطة التسويق' <<< "$pdf_text"
pdf_links="$(pdfinfo -url "$output_pdf")"
rg -q 'https://business.google.com/' <<< "$pdf_links"
echo "Safari marketing PDF verified: $output_pdf"
