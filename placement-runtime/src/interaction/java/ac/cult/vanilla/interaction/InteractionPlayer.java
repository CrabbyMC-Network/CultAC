package ac.cult.vanilla.interaction;

import ac.cult.placement.api.InteractionEngine;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.level.GameType;

/** A normally constructed vanilla client player. All input is packet-compensated. */
final class InteractionPlayer extends Player {
    private final GameType mode;
    private final boolean gameMaster;

    InteractionPlayer(InteractionWorld world, InteractionEngine.Actor actor) {
        super(world, new GameProfile(new UUID(0, 0), "CultPrediction"));
        mode = GameType.valueOf(actor.gameMode());
        gameMaster = actor.gameMaster();
        mode.updatePlayerAbilities(getAbilities());
        getAbilities().instabuild = actor.instabuild();
        getAttribute(Attributes.SCALE).setBaseValue(actor.scale());
        getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(actor.blockRange());
        setPos(actor.x(), actor.y(), actor.z());
        setYRot(actor.yaw());
        setXRot(actor.pitch());
        setPose(Pose.valueOf(actor.pose()));
        setShiftKeyDown(actor.secondaryUse());
        getFoodData().setFoodLevel(actor.food());
        getInventory().setSelectedSlot(actor.selectedSlot());
        for (int slot = 0; slot < actor.inventory().size(); slot++)
            getInventory()
                    .setItem(slot, InteractionItems.decode(actor.inventory().get(slot), world.registryAccess()));
    }

    @Override
    public GameType gameMode() {
        return mode;
    }

    @Override
    public boolean canUseGameMasterBlocks() {
        return gameMaster;
    }

    @Override
    public boolean isLocalPlayer() {
        return true;
    }

    @Override
    protected ItemCooldowns createItemCooldowns() {
        return new RecordingCooldowns();
    }

    List<InteractionEngine.Cooldown> cooldowns() {
        return ((RecordingCooldowns) getCooldowns()).started;
    }

    private static final class RecordingCooldowns extends ItemCooldowns {
        final List<InteractionEngine.Cooldown> started = new ArrayList<>();

        @Override
        protected void onCooldownStarted(Identifier group, int ticks) {
            started.add(new InteractionEngine.Cooldown(group.toString(), ticks));
        }
    }
}
