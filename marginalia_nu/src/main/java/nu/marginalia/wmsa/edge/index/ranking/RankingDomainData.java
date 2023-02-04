package nu.marginalia.wmsa.edge.index.ranking;

import lombok.AllArgsConstructor;
import lombok.Data;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;

@Data
@AllArgsConstructor
public class RankingDomainData {
    public final int id;
    public final String name;
    private int alias;
    private EdgeDomainIndexingState state;
    public final int knownUrls;

    public int resolveAlias() {
        if (alias == 0) return id;
        return alias;
    }

    public boolean isAlias() {
        return alias != 0;
    }

    public boolean isSpecial() {
        return EdgeDomainIndexingState.SPECIAL == state;
    }

    public boolean isSocialMedia() {
        return EdgeDomainIndexingState.SOCIAL_MEDIA == state;
    }
}
