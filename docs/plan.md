# RunicClasses — Design and Implementation Plan

Status: planning deliverable, 2026-10-02, revised the same day with the owner's answers to the first open-question round (see §7 "Resolved"). No code exists yet; this document is the hand-off to the implementation phase.
Every claim about sibling projects below was read from source on this date. Paths are relative to `/home/otectus/Projects/` unless they start with `/`.

Owner-confirmed requirements are treated as fixed and are restated in §2.0. Anything the code could not settle is an **open question** (collected in §7, referenced inline as `OQ-n`).

---

## 1. Grounding survey

### 1.1 Loader, versions, repository state

| Project | Path | Mod id | Version | Loader | Mappings | Notes |
|---|---|---|---|---|---|---|
| Runic Skills | `runic-skills/` | `runicskills` | 2.3.0 (`gradle.properties`) | Forge 47.4.23, MC 1.20.1, Java 17, ForgeGradle 6.0.24 | Parchment 2023.09.03 | Mixins (97), jarJar for MixinExtras. The **Ultima instance still runs `runicskills-2.2.1.jar`**. |
| Runic Races | `runic-races/` | `runic_races` | 1.7.2 | Forge 47.2.0, MC 1.20.1 | Official | Origins Forge add-on; local jars in `Dependencies/`. Instance runs 1.7.2. |
| Origins Forge | `runic-races/Dependencies/origins-forge-1.20.1-1.10.0.9-all.jar` | `origins` | 1.10.0.9 | — | — | Apoli 2.9.0.8, Calio 1.11.0.5. |
| Ultima | `/home/otectus/Documents/curseforge/minecraft/Instances/Ultima/` | — | CurseForge instance "World of Ultima", Forge 47.4.23 | — | — | 348 mods; KubeJS 2001.6.5-build.26; the "spawn treasurebag" is pack data, not a mod of the suite (§1.4). |
| RunicClasses | `RunicClasses/` | (to be `runicclasses`) | — | — | — | Contains only `docs/classes.md`. **Not a git repository** (`git rev-parse` fails). Build must be bootstrapped (§5.1). |

Everything targets **Forge 1.20.1**. RunicClasses will use Forge 47.4.23 to match Runic Skills (the stricter of the two).

### 1.2 Runic Skills (what RunicClasses hard-wires into)

**Stat registry ("Aptitudes", player-facing "Skills").** Ten entries in a custom Forge registry `runicskills:skills` (`registry/RegistrySkills.java:22-42`), registered with `disableSync()`:

| Registry path | Lang | Grid index |
|---|---|---|
| `strength` | Strength | 0 |
| `constitution` | Constitution | 1 |
| `dexterity` | Dexterity | 2 |
| `endurance` | Endurance | 3 |
| `intelligence` | Intelligence | 4 |
| `building` | Building | 5 |
| `wisdom` | Wisdom | 6 |
| `magic` | Magic | 7 |
| `fortune` | Fortune | 8 |
| `tinkering` | Tinkering | 9 |

Terminology inside the code: "Skill" = aptitude, "Perk" = `registry/perks/Perk`, "Passive" = attribute ladder (`registry/passive/Passive`, 42 registered), "Power" = `registry/powers/Power`.

**Level scale and caps.**
- Every skill starts at **level 1** (`common/capability/SkillCapability.java:215` `mapSkills()`, `:350-355` `safeLevel`, `:383` `baselineGlobalLevel()` = number of skills = 10).
- Per-skill cap `skillMaxLevel` default **32** (`handler/HandlerCommonConfig.java:317`); Ultima runs 32 (`config/RunicSkills/runicskills.common.json5:85`). Absolute storage ceiling `CapabilityBounds.MAX_SKILL_LEVEL = 1000` (`common/util/CapabilityBounds.java:32`), so a per-player cap of 40 is representable without sanitizer changes.
- Global budget `playersMaxGlobalLevel` default **256**, mode `custom` (`HandlerCommonConfig.java:322-327`; `common/progression/LevelCaps.java`; `common/util/LevelCapMath.effective`). Ultima: 256 custom. Ten skills × 32 = 320 reachable, so the global budget already binds and "encourages specialization".
- Level-up is an XP purchase: `network/packet/common/SkillLevelUpSP.applyPurchase` checks `SkillLevelUpMath.canLevelUp(level, skillMaxLevel)` (`:84-88`), the global cap (`:97-101`), affordability, then calls `ProgressionService.addSkillLevels(..., Cause.PURCHASE)`.
- **All level mutation funnels through** `common/progression/ProgressionService.setSkillLevel` (`:98-150`): clamp (`clampToConfiguredRange`, `:180-184`, reads `skillMaxLevel` live) → `SkillLevelUpEvent` (cancelable) → KubeJS server hook (only when raising) → write → `reconcile` (attributes, titles, quest bridge, sync). `Cause` enum: `PURCHASE`, `COMMAND`, `RESPEC` (`:54-61`). The global-cap check is skipped for `COMMAND`/`RESPEC` (`:117-120`).
- Every other site that reads the cap directly (needs a per-player hook, see §2.6): `SkillCapability.addSkillLevel` (`:398-400`), `SkillLevelUpSP:86`, `registry/skill/Skill.java:91,116` (rank band and locked icon), `client/screen/RunicSkillsScreen.java:335,378,516,528,1031` (level text, pulse frame, level-up button), `common/perk/ScaledRequirement` (requirement scaling against cap 32), `registry/powers/PowerEligibility.scale` (tier gates as % of cap).
- `/respec` (`common/command/RespecCommand.java`) sets every skill to 1, zeroes perk ranks, clears Mark/Seal/Crown slots and cooldowns. No event is posted.

**Perk system.**
- `Perk` (`registry/perks/Perk.java`): `key` (ResourceLocation), `Supplier<Skill>`, `requiredLevel` (volatile, config-derived), optional multi-rank arrays, `texture`, `Value[]` tooltip parameters. **Constructor is public**; `Perk.add(...)` (KubeJS) shows the intended addon path. `getName()` is **path only**; `getKey()` = `perk.<namespace>.<path>`.
- Registry `runicskills:perks` (`registry/RegistryPerks.java:29-60`, `disableSaving().disableSync()`); ~470 perks registered in `RegistryPerks` by skill (Strength 47, Constitution 45, Dexterity 55, Endurance 54, Intelligence 42, Building 35, Wisdom 48, Magic 84, Fortune 48, Tinkering 69 registration-site counts).
- `RegistryPerks.getCachedValues()` snapshots the whole registry (any namespace). `getPerk(name)` builds a **path-keyed** `toUnmodifiableMap` (`:4489-4495`) — a duplicate path across namespaces would throw at first lookup. `SkillCapability.perkRank` is also path-keyed and serialized as `perk.<path>` (`SkillCapability.java:748-750`). **Consequence:** RunicClasses perk paths must be globally unique (prefix `cls_`).
- Acquisition: no perk points. A perk is **toggleable** once `skillLevel >= requiredLevel` (`Perk.getToggle`), enabled by `TogglePerkSP` (`network/packet/common/TogglePerkSP.java:66-170`) which validates: not over the optional active-perk cap (`maxActivePerks` / `perksPerGlobalLevel`, both **0 = unlimited** in Ultima: `runicskills.common.json5:90-92`), not disabled by config, rank bounds, skill level, perk groups (`data/<ns>/perk_groups/*.json`), swap cooldown (Ultima 0), then `PerkToggleEvent.Pre/Post`.
- Effect gating: `Perk.isEnabled(player)` = requiredLevel ≥ 1 ∧ not disabled ∧ `skillLevel >= requiredLevel` ∧ rank ≥ 1. Called ~234 sites, ~100×/hit on the combat path, so any extra predicate must be O(1).
- UI: `RunicSkillsScreen.buildDetailPageState` (`:727-770`) lists `skill.getPerks(skill)` (iterates the registry snapshot, `Skill.java:62-68`), removes `RegistryPerks.isHiddenFromUi`, sorts, paginates. Click handler `:1075-1085` sends `TogglePerkSP`. **This is the single UI list site for perks**; there is no other perk picker.
- Hook precedent for cross-mod wiring without a dependency cycle: `common/progression/ProgressionHooks` (volatile static functional fields installed by an optional bridge) and `common/util/HeritageBuilderHook`. RunicClasses will follow this exact shape.

**Power system (the "3 tiers").**
- `registry/powers/PowerTier.java`: `MARK(maxEquipped 5, cost 1)`, `SEAL(3, 2)`, `CROWN(1, 3)`. Lang: `tier.runicskills.mark|seal|crown` = "Runic Mark / Runic Seal / Runic Crown".
- `Power` (`registry/powers/Power.java`): `key`, `tier`, `schoolId` (ResourceLocation; ISS schools or `runicskills:` cross-cutting categories, `PowerSchool.java`), `Supplier<Skill>` governing skill, `requiredSkillLevel` (volatile), `texture`, `defaultIcdTicks`, `requiredModId`. **Constructor public**; `Power.of/ofIss` hardcode the `runicskills` namespace, so RunicClasses must call the constructor directly. `getName()` is path only; `RegistryPowers.getPower` is path-keyed (same uniqueness rule as perks). `RegistryPowers.byId` (`RegistryPowers.java:~368`) **only resolves the `runicskills` namespace** — used by proc presentation off the network; needs a one-line change (§5.3).
- Registry `runicskills:powers` (`RegistryPowers.java:44-50`). Design doc `docs/RUNIC_SKILLS_POWERS.md`: Powers are "conditional, triggered, state-based abilities — not stat bonuses"; passive in install, reactive in trigger; Crowns may have an internal cooldown (ICD).
- **Grant/unlock mechanics** (`PowerEligibility.java`): a Power is *equipped* into a tier slot via `PowerEquipSP` (`network/packet/common/PowerEquipSP.java:82-116`) after `evaluateEquip`, and re-checked on **every proc** by `evaluateActive` (`:243-300`). Gates, all percentages of the configured caps (`HandlerCommonConfig.java:395-456`, Ultima at defaults):
  - Mark: governing skill ≥ 40% of cap → **13**.
  - Seal: governing ≥ 65% → **21**, plus **Intelligence ≥ 30% → 10** (`powerSealSecondarySkillPercent`), plus one equipped Mark of the **same school id** (`powerRequirePrerequisiteChain`, `hasPrerequisite` `:338-360`).
  - Crown: governing ≥ 90% → **29**, plus total skill ≥ 70% of global cap → **180**, plus one equipped Seal of the same school.
  - Power Points: budget 14 at max earned global level, proportional below, floored by the highest tier the player's best skill qualifies for (`earnedPowerPoints` `:160-200`); Mark 1 / Seal 2 / Crown 3.
  - Availability (`PowerAvailability`), `ContentStatusIndex.declared(power)` defaults to `FULL` for any id not in its table (`registry/content/ContentStatusIndex.java:94-97`), so foreign-namespace Powers are selectable.
- Behaviour lives in Forge-bus handlers keyed by `getName()` (`registry/events/VanillaPowerEventDispatcher`, `IronsSpellbooksPowerEventDispatcher`, …) using `PowerDispatch.isEquipped(player, RegistryObject)`, `PowerDispatch.checkAndStartCooldown(player, power, now)` and `PowerDispatch.fireProc(...)` (`registry/powers/PowerDispatch.java`). RunicClasses can implement class Powers the same way from its own handlers.
- UI: `client/screen/PowersScreen.java:94-106` builds three pools from `RegistryPowers.getByTier` filtered by `filterHidden` (hidden-by-config and not equipped). Tooltips use `PowerEligibility.evaluateEquip` denial reasons (`power.runicskills.denied.*` lang keys; the `MISSING_PREREQUISITE` message names `school.<ns>.<path>`).
- Datapack tuning exists for Powers (`registry/powers/PowerOverridesReloadListener`, `PowerOverrides` record: `requiredSkillLevel`, `icdTicks`, numeric values) and for perk groups.

**Capability and lifecycle.** `SkillCapability` attached in `registry/events/PlayerLifecycleHandler.onAttachCapabilitiesPlayer`; synced whole by `SyncSkillCapabilityCP.send(player)`; unknown NBT keys are retained (`orphanTags`) so a removed addon's data survives. Login sends config, perk groups, power overrides (`PlayerLifecycleHandler.onPlayerLoggedInEvent`). Public Forge events: `SkillLevelUpEvent`, `PerkToggleEvent.Pre/Post`, `PowerProcEvent`, etc. (`docs/API_EVENTS.md`); KubeJS `RunicSkillsEvents.skillLevelUp` with `event.cause` strings (`docs/KUBEJS.md`).

**Attributes RunicClasses can lean on** (`registry/RegistryAttributes.java`): `beneficial_effect`, `break_speed`, `crafting_luck`, `critical_damage`, `enchanting_power`, `magic_resist`, `projectile_damage`, `repair_efficiency`, `xp_bonus`.

