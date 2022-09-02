package nu.marginalia.wmsa.edge.search.query;

import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.assistant.dict.NGramBloomFilter;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

class QueryParserTest {
    private QueryParser parser;
    private static TermFrequencyDict dict;
    private static EnglishDictionary englishDictionary;
    private static NGramBloomFilter nGramBloomFilter;
    private static final LanguageModels lm = TestLanguageModels.getLanguageModels();

    @BeforeEach
    public void setUp() throws IOException {
        dict = new TermFrequencyDict(lm);
        nGramBloomFilter = new NGramBloomFilter(lm);
        englishDictionary = new EnglishDictionary(nGramBloomFilter);

        parser = new QueryParser(englishDictionary, new QueryVariants(lm, dict, nGramBloomFilter, englishDictionary));
    }

    @Test
    void variantQueries() {
        var r = parser.parse("car stemming");
        parser.variantQueries(r).forEach(query -> {
            System.out.println(query.stream().map(t -> t.str).collect(Collectors.joining(", ")));
        });
    }
}