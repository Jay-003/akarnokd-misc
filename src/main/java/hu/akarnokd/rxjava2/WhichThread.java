package hu.akarnokd.rxjava2;


import java.util.*;
import java.util.concurrent.TimeUnit;


import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import ix.Ix;


public class WhichThread {


    private static final int n = 1000;
    public static void main(String[] args) throws Exception {
        List<Observable<Integer>> sources = createSources();


        for (Observable<Integer> source : sources) {
            List<List<String>> names = runObservables(source);
            printResults(names);
        }
    }


    private static List<Observable<Integer>> createSources() {
        List<Observable<Integer>> sources = new ArrayList<>();
        sources.add(Observable.just(1));
        sources.add(Observable.just(1).delay(1, TimeUnit.MINUTES));
        sources.add(Observable.<Integer>empty());
        sources.add(Observable.<Integer>empty().delay(1, TimeUnit.MINUTES));
        return sources;
    }


    private static List<List<String>> runObservables(Observable<Integer> source) throws InterruptedException {
        List<List<String>> names = new ArrayList<>();
        names.add(new ArrayList<>());
        names.add(new ArrayList<>());
        names.add(new ArrayList<>());
        names.add(new ArrayList<>());


        Scheduler main = Schedulers.single();
        int n = 1000;


        for (int i = 0; i < n; i++) {
            runObservable(source, names, main);
            Thread.sleep(100);
            if (i % 100 == 0) {
                printGroupedNames(i, names);
            }
        }
        System.out.println(n);
        printGroupedNames(n, names);
        return names;
    }


    private static void runObservable(Observable<Integer> source, List<List<String>> names, Scheduler main) {
        main.scheduleDirect(() -> {
            source
                    .doOnSubscribe(s -> names.get(0).add(Thread.currentThread().getName()))
                    .subscribeOn(Schedulers.io())
                    .unsubscribeOn(Schedulers.io())
                    .doFinally(() -> names.get(1).add(Thread.currentThread().getName()))
                    .doOnSubscribe(s -> names.get(2).add(Thread.currentThread().getName()))
                    .subscribeOn(main)
                    .unsubscribeOn(main)
                    .observeOn(main)
                    .doFinally(() -> names.get(3).add(Thread.currentThread().getName()))
                    .subscribe()
                    .dispose();
        });
    }


    private static void printGroupedNames(int i, List<List<String>> names) {
        printOutput(names, i);
    }


    private static void printResults(List<List<String>> names) {
        printOutput(names, n);
    }

    private static void printOutput(List<List<String>> names, int n) {
        System.out.println(n);
        for (int j = 0; j < 4; j++) {
            System.out.print(j);
            System.out.print(" -> ");
            Ix.from(names.get(j))
                    .groupBy(v -> v.contains("Single") ? "main" : "io")
                    .flatMap(v -> v.count().map(c -> v.key() + ": " + c))
                    .join()
                    .forEach(System.out::println);
        }
    }


}