**Icon pipeline**: `runic-skills/tools/icongen/` (16×16 RGBA, catalogue-pinned; 115 power textures under `assets/runicskills/textures/power/`). RunicClasses needs its own icons in its own namespace; RS's `PowerIconCoverageTest` only covers RS ids.

### 1.3 Runic Races (selection flow and the handoff point)

- Pure Origins add-on, **no coupling to Runic Skills** (`grep runicskills runic-races/src` → nothing).
- Two Origins layers: `data/runic_races/origin_layers/family.json` (**order 0**, 7 `family_*` origins, each with `"powers": []`) and `race.json` (**order 10**, origins conditioned on the chosen family via `origins:origin` condition). Constants `RaceHelper.FAMILY_LAYER`, `RACE_LAYER`, `EMPTY_ORIGIN = origins:empty` (`util/RaceHelper.java:44-56`). Origin JSON shape: `runic-races/src/main/resources/data/runic_races/origins/primian.json` (icon item, order, impact, name/description keys, powers list).
- The vanilla `origins:origin` layer is disabled by a built-in datapack (`compat/OriginLayerPacks.java`, config `disableDefaultOriginLayer=true` in Ultima `config/runic_races/runic_races-common.toml:15`).
- **How the sequence runs**: Origins itself chains layers. At login the server sends `S2COpenOriginScreen` when the container lacks origins; the client builds `ChooseOriginScreen(pendingLayers, 0, dirt)` from `OriginsAPI.getActiveLayers()` filtered by `!container.hasOrigin(layer)`, in layer order; after each confirm Origins sets `OriginsClient.OPEN_NEXT_LAYER` and opens the next pending layer (`client/OriginBackButtonHandler.java` documents and relies on this; `C2SChooseOrigin.handle` validates `!hasOrigin(layer)`, `origin.isChoosable()`, `layer.contains(origin, player)`, then `setOrigin`, `hadAllOrigins`, `checkAutoChoosingLayers`, `onChosen`, `synchronize`).
- **Handoff point for RunicClasses:** register a third layer `runicclasses:class` with **order 20**. Nothing in Runic Races needs to call RunicClasses; Origins opens the class screen immediately after the race confirm. The "baton" is the layer order.
- Re-pick precedent: the race screen's Back button sets the family layer to `origins:empty` client-side, tells the server (`network/C2SBackToFamilyPacket.java`), and opens a fresh `ChooseOriginScreen` for the pending layers. RunicClasses reuses this exact pattern for class re-pick.
- Origins Forge exposes **no Forge event on origin change** (`io/github/edwinmindcraft/origins/api/event/` holds only `AutoBadgeEvent`). `OriginContainer.setOriginInternal` fires the `origins:chose_origin` advancement criterion and `synchronize()`. Detection of a class change therefore has to be RunicClasses' own (§2.5).
- `OrbOfOriginItem.use` resets **every active layer** via `setOrigin`, then sends Origins' `S2COpenOriginScreen`, and shrinks the stack by one unless the player is in creative (`m_7500_` check before `shrink`). The Origins Forge jar ships **no recipe** for `origins:orb_of_origin` (only badge/tooltip assets match "recipe"), and Ultima's `kubejs/` and `config/` add none, so today the orb is command-only. Owner decision: the suite ships **divided orbs** instead — a Heritage Orb in Runic Races that re-opens heritage + race only, and a Class Orb in RunicClasses that re-opens the class only (§2.3, §5.3, §5.6). The Origins orb stays an operator tool; the reconciler still handles the empty class layer it leaves behind.
- No other Origins layers exist in the pack (`grep origin_layers` over `datapacks/`, `kubejs/data/`, `global_packs/` → nothing; no "Origins Classes" style mod installed).

### 1.4 Ultima: the universal spawn treasurebag today

- Mods: `treasure-bags-1.20.1-1.9.0.jar` (SilentChaos512) + `silent-lib-1.20.1-8.0.0.jar`.
- **Grant path (decompiled):** `net.silentchaos512.treasurebags.SideProxy.commonSetup` calls `net.silentchaos512.lib.event.InitialSpawnItems.add(treasurebags:starting_inventory, player -> roll that loot table)`. Silent Lib's `InitialSpawnItems.onPlayerLoggedIn(PlayerLoggedInEvent)` runs each registered function **once per player at login**, recording the grant in the player's Forge persisted NBT subcompound `silentlib.SpawnItemsGiven` (strings `getPersistedDataSubcompound`, `givenItems` in the class; exact tag layout to be confirmed when implementing the migration rule in §2.9).
- The pack overrides the table: `kubejs/data/treasurebags/loot_tables/starting_inventory.json` → one `treasurebags:treasure_bag` with `treasurebags:set_bag_type` = `ultima:newcomer`. Generated by `tools/treasurebags/generate.py:317-320` from `tools/treasurebags/catalog/starter.py` (the generator owns every file under `kubejs/data/ultima/` and that override; **never hand-edit the JSON**, per `tools/treasurebags/README.md`).
- Bag type `kubejs/data/ultima/treasurebags_types/newcomer.json` ("Newcomer's Knapsack", `lootTable: ultima:bags/newcomer`, colours `#6E8B3D/#87CEEB/#6B4A2B`). Live contents (`catalog/starter.py`, fixed single-entry pools): `minecraft:wooden_sword`×1, `minecraft:oak_log`×12, `minecraft:bread`×16, `hardcore_torches:unlit_torch`×8, `survival_firesticks:firestick`×1, `plantfiber:grass_bandage`×8, `spartanshields:wooden_basic_shield`×1, `sophisticatedbackpacks:backpack`×1, `map_atlases:atlas`×1, `sereneseasons:calendar`×1, `waystones:warp_scroll`×1. (The prose in `CATALOG_SOURCE.md:425` lists an older recipe; the Python catalog is what generates the live file.)
- Treasure Bags config `config/treasurebags-common.toml`: `disabledGroups = ["example","another_example","default"]` (built-in bags off), `alwaysSpawnItems = false`.
- **Timing consequence (critical):** the bag is granted at `PlayerLoggedInEvent`, *before* heritage/race/class exist. "Ultima's spawn handler queries the player's class" is impossible at that moment. The grant has to move to the instant the class layer is first confirmed (§2.9). The pack's override must then become an **empty-pool** table (not deleted — deleting would re-expose Treasure Bags' built-in `starting_inventory` from its jar).
- **Selection bridge** (`kubejs/{startup,server,client}_scripts/ultima_selection_bridge.js`): cancels MCA's `DestinyScreen` until the server observes heritage + race complete (`usbObserve`: `complete = entry != null && family === entry.familyOrigin`), mirrors the race into a Townstead Root, then `release`s; the client reopens pending Origins layers itself while in `wait_race` (`usbClientMaybeOpenOrigins`). It only looks at `RaceHelper.RACE_LAYER`/`FAMILY_LAYER`. With a class layer, release would fire while the class screen is open and MCA's Destiny screen could replace it → the bridge must treat "class chosen" as part of completeness (§5.5). Townstead is third-party (read-only); the bridge scripts are pack-owned and editable.
- Runic Skills values in the pack (`config/RunicSkills/runicskills.common.json5`): `skillMaxLevel 32`, `playersMaxGlobalLevel 256`, `globalLevelCapMode custom`, `maxActivePerks 0`, `perksPerGlobalLevel 0.0`, `powerPointBudgetMax 14`, tier percents at defaults, `powerRequirePrerequisiteChain true`, `powerEnforcePointBudget true`, `perkSwapCooldownTicks 0`.
- Installed mods relevant to kits and bags: `irons_spellbooks-1.20.1-3.16.3`, `ars_nouveau-1.20.1-4.12.7`, `bettercombat-forge-1.9.0`, `curios-forge-5.14.1`, `Pehkui-3.8.2`, `FarmersDelight-1.3.4`, `SpartanShields-3.1.1`, `spartantoolkit`, `locks_reforged-1.7.5` (namespace `locks`), `sophisticatedbackpacks`, Immersive series 1.1.0, Apotheosis 7.4.8, MCA add-ons, `origins-forge-1.10.0.9`. Item ids used in bag tables **must be validated with `tools/treasurebags/validate.py`** before they ship.

### 1.5 Constraints the plan is built around

1. RS registries are path-keyed in several caches → class perk/power paths are prefixed `cls_<class>_…` and globally unique.
2. RS must not import RunicClasses (RunicClasses imports RS registries) → all RS-side behaviour changes are **hooks with no-op defaults** that RunicClasses installs at mod construction, exactly like `ProgressionHooks`.
3. Origins has no change event → RunicClasses reconciles the class layer against its own applied state (login, respawn, clone, and a cheap per-tick memo), the same approach the pack bridge and `RaceHelper` already use.
4. The spawn bag is granted at login today; the class bag is granted at **first class confirmation** by RunicClasses, and the pack's login grant becomes empty.
5. Ultima's RS jar (2.2.1) lags the repo (2.3.0); RunicClasses will require the RS build that carries the hooks (proposed **2.4.0**).
6. Re-pick must not go through Origins' all-layer orb: the owner wants a Heritage Orb (Runic Races 1.7.3) and a Class Orb (RunicClasses), each clearing only its own layers.

---

## 2. Architecture

### 2.0 Fixed requirements (owner-confirmed)

1. Class is chosen at first join and re-pickable at will. Re-pick is a wholesale swap of stat grants, cap raises, class perks and class powers. The starter bag is a one-time first-join grant based on the first class chosen; never re-granted or changed.
2. Per class: two Runic Skills stats start at level 8; those two stats' caps are raised by 8 for members.
3. Per class: 6 class-exclusive perks (invisible to non-members), earnable ones gated by the class's own two stats.
4. Per class: 3 class powers, one Mark, one Seal, one Crown.
5. Classes are purely positive. Races own all drawbacks.
6. Subclass concepts in lore are flavour only.
7. Classless is selectable: no stats, caps, perks, powers.
8. Per-class loot-table-driven starter bag replacing the universal bag; bland parity.
9. RunicClasses is hard-wired into Runic Skills; edits inside Runic Skills, Runic Races and Ultima are expected.
10. Re-pick is item-driven and **divided**: a **Heritage Orb** (Runic Races) re-opens heritage and race only; a **Class Orb** (RunicClasses) re-opens the class only. Origins' all-layer Orb of Origin is not the player path.
11. Classless counts as the first confirmation for the one-time bag grant. Class Seals keep every Runic Skills gate (no exemptions). Building stays the one Aptitude no class uses.

### 2.1 Component overview

```
Origins Forge  ──layer order──▶ runic_races:family (0) ─▶ runic_races:race (10) ─▶ runicclasses:class (20)
                                                                                      │ (origin chosen / emptied)
RunicClasses server ◀─ ClassReconciler (login, respawn, clone, per-tick memo) ◀───────┘
   │ reads: IOriginContainer.getOrigin(CLASS_LAYER)          writes: ClassStateCapability (applied class, grants, bag flag)
   ├─▶ Runic Skills: ProgressionService.setSkillLevel(Cause.CLASS)   (stat grants)
   ├─▶ Runic Skills hooks: SkillCapHooks (+8 caps), ContentGateHooks (perk/power visibility), RespecEvent
   ├─▶ Runic Skills registries: runicskills:perks / runicskills:powers entries under namespace runicclasses
   ├─▶ Starter bag: StarterBagService (loot table runicclasses:bags/<class>, Treasure Bags item when present)
   └─▶ S2C ClassStateSync (flags for the client UI)  +  S2C OpenClassScreen (re-pick)
Ultima pack: starting_inventory override → empty; class bag overrides generated by tools/treasurebags; selection bridge waits for class.
```

Mod id `runicclasses`, package `com.otectus.runicclasses`, Forge 47.4.23, Parchment like Runic Skills (Runic Races uses official mappings; either compiles against the same SRG jars, Parchment keeps RS source conventions).

