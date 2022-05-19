package nu.marginalia.wmsa.podcasts;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

import java.io.IOException;

public class PodcastScraperMain extends MainClass {

    private final PodcastScraperService service;

    @Inject
    public PodcastScraperMain(PodcastScraperService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.PODCST_SCRAPER, args);

        Injector injector = Guice.createInjector(
                new ConfigurationModule());
        injector.getInstance(PodcastScraperMain.class);
        injector.getInstance(Initialization.class).setReady();
    }

}
