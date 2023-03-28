package nu.marginalia.converting.processor.logic;

import crawlercommons.utils.Strings;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.converting.model.HtmlStandard;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.converting.model.DisqualifiedException;
import org.jsoup.nodes.Document;

import java.util.Set;

public class DocumentValuator {

    public double getQuality(CrawledDocument crawledDocument, HtmlStandard htmlStandard, Document parsedDocument) throws DisqualifiedException {
        double scriptPenalty = getScriptPenalty(parsedDocument);

        int textBodyLength = parsedDocument.text().length();
        int rawLength = crawledDocument.documentBody.length();

        if (textBodyLength == 0) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.LENGTH);
        }

        return Math.log(textBodyLength / (double) (1+rawLength))*htmlStandard.scale
                + htmlStandard.offset
                - scriptPenalty;
    }


    private int getScriptPenalty(Document parsed) {
        var scriptTags = parsed.getElementsByTag("script");
        String scriptText = scriptTags.html();
        int badScript = 0;
        if (scriptText.contains(".createElement(")) {
            badScript = 1;
        }

        double scriptPenalty = 0;
        for (var tag : scriptTags) {
            String srcAttr = tag.attr("src");
            if (srcAttr.contains("wp-content") || srcAttr.contains("wp-includes") || srcAttr.contains("jquery")) {
                scriptPenalty += 0.49;
            }
            else if (!Strings.isBlank(srcAttr)) {
                scriptPenalty += 1;
            }
        }
        return (int)(scriptPenalty + badScript + (scriptText.length())/1000.);
    }

}
