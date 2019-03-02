
package sc.fiji.maskflow;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.CommandInfo;
import org.scijava.io.http.HTTPLocation;
import org.scijava.io.location.FileLocation;
import org.scijava.io.location.Location;
import org.scijava.log.LogService;
import org.scijava.module.Module;
import org.scijava.module.process.PreprocessorPlugin;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultGenericTable;
import org.scijava.table.GenericTable;
import org.tensorflow.Tensor;
import org.yaml.snakeyaml.Yaml;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.OpService;
import net.imagej.tensorflow.TensorFlowService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import sc.fiji.maskflow.internal.MaskRCNNDetector;
import sc.fiji.maskflow.internal.MaskRCNNPostprocessImage;
import sc.fiji.maskflow.internal.MaskRCNNPreprocessImage;
import sc.fiji.maskflow.utils.ROIUtils;

@Plugin(type = Command.class, menuPath = "Plugins>Maskflow>Detect Objects", headless = true)
public class ObjectsDetector implements Command {

	static private Map<String, String> AVAILABLE_MODELS = new HashMap<>();
	static {
		AVAILABLE_MODELS.put("Microtubule",
			"https://storage.googleapis.com/nn-models/microtubule-v0.1.zip");
	}

	@Parameter
	private ImageJ ij;

	@Parameter
	private LogService log;

	@Parameter
	private DatasetService ds;

	@Parameter
	private StatusService ss;

	@Parameter(visibility = ItemVisibility.MESSAGE, required = false)
	private final String header = "You can select the model from 2 different sources.";

	@Parameter(required = false, label = "Model Location (URL or filepath to a ZIP file)",
		description = "The location to the model as a ZIP file. It can be an URL or a filepath.")
	private String model = null;

	@Parameter(visibility = ItemVisibility.MESSAGE, required = false)
	private final String or1 = "or";

	@Parameter(choices = { "---", "Microtubule" }, required = false, label = "Packaged Models",
		description = "A list of prepackaged models.")
	private String modelName = null;

	@Parameter
	private Dataset dataset;

	@Parameter(required = false, label = "Fille ROI Manager",
		description = "Fill the ROI Manager with detected objects.")
	private boolean fillROIManager = false;

	@Parameter(type = ItemIO.OUTPUT)
	private GenericTable table;

	@Parameter(type = ItemIO.OUTPUT)
	private Dataset masks;

	@Parameter
	protected TensorFlowService tfService;

	@Parameter
	protected OpService ops;

	@Parameter
	private CustomDownloadService cds;

	private Location modelLocation;

	// This name is only used for caching the model ZIP file on disk.
	private String modelnameCache;

	private Map<String, Object> parameters;

	@Override
	public void run() {
		try {

			// Get model location
			this.modelLocation = this.getModelLocation();

			// Get a name used for caching the model.
			this.modelnameCache = FilenameUtils.getBaseName(modelLocation.getURI().toString());

			// Load the ZIP model file to access the parameters.
			this.loadParameters();

			// Check input parameters
			this.checkInput();

			// Detect Objects
			this.runPrediction();

		}
		catch (Exception e) {
			log.error(e);
		}
	}

