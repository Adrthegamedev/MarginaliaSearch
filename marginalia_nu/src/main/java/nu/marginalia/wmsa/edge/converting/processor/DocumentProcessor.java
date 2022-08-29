package nu.marginalia.wmsa.edge.converting.processor;

import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.util.language.LanguageFilter;
import nu.marginalia.util.language.processing.DocumentKeywordExtractor;
import nu.marginalia.util.language.processing.SentenceExtractor;
import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.wmsa.edge.converting.model.DisqualifiedException;
import nu.marginalia.wmsa.edge.converting.model.DisqualifiedException.DisqualificationReason;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDocument;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDocumentDetails;
import nu.marginalia.wmsa.edge.converting.processor.logic.*;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDocument;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import nu.marginalia.wmsa.edge.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;

import static nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard.UNKNOWN;

public class DocumentProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final int minDocumentLength;
    private final double minDocumentQuality;

    private static final Set<String> acceptedContentTypes = Set.of("application/xhtml+xml", "application/xhtml", "text/html");

    private final SentenceExtractor sentenceExtractor;
    private final FeatureExtractor featureExtractor;
    private final TitleExtractor titleExtractor;
    private final DocumentKeywordExtractor keywordExtractor;
    private final SummaryExtractor summaryExtractor;

    private static final DocumentValuator documentValuator = new DocumentValuator();
    private static final LanguageFilter languageFilter = new LanguageFilter();
    private static final LinkParser linkParser = new LinkParser();
    private static final FeedExtractor feedExtractor = new FeedExtractor(linkParser);

    @Inject
    public DocumentProcessor(@Named("min-document-length") Integer minDocumentLength,
                             @Named("min-document-quality") Double minDocumentQuality,
                             SentenceExtractor sentenceExtractor,
                             FeatureExtractor featureExtractor,
                             TitleExtractor titleExtractor,
                             DocumentKeywordExtractor keywordExtractor,
                             SummaryExtractor summaryExtractor)
    {
        this.minDocumentLength = minDocumentLength;
        this.minDocumentQuality = minDocumentQuality;
        this.sentenceExtractor = sentenceExtractor;
        this.featureExtractor = featureExtractor;
        this.titleExtractor = titleExtractor;
        this.keywordExtractor = keywordExtractor;
        this.summaryExtractor = summaryExtractor;
    }

    public ProcessedDocument process(CrawledDocument crawledDocument, CrawledDomain crawledDomain) {
        ProcessedDocument ret = new ProcessedDocument();

        try {
            ret.url = new EdgeUrl(crawledDocument.url);
            ret.state = crawlerStatusToUrlState(crawledDocument.crawlerStatus, crawledDocument.httpStatus);

            if (ret.state == EdgeUrlState.OK) {

                if (AcceptableAds.hasAcceptableAdsHeader(crawledDocument)) {
                    throw new DisqualifiedException(DisqualificationReason.ACCEPTABLE_ADS);
                }

                if (isAcceptedContentType(crawledDocument)) {
                    var detailsWords = createDetails(crawledDomain, crawledDocument);

                    ret.details = detailsWords.details();
                    ret.words = detailsWords.words();
                }
                else {
                    throw new DisqualifiedException(DisqualificationReason.CONTENT_TYPE);
                }
            }
            else {
                throw new DisqualifiedException(DisqualificationReason.STATUS);
            }
        }
        catch (DisqualifiedException ex) {
            ret.state = EdgeUrlState.DISQUALIFIED;
            logger.debug("Disqualified {}: {}", ret.url, ex.reason);
        }
        catch (Exception ex) {
            ret.state = EdgeUrlState.DISQUALIFIED;
            logger.info("Failed to convert " + ret.url, ex);
            ex.printStackTrace();
        }

        return ret;
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

    private DetailsWithWords createDetails(CrawledDomain crawledDomain, CrawledDocument crawledDocument)
            throws DisqualifiedException, URISyntaxException {

        Document doc = Jsoup.parse(crawledDocument.documentBody);

        if (AcceptableAds.hasAcceptableAdsTag(doc)) {
            throw new DisqualifiedException(DisqualificationReason.ACCEPTABLE_ADS);
        }
        if (doc.select("meta[name=robots]").attr("content").contains("noindex")) {
            throw new DisqualifiedException(DisqualificationReason.FORBIDDEN);
        }

        DomPruner domPruner = new DomPruner();
        Document prunedDoc = doc.clone();
        domPruner.prune(prunedDoc, 0.5);
        var dld = sentenceExtractor.extractSentences(prunedDoc);

        checkDocumentLanguage(dld);

        var ret = new ProcessedDocumentDetails();

        ret.description = getDescription(doc);
        ret.length = getLength(doc);
        ret.standard = getHtmlStandard(doc);
        ret.title = titleExtractor.getTitleAbbreviated(doc, dld, crawledDocument.url);
        ret.features = featureExtractor.getFeatures(crawledDomain, doc, dld);
        ret.quality = documentValuator.getQuality(ret.standard, doc, dld);
        ret.hashCode = HashCode.fromString(crawledDocument.documentBodyHash).asLong();

        EdgePageWordSet words;
        if (ret.quality < minDocumentQuality || dld.totalNumWords() < minDocumentLength) {
            words = keywordExtractor.extractKeywordsMinimal(dld);
        }
        else {
            words = keywordExtractor.extractKeywords(dld);
        }

        var url = new EdgeUrl(crawledDocument.url);
        addMetaWords(ret, url, crawledDomain, words);

        getLinks(url, ret, doc, words);

        return new DetailsWithWords(ret, words);
    }

    private void addMetaWords(ProcessedDocumentDetails ret, EdgeUrl url, CrawledDomain domain, EdgePageWordSet words) {
        List<String> tagWords = new ArrayList<>();

        var edgeDomain = url.domain;
        tagWords.add("format:"+ret.standard.toString().toLowerCase());

        tagWords.add("site:" + edgeDomain.toString().toLowerCase());
        if (!Objects.equals(edgeDomain.toString(), edgeDomain.domain)) {
            tagWords.add("site:" + edgeDomain.domain.toLowerCase());
        }

        tagWords.add("proto:"+url.proto.toLowerCase());
        tagWords.add("js:" + Boolean.toString(ret.features.contains(HtmlFeature.JS)).toLowerCase());

        if (domain.ip != null) {
            tagWords.add("ip:" + domain.ip.toLowerCase()); // lower case because IPv6 is hexadecimal
        }

        ret.features.stream().map(HtmlFeature::getKeyword).forEach(tagWords::add);

        words.append(IndexBlock.Meta, tagWords);
    }

    private void getLinks(EdgeUrl baseUrl, ProcessedDocumentDetails ret, Document doc, EdgePageWordSet words) {

        final LinkProcessor lp = new LinkProcessor(ret, baseUrl);

        baseUrl = linkParser.getBaseLink(doc, baseUrl);

        EdgeDomain domain = baseUrl.domain;

        for (var atag : doc.getElementsByTag("a")) {
            var linkOpt = linkParser.parseLinkPermissive(baseUrl, atag);
            if (linkParser.shouldIndexLink(atag)) {
                linkOpt.ifPresent(lp::accept);
            }
            else if (linkOpt.isPresent()) {
                if (linkParser.hasBinarySuffix(linkOpt.get().toString())) {
                    linkOpt.ifPresent(lp::acceptNonIndexable);
                }
            }

        }
        for (var frame : doc.getElementsByTag("frame")) {
            linkParser.parseFrame(baseUrl, frame).ifPresent(lp::accept);
        }
        for (var frame : doc.getElementsByTag("iframe")) {
            linkParser.parseFrame(baseUrl, frame).ifPresent(lp::accept);
        }
        for (var link : doc.select("link[rel=alternate]")) {
            feedExtractor
                    .getFeedFromAlternateTag(baseUrl, link)
                    .ifPresent(lp::acceptFeed);
        }

        final Set<String> linkTerms = new HashSet<>();

        for (var fd : lp.getForeignDomains()) {
            linkTerms.add("links:"+fd.toString().toLowerCase());
            linkTerms.add("links:"+fd.getDomain().toLowerCase());
        }

        words.append(IndexBlock.Meta, linkTerms);

        Set<String> fileKeywords = new HashSet<>(100);
        for (var link : lp.getNonIndexableUrls()) {

            if (!Objects.equals(domain, link.domain)) {
                continue;
            }

            synthesizeFilenameKeyword(fileKeywords, link);

        }

        words.append(IndexBlock.Artifacts, fileKeywords);
    }

    private void synthesizeFilenameKeyword(Set<String> fileKeywords, EdgeUrl link) {

        Path pFilename = Path.of(link.path.toLowerCase()).getFileName();

        if (pFilename == null) return;

        String filename = pFilename.toString();
        if (filename.length() > 32
                || filename.endsWith(".xml")
                || filename.endsWith(".jpg")
                || filename.endsWith(".png")
                || filename.endsWith(".pdf")
                || filename.endsWith(".gif"))
            return;

        fileKeywords.add(filename.replace(' ', '_'));
    }

    private void checkDocumentLanguage(DocumentLanguageData dld) throws DisqualifiedException {
        double languageAgreement = languageFilter.dictionaryAgreement(dld);
        if (languageAgreement < 0.1) {
            throw new DisqualifiedException(DisqualificationReason.LANGUAGE);
        }
    }

    private EdgeHtmlStandard getHtmlStandard(Document doc) {
        EdgeHtmlStandard htmlStandard = HtmlStandardExtractor.parseDocType(doc.documentType());

        if (UNKNOWN.equals(htmlStandard)) {
            return HtmlStandardExtractor.sniffHtmlStandard(doc);
        }
        return htmlStandard;
    }

    private String getDescription(Document doc) {
        return summaryExtractor.extractSummary(doc);
    }

    private int getLength(Document doc) {
        return doc.text().length();
    }

    private record DetailsWithWords(ProcessedDocumentDetails details, EdgePageWordSet words) {}
}
