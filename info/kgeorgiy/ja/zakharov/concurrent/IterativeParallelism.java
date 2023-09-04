package info.kgeorgiy.ja.zakharov.concurrent;

import info.kgeorgiy.java.advanced.concurrent.AdvancedIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * IterativeParallel class used to compute functions in iterative parallel mode.
 */
public class IterativeParallelism implements AdvancedIP {
    final ParallelMapper mapper;

    /**
     * Default constructor, class won't use parallel mapper for methods
     */
    public IterativeParallelism() {
        this.mapper = null;
    }


    /**
     * Constructor with mapper. Class will be use mapper for methods
     * @param mapper {@link ParallelMapper} mapper for use method map
     */
    public IterativeParallelism(ParallelMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Add {@code thread} to list and start it.
     * @param workers {@link List} of {@link Thread} that need to add {@code thread}
     * @param thread {@link Thread} thread to run and add to {@code workers}
     */
    public static void addAndStart(List<Thread> workers, Thread thread) {
        workers.add(thread);
        thread.start();
    }

    private static void joinThreads(final List<Thread> workers) throws InterruptedException {
        InterruptedException exception = new InterruptedException();
        for (int i = 0; i < workers.size(); i++) {
            try {
                workers.get(i).join();
            } catch (InterruptedException e) {
                if (exception.getSuppressed().length == 0) {
                    workers.forEach(Thread::interrupt);
                }
                exception.addSuppressed(e);
            }
        }
        if (exception.getSuppressed().length != 0) {
            throw exception;
        }
    }

    private <T> List<Stream<T>> splitValues(int count, List<T> values) {
        List<Stream<T>> data = new ArrayList<>();
        int blockSize = values.size() / count;
        int rest = values.size() % count;
        int r = 0;
        for (int i = 0; i < count; i++) {
            int l = r;
            r += blockSize;
            if (rest > 0) {
                --rest;
                r += 1;
            }
            data.add(values.subList(l, r).stream());
        }
        return data;
    }

    private <T, R> R task(int threads, List<T> values,
                          Function<Stream<T>, R> task,
                          Function<Stream<R>, R> collectorResult) throws InterruptedException {
        threads = Math.max(1, Math.min(values.size(), threads));
        List<Stream<T>> splitValues = splitValues(threads, values);
        List<R> res;
        if (mapper == null) {
            res = new ArrayList<>(Collections.nCopies(threads, null));
            List<Thread> workers = new ArrayList<>();
            IntStream.range(0, splitValues.size()).forEach(pos ->
                    addAndStart(workers, new Thread(() -> res.set(pos, task.apply(splitValues.get(pos))))));
            joinThreads(workers);
        } else {
            res = mapper.map(task, splitValues);
        }
        return collectorResult.apply(res.stream());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException {
        return task(threads, values,
                stream -> stream.max(comparator).orElseThrow(() -> new IllegalArgumentException("values not be empty")),
                stream -> stream.max(comparator).get());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException {
        return maximum(threads, values, Collections.reverseOrder(comparator));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return task(threads, values, stream -> stream.allMatch(predicate), stream -> stream.allMatch(Boolean::booleanValue));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return !all(threads, values, predicate.negate());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int count(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return task(threads, values, stream -> (int) stream.filter(predicate).count(), stream -> stream.mapToInt(Integer::intValue).sum());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String join(int threads, List<?> values) throws InterruptedException {
        return task(threads, values,
                stream -> stream.map(Objects::toString).collect(Collectors.joining()),
                stream -> stream.collect(Collectors.joining()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> List<T> filter(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return task(threads, values,
                stream -> stream.filter(predicate).collect(Collectors.toList()),
                stream -> stream.flatMap(Collection::stream).collect(Collectors.toList()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values, Function<? super T, ? extends U> f) throws InterruptedException {
        return task(threads, values,
                stream -> stream.map(f).collect(Collectors.toList()),
                stream -> stream.flatMap(Collection::stream).collect(Collectors.toList()));
    }

    @Override
    public <T> T reduce(int threads, List<T> values, Monoid<T> monoid) throws InterruptedException {
        Function<Stream<T>, T> function = stream -> stream.reduce(monoid.getIdentity(), monoid.getOperator());
        return task(threads, values, function, function);
    }

    @Override
    public <T, R> R mapReduce(int threads, List<T> values, Function<T, R> lift, Monoid<R> monoid) throws InterruptedException {
        return task(threads, values,
                stream -> stream.map(lift).reduce(monoid.getIdentity(), monoid.getOperator()),
                stream -> stream.reduce(monoid.getIdentity(), monoid.getOperator()));
    }
}
