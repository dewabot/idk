package com.chainmine;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public class ChainMine implements ModInitializer {
    public static final String MOD_ID = "chainmine";
    public static final int MAX_BLOCKS = 64;

    public static final TagKey<Block> CHAIN_MINEABLE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "chain_mineable"));

    // Tag pickaxe umum yang dipakai mod lain (Fabric/NeoForge convention).
    private static final TagKey<Item> C_PICKAXES =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/pickaxe"));

    // Cegah rekursi: destroyBlock memicu event break lagi.
    private static boolean running = false;

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (running || level.isClientSide()) return;
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return;
            if (!sp.isShiftKeyDown()) return;
            if (!state.is(CHAIN_MINEABLE)) return;
            if (!isPickaxe(sp.getMainHandItem(), state)) return;

            chain(sp, sl, pos, state.getBlock());
        });
    }

    /** Semua pickaxe: vanilla, tag c:tools/pickaxe (mod), atau tool apa pun yang benar untuk blok itu. */
    private static boolean isPickaxe(ItemStack tool, BlockState state) {
        if (tool.isEmpty()) return false;
        return tool.is(ItemTags.PICKAXES)
                || tool.is(C_PICKAXES)
                || tool.isCorrectToolForDrops(state);
    }

    private static void chain(ServerPlayer player, ServerLevel level, BlockPos origin, Block target) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);
        int broken = 0;

        running = true;
        try {
            while (!queue.isEmpty() && broken < MAX_BLOCKS) {
                BlockPos current = queue.poll();

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            if (broken >= MAX_BLOCKS) return;

                            BlockPos next = current.offset(dx, dy, dz);
                            if (!visited.add(next)) continue;

                            BlockState nextState = level.getBlockState(next);
                            if (!nextState.is(target)) continue;

                            ItemStack tool = player.getMainHandItem();
                            if (!isPickaxe(tool, nextState)) return;
                            // Sisakan 1 durability supaya pickaxe tidak pecah.
                            if (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1) return;

                            if (player.gameMode.destroyBlock(next)) {
                                broken++;
                                queue.add(next);
                            }
                        }
                    }
                }
            }
        } finally {
            running = false;
        }
    }
}