Dependencies (`mods.toml`): `origins` mandatory `[1.10,)`, `runicskills` mandatory `[2.4.0,)`, `runic_races` **optional** `AFTER` (nothing is called; ordering only guarantees the race layer exists first), `treasurebags` optional `AFTER` (bag item), `irons_spellbooks` / `ars_nouveau` optional `AFTER` (caster perk/power effects, reflection-guarded like Runic Races' `integration/`).

### 2.2 Class definition data (one file per class)

Registries freeze at startup, so perks, powers and origins are declared in code/data at load time; the per-class JSON **binds** registered ids and carries every tunable number. Validation is fail-closed at reload (pattern: Runic Gods `RunicGodsDataManager` snapshot + validator).

`data/runicclasses/classes/<class>.json`:

```json
{
  "id": "runicclasses:fighter",
  "origin": "runicclasses:fighter",
  "icon": { "item": "minecraft:iron_sword" },
  "stats": { "primary": "runicskills:strength", "secondary": "runicskills:endurance" },
  "start_level": 8,
  "cap_bonus": 8,
  "perks": [
    { "id": "runicclasses:cls_fighter_weapon_drill",   "gate": "primary",   "level": 8,  "starting": true },
    { "id": "runicclasses:cls_fighter_quartermaster",  "gate": "secondary", "level": 8,  "starting": true },
    { "id": "runicclasses:cls_fighter_riposte",        "gate": "primary",   "level": 16 },
    { "id": "runicclasses:cls_fighter_battle_rhythm",  "gate": "secondary", "level": 16 },
    { "id": "runicclasses:cls_fighter_armor_mastery",  "gate": "primary",   "level": 24 },
    { "id": "runicclasses:cls_fighter_champion",       "gate": "secondary", "level": 36 }
  ],
  "powers": { "mark": "runicclasses:cls_fighter_measured_cut",
              "seal": "runicclasses:cls_fighter_bulwark_call",
              "crown": "runicclasses:cls_fighter_last_stand" },
  "starter_bag": { "loot_table": "runicclasses:bags/fighter", "bag_type": "runicclasses:fighter_kit" }
}
```

Rules enforced by `ClassDefinitionValidator` (ERROR aborts the commit, previous snapshot stays live):
- exactly two distinct stats, both resolvable in `RegistrySkills`;
- exactly 6 perks, all registered in `runicskills:perks` under namespace `runicclasses`, each perk's registered `Skill` equals the stat named by `gate`, `level` within `[1, skillMaxLevel + cap_bonus]`;
- exactly one power per tier, each registered in `runicskills:powers`, tier matches the key, `schoolId == runicclasses:<class>`, governing skill == `primary`;
- `origin` resolvable in the Origins registry and listed in `runicclasses:class` layer;
- a perk or power id may belong to one class only (the owner map `perk path → class id` is built here and consumed by the visibility hooks);
- `classless.json` is the only definition allowed to omit `stats`, `perks`, `powers` and must omit them.

`tools/generate_classes.py` (static generator, same role as `runic-races/tools/generate_races.py`): from one Python source emits the class JSON, the origin JSON (`data/runicclasses/origins/<class>.json`), the layer, and the lang block. The committed JSON is authoritative; `ClassDataParityTest` (JUnit, `src/test`) re-runs the generator in check mode, so the two can never drift.

### 2.3 Selection UI integration

- **Layer** `data/runicclasses/origin_layers/class.json`: `order 20`, `replace true`, `auto_choose false`, `allow_random false`, `origins` = the 13 class origins + `runicclasses:classless`, `name/gui_title/missing_origin` lang keys (`layer.runicclasses.class.*`, mirroring `layer.runic_races.race.*`). Field names come from `OriginLayer.CODEC` (`order, origins, enabled, replace, name, missing_origin, allow_random, allow_random_unchoosable, exclude_random, default_origin, auto_choose, hidden, gui_title`).
- **Origins** `data/runicclasses/origins/<class>.json`: `impact 0`, `order` = class index, `icon` item, name/description keys, `"powers"` = a small list of **display-only** Apoli powers (`apoli:simple` with `name`/`description`, no effect; verify the type id against the Apoli 2.9 jar) so the Origins panel shows the kit summary: "Aptitudes: Strength & Endurance begin at 8, cap 40", "Class perks: six, see the Skills screen", "Class powers: Mark / Seal / Crown". Classless lists one line "No class grants." These powers carry no gameplay; all gameplay stays in Runic Skills. The screen title line comes from the layer `gui_title.choose` = "Choose your Class".
- **First join:** nothing to code. Origins opens family → race → class in order; after the class confirm `hasAllOrigins()` becomes true and Origins releases the player.
- **Re-pick: the Class Orb** (`runicclasses:class_orb`, item in `registry/RCItems`). The player path is the item; `/runicclasses choose [player]` stays an operator command for testing. Server-side `ClassOrbItem.use` (mirrors `OrbOfOriginItem.use`):
  1. refuse unless the player **has** heritage, race and a class layer value (never mid-selection) and `repickCooldownTicks` has elapsed;
  2. `container.setOrigin(CLASS_LAYER, EMPTY_ORIGIN)`, `synchronize()`, then send Origins' own `S2COpenOriginScreen(false)` through `OriginsCommon.CHANNEL` — exactly what the Origins orb does, and valid here because no screen is open during an item use (the reopen flag only fires while no screen is open, per `C2SBackToFamilyPacket`'s comment);
  3. the reconciler sees the layer empty and applies the Classless state (wholesale removal) immediately, so the player is Classless until they confirm — which also makes an operator's Origins orb reset behave identically;
  4. the client builds `pending = active layers without an origin` (= `[class]`) and opens `ChooseOriginScreen`; the confirm goes through Origins' normal `C2SChooseOrigin`; the reconciler applies the new class;
  5. the orb is consumed on use unless the player is in creative (same as Origins). Recipe: shipped by RunicClasses as `data/runicclasses/recipes/class_orb.json`; Origins ships none for its orb, so there is no precedent cost — `OQ-19` holds the proposed recipe. Ultima may override the recipe like any datapack.
  Escape on the choose screen leaves the layer empty; the player stays Classless and Origins re-prompts at next login because `hasAllOrigins()` is false (`OQ-1`, unchanged).
- **Heritage Orb** (`runic_races:heritage_orb`, Runic Races 1.7.3, `registry/ModItems` + `item/HeritageOrbItem`): same shape, but clears **only** `RaceHelper.FAMILY_LAYER` and `RACE_LAYER` and leaves the class layer untouched, so race and class re-picks never interfere. The Ultima selection bridge already reconciles a changed race into the Townstead Root ("post-Back/Orb selection" path in `usbReconcile`), so no bridge change is needed for it beyond the class-awareness in §5.5. Class grants are unaffected by a race change (no coupling, §1.3).
- **Classless** is a real origin in the layer, selectable like any class, so the Origins flow never blocks on it.
- The pack bridge needs to learn the third layer (§5.5) so MCA's Destiny screen waits for the class confirm.

### 2.4 Class state capability and persistence

`ClassStateCapability` (own Forge capability `runicclasses:class_state`, attached to non-fake players, `INBTSerializable`, copied on `PlayerEvent.Clone` for death and dimension change like `RacialEventHandler.onPlayerClone`):

| Field | Type | Meaning |
|---|---|---|
| `dataVersion` | int | schema (1) |
| `appliedClass` | String (origin id or "") | class whose grants are currently applied; "" = none/Classless |
| `appliedPrimaryStat`, `appliedSecondaryStat` | String | the stats the grants were applied to (so a later definition edit can still be undone correctly) |
| `appliedStartLevel`, `appliedCapBonus` | int | numbers applied (so config changes are reversible) |
| `firstClass` | String | first class ever confirmed (incl. `classless`) |
| `starterBagGranted` | boolean | one-time flag |
| `starterBagClass` | String | which bag was granted (diagnostics) |
| `lastRepickGameTime` | long | re-pick cooldown |

Source of truth for *which* class the player is: the Origins container (`IOriginContainer.getOrigin(CLASS_LAYER)`), on both sides (Origins already syncs the container to its owner; `OriginBackButtonHandler` reads it client-side). The capability stores only *what has been applied*, so reconcile is a diff.

`ClassHelper.getClassId(player)` memoises per tick exactly like `RaceHelper.getRaceId` (`runic-races/.../util/RaceHelper.java:30-75`), with `invalidate(uuid)` on logout and clone. Everything hot (perk/power visibility predicates) reads through it.

### 2.5 Reconcile and the wholesale swap

