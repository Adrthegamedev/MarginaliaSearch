package nu.marginalia.wmsa.edge.index.service;

import com.google.inject.Guice;
import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.edge.index.model.*;
import nu.marginalia.wmsa.edge.index.postings.SearchIndexControl;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.svc.EdgeIndexLexiconService;
import nu.marginalia.wmsa.edge.index.svc.EdgeIndexOpsService;
import nu.marginalia.wmsa.edge.index.svc.EdgeIndexQueryService;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSetIdentifier;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultItem;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSpecification;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSubquery;
import nu.marginalia.wmsa.edge.model.search.domain.SpecificationLimit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import spark.Spark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
public class EdgeIndexIntegrationTest {

    @Inject
    Initialization initialization;
    @Inject
    EdgeIndexLexiconService lexiconService;
    @Inject
    EdgeIndexQueryService queryService;
    @Inject
    EdgeIndexOpsService opsService;

    @Inject
    SearchIndexControl searchIndexControl;

    EdgeIndexIntegrationTestModule testModule;

    @BeforeEach
    public void setUp() throws IOException, InterruptedException {

        testModule = new EdgeIndexIntegrationTestModule();
        Guice.createInjector(testModule).injectMembers(this);

        initialization.setReady();
        searchIndexControl.initialize(initialization);
    }

    @AfterEach
    public void tearDown() throws IOException {
        testModule.cleanUp();

        Spark.stop();
    }

    @Test
    public void willItBlend() throws Exception {
        for (int i = 1; i < 512; i++) {
            loadData(i);
        }
        searchIndexControl.getIndexWriter(0).flushWords();
        Thread.sleep(100);

        opsService.reindexEndpoint(null, null);

        var rsp = queryService.query(
                EdgeSearchSpecification.builder()
                        .queryLimits(new QueryLimits(10, 10, Integer.MAX_VALUE, 4000))
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .year(SpecificationLimit.none())
                        .quality(SpecificationLimit.none())
                        .size(SpecificationLimit.none())
                        .rank(SpecificationLimit.none())
                        .domains(new ArrayList<>())
                        .searchSetIdentifier(SearchSetIdentifier.NONE)
                        .subqueries(List.of(new EdgeSearchSubquery(
                                List.of("3", "5", "2"), List.of("4"), Collections.emptyList(), Collections.emptyList()
                        ))).build());

        Assertions.assertArrayEquals(
                new int[] { 30, 90, 150, 210, 270, 330, 390, 450, 510 },
                rsp.results
                        .stream()
                        .mapToInt(EdgeSearchResultItem::getUrlIdInt)
                        .toArray());
    }


    @Test
    public void testDomainQuery() throws Exception {
        for (int i = 1; i < 512; i++) {
            loadDataWithDomain(i/100, i);
        }
        searchIndexControl.getIndexWriter(0).flushWords();
        Thread.sleep(100);

        opsService.reindexEndpoint(null, null);

        var rsp = queryService.query(
                EdgeSearchSpecification.builder()
                        .queryLimits(new QueryLimits(10, 10, Integer.MAX_VALUE, 4000))
                        .year(SpecificationLimit.none())
                        .quality(SpecificationLimit.none())
                        .size(SpecificationLimit.none())
                        .rank(SpecificationLimit.none())
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .domains(List.of(2))
                        .subqueries(List.of(new EdgeSearchSubquery(
                                List.of("3", "5", "2"), List.of("4"), Collections.emptyList(), Collections.emptyList()
                        ))).build());
        Assertions.assertArrayEquals(
                new int[] { 210, 270 },
                rsp.results.stream().mapToInt(EdgeSearchResultItem::getUrlIdInt).toArray());
    }

    @Test
    public void testYearQuery() throws Exception {
        for (int i = 1; i < 512; i++) {
            loadData(i);
        }
        searchIndexControl.getIndexWriter(0).flushWords();
        Thread.sleep(100);

        opsService.reindexEndpoint(null, null);

        var rsp = queryService.query(
                EdgeSearchSpecification.builder()
                        .queryLimits(new QueryLimits(10, 10, Integer.MAX_VALUE, 4000))
                        .quality(SpecificationLimit.none())
                        .year(SpecificationLimit.equals(1998))
                        .size(SpecificationLimit.none())
                        .rank(SpecificationLimit.none())
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .searchSetIdentifier(SearchSetIdentifier.NONE)
                        .subqueries(List.of(new EdgeSearchSubquery(
                                List.of("4"), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
                        ))
                        ).build());

        Assertions.assertArrayEquals(
                new int[] { 12, 72, 132, 192, 252, 312, 372, 432, 492, 32 },
                rsp.results.stream().mapToInt(EdgeSearchResultItem::getUrlIdInt).toArray());
    }



    public void loadData(int id) {
        int[] factors = IntStream
                .rangeClosed(1, id)
                .filter(v -> (id % v) == 0)
                .toArray();

        long fullId = id | ((long) (32 - (id % 32)) << 32);

        var header = new SearchIndexJournalEntryHeader(factors.length, fullId, new EdgePageDocumentsMetadata(0, 0, 0, id % 5, id, id % 20, (byte) 0).encode());

        long[] data = new long[factors.length*2];
        for (int i = 0; i < factors.length; i++) {
            data[2*i] = lexiconService.getOrInsertWord(Integer.toString(factors[i]));
            data[2*i + 1] = new EdgePageWordMetadata(i, i, i, EnumSet.of(EdgePageWordFlags.Title)).encode();
        }

        lexiconService.putWords(0, header, new SearchIndexJournalEntry(data));
    }

    public void loadDataWithDomain(int domain, int id) {
        int[] factors = IntStream.rangeClosed(1, id).filter(v -> (id % v) == 0).toArray();
        var header = new SearchIndexJournalEntryHeader(factors.length, id | ((long) domain << 32), EdgePageDocumentsMetadata.defaultValue());

        long[] data = new long[factors.length*2];
        for (int i = 0; i < factors.length; i++) {
            data[2*i] = lexiconService.getOrInsertWord(Integer.toString(factors[i]));
            data[2*i + 1] = new EdgePageWordMetadata(i % 20, i, i, EnumSet.of(EdgePageWordFlags.Title)).encode();
        }

        lexiconService.putWords(0, header, new SearchIndexJournalEntry(data));
    }

}
