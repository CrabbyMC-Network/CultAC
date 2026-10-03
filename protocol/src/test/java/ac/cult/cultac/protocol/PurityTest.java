package ac.cult.cultac.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PurityTest {
    @Test
    void noPlatformOrNonBufferNettyClassReferences() throws Exception {
        Path classes = Path.of(ProtocolVersion.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Pattern forbidden = Pattern.compile("net/minecraft/|org/bukkit/|io/papermc/|com/velocitypowered/|io/netty/(?!buffer/)");
        try (var paths = Files.walk(classes)) {
            var files = paths.filter(path -> path.toString().endsWith(".class")).toList();
            assertFalse(files.isEmpty());
            for (Path file : files) {
                String constants = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                assertFalse(forbidden.matcher(constants).find(), file.toString());
                if (file.toString().contains("/value/") || file.toString().contains("/packet/")) {
                    assertFalse(constants.contains("io/netty/"), "Buffer-backed value: " + file);
                }
            }
        }
        assertThrows(ClassNotFoundException.class, () -> Class.forName("net.minecraft.network.FriendlyByteBuf"));
    }
}
