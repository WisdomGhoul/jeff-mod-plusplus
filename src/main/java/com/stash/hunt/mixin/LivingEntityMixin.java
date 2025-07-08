package com.stash.hunt.mixin;

import com.stash.hunt.modules.ElytraFlyPlusPlus;
import com.stash.hunt.modules.NoJumpDelay;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    private int jumpingCooldown;

    @Shadow
    public abstract Brain<?> getBrain();

    Module noJumpDelay = Modules.get().get(NoJumpDelay.class);
    ElytraFlyPlusPlus efly = Modules.get().get(ElytraFlyPlusPlus.class);

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void onTickMovement(CallbackInfo ci) {
        if (mc.player != null && mc.player.getBrain().equals(this.getBrain())) {
            if ((efly != null && efly.isActive()) || (noJumpDelay != null && noJumpDelay.isActive())) {
                this.jumpingCooldown = 0;
            }
        }
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void onTravel(Vec3d movementInput, CallbackInfo ci) {
        if (efly != null && efly.isActive()
            && mc.player != null
            && mc.player.getBrain().equals(this.getBrain())
            && mc.player instanceof PlayerEntity player
            && player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
        }
    }
}
