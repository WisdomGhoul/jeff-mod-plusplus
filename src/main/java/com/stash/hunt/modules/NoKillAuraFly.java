package com.stash.hunt.modules;

import com.stash.hunt.Addon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.orbit.EventHandler;

public class NoKillAuraFly extends Module {
    private boolean wasKillAuraActive = false;
    private boolean killAuraToggledByUs = false;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ✅ Nouveau réglage
    private final Setting<Boolean> showMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("show-messages")
        .description("Show chat messages when KillAura is toggled.")
        .defaultValue(true)
        .build()
    );

    public NoKillAuraFly() {
        super(
            Addon.CATEGORY,
            "NoKillAuraFly",
            "Disable KillAura while flying. By 32766"
        );
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        KillAura killAura = Modules.get().get(KillAura.class);
        String poseName = mc.player.getPose().name();
        boolean isFlyingWithElytra = poseName.equalsIgnoreCase("fall_flying");

        MeteorClient.LOG.debug("[NoKillAuraFly] Pose actuelle = " + poseName);

        if (isFlyingWithElytra) {
            if (killAura.isActive() && !killAuraToggledByUs) {
                wasKillAuraActive = true;
                killAura.toggle();
                killAuraToggledByUs = true;

                if (showMessages.get()) info("§cKillAura disabled during flight");
            }
        } else {
            if (killAuraToggledByUs && wasKillAuraActive && !killAura.isActive()) {
                killAura.toggle();

                if (showMessages.get()) info("§3KillAura enabled");

                killAuraToggledByUs = false;
                wasKillAuraActive = false;
            }

            if (!killAura.isActive() && killAuraToggledByUs) {
                killAuraToggledByUs = false;
                wasKillAuraActive = false;

                if (showMessages.get()) info("§eKillAura manually disabled, module inactive");
            }
        }
    }
}
