package nu.marginalia.wmsa.edge.index;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class EdgeIndexModule extends AbstractModule {

    public void configure() {
        bind(Long.class).annotatedWith(Names.named("edge-dictionary-hash-map-size")).toInstance(1L << 31);
    }

}
