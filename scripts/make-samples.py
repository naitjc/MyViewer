#!/usr/bin/env python3
"""Generate original, tiny local fixtures for manual reader checks (no external media)."""
from pathlib import Path
import struct
import zlib
import zipfile

ROOT = Path(__file__).resolve().parents[1] / "sample-books"
ROOT.mkdir(exist_ok=True)

def png(width, height, page):
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            border = x < 16 or x >= width - 16 or y < 16 or y >= height - 16
            panel = (y // max(1, height // 6)) % 2
            if border:
                rows.extend((36, 55, 45))
            else:
                rows.extend((160 + page * 5 + panel * 25, 185 + panel * 15, 165 + panel * 20))
    def chunk(name, data):
        return struct.pack(">I", len(data)) + name + data + struct.pack(">I", zlib.crc32(name + data) & 0xffffffff)
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows, 8)) + chunk(b"IEND", b"")

novel = "\n\n".join(
    "第%d章 雨后的车站\n" % chapter + "\n".join(
        "雨停时，站台只剩下最后一盏灯。林舟把信折好，沿着石阶慢慢走下去。远处传来列车的声音，他想起了很多年前的那个傍晚。"
        for _ in range(20)
    ) for chapter in range(1, 21)
)
(ROOT / "雨后的车站-UTF8.txt").write_text(novel, encoding="utf-8")
(ROOT / "雨后的车站-GB18030.txt").write_bytes(novel.encode("gb18030"))
with zipfile.ZipFile(ROOT / "山海来信-分页.zip", "w", zipfile.ZIP_DEFLATED) as archive:
    for page in (1, 10, 2, 3):
        archive.writestr(f"{page}.png", png(600, 900, page % 8))
    archive.writestr("ComicInfo.xml", "<ComicInfo><Title>山海来信</Title><Manga>YesAndRightToLeft</Manga></ComicInfo>")
with zipfile.ZipFile(ROOT / "山海来信-长条.zip", "w", zipfile.ZIP_DEFLATED) as archive:
    for page in (1, 2):
        archive.writestr(f"{page:02}.png", png(360, 9000, page))
print("Sample books:", ROOT)

