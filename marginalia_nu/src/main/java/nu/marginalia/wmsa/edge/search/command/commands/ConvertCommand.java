package nu.marginalia.wmsa.edge.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.search.UnitConversion;
import nu.marginalia.wmsa.edge.search.command.ResponseType;
import nu.marginalia.wmsa.edge.search.command.SearchCommandInterface;
import nu.marginalia.wmsa.edge.search.command.SearchParameters;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class ConvertCommand implements SearchCommandInterface {
    private final UnitConversion unitConversion;
    private final MustacheRenderer<Map<String, String>> conversionRenderer;
    private final MustacheRenderer<Map<String, String>> conversionRendererGmi;

    @Inject
    public ConvertCommand(UnitConversion unitConversion, RendererFactory rendererFactory) throws IOException {
        this.unitConversion = unitConversion;

        conversionRenderer = rendererFactory.renderer("edge/conversion-results");
        conversionRendererGmi  = rendererFactory.renderer("edge/conversion-results-gmi");

    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {
        var conversion = unitConversion.tryConversion(ctx, query);
        if (conversion.isEmpty()) {
            return Optional.empty();
        }

        if (parameters.responseType() == ResponseType.GEMINI) {
            return Optional.of(conversionRendererGmi.render(Map.of("query", query, "result", conversion.get())));
        } else {
            return Optional.of(conversionRenderer.render(Map.of("query", query, "result", conversion.get(), "profile", parameters.profileStr())));
        }
    }
}
