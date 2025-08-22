package com.stash.hunt.modules;

import com.stash.hunt.Addon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;


import java.util.HashSet;
import java.util.Set;

public class ChatSigns extends Module {
    private final Set<BlockPos> seenSigns = new HashSet<>();

    public ChatSigns() {
        super(Addon.CATEGORY, "chat-signs", "Show signs content in chat");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        ClientWorld world = mc.world;

        int radius = mc.options.getViewDistance().getValue(); // render distance en chunks
        BlockPos playerPos = mc.player.getBlockPos();

        // On parcourt les chunks dans un carré autour du joueur
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                WorldChunk chunk = world.getChunk(playerPos.getX() / 16 + dx, playerPos.getZ() / 16 + dz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof SignBlockEntity sign)) continue;

                    BlockPos pos = sign.getPos();

                    if (seenSigns.contains(pos)) continue;

                    double dist2 = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    double maxDist2 = (radius * 16.0) * (radius * 16.0);
                    if (dist2 > maxDist2) continue;

                    seenSigns.add(pos);

                    StringBuilder sb = new StringBuilder();
                    for (Text line : sign.getFrontText().getMessages(true)) {
                        String s = line.getString().replace("\n", "⏎").trim();
                        if (!s.isEmpty()) {
                            if (sb.length() > 0) sb.append(" ⏎ ");
                            sb.append(s);
                        }
                    }

                    if (sb.length() > 0) {
                        // Préfixe emoji + code couleur §b (turquoise) + §o (italique)
                        String message = "🪧 §b§o" + sb.toString();
                        mc.player.sendMessage(Text.of("🪧 §b§o" + sb.toString()) , false);
                    };
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        seenSigns.clear();
    }
}
