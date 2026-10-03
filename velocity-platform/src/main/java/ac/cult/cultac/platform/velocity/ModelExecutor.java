package ac.cult.cultac.platform.velocity;

import ac.cult.cultac.vanilla.VanillaRegistryState;
import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Every packet-owner task activates the client's native tags and default components. */
final class ModelExecutor extends AbstractEventExecutor {
    private final EventExecutor delegate;
    private final VanillaRegistryState state;

    ModelExecutor(EventExecutor delegate, VanillaRegistryState state) {
        this.delegate = delegate;
        this.state = state;
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == Thread.currentThread() && delegate.inEventLoop(thread) && state.isActive();
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(() -> state.execute(command));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return delegate.schedule(() -> state.execute(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit) {
        return delegate.schedule(
                () -> {
                    var result = new AtomicReference<V>();
                    var failure = new AtomicReference<Exception>();
                    state.execute(() -> {
                        try {
                            result.set(command.call());
                        } catch (Exception caught) {
                            failure.set(caught);
                        }
                    });
                    if (failure.get() != null) {
                        throw failure.get();
                    }
                    return result.get();
                },
                delay,
                unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long delay, long period, TimeUnit unit) {
        return delegate.scheduleAtFixedRate(() -> state.execute(command), delay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long delay, long period, TimeUnit unit) {
        return delegate.scheduleWithFixedDelay(() -> state.execute(command), delay, period, unit);
    }

    // A client owns its queueing context, while the platform owns the shared model worker.
    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException("The platform owns the model executor");
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException("The platform owns the model executor");
    }

    @Override
    public boolean isShuttingDown() {
        return delegate.isShuttingDown();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public Future<?> terminationFuture() {
        return delegate.terminationFuture();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
