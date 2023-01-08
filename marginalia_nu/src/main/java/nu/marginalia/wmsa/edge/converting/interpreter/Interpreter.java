package nu.marginalia.wmsa.edge.converting.interpreter;

import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DomainLink;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocument;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocumentWithError;
import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentsMetadata;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;

public interface Interpreter {
    void loadUrl(EdgeUrl[] url);
    void loadDomain(EdgeDomain[] domain);
    void loadRssFeed(EdgeUrl[] rssFeed);
    void loadDomainLink(DomainLink[] links);

    void loadProcessedDomain(EdgeDomain domain, EdgeDomainIndexingState state, String ip);
    void loadProcessedDocument(LoadProcessedDocument loadProcessedDocument);
    void loadProcessedDocumentWithError(LoadProcessedDocumentWithError loadProcessedDocumentWithError);

    void loadKeywords(EdgeUrl url, EdgePageDocumentsMetadata metadata, DocumentKeywords words);

    void loadDomainRedirect(DomainLink link);
}
