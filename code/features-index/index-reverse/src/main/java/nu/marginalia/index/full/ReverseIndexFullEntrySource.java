package nu.marginalia.index.full;

import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.query.EntrySource;
import nu.marginalia.index.query.ReverseIndexEntrySourceBehavior;

import static java.lang.Math.min;

public class ReverseIndexFullEntrySource implements EntrySource {
    private final BTreeReader reader;

    int pos;
    int endOffset;

    final int entrySize;
    private final ReverseIndexEntrySourceBehavior behavior;

    public ReverseIndexFullEntrySource(BTreeReader reader,
                                       int entrySize,
                                       ReverseIndexEntrySourceBehavior behavior) {
        this.reader = reader;
        this.behavior = behavior;
        this.entrySize = entrySize;

        pos = 0;
        endOffset = pos + entrySize * reader.numEntries();
    }

    @Override
    public void skip(int n) {
        pos += n;
    }

    @Override
    public void read(LongQueryBuffer buffer) {
        if (behavior == ReverseIndexEntrySourceBehavior.DO_NOT_PREFER
                && buffer.hasRetainedData())
        {
            pos = endOffset;
            return;
        }

        buffer.end = min(buffer.end, endOffset - pos);
        reader.readData(buffer.data, buffer.end, pos);
        pos += buffer.end;

        destagger(buffer);
        buffer.uniq();
    }

    private void destagger(LongQueryBuffer buffer) {
        if (entrySize == 1)
            return;

        for (int ri = entrySize, wi=1; ri < buffer.end ; ri+=entrySize, wi++) {
            buffer.data[wi] = buffer.data[ri];
        }

        buffer.end /= entrySize;
    }

    @Override
    public boolean hasMore() {
        return pos < endOffset;
    }

}