	private void runPrediction() {

		// How many images to process ?
		long nImages;
		if (this.dataset.numDimensions() == 3) {
			nImages = this.dataset.dimension(2);
		}
		else {
			nImages = 1;
		}
		Dataset twoDImage;

		double startTime;
		double stopTime;
		double elapsedTime;

		// Preprocess the image.
		log.info("Preprocessing image.");
		startTime = System.currentTimeMillis();

		Module preprocessModule;
		List<String> classNames = new ArrayList<>();

		Map<String, List<Tensor<?>>> preprocessingOutputsMap = new HashMap<>();
		preprocessingOutputsMap.put("moldedImage", new ArrayList<>());
		preprocessingOutputsMap.put("imageMetadata", new ArrayList<>());
		preprocessingOutputsMap.put("windows", new ArrayList<>());
		preprocessingOutputsMap.put("anchors", new ArrayList<>());
		preprocessingOutputsMap.put("originalImageShape", new ArrayList<>());
		preprocessingOutputsMap.put("imageShape", new ArrayList<>());

		for (int i = 0; i < nImages; i++) {
			ss.showStatus(i, (int) nImages, "Preprocessing image.");

			// Get a 2D image and run it.
			twoDImage = this.getStack(i);
			preprocessModule = this.preprocessSingleImage(twoDImage);

			// Gather outputs in a Map.
			for (Map.Entry<String, List<Tensor<?>>> entry : preprocessingOutputsMap.entrySet()) {
				entry.getValue().add((Tensor<?>) preprocessModule.getOutput(entry.getKey()));
			}
			// Gather non-Tensor outputs.
			classNames = (List<String>) preprocessModule.getOutput("classNames");
		}

		stopTime = System.currentTimeMillis();
		elapsedTime = stopTime - startTime;
		log.info("Preprocessing done. It tooks " + elapsedTime / 1000 + " s.");

		// Detect objects.
		log.info("Running detection.");
		startTime = System.currentTimeMillis();

		Module detectionModule;

		Map<String, List<Tensor<?>>> detectionOutputsMap = new HashMap<>();
		detectionOutputsMap.put("detections", new ArrayList<>());
		detectionOutputsMap.put("mrcnn_mask", new ArrayList<>());
		detectionOutputsMap.put("mrcnn_class", new ArrayList<>());
		detectionOutputsMap.put("mrcnn_bbox", new ArrayList<>());
		detectionOutputsMap.put("rois", new ArrayList<>());

		for (int i = 0; i < nImages; i++) {
			ss.showStatus(i, (int) nImages, "Running detection.");

			// Get a 2D image and run it.
			twoDImage = this.getStack(i);
			detectionModule = this.detectSingleImage(preprocessingOutputsMap.get("moldedImage").get(i),
				preprocessingOutputsMap.get("imageMetadata").get(i), preprocessingOutputsMap.get("anchors")
					.get(i));

			// Gather outputs in a Map.
			for (Map.Entry<String, List<Tensor<?>>> entry : detectionOutputsMap.entrySet()) {
				entry.getValue().add((Tensor<?>) detectionModule.getOutput(entry.getKey()));
			}
		}

		stopTime = System.currentTimeMillis();
		elapsedTime = stopTime - startTime;
		log.info("Detection done. It tooks " + elapsedTime / 1000 + " s.");

		// Postprocess results.
		log.info("Postprocessing results.");
		startTime = System.currentTimeMillis();

		Module postprocessModule;

		Map<String, List<Tensor<?>>> postprocessOutputsMap = new HashMap<>();
		postprocessOutputsMap.put("rois", new ArrayList<>());
		postprocessOutputsMap.put("class_ids", new ArrayList<>());
		postprocessOutputsMap.put("scores", new ArrayList<>());
		postprocessOutputsMap.put("mrcnn_bbox", new ArrayList<>());
		postprocessOutputsMap.put("masks", new ArrayList<>());

		for (int i = 0; i < nImages; i++) {
			ss.showStatus(i, (int) nImages, "Postprocessing results.");

			// Get a 2D image and run it.
			twoDImage = this.getStack(i);
			postprocessModule = this.postprocessSingleImage(detectionOutputsMap.get("detections").get(i),
				detectionOutputsMap.get("mrcnn_mask").get(i), preprocessingOutputsMap.get(
					"originalImageShape").get(i), preprocessingOutputsMap.get("imageShape").get(i),
				preprocessingOutputsMap.get("windows").get(i));

			// Gather outputs in a Map.
			for (Map.Entry<String, List<Tensor<?>>> entry : postprocessOutputsMap.entrySet()) {
				entry.getValue().add((Tensor<?>) postprocessModule.getOutput(entry.getKey()));
			}
		}

		stopTime = System.currentTimeMillis();
		elapsedTime = stopTime - startTime;
		log.info("Postprocessing done. It tooks " + elapsedTime / 1000 + " s.");

		int nDetectedObjects = postprocessOutputsMap.get("scores").stream().mapToInt(
			tensor -> (int) tensor.shape()[0]).sum();

		// Format and return outputs.
		if (nDetectedObjects == 0) {
			this.table = new DefaultGenericTable();
			this.masks = null;
		}
		else {
			this.masks = this.createMasks(postprocessOutputsMap.get("masks"));
			this.table = this.createTable(postprocessOutputsMap.get("rois"), postprocessOutputsMap.get(
				"scores"), postprocessOutputsMap.get("class_ids"), classNames);

			if (fillROIManager) {
				ROIUtils.fillROIManager(this.table);
			}
		}

		// Clean TensorFlow objects.
		// TODO: Calling dispose seems to destroy all the tensors and so the ImageJ
		// image and various array creating from them. On the other side if dispose
		// is not called, sometime closing Fiji does not work.
		// tfService.dispose();

		log.info(nDetectedObjects + " objects detected.");
		log.info("Detection done");
		ss.showStatus("Detection Done.");
	}

