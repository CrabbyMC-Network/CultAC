package ac.cult.cultac.protocol.value;

import java.util.List;
import java.util.Objects;

public record AttributeSnapshot(String attribute, double base, List<AttributeModifier> modifiers) {
    public AttributeSnapshot {
        Objects.requireNonNull(attribute, "attribute");
        modifiers = List.copyOf(modifiers);
    }
}
