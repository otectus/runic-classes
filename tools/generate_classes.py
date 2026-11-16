#!/usr/bin/env python3
"""Generate RunicClasses' class data from one source.

Usage:
    python3 tools/generate_classes.py           # write every output
    python3 tools/generate_classes.py --check   # exit 1 if any committed output differs

This file owns every number of every class kit (stats, start level, cap bonus, perk gates and
magnitudes, power tiers and internal cooldowns, starter bag contents) and the icon art direction;
tools/class_lang.json owns the prose. From the two it writes, under src/main:

  resources/data/runicclasses/classes/<class>.json              class definitions (authoritative at runtime)
  resources/data/runicclasses/origins/<class>.json              the class origins
  resources/data/runicclasses/powers/display_<class>_<n>.json   display-only Apoli powers for the origin panel
  resources/data/runicclasses/origin_layers/class.json          the class layer (order 20)
  resources/data/runicclasses/loot_tables/bags/<class>.json     vanilla-safe starter bag contents
  resources/data/runicclasses/treasurebags_types/<class>_kit.json   Treasure Bags bag types
  resources/assets/runicclasses/lang/en_us.json                 every English string
  java/com/otectus/runicclasses/data/ClassKit.java              the registration table the mod reads

The committed output is authoritative; ClassDataParityTest runs this script with --check, so the
two cannot drift. Stdlib only. Icons are a separate tool (generate_icons.py) that imports KIT.
"""

import json
import os
import sys
from dataclasses import dataclass, field
from typing import List, Optional, Tuple

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MAIN = os.path.join(ROOT, 'src', 'main')
DATA = os.path.join(MAIN, 'resources', 'data', 'runicclasses')
ASSETS = os.path.join(MAIN, 'resources', 'assets', 'runicclasses')
JAVA_KIT = os.path.join(MAIN, 'java', 'com', 'otectus', 'runicclasses', 'data', 'ClassKit.java')
LANG_SOURCE = os.path.join(HERE, 'class_lang.json')

NS = 'runicclasses'
START_LEVEL = 8
CAP_BONUS = 8
# Perk ladder (docs/plan.md 3.0): slots 1-2 start, then primary 16, secondary 16, primary 24,
# secondary 36. 36 sits above the base cap of 32 and is reachable only through the class raise.
LADDER = [('primary', 8, True), ('secondary', 8, True), ('primary', 16, False),
          ('secondary', 16, False), ('primary', 24, False), ('secondary', 36, False)]
TIERS = ('mark', 'seal', 'crown')
VALUE_TYPES = ('PERCENT', 'AMPLIFIER', 'DURATION', 'TICKS', 'MODIFIER', 'PROBABILITY', 'BOOST')
SKILLS = ('strength', 'constitution', 'dexterity', 'endurance', 'intelligence', 'building',
          'wisdom', 'magic', 'fortune', 'tinkering')
SKILL_NAMES = {s: s.capitalize() for s in SKILLS}

# Bag value table (docs/plan.md 4). Arms + Kit of every class bag must land in 5 +/- 1; the core is
# identical everywhere and not counted. The Ultima pack generator enforces the same band on its
# overrides (tools/treasurebags/generate.py --parity).
CORE = [('minecraft:oak_log', 12), ('minecraft:bread', 16), ('minecraft:torch', 8), ('minecraft:string', 4)]
VALUE_TABLE = {
    'minecraft:stone_sword': 3, 'minecraft:stone_axe': 3, 'minecraft:stone_pickaxe': 3,
    'minecraft:stone_hoe': 3, 'minecraft:stone_shovel': 3, 'minecraft:bow': 3,
    'minecraft:leather_boots': 3, 'minecraft:wooden_sword': 2,
    'minecraft:copper_ingot': 2, 'minecraft:lapis_lazuli': 2, 'minecraft:bone_meal': 2,
}
COMMODITY_VALUE = 1   # any other stack of at most 8 units; each further 8 units count again


@dataclass
class V:
    key: str
    type: str
    value: float


@dataclass
class PerkDef:
    slug: str
    values: List[V]
    icon: Tuple[str, str, str, str]   # glyph, secondary palette, badge, badge palette


@dataclass
class PowerDef:
    slug: str
    icd_seconds: int
    icon: Tuple[str, str, str, str]


@dataclass
class ClassDef:
    id: str
    primary: str
    secondary: str
    mechanic: str
    icon_item: str
    color: str
    palette: str
    perks: List[PerkDef]
    powers: List[PowerDef]
    bag: List[Tuple[str, int]]
    order: int = 0


def P(key, value):
    return V(key, 'PERCENT', value)


def A(key, value):
    return V(key, 'AMPLIFIER', value)


def D(key, seconds):
    return V(key, 'DURATION', seconds)


