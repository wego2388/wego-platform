"""Offline safety checks: provenance, path validation and metadata stripping."""
import io
import unittest

from PIL import Image

from fetch_licensed_media import checked_url, validate_entry
from prepare_media_uploads import encoded_for_upload


class MediaPipelineTests(unittest.TestCase):
    def test_rejects_untrusted_download_targets(self):
        for url in (
            "http://images.pexels.com/photos/1.jpg",
            "https://images.pexels.com.evil.example/photos/1.jpg",
            "https://user:pass@images.pexels.com/photos/1.jpg",
            "https://images.pexels.com:8443/photos/1.jpg",
            "https://127.0.0.1/photo.jpg",
        ):
            with self.subTest(url=url), self.assertRaises(ValueError):
                checked_url(url)

    def test_requires_known_license_and_safe_tour_path(self):
        entry = {
            "id": "example-photo", "sourcePage": "https://www.pexels.com/photo/example-1/",
            "downloadUrl": "https://images.pexels.com/photos/1/a.jpg",
            "author": "Photographer", "license": "Pexels",
            "licenseUrl": "https://www.pexels.com/license/",
            "tour": "sea/diving-course", "usage": "Illustrative only",
        }
        validate_entry(entry)
        for changes in (
            {"licenseUrl": "https://evil.example/license"},
            {"tour": "sea/../../secrets"},
            {"id": "../example"},
        ):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                validate_entry({**entry, **changes})

    def test_normalizes_pixels_without_exif_or_upscaling(self):
        image = Image.new("RGB", (50, 20), "#146a91")
        exif = Image.Exif()
        exif[270] = "source metadata must not survive"
        source = io.BytesIO()
        image.save(source, format="JPEG", exif=exif)
        prepared, size = encoded_for_upload(source.getvalue())
        self.assertEqual(size, (50, 20))
        with Image.open(io.BytesIO(prepared)) as result:
            self.assertEqual(result.format, "JPEG")
            self.assertFalse(result.getexif())


if __name__ == "__main__":
    unittest.main()
