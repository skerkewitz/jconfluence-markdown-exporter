package de.skerkewitz.jcme.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Atomic file-write helpers. Equivalent to the Python {@code save_file} + {@code lockfile.save}
 * temp-file pattern: write to a sibling {@code .tmp} file, then move with REPLACE_EXISTING +
 * (best-effort) ATOMIC_MOVE. Falls back to non-atomic move on platforms (e.g. Windows under
 * security software) where ATOMIC_MOVE fails.
 */
public final class FileIO {

    private FileIO() {}

    public static void write(Path path, byte[] content) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.write(tmp, content);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailed) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static void writeString(Path path, String content) throws IOException {
        write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort delete
        }
    }
}
