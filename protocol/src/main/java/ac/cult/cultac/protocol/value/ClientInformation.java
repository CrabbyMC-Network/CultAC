package ac.cult.cultac.protocol.value;

import java.util.Objects;

public record ClientInformation(String language, int viewDistance, ChatVisibility chatVisibility,
                                boolean chatColors, int modelCustomisation, MainHand mainHand,
                                boolean textFilteringEnabled, boolean allowsListing, ParticleStatus particleStatus) {
    public ClientInformation {
        Objects.requireNonNull(language);
        Objects.requireNonNull(chatVisibility);
        Objects.requireNonNull(mainHand);
        Objects.requireNonNull(particleStatus);
    }
}
