
package sc.fiji.maskflow.manual;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.table.GenericTable;

import org.scijava.command.CommandModule;

import sc.fiji.maskflow.ObjectsDetector;

public class TestDetector {

	public static void main(String[] args) throws IOException {

		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		String modelPath = "/home/hadim/Drive/Data/Neural_Network/Mask-RCNN/Microtubules/saved_model/";
		String model = modelPath + "tf_model_microtubule_coco_512.zip";

		// Open an image and display it.
		String basePath = "/home/hadim/Documents/Code/Postdoc/ij/testdata/";

		String imagePath = basePath + "single-256x256.tif";
		imagePath = basePath + "test-tracking-2-frames.tif";
		// imagePath = basePath + "seed-small-10-frames.tif";
		// imagePath = basePath + "Cell_Colony-1.tif";
		// imagePath = basePath + "FakeTracks.tif";
		// imagePath = basePath + "Cell_Colony.tif";
		// imagePath = basePath + "Spindle-1-Frame.tif";
		// imagePath = basePath + "Spindle-1-Frame-Small.tif";

		String maskPath = basePath + "Masks-of-seed-small-2-frames.tif";
		String tablePath = basePath + "Masks-of-seed-small-2-frames.csv";

		final Object dataset = ij.io().open(imagePath);
		ij.ui().show(dataset);

		// Set parameters
		Map<String, Object> inputs = new HashMap<>();
		inputs.put("model", model);
		inputs.put("modelName", null);
		inputs.put("dataset", dataset);
		inputs.put("fillROIManager", true);

		try {

			// Run command and get results
			CommandModule module = ij.command().run(ObjectsDetector.class, true, inputs).get();
			Dataset mask = (Dataset) module.getOutput("masks");
			GenericTable table = (GenericTable) module.getOutput("table");

			// Save results
			/*CommonsCSVTableIOPlugin tableIOPlugin = new CommonsCSVTableIOPlugin();
			tableIOPlugin.save(table, tablePath);
			ij.io().save(mask, maskPath);*/

		}
		catch (InterruptedException | ExecutionException exc) {
			exc.printStackTrace();
		}

	}
}
