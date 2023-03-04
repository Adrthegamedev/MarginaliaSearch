package nu.marginalia.memex.gemini.gmi.parser;

import nu.marginalia.memex.gemini.gmi.line.AbstractGemtextLine;
import nu.marginalia.memex.gemini.gmi.line.GemtextPragma;
import nu.marginalia.memex.gemini.gmi.line.GemtextText;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;

import java.util.regex.Pattern;

public class GemtextPragmaParser {
    private static final Pattern pragmaPattern = Pattern.compile("^%%%\\s*(.*|$)$");

    public static AbstractGemtextLine parse(String s, MemexNodeHeadingId heading) {
        var matcher = pragmaPattern.matcher(s);

        if (!matcher.matches()) {
            return new GemtextText(s, heading);
        }

        String task = matcher.group(1);

        return new GemtextPragma(task, heading);
    }


}
