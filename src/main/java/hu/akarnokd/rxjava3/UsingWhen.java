package hu.akarnokd.rxjava3;


import org.reactivestreams.Publisher;


import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.functions.Function;


class ResourceHandlers<D> {
    Function<? super D, ? extends Publisher<?>> onComplete;
    Function<? super D, ? extends Publisher<?>> onError;
    Function<? super D, ? extends Publisher<?>> onCancel;


    public ResourceHandlers(Function<? super D, ? extends Publisher<?>> onComplete, Function<? super D, ? extends Publisher<?>> onError, Function<? super D, ? extends Publisher<?>> onCancel) {
        this.onComplete = onComplete;
        this.onError = onError;
        this.onCancel = onCancel;
    }
}


public class UsingWhen {


    static <T, D> Flowable<T> usingWhen(Publisher<? extends D> resource, Function<? super D, ? extends Publisher<? extends T>> use, Function<? super D, ? extends Publisher<?>> cleanup) {
        return Maybe.fromPublisher(resource).flatMapPublisher(res -> Flowable.using(() -> res, use, resc -> Flowable.fromPublisher(cleanup.apply(resc)).subscribe(), false));
    }


    static <T, D> Flowable<T> usingWhen(Publisher<? extends D> resource, Function<? super D, ? extends Publisher<? extends T>> use, ResourceHandlers<D> resourceHandlers) {
        return Maybe.fromPublisher(resource).flatMapPublisher(res -> Flowable.fromPublisher(use.apply(res)).flatMap(Flowable::just, e -> Flowable.fromPublisher(resourceHandlers.onError.apply(res)).ignoreElements().toFlowable(), () -> Flowable.fromPublisher(resourceHandlers.onComplete.apply(res)).ignoreElements().toFlowable()).doOnCancel(() -> Flowable.fromPublisher(resourceHandlers.onCancel.apply(res)).subscribe()));
    }
}
