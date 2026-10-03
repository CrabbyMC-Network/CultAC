package ac.cult.cultac.protocol.value;

import java.util.Objects;

public record AttributeModifier(String id, double amount, Operation operation) {
    public AttributeModifier {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(operation, "operation");
    }

    public enum Operation {
        ADD_VALUE,
        ADD_MULTIPLIED_BASE,
        ADD_MULTIPLIED_TOTAL
    }
}
