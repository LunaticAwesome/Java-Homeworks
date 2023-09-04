package info.kgeorgiy.ja.zakharov.walk;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;

public class Visitor extends SimpleFileVisitor<Path> {
    private final FileDataWriter writer;
    private final MessageDigest messageDigest;
    private final static int BUFFER_SIZE = 32;
    private final byte[] bytes = new byte[BUFFER_SIZE];

    Visitor(final FileDataWriter writer, final MessageDigest messageDigest) {
        this.writer = writer;
        this.messageDigest = messageDigest;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
        try (final BufferedInputStream reader = new BufferedInputStream(Files.newInputStream(file))) {
            messageDigest.reset();
            int count;
            while ((count = reader.read(bytes)) >= 0) {
                messageDigest.update(bytes, 0, count);
            }
        } catch (final IOException e) {
            writer.writeDataFail(file.toString());
            return FileVisitResult.CONTINUE;
        }
        writer.writeData(messageDigest.digest(), file.toString());
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
        writer.writeDataFail(file.toString());
        return FileVisitResult.CONTINUE;
    }
}
