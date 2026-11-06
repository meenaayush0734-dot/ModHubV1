package com.modhub.hud;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/**
 * Top-down minimap in the top-right corner. Shows loaded terrain around the player,
 * the player (white dot) and other players (blue dots). Client-side only; it only
 * reads chunks the game has already loaded, so it never requests extra world data.
 */
public final class MiniMap {
    private static final int SIZE = 96;          // blocks shown per side, 1 block = 1 screen pixel
    private static final int MARGIN = 8;
    private static final int REFRESH_TICKS = 10; // how often the terrain is re-sampled
    private static final int FRAME = 0xFF2E2960;
    private static final int SELF_DOT = 0xFFFFFFFF;
    private static final int OTHER_DOT = 0xFF4DA3FF;
    private static final int UNLOADED = 0xFF0B0D1A;

    private static final int[] colors = new int[SIZE * SIZE];
    private static boolean enabled = true;
    private static int tick;
    private static int centerX;
    private static int centerZ;
    private static boolean hasData;

    private MiniMap() {
    }

    public static void toggle() {
        enabled = !enabled;
    }

    /** Called once per client tick. */
    public static void tick() {
        tick++;
    }

    public static void render(DrawContext ctx) {
        if (!enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        PlayerEntity self = mc.player;
        if (world == null || self == null) return;

        int px = self.getBlockX();
        int pz = self.getBlockZ();
        boolean moved = Math.abs(px - centerX) > 8 || Math.abs(pz - centerZ) > 8;
        if (!hasData || tick % REFRESH_TICKS == 0 || moved) {
            refresh(world, px, pz);
        }

        int left = mc.getWindow().getScaledWidth() - SIZE - MARGIN;
        int top = MARGIN;

        ctx.fill(left - 2, top - 2, left + SIZE + 2, top + SIZE + 2, FRAME);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int c = colors[y * SIZE + x];
                ctx.fill(left + x, top + y, left + x + 1, top + y + 1, c);
            }
        }

        for (PlayerEntity other : world.getPlayers()) {
            int dx = other.getBlockX() - centerX + SIZE / 2;
            int dz = other.getBlockZ() - centerZ + SIZE / 2;
            if (dx < 1 || dz < 1 || dx >= SIZE - 1 || dz >= SIZE - 1) continue;
            int color = other == self ? SELF_DOT : OTHER_DOT;
            ctx.fill(left + dx - 1, top + dz - 1, left + dx + 2, top + dz + 2, color);
        }
    }

    private static void refresh(ClientWorld world, int px, int pz) {
        centerX = px;
        centerZ = pz;
        int startX = centerX - SIZE / 2;
        int startZ = centerZ - SIZE / 2;

        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int wx = startX + x;
                int wz = startZ + z;
                int color = UNLOADED;
                if (world.getChunkManager().isChunkLoaded(wx >> 4, wz >> 4)) {
                    int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, wx, wz);
                    BlockPos pos = new BlockPos(wx, topY - 1, wz);
                    BlockState state = world.getBlockState(pos);
                    color = 0xFF000000 | state.getMapColor(world, pos).color;
                }
                colors[z * SIZE + x] = color;
            }
        }
        hasData = true;
    }
}
