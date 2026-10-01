package com.example.godcore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class GodCoreMod implements ModInitializer {
    public static final String MOD_ID = "godcore";
    private static final int CRATER_RADIUS = 100;
    private static final int CRATER_DEPTH = 54;
    private static final Queue<CraterJob> CRATER_JOBS = new ArrayDeque<>();
    private static final Item CABOOM = register("caboom", CaboomItem::new);
    private static final Item FLIGHT = register("flight", FlightItem::new);

    private static <T extends Item> T register(String path, Function<Item.Properties, T> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, path));
        T item = factory.apply(new Item.Properties().setId(key).stacksTo(1));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(CABOOM);
            entries.accept(FLIGHT);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CraterJob job = CRATER_JOBS.peek();
            if (job != null && job.process(300)) CRATER_JOBS.remove();
        });
    }

    private static final class CaboomItem extends Item {
        private CaboomItem(Properties properties) { super(properties); }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            HitResult hit = player.pick(160.0, 1.0F, false);
            Vec3 point = hit.getType() == HitResult.Type.BLOCK
                    ? hit.getLocation()
                    : player.getEyePosition().add(player.getLookAngle().scale(80.0));
            startCrater((ServerLevel) level, BlockPos.containing(point), player);
            player.displayClientMessage(Component.literal("EMPEROR TIME"), true);
            return InteractionResult.SUCCESS;
        }
    }

    private static final class FlightItem extends Item {
        private FlightItem(Properties properties) { super(properties); }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            boolean enabled = player.getTags().contains("godcore_flight");
            if (enabled) {
                player.removeTag("godcore_flight");
                player.getAbilities().mayfly = player.getAbilities().instabuild || player.isSpectator();
                if (!player.getAbilities().mayfly) player.getAbilities().flying = false;
                player.displayClientMessage(Component.literal("Flight OFF"), true);
            } else {
                player.addTag("godcore_flight");
                player.getAbilities().mayfly = true;
                player.displayClientMessage(Component.literal("Flight ON"), true);
            }
            player.onUpdateAbilities();
            return InteractionResult.SUCCESS;
        }
    }

    private static void startCrater(ServerLevel level, BlockPos center, Player caster) {
        int cx = center.getX();
        int cz = center.getZ();
        int baseY = center.getY();
        long seed = level.getGameTime() ^ caster.getUUID().getMostSignificantBits();
        level.playSound(null, center, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5.0F, 0.65F);

        BlockParticleOption sculkShard = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SCULK.defaultBlockState());
        BlockParticleOption stoneShard = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState());
        level.sendParticles(ParticleTypes.SONIC_BOOM, cx + 0.5, baseY + 1.0, cz + 0.5, 1, 0, 0, 0, 0);
        level.sendParticles(sculkShard, cx + 0.5, baseY + 12.0, cz + 0.5, 2200, 42, 13, 42, 0.72);
        level.sendParticles(stoneShard, cx + 0.5, baseY + 8.0, cz + 0.5, 850, 38, 9, 38, 0.55);
        level.sendParticles(ParticleTypes.SCULK_SOUL, cx + 0.5, baseY + 16.0, cz + 0.5, 900, 40, 22, 40, 0.11);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx + 0.5, baseY + 4.0, cz + 0.5, 650, 35, 8, 35, 0.08);
        for (int i = 0; i < 3; i++) {
            level.sendParticles(ParticleTypes.SONIC_BOOM, cx + 0.5, baseY + 12 + i * 18, cz + 0.5, 1, 0, 0, 0, 0);
        }

        List<CraterColumn> columns = new ArrayList<>(31_500);
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (int dx = -CRATER_RADIUS; dx <= CRATER_RADIUS; dx++) {
            for (int dz = -CRATER_RADIUS; dz <= CRATER_RADIUS; dz++) {
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > CRATER_RADIUS * CRATER_RADIUS) continue;
                int x = cx + dx;
                int z = cz + dz;
                check.set(x, baseY, z);
                if (!level.hasChunkAt(check)) continue;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                long hash = mix(seed + dx * 341873128712L + dz * 132897987541L);
                double distance = Math.sqrt(distanceSquared);
                double bowl = 1.0 - (distanceSquared / (double) (CRATER_RADIUS * CRATER_RADIUS));
                int roughness = (int) Math.floorMod(hash, 7L) - 3;
                int floor = Math.max(level.getMinY() + 4,
                        surface - (int) Math.round(CRATER_DEPTH * bowl) + roughness);
                columns.add(new CraterColumn(x, z, surface, floor, distance, distanceSquared, hash));
            }
        }
        columns.sort(Comparator.comparingInt(CraterColumn::distanceSquared));
        CRATER_JOBS.add(new CraterJob(level, columns));
    }

    private record CraterColumn(int x, int z, int surface, int floor, double distance, int distanceSquared, long hash) { }

    private static final class CraterJob {
        private final ServerLevel level;
        private final List<CraterColumn> columns;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private int index;

        private CraterJob(ServerLevel level, List<CraterColumn> columns) {
            this.level = level;
            this.columns = columns;
        }

        private boolean process(int budget) {
            int end = Math.min(index + budget, columns.size());
            while (index < end) buildColumn(columns.get(index++));
            return index >= columns.size();
        }

        private void buildColumn(CraterColumn column) {
            int x = column.x();
            int z = column.z();
            int floor = column.floor();
            long hash = column.hash();
            pos.set(x, floor, z);
            if (!level.hasChunkAt(pos)) return;

            for (int y = floor + 1; y <= column.surface() && y < level.getMaxY(); y++) {
                pos.set(x, y, z);
                if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }

            pos.set(x, floor, z);
            if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
                int material = (int) Math.floorMod(hash >>> 5, 100L);
                level.setBlock(pos, material < 76 ? Blocks.SCULK.defaultBlockState()
                        : material < 91 ? Blocks.DEEPSLATE.defaultBlockState()
                        : Blocks.SOUL_SAND.defaultBlockState(), 3);
            }

            double distance = column.distance();
            int shardChance = distance < 72 ? 4 : 8;
            if (distance < 94 && Math.floorMod(hash >>> 13, shardChance) == 0) {
                int height = 3 + (int) Math.floorMod(hash >>> 21, 19L);
                if (distance > 72) height += (int) ((distance - 72) * 0.55);
                for (int h = 1; h <= height; h++) {
                    int offsetX = h > height * 0.72 && Math.floorMod(hash + h, 3) == 0 ? 1 : 0;
                    pos.set(x + offsetX, floor + h, z);
                    if (pos.getY() >= level.getMaxY()) break;
                    level.setBlock(pos, Math.floorMod(hash + h, 5) == 0
                            ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.SCULK.defaultBlockState(), 3);
                }
                if (Math.floorMod(hash >>> 30, 4) == 0) {
                    level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x + 0.5, floor + height + 0.5, z + 0.5, 2, 0.3, 0.5, 0.3, 0.02);
                }
            }

            if (distance < 86 && Math.floorMod(hash >>> 37, 31) == 0) {
                pos.set(x, floor + 1, z);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.SOUL_FIRE.defaultBlockState(), 3);
                }
            }
        }
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
