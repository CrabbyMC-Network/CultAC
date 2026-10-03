package ac.cult.cultac.protocol;

/** Connection-owned values needed by a platform codec; never retained by a shared catalog. */
public interface CodecState {
    CodecState EMPTY = new CodecState() {
        @Override
        public <T> T require(Class<T> type) {
            throw new IllegalStateException("No connection codec state for " + type.getName());
        }
    };

    <T> T require(Class<T> type);
}
