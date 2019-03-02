
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

import sc.fiji.maskflow.utils.TensorUtils;

@Plugin(type = Command.class, headless = true)
public class MaskRCNNDetector extends AbstractPredictor implements Command {

	private static final String MODEL_FILENAME = "maskrcnn.pb";

	// Specific parameters.
	private static final Map<String, Object> DEFAULT_INPUT_NODES = new HashMap<String, Object>() {

		private static final long serialVersionUID = 1L;
		{
			put("input_image", null);
			put("input_image_meta", null);
			put("input_anchors", null);
		}
	};

	private static final List<String> OUTPUT_NODE_NAMES = Arrays.asList("output_detections",
		"output_mrcnn_class", "output_mrcnn_bbox", "output_mrcnn_mask", "output_rois");

	@Parameter
	private Location modelLocation;

	@Parameter
	private String modelName;

	@Parameter
	private Tensor<?> moldedImage;

	@Parameter
	private Tensor<?> imageMetadata;

	@Parameter
	private Tensor<?> anchors;

	@Parameter(required = false)
	private boolean clearModel = false;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> detections;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> mrcnn_class;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> mrcnn_bbox;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> mrcnn_mask;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> rois;

	@Override
	public void run() {

		this.loadModel(modelLocation, modelName, MODEL_FILENAME);

		// Get input nodes as tensor.
		Map<String, Object> inputNodes = new HashMap<>(DEFAULT_INPUT_NODES);

		moldedImage = TensorUtils.expandDimension(moldedImage, 0);
		inputNodes.put("input_image", moldedImage);

		imageMetadata = TensorUtils.expandDimension(imageMetadata, 0);
		inputNodes.put("input_image_meta", imageMetadata);

		anchors = TensorUtils.expandDimension(anchors, 0);
		inputNodes.put("input_anchors", anchors);

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
		detections = outputsList.get(0);
		mrcnn_class = outputsList.get(1);
		mrcnn_bbox = outputsList.get(2);
		mrcnn_mask = outputsList.get(3);
		rois = outputsList.get(4);

		log.debug("detections : " + detections);
		log.debug("mrcnn_class : " + mrcnn_class);
		log.debug("mrcnn_bbox : " + mrcnn_bbox);
		log.debug("mrcnn_mask : " + mrcnn_mask);
		log.debug("rois : " + rois);

		if (clearModel) {
			this.clear();
		}
	}
}
