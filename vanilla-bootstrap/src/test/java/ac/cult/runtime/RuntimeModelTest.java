package ac.cult.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RuntimeModelTest {
    @Test
    void olderBackendsSelectTheNamed12111ModelRegardlessOfNewerClientVersions() {
        for (int backend = 768; backend <= 774; backend++) {
            assertSame(RuntimeModel.JAVA_1_21_11, RuntimeModel.forBackendProtocol(backend));
        }
        assertEquals("1.21.11_unobfuscated", RuntimeModel.JAVA_1_21_11.minecraftId());
    }

    @Test
    void modernBackendsUseThe263Model() {
        for (int backend = 775; backend <= 777; backend++) {
            assertSame(RuntimeModel.JAVA_26_3, RuntimeModel.forBackendProtocol(backend));
        }
    }

    @Test
    void unsupportedBackendsCannotFallThroughToTheLatestModel() {
        for (int backend : new int[] {-1, 767, 778, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> RuntimeModel.forBackendProtocol(backend));
        }
    }
}
