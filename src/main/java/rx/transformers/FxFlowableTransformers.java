package rx.transformers;

import io.reactivex.FlowableOperator;
import io.reactivex.FlowableTransformer;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.subscribers.ResourceSubscriber;
import javafx.application.Platform;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;


public class FxFlowableTransformers {

    private FxFlowableTransformers() {}

    private static <T> void runOnFx(T t, Consumer<T> consumer)  {
        Platform.runLater(() -> {
                    try {
                        consumer.accept(t);
                    } catch (Throwable e) {
                        throw Exceptions.propagate(e);
                    }
                }
        );
    }

    private static <T> void runOnFx(Action action)  {
        Platform.runLater(() -> {
                    try {
                        action.run();
                    } catch (Throwable e) {
                        throw Exceptions.propagate(e);
                    }
                }
        );
    }

    /**
     * Performs a given action for each item on the FX thread
     * @param onNext
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnNextFx(Consumer<T> onNext) {
        return obs -> obs.doOnNext(t -> runOnFx(t, onNext));
    }

    /**
     * Performs a given action on a Throwable on the FX thread in the event of an onError
     * @param onError
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnErrorFx(Consumer<Throwable> onError) {
        return obs -> obs.doOnError(e -> runOnFx(e,onError));
    }

    /**
     * Performs a given Action on the FX thread when onCompleted is called
     * @param onCompleted
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnCompleteFx(Action onCompleted) {
        return obs -> obs.doOnComplete(() -> runOnFx(onCompleted));
    }

    /**
     * Performs a given Action on the FX thread when subscribed to
     * @param subscribe
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnSubscribeFx(Consumer<Subscription> subscribe) {
        return obs -> obs.doOnSubscribe((d -> runOnFx(d,subscribe)));
    }

    /**
     * Performs the provided onTerminate action on the FX thread
     * @param onTerminate
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnTerminateFx(Action onTerminate) {
        return obs -> obs.doOnTerminate(() -> runOnFx(onTerminate));
    }

    /**
     * Performs the provided onTerminate action on the FX thread
     * @param onDipsose
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnCancelFx(Action onDipsose) {
        return obs -> obs.doOnCancel(() -> runOnFx(onDipsose));
    }

    /**
     * Performs an action on onNext with the provided emission count
     * @param onNext
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnNextCount(Consumer<Integer> onNext) {
        return obs -> obs.lift(new FlowableEmissionCounter<>(new CountObserver(onNext,null,null)));
    }

    /**
     * Performs an action on onComplete with the provided emission count
     * @param onComplete
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnCompleteCount(Consumer<Integer> onComplete) {
        return obs -> obs.lift(new FlowableEmissionCounter<>(new CountObserver(null,onComplete,null)));
    }

    /**
     * Performs an action on onError with the provided emission count
     * @param onError
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnErrorCount(Consumer<Integer> onError) {
        return obs -> obs.lift(new FlowableEmissionCounter<>(new CountObserver(null,null,onError)));
    }

    /**
     * Performs an action on FX thread on onNext with the provided emission count
     * @param onNext
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnNextCountFx(Consumer<Integer> onNext) {
        return obs -> obs.compose(doOnNextCount(i -> runOnFx(i,onNext)));
    }

    /**
     * Performs an action on FX thread on onCompleted with the provided emission count
     * @param onComplete
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnCompleteCountFx(Consumer<Integer> onComplete) {
        return obs -> obs.compose(doOnCompleteCount(i -> runOnFx(i,onComplete)));
    }

    /**
     * Performs an action on FX thread on onError with the provided emission count
     * @param onError
     * @param <T>
     */
    public static <T> FlowableTransformer<T,T> doOnErrorCountFx(Consumer<Integer> onError) {
        return obs -> obs.compose(doOnErrorCount(i -> runOnFx(i,onError)));
    }


    private static class FlowableEmissionCounter<T> implements FlowableOperator<T,T> {

        private final CountObserver ctObserver;

        FlowableEmissionCounter(CountObserver ctObserver) {
            this.ctObserver = ctObserver;
        }

        @Override
        public Subscriber<? super T> apply(Subscriber<? super T> child) {

            return new ResourceSubscriber<T>() {
                private int count = 0;
                private boolean done = false;

                @Override
                protected void onStart() {
                    super.onStart();
                    request(Long.MAX_VALUE);
                }

                @Override
                public void onComplete() {
                    if (done)
                        return;

                    try {
                        if (ctObserver.doOnCompletedCountAction != null)
                            ctObserver.doOnCompletedCountAction.accept(count);
                    } catch (Exception e) {
                        Exceptions.throwIfFatal(e);
                        onError(e);
                        return;
                    }
                    done = true;
                    child.onComplete();
                }

                @Override
                public void onError(Throwable e) {
                    if (done)
                        return;
                    try {
                        if (ctObserver.doOnErrorCountAction != null)
                            ctObserver.doOnErrorCountAction.accept(count);
                    } catch(Exception e1) {
                        Exceptions.throwIfFatal(e1);
                        child.onError(e1);
                    }
                }

                @Override
                public void onNext(T t) {
                    if (done)
                        return;
                    try {
                        if (ctObserver.doOnNextCountAction != null)
                            ctObserver.doOnNextCountAction.accept(++count);
                    } catch(Exception e) {
                        Exceptions.throwIfFatal(e);
                        onError(e);
                        return;
                    }
                    child.onNext(t);
                    request(Long.MAX_VALUE);
                }
            };
        }
    }

    private static final class CountObserver {
        private final Consumer<Integer> doOnNextCountAction;
        private final Consumer<Integer> doOnCompletedCountAction;
        private final Consumer<Integer> doOnErrorCountAction;

        CountObserver(Consumer<Integer> doOnNextCountAction, Consumer<Integer> doOnCompletedCountAction, Consumer<Integer> doOnErrorCountAction) {
            this.doOnNextCountAction = doOnNextCountAction;
            this.doOnCompletedCountAction = doOnCompletedCountAction;
            this.doOnErrorCountAction = doOnErrorCountAction;
        }
    }

}
