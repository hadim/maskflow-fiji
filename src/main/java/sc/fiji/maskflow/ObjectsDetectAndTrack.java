
package sc.fiji.maskflow;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.GenericTable;

import net.imagej.Dataset;
import net.imagej.ops.OpService;

@Plugin(type = Command.class, menuPath = "Plugins>Maskflow>Detect and Track Objects",
	headless = true)
public class ObjectsDetectAndTrack implements Command {

	@Parameter
	private LogService log;

	@Parameter
	private OpService ops;

	@Parameter
	private CommandService cs;

	// Detection Parameters

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

	// Tracking Parameters

	@Parameter(required = false)
	private double linkingMaxDistance = 10.0;

	@Parameter(required = false)
	private double gapClosingMaxDistance = 10.0;

	@Parameter(required = false)
	private int maxFrameGap = 3;

	@Parameter(required = false)
	private boolean fillROIManager = false;

	@Parameter(type = ItemIO.OUTPUT)
	private Dataset masks;

	@Parameter(type = ItemIO.OUTPUT)
	private GenericTable resultTable;

	@Override
	public void run() {
		try {

			// Detect objects
			Map<String, Object> inputs = new HashMap<>();
			inputs.put("model", model);
			inputs.put("modelName", modelName);
			inputs.put("dataset", dataset);
			inputs.put("fillROIManager", false);
			CommandModule module = cs.run(ObjectsDetector.class, true, inputs).get();

			this.masks = (Dataset) module.getOutput("masks");
			GenericTable table = (GenericTable) module.getOutput("table");

			// Track objects
			inputs = new HashMap<>();
			inputs.put("masks", this.masks);
			inputs.put("table", table);
			inputs.put("linkingMaxDistance", linkingMaxDistance);
			inputs.put("gapClosingMaxDistance", gapClosingMaxDistance);
			inputs.put("maxFrameGap", maxFrameGap);
			inputs.put("fillROIManager", fillROIManager);
			module = cs.run(ObjectsTracker.class, true, inputs).get();

			this.resultTable = (GenericTable) module.getOutput("resultTable");

		}
		catch (InterruptedException | ExecutionException e) {
			log.error(e);
		}
	}

}
