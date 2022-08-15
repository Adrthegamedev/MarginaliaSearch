package nu.marginalia.util.btree;

import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.btree.model.BTreeHeader;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.util.multimap.MultimapSearcher;

import static java.lang.Math.min;

public class CachingBTreeReader {

    private final MultimapFileLong file;
    public final BTreeContext ctx;

    private final MultimapSearcher dataSearcher;

    public CachingBTreeReader(MultimapFileLong file, BTreeContext ctx) {
        this.file = file;
        this.dataSearcher = MultimapSearcher.forContext(file, ctx.equalityMask(), ctx.entrySize());

        this.ctx = ctx;
    }

    public BTreeHeader getHeader(long fileOffset) {
        return new BTreeHeader(file.get(fileOffset), file.get(fileOffset+1), file.get(fileOffset+2));
    }

    public Cache prepareCache() {
        return new Cache();
    }
    /**
     *
     * @return file offset of entry matching keyRaw, negative if absent
     */
    public long findEntry(BTreeHeader header, Cache cache, final long keyRaw) {
        final int blockSize = ctx.BLOCK_SIZE_WORDS();

        final long key = keyRaw & ctx.equalityMask();
        final long dataAddress = header.dataOffsetLongs();

        final long searchStart;
        final long numEntries;

        if (header.layers() == 0) { // For small data, there is no index block, only a flat data block
            searchStart = dataAddress;
            numEntries = header.numEntries();
        }
        else {
            cache.load(header);

            long dataLayerOffset = searchIndex(header, cache, key);
            if (dataLayerOffset < 0) {
                return dataLayerOffset;
            }

            searchStart = dataAddress + dataLayerOffset * ctx.entrySize();
            numEntries = min(header.numEntries() - dataLayerOffset, blockSize);
        }

        return dataSearcher.binarySearch(key, searchStart, numEntries);
    }

    private long searchIndex(BTreeHeader header, Cache cache, long key) {
        final int blockSize = ctx.BLOCK_SIZE_WORDS();
        long layerOffset = 0;

        for (int i = header.layers() - 1; i >= 0; --i) {
            final long indexLayerBlockOffset = header.relativeIndexLayerOffset(ctx, i) + layerOffset;

            final long nextLayerOffset = cache.relativePositionInIndex(key, (int) indexLayerBlockOffset, blockSize);
            if (nextLayerOffset < 0)
                return nextLayerOffset;

            layerOffset = blockSize * (nextLayerOffset + layerOffset);
        }

        return layerOffset;
    }


    public class Cache {
        long[] indexData;

        public void load(BTreeHeader header) {
            if (indexData != null)
                return;

            int size = (int)(header.dataOffsetLongs() - header.indexOffsetLongs());
            indexData = new long[size];
            file.read(indexData, header.indexOffsetLongs());
        }

        long relativePositionInIndex(long key, int fromIndex, int n) {
            int low = 0;
            int high = n - 1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                long midVal = indexData[fromIndex + mid];

                if (midVal < key)
                    low = mid + 1;
                else if (midVal > key)
                    high = mid - 1;
                else
                    return mid;
            }
            return low;
        }
    }
}
