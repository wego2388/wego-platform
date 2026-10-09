#!/usr/bin/env python3
"""Focused regressions for the operating-guide PDF renderer, without live data."""
import importlib.util
from io import BytesIO
from pathlib import Path
import sys
import unittest

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("safari_admin_guide", Path(__file__).with_name("build-admin-guide-pdf.py"))
guide = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = guide
spec.loader.exec_module(guide)


class GuideTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        guide.register_fonts()

    def test_bullet_continuation_is_not_truncated_or_a_new_bullet(self):
        self.assertEqual(guide.parse_markdown("- صور الرحلة\n  تحتاج مراجعة الحقوق\n- احفظ\n"), [guide.Block("bullet", "صور الرحلة تحتاج مراجعة الحقوق"), guide.Block("bullet", "احفظ")])

    def test_numbered_continuation_preserves_the_first_words(self):
        self.assertEqual(guide.parse_markdown("1. اختر الموعد\n   والسعة الحقيقية\n2. احفظ\n"), [guide.Block("step", "1. اختر الموعد والسعة الحقيقية"), guide.Block("step", "2. احفظ")])

    def test_metadata_and_headings_are_not_printed_as_paragraphs(self):
        self.assertEqual(guide.parse_markdown("# عنوان\nمرجع عملي | إصدار 9 أكتوبر 2026\n\n## الحجز\n### الموعد\nشرح"), [guide.Block("section", "الحجز"), guide.Block("heading", "الموعد"), guide.Block("paragraph", "شرح")])

    def test_percentages_and_urls_keep_supported_glyphs(self):
        for text in ["رد50%", "1 EUR = 50 EGP", "https://staff.safaritourssharm.com/login", "طلب ← حجز", "صور: JPEG/PNG"]:
            runs = guide.visual_runs(text)
            self.assertTrue(runs)
            if "%" in text:
                self.assertTrue(any("%" in run and face == "Latin" for run, face in runs))
            for run, face in runs:
                self.assertTrue(all(ord(c) in guide.pdfmetrics.getFont(face).face.charToGlyph for c in run))

    def test_every_source_glyph_has_a_font(self):
        for block in guide.parse_markdown(guide.SOURCE.read_text(encoding="utf-8")):
            guide.visual_runs(block.text)

    def test_wrap_respects_the_real_page_width(self):
        lines = guide.wrap("هذا شرح عربي بسيط للعمل أثناء مراجعة الحجوزات وتأكيد التفاصيل " * 8, 180)
        self.assertGreater(len(lines), 1)
        self.assertTrue(all(guide.width(line) <= 180 for line in lines))

    def test_renderer_refuses_a_word_that_would_clip(self):
        with self.assertRaises(ValueError):
            guide.wrap("https://" + "x" * 300, 100)

    def test_complete_real_guide_layout_and_contents_are_stable(self):
        blocks = guide.parse_markdown(guide.SOURCE.read_text(encoding="utf-8"))
        first = guide.Guide(BytesIO(), blocks, "إصدار 9 أكتوبر 2026", [(b.text, 0) for b in blocks if b.kind == "section"])
        first.build()
        second = guide.Guide(BytesIO(), blocks, "إصدار 9 أكتوبر 2026", first.sections, first.page)
        second.build()
        self.assertEqual(first.sections, second.sections)
        self.assertEqual(first.page, second.page)
        self.assertEqual(len(second.sections), 12)
        self.assertGreaterEqual(min(second.bottoms), 55)


if __name__ == "__main__":
    unittest.main()
