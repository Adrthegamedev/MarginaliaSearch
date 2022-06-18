package nu.marginalia.wmsa.edge.search.siteinfo;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.search.model.DomainInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/*
  TODO: This class needs to be refactored, a lot of
        these SQL queries are redundant and can be
        collapsed into one single query that fetches
        all the information
 */
@Singleton
public class DomainInformationService {

    private EdgeDataStoreDaoImpl dataStoreDao;
    private HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DomainInformationService(
            EdgeDataStoreDaoImpl dataStoreDao,
            HikariDataSource dataSource) {
        this.dataStoreDao = dataStoreDao;
        this.dataSource = dataSource;
    }


    public Optional<DomainInformation> domainInfo(String site) {

        EdgeId<EdgeDomain> domainId = getDomainFromPartial(site);
        if (domainId == null) {
            return Optional.empty();
        }
        EdgeDomain domain = dataStoreDao.getDomain(domainId);

        boolean blacklisted = isBlacklisted(domain);
        int pagesKnown = getPagesKnown(domainId);
        int pagesVisited = getPagesVisited(domainId);
        int pagesIndexed = getPagesIndexed(domainId);
        int incomingLinks = getIncomingLinks(domainId);
        int outboundLinks = getOutboundLinks(domainId);
        double rank = Math.round(10000.0*(1.0-getRank(domainId)))/100;
        EdgeDomainIndexingState state = getDomainState(domainId);
        List<EdgeDomain> linkingDomains = getLinkingDomains(domainId);

        return Optional.of(new DomainInformation(domain, blacklisted, pagesKnown, pagesVisited, pagesIndexed, incomingLinks, outboundLinks, rank, state, linkingDomains));
    }

    private EdgeId<EdgeDomain> getDomainFromPartial(String site) {
        try {
            return dataStoreDao.getDomainId(new EdgeDomain(site));
        }
        catch (Exception ex) {
            try {
                return dataStoreDao.getDomainId(new EdgeDomain(site));
            }
            catch (Exception ex2) {
                return null;
            }
        }

    }

    @SneakyThrows
    public boolean isBlacklisted(EdgeDomain domain) {

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN_BLACKLIST WHERE URL_DOMAIN=?")) {
                stmt.setString(1, domain.domain);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    @SneakyThrows
    public int getPagesKnown(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT KNOWN_URLS FROM DOMAIN_METADATA WHERE ID=?")) {
                stmt.setInt(1, domainId.id());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }

    @SneakyThrows
    public int getPagesVisited(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT VISITED_URLS FROM DOMAIN_METADATA WHERE ID=?")) {
                stmt.setInt(1, domainId.id());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }


    @SneakyThrows
    public int getPagesIndexed(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT GOOD_URLS FROM DOMAIN_METADATA WHERE ID=?")) {
                stmt.setInt(1, domainId.id());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }

    @SneakyThrows
    public int getIncomingLinks(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT COUNT(ID) FROM EC_DOMAIN_LINK WHERE DEST_DOMAIN_ID=?")) {
                stmt.setInt(1, domainId.id());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }
    @SneakyThrows
    public int getOutboundLinks(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT COUNT(ID) FROM EC_DOMAIN_LINK WHERE SOURCE_DOMAIN_ID=?")) {
                stmt.setInt(1, domainId.id());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }

    @SneakyThrows
    public double getDomainQuality(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT QUALITY FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, domainId.id());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getDouble(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return -5;
        }
    }

    public EdgeDomainIndexingState getDomainState(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT STATE FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, domainId.id());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return EdgeDomainIndexingState.valueOf(rsp.getString(1));
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return EdgeDomainIndexingState.ERROR;
    }

    public List<EdgeDomain> getLinkingDomains(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {
            List<EdgeDomain> results = new ArrayList<>(25);
            try (var stmt = connection.prepareStatement("SELECT SOURCE_DOMAIN FROM EC_RELATED_LINKS_VIEW WHERE DEST_DOMAIN_ID=? ORDER BY SOURCE_DOMAIN_ID LIMIT 25")) {
                stmt.setInt(1, domainId.id());
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    results.add(new EdgeDomain(rsp.getString(1)));
                }
                return results;
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return Collections.emptyList();
    }

    public double getRank(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT IFNULL(RANK, 1) FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, domainId.id());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getDouble(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return 1;
    }
}
