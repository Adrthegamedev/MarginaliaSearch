package nu.marginalia.wmsa.memex.model.render;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nu.marginalia.gemini.gmi.GemtextDocument;
import nu.marginalia.wmsa.memex.renderer.MemexHtmlRenderer;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public class MemexRenderUploadFormModel implements MemexRendererableDirect {
    public final MemexNodeUrl url;
    public final List<GemtextDocument> docs;

    public String getFilename() {
        return url.getFilename();
    }

    public List<GemtextDocument> getDocs() {
        return docs.stream().sorted(Comparator.comparing(GemtextDocument::getUrl).reversed()).collect(Collectors.toList());
    }

    @Override
    public String render(MemexHtmlRenderer renderer) {
        return renderer.renderModel(this);
    }

}
