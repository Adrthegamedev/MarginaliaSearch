package nu.marginalia.wmsa.configuration;

import nu.marginalia.wmsa.auth.AuthMain;
import nu.marginalia.wmsa.auth.api.ApiMain;
import nu.marginalia.wmsa.configuration.command.Command;
import nu.marginalia.wmsa.configuration.command.ListCommand;
import nu.marginalia.wmsa.configuration.command.StartCommand;
import nu.marginalia.wmsa.configuration.command.VersionCommand;
import nu.marginalia.wmsa.data_store.DataStoreMain;
import nu.marginalia.wmsa.edge.archive.EdgeArchiveMain;
import nu.marginalia.wmsa.edge.assistant.EdgeAssistantMain;
import nu.marginalia.wmsa.edge.crawler.EdgeCrawlerMain;
import nu.marginalia.wmsa.edge.dating.DatingMain;
import nu.marginalia.wmsa.edge.director.EdgeDirectorMain;
import nu.marginalia.wmsa.edge.index.EdgeIndexMain;
import nu.marginalia.wmsa.edge.search.EdgeSearchMain;
import nu.marginalia.wmsa.memex.MemexMain;
import nu.marginalia.wmsa.podcasts.PodcastScraperMain;
import nu.marginalia.wmsa.renderer.RendererMain;
import nu.marginalia.wmsa.resource_store.ResourceStoreMain;
import nu.marginalia.wmsa.smhi.scraper.SmhiScraperMain;
import org.apache.logging.log4j.core.lookup.MainMapLookup;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum ServiceDescriptor {
    RESOURCE_STORE("resource-store", 5000, ResourceStoreMain.class),
    DATA_STORE("data-store", 5001, DataStoreMain.class),
    RENDERER("renderer", 5002, RendererMain.class),
    AUTH("auth", 5003, AuthMain.class),
    API("api", 5004, ApiMain.class),

    SMHI_SCRAPER("smhi-scraper",5012, SmhiScraperMain.class),
    PODCST_SCRAPER("podcast-scraper", 5013, PodcastScraperMain.class),

    EDGE_CRAWLER("edge-crawler", 5020, EdgeCrawlerMain.class),
    EDGE_INDEX("edge-index", 5021, EdgeIndexMain.class),
    EDGE_DIRECTOR("edge-director", 5022, EdgeDirectorMain.class),
    EDGE_SEARCH("edge-search", 5023, EdgeSearchMain.class),
    EDGE_ARCHIVE("edge-archive", 5024, EdgeArchiveMain.class),
    EDGE_ASSISTANT("edge-assistant", 5025, EdgeAssistantMain.class),

    EDGE_MEMEX("memex", 5030, MemexMain.class),

    DATING("dating", 5070, DatingMain.class),

    TEST_1("test-1", 0, null),
    TEST_2("test-2", 0, null);

    public static ServiceDescriptor byName(String name) {
        for (var v : values()) {
            if (v.name.equals(name)) {
                return v;
            }
        }
        throw new IllegalArgumentException(name);
    }
    public final String name;
    public final Class<?> mainClass;
    public final int port;

    ServiceDescriptor(String name, int port, Class<?> mainClass) {
        this.name = name;
        this.port = port;
        this.mainClass = mainClass;
    }

    public String toString() {
        return name;
    }

    public String describeService() {
        return String.format("%s %s", name, mainClass.getName());
    }

    public static void main(String... args) {

        MainMapLookup.setMainArguments(args);
        Map<String, Command> functions = Stream.of(new ListCommand(),
                new StartCommand(),
                new VersionCommand()
        ).collect(Collectors.toMap(c -> c.name, c -> c));

        if(args.length > 0) {
            functions.getOrDefault(args[0], new Command("") {
                @Override
                public void execute(String... args) {
                    System.err.println("Unknown command");
                    System.exit(1);
                }
            }).execute(args);
        }
        else {
            System.err.println("Usage: " + String.join("|", functions.keySet()));
            System.exit(1);
        }
    }

}
