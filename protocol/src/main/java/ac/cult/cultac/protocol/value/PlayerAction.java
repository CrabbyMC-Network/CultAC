/*
 * Action mapping adapted from PacketEvents DiggingAction,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.value;

import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.UnsupportedOnVersionException;

/** Action names in 26.3 wire order. */
public enum PlayerAction {
    START_DESTROY_BLOCK,
    CHANGE_DESTROY_DIRECTION,
    ABORT_DESTROY_BLOCK,
    STOP_DESTROY_BLOCK,
    DROP_ALL_ITEMS,
    DROP_ITEM,
    RELEASE_USE_ITEM,
    SWAP_ITEM_WITH_OFFHAND,
    STAB;

    /** Also preserves native action IDs in stored check verbose data. */
    public int wireId(ProtocolVersion version) {
        if (this == CHANGE_DESTROY_DIRECTION && !version.atLeast(ProtocolVersion.V26_3)
                || this == STAB && !version.atLeast(ProtocolVersion.V1_21_11)) {
            throw new UnsupportedOnVersionException(this + " is unavailable on " + version);
        }
        return version.atLeast(ProtocolVersion.V26_3) || this == START_DESTROY_BLOCK ? ordinal() : ordinal() - 1;
    }
}
