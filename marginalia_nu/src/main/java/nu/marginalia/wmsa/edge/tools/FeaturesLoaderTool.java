package nu.marginalia.wmsa.edge.tools;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.converting.processor.logic.HtmlFeature;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentsMetadata;
import nu.marginalia.wmsa.edge.model.id.EdgeId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FeaturesLoaderTool {
    public static void main(String... args) {

        HtmlFeature feature = HtmlFeature.valueOf(args[0]);
        Path file = Path.of(args[1]);

        try (EdgeIndexClient client = new EdgeIndexClient();
             HikariDataSource ds = new DatabaseModule().provideConnection();
             Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE EC_PAGE_DATA SET FEATURES = FEATURES | ? WHERE ID=?");
             var linesStream = Files.lines(file)) {

            var urls = getUrls(ds);
            linesStream
                    .map(urls::get)
                    .filter(Objects::nonNull)
                    .forEach(id -> {
                        int urlId = (int)(id & 0xFFFF_FFFFL);
                        int domainId = (int)(id >>> 32L);

                        try {
                            ps.setInt(2, urlId);
                            ps.setInt(1, feature.getFeatureBit());
                            ps.executeUpdate();
                        }
                        catch (SQLException ex) {
                            throw new RuntimeException(ex);
                        }

                        client.putWords(Context.internal(), new EdgeId<>(domainId), new EdgeId<>(urlId),
                                new EdgePageDocumentsMetadata(EdgePageDocumentsMetadata.defaultValue()),
                                new DocumentKeywords(new String[] { feature.getKeyword() }, new long[] { 0 })
                                , 0);
                    });

        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Long> getUrls(HikariDataSource ds) {

        Map<String, Long> urls = new HashMap<>(100_000);

        try (var conn = ds.getConnection();
             var stmt = conn.createStatement())
        {
            var rsp = stmt.executeQuery("SELECT URL, ID, DOMAIN_ID FROM EC_URL_VIEW WHERE TITLE IS NOT NULL");

            while (rsp.next()) {
                long val = rsp.getInt(3);
                val = (val << 32L) | rsp.getInt(2);

                urls.put(rsp.getString(1), val);
            }

        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return urls;
    }
}