	private Dataset getStack(int position) {
		if (this.dataset.numDimensions() == 3) {
			return ds.create((RandomAccessibleInterval) ops.transform().hyperSliceView(this.dataset, 2,
				position));
		}
		else {
			return this.dataset;
		}
	}

	private Module preprocessSingleImage(Dataset data) {
		Map<String, Object> inputs = new HashMap<>();
		inputs.put("modelLocation", this.modelLocation);
		inputs.put("modelName", this.modelnameCache);
		inputs.put("inputDataset", data);
		inputs.put("clearModel", false);

		// Disable postprocessing of the SciJava command.
		CommandInfo command = ij.command().getCommand(MaskRCNNPreprocessImage.class);
		List<PreprocessorPlugin> pre = ij.plugin().createInstancesOfType(PreprocessorPlugin.class);
		Module module = ij.module().waitFor(ij.module().run(command, pre, null, inputs));
		return module;
	}

	private Module detectSingleImage(Tensor<?> moldedImage, Tensor<?> imageMetadata,
		Tensor<?> anchors)
	{
		Map<String, Object> inputs = new HashMap<>();
		inputs.put("modelLocation", this.modelLocation);
		inputs.put("modelName", this.modelnameCache);
		inputs.put("moldedImage", moldedImage);
		inputs.put("imageMetadata", imageMetadata);
		inputs.put("anchors", anchors);
		inputs.put("clearModel", false);

		// Disable postprocessing of the SciJava command.
		CommandInfo command = ij.command().getCommand(MaskRCNNDetector.class);
		List<PreprocessorPlugin> pre = ij.plugin().createInstancesOfType(PreprocessorPlugin.class);
		Module module = ij.module().waitFor(ij.module().run(command, pre, null, inputs));
		return module;
	}

	private Module postprocessSingleImage(Tensor<?> detections, Tensor<?> mrcnn_mask,
		Tensor<?> originalImageShape, Tensor<?> imageShape, Tensor<?> windows)
	{
		Map<String, Object> inputs = new HashMap<>();
		inputs.put("modelLocation", modelLocation);
		inputs.put("modelName", this.modelnameCache);
		inputs.put("detections", detections);
		inputs.put("mrcnnMask", mrcnn_mask);
		inputs.put("originalImageShape", originalImageShape);
		inputs.put("imageShape", imageShape);
		inputs.put("window", windows);
		inputs.put("clearModel", false);

		// Disable postprocessing of the SciJava command.
		CommandInfo command = ij.command().getCommand(MaskRCNNPostprocessImage.class);
		List<PreprocessorPlugin> pre = ij.plugin().createInstancesOfType(PreprocessorPlugin.class);
		Module module = ij.module().waitFor(ij.module().run(command, pre, null, inputs));
		return module;
	}

