package ac.cult.cultac.bridge.wire;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;

/** Holds original packets until a verdict, preserving projection-before-next-input ordering. */
public final class ValidatedInputQueue<T> {
    private final ArrayDeque<T> queued=new ArrayDeque<>();
    private final int capacity;
    private final Consumer<T> request,release;
    private T waiting;
    private boolean completing,closed;
    public ValidatedInputQueue(int capacity,Consumer<T> request,Consumer<T> release){
        if(capacity<1)throw new IllegalArgumentException("Invalid queue capacity");
        this.capacity=capacity;this.request=Objects.requireNonNull(request);this.release=Objects.requireNonNull(release);
    }
    /** Transfers ownership even when rejection throws; callers must never release the packet. */
    public void offer(T packet){
        Objects.requireNonNull(packet);
        boolean rejected;
        synchronized(this){
            rejected=closed||queued.size()+(waiting==null?0:1)>=capacity;
            if(!rejected)queued.add(packet);
        }
        if(rejected){release.accept(packet);throw new IllegalStateException("Bridge queue unavailable");}
        dispatch();
    }
    public void complete(T exact,Consumer<T> projection){
        synchronized(this){
            if(closed||waiting!=exact||completing)throw new IllegalStateException("Wrong bridge verdict owner");
            waiting=null;completing=true;
        }
        try{projection.accept(exact);}catch(RuntimeException|Error failure){close();throw failure;}
        finally{
            try{release.accept(exact);}catch(RuntimeException|Error failure){close();throw failure;}
            finally{synchronized(this){completing=false;}}
        }
        dispatch();
    }
    private void dispatch(){
        T next;
        synchronized(this){if(closed||completing||waiting!=null||(next=queued.poll())==null)return;waiting=next;}
        try{request.accept(next);}catch(RuntimeException|Error failure){close();throw failure;}
    }
    public void close(){
        ArrayDeque<T> discarded;
        synchronized(this){if(closed)return;closed=true;discarded=new ArrayDeque<>(queued);queued.clear();if(waiting!=null){discarded.addFirst(waiting);waiting=null;}}
        Throwable failure=null;
        for(T packet:discarded){
            try{release.accept(packet);}catch(RuntimeException|Error error){
                if(failure==null)failure=error;else failure.addSuppressed(error);
            }
        }
        if(failure instanceof RuntimeException error)throw error;
        if(failure instanceof Error error)throw error;
    }
}
