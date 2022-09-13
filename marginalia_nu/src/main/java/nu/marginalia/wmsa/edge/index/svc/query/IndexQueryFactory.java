package nu.marginalia.wmsa.edge.index.svc.query;

import nu.marginalia.wmsa.edge.index.reader.SearchIndex;
import nu.marginalia.wmsa.edge.index.svc.query.types.EntrySource;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterBTreeRange;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterStepFromPredicate;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterStepIf;

import java.util.*;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;

public class IndexQueryFactory {
    private final List<SearchIndex> requiredIndices;
    private final List<SearchIndex> excludeIndex;

    public Collection<SearchIndex> getIndicies() {
        return requiredIndices;
    }

    public IndexQueryFactory(List<SearchIndex> requiredIndices, List<SearchIndex> excludeIndex) {
        this.requiredIndices = requiredIndices.stream().filter(Objects::nonNull).collect(Collectors.toList());
        this.excludeIndex = excludeIndex;
    }

    public IndexQueryBuilder buildQuery(IndexQueryCachePool cachePool, int firstWordId) {
        List<EntrySource> sources = new ArrayList<>(requiredIndices.size());

        for (var ri : requiredIndices) {
            var range = ri.rangeForWord(cachePool, firstWordId);
            if (range.isPresent()) {
                sources.add(range.asEntrySource());
            }
        }

        return new IndexQueryBuilder(new IndexQuery(sources), cachePool);
    }

    public class IndexQueryBuilder {
        private final IndexQuery query;
        private final IndexQueryCachePool cachePool;

        IndexQueryBuilder(IndexQuery query,
                          IndexQueryCachePool cachePool) {
            this.query = query;
            this.cachePool = cachePool;
        }

        public void filter(LongPredicate predicate) {
            query.addInclusionFilter(new QueryFilterStepFromPredicate(predicate));
        }

        public IndexQueryBuilder also(int termId) {
            List<QueryFilterStepIf> filters = new ArrayList<>(requiredIndices.size());

            for (var ri : requiredIndices) {
                var range = ri.rangeForWord(cachePool, termId);

                if (range.isPresent()) {
                    filters.add(new QueryFilterBTreeRange(ri, range, cachePool));
                }
                else {
                    filters.add(QueryFilterStepIf.noPass());
                }
            }

            filters.sort(Comparator.naturalOrder());
            query.addInclusionFilter(QueryFilterStepIf.anyOf(filters));

            return this;
        }


        public IndexQueryBuilder not(int termId) {
            for (var ri : excludeIndex) {
                var range = ri.rangeForWord(cachePool, termId);
                if (range.isPresent()) {
                    query.addInclusionFilter(range.asExcludeFilterStep(cachePool));
                }
            }

            return this;
        }

        public IndexQuery build() {
            return query;
        }

    }

}

