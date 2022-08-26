package nu.marginalia.wmsa.edge.index.reader;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.query.IndexQueryBuilder;
import nu.marginalia.wmsa.edge.index.reader.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.index.reader.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class SearchIndexReader implements AutoCloseable {

    private final EnumMap<IndexBlock, SearchIndex> indices;

    private final EnumMap<IndexBlock, IndexQueryBuilder> queryBuilders;
    private final EnumMap<IndexBlock, IndexQueryBuilder> underspecifiedQueryBuilders;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final IndexBlock[] indicesBySearchOrder = new IndexBlock[] {
            IndexBlock.Top,
            IndexBlock.Middle,
            IndexBlock.Low,
            IndexBlock.NamesWords,
            IndexBlock.Words_1,
            IndexBlock.Words_2,
            IndexBlock.Words_4,
            IndexBlock.Words_8,
            IndexBlock.Words_16Plus,
    };

    @Inject
    public SearchIndexReader(
            EnumMap<IndexBlock, SearchIndex> indices) {
        this.indices = indices;

        var lowIndex  = indices.get(IndexBlock.Low);
        var midIndex  = indices.get(IndexBlock.Middle);
        var topIndex  = indices.get(IndexBlock.Top);
        var linkIndex  = indices.get(IndexBlock.Link);
        var titleIndex  = indices.get(IndexBlock.Title);
        var namesIndex  = indices.get(IndexBlock.NamesWords);
        var titleKeywordsIndex  = indices.get(IndexBlock.TitleKeywords);
        var metaIndex  = indices.get(IndexBlock.Meta);
        var topicIndex  = indices.get(IndexBlock.Topic);

        var words1  = indices.get(IndexBlock.Words_1);
        var words2  = indices.get(IndexBlock.Words_2);
        var words4  = indices.get(IndexBlock.Words_4);
        var words8  = indices.get(IndexBlock.Words_8);
        var words16  = indices.get(IndexBlock.Words_16Plus);
        var artifacts  = indices.get(IndexBlock.Artifacts);

        queryBuilders = new EnumMap<>(IndexBlock.class);
        underspecifiedQueryBuilders = new EnumMap<>(IndexBlock.class);

        queryBuilders.put(IndexBlock.Words_1, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, titleIndex, topIndex, words1), words1));
        queryBuilders.put(IndexBlock.Words_2, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, titleIndex, topIndex, words2), words1));
        queryBuilders.put(IndexBlock.Words_4, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, titleIndex, topIndex, words4), words1));
        queryBuilders.put(IndexBlock.Words_8, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, titleIndex, topIndex, words8), words1));
        queryBuilders.put(IndexBlock.Words_16Plus, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, titleIndex, topIndex, words1, words2, words4, words8, words16, artifacts), words1));

        underspecifiedQueryBuilders.put(IndexBlock.TitleKeywords, new IndexQueryBuilder(listOfNonNulls(titleKeywordsIndex, linkIndex, topicIndex, topIndex, midIndex, lowIndex, namesIndex, metaIndex), words1));
        underspecifiedQueryBuilders.put(IndexBlock.Title, new IndexQueryBuilder(listOfNonNulls(titleIndex, topicIndex, linkIndex, topicIndex, topIndex, midIndex, lowIndex, namesIndex, metaIndex), words1));
        underspecifiedQueryBuilders.put(IndexBlock.Top, new IndexQueryBuilder(listOfNonNulls(topIndex, linkIndex, midIndex, lowIndex, namesIndex, metaIndex), words1));
    }

    @SafeVarargs
    public final <T> List<T> listOfNonNulls(T... vals) {
        return Stream.of(vals).filter(Objects::nonNull).toList();
    }


    public LongStream findHotDomainsForKeyword(IndexBlock block, int wordId, int queryDepth, int minHitCount, int maxResults) {
        var index = indices.get(block);

        if (index == null)
            return LongStream.empty();

        return index.rangeForWord(wordId)
                .stream()
                .limit(queryDepth)
                .filter(new LongPredicate() {
                    long last = Long.MIN_VALUE;
                    int count = 0;

                    @Override
                    public boolean test(long value) {
                        if ((last >>> 32L) == (value >>> 32L)) {
                            return count++ == minHitCount;
                        }
                        else {
                            last = value;
                            count = 0;

                        }
                        return false;
                    }
                })
                .limit(maxResults);
    }

    public Query findUnderspecified(
            IndexBlock block,
            IndexSearchBudget budget,
            LongPredicate filter,
            int wordId) {

        var builder = underspecifiedQueryBuilders.get(block);

        if (null != builder) {
            return builder.buildUnderspecified(budget, filter, wordId);
        }
        return findWord(block, budget, filter, wordId);
    }

    public Query findWord(IndexBlock block, IndexSearchBudget budget, LongPredicate filter, int wordId) {
        var builder = queryBuilders.get(block);

        if (builder == null)
            return Query.EMPTY;

        return builder.build(budget, filter, wordId);
    }

    @Override
    public void close() throws Exception {
        for (var idx : indices.values()) {
            idx.close();
        }
    }

    @SneakyThrows
    public long numHits(IndexBlock block, int word) {
        IndexQueryBuilder builder = queryBuilders.get(block);

        if (builder == null)
            return 0L;

        long hits = 0;
        for (var index : builder.getIndicies()) {
            hits += index.numUrls(word);
        }
        return hits;
    }

    public IndexBlock getBlockForResult(int searchTerm, long urlId) {
        for (var block : indicesBySearchOrder) {
            var index = indices.get(block);

            if (null == index) {
                continue;
            }

            var range = index.rangeForWord(searchTerm);

            if (range.hasUrl(urlId)) {
                return block;
            }
        }
        return IndexBlock.Words_1;
    }

    public boolean isTermInBucket(IndexBlock block, int searchTerm, long urlId) {
        final var index = indices.get(block);
        if (null == index) return false;

        return index
                .rangeForWord(searchTerm)
                .hasUrl(urlId);
    }
}
