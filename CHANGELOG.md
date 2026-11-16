# Changelog

## [0.1.0] - 2026-10-02 - First release

- The `runicclasses:class` Origins layer (order 20, after Runic Races' heritage and race layers): thirteen
  classes and Classless, each origin showing its kit summary.
- Runic Skills integration through its 2.4.0 hooks: class stats start at 8 with caps raised by 8, free
  levels extend the global budget and are not counted as earned, 78 class Perks and 39 class Powers
  visible and usable by members only, the `class` level-up cause, and respec restores the class grants.
- Wholesale re-pick with the Class Orb; operator commands under `/runicclasses`.
- One-time starter bag at the first class confirmation (Classless uses up the grant); players who already
  received Treasure Bags' universal spawn bag are recognised and get none.
- Iron's Spells branches for the caster classes, with vanilla fallbacks for every one.
- Class data validated fail-closed at reload; generator and icon tools in `tools/`.
