package nu.marginalia;


import nu.marginalia.service.descriptor.HostsFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

public class WmsaHome {
    public static UserAgent getUserAgent() throws IOException {
        var uaPath = getHomePath().resolve("conf/user-agent");

        if (!Files.exists(uaPath)) {
            throw new FileNotFoundException("Could not find " + uaPath);
        }

        return new UserAgent(Files.readString(uaPath).trim());
    }


    public static Path getHomePath() {
        var retStr = Optional.ofNullable(System.getenv("WMSA_HOME")).orElseGet(WmsaHome::findDefaultHomePath);

        var ret = Path.of(retStr);
        if (!Files.isDirectory(ret)) {
            throw new IllegalStateException("Could not find WMSA_HOME, either set environment variable or ensure " + retStr + " exists");
        }
        return ret;
    }

    private static String findDefaultHomePath() {

        for (Path p = Paths.get("").toAbsolutePath();
             Files.exists(p);
             p = p.getParent())
        {
            if (Files.exists(p.resolve("run/env")) && Files.exists(p.resolve("run/setup.sh"))) {
                return p.resolve("run").toString();
            }
        }

        return "/var/lib/wmsa";
    }

    public static HostsFile getHostsFile() {
        Path hostsFile = getHomePath().resolve("conf/hosts");
        if (Files.isRegularFile(hostsFile)) {
            try {
                return new HostsFile(hostsFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load hosts file " + hostsFile, e);
            }
        }
        else {
            return new HostsFile();
        }
    }

    public static Path getAdsDefinition() {
        return getHomePath().resolve("data").resolve("adblock.txt");
    }

    public static Path getIPLocationDatabse() {
        return getHomePath().resolve("data").resolve("IP2LOCATION-LITE-DB1.CSV");
    }

    public static Path getDisk(String name) {
        var pathStr = getDiskProperties().getProperty(name);
        if (null == pathStr) {
            throw new RuntimeException("Disk " + name + " was not configured");
        }
        Path p = Path.of(pathStr);
        if (!Files.isDirectory(p)) {
            throw new RuntimeException("Disk " + name + " does not exist or is not a directory!");
        }
        return p;
    }

    public static Properties getDiskProperties() {
        Path settingsFile = getHomePath().resolve("conf/disks.properties");

        if (!Files.isRegularFile(settingsFile)) {
            throw new RuntimeException("Could not find disk settings " + settingsFile);
        }

        try (var is = Files.newInputStream(settingsFile)) {
            var props = new Properties();
            props.load(is);
            return props;
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static LanguageModels getLanguageModels() {
        final Path home = getHomePath();

        return new LanguageModels(
                home.resolve("model/ngrams.bin"),
                home.resolve("model/tfreq-new-algo3.bin"),
                home.resolve("model/opennlp-sentence.bin"),
                home.resolve("model/English.RDR"),
                home.resolve("model/English.DICT"),
                home.resolve("model/opennlp-tok.bin"));
    }

    private static final boolean debugMode = Boolean.getBoolean("wmsa-debug");
    public static boolean isDebug() {
        return debugMode;
    }
}
