package ac.cult.cultac.checks.type;

import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.CheckInfo;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.anticheat.update.BlockPlace;
import ac.cult.cultac.utils.collisions.HitboxData;
import ac.cult.cultac.utils.collisions.datatypes.CollisionBox;
import ac.cult.cultac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.cult.cultac.utils.nmsutil.NmsBlockTags;
import ac.grim.grimac.api.config.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class BlockPlaceCheck extends Check implements CheckListener {
    private static final List<Block> weirdBoxes = new ArrayList<>();
    private static final List<Block> buggyBoxes = new ArrayList<>();
    protected int cancelVL;

    public BlockPlaceCheck(CultPlayer player) {
        super(player);
    }

    public BlockPlaceCheck(CultPlayer player, CheckInfo checkInfo) {
        super(player, checkInfo);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        if (getConfigName() != null) {
            cancelVL = config.getIntElse(getConfigName() + ".cancelVL", getDefaultCancelVL());
        }
    }

    @Contract(pure = true)
    protected int getDefaultCancelVL() {
        return 5;
    }

    protected boolean shouldCancel() {
        return cancelVL >= 0 && violations >= cancelVL;
    }

    // Method called immediately after a block is placed, before forwarding block place to server
    public void onBlockPlace(final BlockPlace place) {}

    // Method called the flying packet after the block place
    public void onPostFlyingBlockPlace(BlockPlace place) {}

    static {
        // Fences and walls aren't worth checking.
        // TODO: Cult should be accurate now to check this?

        // TODO: What do we do about blocks dependent upon lighting levels?
        weirdBoxes.addAll(new ArrayList<>(NmsBlockTags.blockValues(BlockTags.FENCES)));
        weirdBoxes.addAll(new ArrayList<>(NmsBlockTags.blockValues(BlockTags.WALLS)));
        weirdBoxes.add(Blocks.LECTERN);

        buggyBoxes.addAll(new ArrayList<>(NmsBlockTags.blockValues(BlockTags.DOORS)));
        buggyBoxes.addAll(new ArrayList<>(NmsBlockTags.blockValues(BlockTags.STAIRS)));
        buggyBoxes.add(Blocks.CHEST);
        buggyBoxes.add(Blocks.TRAPPED_CHEST);
        buggyBoxes.add(Blocks.CHORUS_PLANT);

        // The client changes these block states around when placing blocks, temporary desync
        buggyBoxes.add(Blocks.KELP);
        buggyBoxes.add(Blocks.KELP_PLANT);
        buggyBoxes.add(Blocks.TWISTING_VINES);
        buggyBoxes.add(Blocks.TWISTING_VINES_PLANT);
        buggyBoxes.add(Blocks.WEEPING_VINES);
        buggyBoxes.add(Blocks.WEEPING_VINES_PLANT);
        buggyBoxes.add(Blocks.REDSTONE_WIRE);
    }

    protected SimpleCollisionBox getCombinedBox(final BlockPlace place) {
        // Alright, instead of skidding AACAdditionsPro, let's just use bounding boxes
        BlockPos clicked = place.getPlacedAgainstBlockLocation();
        if (weirdBoxes.contains(place.getPlacedAgainstMaterial())
                || buggyBoxes.contains(place.getPlacedAgainstMaterial())) {
            return new SimpleCollisionBox(
                    clicked.getX() + 1,
                    clicked.getY() + 1,
                    clicked.getZ() + 1,
                    clicked.getX(),
                    clicked.getY(),
                    clicked.getZ());
        }

        CollisionBox placedOn = HitboxData.getBlockHitbox(
                player,
                player.compensatedWorld.getBlockDataAt(clicked),
                clicked.getX(),
                clicked.getY(),
                clicked.getZ());

        List<SimpleCollisionBox> boxes = new ArrayList<>();
        placedOn.downCast(boxes);

        SimpleCollisionBox combined = new SimpleCollisionBox(clicked.getX(), clicked.getY(), clicked.getZ());
        for (SimpleCollisionBox box : boxes) {
            double minX = Math.max(box.minX, combined.minX);
            double minY = Math.max(box.minY, combined.minY);
            double minZ = Math.max(box.minZ, combined.minZ);
            double maxX = Math.min(box.maxX, combined.maxX);
            double maxY = Math.min(box.maxY, combined.maxY);
            double maxZ = Math.min(box.maxZ, combined.maxZ);
            combined = new SimpleCollisionBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        return combined;
    }
}
