package nu.marginalia.wmsa.edge.index.postings.journal.reader;

import nu.marginalia.util.array.LongArray;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntryHeader;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

public class SearchIndexJournalReadEntry {
    private final long offset;
    public final SearchIndexJournalEntryHeader header;
    private final LongArray map;
    private final long committedSize;

    SearchIndexJournalReadEntry(long offset, LongArray map, long committedSize) {
        this.map = map;
        this.committedSize = committedSize;

        final long sizeBlock = this.map.get(offset);
        final long docId = this.map.get(offset + 1);
        final long meta = this.map.get(offset + 2);

        this.offset = offset;
        this.header = new SearchIndexJournalEntryHeader(
                (int) (sizeBlock >>> 32L),
                docId,
                meta);
    }

    public boolean hasNext() {
        return nextId() < committedSize;
    }

    public long docId() {
        return header.documentId();
    }

    public long docMeta() {
        return header.documentMeta();
    }

    public int domainId() {
        return (int) (docId() >>> 32L);
    }

    public int urlId() {
        return (int) (docId() & 0xFFFF_FFFFL);
    }

    public int wordCount() {
        return header.entrySize() / SearchIndexJournalEntry.ENTRY_SIZE;
    }

    public SearchIndexJournalEntry readEntry() {
        long[] dest = new long[header.entrySize()];

        long offsetStart = offset + SearchIndexJournalEntryHeader.HEADER_SIZE_LONGS;
        long offsetEnd = offsetStart + header.entrySize();

        map.get(offsetStart, offsetEnd, dest);

        return new SearchIndexJournalEntry(header.entrySize(), dest);
    }

    public SearchIndexJournalEntry readEntryUsingBuffer(long[] dest) {
        if (dest.length >= header.entrySize()) {
            long offsetStart = offset + SearchIndexJournalEntryHeader.HEADER_SIZE_LONGS;
            long offsetEnd = offsetStart + header.entrySize();

            map.get(offsetStart, offsetEnd, dest);
            return new SearchIndexJournalEntry(header.entrySize(), dest);
        } else {
            return readEntry();
        }
    }

    public long nextId() {
        return offset + totalEntrySizeLongs();
    }

    public SearchIndexJournalReadEntry next() {
        return new SearchIndexJournalReadEntry(nextId(), map, committedSize);
    }

    public long copyTo(long pos, LongBuffer adequateBuffer, LongArray destArray) {
        long size = totalEntrySizeLongs();

        map.get(offset, offset + size, adequateBuffer, 0);
        destArray.set(pos, pos + size, adequateBuffer, 0);

        return size;
    }

    public long totalEntrySizeLongs() {
        return header.entrySize() + SearchIndexJournalEntryHeader.HEADER_SIZE_LONGS;
    }
}