def T(key, value):
    return V(key, 'TICKS', value)


KIT: List[ClassDef] = [
    ClassDef('barbarian', 'strength', 'constitution', 'Fury', 'minecraft:iron_axe', '#A33A2A', 'crimson', [
        PerkDef('bloodied_resolve', [P('threshold', 50), P('bonus', 8)], ('heart_crack', 'blood', 'up', 'gold')),
        PerkDef('thick_hide', [A('health', 2)], ('chestplate', 'leather', 'plus', 'gold')),
        PerkDef('reckless_swing', [P('bonus', 12), P('knockback', 50)], ('axe', 'iron', 'arrowb', 'gold')),
        PerkDef('stubborn_renewal', [P('threshold', 30), D('duration', 6), D('cooldown', 60)], ('heart', 'nature', 'plus', 'emerald')),
        PerkDef('unstoppable', [D('duration', 5)], ('boot', 'iron', 'starb', 'gold')),
        PerkDef('titans_fury', [D('cooldown', 300), D('duration', 6)], ('crown', 'blood', 'heartb', 'crimson')),
    ], [
        PowerDef('fury_rising', 6, ('axe', 'blood', 'up', 'crimson')),
        PowerDef('blood_roar', 15, ('fang', 'blood', 'burstb', 'crimson')),
        PowerDef('unbroken', 60, ('crown', 'iron', 'shieldb', 'gold')),
    ], [('minecraft:stone_axe', 1), ('minecraft:cooked_beef', 6)]),

    ClassDef('fighter', 'strength', 'endurance', 'Drill', 'minecraft:iron_sword', '#8C94A3', 'steel', [
        PerkDef('weapon_drill', [P('bonus', 6)], ('sword', 'iron', 'up', 'gold')),
        PerkDef('quartermaster', [P('reduction', 10)], ('chestplate', 'iron', 'shieldb', 'gold')),
        PerkDef('riposte', [D('window', 3), P('bonus', 25)], ('shield_bang', 'iron', 'arrowb', 'gold')),
        PerkDef('battle_rhythm', [P('bonus', 15)], ('dual_swords', 'iron', 'clockb', 'gold')),
        PerkDef('armor_mastery', [A('toughness', 2)], ('helmet', 'iron', 'shieldb', 'gold')),
        PerkDef('champion', [P('crit', 25)], ('crown', 'gold', 'starb', 'gold')),
    ], [
        PowerDef('measured_cut', 8, ('sword', 'iron', 'clockb', 'gold')),
        PowerDef('bulwark_call', 20, ('shield', 'iron', 'shieldb', 'gold')),
        PowerDef('last_stand', 120, ('banner', 'crimson', 'heartb', 'crimson')),
    ], [('minecraft:stone_sword', 1), ('minecraft:cooked_beef', 6)]),

    ClassDef('monk', 'dexterity', 'endurance', 'Breath', 'minecraft:bamboo', '#D9822B', 'orange', [
        PerkDef('open_hand', [A('damage', 3)], ('fist', 'leather', 'up', 'gold')),
        PerkDef('centred_breath', [P('reduction', 30)], ('feather', 'bone', 'downb', 'gold')),
        PerkDef('flowing_step', [P('speed', 10)], ('boot', 'wood', 'arrowb', 'gold')),
        PerkDef('deflect', [P('chance', 20)], ('hand', 'iron', 'shieldb', 'gold')),
        PerkDef('flurry', [P('speed', 20)], ('fist', 'orange', 'burstb', 'gold')),
        PerkDef('stillness', [D('delay', 2)], ('hourglass', 'nature', 'heartb', 'emerald')),
    ], [
        PowerDef('opening_palm', 6, ('hand', 'orange', 'downb', 'gold')),
        PowerDef('quivering_strike', 12, ('fist', 'orange', 'burstb', 'gold')),
        PowerDef('perfect_form', 90, ('feather', 'holy', 'sparkle', 'gold')),
    ], [('minecraft:leather_boots', 1), ('minecraft:cooked_chicken', 8)]),

    ClassDef('rogue', 'dexterity', 'fortune', 'Edgework', 'minecraft:tripwire_hook', '#4B5263', 'shadow', [
        PerkDef('backstab', [P('bonus', 15)], ('dagger', 'iron', 'up', 'gold')),
        PerkDef('light_fingers', [A('luck', 1)], ('clover', 'emerald', 'plus', 'emerald')),
        PerkDef('evasive', [P('chance', 10)], ('smoke', 'shadow', 'arrowb', 'gold')),
        PerkDef('treasure_sense', [A('looting', 1)], ('chest', 'gold', 'coinb', 'gold')),
        PerkDef('assassinate', [P('bonus', 40)], ('mask', 'night', 'skullb', 'crimson')),
        PerkDef('lucky_strike', [P('chance', 15)], ('dice', 'gold', 'starb', 'gold')),
    ], [
        PowerDef('opening_cut', 8, ('dagger', 'crimson', 'eyeb', 'gold')),
        PowerDef('vanish', 20, ('smoke', 'night', 'moonb', 'gold')),
        PowerDef('deathmark', 45, ('skull', 'crimson', 'burstb', 'crimson')),
    ], [('minecraft:stone_sword', 1), ('minecraft:string', 8)]),

    ClassDef('ranger', 'dexterity', 'wisdom', 'The Long Watch', 'minecraft:bow', '#4F7A3A', 'moss', [
        PerkDef('steady_draw', [P('bonus', 8)], ('bow', 'wood', 'up', 'gold')),
        PerkDef('trailwise', [P('speed', 10)], ('boot', 'nature', 'leafb', 'emerald')),
        PerkDef('quarry_brand', [D('duration', 6)], ('target', 'crimson', 'eyeb', 'gold')),
        PerkDef('field_dressing', [A('looting', 1)], ('paw', 'leather', 'coinb', 'gold')),
        PerkDef('deadeye', [P('bonus', 20)], ('arrow', 'iron', 'starb', 'gold')),
        PerkDef('warden_of_the_wild', [P('pet_bonus', 20), P('reduction', 20)], ('paw', 'nature', 'shieldb', 'emerald')),
    ], [
        PowerDef('second_arrow', 6, ('arrow', 'wood', 'arrowsb', 'gold')),
        PowerDef('pin', 15, ('arrow', 'iron', 'anchorb', 'gold')),
        PowerDef('volley_of_the_watch', 40, ('arrows3', 'wood', 'starb', 'gold')),
    ], [('minecraft:bow', 1), ('minecraft:arrow', 16)]),

    ClassDef('bard', 'intelligence', 'fortune', 'Cadence', 'minecraft:note_block', '#B25FA8', 'pink', [
        PerkDef('quick_study', [P('discount', 5)], ('book', 'wood', 'up', 'gold')),
        PerkDef('silver_tongue', [P('discount', 10)], ('coin', 'gold', 'downb', 'gold')),
        PerkDef('battle_hymn', [T('radius', 8), P('speed', 8)], ('banner', 'crimson', 'boltb', 'gold')),
        PerkDef('fortunes_chord', [A('luck', 1)], ('clover', 'emerald', 'sparkle', 'gold')),
        PerkDef('hexbreaking_hymn', [T('radius', 8), P('reduction', 30)], ('shield', 'holy', 'downb', 'gold')),
        PerkDef('crescendo', [T('radius', 8), A('heal', 2)], ('heart', 'pink', 'sparkle', 'gold')),
    ], [
        PowerDef('opening_verse', 12, ('scroll', 'pink', 'arrowb', 'gold')),
        PowerDef('dissonance', 20, ('bolt', 'pink', 'downb', 'crimson')),
        PowerDef('anthem', 90, ('banner', 'holy', 'heartb', 'crimson')),
    ], [('minecraft:stone_sword', 1), ('minecraft:cookie', 8)]),

    ClassDef('cleric', 'wisdom', 'magic', 'Rites', 'minecraft:bell', '#E0C25A', 'holy', [
        PerkDef('blessed_vitality', [P('bonus', 15)], ('potion', 'holy', 'clockb', 'gold')),
        PerkDef('consecrated_weapon', [P('bonus', 15)], ('sword', 'holy', 'skullb', 'gold')),
        PerkDef('mending_touch', [P('bonus', 25)], ('flask', 'crimson', 'heartb', 'crimson')),
        PerkDef('turn_the_dead', [P('chance', 15), D('duration', 3)], ('skull', 'bone', 'no', 'gold')),
        PerkDef('sanctuary', [P('threshold', 50), D('duration', 3), D('cooldown', 10)], ('shield', 'holy', 'heartb', 'crimson')),
        PerkDef('intercession', [D('cooldown', 300), P('health', 50)], ('phoenix', 'holy', 'heartb', 'crimson')),
    ], [
        PowerDef('radiant_burst', 5, ('sun', 'holy', 'heartb', 'gold')),
        PowerDef('ward_of_light', 15, ('shield', 'holy', 'sunb', 'gold')),
        PowerDef('beacon', 60, ('torch', 'holy', 'starb', 'gold')),
    ], [('minecraft:stone_sword', 1), ('minecraft:string', 8)]),

    ClassDef('druid', 'magic', 'endurance', 'The Green Covenant', 'minecraft:oak_sapling', '#5FA03E', 'nature', [
        PerkDef('bark_skin', [A('armor', 1)], ('tree', 'darkwood', 'shieldb', 'gold')),
        PerkDef('herb_craft', [A('food', 1)], ('wheat', 'nature', 'plus', 'emerald')),
        PerkDef('thorns', [P('chance', 15), A('damage', 2)], ('thorns', 'moss', 'burstb', 'crimson')),
        PerkDef('rooted', [A('heal', 1), D('interval', 4)], ('leaf', 'darkwood', 'heartb', 'emerald')),
        PerkDef('rain_blessed', [], ('drop', 'water', 'heartb', 'emerald')),
        PerkDef('lord_of_the_grove', [D('interval', 10), T('radius', 4)], ('tree', 'nature', 'up', 'emerald')),
    ], [
        PowerDef('vine_lash', 8, ('leaf', 'moss', 'anchorb', 'emerald')),
        PowerDef('thorned_mantle', 15, ('grove_thorns', 'moss', 'burstb', 'crimson')),
        PowerDef('heart_of_the_grove', 90, ('tree', 'nature', 'heartb', 'crimson')),
    ], [('minecraft:stone_hoe', 1), ('minecraft:wheat_seeds', 8), ('minecraft:bone_meal', 4)]),

    ClassDef('sorcerer', 'magic', 'constitution', 'Wild Vein', 'minecraft:blaze_powder', '#E36C2A', 'fire', [
        PerkDef('innate_reserves', [P('bonus', 10)], ('crystal_ball', 'mana', 'up', 'gold')),
        PerkDef('hardy_vessel', [A('health', 2)], ('heart', 'fire', 'plus', 'gold')),
        PerkDef('surge', [P('chance', 10)], ('bolt', 'storm', 'sparkle', 'gold')),
        PerkDef('arcane_skin', [P('resist', 10)], ('shield', 'arcane', 'sparkle', 'amethyst')),
        PerkDef('volatile_blood', [P('threshold', 50)], ('drop', 'blood', 'up', 'crimson')),
        PerkDef('overflow', [P('heal', 5)], ('cauldron', 'fire', 'heartb', 'crimson')),
    ], [
        PowerDef('sparkvein', 6, ('bolt', 'fire', 'up', 'gold')),
        PowerDef('cascade', 15, ('drop', 'mana', 'plus', 'gold')),
        PowerDef('arcane_upwelling', 30, ('burst', 'storm', 'starb', 'gold')),
    ], [('minecraft:stone_sword', 1), ('minecraft:redstone', 8), ('minecraft:glass_bottle', 4)]),

    ClassDef('warlock', 'magic', 'fortune', 'The Bargain', 'minecraft:ender_eye', '#6B3FA0', 'void', [
        PerkDef('dark_bargain', [A('food', 1)], ('drumstick', 'void', 'plus', 'gold')),
        PerkDef('night_sight', [], ('eye', 'night', 'moonb', 'gold')),
        PerkDef('hex', [D('duration', 4), D('cooldown', 8)], ('sigil', 'void', 'skullb', 'crimson')),
        PerkDef('soul_tithe', [P('chance', 15), A('xp', 3)], ('xp_orb', 'emerald', 'coinb', 'gold')),
        PerkDef('lingering_curse', [P('bonus', 30)], ('hourglass', 'void', 'clockb', 'crimson')),
        PerkDef('pact_of_the_deep', [P('threshold', 30), P('bonus', 25)], ('eldritch_eye', 'eldritch', 'up', 'crimson')),
    ], [
        PowerDef('soul_leech', 3, ('fang', 'void', 'heartb', 'crimson')),
        PowerDef('patrons_shadow', 20, ('ghost', 'night', 'eyeb', 'crimson')),
        PowerDef('the_debt_comes_due', 120, ('eldritch_eye', 'void', 'burstb', 'crimson')),
    ], [('minecraft:stone_sword', 1), ('minecraft:bone', 8), ('minecraft:black_candle', 2)]),

    ClassDef('wizard', 'intelligence', 'magic', 'The Study', 'minecraft:enchanted_book', '#3E5FBF', 'lapis', [
        PerkDef('scholars_eye', [A('power', 1)], ('glasses', 'lapis', 'starb', 'gold')),
        PerkDef('arcane_focus', [P('bonus', 5)], ('wand', 'lapis', 'clockb', 'gold')),
        PerkDef('spell_economy', [P('reduction', 10)], ('spellbook', 'mana', 'downb', 'gold')),
        PerkDef('counter_ward', [P('resist', 15)], ('shield', 'lapis', 'sparkle', 'amethyst')),
        PerkDef('prodigy', [P('discount', 10)], ('tome', 'lapis', 'up', 'gold')),
        PerkDef('archmage', [P('bonus', 20)], ('staff', 'arcane', 'starb', 'gold')),
    ], [
        PowerDef('studied_strike', 4, ('open_book', 'lapis', 'up', 'gold')),
        PowerDef('recitation', 20, ('scroll', 'lapis', 'clockb', 'gold')),
        PowerDef('grand_working', 60, ('staff', 'arcane', 'starb', 'gold')),
    ], [('minecraft:wooden_sword', 1), ('minecraft:book', 3), ('minecraft:lapis_lazuli', 8)]),

    ClassDef('artificer', 'intelligence', 'tinkering', 'Infusions', 'minecraft:piston', '#C8713F', 'copper', [
        PerkDef('field_notes', [P('bonus', 5)], ('quill', 'wood', 'up', 'emerald')),
        PerkDef('tinkers_eye', [P('bonus', 10)], ('anvil', 'iron', 'plus', 'gold')),
        PerkDef('steady_hands', [P('bonus', 10)], ('hand', 'copper', 'coinb', 'gold')),
        PerkDef('keen_edge', [P('bonus', 5)], ('hammer', 'iron', 'up', 'gold')),
        PerkDef('overclock', [P('bonus', 10)], ('gear', 'copper', 'boltb', 'gold')),
        PerkDef('master_infuser', [P('chance', 10)], ('rune_stone', 'amethyst', 'sparkle', 'gold')),
    ], [
        PowerDef('spring_trap', 6, ('trap', 'iron', 'up', 'gold')),
        PowerDef('field_repair', 60, ('wrench', 'iron', 'plus', 'gold')),
        PowerDef('arc_capacitor', 20, ('redstone_torch', 'storm', 'boltb', 'gold')),
    ], [('minecraft:stone_pickaxe', 1), ('minecraft:copper_ingot', 4), ('minecraft:redstone', 8)]),

    ClassDef('paladin', 'constitution', 'wisdom', 'The Oath', 'minecraft:shield', '#F0D27A', 'gold', [
        PerkDef('devout_bulwark', [A('armor', 1)], ('shield', 'holy', 'plus', 'gold')),
        PerkDef('laying_of_hands', [A('heal', 4), D('cooldown', 60)], ('hand', 'holy', 'heartb', 'crimson')),
        PerkDef('valiant_presence', [T('radius', 6), A('armor', 1)], ('banner', 'gold', 'shieldb', 'gold')),
        PerkDef('smite', [P('bonus', 20)], ('warhammer', 'holy', 'sunb', 'gold')),
        PerkDef('bulwark', [A('heal', 1), D('cooldown', 3)], ('round_shield', 'gold', 'heartb', 'crimson')),
        PerkDef('oathkeeper', [D('cooldown', 300), T('radius', 8), P('health', 20)], ('phoenix', 'gold', 'heartb', 'crimson')),
    ], [
        PowerDef('righteous_blow', 8, ('warhammer', 'gold', 'up', 'gold')),
        PowerDef('aegis', 20, ('tower', 'holy', 'shieldb', 'gold')),
        PowerDef('avatar_of_the_oath', 120, ('sun', 'holy', 'heartb', 'crimson')),
    ], [('minecraft:stone_sword', 1), ('minecraft:string', 4)]),
]
for _i, _c in enumerate(KIT, start=1):
    _c.order = _i

