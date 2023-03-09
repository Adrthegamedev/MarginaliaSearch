package nu.marginalia.converting.processor;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.crawling.model.CrawlerDomainStatus;
import nu.marginalia.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.util.StringPool;
import nu.marginalia.converting.processor.logic.links.InternalLinkGraph;
import nu.marginalia.converting.processor.logic.LshDocumentDeduplicator;

import java.util.*;

public class DomainProcessor {
    private final DocumentProcessor documentProcessor;
    private final SiteWords siteWords;
    private final LshDocumentDeduplicator documentDeduplicator;

    @Inject
    public DomainProcessor(DocumentProcessor documentProcessor,
                           SiteWords siteWords,
                           LshDocumentDeduplicator documentDeduplicator) {
        this.documentProcessor = documentProcessor;
        this.siteWords = siteWords;
        this.documentDeduplicator = documentDeduplicator;
    }

    public ProcessedDomain process(CrawledDomain crawledDomain) {
        var ret = new ProcessedDomain();


        ret.domain = new EdgeDomain(crawledDomain.domain);
        ret.ip = crawledDomain.ip;

        if (crawledDomain.redirectDomain != null) {
            ret.redirect = new EdgeDomain(crawledDomain.redirectDomain);
        }

        if (crawledDomain.doc != null) {
            ret.documents = new ArrayList<>(crawledDomain.doc.size());

            fixBadCanonicalTags(crawledDomain.doc);

            for (var doc : crawledDomain.doc) {
                var processedDoc = documentProcessor.process(doc, crawledDomain);

                if (processedDoc.url != null) {
                    ret.documents.add(processedDoc);
                }

            }

            documentDeduplicator.deduplicate(ret.documents);

            InternalLinkGraph internalLinkGraph = new InternalLinkGraph();

            ret.documents.forEach(internalLinkGraph::accept);
            ret.documents.forEach(doc -> {
                if (doc.details != null && doc.details.metadata != null) {
                    doc.details.metadata = doc.details.metadata.withSize(internalLinkGraph.numKnownUrls());
                }
            });

            siteWords.flagCommonSiteWords(ret);
            siteWords.flagAdjacentWords(internalLinkGraph, ret);

        }
        else {
            ret.documents = Collections.emptyList();
        }

        ret.state = getState(crawledDomain.crawlerStatus);

        return ret;
    }


    private void fixBadCanonicalTags(List<CrawledDocument> docs) {
        Map<String, Set<String>> seenCanonicals = new HashMap<>();
        Set<String> seenUrls = new HashSet<>();

        // Sometimes sites set a blanket canonical link to their root page
        // this removes such links from consideration

        for (var document : docs) {
            if (!Strings.isNullOrEmpty(document.canonicalUrl)
                    && !Objects.equals(document.canonicalUrl, document.url)) {
                seenCanonicals.computeIfAbsent(document.canonicalUrl, url -> new HashSet<>()).add(document.documentBodyHash);
            }
            seenUrls.add(document.url);
        }

        for (var document : docs) {
            if (!Strings.isNullOrEmpty(document.canonicalUrl)
                    && !Objects.equals(document.canonicalUrl, document.url)
                    && seenCanonicals.getOrDefault(document.canonicalUrl, Collections.emptySet()).size() > 1) {

                if (seenUrls.add(document.canonicalUrl)) {
                    document.canonicalUrl = document.url;
                }
                else {
                    document.crawlerStatus = CrawlerDocumentStatus.BAD_CANONICAL.name();
                }
            }
        }

        for (var document : docs) {
            if (!Strings.isNullOrEmpty(document.canonicalUrl)
                    && !Objects.equals(document.canonicalUrl, document.url)
                && seenCanonicals.getOrDefault(document.canonicalUrl, Collections.emptySet()).size() > 1) {
                document.canonicalUrl = document.url;
            }
        }

        // Ignore canonical URL if it points to a different domain
        // ... this confuses the hell out of the loader
        for (var document : docs) {
            if (Strings.isNullOrEmpty(document.canonicalUrl))
                continue;

            Optional<EdgeUrl> cUrl = EdgeUrl.parse(document.canonicalUrl);
            Optional<EdgeUrl> dUrl = EdgeUrl.parse(document.url);

            if (cUrl.isPresent() && dUrl.isPresent()
                    && !Objects.equals(cUrl.get().domain, dUrl.get().domain))
            {
                document.canonicalUrl = document.url;
            }
        }
    }

    private EdgeDomainIndexingState getState(String crawlerStatus) {
        return switch (CrawlerDomainStatus.valueOf(crawlerStatus)) {
            case OK -> EdgeDomainIndexingState.ACTIVE;
            case REDIRECT -> EdgeDomainIndexingState.REDIR;
            case BLOCKED -> EdgeDomainIndexingState.BLOCKED;
            default -> EdgeDomainIndexingState.ERROR;
        };
    }

}
