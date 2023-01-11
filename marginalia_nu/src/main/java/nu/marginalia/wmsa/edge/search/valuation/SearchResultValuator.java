package nu.marginalia.wmsa.edge.search.valuation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordFlags;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordMetadata;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultKeywordScore;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSubquery;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.Math.min;

@Singleton
public class SearchResultValuator {
    private final TermFrequencyDict dict;

    private static final Pattern separator = Pattern.compile("_");

    private static final int MIN_LENGTH = 2000;
    private static final int AVG_LENGTH = 5000;
    private final int docCount;

    @Inject
    public SearchResultValuator(TermFrequencyDict dict) {
        this.dict = dict;
        docCount = dict.docCount();
    }


    public double preEvaluate(EdgeSearchSubquery sq) {
        final String[] terms = sq.searchTermsInclude.stream().filter(f -> !f.contains(":")).toArray(String[]::new);

        double termSum = 0.;
        double factorSum = 0.;

        final double[] weights = getTermWeights(terms);

        for (int i = 0; i < terms.length; i++) {
            final double factor = 1. / (1.0 + weights[i]);

            factorSum += factor;
            termSum += factor; // fixme

            // This logic is the casualty of refactoring. It is intended to prioritize search queries
            // according to sum-of-idf, but right now it uses many CPU cycles to always calculate the value 1.
        }

        return termSum / factorSum;
    }

    public double evaluateTerms(List<EdgeSearchResultKeywordScore> rawScores, int length, int titleLength) {
        int sets = 1 + rawScores.stream().mapToInt(EdgeSearchResultKeywordScore::set).max().orElse(0);

        double bestScore = 10;
        double bestAllTermsFactor = 1.;

        final double priorityTermBonus;

        if (hasPriorityTerm(rawScores)) {
            priorityTermBonus = 0.5;
        }
        else {
            priorityTermBonus = 1;
        }

        for (int set = 0; set <= sets; set++) {
            SearchResultsKeywordSet keywordSet = createKeywordSet(rawScores, set);

            if (keywordSet == null)
                continue;

            final double bm25Factor = getBM25(keywordSet, length);
            final double minCountFactor = getMinCountFactor(keywordSet);

            bestScore = min(bestScore, bm25Factor * minCountFactor);

            bestAllTermsFactor = min(bestAllTermsFactor, getAllTermsFactorForSet(keywordSet, titleLength));

        }

        return bestScore * (0.1 + 0.9 * bestAllTermsFactor) * priorityTermBonus;
    }

    private boolean hasPriorityTerm(List<EdgeSearchResultKeywordScore> rawScores) {
        return rawScores.stream()
                .findAny()
                .map(EdgeSearchResultKeywordScore::hasPriorityTerms)
                .orElse(false);
    }

    private double getMinCountFactor(SearchResultsKeywordSet keywordSet) {
        // Penalize results with few keyword hits

        int min = 32;

        for (var keyword : keywordSet) {
            if (!keyword.wordMetadata.hasFlag(EdgePageWordFlags.Title) && keyword.score.isRegular()) {
                min = min(min, keyword.count());
            }
        }

        if (min <= 1) return 2;
        if (min <= 2) return 1.5;
        if (min <= 3) return 1.25;
        return 1;
    }

    private double getBM25(SearchResultsKeywordSet keywordSet, int length) {
        final double scalingFactor = 750.;

        final double wf1 = 0.7;
        double k = 2;

        double sum = 0.;

        for (var keyword : keywordSet) {
            double count = Math.min(255, keyword.count());
            double wt = keyword.weight() * keyword.weight() / keywordSet.length();

            final double invFreq = Math.log(1.0 + (docCount - wt + 0.5)/(wt + 0.5));

            sum += invFreq * (count * (k + 1)) / (count + k * (1 - wf1 + wf1 * AVG_LENGTH/length));
        }

        return Math.sqrt(scalingFactor / sum);
    }

    private double getAllTermsFactorForSet(SearchResultsKeywordSet set, int titleLength) {
        double totalFactor = 1.;

        double totalWeight = 0;
        for (var keyword : set) {
            totalWeight += keyword.weight();
        }

        for (var keyword : set) {
            totalFactor *= getAllTermsFactor(keyword, totalWeight, titleLength);
        }

        if (set.keywords.length > 1) {
            totalFactor = calculateTermCoherencePenalty(set, totalFactor);
        }
        else {
            totalFactor = calculateSingleTermBonus(set, totalFactor);
        }

        return totalFactor;
    }

    private double calculateSingleTermBonus(SearchResultsKeywordSet set, double totalFactor) {
        var theKeyword = set.iterator().next();

        if (theKeyword.wordMetadata.hasFlag(EdgePageWordFlags.Title)) {
            return totalFactor * 0.5;
        }
        else if (theKeyword.wordMetadata.hasFlag(EdgePageWordFlags.Subjects)) {
            return totalFactor * 0.6;
        }
        else if (theKeyword.wordMetadata.hasFlag(EdgePageWordFlags.SiteAdjacent)) {
            return totalFactor * 0.65;
        }
        else if (theKeyword.wordMetadata.hasFlag(EdgePageWordFlags.Site)) {
            return totalFactor * 0.7;
        }
        return totalFactor;
    }

