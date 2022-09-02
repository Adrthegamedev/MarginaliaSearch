package nu.marginalia.wmsa.edge.index;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import io.prometheus.client.Histogram;
import io.reactivex.rxjava3.schedulers.Schedulers;
import marcono1234.gson.recordadapter.RecordTypeAdapterFactory;
import nu.marginalia.util.ListChunker;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.model.EdgeIndexSearchTerms;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexes;
import nu.marginalia.wmsa.edge.index.reader.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.search.*;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchResults;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchSpecification;
import nu.wmsa.wmsa.edge.index.proto.IndexPutKeywordsReq;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;

import static spark.Spark.get;
import static spark.Spark.halt;

public class EdgeIndexService extends Service {
    private static final int SEARCH_BUDGET_TIMEOUT_MS = 3000;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final Initialization init;
    private final SearchIndexes indexes;
    private final KeywordLexicon keywordLexicon;

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapterFactory(RecordTypeAdapterFactory.builder().allowMissingComponentValues().create())
            .create();

    private static final Histogram wmsa_edge_index_query_time
            = Histogram.build().name("wmsa_edge_index_query_time")
                               .linearBuckets(50, 50, 15)
                               .help("-").register();

    public static final int DYNAMIC_BUCKET_LENGTH = 7;


    @Inject
    public EdgeIndexService(@Named("service-host") String ip,
                            @Named("service-port") Integer port,
                            Initialization init,
                            MetricsServer metricsServer,
                            SearchIndexes indexes,
                            IndexServicesFactory servicesFactory) {
        super(ip, port, init, metricsServer);

        this.init = init;
        this.indexes = indexes;
        this.keywordLexicon = servicesFactory.getKeywordLexicon();

        Spark.post("/words/", this::putWords);
        Spark.post("/search/", this::search, gson::toJson);
        Spark.post("/search-domain/", this::searchDomain, gson::toJson);

        Spark.post("/dictionary/*", this::getWordId, gson::toJson);

        Spark.post("/ops/repartition", this::repartitionEndpoint);
        Spark.post("/ops/preconvert", this::preconvertEndpoint);
        Spark.post("/ops/reindex/:id", this::reindexEndpoint);

        get("/is-blocked", this::isBlocked, gson::toJson);

        Schedulers.newThread().scheduleDirect(this::initialize, 1, TimeUnit.MICROSECONDS);
    }

    private Object getWordId(Request request, Response response) {
        final String word = request.splat()[0];

        var dr = indexes.getDictionaryReader();
        if (null == dr) {
            response.status(HttpStatus.SC_FAILED_DEPENDENCY);
            return "";
        }

        final int wordId = dr.get(word);

        if (DictionaryHashMap.NO_VALUE == wordId) {
            response.status(404);
            return "";
        }

        return wordId;
    }

