package com.httpedor.rpgdamageoverhaul;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.httpedor.rpgdamageoverhaul.api.DamageClass;
import com.httpedor.rpgdamageoverhaul.api.DamageHandler;
import com.httpedor.rpgdamageoverhaul.api.RPGDamageOverhaulAPI;
import com.httpedor.rpgdamageoverhaul.events.DamageClassRegisteredEvent;
import com.mojang.brigadier.StringReader;
import com.mojang.logging.LogUtils;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import s_com.udojava.evalexrpgdo.Expression;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(RPGDamageOverhaul.MODID)
public class RPGDamageOverhaul {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "rpgdamageoverhaul";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public static DatapackLoader dl = new DatapackLoader();


    public static final Map<Holder<Enchantment>, Tuple<DamageClass, Float>> damageEnchantments = new HashMap<>();
    public static final Map<LivingEntity, Tuple<Float, Long>> noHealingUntil = new HashMap<>();
    public static final Map<LivingEntity, Map<String, List<Tuple<Float, Long>>>> dmgStacks = new HashMap<>();
    public static final Map<LivingEntity, Tuple<Float, Long>> increasedDamage = new HashMap<>();
    public static final Set<DamageClass> increasedDamageExceptions = new HashSet<>();
    public static final Map<ResourceLocation, Holder<Attribute>> transientModifiers = new HashMap<>();
    public static final Map<LivingEntity, Map<ResourceLocation, Long>> transientModifiersDuration = new HashMap<>();
    public static final Map<DamageClass, Function<Float, Float>> onWaterDamageModifiers = new HashMap<>();

    public static final Map<ResourceLocation, List<ResourceLocation>> mappedDamageTypes = new HashMap<>();
    public static final Map<ResourceLocation, List<ResourceLocation>> mappedTags = new HashMap<>();
    public static final Map<ResourceLocation, List<ResourceLocation>> mappedAttributes = new HashMap<>();

    private void spawnHitParticles(ServerLevel world, ParticleOptions parameters, double x, double y, double z, int amount)
    {
        double maxSpeed  = 0.1;
        for (int i = 0; i < amount; i++)
        {
            world.sendParticles(parameters, x, y, z, amount, 0d, 0d, 0d, maxSpeed);
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1.0");
        registrar.configurationToClient(SyncPacket.TYPE, SyncPacket.STREAM_CODEC, (payload, context) -> {
            payload.handle();
        });
    }

    private void registerConfigurationTasks(RegisterConfigurationTasksEvent event) {
        event.register(new SyncConfigurationTask(event.getListener()));
    }

    public RPGDamageOverhaul(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(this);          // gameplay events go here
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerConfigurationTasks);
        registerOnHitEffects();
    }

