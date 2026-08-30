# RPGDamageOverhaul — Forge 1.20.1 → NeoForge 1.21.1 Porting Notes

*Fork of [httpedor/RPGDamageOverhaul](https://github.com/httpedor/RPGDamageOverhaul). Licensed under Apache License 2.0.*

## Build system

- Uses `net.neoforged.moddev` (ModDevGradle), not NeoGradle. `jarJar` dependency syntax:
  ```groovy
  jarJar(implementation("group:artifact:version")) {
      version { strictly "[x,)"; prefer "x" }
  }
  ```
- `mixins.json` no longer needs a `refmap` field.
- Dependency versions centralized in `gradle.properties`.

## Networking

- Forge's `SimpleChannel` / `NetworkRegistry` / `NetworkDirection` removed entirely. Raw login-phase packets no longer exist.
- Replacement: `CustomPacketPayload` (record) + `Type<T>` + `StreamCodec`, registered via `RegisterPayloadHandlersEvent` (mod bus).
- Sending before world-join now requires `RegisterConfigurationTasksEvent` (mod bus) + an `ICustomConfigurationTask`, whose `run(sender)` must call `listener.finishCurrentTask(type())`.
- See `SyncPacket.java`, `SyncConfigurationTask.java`.
- `HandshakeHandlerMixin` and `NamespacedWrapperMixin` removed (not ported) — targeted Forge classes that no longer exist.

## `Attribute → Holder<Attribute>`

- `LivingEntity.getAttribute(...)` / `getAttributeValue(...)` / `getAttributeBaseValue(...)`: take `Holder<Attribute>`.
- `AttributeSupplier.Builder.add(...)`: takes `Holder<Attribute>`.
- `MobEffect.addAttributeModifier(...)`: takes `Holder<Attribute>` + `ResourceLocation` id (not `String`).
- `DamageClass`'s own fields (`dmgAttribute`, `armorAttribute`, etc.) kept as raw `Attribute`; wrapped at each call site via `BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attr)`.
- `AttributeSupplier`'s internal `instances` map: `Map<Holder<Attribute>, AttributeInstance>` (relevant to `DefaultAttributeContainerMixin`'s `@Shadow` field).

## `AttributeModifier`

- Now `record AttributeModifier(ResourceLocation id, double amount, Operation operation)`. No `String name` param. `id` moved from `UUID`/`String` to `ResourceLocation`.
- `transientModifiers`: `Map<UUID, Attribute>` → `Map<ResourceLocation, Holder<Attribute>>`
- `transientModifiersDuration`: `Map<LivingEntity, Map<UUID, Long>>` → `Map<LivingEntity, Map<ResourceLocation, Long>>`
- `.getId()` → `.id()`, `.getAmount()` → `.amount()`
- `Operation` enum: `ADDITION` → `ADD_VALUE`, `MULTIPLY_BASE` → `ADD_MULTIPLIED_BASE`, `MULTIPLY_TOTAL` → `ADD_MULTIPLIED_TOTAL`. Datapack JSON using old operation names needs updating (`Operation.valueOf(...)` is called directly on the JSON string).
- `AttributeInstance.getModifier(id)` / `.removeModifier(id)`: keyed by `ResourceLocation`.

## Enchantments

- No longer a static registry. Requires `RegistryAccess` (`RPGDamageOverhaul.dl.ra`):
  ```java
  var enchantmentRegistry = RPGDamageOverhaul.dl.ra.registryOrThrow(Registries.ENCHANTMENT);
  var enchantment = enchantmentRegistry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(id))).orElse(null);
  ```
- `EnchantmentHelper.getEnchantmentLevel(...)`: takes `Holder<Enchantment>`.
- `damageEnchantments` map: `Map<Holder<Enchantment>, Tuple<DamageClass, Float>>`.

## Other vanilla API changes

