package ac.cult.runtime;

/** Named vanilla model families; wire protocol and model protocol remain distinct. */
public enum RuntimeModel {
    JAVA_1_21_11("1.21.11", "1.21.11_unobfuscated", 774),
    JAVA_26_3("26.3", "26.3", 777);

    private final String version;
    private final String minecraftId;
    private final int protocol;

    RuntimeModel(String version, String minecraftId, int protocol) {
        this.version = version;
        this.minecraftId = minecraftId;
        this.protocol = protocol;
    }

    public String version() {
        return version;
    }

    public String minecraftId() {
        return minecraftId;
    }

    public int protocol() {
        return protocol;
    }

    public static RuntimeModel forBackendProtocol(int protocol) {
        if (protocol >= 768 && protocol <= 774) return JAVA_1_21_11;
        if (protocol >= 775 && protocol <= 777) return JAVA_26_3;
        throw new IllegalArgumentException("Unsupported backend protocol " + protocol);
    }

    static RuntimeModel forVersion(String version) {
        for (var model : values()) if (model.version.equals(version)) return model;
        throw new IllegalArgumentException("Unsupported vanilla model " + version);
    }
}
