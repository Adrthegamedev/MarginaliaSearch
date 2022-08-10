package nu.marginalia.wmsa.edge.crawling;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.wmsa.configuration.UserAgent;
import nu.marginalia.wmsa.configuration.WmsaHome;
import nu.marginalia.wmsa.edge.crawling.model.CrawlingSpecification;
import nu.marginalia.wmsa.edge.crawling.retreival.CrawlerRetreiver;
import nu.marginalia.wmsa.edge.crawling.retreival.HttpFetcher;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;
import okhttp3.Dispatcher;
import okhttp3.internal.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.*;

public class CrawlerMain implements AutoCloseable {
    public static Gson gson = new GsonBuilder().create();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EdgeCrawlPlan plan;
    private final Path crawlDataDir;

    private final WorkLog workLog;

    private final Dispatcher dispatcher = new Dispatcher(new ThreadPoolExecutor(0, Integer.MAX_VALUE, 5, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Util.threadFactory("OkHttp Dispatcher", true)));

    private final UserAgent userAgent;

    public CrawlerMain(EdgeCrawlPlan plan) throws Exception {
        this.plan = plan;
        this.userAgent = WmsaHome.getUserAgent();

        workLog = plan.createCrawlWorkLog();
        crawlDataDir = plan.crawl.getDir();
    }

    public static void main(String... args) throws Exception {
        if (!AbortMonitor.getInstance().isAlive()) {
            System.err.println("Remove abort file first");
            return;
        }

        if (args.length != 1) {
            System.err.println("Arguments: crawl-plan.yaml");
            System.exit(0);
        }
        var plan = new CrawlPlanLoader().load(Path.of(args[0]));

        try (var crawler = new CrawlerMain(plan)) {
            crawler.run();
        }

        System.exit(0);
    }

    private void fetchDomain(CrawlingSpecification specification) {
        if (workLog.isJobFinished(specification.id))
            return;

        var fetcher = new HttpFetcher(userAgent.uaString(), dispatcher);

        try (var writer = new CrawledDomainWriter(crawlDataDir, specification.domain, specification.id)) {
            var retreiver = new CrawlerRetreiver(fetcher, specification, writer);

            int size = retreiver.fetch();

            workLog.setJobToFinished(specification.id, writer.getOutputFile().toString(), size);

            logger.info("Fetched {}", specification.domain);
        } catch (Exception e) {
            logger.error("Error fetching domain", e);
        }
    }

    public void run() throws InterruptedException {
        // First a validation run to ensure the file is all good to parse

        logger.info("Validating JSON");
        plan.forEachCrawlingSpecification(unused -> {});

        logger.info("Let's go");

        final int poolSize = 1024;

        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(10);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(poolSize/128, poolSize, 5, TimeUnit.MINUTES, queue); // maybe need to set -Xss for JVM to deal with this?

        AbortMonitor abortMonitor = AbortMonitor.getInstance();

        plan.forEachCrawlingSpecification(spec -> {
            if (abortMonitor.isAlive()) {
                pool.execute(() -> fetchDomain(spec));
            }
        });

        logger.info("Awaiting termination");

        pool.shutdown();

        while (!pool.awaitTermination(1, TimeUnit.SECONDS));

        logger.info("All finished");
    }

    public void close() throws Exception {
        workLog.close();
        dispatcher.executorService().shutdownNow();
    }

}
