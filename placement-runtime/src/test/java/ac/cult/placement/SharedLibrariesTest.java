package ac.cult.placement;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

class SharedLibrariesTest {
    @Test
    void reuseFollowsClassResolutionWhenPluginResourceLookupHidesTheLibrary() throws Exception {
        Path library = Path.of(ClassReader.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        var host = new ClassLoader(ClassReader.class.getClassLoader()) {
            @Override
            public URL getResource(String name) {
                return null;
            }
        };
        assertNull(host.getResource("org/objectweb/asm/ClassReader.class"));
        assertSame(ClassReader.class, host.loadClass("org.objectweb.asm.ClassReader"));
        assertTrue(SharedLibraries.packages(List.of(library), host).contains("org.objectweb.asm"));
    }
}
