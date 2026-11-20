"""Draw the Apk-Runner app icon (1024x1024 PNG) with only the standard library.

Usage: python scripts/make_icon.py out.png
"""
import struct
import sys
import zlib

N = 1024


def png(path, rows):
    raw = b"".join(b"\0" + r for r in rows)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", N, N, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def cover(d):
    """Antialiased coverage from a signed distance (negative = inside)."""
    return max(0.0, min(1.0, 0.5 - d))


def main(out):
    m, r = 100, 185          # macOS icon grid: 824px rounded square, corner radius ~185
    lo, hi = m, N - m
    # play triangle, centred optically
    ax, ay, bx, by, cx, cy = 400, 330, 400, 694, 715, 512
    edges = [(ax, ay, bx, by), (bx, by, cx, cy), (cx, cy, ax, ay)]
    norms = []
    for x1, y1, x2, y2 in edges:
        nx, ny = y2 - y1, -(x2 - x1)
        ln = (nx * nx + ny * ny) ** 0.5
        norms.append((nx / ln, ny / ln, x1, y1))
    rows = []
    for y in range(N):
        row = bytearray(N * 4)
        t = (y - lo) / (hi - lo)
        top, bot = (52, 211, 153), (37, 99, 235)  # green -> blue
        base = [int(top[i] + (bot[i] - top[i]) * t) for i in range(3)]
        for x in range(N):
            qx = max(lo + r - x, 0, x - (hi - r))
            qy = max(lo + r - y, 0, y - (hi - r))
            d = (qx * qx + qy * qy) ** 0.5 - r if (qx or qy) else max(lo - x, x - hi, lo - y, y - hi)
            a = cover(d)
            if a == 0:
                continue
            td = max(-((x - px) * nx + (y - py) * ny) for nx, ny, px, py in norms)
            w = cover(td)
            px_ = [int(c + (255 - c) * w) for c in base]
            row[x * 4:x * 4 + 4] = bytes(px_ + [int(255 * a)])
        rows.append(bytes(row))
    png(out, rows)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "icon.png")
