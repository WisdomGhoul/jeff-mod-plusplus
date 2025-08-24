package com.stash.hunt.modules;

import com.stash.hunt.Addon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class AutoPortal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks between each obsidian placement.")
        .defaultValue(2)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place each tick.")
        .defaultValue(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders the portal frame as it's being placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the box is rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(100, 100, 255, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(100, 100, 255, 255))
        .build()
    );

    private final List<BlockPos> portalBlocks = new ArrayList<>();
    private int index = 0;
    private int delay = 0;
    private Direction chosenDirection;

    public AutoPortal() {
        super(Addon.CATEGORY, "AutoPortal", "Automatically builds a Nether portal near the cursor. Not working on 9B, WIP");
    }

    @Override
    public void onActivate() {
        portalBlocks.clear();
        index = 0;
        delay = 0;
        chosenDirection = null;

        BlockHitResult hitResult = mc.crosshairTarget instanceof BlockHitResult bhr ? bhr : null;
        BlockPos target = (hitResult != null) ? hitResult.getBlockPos() : mc.player.getBlockPos();

        // Cherche un emplacement valide autour du curseur
        int radius = 3;
        boolean found = false;
        outer:
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos base = target.add(dx, 1, dz);
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    if (canBuildPortal(base, dir)) {
                        buildPortalBlocks(base, dir);
                        chosenDirection = dir;
                        found = true;
                        break outer;
                    }
                }
            }
        }

        if (!found) {
            error("Aucun emplacement valide trouvé pour construire le portail.");
            toggle();
            return;
        }

        // Sélection de l'obsidienne dans la hotbar
        FindItemResult obsidianResult = InvUtils.find(Items.OBSIDIAN);
        if (!obsidianResult.found()) {
            error("Pas d'obsidienne dans la hotbar.");
            toggle();
            return;
        }
        InvUtils.swap(obsidianResult.slot(), false);
    }

    private boolean canBuildPortal(BlockPos base, Direction right) {
        int[][] offsets = {
            {0,0},{1,0},{2,0},{3,0},
            {0,1},{0,2},{0,3},{0,4},
            {3,1},{3,2},{3,3},{3,4},
            {1,4},{2,4}
        };
        for (int[] off : offsets) {
            BlockPos pos = base.add(right.getOffsetX() * off[0], off[1], right.getOffsetZ() * off[0]);

            // Vérifie que le bloc est à portée (reach distance 5)
            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            if (distance > 5.0) return false;

            if (!mc.world.getBlockState(pos).isAir() && !mc.world.getBlockState(pos).isReplaceable()) return false;
            if (off[1] == 0 && !mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) return false;
        }
        return true;
    }

    private void buildPortalBlocks(BlockPos base, Direction right) {
        portalBlocks.clear();
        portalBlocks.add(base.add(0, 0, 0));
        portalBlocks.add(base.add(right.getOffsetX(), 0, right.getOffsetZ()));
        portalBlocks.add(base.add(right.getOffsetX() * 2, 0, right.getOffsetZ() * 2));
        portalBlocks.add(base.add(right.getOffsetX() * 3, 0, right.getOffsetZ() * 3));

        portalBlocks.add(base.add(0, 1, 0));
        portalBlocks.add(base.add(0, 2, 0));
        portalBlocks.add(base.add(0, 3, 0));
        portalBlocks.add(base.add(0, 4, 0));

        portalBlocks.add(base.add(right.getOffsetX() * 3, 1, right.getOffsetZ() * 3));
        portalBlocks.add(base.add(right.getOffsetX() * 3, 2, right.getOffsetZ() * 3));
        portalBlocks.add(base.add(right.getOffsetX() * 3, 3, right.getOffsetZ() * 3));
        portalBlocks.add(base.add(right.getOffsetX() * 3, 4, right.getOffsetZ() * 3));

        portalBlocks.add(base.add(right.getOffsetX(), 4, right.getOffsetZ()));
        portalBlocks.add(base.add(right.getOffsetX() * 2, 4, right.getOffsetZ() * 2));
    }

    @Override
    public void onDeactivate() {
        portalBlocks.clear();
        index = 0;
        delay = 0;
        chosenDirection = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem blockItem)) return;
        if (blockItem.getBlock().asItem() != Items.OBSIDIAN) return;

        if (index >= portalBlocks.size()) return;

        delay++;
        if (delay < placeDelay.get()) return;

        for (int i = 0; i < blocksPerTick.get() && index < portalBlocks.size(); i++, index++) {
            BlockPos pos = portalBlocks.get(index);
            BlockHitResult bhr = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, bhr, mc.player.currentScreenHandler.getRevision()));
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        delay = 0;

        // Auto-light
        if (index >= portalBlocks.size()) {
            FindItemResult flintResult = InvUtils.findInHotbar(Items.FLINT_AND_STEEL);
            if (flintResult.found()) {
                InvUtils.swap(flintResult.slot(), false);
                BlockPos firePos = portalBlocks.get(0).up();
                BlockHitResult fireHit = new BlockHitResult(Vec3d.ofCenter(firePos), Direction.UP, firePos, false);
                mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, fireHit, mc.player.currentScreenHandler.getRevision()));
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            info("Portal complete. AutoPortal disabled.");
            toggle();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;
        for (int i = index; i < portalBlocks.size(); i++) {
            event.renderer.box(portalBlocks.get(i), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }
}
