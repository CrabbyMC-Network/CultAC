package ac.cult.cultac.utils.nmsutil;

import ac.cult.cultac.network.packet.EntityMetadata;
import java.util.List;

/** Consumed metadata indices on the pinned 26.3 server; client versions do not change this layout. */
public final class WatchableIndexUtil {
    public static final int ENTITY_SHARED_FLAGS = 0;
    public static final int ENTITY_NO_GRAVITY = 5;
    public static final int ENTITY_POSE = 6;
    public static final int ENTITY_TICKS_FROZEN = 7;
    public static final int LIVING_ENTITY_FLAGS = 8;
    public static final int LIVING_HEALTH = 9;
    public static final int LIVING_SLEEPING_POS = 14;
    public static final int MOB_FLAGS = 15;
    public static final int AGEABLE_BABY = 16;
    public static final int SLIME_SIZE = 18;
    public static final int PHANTOM_SIZE = 16;
    public static final int SHULKER_ATTACH_FACE = 16;
    public static final int SHULKER_PEEK = 17;
    public static final int PIG_BOOST_TIME = 18;
    public static final int STRIDER_BOOST_TIME = 18;
    public static final int HORSE_FLAGS = 18;
    public static final int CAMEL_DASH = 19;
    public static final int HAPPY_GHAST_STAYS_STILL = 19;
    public static final int NAUTILUS_DASH = 20;
    public static final int FIREWORK_ATTACHED_TO_TARGET = 9;
    public static final int FISHING_HOOKED_ENTITY = 8;

    private WatchableIndexUtil() {}

    public static EntityMetadata.Entry getIndex(List<EntityMetadata.Entry> objects, int index) {
        for (EntityMetadata.Entry object : objects) {
            if (object.id() == index) return object;
        }

        return null;
    }
}
