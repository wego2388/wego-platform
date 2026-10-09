#!/usr/bin/env python3
"""Build the Arabic operator guide; preserve wrapped lists and every glyph.

Authoritative text remains in ADMIN_OPERATING_GUIDE_AR.md. This is a document
renderer, not a production client, and never reads credentials or bookings.
Requires reportlab, arabic-reshaper and python-bidi plus Noto/DejaVu system fonts.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from functools import lru_cache
from io import BytesIO
from pathlib import Path
import re

import arabic_reshaper
from bidi.algorithm import get_display
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "clients/safari-tours-sharm/operations/ADMIN_OPERATING_GUIDE_AR.md"
BLUE = colors.HexColor("#103C5D")
ORANGE = colors.HexColor("#EC831E")
INK = colors.HexColor("#253F50")
MUTED = colors.HexColor("#657987")
PALE = colors.HexColor("#EAF3F7")
W, H = A4
LEFT, RIGHT = 48, W - 48
WIDTH = RIGHT - LEFT
SIZE, LEADING, BOTTOM = 12.7, 19, 70
ARABIC_DIGITS = str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩")


@dataclass(frozen=True)
class Block:
    kind: str
    text: str


def parse_markdown(text: str) -> list[Block]:
    """Join continuation lines; never slice two characters from a wrapped line."""
    result: list[Block] = []
    pending: list[str] = []
    kind = "paragraph"

    def flush() -> None:
        if pending:
            result.append(Block(kind, " ".join(pending)))
            pending.clear()

    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            flush()
        elif line.startswith("# ") or line.startswith("مرجع عملي"):
            flush()
        elif line.startswith("## "):
            flush()
            result.append(Block("section", line[3:]))
        elif line.startswith("### "):
            flush()
            result.append(Block("heading", line[4:]))
        elif line.startswith("- "):
            flush()
            kind = "bullet"
            pending.append(line[2:])
        elif re.match(r"^\d+\. ", line):
            flush()
            kind = "step"
            pending.append(line)
        else:
            if not pending:
                kind = "paragraph"
            pending.append(line)
    flush()
    return result


def register_fonts() -> None:
    fonts = {
        "Arabic": "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf",
        "ArabicBold": "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Bold.ttf",
        "Latin": "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "LatinBold": "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
        "Symbols": "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    }
    for name, path in fonts.items():
        pdfmetrics.registerFont(TTFont(name, path))


def visual_runs(text: str, bold: bool = False) -> list[tuple[str, str]]:
    shaped = get_display(arabic_reshaper.reshape(text), base_dir="R")
    arabic = "ArabicBold" if bold else "Arabic"
    latin = "LatinBold" if bold else "Latin"
    result: list[tuple[str, str]] = []
    for char in shaped:
        # Arabic fonts do not cover every ASCII symbol: prices/percentages and
        # URLs must not lose glyphs just because their run begins with a digit.
        candidates = (latin, arabic, "Symbols") if ord(char) < 128 else (arabic, latin, "Symbols")
        face = next((f for f in candidates if ord(char) in pdfmetrics.getFont(f).face.charToGlyph), None)
        if face is None:
            raise ValueError(f"Missing printable glyph U+{ord(char):04X}")
        if result and result[-1][1] == face:
            result[-1] = (result[-1][0] + char, face)
        else:
            result.append((char, face))
    return result


@lru_cache(maxsize=16384)
def width(text: str, size: float = SIZE, bold: bool = False) -> float:
    return sum(pdfmetrics.stringWidth(run, face, size) for run, face in visual_runs(text, bold))


def wrap(text: str, available: float, size: float = SIZE, bold: bool = False) -> list[str]:
    text = re.sub(r"\*\*|`", "", text)
    result: list[str] = []
    current = ""
    for word in text.split():
        if width(word, size, bold) > available:
            raise ValueError(f"Unbreakable text exceeds the page: {word!r}")
        candidate = f"{current} {word}".strip()
        if current and width(candidate, size, bold) > available:
            result.append(current)
            current = word
        else:
            current = candidate
    if current:
        result.append(current)
    return result


class Guide:
    def __init__(self, target, blocks: list[Block], revision: str, toc: list[tuple[str, int]], total: int = 0):
        self.c = canvas.Canvas(target, pagesize=A4, pageCompression=1)
        self.c.setTitle("دليل أدمن وتشغيل سفاري تورز شرم")
        self.c.setAuthor("Safari Tours Sharm")
        self.c.setSubject(f"الحجز والتشغيل والصور والتحصيل والمستندات - {revision}")
        self.blocks, self.revision, self.toc, self.total = blocks, revision, toc, total
        self.page = 0
        self.y = 0.0
        self.title = ""
        self.sections: list[tuple[str, int]] = []
        self.bottoms: list[float] = []

    def rtl(self, text: str, y: float, size: float = SIZE, bold: bool = False, right: float = RIGHT) -> None:
        x = right - width(text, size, bold)
        for run, face in visual_runs(text, bold):
            self.c.setFont(face, size)
            self.c.drawString(x, y, run)
            x += pdfmetrics.stringWidth(run, face, size)

    def footer(self) -> None:
        self.c.setStrokeColor(PALE)
        self.c.line(LEFT, 46, RIGHT, 46)
        self.c.setFillColor(MUTED)
        self.rtl(f"{self.revision} - مرجع تشغيل داخلي", 29, 9)
        self.c.setFont("Latin", 9)
        self.c.drawString(LEFT, 29, f"{self.page:02d}" + (f" / {self.total:02d}" if self.total else ""))

    def new_page(self, title: str, continuation: bool = False) -> None:
        if self.page:
            self.footer()
            self.c.showPage()
        self.page += 1
        self.bottoms.append(H)
        self.c.setFillColor(BLUE)
        self.c.rect(0, H - 65, W, 65, fill=1, stroke=0)
        self.c.setFillColor(colors.white)
        self.c.setFont("LatinBold", 10)
        self.c.drawString(LEFT, H - 31, "SAFARI TOURS SHARM")
        self.rtl("دليل الأدمن والتشغيل", H - 30, 12, True)
        self.c.setFillColor(ORANGE)
        self.c.rect(LEFT, H - 67, WIDTH, 3, fill=1, stroke=0)
        self.y = H - 107
        self.c.setFillColor(BLUE)
        for line in wrap(title + (" - متابعة" if continuation else ""), WIDTH, 18, True):
            self.rtl(line, self.y, 18, True)
            self.y -= 26
        self.y -= 10

    def paragraph(self, text: str, bold: bool = False, size: float = SIZE, gap: float = 7, bullet: bool = False) -> None:
        available = WIDTH - (14 if bullet else 0)
        lines = wrap(text, available, size, bold)
        if self.y - len(lines) * LEADING < BOTTOM:
            self.new_page(self.title, continuation=True)
        if self.y - len(lines) * LEADING < BOTTOM:
            raise ValueError("A paragraph is longer than a page; split it in the source")
        self.c.setFillColor(INK)
        if bullet:
            self.c.setFillColor(ORANGE)
            self.c.circle(RIGHT - 2, self.y + 3, 2, fill=1, stroke=0)
            self.c.setFillColor(INK)
        for line in lines:
            self.rtl(line, self.y, size, bold, RIGHT - (14 if bullet else 0))
            for address in re.findall(r"https://[^\s]+", line):
                self.c.linkURL(address, (LEFT, self.y - 3, RIGHT, self.y + size), relative=0)
            self.y -= LEADING
        self.y -= gap
        self.bottoms[-1] = min(self.bottoms[-1], self.y)

    def cover(self) -> None:
        self.new_page("دليل أدمن سفاري تورز شرم")
        self.c.bookmarkPage("cover")
        self.c.addOutlineEntry("دليل أدمن سفاري تورز شرم", "cover")
        logo = ROOT / "web/apps/safari-tours-sharm-site/public/brand/logo.webp"
        self.c.drawImage(ImageReader(str(logo)), W / 2 - 65, self.y - 145, 130, 130, preserveAspectRatio=True, anchor="c", mask="auto")
        self.y -= 180
        self.paragraph("من أول طلب إلى نهاية يوم الشغل", True, 21, gap=16)
        self.paragraph("دليل عملي بالعربية لاستخدام الموقع ولوحة التشغيل: الحجز، الصور، تجهيز الرحلات، المال والورقيات.", size=16, gap=20)
        self.paragraph("ابدأ بصفحة «أول يوم شغل»، ثم استخدم الفهرس للوصول إلى المهمة التي تريدها. الروابط والفهرس قابلة للضغط.", gap=18)
        self.c.setFillColor(PALE)
        self.c.roundRect(LEFT, self.y - 145, WIDTH, 145, 12, fill=1, stroke=0)
        self.y -= 28
        self.paragraph("التشغيل الحالي: طلب واتساب ثم تأكيد المكتب وتسجيل الحجز. الدفع الإلكتروني غير مفعّل بعد.", True, gap=14)
        self.paragraph("لا تسجّل تحصيلًا أو ردًا أو دفعة مورد إلا بعد حدوثها فعلًا. الطلب ليس حجزًا، والتأكيد ليس إيصال دفع.")

    def contents(self) -> None:
        self.new_page("الفهرس: اختَر المهمة التي تحتاجها")
        self.c.bookmarkPage("contents")
        self.c.addOutlineEntry("الفهرس", "contents")
        self.paragraph("اضغط على اسم القسم للانتقال إليه. الدليل مرجع أثناء العمل؛ ليس مطلوبًا قراءته كله قبل أول مهمة.", gap=18)
        for index, (title, number) in enumerate(self.toc):
            label = re.sub(r"^[٠-٩\d]+\.\s*", "", title)
            top = self.y + 14
            self.paragraph(label, size=12, bold=True, gap=12)
            self.c.setFillColor(MUTED)
            self.c.setFont("Latin", 10)
            self.c.drawString(LEFT, top - 14, f"{number:02d}")
            self.c.linkRect("", f"section-{index}", (LEFT, self.y + 4, RIGHT, top), relative=0, thickness=0)
        self.paragraph("النسخة القديمة الصادرة صباح 8 أكتوبر لا تشرح آخر تحسينات المواعيد والرحلات والصور؛ استخدم هذا الإصدار بدلًا منها.", size=11)

    def build(self) -> None:
        self.cover()
        self.contents()
        for block in self.blocks:
            if block.kind == "section":
                self.title = block.text
                self.new_page(self.title)
                index = len(self.sections)
                self.sections.append((self.title, self.page))
                self.c.bookmarkPage(f"section-{index}")
                self.c.addOutlineEntry(self.title, f"section-{index}")
            elif block.kind == "heading":
                if self.y < BOTTOM + 95:
                    self.new_page(self.title, continuation=True)
                self.y -= 5
                self.paragraph(block.text, True, 14, gap=6)
            else:
                self.paragraph(block.text, bullet=block.kind == "bullet", gap=4 if block.kind in {"bullet", "step"} else 7)
        self.footer()
        self.c.save()
        if min(self.bottoms) < 55:
            raise ValueError("Content overlaps the footer")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    text = args.source.read_text(encoding="utf-8")
    revision = re.search(r"إصدار [^\n]+", text)
    if not revision:
        raise ValueError("Guide source needs an explicit revision date")
    register_fonts()
    blocks = parse_markdown(text)
    titles = [(b.text, 0) for b in blocks if b.kind == "section"]
    draft = Guide(BytesIO(), blocks, revision.group(), titles)
    draft.build()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    final = Guide(str(args.output), blocks, revision.group(), draft.sections, draft.page)
    final.build()
    if final.sections != draft.sections or final.page != draft.page:
        raise ValueError("Contents page changed the final pagination")
    print(f"PDF: {args.output}\nPages: {final.page}\nSections: {len(final.sections)}\nLayout: PASS")
    for title, page in final.sections:
        print(f"{page:02d}: {title}")


if __name__ == "__main__":
    main()
