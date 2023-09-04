package info.kgeorgiy.ja.zakharov.implementor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Class for useful Implementor utilities
 */
public final class ImplementorUtilities {
    /**
     * Intended for generated classes.
     */
    public static final String TAB = " ".repeat(4);

    /**
     * Creates {@link System#lineSeparator()}.
     *
     * @return {@link String} representing value
     */
    public static String newLine() {
        return newLine(0);
    }

    /**
     * Creates {@link System#lineSeparator()} and {@code cnt} {@link #TAB}.
     *
     * @param cnt number of {@link #TAB}
     * @return {@link String} representing value
     */
    public static String newLine(int cnt) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        while (cnt > 0) {
            sb.append(TAB);
            --cnt;
        }
        return sb.toString();
    }

    /**
     * Recursive deleter of files.
     */
    public static final SimpleFileVisitor<Path> DELETE_VISITOR = new SimpleFileVisitor<Path>() {

        /**
         * Deletes {@code file}.
         *
         * @param file
         *          a reference to the file
         * @param attrs
         *          the file's basic attributes
         *
         * @return {@link FileVisitResult#CONTINUE}
         * @throws IOException can't delete file
         */
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }


        /**
         * Deletes {@code dir} after visit
         *
         * @param dir
         *          a reference to the directory
         * @param exc
         *          {@code null} if the iteration of the directory completes without
         *          an error; otherwise the I/O exception that caused the iteration
         *          of the directory to complete prematurely
         *
         * @return {@link FileVisitResult#CONTINUE}
         * @throws IOException if I\O was occurred on delete
         */
        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    };
}
