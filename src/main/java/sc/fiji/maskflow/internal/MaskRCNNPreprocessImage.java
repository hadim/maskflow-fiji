
package sc.fiji.maskflow.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ops.OpService;
import net.imagej.tensorflow.Tensors;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.io.location.Location;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.tensorflow.Session.Runner;
import org.tensorflow.Tensor;
import org.yaml.snakeyaml.Yaml;

import sc.fiji.maskflow.CustomDownloadService;
import sc.fiji.maskflow.utils.ArrayUtils;

@Plugin(type = Command.class, headless = true)
public class MaskRCNNPreprocessImage extends AbstractPredictor implements Command {

	private static final String MODEL_FILENAME = "preprocessing_graph.pb";

	private static final List<String> OUTPUT_NODE_NAMES = Arrays.asList("molded_image",
		"image_metadata", "window", "anchors");

	@Parameter
	private Location modelLocation;

	@Parameter
	private String modelName;

	@Parameter
	private Dataset inputDataset;

	@Parameter(required = false)
	private boolean clearModel = false;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> moldedImage;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> imageMetadata;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> windows;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> anchors;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> originalImageShape;

	@Parameter(type = ItemIO.OUTPUT)
	private Tensor<?> imageShape;

	@Parameter(type = ItemIO.OUTPUT)
	private List<String> classLabels;

	@Parameter
	private OpService op;

	@Parameter
	private DatasetService ds;

	@Parameter
	private CustomDownloadService cds;

	private Tensor<Float> inputTensorImage = null;

	@Override
	public void run() {

		this.loadModel(modelLocation, modelName, MODEL_FILENAME);

		// Get input nodes as tensor.
		Map<String, Tensor<?>> inputNodes = this.preprocessInputs();

		// Setup the runner with input and output nodes.
		Runner runner = this.session.runner();
		for (Map.Entry<String, Tensor<?>> entry : inputNodes.entrySet()) {
			runner = runner.feed(entry.getKey(), entry.getValue());
		}

		for (String outputName : OUTPUT_NODE_NAMES) {
			runner = runner.fetch(outputName);
		}

		// Run the model
		final List<Tensor<?>> outputsList = runner.run();

		// Save results
		moldedImage = outputsList.get(0);
		imageMetadata = outputsList.get(1);
		windows = outputsList.get(2);
		anchors = outputsList.get(3);

		// Write image shape before and after processing for later reuse.
		Tensor<?> originalImage = (Tensor<?>) inputNodes.get("input_image");
		long[] originalImageShapeArray = Arrays.copyOf(originalImage.shape(), 3);
		originalImageShapeArray[2] = 1;
		originalImageShape = org.tensorflow.Tensors.create(originalImageShapeArray);
		imageShape = org.tensorflow.Tensors.create(moldedImage.shape());

		log.debug("moldedImage : " + moldedImage);
		log.debug("imageMetadata : " + imageMetadata);
		log.debug("windows : " + windows);
		log.debug("anchors : " + anchors);
		log.debug("originalImageShape : " + originalImageShape);
		log.debug("imageShape : " + imageShape);

		if (clearModel) {
			this.clear();
		}
	}

	private Map<String, Tensor<?>> preprocessInputs() {

		// Load parameters from YAML file
		try {
			File parametersFile = cds.loadFile(modelLocation, modelName, "parameters.yml");

			InputStream input = new FileInputStream(parametersFile);
			Yaml yaml = new Yaml();
			Map data = (Map) yaml.load(input);

			this.classLabels = (List<String>) data.get("class_names");

			// Compute input values
			Map<String, Tensor<?>> inputNodes = new HashMap<>();

			this.inputTensorImage = Tensors.tensorFloat((RandomAccessibleInterval<FloatType>) op.run(
				"convert.float32", inputDataset.getImgPlus()));
			inputNodes.put("input_image", this.inputTensorImage);

			inputNodes.put("original_image_height", org.tensorflow.Tensors.create(
				((Long) this.inputTensorImage.shape()[0]).intValue()));
			inputNodes.put("original_image_width", org.tensorflow.Tensors.create(
				((Long) this.inputTensorImage.shape()[1]).intValue()));

			int[] class_ids = ArrayUtils.listDoubleToIntArray((List) data.get("class_ids"));
			inputNodes.put("class_ids", org.tensorflow.Tensors.create(class_ids));

			inputNodes.put("image_min_dimension", org.tensorflow.Tensors.create((int) data.get(
				"image_min_dimension")));
			inputNodes.put("image_max_dimension", org.tensorflow.Tensors.create((int) data.get(
				"image_max_dimension")));

			inputNodes.put("minimum_scale", org.tensorflow.Tensors.create(((Double) data.get(
				"minimum_scale")).floatValue()));

			float[] mean_pixels = ArrayUtils.listDoubleToFloatArray((List) data.get("mean_pixels"));
			inputNodes.put("mean_pixels", org.tensorflow.Tensors.create(mean_pixels));

			int[] backbone_strides = ArrayUtils.listIntegerToIntArray((List) data.get(
				"backbone_strides"));
			inputNodes.put("backbone_strides", org.tensorflow.Tensors.create(backbone_strides));

			int[] rpn_anchor_scales = ArrayUtils.listIntegerToIntArray((List) data.get(
				"rpn_anchor_scales"));
			inputNodes.put("rpn_anchor_scales", org.tensorflow.Tensors.create(rpn_anchor_scales));

			float[] rpn_anchor_ratios = ArrayUtils.listDoubleToFloatArray((List) data.get(
				"rpn_anchor_ratios"));
			inputNodes.put("rpn_anchor_ratios", org.tensorflow.Tensors.create(rpn_anchor_ratios));

			inputNodes.put("rpn_anchor_stride", org.tensorflow.Tensors.create((int) data.get(
				"rpn_anchor_stride")));

			return inputNodes;

		}
		catch (IOException e) {
			log.error("Can't read parameters.yml in the ZIP model file: " + e);
			return null;
		}

	}

}
