package com.httpedor.rpgdamageoverhaul.mixin;

import com.httpedor.rpgdamageoverhaul.api.DamageClass;
import com.httpedor.rpgdamageoverhaul.api.RPGDamageOverhaulAPI;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.effect.PoisonMobEffect")
public class PoisonEffectMixin {

    @WrapOperation(method = "applyEffectTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean poisonDamageType(LivingEntity instance, DamageSource source, float amount, Operation<Boolean> original)
    {
        DamageClass dc = RPGDamageOverhaulAPI.getDamageClass("poison");
        if (dc != null)
            return instance.hurt(dc.createDamageSource(false), amount);
        return original.call(instance, source, amount);
    }
}