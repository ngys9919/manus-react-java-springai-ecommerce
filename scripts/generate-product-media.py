#!/usr/bin/env python3
"""Generate one branded SVG tile per product, plus a placeholder.

Reads the seeded catalogue straight from PostgreSQL so the artwork always matches
what the API serves. Deterministic: the same catalogue always produces the same
files, so the assets can be committed and regenerated safely.

Usage:
    python3 scripts/generate-product-media.py [output_dir]
"""
from __future__ import annotations

import json
import os
import subprocess
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "frontend/public/media/products"
W, H = 800, 600

QUERY = """
select json_agg(json_build_object(
    'sku', p.sku,
    'name', p.name,
    'brand', p.brand,
    'category', c.slug,
    'accent', coalesce(p.accent_color, '#4f46e5'),
    'rating', p.rating
) order by p.sku)
from product p join category c on c.id = p.category_id;
"""

# Device silhouettes, drawn inside a 0..200 box that is then scaled and centred.
GLYPHS = {
    "laptops": """
      <rect x="26" y="42" width="148" height="92" rx="9"/>
      <rect x="34" y="50" width="132" height="76" rx="5" fill="rgba(255,255,255,0.20)" stroke="none"/>
      <path d="M12 142h176l-12 20H24z"/>
      <circle cx="100" cy="152" r="3.4" fill="rgba(255,255,255,0.75)" stroke="none"/>
    """,
    "smartphones": """
      <rect x="60" y="16" width="80" height="168" rx="17"/>
      <rect x="69" y="32" width="62" height="128" rx="8" fill="rgba(255,255,255,0.20)" stroke="none"/>
      <path d="M88 24h24"/>
      <circle cx="100" cy="172" r="5" fill="rgba(255,255,255,0.7)" stroke="none"/>
      <rect x="88" y="86" width="24" height="34" rx="6" fill="none" stroke="rgba(255,255,255,0.55)" stroke-width="4"/>
    """,
    "headphones": """
      <path d="M42 118V96a58 58 0 0 1 116 0v22"/>
      <rect x="26" y="112" width="34" height="58" rx="14"/>
      <rect x="140" y="112" width="34" height="58" rx="14"/>
      <path d="M174 168v6a20 20 0 0 1-20 20h-26" stroke-width="5"/>
    """,
    "smartwatches": """
      <rect x="66" y="52" width="68" height="96" rx="20"/>
      <rect x="76" y="64" width="48" height="72" rx="12" fill="rgba(255,255,255,0.22)" stroke="none"/>
      <path d="M74 52 82 12h36l8 40M74 148l8 40h36l8-40"/>
      <circle cx="100" cy="100" r="17" fill="none" stroke="rgba(255,255,255,0.55)" stroke-width="5"/>
    """,
    "cameras": """
      <rect x="22" y="62" width="156" height="104" rx="18"/>
      <path d="M74 62l12-22h30l12 22"/>
      <circle cx="100" cy="114" r="36" fill="none" stroke="rgba(255,255,255,0.6)" stroke-width="6"/>
      <circle cx="100" cy="114" r="19" fill="rgba(255,255,255,0.22)" stroke="none"/>
      <circle cx="150" cy="82" r="6" fill="rgba(255,255,255,0.7)" stroke="none"/>
    """,
    "smart-home": """
      <rect x="56" y="42" width="88" height="140" rx="26"/>
      <rect x="68" y="58" width="64" height="108" rx="18" fill="rgba(255,255,255,0.18)" stroke="none"/>
      <circle cx="100" cy="98" r="21" fill="none" stroke="rgba(255,255,255,0.6)" stroke-width="5"/>
      <circle cx="100" cy="98" r="8" fill="rgba(255,255,255,0.7)" stroke="none"/>
      <path d="M84 142h32" stroke-width="5"/>
    """,
}

PLACEHOLDER_GLYPH = """
  <rect x="30" y="52" width="140" height="96" rx="14"/>
  <path d="M70 96h60M100 76v40"/>
"""


def fetch_products() -> list[dict]:
    env = dict(os.environ, PGPASSWORD=os.environ.get("PGPASSWORD", "shopwise"))
    result = subprocess.run(
        ["psql", "-h", "127.0.0.1", "-U", "shopwise", "-d", "shopwise", "-tAc", QUERY],
        capture_output=True, text=True, env=env, check=True,
    )
    return json.loads(result.stdout.strip() or "[]")


