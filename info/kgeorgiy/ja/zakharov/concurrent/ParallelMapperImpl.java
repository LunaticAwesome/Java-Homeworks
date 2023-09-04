package info.kgeorgiy.ja.zakharov.concurrent;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;

import static info.kgeorgiy.ja.zakharov.concurrent.IterativeParallelism.addAndStart;


/**
 * ParallelMapperImpl class is used to compute map function in parallel mode.
 */
public class ParallelMapperImpl implements ParallelMapper {
    private final Queue<Runnable> tasks;
    private final List<Thread> workers;
    private static final int MAX_SIZE = 256;

    /**
     * Constructor, creates new instance of ParallelMapperImpl class
     *
     * @param threads - count of threads used in map function.
     */
    public ParallelMapperImpl(int threads) {
        tasks = new ArrayDeque<>(MAX_SIZE);
        workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            addAndStart(workers, new Thread(() -> {
                try {
                    while (!Thread.interrupted()) {
                        getTask().run();
                    }
                } catch (InterruptedException ignored) {
                }
            }));
        }
    }

    private void addTask(Runnable task) throws InterruptedException {
        synchronized (tasks) {
            while (tasks.size() == MAX_SIZE) {
                tasks.wait();
            }
            tasks.add(task);
            tasks.notify();
        }
    }

    private Runnable getTask() throws InterruptedException {
        synchronized (tasks) {
            while (tasks.isEmpty()) {
                tasks.wait();
            }
            tasks.notifyAll();
            return tasks.poll();
        }
    }

    private static class SynchronizedList<R> {
        private final List<R> list;
        private int cnt;

        public SynchronizedList(int size) {
            list = new ArrayList<>(Collections.nCopies(size, null));
            cnt = size;
        }

        public void setValue(final int pos, R result) {
            list.set(pos, result);
            synchronized (this) {
                if (--cnt == 0) {
                    notify();
                }
            }
        }

        public synchronized List<R> getList() throws InterruptedException {
            while (cnt > 0) {
                wait();
            }
            return list;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> args) throws InterruptedException {
        SynchronizedList<R> collector = new SynchronizedList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            final int ind = i;
            addTask(() -> collector.setValue(ind, f.apply(args.get(ind))));
        }
        return collector.getList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        workers.forEach(Thread::interrupt);
        workers.forEach(worker -> {
            try {
                worker.join();
            } catch (InterruptedException ignored) {
            }
        });
    }
}
