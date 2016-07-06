package hu.akarnokd.comparison;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.reactivestreams.Publisher;

import io.reactivex.Flowable;

/**
 * Example benchmark. Run from command line as
 * <br>
 * gradle jmh -Pjmh='PublisherFlatMapPerf'
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1)
@State(Scope.Thread)
public class PublisherFlatMapPerf {

    @State(Scope.Thread)
    public static class Regular { 
        @Param({"1", "1000", "1000000"})
        int count;

        Publisher<Integer> baseline;

        Publisher<Integer> justFlatMapRange;

        Publisher<Integer> rangeFlatMapJust;

        Publisher<Integer> justFlatMapArray;

        @Setup
        public void setup() {
            baseline = Flowable.range(1, count);

            justFlatMapRange = Flowable.just(1).flatMap(v -> Flowable.range(v, count));

            Integer[] arr = new Integer[count];
            Arrays.fill(arr, 777);
            
            justFlatMapArray = Flowable.just(1).flatMap(v -> Flowable.fromArray(arr));

            rangeFlatMapJust = Flowable.range(1, count).flatMap(v -> Flowable.just(v));
        }
    }

    @State(Scope.Thread)
    public static class CrossRange { 
        Publisher<Integer> justFlatMapJust;

        Publisher<Integer> rangeFlatMapRange;

        Publisher<Integer> rangeFlatMapArray;

        @Setup
        public void setup() {
            justFlatMapJust = Flowable.just(1).flatMap(v -> Flowable.just(v));
            
            Integer[] arr = new Integer[1000];
            Arrays.fill(arr, 777);

            rangeFlatMapRange = Flowable.range(0, 1000).flatMap(v -> Flowable.range(v, 1000));

            rangeFlatMapArray = Flowable.range(0, 1000).flatMap(v -> Flowable.fromArray(arr));
        }
    }

    @Benchmark
    public void baseline(Regular o, Blackhole bh) {
        o.baseline.subscribe(new LatchedRSObserver<>(bh));
    }

    @Benchmark
    public void justFlatMapRange(Regular o, Blackhole bh) {
        o.justFlatMapRange.subscribe(new LatchedRSObserver<>(bh));
    }

    @Benchmark
    public void justFlatMapArray(Regular o, Blackhole bh) {
        o.justFlatMapArray.subscribe(new LatchedRSObserver<>(bh));
    }

    @Benchmark
    public void rangeFlatMapJust(Regular o, Blackhole bh) {
        o.rangeFlatMapJust.subscribe(new LatchedRSObserver<>(bh));
    }

    @Benchmark
    public void justFlatMapJust(CrossRange o, Blackhole bh) {
        o.justFlatMapJust.subscribe(new LatchedRSObserver<>(bh));
    }

    @Benchmark
    public void rangeFlatMapRange(CrossRange o, Blackhole bh) {
        o.rangeFlatMapRange.subscribe(new LatchedRSObserver<>(bh));
    }

    @Benchmark
    public void rangeFlatMapArray(CrossRange o, Blackhole bh) {
        o.rangeFlatMapArray.subscribe(new LatchedRSObserver<>(bh));
    }
}
