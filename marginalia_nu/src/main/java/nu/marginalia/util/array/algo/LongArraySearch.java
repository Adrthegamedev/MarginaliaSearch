package nu.marginalia.util.array.algo;

import nu.marginalia.util.array.buffer.LongQueryBuffer;

public interface LongArraySearch extends LongArrayBase {

    int LINEAR_SEARCH_CUTOFF = 32;

    default long linearSearch(long key, long fromIndex, long toIndex) {
        long pos;

        for (pos = fromIndex; pos < toIndex; pos++) {
            long val = get(pos);

            if (val == key) return pos;
            if (val > key) break;
        }

        return encodeSearchMiss(pos - 1);
    }

    default long linearSearchUpperBound(long key, long fromIndex, long toIndex) {

        for (long pos = fromIndex; pos < toIndex; pos++) {
            if (get(pos) >= key) return pos;
        }

        return toIndex;
    }

    default long linearSearchN(int sz, long key, long fromIndex, long toIndex) {
        long pos;

        for (pos = fromIndex; pos < toIndex; pos+=sz) {
            long val = get(pos);

            if (val == key) return pos;
            if (val > key) return encodeSearchMiss(pos);
        }

        return encodeSearchMiss(toIndex - sz);
    }

    default long binarySearch(long key, long fromIndex, long toIndex) {
        long low = 0;
        long high = (toIndex - fromIndex) - 1;

        while (high - low >= LINEAR_SEARCH_CUTOFF) {
            long mid = (low + high) >>> 1;
            long midVal = get(fromIndex + mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return fromIndex + mid;
        }

        return linearSearch(key, fromIndex + low, fromIndex + high + 1);
    }

    default long binarySearchN(int sz, long key, long fromIndex, long toIndex) {
        long low = 0;
        long high = (toIndex - fromIndex)/sz - 1;

        while (high - low >= LINEAR_SEARCH_CUTOFF) {
            long mid = (low + high) >>> 1;
            long midVal = get(fromIndex + sz*mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return fromIndex + sz*mid;
        }

        for (fromIndex += low*sz; fromIndex < toIndex; fromIndex+=sz) {
            long val = get(fromIndex);

            if (val == key) return fromIndex;
            if (val > key) return encodeSearchMiss(fromIndex);
        }

        return encodeSearchMiss(toIndex - sz);
    }


    default long binarySearchUpperBound(long key, long fromIndex, long toIndex) {
        long low = 0;
        long high = (toIndex - fromIndex) - 1;

        while (high - low >= LINEAR_SEARCH_CUTOFF) {
            long mid = (low + high) >>> 1;
            long midVal = get(fromIndex + mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return fromIndex + mid;
        }

        for (fromIndex += low; fromIndex < toIndex; fromIndex++) {
            if (get(fromIndex) >= key) return fromIndex;
        }

        return toIndex;
    }

    default long binarySearchUpperBoundN(int sz, long key, long fromIndex, long toIndex) {
        long low = 0;
        long high = (toIndex - fromIndex)/sz - 1;

        while (high - low >= LINEAR_SEARCH_CUTOFF) {
            long mid = (low + high) >>> 1;
            long midVal = get(fromIndex + sz*mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return fromIndex + sz*mid;
        }

        for (fromIndex += low; fromIndex < toIndex; fromIndex+=sz) {
            if (get(fromIndex) >= key) return fromIndex;
        }

        return toIndex;
    }

    default void retain(LongQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {

        if (searchStart >= searchEnd) return;

        long bv = buffer.currentValue();
        long av = get(searchStart);
        long pos = searchStart;

        while (bv <= boundary && buffer.hasMore()) {
            if (bv < av) {
                if (!buffer.rejectAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }
            else if (bv == av) {
                if (!buffer.retainAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }

            if (++pos < searchEnd) {
                av = get(pos);
            }
            else {
                break;
            }
        }
    }

    default void retainN(LongQueryBuffer buffer, int sz, long boundary, long searchStart, long searchEnd) {

        if (searchStart >= searchEnd) return;

        long bv = buffer.currentValue();
        long av = get(searchStart);
        long pos = searchStart;

        while (bv <= boundary && buffer.hasMore()) {
            if (bv < av) {
                if (!buffer.rejectAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }
            else if (bv == av) {
                if (!buffer.retainAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }

            pos += sz;

            if (pos < searchEnd) {
                av = get(pos);
            }
            else {
                break;
            }
        }
    }
    default void reject(LongQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {

        if (searchStart >= searchEnd) return;

        long bv = buffer.currentValue();
        long av = get(searchStart);
        long pos = searchStart;

        while (bv <= boundary && buffer.hasMore()) {
            if (bv < av) {
                if (!buffer.retainAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }
            else if (bv == av) {
                if (!buffer.rejectAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }

            if (++pos < searchEnd) {
                av = get(pos);
            }
            else {
                break;
            }
        }

    }

    default void rejectN(LongQueryBuffer buffer, int sz, long boundary, long searchStart, long searchEnd) {

        if (searchStart >= searchEnd) return;

        long bv = buffer.currentValue();
        long av = get(searchStart);
        long pos = searchStart;

        while (bv <= boundary && buffer.hasMore()) {
            if (bv < av) {
                if (!buffer.retainAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }
            else if (bv == av) {
                if (!buffer.rejectAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }

            pos += sz;
            if (pos < searchEnd) {
                av = get(pos);
            }
            else {
                break;
            }
        }

    }

    static long encodeSearchMiss(long value) {
        return -1 - value;
    }

    static long decodeSearchMiss(long value) {
        return -value - 1;
    }
}
