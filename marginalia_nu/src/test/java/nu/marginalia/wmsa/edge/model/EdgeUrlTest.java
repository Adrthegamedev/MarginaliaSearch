package nu.marginalia.wmsa.edge.model;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

class EdgeUrlTest {

    @Test
    public void testHashCode() throws URISyntaxException {
        System.out.println(new EdgeUrl("https://memex.marginalia.nu").hashCode());
    }

    @Test
    void urlencodeFixer() throws URISyntaxException {
        System.out.println(EdgeUrl.urlencodeFixer("https://www.example.com/%-sign"));
        System.out.println(EdgeUrl.urlencodeFixer("https://www.example.com/%22-sign"));
        System.out.println(EdgeUrl.urlencodeFixer("https://www.example.com/\n \"huh\""));
    }

    @Test
    void testParms() throws URISyntaxException {
        System.out.println(new EdgeUrl("https://search.marginalia.nu/?id=123"));
        System.out.println(new EdgeUrl("https://search.marginalia.nu/?t=123"));
        System.out.println(new EdgeUrl("https://search.marginalia.nu/?v=123"));
        System.out.println(new EdgeUrl("https://search.marginalia.nu/?m=123"));
        System.out.println(new EdgeUrl("https://search.marginalia.nu/?follow=123"));
    }
}