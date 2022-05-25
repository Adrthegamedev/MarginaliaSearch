package nu.marginalia.util.language.processing.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor @EqualsAndHashCode @Getter
public class WordRep implements Comparable<WordRep> {
    public WordRep(DocumentSentence sent, WordSpan span) {
        word = sent.constructWordFromSpan(span);
        stemmed = sent.constructStemmedWordFromSpan(span);
        length = span.end - span.start;
    }
    public final int length;
    public final String word;
    public final String stemmed;

    @Override
    public int compareTo(@NotNull WordRep o) {
        return stemmed.compareTo(o.stemmed);
    }

    @Override
    public String toString() {
        return word;
    }
}
