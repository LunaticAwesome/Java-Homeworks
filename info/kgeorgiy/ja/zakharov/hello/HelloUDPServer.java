package info.kgeorgiy.ja.zakharov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Implementation for {@link HelloServer}
 */
public class HelloUDPServer implements HelloServer {
    public static final int SO_TIMEOUT = 50;
    DatagramSocket socket;
    ExecutorService pool;
    int size;

    /**
     * Creates default response for this server in format: "Hello, " + {@code string}.
     * @param string {@link String} data.
     * @return {@link String} of value representation.
     */
    public static String response(String string) {
        return "Hello, " + string;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(int port, int threads) {
        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(SO_TIMEOUT);
            size = socket.getReceiveBufferSize();
        } catch (SocketException e) {
            System.err.println("Failed to open socket on port " + port + e);
            return;
        }
        pool = Executors.newFixedThreadPool(threads);
        IntStream.range(0, threads).forEach(i ->
                pool.submit(() -> {
                    final DatagramPacket packet = new DatagramPacket(new byte[size], size);
                    while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                        try {
                            socket.receive(packet);
                            String message = response(new String(packet.getData(),
                                    packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8));
                            packet.setData(message.getBytes(StandardCharsets.UTF_8));
                            socket.send(packet);
                        } catch (IOException e) {
                            System.err.println("Error in processing request: " + e);
                        }
                    }
                }));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        Util.close(socket);
        Util.shutdownPool(pool, 20, TimeUnit.MILLISECONDS);
    }

    /**
     * Create server with {@code args[0]} port and {@code args[1]} threads number.
     * @param args arguments for server. Expected format: args.length() == 2, args[0] and args[1] are valid integers.
     */
    public static void main(String[] args) {
        Util.startServer(HelloUDPServer::new, args);
    }
}
