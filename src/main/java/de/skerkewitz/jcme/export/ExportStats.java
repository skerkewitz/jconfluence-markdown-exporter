package de.skerkewitz.jcme.export;

import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe counters for a single export run. Mirrors the Python {@code ExportStats}
 * dataclass: page outcomes (exported / skipped / failed / removed) and attachment outcomes.
 */
public final class ExportStats {

    private final int total;
    private final LongAdder exported = new LongAdder();
    private final LongAdder skipped = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder removed = new LongAdder();
    private final LongAdder attachmentsExported = new LongAdder();
    private final LongAdder attachmentsSkipped = new LongAdder();
    private final LongAdder attachmentsFailed = new LongAdder();
    private final LongAdder attachmentsRemoved = new LongAdder();

    public ExportStats(int total) {
        this.total = total;
    }

    public ExportStats() { this(0); }

    public int total() { return total; }
    public long exported() { return exported.sum(); }
    public long skipped() { return skipped.sum(); }
    public long failed() { return failed.sum(); }
    public long removed() { return removed.sum(); }
    public long attachmentsExported() { return attachmentsExported.sum(); }
    public long attachmentsSkipped() { return attachmentsSkipped.sum(); }
    public long attachmentsFailed() { return attachmentsFailed.sum(); }
    public long attachmentsRemoved() { return attachmentsRemoved.sum(); }

    public void incExported() { exported.increment(); }
    public void incSkipped() { skipped.increment(); }
    public void incFailed() { failed.increment(); }
    public void incRemoved() { removed.increment(); }
    public void incAttachmentsExported() { attachmentsExported.increment(); }
    public void incAttachmentsSkipped() { attachmentsSkipped.increment(); }
    public void incAttachmentsFailed() { attachmentsFailed.increment(); }
    public void incAttachmentsRemoved() { attachmentsRemoved.increment(); }
}
