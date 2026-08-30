package com.httpedor.rpgdamageoverhaul.mixin.simplyswords;

import com.httpedor.rpgdamageoverhaul.api.DamageClass;
import com.httpedor.rpgdamageoverhaul.api.RPGDamageOverhaulAPI;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.sweenus.simplyswords.entity.FrostfallEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FrostfallEntity.class)
public class FrostfallMixin {

    @WrapOperation(method = "doOnTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSources;trident(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/damagesource/DamageSource;"))
    public DamageSource frostDamage(DamageSources instance, Entity source, Entity attacker, Operation<DamageSource> original)
    {
        DamageClass dc = RPGDamageOverhaulAPI.getDamageClass("frost");
        if (dc != null)
            return dc.createDamageSource(attacker, source);
        return original.call(instance, source, attacker);
    }

}