    private Object repartitionEndpoint(Request request, Response response) {

        if (!indexes.repartition()) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

    private Object preconvertEndpoint(Request request, Response response) {
        if (!indexes.preconvert()) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

    private Object reindexEndpoint(Request request, Response response) {
        int id = Integer.parseInt(request.params("id"));

        if (!indexes.reindex(id)) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

    private Object isBlocked(Request request, Response response) {
        return indexes.isBusy() || !initialized;
    }

    volatile boolean initialized = false;
    public void initialize() {
        if (!initialized) {
            init.waitReady();
            initialized = true;
        }
        else {
            return;
        }
        indexes.initialize(init);
    }

    private Object putWords(Request request, Response response) throws InvalidProtocolBufferException {
        var req = IndexPutKeywordsReq.parseFrom(request.bodyAsBytes());

        EdgeId<EdgeDomain> domainId = new EdgeId<>(req.getDomain());
        EdgeId<EdgeUrl> urlId = new EdgeId<>(req.getUrl());
        int idx = req.getIndex();

        for (int ws = 0; ws < req.getWordSetCount(); ws++) {
            putWords(domainId, urlId, req.getWordSet(ws), idx);
        }

        response.status(HttpStatus.SC_ACCEPTED);
        return "";
    }

    public void putWords(EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId,
                         IndexPutKeywordsReq.WordSet words, int idx
    ) {
        SearchIndexJournalWriterImpl indexWriter = indexes.getIndexWriter(idx);

        IndexBlock block = IndexBlock.values()[words.getIndex()];

        for (var chunk : ListChunker.chopList(words.getWordsList(), SearchIndexJournalEntry.MAX_LENGTH)) {

            var entry = new SearchIndexJournalEntry(getOrInsertWordIds(chunk));
            var header = new SearchIndexJournalEntryHeader(domainId, urlId, block);

            indexWriter.put(header, entry);
        };
    }

    private long[] getOrInsertWordIds(List<String> words) {
        return words.stream()
                .filter(w -> w.getBytes().length < Byte.MAX_VALUE)
                .mapToLong(keywordLexicon::getOrInsert)
                .toArray();
    }

    private Object searchDomain(Request request, Response response) {
        if (indexes.getDictionaryReader() == null) {
            logger.warn("Dictionary reader not yet initialized");
            halt(HttpStatus.SC_SERVICE_UNAVAILABLE, "Come back in a few minutes");
        }

        String json = request.body();
        EdgeDomainSearchSpecification specsSet = gson.fromJson(json, EdgeDomainSearchSpecification.class);

        final int wordId = keywordLexicon.getReadOnly(specsSet.keyword);

        List<EdgeId<EdgeUrl>> urlIds = indexes
                .getBucket(specsSet.bucket)
                .findHotDomainsForKeyword(specsSet.block, wordId, specsSet.queryDepth, specsSet.minHitCount, specsSet.maxResults)
                .mapToObj(lv -> new EdgeId<EdgeUrl>((int)(lv & 0xFFFF_FFFFL)))
                .toList();

        return new EdgeDomainSearchResults(specsSet.keyword, urlIds);
    }

    private Object search(Request request, Response response) {
        if (indexes.getDictionaryReader() == null) {
            logger.warn("Dictionary reader not yet initialized");
            halt(HttpStatus.SC_SERVICE_UNAVAILABLE, "Come back in a few minutes");
        }

        String json = request.body();
        EdgeSearchSpecification specsSet = gson.fromJson(json, EdgeSearchSpecification.class);

        long start = System.currentTimeMillis();
        try {
            return new EdgeSearchResultSet(searchStraight(specsSet));
        }
        catch (HaltException ex) {
            logger.warn("Halt", ex);
            throw ex;
        }
        catch (Exception ex) {
            logger.info("Error during search {}({}) (query: {})", ex.getClass().getSimpleName(), ex.getMessage(), json);
            logger.info("Error", ex);
            Spark.halt(500, "Error");
            return null;
        }
        finally {
            wmsa_edge_index_query_time.observe(System.currentTimeMillis() - start);
        }
    }

    @NotNull
    private Map<IndexBlock, List<EdgeSearchResultItem>> searchStraight(EdgeSearchSpecification specsSet) {
        Map<IndexBlock, List<EdgeSearchResultItem>> results = new HashMap<>();
        int count = 0;
        TIntHashSet seenResults = new TIntHashSet();

        final DomainResultCountFilter domainCountFilter = new DomainResultCountFilter(specsSet.limitByDomain);

        IndexSearchBudget budget = new IndexSearchBudget(SEARCH_BUDGET_TIMEOUT_MS);
        for (var sq : specsSet.subqueries) {
            Optional<EdgeIndexSearchTerms> searchTerms = getSearchTerms(sq);

            if (searchTerms.isEmpty())
                continue;

            var resultForSq = performSearch(searchTerms.get(),
                    budget, seenResults, domainCountFilter,
                    sq, specsSet.buckets, specsSet,
                    specsSet.limitTotal - count);

            if (logger.isDebugEnabled()) {
                logger.debug("{} -> {} {}", sq.block, sq.searchTermsInclude, resultForSq.size());
            }

            count += resultForSq.size();
            if (resultForSq.size() > 0) {
                results.computeIfAbsent(sq.block, s -> new ArrayList<>()).addAll(resultForSq);
            }
        }


        List<List<String>> distinctSearchTerms = specsSet.subqueries.stream().map(sq -> sq.searchTermsInclude).distinct().toList();

        results.forEach((index, blockResults) -> {
            for (var result : blockResults) {
                for (int i = 0; i < distinctSearchTerms.size(); i++) {
                    for (var term : distinctSearchTerms.get(i)) {
                        result.scores.add(getSearchTermScore(i, result.bucketId, term, result.getCombinedId()));
                    }
                }
            }
        });

        return results;
    }

    private List<EdgeSearchResultItem>  performSearch(EdgeIndexSearchTerms searchTerms,
                                            IndexSearchBudget budget,
                                            TIntHashSet seenResults,
                                            DomainResultCountFilter domainCountFilter,
                                            EdgeSearchSubquery sq,
                                            List<Integer> specBuckets,
                                            EdgeSearchSpecification specs,
                                            int limit)
    {
        if (limit <= 0) {
            return new ArrayList<>();
        }

        final List<EdgeSearchResultItem> results = new ArrayList<>();
        final DomainResultCountFilter localFilter = new DomainResultCountFilter(specs.limitByDomain);

        for (int i : specBuckets) {
            int foundResultsCount = results.size();

            if (foundResultsCount >= specs.limitTotal || foundResultsCount >= limit)
                break;

            List<EdgeSearchResultItem> resultsForBucket = new ArrayList<>(specs.limitByBucket);

            getQuery(i, budget, sq.block, lv -> localFilter.filterRawValue(i, lv), searchTerms)
                    .mapToObj(id -> new EdgeSearchResultItem(i, sq.termSize(), id))
                    .filter(ri -> !seenResults.contains(ri.url.id()) && localFilter.test(i, domainCountFilter, ri))
                    .limit(specs.limitTotal * 3L)
                    .distinct()
                    .limit(Math.min(specs.limitByBucket
                            - results.size(), limit - foundResultsCount))
                    .forEach(resultsForBucket::add);


            for (var result : resultsForBucket) {
                seenResults.add(result.url.id());
            }

            domainCountFilter.addAll(i, resultsForBucket);

            results.addAll(resultsForBucket);
        }

        return results;
    }

    private EdgeSearchResultKeywordScore getSearchTermScore(int set, int bucketId, String term, long urlId) {
        final int termId = indexes.getDictionaryReader().get(term);

        var bucket = indexes.getBucket(bucketId);

        return new EdgeSearchResultKeywordScore(set, term,
                bucket.getTermScore(termId, urlId),
                bucket.isTermInBucket(IndexBlock.Title, termId, urlId),
                bucket.isTermInBucket(IndexBlock.Link, termId, urlId),
                bucket.isTermInBucket(IndexBlock.Site, termId, urlId),
                bucket.isTermInBucket(IndexBlock.Subjects, termId, urlId)
                );

    }

    private LongStream getQuery(int bucket, IndexSearchBudget budget, IndexBlock block,
                                LongPredicate filter, EdgeIndexSearchTerms searchTerms) {
        if (!indexes.isValidBucket(bucket)) {
            logger.warn("Invalid bucket {}", bucket);
            return LongStream.empty();
        }
        return indexes.getBucket(bucket).getQuery(block, filter, budget, searchTerms);
    }

    static class DomainResultCountFilter {
        final TLongIntMap resultsByDomain = new TLongIntHashMap(200, 0.75f, -1, 0);
        final int limitByDomain;

        DomainResultCountFilter(int limitByDomain) {
            this.limitByDomain = limitByDomain;
        }

        public boolean filterRawValue(int bucket, long value) {
            var domain = new EdgeId<EdgeDomain>((int)(value >>> 32));

            if (domain.id() == Integer.MAX_VALUE) {
                return true;
            }

            return resultsByDomain.get(getKey(bucket, domain)) <= limitByDomain;
        }

        long getKey(int bucket, EdgeId<EdgeDomain> id) {
            return ((long)bucket) << 32 | id.id();
        }

        public boolean test(int bucket, EdgeSearchResultItem item) {
            if (item.domain.id() == Integer.MAX_VALUE) {
                return true;
            }

            return resultsByDomain.adjustOrPutValue(getKey(bucket, item.domain), 1, 1) <= limitByDomain;
        }

        int getCount(int bucket, EdgeSearchResultItem item) {
            return resultsByDomain.get(getKey(bucket, item.domain));
        }

        public void addAll(int bucket, List<EdgeSearchResultItem> items) {
            items.forEach(item -> {
                resultsByDomain.adjustOrPutValue(getKey(bucket, item.domain), 1, 1);
            });
        }

        public boolean test(int bucket, DomainResultCountFilter root, EdgeSearchResultItem item) {
            if (item.domain.id() == Integer.MAX_VALUE) {
                return true;
            }
            return root.getCount(bucket, item) + resultsByDomain.adjustOrPutValue(getKey(bucket, item.domain), 1, 1) <= limitByDomain;
        }
    }

    private Optional<EdgeIndexSearchTerms> getSearchTerms(EdgeSearchSubquery request) {
        final List<Integer> excludes = new ArrayList<>();
        final List<Integer> includes = new ArrayList<>();

        for (var include : request.searchTermsInclude) {
            var word = lookUpWord(include);
            if (word.isEmpty()) {
                logger.debug("Unknown search term: " + include);
                return Optional.empty();
            }
            includes.add(word.getAsInt());
        }

        for (var exclude : request.searchTermsExclude) {
            lookUpWord(exclude).ifPresent(excludes::add);
        }

        if (includes.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new EdgeIndexSearchTerms(includes, excludes));
    }

    private OptionalInt lookUpWord(String s) {
        int ret = indexes.getDictionaryReader().get(s);
        if (ret == DictionaryHashMap.NO_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(ret);
    }

}

