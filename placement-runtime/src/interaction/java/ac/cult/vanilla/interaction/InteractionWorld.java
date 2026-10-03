package ac.cult.vanilla.interaction;

import ac.cult.placement.api.InteractionEngine;
import ac.cult.placement.api.PlacementEngine;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;

/** Vanilla Level and chunk lifecycle over a per-action compensated overlay. */
final class InteractionWorld extends ModelLevel {
    private final PlacementEngine.World input;
    private final Map<BlockPos, BlockState> overlay = new HashMap<>();
    private final Map<Long, LevelChunk> chunks = new HashMap<>();
    final List<PlacementEngine.Write> writes = new ArrayList<>();
    private final ChunkSource source;
    private final WorldBorder border;
    private final EnvironmentAttributeSystem attributes;
    private final TickRateManager tickRate = new TickRateManager();
    private final Scoreboard scoreboard = new Scoreboard();

    InteractionWorld(InteractionEngine.Request request, RegistryAccess registries) {
        super(
                new Data(),
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(request.dimension())),
                registries,
                request.dimensionType() == null
                        ? registries
                                .lookupOrThrow(Registries.DIMENSION_TYPE)
                                .getOrThrow(ResourceKey.create(
                                        Registries.DIMENSION_TYPE, Identifier.parse(request.dimension())))
                        : Holder.direct(DimensionType.DIRECT_CODEC
                                .parse(
                                        RegistryOps.create(JsonOps.INSTANCE, registries),
                                        JsonParser.parseString(request.dimensionType()))
                                .getOrThrow()),
                true,
                false,
                0,
                512);
        input = request.world();
        border = new WorldBorder() {
            @Override
            public boolean isWithinBounds(BlockPos pos) {
                return input.insideBorder(pos.getX(), pos.getZ());
            }
        };
        source = new Source();
        attributes = EnvironmentAttributeSystem.builder().addDefaultLayers(this).build();
    }

    @Override
    public int getMinY() {
        return input.minY();
    }

    @Override
    public int getHeight() {
        return input.height();
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return isInValidBounds(pos)
                ? overlay.getOrDefault(pos, Block.stateById(input.stateAt(pos.getX(), pos.getY(), pos.getZ())))
                : Blocks.VOID_AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public boolean hasChunk(int x, int z) {
        return input.loaded(x, z);
    }

    @Override
    public LevelChunk getChunk(int x, int z) {
        long key = Integer.toUnsignedLong(x) | Integer.toUnsignedLong(z) << 32;
        return chunks.computeIfAbsent(key, ignored -> new Chunk(new ChunkPos(x, z)));
    }

    @Override
    public boolean isUnobstructed(BlockState state, BlockPos pos, CollisionContext context) {
        return input.clear(
                new PlacementEngine.Pos(pos.getX(), pos.getY(), pos.getZ()),
                state.getCollisionShape(this, pos, context).toAabbs().stream()
                        .map(b -> new PlacementEngine.Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ))
                        .toList());
    }

    @Override
    public int getRawBrightness(BlockPos pos, int skyDarken) {
        return input.brightness(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public ChunkSource getChunkSource() {
        return source;
    }

    @Override
    public WorldBorder getWorldBorder() {
        return border;
    }

    @Override
    public EnvironmentAttributeSystem environmentAttributes() {
        return attributes;
    }

    @Override
    public TickRateManager tickRateManager() {
        return tickRate;
    }

    @Override
    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return FeatureFlags.DEFAULT_FLAGS;
    }

    @Override
    public RecipeAccess recipeAccess() {
        return new RecipeAccess() {
            public RecipePropertySet propertySet(ResourceKey<RecipePropertySet> key) {
                return RecipePropertySet.EMPTY;
            }

            public SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutterRecipes() {
                return SelectableRecipe.SingleInputSet.empty();
            }
        };
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return EmptyEntities.INSTANCE;
    }

    @Override
    public Entity getEntity(int id) {
        return null;
    }

    @Override
    public List<? extends Player> players() {
        return List.of();
    }

    @Override
    public Collection<net.minecraft.world.entity.boss.enderdragon.EnderDragonPart> dragonParts() {
        return List.of();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState old, BlockState state, int flags) {}

    @Override
    public void playSeededSound(
            Entity source,
            double x,
            double y,
            double z,
            Holder<net.minecraft.sounds.SoundEvent> sound,
            net.minecraft.sounds.SoundSource category,
            float volume,
            float pitch,
            long seed) {}

    @Override
    public void playSeededSound(
            Entity source,
            Entity target,
            Holder<net.minecraft.sounds.SoundEvent> sound,
            net.minecraft.sounds.SoundSource category,
            float volume,
            float pitch,
            long seed) {}

    @Override
    public void explode(
            Entity source,
            net.minecraft.world.damagesource.DamageSource damage,
            net.minecraft.world.level.ExplosionDamageCalculator calculator,
            double x,
            double y,
            double z,
            float power,
            boolean fire,
            ExplosionInteraction interaction,
            net.minecraft.core.particles.ParticleOptions small,
            net.minecraft.core.particles.ParticleOptions large,
            net.minecraft.util.random.WeightedList<net.minecraft.core.particles.ExplosionParticleInfo> particles,
            Holder<net.minecraft.sounds.SoundEvent> sound) {
        throw new IllegalStateException("A client interaction cannot run a server explosion");
    }

    @Override
    public String gatherChunkSourceStats() {
        return "compensated-interaction";
    }

    @Override
    public LevelData.RespawnData getRespawnData() {
        return levelData.getRespawnData();
    }

    @Override
    public void setRespawnData(LevelData.RespawnData respawn) {
        levelData.setSpawn(respawn);
    }

    @Override
    public MapItemSavedData getMapData(MapId id) {
        return null;
    }

    @Override
    public void destroyBlockProgress(int id, BlockPos pos, int progress) {}

    @Override
    public void levelEvent(Entity actor, int event, BlockPos pos, int data) {}

    @Override
    public void gameEvent(Holder<GameEvent> event, net.minecraft.world.phys.Vec3 pos, GameEvent.Context context) {}

    private final class Chunk extends LevelChunk {
        Chunk(ChunkPos pos) {
            super(InteractionWorld.this, pos);
            for (int index = 0; index < sections.length; index++)
                sections[index] = new Section(pos.getMinBlockX(), getMinY() + index * 16, pos.getMinBlockZ());
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return InteractionWorld.this.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }
    }

    /** Materialize packet state only when vanilla touches a section; retain vanilla palette/lifecycle logic. */
    private final class Section extends LevelChunkSection {
        private final int baseX, baseY, baseZ;
        private boolean populated;

        Section(int x, int y, int z) {
            super(palettedContainerFactory());
            baseX = x;
            baseY = y;
            baseZ = z;
        }

        private void populate() {
            if (populated) return;
            populated = true;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        super.setBlockState(
                                x,
                                y,
                                z,
                                InteractionWorld.this.getBlockState(new BlockPos(baseX + x, baseY + y, baseZ + z)),
                                false);
        }

        @Override
        public BlockState getBlockState(int x, int y, int z) {
            populate();
            return super.getBlockState(x, y, z);
        }

        @Override
        public FluidState getFluidState(int x, int y, int z) {
            return getBlockState(x, y, z).getFluidState();
        }

        @Override
        public boolean hasOnlyAir() {
            populate();
            return super.hasOnlyAir();
        }

        @Override
        public boolean maybeHas(java.util.function.Predicate<BlockState> predicate) {
            populate();
            return super.maybeHas(predicate);
        }

        @Override
        public net.minecraft.world.level.chunk.PalettedContainer<BlockState> getStates() {
            populate();
            return super.getStates();
        }

        @Override
        public BlockState setBlockState(int x, int y, int z, BlockState state, boolean lock) {
            populate();
            var old = super.setBlockState(x, y, z, state, lock);
            if (old != state) {
                var pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                overlay.put(pos, state);
                writes.add(new PlacementEngine.Write(
                        new PlacementEngine.Pos(pos.getX(), pos.getY(), pos.getZ()), Block.getId(state)));
            }
            return old;
        }
    }

    private final class Source extends ChunkSource {
        private final LevelLightEngine light =
                new LevelLightEngine(this, true, dimensionType().hasSkyLight());

        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
            return input.loaded(x, z) ? InteractionWorld.this.getChunk(x, z) : null;
        }

        public void tick(java.util.function.BooleanSupplier budget, boolean tickChunks) {}

        public String gatherStats() {
            return "compensated-interaction";
        }

        public int getLoadedChunksCount() {
            return chunks.size();
        }

        public LevelLightEngine getLightEngine() {
            return light;
        }

        public net.minecraft.world.level.BlockGetter getLevel() {
            return InteractionWorld.this;
        }
    }

    private static final class Data implements WritableLevelData {
        private RespawnData respawn = RespawnData.DEFAULT;

        public RespawnData getRespawnData() {
            return respawn;
        }

        public void setSpawn(RespawnData value) {
            respawn = value;
        }

        public long getGameTime() {
            return 0;
        }

        public long getDayTime() {
            return 0;
        }

        public boolean isThundering() {
            return false;
        }

        private boolean raining;

        public boolean isRaining() {
            return raining;
        }

        public void setRaining(boolean value) {
            raining = value;
        }

        public boolean isHardcore() {
            return false;
        }

        public Difficulty getDifficulty() {
            return Difficulty.NORMAL;
        }

        public boolean isDifficultyLocked() {
            return false;
        }
    }
}
