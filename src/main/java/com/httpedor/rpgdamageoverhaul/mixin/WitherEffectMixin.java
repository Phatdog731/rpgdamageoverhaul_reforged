package com.httpedor.rpgdamageoverhaul.mixin;

import com.httpedor.rpgdamageoverhaul.api.DamageClass;
import com.httpedor.rpgdamageoverhaul.api.RPGDamageOverhaulAPI;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.effect.WitherMobEffect")
public class WitherEffectMixin {

    @WrapOperation(method = "applyEffectTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSources;wither()Lnet/minecraft/world/damagesource/DamageSource;"))
    private DamageSource witherDamageType(DamageSources instance, Operation<DamageSource> original)
    {
        DamageClass dc = RPGDamageOverhaulAPI.getDamageClass("wither");
        if (dc != null)
            return dc.createDamageSource(false);
        return original.call(instance);
    }
}