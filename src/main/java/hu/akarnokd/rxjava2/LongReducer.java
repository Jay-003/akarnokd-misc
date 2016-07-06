package hu.akarnokd.rxjava2;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

abstract class LongReducer implements Observer<Long>, Disposable {
    
    final Observer<? super Long> actual;
    
    Disposable d;
    
    boolean hasValue;
    
    long accumulator;
    
    boolean cancelled;
    
    public LongReducer(Observer<? super Long> actual) {
        this.actual = actual;
    }
    
    @Override
    public final void onSubscribe(Disposable d) {
        this.d = d;
        
        actual.onSubscribe(this);
    }
    
    @Override
    public final void onError(Throwable e) {
        actual.onError(e);
    }
    
    @Override
    public final void onComplete() {
        if (hasValue) {
            actual.onNext(accumulator);
        }
        actual.onComplete();
    }
    
    @Override
    public final void dispose() {
        cancelled = true;
        d.dispose();
    }
    
    @Override
    public final boolean isDisposed() {
        return cancelled;
    }
}