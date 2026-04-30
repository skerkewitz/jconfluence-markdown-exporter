package de.skerkewitz.jcme.export;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ExportStatsTest {

    @Test
    void counters_start_at_zero() {
        ExportStats stats = new ExportStats(10);
        assertThat(stats.total()).isEqualTo(10);
        assertThat(stats.exported()).isZero();
        assertThat(stats.skipped()).isZero();
        assertThat(stats.failed()).isZero();
    }

    @Test
    void increment_methods_update_each_counter() {
        ExportStats stats = new ExportStats();
        stats.incExported();
        stats.incExported();
        stats.incSkipped();
        stats.incFailed();
        stats.incRemoved();
        stats.incAttachmentsExported();
        stats.incAttachmentsSkipped();
        stats.incAttachmentsFailed();
        stats.incAttachmentsRemoved();

        assertThat(stats.exported()).isEqualTo(2);
        assertThat(stats.skipped()).isEqualTo(1);
        assertThat(stats.failed()).isEqualTo(1);
        assertThat(stats.removed()).isEqualTo(1);
        assertThat(stats.attachmentsExported()).isEqualTo(1);
        assertThat(stats.attachmentsSkipped()).isEqualTo(1);
        assertThat(stats.attachmentsFailed()).isEqualTo(1);
        assertThat(stats.attachmentsRemoved()).isEqualTo(1);
    }

    @Test
    void counters_are_thread_safe_under_concurrent_increments() throws InterruptedException {
        ExportStats stats = new ExportStats();
        int threads = 8;
        int incrementsPerThread = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) stats.incExported();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(stats.exported()).isEqualTo((long) threads * incrementsPerThread);
    }
}
