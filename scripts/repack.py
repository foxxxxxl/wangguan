"""Rebuild FOXO APK: copy original entries, swap assets/index.html, keep methods,
4-byte align every STORED entry (zipalign-compatible padding field 0xd935)."""
import struct, sys, zipfile, zlib

import os
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.environ.get("FOXO_BASE_APK", "")
DST = os.path.join(ROOT, "build", "app-repacked.apk")
NEW_INDEX = os.path.join(ROOT, "build", "assets__index.html")

if not SRC:
    raise SystemExit("Set FOXO_BASE_APK=/path/to/base.apk before running.")
if not os.path.isfile(SRC):
    raise SystemExit("Base APK not found: " + SRC)

with open(NEW_INDEX, "rb") as fh:
    new_index = fh.read()

MERGED_DEX = os.path.join(ROOT, "build", "classes_merged.dex")
NEW_MANIFEST = os.path.join(ROOT, "build", "AndroidManifest.xml")
with open(MERGED_DEX, "rb") as fh:
    merged_dex = fh.read()

ZF_TIME, ZF_DATE = 0, 0  # let apksigner/Android not care; use fixed dos time
DOS_TIME = (12 << 11) | (0 << 5) | 0   # 12:00:00
DOS_DATE = (2026 - 1980) << 9 | (11 << 5) | 5

def extra_field(pad):
    if pad == 0:
        return b""
    # zipalign padding extra field id 0xd935
    return struct.pack("<HH", 0xD935, pad) + b"\x00" * pad

out = bytearray()
central = bytearray()
offset = 0

zin = zipfile.ZipFile(SRC)
for info in zin.infolist():
    if info.filename == "assets/index.html":
        data = new_index
    elif info.filename == "classes.dex":
        data = merged_dex
    elif info.filename == "AndroidManifest.xml":
        data = open(NEW_MANIFEST, "rb").read()
    else:
        data = zin.read(info.filename)
    method = info.compress_type
    crc = zlib.crc32(data) & 0xFFFFFFFF
    if method == zipfile.ZIP_STORED:
        cdata = data
    else:
        co = zlib.compressobj(9, zlib.DEFLATED, -15)
        cdata = co.compress(data) + co.flush()

    name = info.filename.encode("utf-8")
    base_local = 30 + len(name)
    # local header: sig(4) ver(2) flag(2) method(2) time(2) date(2) crc(4) csize(4) usize(4) nlen(2) xlen(2)
    extra_len = len(info.extra)
    if method == zipfile.ZIP_STORED:
        cur = offset + base_local + extra_len
        pad = (4 - cur % 4) % 4
    else:
        pad = 0
    extra = info.extra + extra_field(pad)

    local = struct.pack(
        "<IHHHHHIIIHH", 0x04034B50, 20, 0, method, DOS_TIME, DOS_DATE,
        crc, len(cdata), len(data), len(name), len(extra),
    )
    header_at = offset
    out += local + name + extra + cdata
    offset = header_at + len(local) + len(name) + len(extra) + len(cdata)

    central += struct.pack(
        "<IHHHHHHIIIHHHHHII", 0x02014B50, 20, 20, 0, method, DOS_TIME, DOS_DATE,
        crc, len(cdata), len(data), len(name), len(extra), 0, 0, 0, 0, header_at,
    ) + name + extra

central_size = len(central)
central_at = offset
eocd = struct.pack(
    "<IHHHHIIH", 0x06054B50, 0, 0, len(zin.infolist()), len(zin.infolist()),
    central_size, central_at, 0,
)
out += central + eocd

with open(DST, "wb") as fh:
    fh.write(out)
print("wrote", DST, len(out), "bytes")
