package info.kgeorgiy.ja.zakharov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation for {@link HelloClient}
 */
public class HelloUDPNonblockingClient implements HelloClient {
    int capacity;
    int requests;

    private static class Information {
        final int threadId;
        int requestId;
        final String prefix;
        final SocketAddress address;

        Information(int threadId, String prefix, SocketAddress address) {
            this.threadId = threadId;
            this.requestId = 1;
            this.prefix = prefix;
            this.address = address;
        }

        public String getMessage() {
            return Util.createMessage(prefix, threadId, requestId);
        }

        public boolean validString(String string) {
            return Util.validString(threadId, requestId, string);
        }
    }

    private void handleSend(SelectionKey key) {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Information information = (Information) key.attachment();

        ByteBuffer buffer = ByteBuffer.allocate(capacity);

        String msg = information.getMessage();
        buffer.put(msg.getBytes(StandardCharsets.UTF_8));

        if (Util.sendBuffer(channel, buffer, information.address) != 0) {
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void handleReceive(SelectionKey key) {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Information information = (Information) key.attachment();

        ByteBuffer buffer = ByteBuffer.allocate(capacity);
        SocketAddress address = Util.readBuffer(channel, buffer);
        if (address != null) {
            String response = new String(buffer.array(), buffer.arrayOffset(), buffer.limit(), StandardCharsets.UTF_8);
            if (information.validString(response)) {
                information.requestId++;
                if (information.requestId <= requests) {
                    key.interestOps(SelectionKey.OP_WRITE);
                } else {
                    key.interestOps(0);
                }
                System.out.println(response);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        final SocketAddress address;
        final Selector selector;
        try {
            address = new InetSocketAddress(host, port);
            selector = Selector.open();
        } catch (IOException e) {
            System.out.println("Unable to open selector: " + e.getMessage());
            return;
        }

        final List<DatagramChannel> channels = new ArrayList<>();
        capacity = 4096;
        this.requests = requests;
        for (int i = 1; i <= threads; i++) {
            try {
                DatagramChannel channel = DatagramChannel.open();

                channel.configureBlocking(false);
                channel.bind(null);
                channel.register(selector, SelectionKey.OP_WRITE,
                        new Information(i, prefix, address));
                channels.add(channel);

                capacity = channel.getOption(StandardSocketOptions.SO_RCVBUF);
            } catch (IOException e) {
                System.err.println("Unable to open channel: " + e.getMessage());
            }
        }
        Util.run(selector, this::handleReceive, this::handleSend, 100, true);

        Util.close(selector);
        channels.forEach(Util::close);
    }

    /**
     * Runs a client, that connects to the server with hostname {@code host} and {@code port}. Client creates
     * 1 thread and make requests in format {@code prefix + i + "_" + j},
     * where {@code i} - number of thread, {@code j} - number of request.
     *
     * @param args expected format: {@link String} {@code host}, {@code port}, {@link String} {@code prefix},
     *             {@code threads}, {@code requests}.
     */
    public static void main(String[] args) {
        Util.startClient(HelloUDPNonblockingClient::new, args);
    }
}
