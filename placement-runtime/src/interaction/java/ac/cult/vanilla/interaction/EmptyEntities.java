package ac.cult.vanilla.interaction;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;

/** Entity obstruction is supplied by the compensated world callback, never a live world. */
final class EmptyEntities implements LevelEntityGetter<Entity> {
    static final EmptyEntities INSTANCE = new EmptyEntities();

    public Entity get(int id) {
        return null;
    }

    public Entity get(UUID id) {
        return null;
    }

    public Iterable<Entity> getAll() {
        return List.of();
    }

    public <T extends Entity> void get(EntityTypeTest<Entity, T> type, AbortableIterationConsumer<T> visitor) {}

    public void get(AABB box, Consumer<Entity> visitor) {}

    public <T extends Entity> void get(
            EntityTypeTest<Entity, T> type, AABB box, AbortableIterationConsumer<T> visitor) {}
}
