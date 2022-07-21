package nu.marginalia.util.multimap;

public interface MultimapSearcher {
    long binarySearchUpper(long key, long fromIndex, long n);
    long binarySearch(long key, long fromIndex, long n);

    static MultimapSearcher forContext(MultimapFileLongSlice slice, long mask, int stepSize) {
        if (mask == ~0L && stepSize == 1) {
            return new SimpleMultimapSearcher(new MultimapSearcherBase(slice));
        }
        else if (stepSize == 1) {
            return new MaskedMultimapSearcher(new MultimapSearcherBase(slice), mask);
        }
        else {
            return new SteppingMaskedMultimapSearcher(new MultimapSearcherBase(slice), mask, stepSize);
        }
    }
}

class SimpleMultimapSearcher implements  MultimapSearcher {
    private final MultimapSearcherBase base;

    SimpleMultimapSearcher(MultimapSearcherBase base) {
        this.base = base;
    }

    @Override
    public long binarySearchUpper(long key, long fromIndex, long n) {
        return base.binarySearchUpper(key, fromIndex, n);
    }

    @Override
    public long binarySearch(long key, long fromIndex, long n) {
        return base.binarySearch(key, fromIndex, n);
    }
}


class MaskedMultimapSearcher implements  MultimapSearcher {
    private final MultimapSearcherBase base;
    private final long mask;

    MaskedMultimapSearcher(MultimapSearcherBase base, long mask) {
        this.base = base;
        this.mask = mask;
    }

    @Override
    public long binarySearchUpper(long key, long fromIndex, long n) {
        return base.binarySearchUpper(key, fromIndex, n, mask);
    }

    @Override
    public long binarySearch(long key, long fromIndex, long n) {
        return base.binarySearch(key, fromIndex, n, mask);
    }
}


class SteppingMaskedMultimapSearcher implements  MultimapSearcher {
    private final MultimapSearcherBase base;
    private final long mask;
    private final int step;

    SteppingMaskedMultimapSearcher(MultimapSearcherBase base, long mask, int step) {
        this.base = base;
        this.mask = mask;
        this.step = step;
    }

    @Override
    public long binarySearchUpper(long key, long fromIndex, long n) {
        return base.binarySearchUpper(key, fromIndex, step, n, mask);
    }

    @Override
    public long binarySearch(long key, long fromIndex, long n) {
        return base.binarySearch(key, fromIndex, step, n, mask);
    }
}