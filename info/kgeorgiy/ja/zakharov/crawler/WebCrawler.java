package info.kgeorgiy.ja.zakharov.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


/**
 * Webcrawler class used to recursive traversal of pages on internet and downloads content of them.
 * Webcrawler use multithreading dfs and correctly works in multithreading.
 */
public class WebCrawler implements AdvancedCrawler {
    private final Downloader downloader;
    private final int perHost;
    private final Phaser parentSync;
    private final ExecutorService downloadersPool;
    private final ExecutorService extractorsPool;

    /**
     * Creates instance of WebCrawler
     *
     * @param downloader  {@link Downloader} used to download content from internet pages.
     * @param downloaders number of maximum threads can be used for download.
     * @param extractors  number of maximum extractors can be used for extract content from page.
     * @param perHost     maximum number of downloads that might be from one host.
     */
    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.perHost = perHost;
        downloadersPool = Executors.newFixedThreadPool(downloaders);
        extractorsPool = Executors.newFixedThreadPool(extractors);
        parentSync = new Phaser();
    }

    private class HostQueue {
        private final Queue<Runnable> waiting;
        private int cnt;

        HostQueue() {
            waiting = new ArrayDeque<>();
            cnt = 0;
        }

        private synchronized void add(Runnable task) {
            if (cnt < perHost) {
                ++cnt;
                try {
                    downloadersPool.submit(task);
                } catch (RejectedExecutionException ignored) {}
            } else {
                waiting.add(task);
            }
        }

        private synchronized void next() {
            final Runnable task = waiting.poll();
            if (task != null) {
                try {
                    downloadersPool.submit(task);
                } catch (RejectedExecutionException ignored) {}
            } else {
                --cnt;
            }
        }
    }

    private static class BFSInformation {
        final ConcurrentMap<String, IOException> errors;
        final Set<String> used;
        final Phaser sync;
        final Set<String> cashedLinks;
        final Set<String> allowedHosts;
        private final ConcurrentMap<String, HostQueue> hostsQueue;

        @SuppressWarnings("SimplifyStreamApiCallChains")
        BFSInformation(List<String> hosts, Phaser parentSync) {
            if (hosts != null) {
                allowedHosts = hosts.stream().collect(Collectors.toSet());
            } else {
                allowedHosts = null;
            }
            errors = new ConcurrentHashMap<>();
            used = ConcurrentHashMap.newKeySet();
            sync = new Phaser(parentSync, 1);
            cashedLinks = ConcurrentHashMap.newKeySet();
            hostsQueue = new ConcurrentHashMap<>();
        }

        public boolean allowedHost(final String host) {
            if (allowedHosts == null) {
                return true;
            }
            return allowedHosts.contains(host);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result download(String url, int depth) {
        return download(url, depth, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result download(String url, int depth, List<String> hosts) {
        final BFSInformation bfsInformation = new BFSInformation(hosts, parentSync);
        bfsInformation.used.add(url);
        ConcurrentLinkedDeque<String> deque = new ConcurrentLinkedDeque<>();
        deque.add(url);
        for (int i = 1; i <= depth; i++) {
            deque = downloadImpl(deque, bfsInformation, i < depth);
            bfsInformation.sync.arriveAndAwaitAdvance();
        }
        return new Result(new ArrayList<>(bfsInformation.cashedLinks), bfsInformation.errors);
    }

    private ConcurrentLinkedDeque<String> downloadImpl(final ConcurrentLinkedDeque<String> urls,
                                                       final BFSInformation bfsInformation, final boolean needExtract) {
        ConcurrentLinkedDeque<String> nextLayer = new ConcurrentLinkedDeque<>();
        while (!urls.isEmpty()) {
            final String url = urls.poll();
            try {
                final String host = URLUtils.getHost(url);
                if (!bfsInformation.allowedHost(host)) {
                    continue;
                }
                final HostQueue queue = bfsInformation.hostsQueue.computeIfAbsent(host, s -> new HostQueue());

                bfsInformation.sync.register();
                queue.add(() -> {
                    try {
                        final Document page = downloader.download(url);
                        bfsInformation.cashedLinks.add(url);
                        if (needExtract) {
                            bfsInformation.sync.register();
                            try {
                                extractorsPool.submit(() -> {
                                    try {
                                        nextLayer.addAll(page.extractLinks().stream().filter(bfsInformation.used::add).toList());
                                    } catch (IOException e) {
                                        bfsInformation.errors.put(url, e);
                                    } finally {
                                        bfsInformation.sync.arriveAndDeregister();
                                    }
                                });
                            } catch (RejectedExecutionException ignored) {}
                        }
                    } catch (IOException e) {
                        bfsInformation.errors.put(url, e);
                    } finally {
                        bfsInformation.sync.arriveAndDeregister();
                        queue.next();
                    }
                });
            } catch (MalformedURLException e) {
                bfsInformation.errors.put(url, e);
            }
        }
        return nextLayer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        parentSync.forceTermination();
        downloadersPool.shutdown();
        extractorsPool.shutdown();
        forceShutdown(downloadersPool);
        forceShutdown(extractorsPool);
    }

    private static void forceShutdown(final ExecutorService pool) {
        try {
            if (!pool.awaitTermination(10, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
                pool.awaitTermination(10, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ignored) {
            System.err.println("Terminated with exceptions");
            pool.shutdownNow();
        }
    }

    private static int getArg(String[] args, int index, int defaultValue) {
        return index < args.length ? Integer.parseInt(args[index]) : defaultValue;
    }


    /**
     * Recursively downloads content from pages, starts from url. Uses {@link CachingDownloader} for download pages
     * content. If command-line arguments print error message for user. After usage print user pages, that download
     * successfully and prints pages downloaded with errors.
     *
     * @param args command-line arguments, expected format : url {@link String} [depth {@code int} [downloaders
     *             {@code int} [extractors {@code int} [perHost {@code int}]]]]
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0 || args.length > 5 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Incorrect arguments. Expected: url [depth [downloaders [extractors [perHost]]]]");
            return;
        }

        boolean allPositiveIntegers = true;
        for (int i = 1; i < args.length; i++) {
            try {
                if (Integer.parseInt(args[i]) <= 0) {
                    allPositiveIntegers = false;
                }
            } catch (NumberFormatException e) {
                allPositiveIntegers = false;
            }
        }

        if (!allPositiveIntegers) {
            System.err.println("Expected positive integer arguments");
        }

        final String url = args[0];
        int depth = getArg(args, 1, 2);
        int downloaders = getArg(args, 2, 8);
        int extractors = getArg(args, 3, 8);
        int perHost = getArg(args, 4, 2);
        try {
            try (final Crawler crawler = new WebCrawler(new CachingDownloader(2), downloaders, extractors, perHost)) {
                final Result result = crawler.download(url, depth);
                System.out.println(result.getDownloaded().size() + " pages downloaded successfully:");
                for (final String string : result.getDownloaded()) {
                    System.out.println(string);
                }
                System.out.println(result.getErrors().size() + " pages downloaded with errors:");
                for (final Map.Entry<String, IOException> entry : result.getErrors().entrySet()) {
                    System.out.println("url: " + entry.getKey() + " with exception: " + entry.getValue());
                }
            }
        } catch (IOException e) {
            System.err.println("Unable to create Caching downloader: " + e);
        }
    }
}
