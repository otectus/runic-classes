#!/usr/bin/env python3
"""Render RunicClasses' 16x16 perk and power icons, the Class Orb, and the aura effect icons.

Usage:
    python3 tools/generate_icons.py           # write every icon
    python3 tools/generate_icons.py --check   # exit 1 if any committed icon differs

Uses Runic Skills' icon generator (tools/icongen/gen.py: glyph library, palettes, badges, carved
shading) so class icons share its art direction exactly. Its location defaults to the sibling
checkout ../runic-skills/tools/icongen and can be overridden with RS_ICONGEN_DIR. Art direction per
icon (glyph, secondary palette, badge) lives in generate_classes.py's KIT; the primary palette is
the class's. Power icons carry Runic Skills' carved tier tally (one, two or three notches).
Requires Pillow; the committed PNGs are what the build ships, so only artists need to run this.
"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'runicclasses', 'textures')
ICONGEN = os.environ.get('RS_ICONGEN_DIR', os.path.join(os.path.dirname(ROOT), 'runic-skills', 'tools', 'icongen'))

sys.path.insert(0, HERE)
sys.path.insert(0, ICONGEN)
import generate_classes as kit  # noqa: E402

try:
    import gen  # noqa: E402  (Runic Skills' icongen)
    from PIL import Image, ImageDraw  # noqa: E402
except ImportError as e:  # pragma: no cover
    sys.exit('needs Pillow and Runic Skills\' tools/icongen (set RS_ICONGEN_DIR): %s' % e)

TIERS = {'mark': 1, 'seal': 2, 'crown': 3}


def perk_icons(glyphs):
    for c in kit.KIT:
        for p in c.perks:
            glyph, secondary, badge, badge_pal = p.icon
            yield 'perk/%s.png' % kit.perk_path(c, p), gen.render_icon(glyphs[glyph], c.palette, secondary, badge, badge_pal)


def power_icons(glyphs):
    for c in kit.KIT:
        for tier, p in zip(kit.TIERS, c.powers):
            glyph, secondary, badge, badge_pal = p.icon
            icon = gen.render_icon(glyphs[glyph], c.palette, secondary, badge, badge_pal)
            # Runic Skills' carved tier tally: visible without colour, clear of the mechanic's centre.
            for x in range(1, 8):
                icon.putpixel((x, 15), gen.OUTLINE)
            for i in range(TIERS[tier]):
                icon.putpixel((2 + i * 2, 15), gen.PALETTES[c.palette][2] + (255,))
            yield 'power/%s.png' % kit.power_path(c, p), icon


def orb():
    """The Class Orb: a cut-glass sphere in amethyst with a gold runic band."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    dark, mid, light = gen.PALETTES['amethyst']
    d.ellipse((2, 2, 13, 13), fill=mid + (255,), outline=gen.OUTLINE)
    d.ellipse((4, 4, 11, 11), fill=dark + (255,))
    d.ellipse((5, 4, 9, 7), fill=light + (255,))
    gold = gen.PALETTES['gold'][1] + (255,)
    for x in range(3, 13):
        img.putpixel((x, 9), gold)
    img.putpixel((5, 5), gen.WHITE)
    img.putpixel((6, 4), gen.WHITE)
    return img


def effect(palette, glyph_name, glyphs):
    """18x18 mob effect icon: the class glyph centred on a transparent field."""
    img = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    icon = gen.render_icon(glyphs[glyph_name], palette, palette)
    img.paste(icon, (1, 1), icon)
    return img


def outputs():
    glyphs, errors = gen.load_glyphs()
    if errors:
        sys.exit('glyph library errors: %s' % errors)
    out = {}
    for rel, icon in perk_icons(glyphs):
        out[rel] = icon
    for rel, icon in power_icons(glyphs):
        out[rel] = icon
    out['item/class_orb.png'] = orb()
    out['mob_effect/battle_hymn.png'] = effect('pink', 'banner', glyphs)
    out['mob_effect/valiant_presence.png'] = effect('gold', 'shield', glyphs)
    out['mob_effect/beacon.png'] = effect('holy', 'sun', glyphs)
    return out


def same(path, icon):
    if not os.path.isfile(path):
        return False
    with Image.open(path) as shipped:
        return shipped.mode == 'RGBA' and shipped.size == icon.size and shipped.tobytes() == icon.tobytes()


def main(argv):
    check = '--check' in argv
    problems = 0
    written = 0
    for rel, icon in sorted(outputs().items()):
        path = os.path.join(ASSETS, rel)
        if same(path, icon):
            continue
        if check:
            print('STALE ' + rel)
            problems += 1
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        icon.save(path)
        written += 1
    if check:
        print('check: %d stale icon(s)' % problems)
        return 1 if problems else 0
    print('wrote %d icon(s)' % written)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
