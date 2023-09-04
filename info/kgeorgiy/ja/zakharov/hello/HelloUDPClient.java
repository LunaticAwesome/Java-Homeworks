package info.kgeorgiy.ja.zakharov.hello;


import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Implementation for {@link HelloClient}
 */
public class HelloUDPClient implements HelloClient {

    public static final int SO_TIMEOUT = 50;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(final String host, int port, final String prefix, int threads, final int requests) {
        SocketAddress address = new InetSocketAddress(host, port);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        IntStream.range(1, threads + 1).forEach(i -> pool.submit(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(address);
                    socket.setSoTimeout(SO_TIMEOUT);
                final int size = socket.getReceiveBufferSize();
                final DatagramPacket receive = new DatagramPacket(new byte[size], size);
                final DatagramPacket packet = new DatagramPacket(new byte[0], 0);
                IntStream.range(1, requests + 1).forEachOrdered(j -> {
                    final String msg = Util.createMessage(prefix, i, j);
                    byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                    packet.setData(data);
                    while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                        try {
                            socket.send(packet);
                            socket.receive(receive);
                        } catch (IOException ignored) {
                            continue;
                        }
                        String response = new String(receive.getData(), receive.getOffset(),
                                receive.getLength(), StandardCharsets.UTF_8);
                        if (Util.validString(i, j, response)) {
                            System.out.println(response);
                            break;
                        }
                    }
                });
            } catch (SocketException e) {
                System.err.println("Cannot create socket " + i + ": " + e);
            }
        }));
        Util.shutdownPool(pool, ((long) requests) * 5, TimeUnit.SECONDS);
    }

    /**
     * Runs a client, that connects to the server with hostname {@code host} and {@code port}. Client creates
     * {@code thread} and in every thread make request in format {@code prefix + i + "_" + j},
     * where {@code i} - number of thread, {@code j} - number of request.
     *
     * @param args expected format: {@link String} {@code host}, {@code port}, {@link String} {@code prefix},
     *             {@code threads}, {@code requests}.
     */
    public static void main(String[] args) {
        Util.startClient(HelloUDPClient::new, args);
    }
}
