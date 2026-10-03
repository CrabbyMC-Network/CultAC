package ac.cult.cultac.utils.nmsutil;

import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.bedrock.replay.offline.OfflineCultTestBootstrap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import org.junit.jupiter.api.Test;

class NativeEntityClassificationTest {
    @Test
    @SuppressWarnings("unchecked")
    void everyRegisteredTypeHasItsNativeClass() throws Exception {
        OfflineCultTestBootstrap.installConfig();
        var field = EntityTypeUtil.class.getDeclaredField("NMS_ENTITY_CLASSES");
        field.setAccessible(true);
        var classes = (Map<EntityType<?>, Class<? extends Entity>>) field.get(null);
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            Class<?> entityClass = classes.get(type);
            assertNotNull(entityClass, () -> "No native entity class for " + EntityTypeUtil.getKey(type));
            assertEquals(LivingEntity.class.isAssignableFrom(entityClass), EntityTypeUtil.isLiving(type));
            assertEquals(Animal.class.isAssignableFrom(entityClass), EntityTypeUtil.isAnimal(type));
            assertEquals(AgeableMob.class.isAssignableFrom(entityClass), EntityTypeUtil.isAgeable(type));
        }
    }
}