    private double calculateTermCoherencePenalty(SearchResultsKeywordSet keywordSet, double f) {
        long maskDirect = ~0;
        long maskAdjacent = ~0;

        byte excludeMask = (byte) (EdgePageWordFlags.Title.asBit() | EdgePageWordFlags.Subjects.asBit() | EdgePageWordFlags.Synthetic.asBit());

        for (var keyword : keywordSet) {
            var meta = keyword.wordMetadata;
            long positions;

            if (meta.isEmpty()) {
                return f;
            }


            positions = meta.positions();

            maskAdjacent &= (positions | (positions << 1) | (positions >>> 1));
            if (positions != 0 && !EdgePageWordMetadata.hasAnyFlags(meta.flags(),  excludeMask))
            {
                maskDirect &= positions;
            }
        }

        if (maskAdjacent == 0) {
            return 2 * f;
        }

        if (maskDirect == 0) {
            return 1.25 * f;
        }

        if (maskDirect != ~0L) {
            double locationFactor = 0.5 + Math.max(0.,
                    0.5 * Long.numberOfTrailingZeros(maskDirect) / 16.
                        - Math.sqrt(Long.bitCount(maskDirect) - 1) / 3.
            );

            return f * locationFactor;
        }
        else {
            return f;
        }
    }

    private double getAllTermsFactor(SearchResultsKeyword keyword, double totalWeight, int titleLength) {
        double f = 1.;

        final double k = keyword.weight() / totalWeight;

        EnumSet<EdgePageWordFlags> flags = keyword.flags();

        final boolean title = flags.contains(EdgePageWordFlags.Title);
        final boolean site = flags.contains(EdgePageWordFlags.Site);
        final boolean siteAdjacent = flags.contains(EdgePageWordFlags.SiteAdjacent);
        final boolean subject = flags.contains(EdgePageWordFlags.Subjects);
        final boolean names = flags.contains(EdgePageWordFlags.NamesWords);

        if (title) {
            if (titleLength <= 64) {
                f *= Math.pow(0.5, k);
            }
            else if (titleLength < 96) {
                f *= Math.pow(0.75, k);
            }
            else { // likely keyword stuffing if the title is this long
                f *= Math.pow(0.9, k);
            }
        }

        if (site) {
            f *= Math.pow(0.75, k);
        }
        else if (siteAdjacent) {
            f *= Math.pow(0.8, k);
        }

        if (subject) {
            f *= Math.pow(0.8, k);
        }

        if (!title && !subject && names) {
            f *= Math.pow(0.9, k);
        }

        return f;
    }

    private double[] getTermWeights(EdgeSearchResultKeywordScore[] scores) {
        double[] weights = new double[scores.length];

        for (int i = 0; i < scores.length; i++) {
            String[] parts = separator.split(scores[i].keyword());
            double sumScore = 0.;

            int count = 0;
            for (String part : parts) {
                if (!WordPatterns.isStopWord(part)) {
                    sumScore += dict.getTermFreq(part);
                    count++;
                }
            }
            if (count == 0) count = 1;

            weights[i] = Math.sqrt(sumScore)/count;
        }

        return weights;
    }


    private double[] getTermWeights(String[] words) {
        double[] weights = new double[words.length];

        for (int i = 0; i < words.length; i++) {
            String[] parts = separator.split(words[i]);
            double sumScore = 0.;

            int count = 0;
            for (String part : parts) {
                if (!WordPatterns.isStopWord(part)) {
                    sumScore += dict.getTermFreq(part);
                    count++;
                }
            }
            if (count == 0) count = 1;

            weights[i] = Math.sqrt(sumScore)/count;
        }

        return weights;
    }

    private SearchResultsKeywordSet createKeywordSet(List<EdgeSearchResultKeywordScore> rawScores, int thisSet) {
        EdgeSearchResultKeywordScore[] scores = rawScores.stream().filter(w -> w.set() == thisSet && !w.keyword().contains(":")).toArray(EdgeSearchResultKeywordScore[]::new);
        if (scores.length == 0) {
            return null;
        }
        final double[] weights = getTermWeights(scores);

        SearchResultsKeyword[] keywords = new SearchResultsKeyword[scores.length];
        for (int i = 0; i < scores.length; i++) {
            keywords[i] = new SearchResultsKeyword(scores[i], weights[i]);
        }

        return new SearchResultsKeywordSet(keywords);

    }


    private record SearchResultsKeyword(EdgeSearchResultKeywordScore score, EdgePageWordMetadata wordMetadata, double weight) {
        public SearchResultsKeyword(EdgeSearchResultKeywordScore score,  double weight) {
            this(score, new EdgePageWordMetadata(score.encodedWordMetadata()), weight);
        }

        public int tfIdf() {
            return wordMetadata.tfIdf();
        }
        public int count() {
            return wordMetadata.count();
        }
        public EnumSet<EdgePageWordFlags> flags() {
            return wordMetadata.flagSet();
        }
    }

    private record SearchResultsKeywordSet(
            SearchResultsKeyword[] keywords) implements Iterable<SearchResultsKeyword>
    {
        @NotNull
        @Override
        public Iterator<SearchResultsKeyword> iterator() {
            return Arrays.stream(keywords).iterator();
        }

        public int length() {
            return keywords.length;
        }
    }
}
