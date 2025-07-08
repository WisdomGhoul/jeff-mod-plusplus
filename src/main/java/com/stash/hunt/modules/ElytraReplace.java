package com.stash.hunt.modules;

import com.stash.hunt.Addon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class ElytraReplace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("replace-threshold")
        .description("Configurable durability threshold for the replacement")
        .defaultValue(5)
        .min(1).sliderMax(100).build()
    );

    private final Setting<Boolean> alertOnNoElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("alert-on-no-elytra")
        .description("Send an alert every 60s when no more ely are available")
        .defaultValue(true)
        .build()
    );

    private boolean waitingToReleaseJump = false;
    private long lastNoElytraAlert = 0;

    public ElytraReplace() {
        super(Addon.CATEGORY, "elytra-replace", "Does what it says. Not compatible with 'Armor Storage'. Disable this shit. By 32766");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        ItemStack equipped = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!equipped.isOf(Items.ELYTRA)) return;

        int currentDurability = equipped.getMaxDamage() - equipped.getDamage();

        if (waitingToReleaseJump) {
            mc.options.jumpKey.setPressed(false);
            waitingToReleaseJump = false;
        }

        int bestSlot = -1;
        int bestDurability = -1;
        int viableElytras = 0;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.ELYTRA)) {
                int durability = stack.getMaxDamage() - stack.getDamage();
                if (durability > threshold.get()) {
                    viableElytras++;
                    if (durability > bestDurability) {
                        bestDurability = durability;
                        bestSlot = i;
                    }
                }
            }
        }

        // Alerte toutes les 60 secondes si aucune Elytra disponible
        if (alertOnNoElytra.get() && viableElytras == 0) {
            long now = System.currentTimeMillis();
            if (now - lastNoElytraAlert > 60000) {
                warning("§4UR USING UR LAST ELYTRA !");
                lastNoElytraAlert = now;
            }
        }

        if (currentDurability > threshold.get()) return;
        if (bestSlot == -1) return;

        int syncId = mc.player.currentScreenHandler.syncId;
        int chestSlotIndex = 6;
        int invSlot = bestSlot < 9 ? 36 + bestSlot : bestSlot;

        mc.interactionManager.clickSlot(syncId, invSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, chestSlotIndex, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, invSlot, 0, SlotActionType.PICKUP, mc.player);

        mc.options.jumpKey.setPressed(true);
        waitingToReleaseJump = true;

        info("§8Elytra Replaced (remaining durability " + bestDurability + ")");
    }
}
