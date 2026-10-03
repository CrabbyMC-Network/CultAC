package ac.cult.cultac.network.event;

import ac.cult.cultac.network.CultWrite;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.PacketDirection;
import ac.cult.cultac.protocol.PacketType;
import java.util.ArrayList;
import java.util.List;

public final class PacketSendEvent<R> extends PacketEvent<R> {
    private final boolean insideBundle;
    private List<CultWrite> before, after;
    private List<Runnable> tasks;
    private boolean bundle;

    public PacketSendEvent(User user, ConnectionPhase phase, PacketType<R> type, R packet, boolean insideBundle) {
        super(user, phase, type, packet);
        if (type.direction() != PacketDirection.CLIENTBOUND)
            throw new IllegalArgumentException("Send event requires clientbound packet");
        this.insideBundle = insideBundle;
    }

    public List<CultWrite> getWritesBeforeSend() {
        if (before == null) before = new ArrayList<>(2);
        return before;
    }

    public List<CultWrite> getWritesAfterSend() {
        if (after == null) after = new ArrayList<>(2);
        return after;
    }

    public List<Runnable> getTasksAfterSend() {
        if (tasks == null) tasks = new ArrayList<>(2);
        return tasks;
    }

    public boolean isInsideBundle() {
        return insideBundle;
    }

    public void bundle() {
        bundle = true;
    }

    public boolean isBundleRequested() {
        return bundle;
    }

    public List<CultWrite> writesBefore() {
        return before == null ? List.of() : before;
    }

    public List<CultWrite> writesAfter() {
        return after == null ? List.of() : after;
    }

    public List<Runnable> tasksAfter() {
        return tasks == null ? List.of() : tasks;
    }

    @Override
    public void discardChanges() {
        super.discardChanges();
        if (before != null) before.clear();
        if (after != null) after.clear();
        if (tasks != null) tasks.clear();
        bundle = false;
    }
}
