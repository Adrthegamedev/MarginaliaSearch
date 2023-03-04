package nu.marginalia.memex.gemini.gmi.line;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;

@AllArgsConstructor @Getter @ToString
public class GemtextPragma extends AbstractGemtextLine {
    private final String line;
    private final MemexNodeHeadingId heading;

    @Override
    public <T> T visit(GemtextLineVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public boolean breaksTask() {
        return false;
    }
}
