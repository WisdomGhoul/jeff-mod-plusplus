package com.stash.hunt.modules;

import com.stash.hunt.Addon;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalNear;

import java.util.*;

public class ChestSearcher extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Rechercher par nom ou par type.")
        .defaultValue(Mode.Nom)
        .build()
    );

    private final Setting<String> itemName = sgGeneral.add(new StringSetting.Builder()
        .name("nom-item")
        .description("Nom de l'item (ex: minecraft:diamond_pickaxe).")
        .defaultValue("minecraft:diamond_pickaxe")
        .visible(() -> mode.get() == Mode.Nom)
        .build()
    );

    private final Setting<List<Item>> itemTypes = sgGeneral.add(new ItemListSetting.Builder()
        .name("types-items")
        .description("Liste d'items par type (ex: outils).")
        .defaultValue(Arrays.asList(Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE))
        .visible(() -> mode.get() == Mode.Type)
        .build()
    );

    private final Setting<Integer> maxRadius = sgGeneral.add(new IntSetting.Builder()
        .name("rayon-max")
        .description("Rayon de recherche des coffres (en chunks).")
        .defaultValue(5)
        .min(1)
        .max(20)
        .build()
    );

    private final Setting<Double> yRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-range")
        .description("The vertical range around the player to search for chests.")
        .defaultValue(50.0)
        .min(0.0)
        .build()
    );

    private final Setting<Double> chestInteractRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("chest-interact-range")
        .description("The range in blocks to interact with chests.")
        .defaultValue(4.0)
        .min(1.0)
        .max(6.0)
        .build()
    );

    private final Setting<Integer> chestInteractDelay = sgGeneral.add(new IntSetting.Builder()
        .name("chest-interact-delay")
        .description("The delay in ticks between interacting with chests.")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> chestOpenDelay = sgGeneral.add(new IntSetting.Builder()
        .name("chest-open-delay")
        .description("The delay in ticks after opening a chest before checking inventory.")
        .defaultValue(5)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Enable debug messages to trace shulker box inspection.")
        .defaultValue(true)
        .build()
    );

    private List<BlockPos> chests = new ArrayList<>();
    private Set<BlockPos> visited = new HashSet<>();
    private int currentIndex = 0;
    private BlockPos currentChest;
    private boolean openingChest = false;
    private int interactDelay = 0;
    private int openDelay = 0;
    private GenericContainerScreen currentScreen = null;

    public ChestSearcher() {
        super(Addon.CATEGORY, "chest-searcher", "Cherche un item dans les coffres proches ou leurs shulkers via Baritone.");
    }

    @Override
    public void onActivate() {
        chests.clear();
        visited.clear();
        currentIndex = 0;
        openingChest = false;
        interactDelay = 0;
        openDelay = 0;
        currentScreen = null;

        // Collecter les coffres dans les chunks chargés autour du joueur
        int radius = maxRadius.get();
        BlockPos playerPos = mc.player.getBlockPos();
        int chunkX = playerPos.getX() >> 4;
        int chunkZ = playerPos.getZ() >> 4;
        debugMsg("Scanning chunks for chests within radius " + radius + " around player at " + playerPos);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk chunk = mc.world.getChunk(chunkX + dx, chunkZ + dz);
                if (chunk != null) {
                    int yMin = Math.max(mc.world.getBottomY(), (int)(mc.player.getY() - yRange.get()));
                    int yMax = Math.min(mc.world.getBottomY() + mc.world.getHeight(), (int)(mc.player.getY() + yRange.get()));
                    ChunkPos chunkPos = chunk.getPos();
                    for (int x = 0; x < 16; x++) {
                        for (int y = yMin; y < yMax; y++) {
                            for (int z = 0; z < 16; z++) {
                                BlockPos pos = new BlockPos(chunkPos.getStartX() + x, y, chunkPos.getStartZ() + z);
                                BlockState state = chunk.getBlockState(pos);
                                Block block = state.getBlock();
                                if (block instanceof ChestBlock || block instanceof TrappedChestBlock) {
                                    ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
                                    Direction facing = state.get(ChestBlock.FACING);
                                    debugMsg("Found chest at " + pos + " with ChestType: " + chestType + ", facing: " + facing);
                                    if (chestType == ChestType.SINGLE || chestType == ChestType.LEFT) {
                                        chests.add(pos);
                                    } else if (chestType == ChestType.RIGHT) {
                                        BlockPos leftPos = pos.offset(facing.rotateYCounterclockwise());
                                        debugMsg("Double chest right at " + pos + ", using left at " + leftPos);
                                        chests.add(leftPos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Supprimer les doublons
        chests = new ArrayList<>(new LinkedHashSet<>(chests));
        debugMsg("Found " + chests.size() + " unique chests: " + chests);
        chests.sort(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(Vec3d.ofCenter(pos))));
        debugMsg("Sorted chests by distance: " + chests);

        if (chests.isEmpty()) {
            ChatUtils.error("Aucun coffre trouvé dans le rayon.");
            toggle();
            return;
        }

        startPathToNextChest();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (currentChest == null || currentIndex >= chests.size()) return;

        // Gérer le délai d'interaction
        if (interactDelay > 0) {
            interactDelay--;
            debugMsg("Waiting for interact delay: " + interactDelay);
            return;
        }

        // Gérer le délai d'ouverture
        if (openDelay > 0 && currentScreen != null) {
            openDelay--;
            debugMsg("Waiting for chest open delay: " + openDelay);
            if (openDelay == 0) {
                checkInventory(currentScreen);
            }
            return;
        }

        // Vérifier si Baritone a fini le pathing et si on est proche
        if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing() &&
            mc.player.squaredDistanceTo(Vec3d.ofCenter(currentChest)) < chestInteractRange.get() * chestInteractRange.get()) {

            if (!openingChest) {
                // Inspiré de StashMover2: vérifier la ligne de vision et ajuster la rotation
                if (interactWithBlock(currentChest)) {
                    openingChest = true;
                    interactDelay = chestInteractDelay.get();
                    debugMsg("Chest interaction initiated, waiting for screen to open");
                }
            }
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        debugMsg("OpenScreenEvent triggered for screen: " + (event.screen != null ? event.screen.getClass().getName() : "null"));
        if (openingChest && event.screen instanceof GenericContainerScreen containerScreen) {
            debugMsg("GenericContainerScreen opened for chest at " + currentChest + ", syncId: " + containerScreen.getScreenHandler().syncId);
            currentScreen = containerScreen;
            openDelay = chestOpenDelay.get(); // Attendre avant de vérifier l'inventaire
        }
    }

    private void checkInventory(GenericContainerScreen containerScreen) {
        boolean found = false;
        int slots = containerScreen.getScreenHandler().getInventory().size();
        debugMsg("Checking " + slots + " chest slots at " + currentChest);
        for (int i = 0; i < slots; i++) {
            ItemStack stack = containerScreen.getScreenHandler().getInventory().getStack(i);
            debugMsg("Chest slot " + i + ": " + (stack.isEmpty() ? "empty" : stack.getItem().getRegistryEntry().getKey().map(key -> key.getValue().toString()).orElse("unknown")));
            if (!stack.isEmpty() && checkStackForItem(stack, 0)) {
                found = true;
                break;
            }
        }

        if (found) {
            ChatUtils.info("Item trouvé dans le coffre (ou dans un shulker dedans) à %s!", currentChest);
            toggle();
        } else {
            debugMsg("No matching item found in chest at " + currentChest);
            closeChest(containerScreen);
            visited.add(currentChest);
            openingChest = false;
            currentScreen = null;
            currentIndex++;
            startPathToNextChest();
        }
    }

    private void closeChest(GenericContainerScreen containerScreen) {
        debugMsg("Closing chest at " + currentChest + ", syncId: " + containerScreen.getScreenHandler().syncId);
        mc.player.closeHandledScreen();
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(containerScreen.getScreenHandler().syncId));
    }

    private boolean interactWithBlock(BlockPos pos) {
        Vec3d vec = Vec3d.ofCenter(pos);
        BlockHitResult hitResult = new BlockHitResult(vec, Direction.UP, pos, false);
        float yaw = (float) Rotations.getYaw(pos);
        float pitch = (float) Rotations.getPitch(pos);
        if (!isBlockInLineOfVision(pos, chestInteractRange.get())) {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
            debugMsg("Set yaw: " + yaw + ", pitch: " + pitch + " to face chest at " + pos);
            return false;
        }
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        if (result == ActionResult.SUCCESS) {
            debugMsg("Interaction with chest at " + pos + " succeeded");
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        } else {
            debugMsg("Interaction with chest at " + pos + " failed: " + result);
            return false;
        }
    }

    private boolean isBlockInLineOfVision(BlockPos blockPos, double maxDistance) {
        double fovThreshold = 0.95; // Inspiré de debugTemp2 dans StashMover2
        Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
        Vec3d lookVec = mc.player.getRotationVec(1.0F).normalize();
        Vec3d blockVec = new Vec3d(
            blockPos.getX() + 0.5 - eyePos.x,
            blockPos.getY() + 0.5 - eyePos.y,
            blockPos.getZ() + 0.5 - eyePos.z
        ).normalize();
        double dotProduct = lookVec.dotProduct(blockVec);
        return dotProduct >= fovThreshold && eyePos.squaredDistanceTo(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= (maxDistance * maxDistance);
    }

    private boolean checkStackForItem(ItemStack stack, int depth) {
        if (depth > 5) {
            debugMsg("Depth limit reached (depth: " + depth + ")");
            return false;
        }

        Item item = stack.getItem();
        String itemId = item.getRegistryEntry().getKey().map(key -> key.getValue().toString()).orElse("");
        debugMsg("Checking item: " + itemId + " at depth " + depth);

        if (mode.get() == Mode.Nom && itemId.equals(itemName.get())) {
            debugMsg("Found matching item by name: " + itemName.get());
            return true;
        } else if (mode.get() == Mode.Type && itemTypes.get().contains(item)) {
            debugMsg("Found matching item by type: " + itemId);
            return true;
        }

        if (item instanceof BlockItem && ((BlockItem) item).getBlock() instanceof ShulkerBoxBlock) {
            debugMsg("Detected shulker box at depth " + depth);
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null) {
                debugMsg("Found ContainerComponent, checking contents...");
                int slotIndex = 0;
                for (ItemStack innerStack : container.iterateNonEmpty()) {
                    String innerItemId = innerStack.getItem().getRegistryEntry().getKey().map(key -> key.getValue().toString()).orElse("");
                    debugMsg("Checking shulker slot " + slotIndex + ": " + innerItemId);
                    if (checkStackForItem(innerStack, depth + 1)) {
                        debugMsg("Found target item in shulker slot " + slotIndex);
                        return true;
                    }
                    slotIndex++;
                }
            } else {
                debugMsg("No ContainerComponent found, falling back to NBT");
                try {
                    NbtElement nbtElement = stack.toNbt(mc.world.getRegistryManager(), new NbtCompound());
                    if (nbtElement == null) {
                        debugMsg("toNbt returned null for shulker box");
                        return false;
                    }
                    if (!(nbtElement instanceof NbtCompound nbt)) {
                        debugMsg("toNbt did not return a NbtCompound, found: " + nbtElement.getType());
                        return false;
                    }
                    if (nbt.contains("BlockEntityTag")) {
                        NbtElement blockEntityTagElement = nbt.get("BlockEntityTag");
                        if (blockEntityTagElement instanceof NbtCompound blockEntityTag) {
                            if (blockEntityTag.contains("Items")) {
                                NbtList itemsList = blockEntityTag.getList("Items").orElse(null);
                                if (itemsList != null) {
                                    debugMsg("Found " + itemsList.size() + " items in shulker NBT");
                                    for (int i = 0; i < itemsList.size(); i++) {
                                        NbtElement itemTagElement = itemsList.get(i);
                                        if (itemTagElement instanceof NbtCompound itemTag) {
                                            ItemStack innerStack = ItemStack.fromNbt(mc.world.getRegistryManager(), itemTag).orElse(ItemStack.EMPTY);
                                            String innerItemId = innerStack.getItem().getRegistryEntry().getKey().map(key -> key.getValue().toString()).orElse("");
                                            debugMsg("Checking NBT shulker slot " + i + ": " + innerItemId);
                                            if (checkStackForItem(innerStack, depth + 1)) {
                                                debugMsg("Found target item in NBT shulker slot " + i);
                                                return true;
                                            }
                                        } else {
                                            debugMsg("Item at slot " + i + " is not a NbtCompound, found: " + itemTagElement.getType());
                                        }
                                    }
                                } else {
                                    debugMsg("No Items tag in BlockEntityTag");
                                }
                            } else {
                                debugMsg("No Items tag found in BlockEntityTag");
                            }
                        } else {
                            debugMsg("BlockEntityTag is not a NbtCompound, found: " + blockEntityTagElement.getType());
                        }
                    } else {
                        debugMsg("No BlockEntityTag found in NBT");
                    }
                } catch (IllegalStateException e) {
                    debugMsg("Error: Cannot encode empty ItemStack: " + e.getMessage());
                    return false;
                } catch (Exception e) {
                    debugMsg("Error accessing NBT data: " + e.getMessage());
                    return false;
                }
            }
        }

        return false;
    }

    private void startPathToNextChest() {
        while (currentIndex < chests.size()) {
            currentChest = chests.get(currentIndex);
            if (!visited.contains(currentChest)) {
                debugMsg("Pathing to chest at " + currentChest);
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(currentChest, 1));
                return;
            }
            currentIndex++;
        }

        ChatUtils.error("Item non trouvé dans les coffres ou leurs shulkers.");
        toggle();
    }

    private void debugMsg(String msg) {
        if (debug.get()) {
            ChatUtils.info("[DEBUG] " + msg);
        }
    }

    public enum Mode {
        Nom,
        Type
    }
}
