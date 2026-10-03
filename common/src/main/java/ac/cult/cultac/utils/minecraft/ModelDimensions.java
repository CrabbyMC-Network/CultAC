package ac.cult.cultac.utils.minecraft;

import ac.cult.cultac.protocol.ProtocolVersion;
import com.google.gson.JsonParser;
import java.util.List;

/** Projects dimension schema fields exactly as the pinned ViaBackwards registry handlers do. */
final class ModelDimensions {
    private ModelDimensions() {}

    static String project(String json, ProtocolVersion source, ProtocolVersion target) {
        if (json == null || source.protocol() <= target.protocol()) return json;
        var dimension = JsonParser.parseString(json).getAsJsonObject();
        var attributes = dimension.getAsJsonObject("attributes");
        if (attributes == null) return json;
        // Protocol26_3To26_2#handleEnvironmentAttributes: older modifier semantics.
        if (source.protocol() >= 777 && target.protocol() <= 776) {
            attributes.entrySet().forEach(entry -> {
                if (!entry.getValue().isJsonObject()) return;
                var value = entry.getValue().getAsJsonObject();
                var modifier = value.get("modifier");
                if (modifier != null && List.of("append", "overlay").contains(modifier.getAsString()))
                    value.addProperty("modifier", "override");
            });
        }
        // Protocol26_1To1_21_11#removeEnvironmentAttributes: these three visual
        // attributes do not exist in 1.21.11's EnvironmentAttribute registry.
        if (source.protocol() >= 775 && target.protocol() <= 774) {
            for (String key :
                    List.of("visual/block_light_tint", "visual/night_vision_color", "visual/ambient_light_color")) {
                attributes.remove(key);
                attributes.remove("minecraft:" + key);
            }
        }
        return dimension.toString();
    }
}
