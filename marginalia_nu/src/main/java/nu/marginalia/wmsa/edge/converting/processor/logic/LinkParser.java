package nu.marginalia.wmsa.edge.converting.processor.logic;

import com.google.common.base.CharMatcher;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.jetbrains.annotations.Contract;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class LinkParser {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<String> blockPrefixList = List.of(
            "mailto:", "javascript:", "tel:", "itpc:", "#", "file:");
    private final List<String> blockSuffixList = List.of(
            ".pdf", ".mp3", ".wmv", ".avi", ".zip", ".7z",
            ".bin", ".exe", ".tar.gz", ".tar.bz2", ".xml", ".swf",
            ".wav", ".ogg", ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".webm", ".bmp", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx",
            ".gz", ".asc", ".md5", ".asf", ".mov", ".sig", ".pub", ".iso");

    @Contract(pure=true)
    public Optional<EdgeUrl> parseLink(EdgeUrl baseUrl, Element l) {
        return Optional.of(l)
                .filter(this::shouldIndexLink)
                .map(this::getUrl)
                .map(link -> resolveUrl(baseUrl, link))
                .flatMap(this::createURI)
                .map(URI::normalize)
                .map(this::renormalize)
                .flatMap(this::createEdgeUrl);
    }

    private Optional<URI> createURI(String s) {
        try {
            return Optional.of(new URI(s));
        }
        catch (URISyntaxException e) {
            logger.debug("Bad URI {}", s);
            return Optional.empty();
        }
    }

    private Optional<EdgeUrl> createEdgeUrl(URI uri) {
        try {
            return Optional.of(new EdgeUrl(uri));
        }
        catch (Exception ex) {
            logger.debug("Bad URI {}", uri);
            return Optional.empty();
        }
    }

    @Contract(pure=true)
    public Optional<EdgeUrl> parseLink(EdgeUrl baseUrl, String str) {
        return Optional.of(str)
                .map(link -> resolveUrl(baseUrl, link))
                .flatMap(this::createURI)
                .map(URI::normalize)
                .map(this::renormalize)
                .flatMap(this::createEdgeUrl);
    }

    @Contract(pure=true)
    public Optional<EdgeUrl> parseFrame(EdgeUrl baseUrl, Element frame) {
        return Optional.of(frame)
                .map(l -> l.attr("src"))
                .map(link -> resolveUrl(baseUrl, link))
                .flatMap(this::createURI)
                .map(URI::normalize)
                .map(this::renormalize)
                .flatMap(this::createEdgeUrl);
    }

    @SneakyThrows
    private URI renormalize(URI uri) {
        if (uri.getPath() == null) {
            return renormalize(new URI(uri.getScheme(), uri.getHost(), "/", uri.getFragment()));
        }
        if (uri.getPath().startsWith("/../")) {
            return renormalize(new URI(uri.getScheme(), uri.getHost(), uri.getPath().substring(3), uri.getFragment()));
        }
        return uri;
    }

    private String getUrl(Element element) {
        var url = CharMatcher.noneOf(" \r\n\t").retainFrom(element.attr("href"));

        int anchorIndex = url.indexOf('#');
        if (anchorIndex > 0) {
            return url.substring(0, anchorIndex);
        }
        return url;
    }

    private static final Pattern paramRegex = Pattern.compile("\\?.*$");
    @SneakyThrows
    private String resolveUrl(EdgeUrl baseUrl, String s) {
        s = paramRegex.matcher(s).replaceAll("");

        // url looks like http://www.marginalia.nu/
        if (isAbsoluteDomain(s)) {
            return s;
        }

        // url looks like /my-page
        if (s.startsWith("/")) {
            return baseUrl.sibling(s).toString();
        }

        return baseUrl.sibling(relativeNavigation(baseUrl) + s.replaceAll(" ", "%20")).toString();
    }

    // for a relative url that looks like /foo or /foo/bar; return / or /foo
    private String relativeNavigation(EdgeUrl url) {

        var lastSlash = url.path.lastIndexOf("/");
        if (lastSlash < 0) {
            return "/";
        }
        return url.path.substring(0, lastSlash+1);
    }

    private boolean isAbsoluteDomain(String s) {
        return  s.matches("^[a-zA-Z]+:.*$");
    }

    private boolean shouldIndexLink(Element link) {
        return isUrlRelevant(link.attr("href"))
                && isRelRelevant(link.attr("rel"));

    }

    private boolean isRelRelevant(String rel) {
        if (null == rel) {
            return true;
        }
        return switch (rel) {
            case "noindex" -> false;
            default -> true;
        };
    }

    private boolean isUrlRelevant(String href) {
        if (null == href || "".equals(href)) {
            return false;
        }
        if (blockPrefixList.stream().anyMatch(href::startsWith)) {
            return false;
        }
        if (blockSuffixList.stream().anyMatch(href::endsWith)) {
            return false;
        }
        if (href.length() > 128) {
            return false;
        }
        return true;
    }
}
