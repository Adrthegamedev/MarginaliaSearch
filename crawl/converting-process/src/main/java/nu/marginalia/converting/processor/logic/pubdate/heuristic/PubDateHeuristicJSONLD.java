package nu.marginalia.converting.processor.logic.pubdate.heuristic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import nu.marginalia.model.crawl.EdgeHtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.converting.processor.logic.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.logic.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.converting.processor.logic.pubdate.PubDateEffortLevel;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class PubDateHeuristicJSONLD implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, EdgeHtmlStandard htmlStandard) {
        for (var tag : document.select("script[type=\"application/ld+json\"]")) {
            var maybeDate = parseLdJson(tag.data())
                    .flatMap(PubDateParser::attemptParseDate);

            if (maybeDate.isPresent()) {
                return maybeDate;
            }
        }

        return Optional.empty();
    }


    private static class JsonModel {
        String datePublished;
    }
    private static Gson gson = new GsonBuilder().create();

    public Optional<String> parseLdJson(String content) {
        try {
            var model = gson.fromJson(content, JsonModel.class);
            return Optional.ofNullable(model)
                    .map(m -> m.datePublished);
        }
        catch (JsonSyntaxException ex) {
            return Optional.empty();
        }
    }

}
