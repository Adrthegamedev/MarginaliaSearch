package nu.marginalia.wmsa.edge.search.results;

import com.google.inject.Inject;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import nu.marginalia.util.BrailleBlockPunchCards;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.id.EdgeIdList;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultItem;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultKeywordScore;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import nu.marginalia.wmsa.edge.search.valuation.SearchResultValuator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SearchResultDecorator {
    private final EdgeDataStoreDao edgeDataStoreDao;
    private final SearchResultValuator valuator;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final boolean dumpTermData = Boolean.getBoolean("search-dump-term-data");

    @Inject
    public SearchResultDecorator(EdgeDataStoreDao edgeDataStoreDao, SearchResultValuator valuator) {
        this.edgeDataStoreDao = edgeDataStoreDao;
        this.valuator = valuator;
    }

    public List<EdgeUrlDetails> getAllUrlDetails(List<EdgeSearchResultItem> resultItems) {
        TIntObjectHashMap<EdgeUrlDetails> detailsById = new TIntObjectHashMap<>(resultItems.size());

        EdgeIdList<EdgeUrl> idList = resultItems.stream()
                                                .mapToInt(EdgeSearchResultItem::getUrlIdInt)
                                                .collect(EdgeIdList::new, EdgeIdList::add, EdgeIdList::addAll);

        List<EdgeUrlDetails> ret = edgeDataStoreDao.getUrlDetailsMulti(idList);

        for (var val : ret) {
            detailsById.put(val.id, val);
        }

        List<EdgeUrlDetails> retList = new ArrayList<>(resultItems.size());

        TIntArrayList missedIds = new TIntArrayList();
        for (var resultItem : resultItems) {

            var rankingId = resultItem.getRanking();
            var uid = resultItem.getUrlId().id();

            var details = detailsById.get(uid);
            if (details == null) {
                missedIds.add(uid);
                continue;
            }

            details.rankingId = rankingId;

            details.resultsFromSameDomain = resultItem.resultsFromDomain;
            details.termScore = calculateTermScore(resultItem, details);
            details.positions = getPositions(resultItem);
            details.resultItem = resultItem;

            logger.debug("{} -> {}", details.url, details.termScore);

            retList.add(details);
        }
        if (!missedIds.isEmpty()) {
            logger.info("Could not look up documents: {}", missedIds.toArray());
        }

        return retList;
    }

    private String getPositions(EdgeSearchResultItem resultItem) {
        int bits = resultItem.scores.stream()
                .filter(EdgeSearchResultKeywordScore::isRegular)
                .mapToInt(EdgeSearchResultKeywordScore::positions)
                .reduce(this::or)
                .orElse(0);

        return BrailleBlockPunchCards.printBits(bits, 32);

    }

    private int or(int a, int b) {
        return a | b;
    }

    private double calculateTermScore(EdgeSearchResultItem resultItem, EdgeUrlDetails details) {

        final double statePenalty = (details.domainState == EdgeDomainIndexingState.SPECIAL) ? 1.25 : 0;

        final double value =  valuator.evaluateTerms(resultItem.scores, details.words, details.title.length());
        if (dumpTermData) {
            System.out.println("---");
            System.out.println(details.getUrl());
            System.out.println(details.getTitle());
            System.out.println(details.words);
            for (var score : resultItem.scores) {
                System.out.println(score);
            }
            System.out.println(value);
        }

        return value + statePenalty;
    }

}