CLASSLESS = dict(id='classless', icon_item='minecraft:compass', color='#BFA46A', order=len(KIT) + 1,
                 bag=[('minecraft:wooden_sword', 1)])


# --------------------------------------------------------------------------------------- helpers


def perk_path(c, p):
    return 'cls_%s_%s' % (c.id, p.slug)


def power_path(c, p):
    return 'cls_%s_%s' % (c.id, p.slug)


def bag_value(entries):
    total = 0
    for item, count in entries:
        if item in VALUE_TABLE:
            total += VALUE_TABLE[item]
        else:
            total += COMMODITY_VALUE * max(1, (count + 7) // 8)
    return total


def check_kit():
    errors = []
    seen = set()
    for c in KIT:
        if c.primary not in SKILLS or c.secondary not in SKILLS or c.primary == c.secondary:
            errors.append('%s: stats must be two distinct Runic Skills Aptitudes' % c.id)
        if 'building' in (c.primary, c.secondary):
            errors.append('%s: Building stays unassigned (OQ-11)' % c.id)
        if len(c.perks) != 6:
            errors.append('%s: exactly six perks, got %d' % (c.id, len(c.perks)))
        if len(c.powers) != 3:
            errors.append('%s: exactly three powers, got %d' % (c.id, len(c.powers)))
        for p in c.perks:
            path = perk_path(c, p)
            if path in seen:
                errors.append('duplicate id ' + path)
            seen.add(path)
            for v in p.values:
                if v.type not in VALUE_TYPES:
                    errors.append('%s: bad value type %s' % (path, v.type))
        for p in c.powers:
            path = power_path(c, p)
            if path in seen:
                errors.append('duplicate id ' + path)
            seen.add(path)
        value = bag_value(c.bag)
        if not 4 <= value <= 6:
            errors.append('%s: starter bag value %d outside 5 +/- 1' % (c.id, value))
    if errors:
        for e in errors:
            print('ERROR ' + e)
        sys.exit(1)


def read_lang_source():
    with open(LANG_SOURCE, encoding='utf-8') as f:
        return json.load(f)


def item_entry(item, count):
    entry = {'type': 'minecraft:item', 'name': item}
    if count != 1:
        entry['functions'] = [{'function': 'minecraft:set_count', 'count': count}]
    return entry


def gift_table(name, entries):
    return {
        'type': 'minecraft:gift',
        'random_sequence': '%s:bags/%s' % (NS, name),
        'pools': [{'rolls': 1, 'entries': [item_entry(i, n)]} for i, n in CORE + entries],
    }


def bag_type(name, color):
    return {
        'group': NS,
        'displayName': {'translate': 'bag.%s.%s_kit' % (NS, name)},
        'lootTable': '%s:bags/%s' % (NS, name),
        'rarity': 'common',
        'bagColor': color,
        'bagOverlayColor': '#2E2A22',
        'bagStringColor': '#6B4A2B',
        'visible': True,
        'dropsFromGroups': [],
    }


def fmt_number(v):
    return int(v) if float(v).is_integer() else v


# ------------------------------------------------------------------------------------- emitters


def build_outputs():
    lang_src = read_lang_source()
    out = {}  # relative path -> text

    def put_json(rel, obj):
        out[rel] = json.dumps(obj, indent=2, ensure_ascii=False) + '\n'

    rel_data = os.path.relpath(DATA, ROOT)
    rel_assets = os.path.relpath(ASSETS, ROOT)
    lang = {}
    texts = lang_src['classes']

    # Layer.
    put_json(os.path.join(rel_data, 'origin_layers', 'class.json'), {
        'replace': True,
        'order': 20,
        'enabled': True,
        'origins': ['%s:%s' % (NS, c.id) for c in KIT] + ['%s:classless' % NS],
        'name': 'layer.%s.class.name' % NS,
        'missing_origin': {
            'name': 'layer.%s.class.missing_origin.name' % NS,
            'description': 'layer.%s.class.missing_origin.description' % NS,
        },
        'gui_title': {
            'choose_origin': 'layer.%s.class.choose' % NS,
            'view_origin': 'layer.%s.class.view' % NS,
        },
        'allow_random': False,
        'auto_choose': False,
    })

    for c in KIT:
        cid = '%s:%s' % (NS, c.id)
        name = texts[c.id]['name']
        perks = []
        for (gate, level, starting), p in zip(LADDER, c.perks):
            entry = {'id': '%s:%s' % (NS, perk_path(c, p)), 'gate': gate, 'level': level}
            if starting:
                entry['starting'] = True
            if p.values:
                entry['values'] = {v.key: fmt_number(v.value) for v in p.values}
            perks.append(entry)
        put_json(os.path.join(rel_data, 'classes', c.id + '.json'), {
            'id': cid,
            'origin': cid,
            'icon': {'item': c.icon_item},
            'color': c.color,
            'stats': {'primary': 'runicskills:' + c.primary, 'secondary': 'runicskills:' + c.secondary},
            'start_level': START_LEVEL,
            'cap_bonus': CAP_BONUS,
            'perks': perks,
            'powers': {t: '%s:%s' % (NS, power_path(c, p)) for t, p in zip(TIERS, c.powers)},
            'starter_bag': {'loot_table': '%s:bags/%s' % (NS, c.id), 'bag_type': '%s:%s_kit' % (NS, c.id)},
        })
        displays = ['%s:display_%s_%d' % (NS, c.id, n) for n in (1, 2, 3)]
        put_json(os.path.join(rel_data, 'origins', c.id + '.json'), {
            'icon': {'item': c.icon_item},
            'order': c.order,
            'impact': 0,
            'name': 'origin.%s.%s.name' % (NS, c.id),
            'description': 'origin.%s.%s.description' % (NS, c.id),
            'powers': displays,
        })
        for n in (1, 2, 3):
            put_json(os.path.join(rel_data, 'powers', 'display_%s_%d.json' % (c.id, n)), {
                'type': 'apoli:simple',
                'name': 'power.%s.display_%s_%d.name' % (NS, c.id, n),
                'description': 'power.%s.display_%s_%d.description' % (NS, c.id, n),
            })
        put_json(os.path.join(rel_data, 'loot_tables', 'bags', c.id + '.json'), gift_table(c.id, c.bag))
        put_json(os.path.join(rel_data, 'treasurebags_types', c.id + '_kit.json'), bag_type(c.id, c.color))

        # Lang for this class.
        prim, sec = SKILL_NAMES[c.primary], SKILL_NAMES[c.secondary]
        lang['origin.%s.%s.name' % (NS, c.id)] = name
        lang['origin.%s.%s.description' % (NS, c.id)] = texts[c.id]['lore']
        lang['school.%s.%s' % (NS, c.id)] = name
        lang['power.%s.display_%s_1.name' % (NS, c.id)] = 'Aptitudes'
        lang['power.%s.display_%s_1.description' % (NS, c.id)] = (
            '§l%s§r and §l%s§r start at level %d, and both of their caps rise by %d.'
            % (prim, sec, START_LEVEL, CAP_BONUS))
        lang['power.%s.display_%s_2.name' % (NS, c.id)] = 'Class Perks'
        lang['power.%s.display_%s_2.description' % (NS, c.id)] = (
            'Six Perks only a %s can take, under %s and %s in the Skills screen. Two are active from the start.'
            % (name, prim, sec))
        lang['power.%s.display_%s_3.name' % (NS, c.id)] = 'Class Powers'
        lang['power.%s.display_%s_3.description' % (NS, c.id)] = (
            'A Runic Mark, Seal and Crown of the %s school, equipped from the Powers screen like any other Power.'
            % name)
        for p in c.perks:
            path = perk_path(c, p)
            pname, pdesc = lang_src['perks'][path]
            if pdesc.count('%s') != len(p.values):
                sys.exit('ERROR %s: description has %d placeholders for %d values'
                         % (path, pdesc.count('%s'), len(p.values)))
            lang['perk.%s.%s' % (NS, path)] = pname
            lang['perk.%s.%s.description' % (NS, path)] = pdesc
        for p in c.powers:
            path = power_path(c, p)
            pname, pdesc = lang_src['powers'][path]
            lang['power.%s.%s' % (NS, path)] = pname
            lang['power.%s.%s.description' % (NS, path)] = pdesc

    # Classless.
    cl = CLASSLESS
    put_json(os.path.join(rel_data, 'classes', 'classless.json'), {
        'id': '%s:classless' % NS,
        'origin': '%s:classless' % NS,
        'icon': {'item': cl['icon_item']},
        'color': cl['color'],
        'starter_bag': {'loot_table': '%s:bags/classless' % NS, 'bag_type': '%s:classless_kit' % NS},
    })
    put_json(os.path.join(rel_data, 'origins', 'classless.json'), {
        'icon': {'item': cl['icon_item']},
        'order': cl['order'],
        'impact': 0,
        'name': 'origin.%s.classless.name' % NS,
        'description': 'origin.%s.classless.description' % NS,
        'powers': ['%s:display_classless_1' % NS],
    })
    put_json(os.path.join(rel_data, 'powers', 'display_classless_1.json'), {
        'type': 'apoli:simple',
        'name': 'power.%s.display_classless_1.name' % NS,
        'description': 'power.%s.display_classless_1.description' % NS,
    })
    put_json(os.path.join(rel_data, 'loot_tables', 'bags', 'classless.json'), gift_table('classless', cl['bag']))
    put_json(os.path.join(rel_data, 'treasurebags_types', 'classless_kit.json'), bag_type('classless', cl['color']))
    lang['origin.%s.classless.name' % NS] = texts['classless']['name']
    lang['origin.%s.classless.description' % NS] = texts['classless']['lore']
    lang['power.%s.display_classless_1.name' % NS] = 'No Class Grants'
    lang['power.%s.display_classless_1.description' % NS] = (
        'No class Aptitudes, Perks or Powers. You can choose a class later with a Class Orb.')

    lang.update(lang_src['misc'])
    put_json(os.path.join(rel_assets, 'lang', 'en_us.json'), dict(sorted(lang.items())))

    out[os.path.relpath(JAVA_KIT, ROOT)] = java_kit()
    out[os.path.join('docs', 'classes.md')] = classes_doc(texts)
    return out


def classes_doc(texts):
    """docs/classes.md: the shipped class lore, readable outside the game (bold codes become Markdown)."""
    lines = ['<!-- GENERATED by tools/generate_classes.py from tools/class_lang.json; edit the lore there. -->', '',
             '# Runic Classes', '',
             'The class lore exactly as it ships in the class selection screen. Each class names its craft, its '
             'two Aptitudes (primary first), what its Runic Mark, Seal and Crown feel like, and its place in a '
             'company. Numbers live in the Perk and Power tooltips, not here.', '']
    for c in KIT + [type('C', (), {'id': 'classless'})()]:
        entry = texts[c.id]
        lines.append('## ' + entry['name'])
        lines.append('')
        lines.append(entry['lore'].replace('§l', '**').replace('§r', '**'))
        lines.append('')
    return '\n'.join(lines)


def java_kit():
    lines = [
        '// GENERATED by tools/generate_classes.py from its kit table; do not edit by hand.',
        'package com.otectus.runicclasses.data;',
        '',
        'import java.util.List;',
        '',
        '/**',
        ' * The registration table: which class Perks and Powers exist, which Aptitude each belongs to, and',
        ' * the default numbers registered with them. The class JSON under {@code data/runicclasses/classes}',
        ' * is authoritative at runtime and may override every number here; these defaults only fill the',
        ' * registered entries before the first datapack load and are what the generator also wrote there.',
        ' */',
        'public final class ClassKit {',
        '',
        '    private ClassKit() {}',
        '',
        '    public static final int START_LEVEL = %d;' % START_LEVEL,
        '    public static final int CAP_BONUS = %d;' % CAP_BONUS,
        '',
        '    /** The thirteen classes in layer order. Classless is not a class. */',
        '    public static final List<String> CLASSES = List.of(%s);' % ', '.join('"%s"' % c.id for c in KIT),
        '',
        '    /** One tooltip/handler number of a Perk: its key in the class JSON, its RS value type, the default. */',
        '    public record ValueSpec(String key, String type, double value) {}',
        '',
        '    public record PerkSpec(String path, String classId, String skill, boolean primaryGate, int level,',
        '                           boolean starting, List<ValueSpec> values) {}',
        '',
        '    public record PowerSpec(String path, String classId, String tier, String skill, int icdTicks) {}',
        '',
        '    public static final List<PerkSpec> PERKS = List.of(',
    ]
    perk_lines = []
    for c in KIT:
        for (gate, level, starting), p in zip(LADDER, c.perks):
            skill = c.primary if gate == 'primary' else c.secondary
            values = ', '.join('new ValueSpec("%s", "%s", %s)' % (v.key, v.type, float(v.value)) for v in p.values)
            perk_lines.append('            new PerkSpec("%s", "%s", "%s", %s, %d, %s, List.of(%s))'
                              % (perk_path(c, p), c.id, skill, 'true' if gate == 'primary' else 'false',
                                 level, 'true' if starting else 'false', values))
    lines.append(',\n'.join(perk_lines))
    lines.append('    );')
    lines.append('')
    lines.append('    public static final List<PowerSpec> POWERS = List.of(')
    power_lines = []
    for c in KIT:
        for tier, p in zip(TIERS, c.powers):
            power_lines.append('            new PowerSpec("%s", "%s", "%s", "%s", %d)'
                               % (power_path(c, p), c.id, tier.upper(), c.primary, p.icd_seconds * 20))
    lines.append(',\n'.join(power_lines))
    lines.append('    );')
    lines.append('}')
    return '\n'.join(lines) + '\n'


def managed_dirs():
    """Directories whose JSON files this generator owns entirely (stale files are reported)."""
    return [os.path.join(DATA, d) for d in ('classes', 'origins', 'powers', 'origin_layers',
                                             os.path.join('loot_tables', 'bags'), 'treasurebags_types')]


def main(argv):
    check = '--check' in argv
    check_kit()
    outputs = build_outputs()
    problems = []
    for rel, text in sorted(outputs.items()):
        path = os.path.join(ROOT, rel)
        current = None
        if os.path.isfile(path):
            with open(path, encoding='utf-8') as f:
                current = f.read()
        if current == text:
            continue
        if check:
            problems.append('%s %s' % ('STALE' if current is not None else 'MISSING', rel))
        else:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'w', encoding='utf-8', newline='\n') as f:
                f.write(text)
    owned = {os.path.normpath(os.path.join(ROOT, rel)) for rel in outputs}
    for d in managed_dirs():
        if not os.path.isdir(d):
            continue
        for fn in os.listdir(d):
            p = os.path.normpath(os.path.join(d, fn))
            if fn.endswith('.json') and p not in owned:
                if check:
                    problems.append('UNOWNED %s' % os.path.relpath(p, ROOT))
                else:
                    os.remove(p)
    if check:
        for p in problems:
            print(p)
        print('check: %d problem(s) across %d generated files' % (len(problems), len(outputs)))
        return 1 if problems else 0
    perks = sum(len(c.perks) for c in KIT)
    powers = sum(len(c.powers) for c in KIT)
    print('wrote %d files: %d classes + classless, %d perks, %d powers' % (len(outputs), len(KIT), perks, powers))
    for c in KIT:
        print('  %-10s %-12s / %-12s bag value %d' % (c.id, c.primary, c.secondary, bag_value(c.bag)))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