| Old | New |
|---|---|
| `ResourceLocation` constructors | Removed. `ResourceLocation.parse(str)` / `.fromNamespaceAndPath(ns, path)`. |
| `LivingEntity.setSecondsOnFire(int)` | `Entity.igniteForSeconds(float)` |
| `EnchantmentHelper.getFireAspect(...)` | Removed. Fire Aspect is now generic component-driven logic. |
| `ItemStack.getAttributeModifiers(EquipmentSlot)` | `getAttributeModifiers().forEach(slot, (holder, modifier) -> {...})` |
| `CombatRules.getDamageAfterAbsorb(float, float, float)` | `(LivingEntity, float, DamageSource, float, float)` |
| `TextColor.parseColor(str)` | Returns `DataResult<TextColor>` — use `.result().orElse(null)` |
| `BoxStyle.DEFAULT` (Jade API) | `BoxStyle.getNestedBox()` |
| `PoisonMobEffect` | No longer calls `DamageSources.magic()`; builds `DamageSource` via `NeoForgeMod.POISON_DAMAGE` holder with `DamageTypes.MAGIC` fallback. Mixin retargeted to wrap `LivingEntity.hurt(...)` instead. |

## Compat mod status

| Mod | Status | Notes |
|---|---|---|
| Apothic Attributes (formerly AttributesLib) | Ported | Package `dev.shadowsoffire.attributeslib` → `dev.shadowsoffire.apothic_attributes`. Mod ID also changed to `apothic_attributes`. Requires Placebo. |
| Jade | Ported | `BoxStyle.DEFAULT` → `getNestedBox()`. |
| Better Combat | Ported | Requires Cloth Config + PlayerAnimator at runtime. |
| Simply Swords | Ported (18 mixins) | See mechanic changes below. |
| Simply More | Ported (2 of 4 mixins) | `BleedMixin`/`VipersCallMixin` dropped, see below. Requires Simply Swords, Architectury API, Fzzy Config, Kotlin for Forge, Simply Tooltips (pin to exactly 0.1.3 — 0.1.5 breaks Simply More 1.2.3's mixin). |
| AttributeSetter | Ported (data only) | Separate mod, same author. Ships 51 files of cross-mod compat data under `data/rpgdamageoverhaul/attributesetter/`. Schema unchanged (UUIDs optional but still accepted). |
| Better Mob Combat, Dungeons and Combat, Soul's Weapons | Not ported | No NeoForge 1.21.1 release. `OptionalMixinCanceler` no-ops their mixins if absent. |

## Simply Swords / Simply More mechanic changes

- **Sunfire** (`FireSwordMixin`): damage moved from the sword item into `BattleStandardEntity.baseTick()`. Mixin checks `standardType == "sunfire"` at runtime since the method also handles unrelated banner types (`"nullification"`, `"api"`).
- **Volcanic Fury** (`VolcanicFurySwordMixin`): `VolcanicFurySwordItem` no longer exists. Ability now on a weapon called Hearthflame — either directly in `releaseUsing()` or via a separate `HearthflameAbilityManager`, depending on dependency version.
- **Livyatan / Frostfall**: damage moved from the item's `inventoryTick` into the thrown entity's own class (`LivyatanEntity.damageOnReturn()`, `FrostfallEntity.doOnTick()`), via `DamageSources.trident(...)`.
- **VipersCall** (Simply More): redesigned into a passive effect-refresh weapon, no damage/poison dealing. Mixin dropped.
- **Bleed → "Wounded"** (Simply More): redesigned from damage-over-time into a heal-reduction debuff, implemented in Simply More's own `LivingEntity` mixin. No damage tick to reclassify. Mixin dropped.
- **PoisonBolt**: class package differs between some source snapshots (`legacy` subpackage) and the compiled 1.2.3 release (no `legacy`).

## Content additions (1.20.1 → 1.21.1 content gap)

- **Mace** → `item_overrides.json`: `#c:maces` / `#minecraft:maces` set to 100% blunt.
- **Breeze, Bogged, Armadillo** → `attributesetter/entity/minecraft.json`: resistance/vulnerability values added (see git history for exact values).

## Outstanding items

- Only vanilla-targeted `attributesetter/` files (`minecraft.json`) are in-game verified. ~48 other-mod-targeted files are untested (will no-op if target mod absent).
- `mods.toml` dependency declarations incomplete — only lists `bettercombat`, `simplyswords`, `soulsweapons`, `attributesetter`, `origins`. Jade/Apothic Attributes/Simply More not formally declared (compat handled via runtime `ModList` checks instead).
