package info.kgeorgiy.ja.zakharov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Util {

    /**
     * Shutdowns pool, using method {@link ExecutorService#shutdown()}, wait {@code time} {@link TimeUnit#MILLISECONDS}
     * after that use {@link ExecutorService#shutdownNow()} and wait {@code time} {@link TimeUnit#MILLISECONDS}.
     *
     * @param pool {@link ExecutorService} that need to shut down.
     * @param time {@code time} that need to wait.
     * @param timeUnit {@link TimeUnit} for waiting.
     */
    public static void shutdownPool(ExecutorService pool, long time, TimeUnit timeUnit) {
        shutdownPool(pool, time, timeUnit, true);
    }

    /**
     * Shutdowns pool, if {@code needShutdown}
     * using method {@link ExecutorService#shutdown()}, wait {@code time} {@link TimeUnit#MILLISECONDS}
     * after that use {@link ExecutorService#shutdownNow()} and wait {@code time} {@link TimeUnit#MILLISECONDS}.
     *
     * @param pool {@link ExecutorService} that need to shut down.
     * @param time {@code time} that need to wait.
     * @param timeUnit {@link TimeUnit} for waiting.
     * @param needShutdown {@code boolean} for shutdown.
     */
    public static void shutdownPool(ExecutorService pool, long time, TimeUnit timeUnit, boolean needShutdown) {
        if (pool == null) {
            return;
        }
        if (needShutdown) {
            pool.shutdown();
        }
        try {
            if (!pool.awaitTermination(time, timeUnit)) {
                pool.shutdownNow();
                pool.awaitTermination(time, timeUnit);
            }
        } catch (InterruptedException e) {
            System.err.println("Terminated with exception: " + e);
            pool.shutdownNow();
        }
    }

    /**
     * Check that following {@link String[]} has {@link String[]#length()} equals to {@code expectedLength}
     * and don't contain null elements else print message about it.
     *
     * @param args           {@link String[]} array for check
     * @param expectedLength expected length of {@code args}
     * @return {@code false} if arguments is valid, otherwise print error message and return true.
     */
    public static boolean checkArgs(String[] args, int expectedLength) {
        if (args == null || args.length != expectedLength) {
            System.err.printf("Expected %d arguments.%n", expectedLength);
            return true;
        }
        if (Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.printf("Arguments should be correct: found null.%n");
            return true;
        }
        return false;
    }

    /**
     * returns message in format {@link String#format} "%s%d_%d", prefix, i, j;
     * @param prefix {@link String}
     * @param i {@code int}
     * @param j {@code int}
     * @return {@link String} representation
     */
    public static String createMessage(String prefix, int i, int j) {
        return String.format("%s%d_%d", prefix, i, j);
    }

    private static int findInteger(int expected, final String string, int i) {
        int j = -1;
        for (; i < string.length(); i++) {
            if (j == -1 && Character.isDigit(string.charAt(i))) {
                j = i;
                continue;
            }
            if (j != -1 && !Character.isDigit(string.charAt(i))) {
                break;
            }
        }
        try {
            if (j == -1 || Integer.parseInt(string.substring(j, i)) != expected) {
                return -1;
            }
        } catch (NumberFormatException ignored) {
            return -1;
        }
        return i;
    }

    /**
     * Checks that following {@link String} string satisfies format [\\D*] + first + [\\D+] + second + [\\D*].
     * @param first {@code int}.
     * @param second {@code int}.
     * @param string {@link String} for which need to check.
     * @return satisfies string this condition.
     */
    public static boolean validString(int first, int second, final String string) {
        int i = findInteger(first, string, 0);
        if (i < 0) {
            return false;
        }
        i = findInteger(second, string, i);
        if (i < 0) {
            return false;
        }
        for (; i < string.length(); i++) {
            if (Character.isDigit(string.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Runs selector and gets keys from selector. If current key is readable use method receive for get it, otherwise
     * use method send to send it. If select is empty and select flag is true repeat requests.
     *
     * @param selector {@link Selector} socket.
     * @param receive {@link Consumer} for receive.
     * @param send {@link Consumer} for send.
     * @param timeout {@code int} for select.
     * @param repeat {@code boolean}.
     */
    public static void run(final Selector selector, final Consumer<SelectionKey> receive,
                           final Consumer<SelectionKey> send, long timeout, boolean repeat) {
        while (selector.isOpen()) {
            try {
                if (selector.select(timeout) > 0) {
                    for (Iterator<SelectionKey> iterator = selector.selectedKeys().iterator(); iterator.hasNext(); ) {
                        SelectionKey key = iterator.next();
                        try {
                            if (key.isReadable() && (key.interestOps() & SelectionKey.OP_READ) != 0) {
                                receive.accept(key);
                            }
                            if (key.isValid() && key.isWritable() && (key.interestOps() & SelectionKey.OP_WRITE) != 0) {
                                send.accept(key);
                            }
                        } finally {
                            iterator.remove();
                        }
                    }
                } else if (repeat) {
                    boolean flag = false;
                    for (SelectionKey key : selector.keys()) {
                        if (key.interestOps() > 0) {
                            flag = true;
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                    }
                    if (!flag) {
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Can't select: " + e.getMessage());
                return;
            }
        }
    }

    /**
     * Sends data using channel.
     *
     * @param channel {@link DatagramChannel} used for send.
     * @param buffer {@link ByteBuffer} data that need to send.
     * @param target {@link SocketAddress} address to send.
     * @return 0 if I\O was occurred, otherwise returns count of bytes send.
     */
    public static int sendBuffer(DatagramChannel channel, ByteBuffer buffer, SocketAddress target) {
        buffer.flip();
        try {
            return channel.send(buffer, target);
        } catch (IOException e) {
            System.err.println("Can't send message: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Read data from channel to buffer using {@link DatagramChannel#receive(ByteBuffer)}.
     *
     * @param channel {@link DatagramChannel} used
     * @param buffer {@link ByteBuffer} for data from channel
     * @return if I\O was occurred returns null, otherwise returns {@link SocketAddress} from channel.
     */
    public static SocketAddress readBuffer(final DatagramChannel channel, final ByteBuffer buffer) {
        buffer.clear();
        SocketAddress clientAddress;
        try {
            clientAddress = channel.receive(buffer);
            buffer.flip();
            return clientAddress;
        } catch (final IOException e) {
            System.err.println("Can't receive message: " + e.getMessage());
            return null;
        }
    }

    /**
     * Closes a resource with ignoring exceptions. If {@code closeable} is null do nothing.
     *
     * @param closeable {@link Closeable} that need to close.
     */
    public static void close(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (final IOException ignored) {
        }
    }

    /**
     * Creates a server using {@code constructor} with {@code args[0]} port and {@code args[1]}
     * threads number and runs it.
     *
     * @param constructor {@link Supplier} for constructing server.
     * @param args arguments for server. Expected format: args.length() == 2, args[0] and args[1] are valid integers.
     */
    public static void startServer(Supplier<HelloServer> constructor, String[] args) {
        if (Util.checkArgs(args, 2)) {
            return;
        }
        try (HelloServer server = constructor.get()) {
            try {
                server.start(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                System.err.println("Arguments should be correct integer numbers: " + e.getMessage());
            }
        }
    }

    /**
     * Runs a client created by using {@link Supplier} {@code constructor},
     * that connects to the server with hostname {@code host} and {@code port}. Client creates
     * {@code thread} and in every thread make request in format {@code prefix + i + "_" + j},
     * where {@code i} - number of thread, {@code j} - number of request.
     *
     * @param constructor {@link Supplier} that creates instance of Hello client
     * @param args expected format: {@link String} {@code host}, {@code port}, {@link String} {@code prefix},
     *                    {@code threads}, {@code requests}.
     */
    public static void startClient(Supplier<HelloClient> constructor, String[] args) {
        if (Util.checkArgs(args, 5)) {
            return;
        }
        try {
            constructor.get().run(args[0],
                    Integer.parseInt(args[1]),
                    args[2],
                    Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]));
        } catch (NumberFormatException e) {
            System.err.println("Arguments should be correct integer numbers: " + e.getMessage());
        }
    }
}