    @SubscribeEvent
    public void onDCRegistered(DamageClassRegisteredEvent e)
    {
        var dc = e.getDamageClass();

        // Register potion attributes
        if (dc.properties.containsKey("potions"))
        {
            JsonObject potions = dc.properties.get("potions").getAsJsonObject();
            for (Map.Entry<String, JsonElement> attribute: potions.entrySet())
            {
                Attribute attr = switch (attribute.getKey()) {
                    case "resistance" -> dc.resistanceAttribute;
                    case "damage" -> dc.dmgAttribute;
                    case "armor" -> dc.armorAttribute;
                    case "absorption" -> dc.absorptionAttribute;
                    default -> null;
                };
                if (attr == null)
                {
                    RPGDamageOverhaul.LOGGER.warn("Unknown attribute: {} for damage class potions: {}", attribute.getKey(), dc.name);
                    continue;
                }
                var attrHolder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attr);
                JsonObject potionAttrs = attribute.getValue().getAsJsonObject();
                for (Map.Entry<String, JsonElement> potion: potionAttrs.entrySet())
                {
                    MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(potion.getKey()));
                    if (effect == null)
                    {
                        RPGDamageOverhaul.LOGGER.warn("Unknown potion effect: {} for damage class potions: {}", potion.getKey(), dc.name);
                        continue;
                    }
                    double value = potion.getValue().getAsDouble();
                    ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "potion_" + UUID.randomUUID());
                    effect.addAttributeModifier(attrHolder, modifierId, value, AttributeModifier.Operation.ADD_VALUE);
                }
            }
        }

        //Register enchantments
        if (dc.properties.containsKey("enchantments"))
        {
            JsonObject enchantments = dc.properties.get("enchantmentseffect.addAttributeModifier(attrHolder, modifierId, value, AttributeModifier.Operation.ADDITION);").getAsJsonObject();
            if (enchantments.has("damage"))
            {
                JsonObject dmgEnchants = enchantments.getAsJsonObject("damage");
                var enchantmentRegistry = RPGDamageOverhaul.dl.ra.registryOrThrow(Registries.ENCHANTMENT);
                for (Map.Entry<String, JsonElement> enchant: dmgEnchants.entrySet())
                {
                    var enchantmentKey = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(enchant.getKey()));
                    var enchantment = enchantmentRegistry.getHolder(enchantmentKey).orElse(null);
                    if (enchantment == null)
                    {
                        RPGDamageOverhaul.LOGGER.warn("Unknown enchantment: {} for damage class : {}", enchant.getKey(), dc.name);
                        continue;
                    }
                    float value = enchant.getValue().getAsFloat();
                    damageEnchantments.put(enchantment, new Tuple<>(dc, value));
                }
            }
        }

        var dtKey = ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", dc.name);
        //Register DT aliases
        if (dc.properties.containsKey("damageTypes"))
        {
            var dts = dc.properties.get("damageTypes").getAsJsonArray().asList();
            if (!mappedDamageTypes.containsKey(dtKey))
                mappedDamageTypes.put(dtKey, new ArrayList<>());
            for (var damageTypeEl : dts)
            {
                mappedDamageTypes.get(dtKey).add(ResourceLocation.parse(damageTypeEl.getAsString()));
            }
        }

        //Register DC DT Tags
        if (dc.properties.containsKey("tags"))
        {
            var tags = dc.properties.get("tags").getAsJsonArray().asList();
            if (!mappedTags.containsKey(dtKey))
                mappedTags.put(dtKey, new ArrayList<>());
            for (var tag : tags)
            {
                mappedTags.get(dtKey).add(ResourceLocation.parse(tag.getAsString()));
            }
        }

        if (dc.properties.containsKey("on_water"))
        {
            var element = dc.properties.get("on_water");
            Function<Float, Float> func;
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())
            {
                float multiplier = element.getAsFloat();
                func = (dmg) -> dmg * multiplier;
            }
            else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
            {
                Expression exp = new Expression(element.getAsString());
                func = (dmg) -> {
                    var newExp = exp.with("dmg", BigDecimal.valueOf(dmg));
                    return newExp.eval().floatValue();
                };
            }
            else
            {
                RPGDamageOverhaul.LOGGER.warn("Invalid on_water property for damage class {}: {}", dc.name, element);
                func = (dmg) -> dmg;
            }
            onWaterDamageModifiers.put(dc, func);
        }
    }

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre e)
    {
        var dc = RPGDamageOverhaulAPI.getDamageClass(e.getSource().type());
        if (dc != null)
        {
            if (onWaterDamageModifiers.containsKey(dc) && e.getEntity().isInWaterRainOrBubble())
            {
                var func = onWaterDamageModifiers.get(dc);
                e.setNewDamage(func.apply(e.getNewDamage()));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDatapackReload(AddReloadListenerEvent e)
    {
        dl.ra = e.getRegistryAccess();
        e.addListener(dl);
    }

    private void registerOnHitEffects()
    {
        RPGDamageOverhaulAPI.registerOnHitEffect(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "particles"), (target, source, dmg) -> {
            DamageClass dc = RPGDamageOverhaulAPI.getDamageClass(source.type());
            var particleEl = dc.properties.getOrDefault("particle", null);
            if (particleEl == null)
                return;

            String particleId = particleEl.getAsString();
            if (!target.level().isClientSide)
            {
                SimpleParticleType pt = (SimpleParticleType) BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(particleId));
                if (pt == null)
                {
                    System.out.println("Particle not found: " + particleId);
                    return;
                }

                spawnHitParticles((ServerLevel) target.level(), pt, target.getX(), target.getEyeY(), target.getZ(), (int) (dmg/2)+1);
            }
        });
        RPGDamageOverhaulAPI.registerOnHitEffect(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "set_fire"), (target, source, dmg) -> {
            if (source.getEntity() != null)
                target.igniteForSeconds((float) (dmg/2));
            target.setTicksFrozen(0);
        });
        RPGDamageOverhaulAPI.registerOnHitEffect(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "set_frozen"), (target, source, dmg) -> {
            target.setTicksFrozen((int) Math.round(dmg/1.5 * 20));
            target.setRemainingFireTicks(0);
        });
        RPGDamageOverhaulAPI.registerOnHitEffect(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "anti_heal"), (target, source, dmg) -> {
            float percent;
            long time;
            DamageClass dc = RPGDamageOverhaulAPI.getDamageClass(source.type());
            if (dc.properties.containsKey("antiHeal"))
            {
                var element = dc.properties.get("antiHeal").getAsJsonObject();
                percent = (float) (element.get("percentBlockedPerHP").getAsDouble() * dmg);
                time = (long) (element.get("durationPerHeart").getAsFloat() * 1000) * (long) (dmg/2);
            }
            else
            {
                percent = 0.5f;
                time = 500 * (long) (dmg/2);
            }
            percent = Math.min(percent, 1);
            if (noHealingUntil.containsKey(target) && noHealingUntil.get(target).getA() > percent)
                return;
            noHealingUntil.put(target, new Tuple<>(percent, System.currentTimeMillis() + time));
        });
        RPGDamageOverhaulAPI.registerOnHitEffect(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "chain_lightning"), (target, source, dmg) -> {
            DamageClass dc = RPGDamageOverhaulAPI.getDamageClass(source.type());
            var obj = dc.properties.get("chainLightning").getAsJsonObject();
            double range = obj.get("range").getAsDouble();
            int maxTargets = obj.get("maxTargets").getAsInt();
            double damagePercentage = obj.get("damagePercentage").getAsDouble();

            int targets = 0;
            double currentDmg = dmg * damagePercentage;
            Set<LivingEntity> traveled = new HashSet<>();
            LivingEntity current = target;
            while (current != null && targets < maxTargets)
            {
                traveled.add(current);
                current = current.level().getEntitiesOfClass(LivingEntity.class, AABB.ofSize(current.position(), range, range, range), (e) -> !traveled.contains(e)).stream().findFirst().orElse(null);
                if (current == null)
                    break;
                current.hurt(dc.createDamageSource(source.getEntity(), source.getDirectEntity(), false), (float) currentDmg);
                if (dc.onHitEffects.contains(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "particles")))
                    DamageHandler.executeOnHitEffect(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "particles"), current, source, (float) currentDmg);
                currentDmg *= damagePercentage;
                targets++;
            }
        });
        RPGDamageOverhaulAPI.registerOnHitEffect(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "heal"), (target, source, dmg) -> {
            DamageClass dc = RPGDamageOverhaulAPI.getDamageClass(source.type());

            if (source.getEntity() instanceof LivingEntity le)
            {
                var multiplierEl = dc.properties.getOrDefault("heal", null);
                double multiplier;
                if (multiplierEl == null)
                    multiplier = 1;
                else if (multiplierEl.getAsJsonPrimitive().isNumber())
                    multiplier = multiplierEl.getAsDouble();
                else
                {
                    Expression exp = new Expression(multiplierEl.getAsString()).with("dmg", BigDecimal.valueOf(dmg));
                    multiplier = exp.eval().doubleValue();
                }
                le.heal((float) (dmg * multiplier));
            }
        });
        RPGDamageOverhaulAPI.registerOnHitEffect(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "stacking"), (target, source, dmg) -> {
            DamageClass dc = RPGDamageOverhaulAPI.getDamageClass(source.type());
            var obj = dc.properties.get("stacking").getAsJsonObject();
            String stackName;
            if (obj.has("stackName"))
                stackName = obj.get("stackName").getAsString();
            else
                stackName = dc.name;
            var stacks = dmgStacks.computeIfAbsent(target, (t) -> new HashMap<>()).computeIfAbsent(stackName, (s) -> new ArrayList<>());
            int maxStacks;
            long stacksDuration;
            if (obj.has("maxStacks"))
                maxStacks = obj.get("maxStacks").getAsInt();
            else
                maxStacks = 5;
            if (obj.has("stacksDuration"))
                stacksDuration = (long) (obj.get("stacksDuration").getAsFloat() * 1000);
            else
                stacksDuration = 5000;
            stacks.removeIf(stack -> stack.getB() + stacksDuration < System.currentTimeMillis());
            if (stacks.size() >= maxStacks)
                stacks.remove(0);

            var currentDmg = stacks.stream().mapToDouble(Tuple::getA).sum();
            target.hurt(dc.createDamageSource(source.getEntity(), source.getDirectEntity(), false), ((float) currentDmg));

            if (obj.has("formula"))
            {
                Expression exp = new Expression(obj.get("formula").getAsString()).with("dmg", BigDecimal.valueOf(dmg));
                stacks.add(new Tuple<>(exp.eval().floatValue(), System.currentTimeMillis()));
            }
            else
                stacks.add(new Tuple<>(dmg.floatValue()/3, System.currentTimeMillis()));
        });
        RPGDamageOverhaulAPI.registerOnHitEffect(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "increase_damage"), (target, source, dmg) -> {
            DamageClass dc = RPGDamageOverhaulAPI.getDamageClass(source.type());
            var obj = dc.properties.get("increaseDamage").getAsJsonObject();
            float duration;
            float dmgIncrease;
            if (obj.has("except"))
            {
                var except = obj.get("except").getAsJsonArray();
                for (var el : except)
                {
                    var tdc = RPGDamageOverhaulAPI.getDamageClass(el.getAsString());
                    if (tdc != null)
                        increasedDamageExceptions.add(tdc);
                }
            }
            if (obj.has("duration"))
            {
                var el = obj.get("duration");
                if (el.getAsJsonPrimitive().isNumber())
                    duration = obj.get("duration").getAsFloat();
                else
                {
                    Expression exp = new Expression(el.getAsString()).with("dmg", BigDecimal.valueOf(dmg));
                    duration = exp.eval().floatValue();
                }
            }
            else
                duration = 5;
            if (obj.has("multiplier"))
            {
                var el = obj.get("multiplier");
                if (el.getAsJsonPrimitive().isNumber())
                    dmgIncrease = obj.get("multiplier").getAsFloat();
                else
                {
                    Expression exp = new Expression(el.getAsString()).with("dmg", BigDecimal.valueOf(dmg));
                    dmgIncrease = exp.eval().floatValue();
                }
            }
            else
                dmgIncrease = (float) (1 + 0.03f * dmg);
            increasedDamage.put(target, new Tuple<>(dmgIncrease, System.currentTimeMillis() + (long) (duration * 1000)));
        });
        RPGDamageOverhaulAPI.registerOnHitEffect(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "apply_potion"), (target, source, dmg) -> {
            DamageClass dc = RPGDamageOverhaulAPI.getDamageClass(source.type());

            var obj = dc.properties.get("applyPotion").getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet())
            {
                var effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(entry.getKey()));
                if (effect == null)
                {
                    LOGGER.warn("Unknown potion effect: {} for damage class applyPotion: {}", entry.getKey(), dc.name);
                    continue;
                }
                var duration = entry.getValue().getAsFloat();
                target.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), (int) (duration * 20), 0));
            }
        });
        RPGDamageOverhaulAPI.registerOnHitEffect(ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "attribute_modifier"), (target, source, dmg) -> {
            DamageClass dc = RPGDamageOverhaulAPI.getDamageClass(source.type());
            var obj = dc.properties.get("attributeModifier").getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet())
            {
                var name = entry.getKey();
                var modifier = entry.getValue().getAsJsonObject();
                var rawAttribute = BuiltInRegistries.ATTRIBUTE.get(ResourceLocation.parse(modifier.get("attribute").getAsString()));
                if (rawAttribute == null)
                {
                    LOGGER.warn("Unknown attribute: {} for damage class attributeModifier: {}", modifier.get("attribute").getAsString(), dc.name);
                    continue;
                }
                var attribute = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(rawAttribute);
                if (target.getAttribute(attribute) == null)
                {
                    continue;
                }
                var operation = modifier.get("operation").getAsString();
                double amount;
                if (modifier.get("amount").getAsJsonPrimitive().isNumber())
                    amount = modifier.get("amount").getAsDouble();
                else
                {
                    Expression exp = new Expression(modifier.get("amount").getAsString()).with("dmg", BigDecimal.valueOf(dmg));
                    amount = exp.eval().doubleValue();
                }
                ResourceLocation id;
                if (modifier.has("id"))
                    id = ResourceLocation.parse(modifier.get("id").getAsString());
                else
                    id = ResourceLocation.fromNamespaceAndPath("rpgdamageoverhaul", "modifier_" + UUID.randomUUID());
                double duration;
                if (modifier.get("duration").getAsJsonPrimitive().isNumber())
                    duration = modifier.get("duration").getAsDouble();
                else
                {
                    Expression exp = new Expression(modifier.get("duration").getAsString()).with("dmg", BigDecimal.valueOf(dmg));
                    duration = exp.eval().doubleValue();
                }

                String replaceType = "ALWAYS";
                if (modifier.has("replaceType"))
                    replaceType = modifier.get("replaceType").getAsString();

                var mod = new AttributeModifier(id, amount, AttributeModifier.Operation.valueOf(operation));
                transientModifiersDuration.computeIfAbsent(target, (t) -> new HashMap<>()).put(id, System.currentTimeMillis() + Math.round(duration * 1000L));
                if (target.getAttribute(attribute).getModifier(mod.id()) != null)
                {
                    double currentAmount = target.getAttribute(attribute).getModifier(mod.id()).amount();
                    if (replaceType.equalsIgnoreCase("always")
                            || (replaceType.equalsIgnoreCase("lower") && amount < currentAmount)
                            || (replaceType.equalsIgnoreCase("higher") && amount > currentAmount))
                    {
                        target.getAttribute(attribute).removeModifier(mod.id());
                    }
                    else
                    {
                        return;
                    }
                }
                double healthRatio = target.getHealth() / target.getMaxHealth();
                target.getAttribute(attribute).addTransientModifier(mod);
                transientModifiers.put(id, attribute);
                if (attribute.value() == Attributes.MAX_HEALTH.value())
                {
                    target.setHealth((float) (target.getMaxHealth() * healthRatio));
                }
            }
        });
    }
}