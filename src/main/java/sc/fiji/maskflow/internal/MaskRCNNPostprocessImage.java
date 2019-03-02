
package sc.fiji.maskflow.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.io.location.Location;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.tensorflow.Session.Runner;
import org.tensorflow.Tensor;

@Plugin(type = Command.class, headless = true)
public class MaskRCNNPostprocessImage extends AbstractPredictor implements Command {

	private static final String MODEL_FILENAME = "postprocessing.pb";

	// Specific parameters.
	private static final Map<String, Object> DEFAULT_INPUT_NODES = new HashMap<String, Object>() {

		private static final long serialVersionUID = 1L;
		{
			put("detections", null);
			put("mrcnn_mask", null);
			put("original_image_shape", null);
			put("image_shape", null);
			put("window", null);
		}
	};

	private static final List<String> OUTPUT_NODE_NAMES = Arrays.asList("rois", "class_ids", "scores",
		"masks");

	@Parameter
	private Location modelLocation;

	@Parameter
	private String modelName;

	@Parameter
	private Tensor<?> detections;

	@Parameter
	private Tensor<?> mrcnnMask;

	@Parameter
	private Tensor<?> originalImageShape;

	@Parameter
	private Tensor<?> imageShape;

	@Parameter
	private Tensor<?> window;

	@Parameter(required = false)
	private boolean clearModel = false;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> rois;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> class_ids;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> scores;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> masks;

	@Override
	public void run() {

		this.loadModel(modelLocation, modelName, MODEL_FILENAME);

		// Get input nodes as tensor.
		Map<String, Object> inputNodes = new HashMap<>(DEFAULT_INPUT_NODES);

		inputNodes.put("detections", detections);
		inputNodes.put("mrcnn_mask", mrcnnMask);
		inputNodes.put("original_image_shape", originalImageShape);
		inputNodes.put("image_shape", imageShape);
		inputNodes.put("window", window);

		// Setup the runner with input and output nodes.
		Runner runner = this.session.runner();
		for (Map.Entry<String, Object> entry : inputNodes.entrySet()) {
			runner = runner.feed(entry.getKey(), (Tensor<?>) entry.getValue());
		}

		for (String outputName : OUTPUT_NODE_NAMES) {
			runner = runner.fetch(outputName);
		}

		// Run the model
		final List<Tensor<?>> outputsList = runner.run();

		// Save results in a dict
		rois = outputsList.get(0);
		class_ids = outputsList.get(1);
		scores = outputsList.get(2);
		masks = outputsList.get(3);

		log.debug("rois : " + rois);
		log.debug("class_ids : " + class_ids);
		log.debug("scores : " + scores);
		log.debug("masks : " + masks);

		if (clearModel) {
			this.clear();
		}
	}

}
