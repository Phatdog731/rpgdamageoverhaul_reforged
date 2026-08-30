package com.httpedor.rpgdamageoverhaul.mixin.simplyswords;

import com.httpedor.rpgdamageoverhaul.api.DamageClass;
import com.httpedor.rpgdamageoverhaul.api.RPGDamageOverhaulAPI;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.sweenus.simplyswords.entity.BattleStandardEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BattleStandardEntity.class)
public class FireSwordMixin {

    @WrapOperation(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSources;magic()Lnet/minecraft/world/damagesource/DamageSource;"))
    public DamageSource fireDamage(DamageSources instance, Operation<DamageSource> original)
    {
        BattleStandardEntity self = (BattleStandardEntity)(Object)this;
        if ("sunfire".equals(self.standardType))
        {
            DamageClass dc = RPGDamageOverhaulAPI.getDamageClass("fire");
            if (dc != null)
                return dc.createDamageSource(self.ownerEntity, self.ownerEntity);
        }
        return original.call(instance);
    }
}