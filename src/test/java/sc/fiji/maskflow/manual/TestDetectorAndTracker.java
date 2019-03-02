
package sc.fiji.maskflow.manual;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.scijava.command.CommandModule;
import org.scijava.table.GenericTable;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import sc.fiji.maskflow.ObjectsDetector;
import sc.fiji.maskflow.ObjectsTracker;

public class TestDetectorAndTracker {

	public static void main(String[] args) throws IOException {

		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		String modelPath = "/home/hadim/Drive/Data/Neural_Network/Maskflow/Microtubule/SavedModel/";
		String model = modelPath + "microtubule-v0.1.zip";

		// Open an image and display it.
		String basePath = "/home/hadim/Documents/Code/Postdoc/ij/testdata/";

		String imagePath = basePath + "single-256x256.tif";
		imagePath = basePath + "test-tracking-2-frames.tif";
		imagePath = basePath + "seed-small-10-frames.tif";
		// imagePath = basePath + "Cell_Colony-1.tif";
		// imagePath = basePath + "FakeTracks.tif";
		// imagePath = basePath + "Cell_Colony.tif";
		// imagePath = basePath + "Spindle-1-Frame.tif";
		// imagePath = basePath + "Spindle-1-Frame-Small.tif";

		final Object dataset = ij.io().open(imagePath);
		ij.ui().show(dataset);

		try {

			// Detect objects
			Map<String, Object> inputs = new HashMap<>();
			inputs.put("model", model);
			inputs.put("modelName", null);
			inputs.put("dataset", dataset);
			inputs.put("fillROIManager", true);
			CommandModule module = ij.command().run(ObjectsDetector.class, true, inputs).get();

			Dataset masks = (Dataset) module.getOutput("masks");
			GenericTable table = (GenericTable) module.getOutput("table");

			// Track objects
			inputs = new HashMap<>();
			inputs.put("masks", masks);
			inputs.put("table", table);
			inputs.put("linkingMaxDistance", 10.0);
			inputs.put("gapClosingMaxDistance", 10.0);
			inputs.put("maxFrameGap", 5);
			inputs.put("fillROIManager", true);
			module = ij.command().run(ObjectsTracker.class, true, inputs).get();
		}
		catch (InterruptedException | ExecutionException exc) {
			exc.printStackTrace();
		}

	}
}
