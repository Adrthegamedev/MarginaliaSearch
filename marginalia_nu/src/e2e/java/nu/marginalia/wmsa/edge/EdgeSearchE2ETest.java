package nu.marginalia.wmsa.edge;


import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.edge.crawling.CrawlJobExtractorMain;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openzim.ZIMTypes.ZIMFile;
import org.openzim.ZIMTypes.ZIMReader;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.*;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static nu.marginalia.wmsa.configuration.ServiceDescriptor.*;
import static org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode.RECORD_ALL;

@Tag("e2e")
@Testcontainers
public class EdgeSearchE2ETest {
    Network network = Network.newNetwork();

    @Container
    public GenericContainer<?> mariaDB = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withInitScript("sql/edge-crawler-cache.sql")
            .withNetwork(network)
            .withNetworkAliases("mariadb");

    @Container
    public GenericContainer<?> searchContainer =  forService(EDGE_SEARCH);
    @Container
    public GenericContainer<?> assistantContainer =  forService(EDGE_ASSISTANT);
    @Container
    public GenericContainer<?> indexContainer =  forService(EDGE_INDEX);

    @Container
    public NginxContainer<?> mockWikipedia = new NginxContainer<>("nginx:stable")
            .dependsOn(searchContainer)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("wikipedia")))
            .withFileSystemBind(getWikipediaFiles(), "/usr/share/nginx/html/", BindMode.READ_ONLY)
            .withNetwork(network)
            .withNetworkAliases("wikipedia");


    @Container
    public BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>()
            .withNetwork(network)
            .withCapabilities(new ChromeOptions());

    @Container
    public GenericContainer<?> crawlerContainer = new GenericContainer<>("openjdk:17-alpine")
                .dependsOn(mockWikipedia)
                .dependsOn(indexContainer)
                .withNetwork(network)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("crawler")))
                .withFileSystemBind(modelsPath(), "/var/lib/wmsa/model", BindMode.READ_ONLY)
                .withCopyFileToContainer(jarFile(), "/WMSA.jar")
                .withCopyFileToContainer(MountableFile.forClasspathResource("crawl.sh"), "/crawl.sh")
                .withFileSystemBind(getCrawlPath().toString(), "/crawl/", BindMode.READ_WRITE)
                .withCommand("sh", "crawl.sh")
                .waitingFor(Wait.forLogMessage(".*ALL DONE.*", 1).withStartupTimeout(Duration.ofMinutes(10)));

    @Container
    public NginxContainer<?> proxyNginx = new NginxContainer<>("nginx:stable")
            .dependsOn(searchContainer)
            .dependsOn(crawlerContainer)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("nginx")))
            .withCopyFileToContainer(MountableFile.forClasspathResource("nginx/search.conf"), "/etc/nginx/conf.d/default.conf")
            .withNetwork(network)
            .withNetworkAliases("proxyNginx");
    ;
    public GenericContainer<?> forService(ServiceDescriptor service) {
        return new GenericContainer<>("openjdk:17-alpine")
                .dependsOn(mariaDB)
                .withCopyFileToContainer(jarFile(), "/WMSA.jar")
                .withCopyFileToContainer(MountableFile.forClasspathResource("init.sh"), "/init.sh")
                .withExposedPorts(service.port)
                .withFileSystemBind(modelsPath(), "/var/lib/wmsa/model", BindMode.READ_ONLY)
                .withNetwork(network)
                .withNetworkAliases(service.name)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(service.name)))
                .withCommand("sh", "init.sh", service.name)
                .waitingFor(Wait.forHttp("/internal/ping")
                        .forPort(service.port)
                        .withReadTimeout(Duration.ofSeconds(15)))
        ;
    }

    public static MountableFile jarFile() {
        Path cwd = Path.of(System.getProperty("user.dir"));

        cwd = cwd.resolve("..");
        var jarFile =  cwd.resolve("build/libs/wmsa-SNAPSHOT-all.jar");
        if (!Files.exists(jarFile)) {
            System.err.println("Could not find jarFile " + jarFile);
            throw new RuntimeException();
        }
        else {
            System.out.println("jar file = " + jarFile);
        }
        return MountableFile.forHostPath(jarFile);
    }

    public static String modelsPath() {
        Path modelsPath = Path.of(System.getProperty("user.dir")).resolve("data/models");
        if (!Files.isDirectory(modelsPath)) {
            System.err.println("Could not find models, looked in " + modelsPath.toAbsolutePath());
            throw new RuntimeException();
        }
        return modelsPath.toString();
    }

    private Path getCrawlPath() {
        return Path.of(System.getProperty("user.dir")).resolve("build/tmp/crawl");
    }

    private String getWikipediaFiles() {
        Path wikipediaFiles = Path.of(System.getProperty("user.dir")).resolve("build/tmp/wikipedia");
        Path crawlFiles = getCrawlPath();
        Path zimFile = Path.of(System.getProperty("user.dir")).resolve("data/test/wikipedia_en_100_nopic.zim");


        List<String> urls = new ArrayList<>();
        try {
            Files.deleteIfExists(wikipediaFiles);
            Files.createDirectories(wikipediaFiles);
            Files.createDirectories(crawlFiles);

            Files.writeString(crawlFiles.resolve("crawl.plan"), """
                    jobSpec: "/crawl/crawl.spec"
                    crawl:
                        dir: "/crawl/crawl"
                        logName: "crawl.log"
                    process:
                        dir: "/crawl/process"
                        logName: "process.log"
                    """);

            Files.createDirectories(crawlFiles.resolve("crawl"));
            Files.createDirectories(crawlFiles.resolve("process"));
            Files.deleteIfExists(crawlFiles.resolve("process").resolve("process.log"));
            Files.deleteIfExists(crawlFiles.resolve("crawl").resolve("crawl.log"));

            var zr = new ZIMReader(new ZIMFile(zimFile.toString()));
            zr.forEachArticles((url, art) -> {
                urls.add("http://wikipedia/" + url + ".html");

                if (art != null) {
                    try {
                        var doc = Jsoup.parse(art);
                        doc.getElementsByTag("script").remove();
                        Files.writeString(wikipediaFiles.resolve(url+".html"), doc.html());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, pred -> true);
            urls.forEach(System.out::println);
            Files.writeString(wikipediaFiles.resolve("index.html"), "<html/>");
            CrawlJobExtractorMain.writeSpec(crawlFiles.resolve("crawl.spec"), "wikipedia", urls);
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        return wikipediaFiles.toString();
    }

    @Test
    public void run() {
        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/");
        System.out.println(driver.getTitle());
        System.out.println(driver.findElement(new By.ByXPath("//*")).getAttribute("outerHTML"));

        driver.get("http://proxyNginx/search?query=bird&profile=corpo");
        System.out.println(driver.getTitle());
        System.out.println(driver.findElement(new By.ByXPath("//*")).getAttribute("outerHTML"));
    }
}
