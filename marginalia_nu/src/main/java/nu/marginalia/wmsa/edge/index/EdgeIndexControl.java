package nu.marginalia.wmsa.edge.index;


import com.google.inject.Inject;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.conversion.ConversionUnnecessaryException;

import java.io.IOException;


public class EdgeIndexControl {

    private final IndexServicesFactory servicesFactory;

    @Inject
    public EdgeIndexControl(IndexServicesFactory servicesFactory) {
        this.servicesFactory = servicesFactory;
    }

    public void regenerateIndex(int id) {
        System.runFinalization();
        System.gc();

        for (IndexBlock block : IndexBlock.values()) {
            try {
                servicesFactory.getIndexConverter(id, block);

                System.runFinalization();
                System.gc();
            }
            catch (ConversionUnnecessaryException unnecessary) {
                // swallow quietly
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.runFinalization();
        System.gc();
    }

    public long wordCount(int id) {
        return servicesFactory.wordCount(id);
    }

    public void switchIndexFiles(int id) throws Exception {
        servicesFactory.switchFilesJob(id).call();
    }
}
