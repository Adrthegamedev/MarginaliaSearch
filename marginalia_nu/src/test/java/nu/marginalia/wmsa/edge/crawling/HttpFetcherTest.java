package nu.marginalia.wmsa.edge.crawling;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.crawling.retreival.HttpFetcher;
import nu.marginalia.wmsa.edge.crawling.retreival.HttpRedirectResolver;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

class HttpFetcherTest {

    @SneakyThrows
    @Test
    void testUrlPattern() {
        var fetcher = new HttpFetcher("nu.marginalia.edge-crawler");

        Assertions.assertFalse(fetcher.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.txt")));
        Assertions.assertTrue(fetcher.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.bin")));
        Assertions.assertTrue(fetcher.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.tar.gz")));
        Assertions.assertFalse(fetcher.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.htm")));
        Assertions.assertFalse(fetcher.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.html")));
        Assertions.assertFalse(fetcher.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log")));
        Assertions.assertFalse(fetcher.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.php?id=1")));
    }

    @Test
    void fetchUTF8() throws URISyntaxException {
        var fetcher = new HttpFetcher("nu.marginalia.edge-crawler");
        var str = fetcher.fetchContent(new EdgeUrl("https://www.marginalia.nu"));
        System.out.println(str.contentType);
    }

    @Test
    void fetchText() throws URISyntaxException {
        var fetcher = new HttpFetcher("nu.marginalia.edge-crawler");
        var str = fetcher.fetchContent(new EdgeUrl("https://www.marginalia.nu/robots.txt"));
        System.out.println(str);
    }

    @Test
    void resolveRedirect() throws URISyntaxException {
        var fetcher = new HttpRedirectResolver("nu.marginalia.edge-crawler");
        var str = fetcher.probe(new EdgeUrl("https://www.marginalia.nu/robots.txt"));
        System.out.println(str);
    }

    @Test
    void resolveRedirectRitEdu() throws URISyntaxException {
        var fetcher = new HttpRedirectResolver("nu.marginalia.edge-crawler");
        var str = fetcher.probe(new EdgeUrl("http://www.rit.edu/cla/philosophy/Suits.html")).blockingFirst();
        System.out.println(str);
    }

    @Test
    void resolveRedirect2() throws URISyntaxException {
        var fetcher = new HttpRedirectResolver("nu.marginalia.edge-crawler");
        var str = fetcher.probe(new EdgeUrl("https://www.marginalia.nu/robots.txt")).blockingFirst();
        System.out.println(str);
    }

    @Test
    void resolveRedirect3() throws URISyntaxException {
        var fetcher = new HttpRedirectResolver("nu.marginalia.edge-crawler");
        var str = fetcher.probe(new EdgeUrl("https://www.marginalia.nu/robots.txt"));
        System.out.println(str);
    }


    @Test
    void resolveRedirect4() throws URISyntaxException {
        var fetcher = new HttpRedirectResolver("nu.marginalia.edge-crawler");
        var str = fetcher.probe(new EdgeUrl("https://www.marginalia.nu/robots.txt"));
        System.out.println(str);
    }
}