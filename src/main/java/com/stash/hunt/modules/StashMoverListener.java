package com.stash.hunt.modules;

import com.stash.hunt.Addon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StashMoverListener extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTrigger = settings.createGroup("Trigger Settings");
    private final SettingGroup sgTrapdoor = settings.createGroup("Trapdoor Settings");

    // General settings
    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("Debug Mode")
        .description("Print debug messages in chat")
        .defaultValue(false)
        .build()
    );

    // Trigger settings
    private final Setting<String> baseReturnTrigger = sgTrigger.add(new StringSetting.Builder()
        .name("Base Return Trigger")
        .description("The message that will trigger teleportation back to base")
        .defaultValue("tpbacknow")
        .build()
    );

    private final Setting<String> stashReturnTrigger = sgTrigger.add(new StringSetting.Builder()
        .name("Stash Return Trigger")
        .description("The message that will trigger teleportation back to stash")
        .defaultValue("tpbacktostash")
        .build()
    );

    private final Setting<List<String>> allowedSenders = sgTrigger.add(new StringListSetting.Builder()
        .name("Allowed Senders")
        .description("List of players allowed to trigger the trapdoor")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Boolean> ignoreCase = sgTrigger.add(new BoolSetting.Builder()
        .name("Ignore Case")
        .description("Whether to ignore case when matching the trigger message")
        .defaultValue(true)
        .build()
    );

    // Trapdoor settings
    private final Setting<Block> trapdoorType = sgTrapdoor.add(new BlockSetting.Builder()
        .name("Trapdoor Type")
        .description("The type of trapdoor to activate")
        .defaultValue(Blocks.BIRCH_TRAPDOOR)
        .filter(b -> b instanceof TrapdoorBlock)
        .build()
    );

    private final Setting<Integer> searchRange = sgTrapdoor.add(new IntSetting.Builder()
        .name("Search Range")
        .description("Range to search for the trapdoor")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> activationDelay = sgTrapdoor.add(new IntSetting.Builder()
        .name("Activation Delay")
        .description("Delay in ticks between trapdoor activations")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    // State tracking
    private boolean shouldActivateTrapdoor = false;
    private boolean shouldResetTrapdoor = false;
    private int activationTicks = 0;
    private int resetTicks = 0;
    private BlockPos targetTrapdoor = null;

    // Pattern for private message detection - updated to handle more formats
    private static final Pattern PRIVATE_MESSAGE_PATTERN = Pattern.compile("^(?:\\[.*?\\] )?([\\w]+) (?:whispers|whispers to you|messages|messages you|says|tells you): (.+)$");

    public StashMoverListener() {
        super(Addon.CATEGORY, "StashMoverListener", "Listens for trigger messages and activates a trapdoor for pearl teleportation");
    }

    @Override
    public void onActivate() {
        shouldActivateTrapdoor = false;
        shouldResetTrapdoor = false;
        activationTicks = 0;
        resetTicks = 0;
        targetTrapdoor = null;
        debug("Activated StashMoverListener. Waiting for trigger messages.");
    }

    // Flag to track if we should log trapdoor activation in the onTick method
    private boolean shouldLogActivation = false;

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc == null || mc.player == null) return;

        final String raw = event.getMessage().getString();
        final boolean ic = ignoreCase.get();

        // 1) Détecter la présence du trigger n'importe où dans le message
        String base = baseReturnTrigger.get();
        String stash = stashReturnTrigger.get();
        String hay = ic ? raw.toLowerCase() : raw;
        String needleBase = ic ? base.toLowerCase() : base;
        String needleStash = ic ? stash.toLowerCase() : stash;

        boolean hasTrigger = hay.contains(needleBase) || hay.contains(needleStash);
        if (!hasTrigger) return;

        // 2) Déterminer (au mieux) l'expéditeur
        String sender = extractSender(raw);

        // 3) Vérifier Allowed Senders si renseigné
        List<String> allowed = allowedSenders.get();
        if (!allowed.isEmpty()) {
            boolean ok = false;

            // a) match direct sur l'expéditeur extrait
            if (sender != null && !sender.isEmpty()) {
                for (String s : allowed) {
                    ok |= ic ? s.equalsIgnoreCase(sender) : s.equals(sender);
                    if (ok) break;
                }
            }

            // b) fallback: si pas d'expéditeur fiable, accepter si le message contient un des pseudos
            if (!ok) {
                for (String s : allowed) {
                    ok |= ic ? hay.contains(s.toLowerCase()) : raw.contains(s);
                    if (ok) break;
                }
            }

            if (!ok) return; // bloqué par la whitelist
        }

        // 4) Armer l’activation; le log partira dans onTick
        shouldActivateTrapdoor = true;
        shouldLogActivation = true;
        activationTicks = activationDelay.get();
    }

    // Essaie plusieurs formats fréquents de MP (EN/FR + variantes plugins)
    private String extractSender(String raw) {
        // Exemples gérés :
        // "From Name: msg" | "[From Name] msg" | "Name -> you: msg" | "you -> Name: msg"
        // "De Name : msg" | "[De Name] msg" | "[MSG] Name: msg" | "[Privé] Name: msg"
        String[] patterns = new String[] {
            "^(?:\\[.*?\\]\\s*)?From\\s+([A-Za-z0-9_]+)\\s*:\\s+.+$",
            "^(?:\\[.*?\\]\\s*)?To\\s+([A-Za-z0-9_]+)\\s*:\\s+.+$",
            "^([A-Za-z0-9_]+)\\s*->\\s*(?:me|you)\\s*:\\s+.+$",
            "^(?:me|you)\\s*->\\s*([A-Za-z0-9_]+)\\s*:\\s+.+$",
            "^(?:\\[.*?\\]\\s*)?De\\s+([A-Za-z0-9_]+)\\s*:\\s+.+$",   // FR: "De Pseudo : ..."
            "^(?:\\[.*?\\]\\s*)?\\[?MSG\\]?\\s+([A-Za-z0-9_]+)\\s*:\\s+.+$",
            "^(?:\\[.*?\\]\\s*)?\\[?Priv[ée]??\\]?\\s+([A-Za-z0-9_]+)\\s*:\\s+.+$",
            "^(?:\\[.*?\\]\\s*)?([A-Za-z0-9_]+)\\s+(?:whispers|tells you|messages(?: you)?)\\s*:\\s+.+$"
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(raw);
            if (m.matches()) return m.group(1);
        }
        return null; // pas sûr
    }


    /**
     * Check if a message contains a trigger word
     */
    private boolean containsTriggerWord(String message, String trigger) {
        if (ignoreCase.get()) {
            return message.toLowerCase().contains(trigger.toLowerCase());
        } else {
            return message.contains(trigger);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Handle logging of activation - safe to do here as it's not in the message handler
        if (shouldLogActivation) {
            debug("Received trigger message - activating trapdoor");
            shouldLogActivation = false;
        }

        if (shouldActivateTrapdoor && targetTrapdoor == null) {
            targetTrapdoor = findTrapdoor();
            if (targetTrapdoor == null) {
                debug("Could not find a suitable trapdoor to activate");
                shouldActivateTrapdoor = false;
                return;
            }
            debug("Found trapdoor at " + targetTrapdoor.toShortString());
        }

        // Handle trapdoor activation
        if (shouldActivateTrapdoor && targetTrapdoor != null) {
            if (activationTicks <= 0) {
                activateTrapdoor(targetTrapdoor);
                shouldActivateTrapdoor = false;
                shouldResetTrapdoor = true;
                resetTicks = activationDelay.get();
            } else {
                activationTicks--;
            }
        }

        // Handle trapdoor reset (to be ready for next activation)
        if (shouldResetTrapdoor && targetTrapdoor != null) {
            if (resetTicks <= 0) {
                activateTrapdoor(targetTrapdoor);
                shouldResetTrapdoor = false;
                targetTrapdoor = null;
                debug("Trapdoor reset and ready for next activation");
            } else {
                resetTicks--;
            }
        }
    }

    /**
     * Find a suitable trapdoor of the selected type
     */
    private BlockPos findTrapdoor() {
        if (mc.player == null || mc.world == null) return null;

        BlockPos playerPos = mc.player.getBlockPos();
        int range = searchRange.get();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();

                    if (block == trapdoorType.get()) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Activate a trapdoor by right-clicking it
     */
    private void activateTrapdoor(BlockPos pos) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        Vec3d hitPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, pos, false);

        // Look at the trapdoor and right-click it
        Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
        });

        debug("Activated trapdoor at " + pos.toShortString());
    }

    /**
     * Check if a message matches the trigger, with case sensitivity option
     * Now more forgiving - checks if the message contains the trigger, not just exact match
     */
    private boolean matches(String message, String trigger) {
        if (ignoreCase.get()) {
            return message.toLowerCase().contains(trigger.toLowerCase());
        } else {
            return message.contains(trigger);
        }
    }

    /**
     * Print a debug message if debug mode is enabled
     */
    private void debug(String message) {
        if (debug.get()) {
            info(message);
        }
    }
}