`ClassReconciler.reconcile(ServerPlayer)` runs at `PlayerLoggedInEvent` (after Runic Skills' own login handler — register with `EventPriority.LOW`), `PlayerRespawnEvent`, after `Clone`, and from a per-tick memo check every 20 ticks (cheap: one container read). It compares `selected = origin on class layer` with `cap.appliedClass`:

```
if selected == applied: ensure(); return          // idempotent repair (see below)
undo(applied)   // if applied != ""
apply(selected) // if selected is a class (not "", not classless)
grantStarterBagIfFirst(selected)
sync()
```

`undo(oldClass)` (per stat S with level L, using the applied numbers):
- `ProgressionService.setSkillLevel(player, S, clamp(1, L − (startLevel − 1)), Cause.CLASS)` then re-clamp to the base cap: the free `startLevel − 1 = 7` levels leave, purchased levels stay. Only a stat sitting at exactly 40 loses one purchased level to the base cap 32 (40 − 7 = 33 → 32); `OQ-3` asks whether to refund that XP (`SkillLevelUpSP.requiredPoints` gives the cost).
- `capability.setPerkRank(perk, 0)` for all six class perks; `unequipPower` for the three class powers; clear their `powerCooldowns`/`powerWindows` entries; `RegistryAttributes.modifierAttributes`, `RegistryTitles.syncTitles`, `SyncSkillCapabilityCP.send` (the same reconcile `ProgressionService` performs).
- Perks of other trees gated on the old stat simply stop working if the level falls below their requirement — that is existing RS policy for skill loss (`Perk.isEnabled` re-checks the level every call).

`apply(newClass)`:
- `setSkillLevel(player, S, max(L, startLevel), Cause.CLASS)` for both stats. `Cause.CLASS` bypasses the global budget like `COMMAND` does and is visible to KubeJS as `event.cause === "class"`.
- `setPerkRank(perk, 1)` for perks flagged `starting`; the other four are left at 0 and appear in the Skills screen for the member to enable when the gate is met.
- Powers are **not** auto-equipped (slots and Power Points are the player's budget); they become visible/equippable in the Powers screen. `OQ-5` asks whether the Mark should auto-equip into a free slot on first pick.
- Record `applied*` fields.

`ensure()` (same class, periodic): if a class stat is below `startLevel` (a `/respec` happened — RS resets to 1) raise it back; if a starting perk is at rank 0 **because of a respec** (RS posts the new `RespecEvent`, §5.3) re-enable it; otherwise leave the player's toggles alone (starting perks are "active by default", not forced on).

Death, dimension change and relog change nothing: the capability clones, the Origins container persists, and the memo recomputes.

### 2.6 Stat grants and cap raises inside Runic Skills

New `common/progression/SkillCapHooks` in Runic Skills:

```java
public final class SkillCapHooks {
    /** Extra per-skill cap for this player (0 default). Installed by addons; O(1) required. */
    public static volatile ToIntBiFunction<Player, Skill> skillCapBonus = (p, s) -> 0;
    /** Extra global budget for this player (0 default). */
    public static volatile ToIntFunction<Player> globalCapBonus = p -> 0;
    public static int skillCap(Player p, Skill s) { return CapabilityBounds.clampSkillLevelTo(cfg.skillMaxLevel + bonus, …); }
    public static int globalCap(Player p) { return LevelCaps.global() + globalCapBonus(p); }
}
```

Consult sites (all currently read `skillMaxLevel` directly, §1.2): `SkillCapability.addSkillLevel`, `ProgressionService.clampToConfiguredRange` → new `clampForPlayer(player, skill, requested)` used by `setSkillLevel`, `SkillLevelUpSP.applyPurchase` (both the per-skill and the global check), `RunicSkillsScreen` (`:335`, `:378`, `:516`, `:528`, `:1031` — client reads the local player's bonus), `Skill.getRank`/`lockedTextureFor` (rank band and locked icon scale to the effective cap). `ScaledRequirement` and `PowerEligibility.scale` **stay on the configured cap** — tier gates and integration requirements must not move for class members (decision; `OQ-4` lists the alternative).

RunicClasses installs `skillCapBonus = (p, s) -> definition(classOf(p)).capBonusFor(s)` on both sides (the client reads the Origins container) and `globalCapBonus = p -> definition(classOf(p)).freeLevels()` where free levels = 2 × (startLevel − 1) = 14, so the grant never eats purchasable budget (`OQ-2` confirms this policy and whether free levels should count toward the perk/Power-Point "earned" scaling; recommendation: raise the global cap, and subtract free levels from `getEarnedGlobalLevelForPerkBudget` through a third hook so a class does not accelerate Power Points).

### 2.7 Perk registration and class exclusivity

- RunicClasses registers 78 perks into `runicskills:perks` with `DeferredRegister.create(RegistryPerks.PERKS_KEY, "runicclasses")`, ids `runicclasses:cls_<class>_<slug>`, through the public `Perk` constructor (`new Perk(key, RegistrySkills.STRENGTH, requiredLevel, texture, values...)`), textures under `assets/runicclasses/textures/perk/`, lang `perk.runicclasses.cls_<class>_<slug>` + `.description`. Config-derived tunables (`requiredLevel`, `Value`s) come from RunicClasses' own `ForgeConfigSpec` server config and are refreshed in place with `Perk.adoptTunables` on config reload (same contract as `RegistryPerks.refreshFromConfig`).
- Registration order: Forge fires `RegisterEvent` for `runicskills:perks` to every mod's DeferredRegister after Runic Skills created the registry; `RegistryPerks.getCachedValues()` is memoised on first call (server start), after all registration — verified by a GameTest that lists the 78 ids.
- **Exclusivity** = a new `registry/ContentGateHooks` in Runic Skills:

```java
public static volatile BiPredicate<Player, Perk>  perkAvailable  = (p, perk) -> true;
public static volatile BiPredicate<Player, Power> powerAvailable = (p, power) -> true;
```

  consulted at: `RunicSkillsScreen.buildDetailPageState` (`perks.removeIf(!perkAvailable(local, perk))` right after `isHiddenFromUi`), `TogglePerkSP.handle` (reject enable/rank-up when unavailable, allow disable), `Perk.isEnabled(player)` and `isEnabled()` (effects never fire for non-members even if a rank is somehow set), `Perk.getToggle`/`canPerk` (tooltip state). RunicClasses' predicate: `ownerClass = PERK_OWNER.get(perk.getName())` (an `Object2ObjectOpenHashMap` built at data commit; null for every non-class perk → `true` fast path), else `ownerClass.equals(ClassHelper.getClassId(player))` via the per-tick memo. Cost: one hash lookup for RS's own perks.
- Perk **thresholds**: starting perks at level 8 (the class start, so they are enable-able from the first second), earnable at 16 / 16 / 24 / 36. 36 sits above the base cap of 32 and is reachable only through the class cap raise, which makes the sixth perk a genuine class capstone. Levels are literal (not `ScaledRequirement`), because they are expressed against the class cap of 40, not the reference cap of 32; the server config exposes the four earnable levels per class for pack tuning.
- Class perks count toward `maxActivePerks` like any perk (Ultima: unlimited). **Resolved (OQ-6, from the owner's "no exemptions"):** starting perks are not exempt in packs that cap perks.

### 2.8 Power registration per tier

- 39 powers into `runicskills:powers`, ids `runicclasses:cls_<class>_<slug>`, `new Power(key, tier, new ResourceLocation("runicclasses", "<class>"), RegistrySkills.<PRIMARY>, requiredSkillLevel, texture, icdTicks, null)`. Lang: `power.runicclasses.<path>` + `.description`, `school.runicclasses.<class>` (the prerequisite denial message resolves `school.<ns>.<path>`).
- **School id = the class.** With one power per tier sharing the class school, Runic Skills' prerequisite chain gives the intended ladder for free: the class Seal needs the class Mark equipped, the class Crown needs the class Seal (`PowerEligibility.hasPrerequisite`).
- Gates reuse RS's tier thresholds: `requiredSkillLevel = PowerEligibility.governingSkillRequirement(tier)` (13 / 21 / 29 in Ultima) on the class primary stat, which members start at 8, so the Mark arrives after five purchased levels. The Seal's Intelligence ≥ 10 secondary gate and the Crown's 180 total-skill gate apply unchanged. **Resolved (OQ-7):** no exemptions; class powers obey every Runic Skills gate.
- Slots and Power Points are **not** extended: class powers compete for the normal 5/3/1 slots and the 14-point budget. The class simply adds three options nobody else can take. `OQ-8` lists the alternative (dedicated class slots).
- **Visibility/eligibility**: `ContentGateHooks.powerAvailable` consulted in `PowersScreen.filterHidden` (remove unless equipped) and in `PowerEligibility.evaluateActive` as a new denial `Reason.CLASS_RESTRICTED` (lang `power.runicskills.denied.class_restricted`), which automatically covers `evaluateEquip`, every proc (`PowerDispatch.isEquipped`) and the Powers tooltip. After a swap, `undo()` also unequips the old class's powers explicitly so the slots free up.
- **Behaviour**: RunicClasses' own Forge-bus handlers (`power/handlers/*`), one class per power family, using `PowerDispatch.isEquipped`, `checkAndStartCooldown` (ICD via `PowerCooldownDebt`, so it persists across relog) and `fireProc` for the shared presentation. Caster-class powers have an Iron's Spells branch (events `SpellOnCastEvent`, `SpellDamageEvent`, `ModifySpellLevelEvent`, reflection/`ModList` guarded) and a vanilla fallback so every power works in a pack without magic mods (RS's `VanillaPowerEventDispatcher` policy).
- **Presentation**: `PowerProcCP` resolves ids with `RegistryPowers.byId`, which rejects non-`runicskills` namespaces → change it to resolve any namespace by path (`getPower(path)`), and confirm `client/vfx` descriptors fall back to a default glyph/colour for an unknown school id (task in §5.3).
- Datapack tuning: RS's power override listener reads `data/<namespace>/…`, so class powers get the same `requiredSkillLevel`/`icdTicks` overrides without new code (verify the listener folder name when implementing).

### 2.9 Starter bag pipeline

**Definitions shipped by RunicClasses (vanilla-safe defaults):**
- `data/runicclasses/loot_tables/bags/<class>.json` (`type: minecraft:gift`, fixed single-entry pools like `ultima:bags/newcomer`), one per class plus `classless.json`.
- `data/runicclasses/treasurebags_types/<class>_kit.json` (`group: "runicclasses"`, `lootTable: runicclasses:bags/<class>`, `rarity: common`, `dropsFromGroups: []`, `visible: true`, class colours). Treasure Bags' `BagTypeManager` loads `treasurebags_types` from every namespace (the pack already uses `ultima:`), and ignores the folder when the mod is absent.

**Grant (`StarterBagService.grant(player, classId)`), called by the reconciler the first time the class layer is confirmed (any class or Classless):**
1. If `cap.starterBagGranted` → return.
2. Migration rule for players who existed before RunicClasses: if the Silent Lib persisted subcompound `silentlib.SpawnItemsGiven` already records `treasurebags:starting_inventory`, set `starterBagGranted = true`, `starterBagClass = "legacy"` and return (they already got the Newcomer's Knapsack at some earlier login).
3. If `treasurebags` is loaded: build `treasurebags:treasure_bag` with NBT `BagType = <bag_type>` (same tag `treasurebags:set_bag_type` writes; verify the key in `TreasureBagItem`) and give it (`ItemHandlerHelper.giveItemToPlayer`). Otherwise roll `<loot_table>` with `LootContextParamSets.GIFT` and give the items directly.
4. Set `starterBagGranted = true`, `firstClass`, `starterBagClass`; log once.

Classless receives the universal bag: `runicclasses:bags/classless` defaults to a vanilla copy of today's Newcomer's Knapsack; Ultima overrides it with the exact newcomer contents (§5.5). **Resolved (OQ-9):** a Classless first confirmation uses up the grant; picking a class later grants nothing.

**Ultima side:** the generator stops emitting a bag into `starting_inventory.json` (empty pool, file kept) and gains a `class_kits.py` catalog that writes overrides for `runicclasses:bags/<class>` and `runicclasses:<class>_kit` in the `runicclasses` namespace, validated by `validate.py`. Silent Lib still "grants" the empty table at login and sets its flag — harmless.

### 2.10 Networking, commands, config

- Channel `runicclasses:main`, protocol "1": `S2CClassStateSync` (flags: starterBagGranted, firstClass, lastRepick; the class id itself comes from the Origins container). Screen opening reuses Origins' `S2COpenOriginScreen`; no re-pick request packet exists because the Class Orb is a server-side item use.
- Commands (`/runicclasses` root, aliases registered the way `PlayerLifecycleHandler.registerNamespacedAliases` does): `choose [player]` (op 2; forces the Class Orb flow without an item), `info [player]`, `set <player> <class>` (op 2; writes the Origins layer and reconciles), `reset <player>` (op 2; clears to empty), `bag give <player> [class]` (op 2; testing; respects the flag unless `--force`), `validate` (op 2; prints the data validator report), `diagnostics`.
- Server config `runicclasses-server.toml`: `startLevel=8`, `capBonus=8`, `allowRepick=true`, `repickCooldownTicks=0`, `classlessGetsUniversalBag=true`, `classFreeLevelsExtendGlobalCap=true`, `classFreeLevelsCountAsEarned=false`, per-class earnable perk levels, per-perk magnitudes, per-power ICDs. Common config: `debugLogging`.

### 2.11 Client UI touchpoints

- Origins' `ChooseOriginScreen` is reused unchanged for class selection (same look as heritage/race).
- Runic Skills `RunicSkillsScreen`: class perks appear inside the two class stats' perk pages for members only (hook filter). A small class badge on the overview page (tooltip: current class, "Use a Class Orb to change class") is a RunicClasses client addition via `ScreenEvent.Init.Post` on `RunicSkillsScreen` (the same injection technique as `OriginBackButtonHandler`), not an RS edit. No free re-pick button: re-pick is item-gated.
- Runic Skills `PowersScreen`: class powers show in their tier pools for members only; tooltip denial text "Class-restricted" for everybody else (should never display, since non-members never see them).
- Level text and level-up button honour the raised cap (hook).
- A tiny HUD rune or title is **not** planned; `OQ-10` asks whether the class should appear as a Runic Skills Title (`RegistryTitles` is config-driven, so a title per class is cheap).

---

## 3. Class kits

### 3.0 Shared scheme

**Stat matrix** (real `runicskills:` paths). Coverage: Magic 5, Constitution/Dexterity/Endurance/Intelligence/Wisdom/Fortune 3 each, Strength 2, Tinkering 1, **Building 0** (confirmed by the owner, OQ-11).

| Class | Primary (governs powers) | Secondary |
|---|---|---|
| Barbarian | strength | constitution |
| Fighter | strength | endurance |
| Monk | dexterity | endurance |
| Rogue | dexterity | fortune |
| Ranger | dexterity | wisdom |
| Bard | intelligence | fortune |
| Cleric | wisdom | magic |
| Druid | magic | endurance |
| Sorcerer | magic | constitution |
| Warlock | magic | fortune |
| Wizard | intelligence | magic |
| Artificer | intelligence | tinkering |
| Paladin | constitution | wisdom |

**Perk ladder (same for every class):** six perks, 2 starting + 4 earnable.

| Slot | Gate | Role | Magnitude band |
|---|---|---|---|
| 1 | primary ≥ 8 (**starting**, auto rank 1) | identity passive | small: +5–8 %, +1 attribute point, or a minor rule |
| 2 | secondary ≥ 8 (**starting**) | survival/utility passive | small |
| 3 | primary ≥ 16 | conditional combat bonus | +10–15 % or a proc with 6–10 s ICD |
| 4 | secondary ≥ 16 | sustain/utility | medium |
| 5 | primary ≥ 24 | build-defining conditional | +20–25 % conditional, or a strong proc |
| 6 | secondary ≥ 36 (above base cap, class-only) | capstone | a unique rule; survive-lethal perks share RS's `runicskills.survive_lethal` lockout |

Ids: `runicclasses:cls_<class>_<slug>`. Gate levels are literal against the class cap (40); all four earnable levels are server-config tunables.

**Power ladder:** school `runicclasses:<class>`, governing skill = primary. Gates at Ultima defaults: Mark 13; Seal 21 + Intelligence 10 + class Mark equipped; Crown 29 + total 180 + class Seal equipped. Marks have ≤ 8 s ICD, Seals 15–20 s, Crowns 60–120 s. Every power is a trigger → effect, never a flat stat (RS powers philosophy). "ISS" = Iron's Spells branch when `irons_spellbooks` is loaded; every caster power has a vanilla fallback.

**Bag scheme ("bland parity"):** every class bag = **Core** (identical) + **Arms** (one stone-tier item or equivalent, value 3) + **Kit** (≤ 8 units of a cheap commodity, value ≤ 2). Newcomer's Knapsack = Core + wooden sword (2) + wooden shield (3). Target value per bag **5 ± 1**; the generator asserts it from a small value table. No enchantments, no potions, no XP bottles, no armor beyond a single leather piece.

Core (Ultima override): `oak_log×12, bread×16, hardcore_torches:unlit_torch×8, survival_firesticks:firestick×1, plantfiber:grass_bandage×8, sophisticatedbackpacks:backpack×1, map_atlases:atlas×1, sereneseasons:calendar×1, waystones:warp_scroll×1`.
Core (RunicClasses vanilla default): `oak_log×12, bread×16, torch×8, string×4`.

### 3.1 Barbarian — strength / constitution — named mechanic: **Fury**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Bloodied Resolve | str 8 ★ | Below 50 % health your melee damage is +8 %. |
| 2 | Thick Hide | con 8 ★ | +2 max health. |
| 3 | Reckless Swing | str 16 | Sprint attacks deal +12 % and knock back 50 % further. |
| 4 | Second Wind | con 16 | Dropping below 30 % health grants Regeneration I for 6 s (60 s cooldown). |
| 5 | Unstoppable | str 24 | Killing a hostile grants Speed I and Strength I for 5 s (refreshes). |
| 6 | Titan's Fury | con 36 | Once per 5 min, survive lethal damage at 1 health and gain Strength II for 6 s (shared survive lockout). |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Fury Rising | Taking melee damage below 50 % health: your next melee hit within 4 s deals +20 %. | 6 s |
| Seal | Blood Roar | Killing a hostile below 50 % health: heal 10 % max health, knock back enemies within 3 blocks. | 15 s |
| Crown | Unbroken | A single hit for ≥ 30 % max health: Resistance II and Strength II for 6 s. | 60 s |

Bag: Core + `stone_axe` + `cooked_beef×6`.

### 3.2 Fighter — strength / endurance — named mechanic: **Drill**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Weapon Drill | str 8 ★ | +6 % melee damage with swords, axes and spears (`#minecraft:swords`, `#minecraft:axes`, Spartan weapon tags when present). |
| 2 | Quartermaster | end 8 ★ | Armor loses 10 % less durability (RS `repair_efficiency` / durability package). |
| 3 | Riposte | str 16 | After blocking with a shield, your next melee hit within 3 s deals +25 %. |
| 4 | Battle Rhythm | end 16 | Every third consecutive melee hit on the same target deals +15 %. |
| 5 | Armor Mastery | str 24 | +2 armor toughness while wearing a chestplate. |
| 6 | Champion | end 36 | Melee critical damage +25 % (RS `critical_damage`). |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Measured Cut | First melee hit after 5 s without attacking deals +30 %. | 8 s |
| Seal | Bulwark Call | Blocking an attack: Resistance I for 4 s to you and allies within 4 blocks. | 20 s |
| Crown | Last Stand | Health drops below 25 %: Absorption IV for 10 s; melee hits heal 1 health while it lasts. | 120 s |

Bag: Core + `stone_sword` + `spartanshields:wooden_basic_shield` (vanilla default: `stone_sword` + `cooked_beef×6`).

### 3.3 Monk — dexterity / endurance — named mechanic: **Breath**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Open Hand | dex 8 ★ | Unarmed attacks deal +3 damage. |
| 2 | Centred Breath | end 8 ★ | Fall damage −30 %. |
| 3 | Flowing Step | dex 16 | +10 % movement speed while wearing no iron-or-better chestplate. |
| 4 | Deflect | end 16 | 20 % chance to negate projectile damage while unarmed. |
| 5 | Flurry | dex 24 | +20 % attack speed while unarmed. |
| 6 | Stillness | end 36 | Standing still for 2 s grants Regeneration I until you move. |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Opening Palm | Unarmed hit on a target you have not hit in 5 s applies Weakness I for 3 s. | 6 s |
| Seal | Quivering Strike | Every fifth unarmed hit on one target deals bonus damage = 10 % of its max health (cap 20). | 12 s |
| Crown | Perfect Form | A hit for ≥ 40 % max health while wearing no iron-or-better armor is negated; Speed II for 4 s. | 90 s |

Bag: Core + `leather_boots` + `cooked_chicken×8`.

### 3.4 Rogue — dexterity / fortune — named mechanic: **Edgework**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Backstab | dex 8 ★ | Melee hits on targets facing away deal +15 %. |
| 2 | Light Fingers | for 8 ★ | +1 Luck. |
| 3 | Evasive | dex 16 | 10 % chance to dodge melee hits. |
| 4 | Treasure Sense | for 16 | Mob loot rolls with +1 Looting (`LootingLevelEvent`). |
| 5 | Assassinate | dex 24 | Hits from sneaking or invisibility on full-health targets deal +40 %. |
| 6 | Lucky Strike | for 36 | 15 % of your hits become critical hits. |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Opening Cut | First hit on a full-health target applies Glowing and Weakness I for 4 s. | 8 s |
| Seal | Vanish | Killing a hostile grants Invisibility 4 s and Speed I. | 20 s |
| Crown | Deathmark | Four hits on one target within 5 s detonate 25 % of the damage dealt in that window (cap 20). | 45 s |

Bag: Core + `stone_sword` + `locks:lockpick×4` (verify id with `validate.py`; vanilla default `string×8`).

### 3.5 Ranger — dexterity / wisdom — named mechanic: **The Long Watch**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Steady Draw | dex 8 ★ | +8 % projectile damage (RS `projectile_damage`). |
| 2 | Trailwise | wis 8 ★ | +10 % movement speed in forest, taiga and jungle biomes. |
| 3 | Hunter's Mark | dex 16 | Projectile hits make the target Glow for 6 s. |
| 4 | Field Dressing | wis 16 | +1 Looting against animals and arthropods. |
| 5 | Deadeye | dex 24 | Fully charged arrows deal +20 %. |
| 6 | Warden of the Wild | wis 36 | Your tamed animals deal +20 %; hostile animals and arthropods deal −20 % to you. |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Second Arrow | A projectile hit from ≥ 12 blocks fires a copy at 50 % damage. | 6 s |
| Seal | Pin | A fully charged shot roots the target (Slowness V, 2 s). | 15 s |
| Crown | Volley of the Watch | Three projectile hits within 4 s: the next arrow splits into three. | 40 s |

Bag: Core + `bow` + `arrow×16`.

### 3.6 Bard — intelligence / fortune — named mechanic: **Cadence**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Quick Study | int 8 ★ | Skill level-ups cost 5 % less XP (RS discount hook, §5.3). |
| 2 | Silver Tongue | for 8 ★ | Villager trades 10 % cheaper (special price on offer open; MCA villagers `OQ-12`). |
| 3 | Battle Hymn | int 16 | While in combat, you and allies within 8 blocks gain +8 % attack speed. |
| 4 | Fortune's Chord | for 16 | +1 Luck. |
| 5 | Countercharm | int 24 | Harmful effects on you and allies within 8 blocks last 30 % shorter. |
| 6 | Crescendo | for 36 | Every tenth hit by you or an ally within 8 blocks heals the group 2 health. |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Opening Verse | Your first hit after 10 s out of combat: Speed I 4 s to allies within 8 blocks. | 12 s |
| Seal | Dissonance | Striking a target: Weakness I and Slowness I 4 s to enemies within 4 blocks of it. | 20 s |
| Crown | Anthem | An ally within 10 blocks drops below 30 %: Absorption II and Regeneration II 6 s on them. | 90 s |

Bag: Core + `stone_sword` + `cookie×8`.

### 3.7 Cleric — wisdom / magic — named mechanic: **Rites**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Blessed Vitality | wis 8 ★ | Beneficial effects last +15 % (RS `beneficial_effect`). |
| 2 | Consecrated Weapon | mag 8 ★ | +15 % damage to undead. |
| 3 | Mending Touch | wis 16 | Healing potions you drink heal +25 %. |
| 4 | Turn the Dead | mag 16 | Undead that hit you have a 15 % chance to lose their target and gain Slowness II 3 s. |
| 5 | Sanctuary | wis 24 | Being healed while below 50 % grants Resistance I 3 s (10 s ICD). |
| 6 | Intercession | mag 36 | Once per 5 min, survive lethal damage at 50 % health (shared survive lockout). |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Radiant Burst | Killing an undead heals you 2 and allies within 4 blocks 1. | 5 s |
| Seal | Ward of Light | An ally within 8 blocks takes ≥ 20 % max health in one hit: Absorption I 5 s on them. | 15 s |
| Crown | Beacon | For 8 s after you heal an ally (ISS heal spell, or a splash/lingering healing potion you threw): allies within 8 blocks deal +15 % and gain Regeneration I. | 60 s |

Bag: Core + `spartanshields:wooden_basic_shield` + `plantfiber:grass_bandage×8` extra + `candle×2` (vanilla default: `stone_sword` + `string×8`).

### 3.8 Druid — magic / endurance — named mechanic: **The Green Covenant**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Bark Skin | mag 8 ★ | +1 armor while standing on grass, dirt, leaves or wood (block below). |
| 2 | Herb Craft | end 8 ★ | Crops, berries and fruit restore +1 hunger. |
| 3 | Thorns | mag 16 | 15 % chance that melee attackers take 2 damage. |
| 4 | Rooted | end 16 | Standing still on natural ground regenerates 1 health every 4 s. |
| 5 | Rain Blessed | mag 24 | Regeneration I in rain or water. |
| 6 | Lord of the Grove | end 36 | Every 10 s, crops within 4 blocks receive a growth tick. |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Vine Lash | Hitting a target while on natural ground: Slowness II 3 s. | 8 s |
| Seal | Thorned Mantle | A hit for ≥ 15 % max health: 4 damage and Poison I 3 s to attackers within 3 blocks. | 15 s |
| Crown | Heart of the Grove | Below 30 % health: Regeneration III 5 s on you; enemies within 5 blocks rooted (Slowness V 2 s). | 90 s |

Bag: Core + `stone_hoe` + `wheat_seeds×8` + `bone_meal×4`.

### 3.9 Sorcerer — magic / constitution — named mechanic: **Wild Vein**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Innate Reserves | mag 8 ★ | ISS: +10 % max mana. Fallback: beneficial effects last +10 %. |
| 2 | Hardy Vessel | con 8 ★ | +2 max health. |
| 3 | Surge | mag 16 | ISS: 10 % chance a cast costs no mana (`SpellPreCastEvent`/`ChangeManaEvent`). Fallback: 10 % chance a thrown potion is not consumed. |
| 4 | Arcane Skin | con 16 | +10 % magic resist (RS `magic_resist`). |
| 5 | Volatile Blood | mag 24 | Being hit below 50 % health: your next spell casts +1 level (`ModifySpellLevelEvent`) / fallback: next potion you throw gains +1 amplifier. |
| 6 | Overflow | con 36 | Magic damage you deal heals you 5 % of the damage. |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Sparkvein | Magic hit on a full-health enemy deals +15 %. | 6 s |
| Seal | Cascade | Killing with magic refunds 20 % max mana / fallback: Haste II 4 s. | 15 s |
| Crown | Wild Surge | Every sixth spell cast (fallback: sixth magic hit) triggers a random surge: lightning on the target, 4 health heal, or an AoE knockback. | 30 s |

Bag: Core + `stone_sword` + `redstone×8` + `glass_bottle×4`.

### 3.10 Warlock — magic / fortune — named mechanic: **The Bargain**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Dark Bargain | mag 8 ★ | Kills restore 1 hunger. |
| 2 | Night Sight | for 8 ★ | Immune to Blindness and Darkness. |
| 3 | Hex | mag 16 | Your first hit on a target applies Weakness I 4 s (8 s per-target ICD). |
| 4 | Soul Tithe | for 16 | Kills have a 15 % chance to drop an extra 3-point XP orb. |
| 5 | Lingering Curse | mag 24 | Harmful effects you apply last +30 %. |
| 6 | Pact of the Deep | for 36 | Below 30 % health your damage is +25 %. |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Soul Leech | Hitting a Weakened target heals you 1. | 3 s |
| Seal | Patron's Shadow | Killing a hostile: Blindness 3 s and Slowness I to enemies within 5 blocks. | 20 s |
| Crown | The Debt Comes Due | Dropping below 20 % health: 6 damage to every enemy within 8 blocks, heal 3 per enemy hit. | 120 s |

Bag: Core + `stone_sword` + `bone×8` + `black_candle×2`.

### 3.11 Wizard — intelligence / magic — named mechanic: **The Study**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Scholar's Eye | int 8 ★ | +1 enchanting power (RS `enchanting_power`). |
| 2 | Arcane Focus | mag 8 ★ | ISS: spell cooldowns −5 %. Fallback: +5 % XP (RS `xp_bonus`). |
| 3 | Spell Economy | int 16 | ISS: mana costs −10 %. Fallback: enchanting level costs −1 (RS `MixEnchantmentMenu` precedent). |
| 4 | Counter-Ward | mag 16 | +15 % magic resist. |
| 5 | Prodigy | int 24 | Intelligence and Magic level-ups cost 10 % less XP (discount hook). |
| 6 | Archmage | mag 36 | ISS: all spells cast +1 level. Fallback: magic damage +20 %. |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Studied Strike | Magic hit on a mob type you have killed before (per-player set, max 64 types) deals +15 %. | 4 s |
| Seal | Recitation | A spell kill refunds 50 % of that spell's cooldown / fallback: Haste II 5 s. | 20 s |
| Crown | Grand Working | Five casts within 15 s: the next spell is +2 levels and free / fallback: next magic hit deals double. | 60 s |

Bag: Core + `wooden_sword` + `book×3` + `lapis_lazuli×8`.

### 3.12 Artificer — intelligence / tinkering — named mechanic: **Infusions** (owner's template)

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Field Notes | int 8 ★ | +5 % XP (RS `xp_bonus`). |
| 2 | Tinker's Eye | tin 8 ★ | +10 % repair efficiency (RS `repair_efficiency`). |
| 3 | Infusion: Steady Hands | int 16 | +10 % crafting luck (RS `crafting_luck`). |
| 4 | Infusion: Keen Edge | tin 16 | Tools and weapons you craft have +5 % max durability (stamped at craft; RS `MixItemStack.getMaxDamage` precedent). |
| 5 | Infusion: Overclock | int 24 | +10 % mining speed (RS `break_speed`). |
| 6 | Master Infuser | tin 36 | Items you craft have a 10 % chance to receive Unbreaking I (Runic Races Forge Blessing precedent). |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Spring Trap | Hitting with a tool (pickaxe, axe, shovel, hoe) deals +20 %. | 6 s |
| Seal | Field Repair | Your held item drops below 10 % durability: it repairs 15 %. | 60 s |
| Crown | Arc Capacitor | Every eighth hit releases a lightning arc: 5 damage to up to 3 enemies within 5 blocks. | 20 s |

Bag: Core + `stone_pickaxe` + `copper_ingot×4` + `redstone×8`.

### 3.13 Paladin — constitution / wisdom — named mechanic: **The Oath**

| # | Perk | Gate | Effect |
|---|---|---|---|
| 1 | Shield of Faith | con 8 ★ | +1 armor. |
| 2 | Laying of Hands | wis 8 ★ | Sneak + use empty hand on an ally: heal them 4 (60 s cooldown). |
| 3 | Aura of Courage | con 16 | Allies within 6 blocks gain +1 armor. |
| 4 | Smite | wis 16 | Melee hits on undead and illagers deal +20 %. |
| 5 | Bulwark | con 24 | Blocking with a shield heals you 1 (3 s ICD). |
| 6 | Oathkeeper | wis 36 | Once per 5 min, an ally within 8 blocks who would die survives at 20 % health. |

| Tier | Power | Trigger → effect | ICD |
|---|---|---|---|
| Mark | Righteous Blow | First hit on an enemy that damaged an ally in the last 5 s deals +25 %. | 8 s |
| Seal | Aegis | Blocking three attacks within 5 s: Resistance I 4 s to allies within 6 blocks. | 20 s |
| Crown | Avatar of the Oath | Below 25 % health: Absorption III, and for 8 s your hits heal the nearest ally 2. | 120 s |

Bag: Core + `wooden_sword` + `spartanshields:wooden_basic_shield` + `plantfiber:grass_bandage×4` extra (vanilla default: `stone_sword` + `string×4`).

### 3.14 Classless

Origin `runicclasses:classless`: `powers: [ one display-only line "No class grants." ]`, impact 0. No stats, caps, perks, powers. Bag: `runicclasses:bags/classless` = the universal bag (Ultima override = exact Newcomer's Knapsack contents; vanilla default = Core + `wooden_sword`).

### 3.15 Lore rewrite — voice guidance and exemplars

The thirteen entries in `docs/classes.md` are paraphrased from D&D source text and are replaced wholesale. The Artificer entry (owner-written) is the template: present-tense gameplay language, a **named mechanic in bold**, a **named primary Aptitude in bold**, 90–130 words, third person plural or "An X is…".

Rules:
1. **Purge list** (never appears in shipped text): multiverse, Outer Planes, Far Realm, planes of existence, archfey, hags, Words of Creation, Rage/Ki as capitalised feature names, Bigby, Tasha, Mordenkainen, Yolande, Bag of Holding, "Divine Smite", "Channel Divinity", "fighting style" as a system, subclass names (Oath of…, Circle of…, Pact of the…). Subclass ideas may survive only as unnamed flavour ("some swear their oath at an altar, some in a storm").
2. **Use the suite's own nouns**: Aptitudes (Strength … Tinkering), Runic Marks / Seals / Crowns, Perks, runes. Working assumption (OQ-14 answered "go with reasonable assumptions"): class lore uses **only** this suite-generic vocabulary and names no Ultima lore-book proper nouns (Towers, Concord, Soul Forge, regional histories), so the classes read correctly in any pack; the owner can add setting references in review.
3. Each entry names: the class's named mechanic, its two Aptitudes (primary first), one sentence of what the Mark/Seal/Crown ladder feels like, and one sentence of role in a party. No numbers in lore; numbers live in tooltips.
4. Purely positive tone: no "weak to", no "cannot". Races own the costs.
5. Lang keys: `origin.runicclasses.<class>.name` ("Fighter"), `.description` (the entry), plus three `power.runicclasses.display_<class>_<n>.name/.description` display lines.

Exemplar — Fighter:

> A **Fighter** is the soldier the rest of the company stands behind. Their craft is **Drill**: the thousand repetitions that turn a sword, an axe or a spear into an extension of the arm, and a shield into a wall that answers back. Fighters run on **Strength** first and **Endurance** second; their Runic Mark rewards the patient first cut, their Seal turns a raised shield into cover for everyone beside them, and their Crown is the last stand that refuses to end a fight on anyone's terms but theirs. Knights, caravan guards and sellswords all drill the same forms. What they do with the forms is their own business.

Exemplar — Rogue:

> A **Rogue** wins before the other side knows a fight has started. Their craft is **Edgework**: the precise placement of one blade, one lockpick, one quiet step. Rogues run on **Dexterity** first and **Fortune** second; their Runic Mark opens a fight with a cut that marks the target, their Seal lets a kill fold them back into the dark, and their Crown collects on every wound they have opened at once. Some work the alleys of a city, some the halls beneath a ruin. All of them prefer one perfect strike to a hundred honest ones.

Exemplar — Cleric:

> A **Cleric** carries the **Rites**: the spoken forms through which a chosen power reaches into the world to mend, to ward and to break what should not walk. Clerics run on **Wisdom** first and **Magic** second; their Runic Mark turns a fallen undead into light for their companions, their Seal shields an ally the moment a blow lands too hard, and their Crown makes every healing a rallying call. Temples train them, but the Rites answer anyone who keeps them faithfully, altar or no altar.

The remaining ten entries are written in the implementation phase against these three and the Artificer template, then reviewed by the owner before the lang file ships.

---

## 4. Balance framework

**Equal budget per class.** Every class receives exactly: 2 × 7 free levels (1 → 8) on its two stats; 2 × +8 cap (32 → 40); 6 perks on the fixed ladder (§3.0 magnitude bands); 3 powers, one per tier, with ICD bands Mark ≤ 8 s / Seal 15–20 s / Crown 60–120 s; one starter bag of value 5 ± 1. No class gets a fourth power, a seventh perk, a third stat or a bigger bag. A `ClassBudgetTest` (JUnit over the committed class JSON) asserts counts and gate levels; magnitudes are reviewed against the band table by hand and recorded in the class JSON as tunables.

**Purely positive.** No class perk or power may: reduce a stat, add a vulnerability, add a cost (hunger, durability, mana) beyond vanilla, or gate an existing Runic Skills perk/power away from the member. Conditional bonuses ("while unarmed", "below 50 % health") are allowed because the baseline without the condition is unchanged vanilla. Races keep every drawback (Runic Races' one-weakness-per-race rule stays untouched).

**Interaction rules with Runic Skills content (`OQ-15` to confirm):** class perk bonuses stack additively with RS perks on the same quantity (e.g., Fighter Weapon Drill +6 % and RS One Handed are separate additive terms, never multiplicative); survive-lethal class perks share RS's `runicskills.survive_lethal` lockout so a player cannot chain rescues; class powers obey the RS school prerequisite chain and the Power-Point budget; attribute-modifier perks use fixed UUIDs under the RunicClasses namespace and are reconciled with the RS attribute pass so a swap leaves no stale modifier.

**Stat collisions with races:** Runic Races never touches Runic Skills levels (no `runicskills` reference in its source), so there is no level-layer collision. Attribute-layer overlap is additive (a Valen's fortitude plus a Barbarian's Thick Hide both raise max health); `OQ-16` asks whether any combined ceiling is wanted.

**Progression economics.** Free class levels are excluded from the purchasable global budget (hook) so a class member keeps the same 246 purchasable levels as a Classless player; the +16 cap headroom is purchasable inside the same budget, so the class shapes *where* a player specialises rather than *how much*. Class Marks arrive at primary 13 (five purchases from the start), Seals at 21 + Intelligence 10, Crowns at 29 + 180 total — identical to any RS power, so a class does not accelerate the power ladder; it adds three exclusive rungs.

**Bag parity.** Value table (generator constant): stone tool 3, bow 3, wooden shield 3, leather piece 3, wooden sword 2, cheap commodity stack (≤ 8 units of wood/stone/plant/animal/redstone-tier items) 1, copper/lapis/bone-meal stack 2. Every bag = Core + 5 ± 1. Nothing enchanted, no potions, no XP items, no armor set, no mod item above "village" grade. `validate.py` rejects unknown ids; a new `--parity` switch (added in the pack generator) rejects a bag outside the band.

---

## 5. Build order and task list

Flagship first (Fighter, Rogue, Cleric, Artificer) to prove: melee perks + shield-based power (Fighter), stealth/loot hooks (Rogue), Iron's Spells branch + healing detection (Cleric), tool/crafting hooks + Tinkering governance (Artificer). Then the remaining nine as an assembly line driven by the generator.

Iteration command (owner's global rule): `/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <dir> compileJava`; `check_mod.py <project>` for assets/lang; `find_api.py --class X --symbol y` before relying on any uncertain Origins/Apoli/Treasure Bags symbol (notably the Apoli display-power type id and the Treasure Bags `BagType` NBT key).

### 5.1 Phase 0 — Runic Skills 2.4.0 hooks (prerequisite; RunicClasses compiles against this jar)

Repository `runic-skills/`. New files **(N)**, edits **(E)**.

| File | Change |
|---|---|
| `src/main/java/com/otectus/runicskills/common/progression/SkillCapHooks.java` **(N)** | `skillCapBonus`, `globalCapBonus`, `earnedLevelAdjust` volatile hooks (no-op defaults); `skillCap(player, skill)`, `globalCap(player)`. |
| `common/capability/SkillCapability.java` **(E)** `:398-400` | Add `addSkillLevel(Player, Skill, int)` clamping to `SkillCapHooks.skillCap`; keep the old overload clamping to config for callers without a player. `getEarnedGlobalLevelForPerkBudget` subtracts `earnedLevelAdjust`. |
| `common/progression/ProgressionService.java` **(E)** | `Cause.CLASS`; `clampForPlayer(player, skill, requested)` used in `setSkillLevel`; global-cap check uses `SkillCapHooks.globalCap(player)`; `CLASS` bypasses the global check like `COMMAND`. |
| `network/packet/common/SkillLevelUpSP.java` **(E)** `:84-101`, `requiredPoints` | Per-player skill and global caps; add `ProgressionHooks.levelUpDiscount` (`ToDoubleBiFunction<Player, Skill>`, default 0) folded into the existing discount sum. |
| `common/progression/ProgressionHooks.java` **(E)** | The `levelUpDiscount` field. |
| `registry/ContentGateHooks.java` **(N)** | `perkAvailable`, `powerAvailable` `BiPredicate`s, default `true`. |
| `registry/perks/Perk.java` **(E)** | `isEnabled(player)`, `isEnabled()`, `getToggle(..)`, `canPerk(..)` return false when `!perkAvailable`. |
| `network/packet/common/TogglePerkSP.java` **(E)** | After the disabled check: reject enable/rank-up when `!perkAvailable`. Disable stays allowed. |
| `client/screen/RunicSkillsScreen.java` **(E)** `:738-744`, `:335`, `:378`, `:516`, `:528`, `:1031` | Perk list filter; effective cap for text, pulse frame, level-up button and click guard. |
| `registry/skill/Skill.java` **(E)** `:91`, `:116` | Rank band / locked icon against `SkillCapHooks.skillCap` where a player is known (cosmetic). |
| `registry/powers/PowerEligibility.java` **(E)** | `Reason.CLASS_RESTRICTED`; check `ContentGateHooks.powerAvailable` in `evaluateActive` right after `PowerAvailability`. |
| `client/screen/PowersScreen.java` **(E)** `:101-106` | `filterHidden` also removes `!powerAvailable` unless equipped. |
| `registry/RegistryPowers.java` **(E)** `byId` | Resolve any namespace through `getPower(path)`. |
| `client/vfx/*` **(E, verify)** | Unknown school id → default glyph/colour instead of a dropped proc. |
| `event/RespecEvent.java` **(N)**; `common/command/RespecCommand.java` **(E)** | Post `RespecEvent(player)` after the reset, before `SyncSkillCapabilityCP`. |
| `src/main/resources/assets/runicskills/lang/en_us.json` **(E)** | `power.runicskills.denied.class_restricted`. |
| `src/gametest/java/.../SkillCapHooksGameTest.java`, `ContentGateHooksGameTest.java`, `RespecEventGameTest.java` **(N)** | Install a test predicate/bonus, assert purchase clamp, perk toggle rejection, power denial reason, event firing. |
| `docs/API_EVENTS.md`, `docs/KUBEJS.md` **(E)** | New event and the `class` cause value (these docs enumerate causes and events, so they must stay truthful). |
| `gradle.properties` **(E)**, `CHANGELOG.md` **(E)**, `MODMAP.md` (regen above `AUTO:END`) | Version 2.4.0. |

Verification: `gradlew-quiet.sh runic-skills compileJava`, `gradlew-quiet.sh runic-skills check` (includes `checkMixinInventory`, `checkSidedImports`, `checkMixinRemapping`), `./gradlew runGameTestServer` for the three new tests, then `build` → `build/libs/runicskills-2.4.0.jar` (jarJar'd full jar) for RunicClasses' `libs/`.

### 5.2 Phase 1 — RunicClasses skeleton and selection

Repository `RunicClasses/` (initialise git; bootstrap with the `bootstrap-project` skill once `build.gradle` exists).

| File | Content |
|---|---|
| `settings.gradle`, `build.gradle`, `gradle.properties`, `gradlew*`, `gradle/` | Copy Runic Races' Gradle shape (`runic-races/build.gradle`: FG 6.0.54 → pin **6.0.24** like RS, `flatDir Dependencies/`, `local` Ivy for `fg.deobf` runtime jars). Add `libs/runicskills-2.4.0.jar` via an Ivy `local.mods` repo (RS `build.gradle:367-371` pattern) as `compileOnly fg.deobf` + `runtimeOnly fg.deobf`; Origins/Apoli/Calio from `Dependencies/`; `treasurebags`, `silent-lib`, `irons_spellbooks` `compileOnly` from `Dependencies/` (copied from the instance `mods/`). Parchment mappings. GameTest source set. |
| `tools/sync_runicskills_lib.py` | Copies `../runic-skills/build/libs/runicskills-<v>.jar` into `libs/` (mirrors RS `tools/sync_immersive_libs.py`). |
| `src/main/resources/META-INF/mods.toml`, `pack.mcmeta` | Dependencies per §2.1. |
| `RunicClasses.java` | Mod class: registries, configs, network, reload listener, hook installation (`integration/runicskills/RunicSkillsHooksInstaller`), Forge-bus handlers. |
| `data/ClassIds.java` | `CLASS_LAYER = runicclasses:class`, `CLASSLESS = runicclasses:classless`, `EMPTY_ORIGIN = origins:empty`. |
| `data/ClassDefinition.java`, `ClassDefinitionLoader.java`, `ClassDefinitionValidator.java`, `ClassDataManager.java` | §2.2 schema, codec, fail-closed snapshot, owner maps. |
| `state/ClassStateCapability.java`, `state/ClassStateProvider.java`, `state/RCCapabilities.java` | §2.4. |
| `service/ClassHelper.java`, `service/ClassReconciler.java`, `service/ClassGrantService.java`, `service/RepickService.java` | §2.5 (stats and caps only in this phase). |
| `event/ClassLifecycleHandler.java` | Login (`LOW` priority), respawn, clone, logout, tick memo. |
| `network/RCNetwork.java`, `S2CClassStateSync.java` | §2.10 (screen opening reuses Origins' `S2COpenOriginScreen`). |
| `registry/RCItems.java`, `item/ClassOrbItem.java` | The Class Orb (§2.3): use-guard, clear class layer, Origins open-screen packet, consume unless creative, cooldown. Creative-tab entry via `BuildCreativeModeTabContentsEvent` (tools tab). |
| `data/runicclasses/recipes/class_orb.json`; `assets/runicclasses/models/item/class_orb.json`, `textures/item/class_orb.png`; lang `item.runicclasses.class_orb` | Recipe per `OQ-19`; 16×16 orb texture in the class colour family. |
| `command/RCCommands.java` | `choose` (op), `info`, `set`, `reset`, `validate`, `diagnostics` (bag commands in Phase 2). |
| `client/RCClient.java`, `client/ClientHooksInstaller.java` | Client-side hook install (cap display, perk/power filters). |
| `config/RCServerConfig.java`, `config/RCCommonConfig.java` | §2.10. |
| `data/runicclasses/origin_layers/class.json`; `origins/classless.json`, `origins/fighter.json`; `powers/display_fighter_{1,2,3}.json`, `powers/display_classless_1.json`; `classes/classless.json`, `classes/fighter.json` | First two origins. |
| `assets/runicclasses/lang/en_us.json` | Layer, origin, display power keys for Fighter and Classless. |
| `tools/generate_classes.py`, `tools/class_lang.json` | Generator emitting all of the above from one source (Fighter + Classless first). |
| `CLAUDE.md`, `MODMAP.md` | Via `bootstrap-project` after first compile. |

Verification: `compileJava`; `runClient` → new world: heritage → race → **class** screen appears automatically; pick Fighter → Skills screen shows Strength 8/40 and Endurance 8/40; use a Class Orb (`/give @s runicclasses:class_orb`) → the class screen reopens, the orb is consumed in survival and kept in creative, stats drop to 1/32 while Classless; pick Fighter again → 8/40 (no bag yet); `/respec` → stats return to 8 within a second; relog, death and dimension change keep the state. `check_mod.py RunicClasses` clean.

### 5.3 Phase 2 — flagship kits (Fighter, Rogue, Cleric, Artificer) and the bag

| File | Content |
|---|---|
| `registry/RCPerks.java` | DeferredRegister on `RegistryPerks.PERKS_KEY`; 24 flagship perks (public `Perk` ctor, config-fed `Value`s, textures), `refreshFromConfig()`. |
| `registry/RCPowers.java` | DeferredRegister on `RegistryPowers.POWERS_KEY`; 12 flagship powers (public `Power` ctor, school `runicclasses:<class>`, gate from `PowerEligibility.governingSkillRequirement(tier)`). |
| `perk/PerkAttributes.java` | Attribute-modifier perks (fixed UUIDs), applied/removed on `PerkToggleEvent.Post`, login, respawn, class swap. |
| `perk/handlers/FighterPerks.java`, `RoguePerks.java`, `ClericPerks.java`, `ArtificerPerks.java` | Event-driven perk effects (`LivingHurtEvent`, `LivingDamageEvent`, `ShieldBlockEvent`, `LootingLevelEvent`, `LivingDeathEvent`, `PlayerEvent.ItemCraftedEvent`, `LivingEntityUseItemEvent.Finish`, `MobEffectEvent.Added`). |
| `power/handlers/FighterPowers.java`, `RoguePowers.java`, `ClericPowers.java`, `ArtificerPowers.java` | `PowerDispatch.isEquipped` / `checkAndStartCooldown` / `fireProc`; Cleric ISS branch in `integration/irons/IronsSpellsBridge.java` (reflection + `ModList.isLoaded`). |
| `service/StarterBagService.java`, `integration/treasurebags/TreasureBagsBridge.java` | §2.9 grant, migration rule, bag item construction (verify NBT key via `find_api.py --class TreasureBagItem`). |
| `data/runicclasses/loot_tables/bags/{fighter,rogue,cleric,artificer,classless}.json`, `treasurebags_types/*_kit.json` | Vanilla-safe defaults. |
| `assets/runicclasses/textures/perk/*.png` (24), `textures/power/*.png` (12) | 16×16, same art direction as RS icongen (school colour = class colour, tally cuts for tier). |
| `command/RCCommands.java` **(E)** | `bag give`. |
| `client/SkillsScreenClassBadge.java` | Class badge injected into `RunicSkillsScreen` via `ScreenEvent.Init.Post` (tooltip names the Class Orb). |
| `src/gametest/java/...`: `ClassGrantGameTest`, `ClassSwapGameTest`, `PerkVisibilityGameTest`, `PowerLadderGameTest`, `StarterBagGameTest`, `ClassOrbGameTest`, `RegistryUniquenessGameTest` | Grant/undo arithmetic incl. the level-40 edge; non-member cannot see/toggle class perks; Seal requires class Mark; bag granted once, migration flag honoured, Classless bag uses up the grant; orb refused mid-selection, consumed in survival, cooldown respected. |

Verification: all GameTests green; `runClient` manual pass: Fighter sees 6 perks under Strength/Endurance, Rogue does not; Fighter Mark equippable at Strength 13 (use `/skills <me> strength set 13`), Seal denied until Mark equipped and Intelligence 10; proc rune shows for class powers; bag appears once on first confirm and never again across re-picks; `/treasurebags give @s runicclasses:fighter_kit` opens to the vanilla default contents.

### 5.4 Phase 3 — assembly line (remaining nine classes)

Add the nine entries to `tools/generate_classes.py`; regenerate origins, class JSON, lang; extend `RCPerks`/`RCPowers` (54 perks, 27 powers), `perk/handlers/<Class>Perks.java` ×9, `power/handlers/<Class>Powers.java` ×9, caster branches in `IronsSpellsBridge` (Sorcerer, Warlock, Wizard, Druid), bag tables ×9, icons (54 + 27). Lore for all ten remaining classes written to the §3.15 rules and sent to the owner for review before the lang file is final. `ClassBudgetTest` and `ClassDataParityTest` run in `./gradlew test`.

### 5.5 Phase 4 — Ultima integration

Instance root `/home/otectus/Documents/curseforge/minecraft/Instances/Ultima/`.

| File | Change |
|---|---|
| `tools/treasurebags/generate.py` `:317-320` | `starting_inventory.json` keeps `type`/`random_sequence` but `"pools": []`. |
| `tools/treasurebags/tbdsl.py`, `generate.py` | Allow a target namespace other than `ultima` for a `Single` (bag type id and loot-table id written under `kubejs/data/runicclasses/...`); add `--parity` value check (§4). |
| `tools/treasurebags/catalog/class_kits.py` **(N)** | 14 overrides (`runicclasses:bags/<class>`, `runicclasses:<class>_kit`) with the Ultima Core and the §3 Arms/Kit entries; `classless` = the Newcomer's Knapsack list from `starter.py`. |
| `tools/treasurebags/validate.py` | Include the RunicClasses jar in the registry scan; run after regeneration. |
| `kubejs/server_scripts/ultima_selection_bridge.js` | `usbLoadApi`: load `com.otectus.runicclasses.data.ClassIds`; `usbObserve`: read `container.getOrigin(ClassIds.CLASS_LAYER)`, `complete` also requires a chosen class; `usbReady`/`usbSendStatus`/`usbStatusSignature`/`usbHandleAck`: carry and compare `class`. |
| `kubejs/client_scripts/ultima_selection_bridge.js` | `usbClientCachesReady`: compare the class layer; `usbClientMaybeOpenOrigins` already opens every pending layer. |
| `mods/` | Replace `runicskills-2.2.1.jar` with `runicskills-2.4.0.jar`; add `runicclasses-<v>.jar`. |
| `config/origins-common.toml` | Auto-populated on first run with the 14 class origins and their display powers; confirm `enabled = true`. |
| Quest/FTB data (`config/ftbquests`, `kubejs/data/ultima/mcaquests`) | grep `newcomer` / `starting_inventory` / `treasure_bag` before shipping; any quest that expects the bag at login moves to "after choosing a class". |
| Orbs | Origins ships no `orb_of_origin` recipe and the pack adds none, so nothing to remove; optionally hide `origins:orb_of_origin` from JEI (`JEIEvents.hideItems`). The Heritage Orb and Class Orb recipes come from their mods; override here only if the cost should be pack-specific. |
| `mods/` | Also replace `runic_races-1.7.2.jar` with `runic_races-1.7.3.jar` (Heritage Orb). |

Verification: `python3 tools/treasurebags/generate.py && python3 tools/treasurebags/validate.py --strict --parity`; `/reload`; fresh profile: heritage → race → class → MCA Destiny opens only after the class confirm; bag arrives once at class confirm; existing-player profile: class screen prompts at login, no second bag (Silent Lib flag migration). Record the run the way `MCAQuests/tools/packtest/ultima_pack_test.py` does if a pack-test harness is wanted for this suite (`OQ-17`).

### 5.6 Phase 5 — hardening and release

RunicClasses `CLAUDE.md`/`MODMAP.md` regenerated; `README.md` with the class table; `check_mod.py` clean; full `./gradlew build` for RunicClasses, RS and RR; GameTests green in all three; CHANGELOGs.

**Runic Races 1.7.3 — Heritage Orb** (the only Runic Races code change; can run in parallel with Phase 1):

| File | Change |
|---|---|
| `src/main/java/com/otectus/runic_races/registry/ModItems.java` **(E)** | Register `heritage_orb` beside the icon items. |
| `item/HeritageOrbItem.java` **(N)** | `use`: refuse unless family **and** race are set (mid-selection guard, same rule as `C2SBackToFamilyPacket`); set both layers to `RaceHelper.EMPTY_ORIGIN`; `RaceHelper.invalidate(uuid)`; `synchronize()`; send `S2COpenOriginScreen(false)` via `OriginsCommon.CHANNEL`; shrink unless creative. Leaves every other layer alone. |
| `data/runic_races/recipes/heritage_orb.json` **(N)**; `assets/runic_races/models/item/heritage_orb.json`, `textures/item/heritage_orb.png`, lang `item.runic_races.heritage_orb` | Recipe per `OQ-19`. |
| `client/OriginBackButtonHandler.java` **(verify)** | The Back button already appears on the race layer when re-opened by the orb; confirm the `OPEN_NEXT_LAYER` reset still holds with a third layer present. |
| `src/gameTest/java/.../HeritageOrbGameTest.java` **(N)** | Orb refused mid-selection; clears exactly two layers; class layer (when present) untouched. |
| `gradle.properties`, `CHANGELOG.md`, `CLAUDE.md` | 1.7.3; note that `runicclasses:class` sits at order 20 above the race layer. |

---

## 6. Risks

| Risk | Mitigation |
|---|---|
| Path collision in RS's path-keyed caches throws at startup (`toUnmodifiableMap`). | `cls_` prefix; `RegistryUniquenessGameTest` lists both registries and asserts no duplicate paths. |
| `Perk.isEnabled` hot path slows combat. | O(1) owner-map lookup with null fast path; per-tick class memo. Measure with the existing RS proc throttle logs. |
| MCA Destiny screen replaces the class screen (bridge release fires on race completion). | Phase 4 bridge change; test on a fresh profile before shipping. |
| Origins' Orb of Origin (operator-only; no recipe) empties every layer at once. | Players get the divided Heritage Orb and Class Orb; the reconciler treats an empty class layer as Classless and Origins re-prompts, so an operator reset still converges. |
| Existing players receive a second bag or none. | Silent Lib flag migration rule; GameTest with a pre-seeded persisted tag. |
| Loot tables with pack-only item ids fail to load in other packs. | RunicClasses ships vanilla-only defaults; pack-specific contents live only in Ultima's override, validated by `validate.py`. |
| Treasure Bags absent. | Direct item grant fallback from the same loot table. |
| Iron's Spells absent or API moves (3.16.3 pinned). | Reflection + `ModList` guards and vanilla fallbacks for every caster perk/power; compile against the same CurseForge file RS uses (`8680180`). |
| Config reload leaves class perk tunables stale. | `RCPerks.refreshFromConfig` on `ModConfigEvent.Reloading`, same contract as RS. |
| Global budget arithmetic drifts (free levels counted twice). | `globalCapBonus` and `earnedLevelAdjust` derive from the **applied** numbers in the capability, not the current definition. |
| Forge registers RunicClasses' DeferredRegister into `runicskills:perks` before RS creates the registry. | RS declares the registry in its mod constructor via `makeRegistry`; `RegisterEvent` for custom registries fires after all `NewRegistryEvent` handlers, and `mods.toml` orders RunicClasses `AFTER runicskills`. Verified by the uniqueness GameTest listing 78 ids. |

---

## 7. Open questions for the owner

| Id | Question | Recommendation |
|---|---|---|
| OQ-1 | Re-pick screen closed with Escape: stay Classless until `/runicclasses choose` again (Origins re-prompts at next login), or restore the previous class? | Stay Classless; simplest and matches the Orb/empty-layer path. |
| OQ-2 | Do the 14 free class levels extend the global budget (so members keep 246 purchasable levels), and are they excluded from the "earned" level that scales Power Points and the optional perk cap? | Yes to both. |
| OQ-3 | On swap, a stat at exactly 40 loses one purchased level to the base cap (40 − 7 = 33 → 32). Refund its XP cost, or accept? | Accept; document in the re-pick confirmation text. |
| OQ-4 | Should power tier gates and `ScaledRequirement` scale with the raised cap for members (Mark at 16 instead of 13)? | No; keep gates on the configured cap so a class never slows its own ladder. |
| OQ-5 | Auto-equip the class Mark into a free Mark slot when the class is first confirmed? | No; slots and Power Points are the player's budget. |
| OQ-6 | In packs with `maxActivePerks > 0`, are the two starting class perks exempt from the count? | **Resolved: no exemptions** (owner, applied from the same answer as OQ-7). |
| OQ-7 | Class Seals: keep the Intelligence ≥ 10 secondary gate (odd for Barbarian/Fighter), or exempt class powers via a hook? | **Resolved: no exemptions.** Every RS gate applies to class powers. |
| OQ-8 | Dedicated extra slots for class powers (a 6th Mark / 4th Seal / 2nd Crown reserved for the class) instead of competing in the normal 5/3/1? | No for v1; revisit after play data. |
| OQ-9 | A player whose first confirmation is Classless receives the universal bag and the flag is set; later picking a class grants nothing. Keep, or defer the grant until a real class is chosen? | **Resolved: Classless uses up the grant.** |
| OQ-10 | Represent the class as a Runic Skills Title (`runicskills.titles.json5`) too? | Optional cosmetic; not in v1. |
| OQ-11 | Building is the only Aptitude no class uses (Artificer follows the owner's template: Intelligence + Tinkering). Accept, or re-map Artificer to Tinkering + Building? | **Resolved: accepted.** Building stays a universal utility tree. |
| OQ-12 | Bard "Silver Tongue" on MCA villagers: MCA replaces vanilla trading; apply only to vanilla merchants, or skip the perk for a different Fortune-8 perk? | Vanilla merchants only; confirm MCA trading path is out of scope. |
| OQ-13 | Is Origins' Orb of Origin craftable in Ultima? If yes, it resets heritage, race and class together. | **Resolved by decision:** divided orbs — Heritage Orb (Runic Races) and Class Orb (RunicClasses). Origins ships no orb recipe, so the all-layer orb stays operator-only. |
| OQ-14 | Which Ultima lore-book vocabulary is canon enough to appear in class lore? | **Resolved by assumption** (owner delegated): class lore uses only suite-generic vocabulary and no Ultima lore-book proper nouns; setting references can be added in review. |
| OQ-15 | Stacking policy: additive with RS perks on the same quantity, shared survive-lethal lockout, class powers inside the RS chain and budget (§4). Confirm. | As stated. |
| OQ-16 | Any ceiling on race + class attribute stacking (e.g., combined max-health bonus)? | None for v1. |
| OQ-17 | Should the suite get a pack-test harness like `MCAQuests/tools/packtest/ultima_pack_test.py` for the heritage → race → class → Destiny flow? | Yes if the release cadence continues; otherwise manual checklist in §5.5. |
| OQ-18 | Existing players on the live pack: at next login Origins prompts them for a class (their `hasAllOrigins()` becomes false) and they get no second bag. Confirm this is the desired migration. | Confirm. |
| OQ-19 | Orb recipes and consumption. Proposed: both orbs are consumed on use unless creative (Origins' behaviour); Class Orb = 4 amethyst shards + 1 ender pearl + 4 lapis around a book (mid-game cheap, no diamonds, so a re-pick is a deliberate but not punishing choice); Heritage Orb = the same with an ender eye in place of the pearl (rarer, since it also re-rolls the Townstead body). Should re-pick instead be free-of-cost at a block, or limited per player? | As proposed; costs are datapack-overridable in Ultima. |

**Resolved on 2026-10-02 (owner):** OQ-6 no exemptions, OQ-7 no exemptions, OQ-9 Classless uses up the bag grant, OQ-11 Building stays unused, OQ-13 replaced by the divided Heritage Orb / Class Orb decision, OQ-14 answered by assumption (suite-generic lore vocabulary only). Still open with recommendations as defaults for the implementation phase: OQ-1, 2, 3, 4, 5, 8, 10, 12, 15, 16, 17, 18, 19.

Decisions already taken in this plan that the owner may still overturn: class layer order 20; `Cause.CLASS`; hooks live inside Runic Skills (no API jar); starter bag granted at class confirmation by RunicClasses, with the pack's login grant emptied; perk ladder 8/8/16/16/24/36; powers reuse RS gates and share RS slots; Classless is an origin in the layer; re-pick is item-gated through the Class Orb, with the Heritage Orb as Runic Races' only code change.

## 8. Implementation notes (2026-10-02)

Appended after implementation; sections 1–7 are unchanged.

- **Built:** Runic Skills 2.4.0, RunicClasses 0.1.0 (13 classes and Classless), Runic Races 1.7.3 (Heritage Orb) and the Ultima changes in §5.5. Nothing is committed.
- **Verified:** builds and unit tests in the three repositories; GameTests all pass (Runic Skills 287, RunicClasses 28, Runic Races 35); Ultima `generate.py`, then `validate.py --parity` with 0 errors. `--strict` still fails on four optional-entity tag warnings that predate this work.
- **Not verified:** a client launch and the §5.5 in-game checklist (fresh profile; existing profile).
- **Open-question defaults applied:** OQ-1, 2, 3, 4, 5, 8, 10, 12, 15, 16, 17, 18 and 19, as recommended. OQ-15: a class damage percentage is taken on the hit as it was before other listeners scaled it and is added on top, so it adds to Runic Skills' percentages. Class damage reductions still multiply, so stacked reductions cannot reach immunity. OQ-19: each orb uses 3 lapis, not 4, because the proposed 10 ingredients do not fit a 3×3 grid.
- **Deviations:** the Rogue kit has `locks:wood_lock_pick×4`, because `locks:lockpick` does not exist. The Warlock capstone is displayed as "Desperate Terms", because "Pact of the…" is on the §3.15 purge list; its id is unchanged. Ultima's Classless kit keeps the name "Newcomer's Knapsack", because the mod's default name, "Wayfarer's Knapsack", would read as one of the pack's Wayfarer bags. The selection bridge's field is `classOrigin` (`class` is a reserved word in JavaScript), and its protocol is now 2.

### Naming cleanup (2026-10-02)

The following shipped names supersede the original names in sections 1–7; mechanics and balance are unchanged. Registry ids, language keys, runtime references and icon paths use the new snake_case names, including the Paladin mob effect.

| Class / kind | Original name | Shipped name | New id path |
|---|---|---|---|
| Paladin Perk | Aura of Courage | Valiant Presence | `cls_paladin_valiant_presence` |
| Bard Perk | Countercharm | Hexbreaking Hymn | `cls_bard_hexbreaking_hymn` |
| Ranger Perk | Hunter's Mark | Quarry Brand | `cls_ranger_quarry_brand` |
| Barbarian Perk | Second Wind | Stubborn Renewal | `cls_barbarian_stubborn_renewal` |
| Paladin Perk | Shield of Faith | Devout Bulwark | `cls_paladin_devout_bulwark` |
| Sorcerer Crown Power | Wild Surge | Arcane Upwelling | `cls_sorcerer_arcane_upwelling` |

Replacement names were sanity-checked against D&D 5e naming and exact-phrase web searches; no official feature/spell match was found. Runic Skills and Runic Races contain no references to these class ids. Runic Skills has an independent `runicskills:second_wind` perk, left unchanged.

- **Rename validation:** clean build and all 3 JUnit tests passed; all 28 required GameTests passed. Class data parity (100 outputs), icon parity and `check_mod.py` passed. The packaged jar contains none of the six old id fragments.
- **Delivery:** replaced Ultima `mods/runicclasses-0.1.0.jar`; displaced jar preserved in `mods-backup-20261002-pre-class-renames-165745/`. New SHA-256: `1cfac16d79fb1a70338333e9e582f14eae6558549596b38432ab07185db331ef`. Rebuilt the treasure-bag registry, regenerated pack data and passed `validate.py --parity` (0 errors; the same four optional-entity warnings). No client launch was performed.
- **Additional name matches, reported only:** Fighter Riposte, Rogue Assassinate, Bard Silver Tongue, Cleric Sanctuary, Warlock Hex, and Rogue Seal Vanish also match D&D 5e features/spells. Wizard Prodigy matches a feat. Broader vocabulary overlap includes Fighter Champion (subclass), Wizard Arcane Focus (equipment) and Archmage (monster); Rogue Treasure Sense and Druid Mark Vine Lash match monster abilities in SRD 5.2. Bard Quick Study also appears as a feat benefit in the official Expert Classes playtest. These names were not changed. This is a sanity audit of public rules and targeted searches, not an exhaustive certification across every supplement.
- **Naming references:** [2014 classes](https://www.dndbeyond.com/sources/dnd/basic-rules-2014/classes), [2014 spells](https://www.dndbeyond.com/sources/dnd/basic-rules-2014/spells), [Sage Advice](https://www.dndbeyond.com/sources/dnd/sac/sage-advice-compendium), [College of Eloquence](https://www.dndbeyond.com/posts/864-bard-101-college-of-eloquence), [SRD 5.2](https://media.dndbeyond.com/compendium-images/srd/5.2/SRD_CC_v5.2.pdf), [Prodigy](https://www.aidedd.org/dnd/dons.php?vo=prodigy), [Expert Classes playtest](https://media.dndbeyond.com/compendium-images/one-dnd/expert-classes/kpx0MvyfBGHe0XKk/UA2022-Expert-Classes.pdf).
