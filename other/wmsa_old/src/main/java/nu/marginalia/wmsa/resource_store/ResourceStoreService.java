package nu.marginalia.wmsa.resource_store;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.SneakyThrows;
import nu.marginalia.client.Context;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.MetricsServer;
import nu.marginalia.service.server.Service;
import nu.marginalia.service.server.StaticResources;
import nu.marginalia.wmsa.resource_store.model.RenderedResource;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.resource.ClassPathResource;
import spark.staticfiles.MimeType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

public class ResourceStoreService extends Service {
    private final Gson gson = new Gson();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final long startTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

    private final ResourceEntityStore resourceStore;
    private StaticResources staticResources;

    @Inject
    public ResourceStoreService(@Named("service-host") String ip,
                                @Named("service-port") Integer port,
                                ResourceEntityStore resourceStore,
                                Initialization initialization,
                                MetricsServer metricsServer,
                                StaticResources staticResources
                                ) {
        super(ip, port, initialization, metricsServer);
        this.resourceStore = resourceStore;
        this.staticResources = staticResources;

        Schedulers.io().schedulePeriodicallyDirect(resourceStore::reapStaleResources,
                5, 5, TimeUnit.MINUTES);

        Spark.get("/public/*", this::getDefaultResource);

        Spark.get("/:domain/*", this::getResource);
        Spark.post("/:domain", this::storeResource);

    }

    private Object getDefaultResource(Request request, Response response) {
        String headerDomain = request.headers("X-Domain");

        if (headerDomain == null) {
            Spark.halt(404);
        }

        var splat = request.splat();
        var resource = splat.length == 0 ? "index.html" : splat[0];

        return getResource(request, response, headerDomain, resource);

    }

    private Object storeResource(Request request, Response response) {
        var domain = request.params("domain");
        var data = gson.fromJson(request.body(), RenderedResource.class);

        logger.info("storeResource({}/{}, {})", domain, data.filename, data.etag());

        resourceStore.putResource(domain, data.filename, data);

        Spark.halt(HttpStatus.SC_ACCEPTED);
        return null;
    }

    private Object getResource(Request request, Response response) {
        String headerDomain = request.headers("X-Domain");
        var domain = request.params("domain");

        if (headerDomain != null && !domain.equals(headerDomain)) {
            logger.warn("{} - domain mismatch: Header = {}, request = {}", Context.fromRequest(request), headerDomain, domain);
            Spark.halt(403);
        }

        var splat = request.splat();
        var resource = splat.length == 0 ? "index.html" : splat[0];

        return getResource(request, response, domain, resource);
    }

    private String getResource(Request request, Response response, String domain, String resource) {

        var data = resourceStore.getResource(domain, resource);

        if (data != null) {
            logger.info("getResource({}/{}, {})", domain, resource, data.etag());

            return serveDynamic(data, request, response);
        }
        else {
            logger.info("getResource({}/{}, static)", domain, resource);
            staticResources.serveStatic(domain, resource, request, response);
        }
        return "";
    }

    private String serveDynamic(RenderedResource data, Request request, Response response) {
        handleEtag(data, request, response);

        return data.data;
    }



    @SneakyThrows
    private void handleEtag(RenderedResource page, Request req, Response rsp) {
        rsp.header("Cache-Control", "private, must-revalidate");

        if (!page.filename.endsWith(".txt")) {
            rsp.type("text/html");
        }
        else {
            rsp.type(MimeType.fromResource(new ClassPathResource(page.filename)));
        }
        final String etag = page.etag();

        if (etag.equals(req.headers("If-None-Match"))) {
            Spark.halt(304);
        }

        rsp.header("ETag", etag);
    }

    @SneakyThrows
    private void handleEtagStatic(ClassPathResource resource, Request req, Response rsp) {
        rsp.header("Cache-Control", "public,max-age=3600");
        rsp.type(MimeType.fromResource(resource));

        final String etag = staticResourceEtag(resource.getFilename());

        if (etag.equals(req.headers("If-None-Match"))) {
            Spark.halt(304);
        }

        rsp.header("ETag", etag);
    }

    private String staticResourceEtag(String resource) {
        return "\"" + resource.hashCode() + "-" + startTime + "\"";
    }
}
