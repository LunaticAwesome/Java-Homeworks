package info.kgeorgiy.ja.zakharov.walk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class FileDataWriter implements AutoCloseable {
    private final BufferedWriter writer;
    private final int length;

    public FileDataWriter(final Path outputPath, int length) throws IOException {
        if (outputPath.getParent() != null) {
            try {
                Files.createDirectories(outputPath.getParent());
            } catch (IOException ignored) {
            }
        }
        writer = Files.newBufferedWriter(outputPath);
        this.length = 2 * length;
    }

    public void writeData(final byte[] printData, final String path) throws IOException {
        writer.write(String.format("%s %s%n", HexFormat.of().formatHex(printData), path));
    }

    public void writeDataFail(final String path) throws IOException {
        for (int i = 0; i < length; i++) {
            writer.write('0');
        }
        writer.write(" " + path + System.lineSeparator());
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
