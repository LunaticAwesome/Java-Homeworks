package info.kgeorgiy.ja.zakharov.walk;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;

public class RecursiveWalk {
    private final Path inputPath;
    private final Path outputPath;
    private final MessageDigest messageDigest;

    public RecursiveWalk(final String[] args) throws WalkerException {
        if (args == null || args.length != 2) {
            throw new WalkerException("Invalid number of arguments");
        }
        inputPath = makePath(args[0], "Invalid path for input file: ");
        outputPath = makePath(args[1], "Invalid path for output file: ");
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new WalkerException("Cannot create message digest: " + e);
        }
    }

    private Path makePath(String path, String exceptionMessage) throws WalkerException {
        if (path == null) {
            throw new WalkerException(exceptionMessage + "Path shouldn't be null");
        }
        try {
            return Path.of(path);
        } catch (InvalidPathException e) {
            throw new WalkerException(exceptionMessage + e.getMessage());
        }
    }

    public void walk(int depth) throws WalkerException {
        try (final BufferedReader reader = Files.newBufferedReader(inputPath)) {
            try (final FileDataWriter writer = new FileDataWriter(outputPath, messageDigest.getDigestLength())) {
                Visitor visitor = new Visitor(writer, messageDigest);
                while (true) {
                    final String line;
                    try {
                        line = reader.readLine();
                    } catch (final IOException e) {
                        throw new WalkerException("Exception in reading input file: " + e);
                    }
                    if (line == null) {
                        break;
                    }
                    try {
                        Path path = Path.of(line);
                        Files.walkFileTree(path, EnumSet.noneOf(FileVisitOption.class), depth, visitor);
                    } catch (InvalidPathException e) {
                        writer.writeDataFail(line);
                    } catch (IOException e) {
                        throw new WalkerException("Exception in writing in output file: " + e);
                    }
                }
            } catch (IOException e) {
                throw new WalkerException("Exception in opening output file: " + e);
            }
        } catch (IOException e) {
            throw new WalkerException("Exception in opening input file: " + e);
        }
    }

    public static void run(int depth, String[] args) {
        try {
            new RecursiveWalk(args).walk(depth);
        } catch (final WalkerException e) {
            System.err.println(e.getMessage());
        }
    }

    public static void main(String[] args) {
        run(Integer.MAX_VALUE, args);
    }
}
