package info.kgeorgiy.ja.zakharov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation for {@link HelloServer}
 */
public class HelloUDPNonblockingServer implements HelloServer {
    private ExecutorService workers;
    private ExecutorService socketHolder;
    private Selector selector;
    private DatagramChannel serverChannel;
    private final ConcurrentLinkedQueue<Data> packetsToWrite = new ConcurrentLinkedQueue<>();
    int capacity;

    private record Data(ByteBuffer buffer, SocketAddress target) {}

    private void handleReceive(final SelectionKey key) {
        final DatagramChannel serverChannel = (DatagramChannel) key.channel();
        final ByteBuffer buffer = ByteBuffer.allocate(capacity);

        final SocketAddress clientAddress = Util.readBuffer(serverChannel, buffer);
        if (clientAddress != null) {
            workers.submit(() -> {
                final String response = HelloUDPServer.response(
                        StandardCharsets.UTF_8.decode(buffer).toString());

                buffer.clear();
                buffer.put(response.getBytes(StandardCharsets.UTF_8));

                packetsToWrite.add(new Data(buffer, clientAddress));
                key.interestOpsOr(SelectionKey.OP_WRITE);
                selector.wakeup();
            });
        }
    }

    private void handleSend(final SelectionKey key) {
        final DatagramChannel channel = (DatagramChannel) key.channel();
        final Data data = packetsToWrite.remove();

        if (packetsToWrite.isEmpty()) {
            key.interestOpsAnd(~SelectionKey.OP_WRITE);
        }

        int bytesSent = Util.sendBuffer(channel, data.buffer, data.target);
        if (bytesSent != 0) {
            key.interestOpsOr(SelectionKey.OP_READ);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(int port, int threads) {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            System.err.println("Cannot start server: " + e.getMessage());
            return;
        }

        try {
            serverChannel = DatagramChannel.open();
            try {
                serverChannel.configureBlocking(false);
                serverChannel.bind(new InetSocketAddress(port));
                serverChannel.register(selector, SelectionKey.OP_READ);
            } catch (IOException e) {
                System.err.println("Cannot configure channel: " + e.getMessage());
                return;
            }
        } catch (IOException e) {
            System.err.println("Cannot create channel: " + e.getMessage());
            return;
        }

        try {
            capacity = serverChannel.getOption(StandardSocketOptions.SO_RCVBUF);

            workers = Executors.newFixedThreadPool(threads);
            socketHolder = Executors.newSingleThreadExecutor();
            socketHolder.submit(() -> Util.run(selector, this::handleReceive, this::handleSend, 0, false));
        } catch (IOException e) {
            System.err.println("Can't start server: " + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        Util.close(selector);
        Util.close(serverChannel);
        workers.shutdown();
        socketHolder.shutdown();
        Util.shutdownPool(workers, 1000, TimeUnit.MILLISECONDS, false);
        Util.shutdownPool(socketHolder, 1000, TimeUnit.MILLISECONDS, false);
    }

    /**
     * Create server with {@code args[0]} port and {@code args[1] + 1} threads number.
     * 1 thread make requests using socket, another threads creates responses.
     * @param args arguments for server. Expected format: args.length() == 2, args[0] and args[1] are valid integers.
     */
    public static void main(String[] args) {
        Util.startServer(HelloUDPNonblockingServer::new, args);
    }
}
