package nu.marginalia.converting.processor;

import com.google.inject.Inject;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.model.crawl.EdgeUrlState;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.plugin.AbstractDocumentProcessorPlugin;
import nu.marginalia.converting.processor.plugin.HtmlDocumentProcessorPlugin;
import nu.marginalia.converting.processor.plugin.PlainTextDocumentProcessorPlugin;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.*;

public class DocumentProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final Set<String> acceptedContentTypes = Set.of("application/xhtml+xml",
            "application/xhtml",
            "text/html",
            "text/plain");


    private final List<AbstractDocumentProcessorPlugin> processorPlugins = new ArrayList<>();

    @Inject
    public DocumentProcessor(HtmlDocumentProcessorPlugin htmlDocumentProcessorPlugin,
                             PlainTextDocumentProcessorPlugin plainTextDocumentProcessorPlugin)
    {

        processorPlugins.add(htmlDocumentProcessorPlugin);
        processorPlugins.add(plainTextDocumentProcessorPlugin);
    }

    public ProcessedDocument process(CrawledDocument crawledDocument, CrawledDomain crawledDomain) {
        ProcessedDocument ret = new ProcessedDocument();

        try {
            processDocument(crawledDocument, crawledDomain, ret);
        }
        catch (DisqualifiedException ex) {
            ret.state = EdgeUrlState.DISQUALIFIED;
            ret.stateReason = ex.reason.toString();
            logger.debug("Disqualified {}: {}", ret.url, ex.reason);
        }
        catch (Exception ex) {
            ret.state = EdgeUrlState.DISQUALIFIED;
            ret.stateReason = DisqualifiedException.DisqualificationReason.PROCESSING_EXCEPTION.toString();
            logger.info("Failed to convert " + crawledDocument.url, ex);
            ex.printStackTrace();
        }

        return ret;
    }

    private void processDocument(CrawledDocument crawledDocument, CrawledDomain crawledDomain, ProcessedDocument ret) throws URISyntaxException, DisqualifiedException {

        var crawlerStatus = CrawlerDocumentStatus.valueOf(crawledDocument.crawlerStatus);
        if (crawlerStatus != CrawlerDocumentStatus.OK) {
            throw new DisqualifiedException(crawlerStatus);
        }

        if (AcceptableAds.hasAcceptableAdsHeader(crawledDocument)) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.ACCEPTABLE_ADS);
        }

        if (!isAcceptedContentType(crawledDocument)) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.CONTENT_TYPE);
        }


        ret.url = getDocumentUrl(crawledDocument);
        ret.state = crawlerStatusToUrlState(crawledDocument.crawlerStatus, crawledDocument.httpStatus);

        final var plugin = findPlugin(crawledDocument);

        AbstractDocumentProcessorPlugin.DetailsWithWords detailsWithWords = plugin.createDetails(crawledDomain, crawledDocument);

        ret.details = detailsWithWords.details();
        ret.words = detailsWithWords.words();
    }

    private AbstractDocumentProcessorPlugin findPlugin(CrawledDocument crawledDocument) throws DisqualifiedException {
        for (var plugin : processorPlugins) {
            if (plugin.isApplicable(crawledDocument))
                return plugin;
        }

        throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.CONTENT_TYPE);
    }


    private EdgeUrl getDocumentUrl(CrawledDocument crawledDocument)
            throws URISyntaxException
    {
        if (crawledDocument.canonicalUrl != null) {
            try {
                return new EdgeUrl(crawledDocument.canonicalUrl);
            }
            catch (URISyntaxException ex) { /* fallthrough */ }
        }

        return new EdgeUrl(crawledDocument.url);
    }

    public static boolean isAcceptedContentType(CrawledDocument crawledDocument) {
        if (crawledDocument.contentType == null) {
            return false;
        }

        var ct = crawledDocument.contentType;

        if (acceptedContentTypes.contains(ct))
            return true;

        if (ct.contains(";")) {
            return acceptedContentTypes.contains(ct.substring(0, ct.indexOf(';')));
        }
        return false;
    }

    private EdgeUrlState crawlerStatusToUrlState(String crawlerStatus, int httpStatus) {
        return switch (CrawlerDocumentStatus.valueOf(crawlerStatus)) {
            case OK -> httpStatus < 300 ? EdgeUrlState.OK : EdgeUrlState.DEAD;
            case REDIRECT -> EdgeUrlState.REDIRECT;
            default -> EdgeUrlState.DEAD;
        };
    }

}
