package de.skerkewitz.jcme.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileIOTest {

    @Test
    void writes_file_creating_parent_dirs(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("a/b/c/file.txt");
        FileIO.writeString(target, "hello");
        assertThat(Files.readString(target)).isEqualTo("hello");
    }

    @Test
    void overwrites_existing_file(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("file.txt");
        FileIO.writeString(target, "first");
        FileIO.writeString(target, "second");
        assertThat(Files.readString(target)).isEqualTo("second");
    }

    @Test
    void writes_binary_bytes(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("bin.bin");
        byte[] payload = {1, 2, 3, 4, 5};
        FileIO.write(target, payload);
        assertThat(Files.readAllBytes(target)).isEqualTo(payload);
    }

    @Test
    void cleans_up_tmp_file_when_present(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("file.txt");
        FileIO.writeString(target, "x");
        assertThat(Files.list(tmp).count()).isEqualTo(1);
    }

    @Test
    void delete_if_exists_is_silent_when_missing(@TempDir Path tmp) {
        // Should not throw
        FileIO.deleteIfExists(tmp.resolve("nope.txt"));
    }
}