def lighten(hex_color: str, amount: float) -> str:
    hex_color = hex_color.lstrip("#")
    if len(hex_color) == 3:
        hex_color = "".join(ch * 2 for ch in hex_color)
    r, g, b = (int(hex_color[i:i + 2], 16) for i in (0, 2, 4))
    r = int(r + (255 - r) * amount)
    g = int(g + (255 - g) * amount)
    b = int(b + (255 - b) * amount)
    return f"#{r:02x}{g:02x}{b:02x}"


def darken(hex_color: str, amount: float) -> str:
    hex_color = hex_color.lstrip("#")
    if len(hex_color) == 3:
        hex_color = "".join(ch * 2 for ch in hex_color)
    r, g, b = (int(hex_color[i:i + 2], 16) for i in (0, 2, 4))
    return f"#{int(r * (1 - amount)):02x}{int(g * (1 - amount)):02x}{int(b * (1 - amount)):02x}"


def escape(text: str) -> str:
    return (
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def tile(sku: str, name: str, brand: str, category: str, accent: str, rating: float) -> str:
    deep = darken(accent, 0.58)
    mid = darken(accent, 0.18)
    light = lighten(accent, 0.34)
    glyph = GLYPHS.get(category, PLACEHOLDER_GLYPH)

    # Wrap the device glyph so it scales into the upper two thirds of the tile.
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" role="img" aria-label="{escape(name)}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="{deep}"/>
      <stop offset="52%" stop-color="{mid}"/>
      <stop offset="100%" stop-color="{light}"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.76" cy="0.16" r="0.72">
      <stop offset="0%" stop-color="#ffffff" stop-opacity="0.36"/>
      <stop offset="100%" stop-color="#ffffff" stop-opacity="0"/>
    </radialGradient>
  </defs>

  <rect width="{W}" height="{H}" fill="url(#bg)"/>
  <rect width="{W}" height="{H}" fill="url(#glow)"/>

  <g transform="translate(400 250) scale(1.24) translate(-100 -100)" fill="none" stroke="#ffffff" stroke-opacity="0.94"
     stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
    {glyph}
  </g>

  <text x="46" y="62" font-family="Inter, Segoe UI, Helvetica, Arial, sans-serif" font-size="21"
        font-weight="700" letter-spacing="3.4" fill="#ffffff" fill-opacity="0.86">{escape(brand.upper())}</text>

  <text x="46" y="516" font-family="Inter, Segoe UI, Helvetica, Arial, sans-serif" font-size="37"
        font-weight="700" fill="#ffffff">{escape(name[:30])}</text>

  <text x="46" y="556" font-family="Inter, Segoe UI, Helvetica, Arial, sans-serif" font-size="20"
        fill="#ffffff" fill-opacity="0.78">{escape(sku)}  ·  {rating:.1f} / 5</text>

  <rect x="{W - 214}" y="{H - 74}" width="168" height="30" rx="15" fill="#ffffff" fill-opacity="0.2"/>
  <text x="{W - 130}" y="{H - 53}" text-anchor="middle"
        font-family="Inter, Segoe UI, Helvetica, Arial, sans-serif" font-size="16" font-weight="600"
        fill="#ffffff" fill-opacity="0.92">SHOPWISE</text>
</svg>
"""


def placeholder() -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" role="img" aria-label="Product image unavailable">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#1e1b4b"/>
      <stop offset="100%" stop-color="#4f46e5"/>
    </linearGradient>
  </defs>
  <rect width="{W}" height="{H}" fill="url(#bg)"/>
  <g transform="translate(400 300) scale(1.4) translate(-100 -100)" fill="none" stroke="#ffffff" stroke-opacity="0.7"
     stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
    {PLACEHOLDER_GLYPH}
  </g>
  <text x="400" y="470" text-anchor="middle" font-family="Inter, Segoe UI, Helvetica, Arial, sans-serif"
        font-size="26" fill="#ffffff" fill-opacity="0.85">Image coming soon</text>
</svg>
"""


def main() -> int:
    products = fetch_products()
    os.makedirs(OUT, exist_ok=True)

    with open(os.path.join(OUT, "placeholder.svg"), "w", encoding="utf-8") as handle:
        handle.write(placeholder())

    for product in products:
        svg = tile(
            product["sku"], product["name"], product["brand"], product["category"],
            product["accent"], float(product["rating"]),
        )
        with open(os.path.join(OUT, f"{product['sku']}.svg"), "w", encoding="utf-8") as handle:
            handle.write(svg)

    print(f"Wrote {len(products)} product tiles + placeholder to {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
