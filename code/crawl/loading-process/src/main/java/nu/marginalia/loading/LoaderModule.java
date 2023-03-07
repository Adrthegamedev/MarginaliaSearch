package nu.marginalia.loading;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.LanguageModels;
import nu.marginalia.WmsaHome;
import nu.marginalia.crawling.common.plan.EdgeCrawlPlan;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.SearchServiceDescriptors;
import nu.marginalia.service.descriptor.ServiceDescriptors;

import java.nio.file.Path;

public class LoaderModule extends AbstractModule {

    private final EdgeCrawlPlan plan;

    public LoaderModule(EdgeCrawlPlan plan) {
        this.plan = plan;
    }

    public void configure() {
        bind(EdgeCrawlPlan.class).toInstance(plan);

        bind(ServiceDescriptors.class).toInstance(SearchServiceDescriptors.descriptors);

        bind(Gson.class).toInstance(createGson());

        bind(Path.class).annotatedWith(Names.named("local-index-path")).toInstance(Path.of(System.getProperty("local-index-path", "/vol")));
        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
    }

    private Gson createGson() {
        return GsonFactory.get();
    }

}