	protected GenericTable createTable(List<Tensor<?>> rois, List<Tensor<?>> scores,
		List<Tensor<?>> classIds, List<String> classLabels)
	{

		GenericTable table = new DefaultGenericTable();

		table.appendColumn("id");
		table.appendColumn("id");
		table.appendColumn("frame");
		table.appendColumn("class_id");
		table.appendColumn("class_label");
		table.appendColumn("score");
		table.appendColumn("x");
		table.appendColumn("y");
		table.appendColumn("width");
		table.appendColumn("height");

		int x1, y1, x2, y2;
		int lastRow = 0;
		int id = 0;

		for (int n = 0; n < rois.size(); n++) {

			int nRois = (int) rois.get(n).shape()[0];
			int nCoords = (int) rois.get(n).shape()[1];
			int[][] roisArray = rois.get(n).copyTo(new int[nRois][nCoords]);

			float[] scoresArray = scores.get(n).copyTo(new float[(int) scores.get(n).shape()[0]]);
			int[] classIdsArray = classIds.get(n).copyTo(new int[(int) classIds.get(n).shape()[0]]);

			for (int i = 0; i < roisArray.length; i++) {
				x1 = roisArray[i][0];
				y1 = roisArray[i][1];
				x2 = roisArray[i][2];
				y2 = roisArray[i][3];
				table.appendRow();
				lastRow = table.getRowCount() - 1;
				table.set("id", lastRow, String.valueOf(id));
				table.set("frame", lastRow, String.valueOf(n));
				table.set("class_id", lastRow, String.valueOf(classIdsArray[i]));
				table.set("class_label", lastRow, String.valueOf(classLabels.get(classIdsArray[i])));
				table.set("score", lastRow, String.valueOf(scoresArray[i]));
				table.set("x", lastRow, String.valueOf(y1));
				table.set("y", lastRow, String.valueOf(x1));
				table.set("width", lastRow, String.valueOf(y2 - y1));
				table.set("height", lastRow, String.valueOf(x2 - x1));
				id++;
			}
		}
		return table;
	}

	private <T extends RealType<T>> Dataset createMasks(List<Tensor<?>> masks) {

		RandomAccessibleInterval<T> im;

		if (masks.size() == 1) {
			RandomAccessibleInterval<T> imgFloat =
				(RandomAccessibleInterval<T>) net.imagej.tensorflow.Tensors.imgFloat((Tensor<Float>) masks
					.get(0));
			im = imgFloat;
		}
		else {

			List<RandomAccessibleInterval<T>> maskList = new ArrayList<>();
			RandomAccessibleInterval<T> singleIm;

			for (Tensor<?> mask : masks) {
				singleIm = (RandomAccessibleInterval<T>) net.imagej.tensorflow.Tensors.imgFloat(
					(Tensor<Float>) mask);
				for (int i = 0; i < mask.shape()[0]; i++) {
					maskList.add((RandomAccessibleInterval<T>) ops.transform().hyperSliceView(singleIm, 2,
						i));
				}
			}
			im = Views.stack(maskList);
		}

		AxisType[] axisTypes = new AxisType[] { Axes.X, Axes.Y, Axes.TIME };
		String maskName = "Masks of " + this.dataset.getName();
		ImgPlus<T> imgPlus = new ImgPlus(ds.create(im), maskName, axisTypes);
		return ds.create(imgPlus);
	}

	public void checkInput() throws Exception {
		if (this.dataset.numDimensions() != 2 && this.dataset.numDimensions() != 3) {
			throw new Exception("Input image must have 2 or 3 dimensions.");
		}

		int maxSize = (int) this.parameters.get("IMAGE_MAX_DIM");
		if (this.dataset.dimension(0) > maxSize) {
			throw new Exception("Width cannot be greater than " + maxSize + " pixels.");
		}
		if (this.dataset.dimension(1) > maxSize) {
			throw new Exception("Height cannot be greater than " + maxSize + " pixels.");
		}
	}

	private void loadParameters() {
		try {
			File parametersFile = cds.loadFile(this.modelLocation, this.modelnameCache, "config.yml");

			InputStream input = new FileInputStream(parametersFile);
			Yaml yaml = new Yaml();
			this.parameters = (Map<String, Object>) yaml.load(input);

		}
		catch (IOException e) {
			log.error("Can't read parameters.yml in the ZIP model file: " + e);
		}
	}

	private Location getModelLocation() throws Exception {
		if (model != null && !model.equals("")) {
			try {
				URL url = new URL(model);
				return new HTTPLocation(url.toURI());
			}
			catch (MalformedURLException e) {
				File modelFile = new File(model);
				if (modelFile.exists() && !modelFile.isDirectory()) {
					return new FileLocation(modelFile);
				}
				throw new Exception("model is neither an URL or a valid filepath.");
			}
		}
		else if (AVAILABLE_MODELS.containsKey(modelName)) {
			return new HTTPLocation(AVAILABLE_MODELS.get(modelName));
		}
		else {
			throw new Exception("You need to select a valid prepackaged models.");
		}
	}

}
