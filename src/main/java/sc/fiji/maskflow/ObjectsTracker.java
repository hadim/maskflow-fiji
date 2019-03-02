
package sc.fiji.maskflow;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.GenericTable;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.tracking.LAPUtils;
import fiji.plugin.trackmate.tracking.SpotTracker;
import fiji.plugin.trackmate.tracking.SpotTrackerFactory;
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPTrackerFactory;
import net.imagej.Dataset;
import net.imagej.lut.LUTService;
import net.imagej.ops.OpService;
import net.imglib2.IterableInterval;
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.display.ColorTable;
import net.imglib2.img.Img;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.IntervalView;
import sc.fiji.maskflow.utils.ROIUtils;

@Plugin(type = Command.class, headless = true)
public class ObjectsTracker implements Command {

	@Parameter
	private LogService log;

	@Parameter
	private OpService ops;

	@Parameter
	private LUTService luts;

	@Parameter
	private GenericTable table;

	@Parameter
	private Dataset masks;

	@Parameter(required = false)
	private double linkingMaxDistance = 10.0;

	@Parameter(required = false)
	private double gapClosingMaxDistance = 10.0;

	@Parameter(required = false)
	private int maxFrameGap = 3;

	@Parameter(required = false)
	private boolean fillROIManager = false;

	@Parameter(type = ItemIO.OUTPUT)
	private GenericTable resultTable;

	@Override
	public void run() {

		assert masks.numDimensions() == 3 : "Mask needs to be of dimension 3.";

		SpotCollection spots = this.buildSpotsFromMasks();
		// spots = this.getFakeSpots();

		// Do the tracking.
		SpotTrackerFactory factory = new SparseLAPTrackerFactory();
		Map<String, Object> settings = new HashMap<>();
		settings.putAll(LAPUtils.getDefaultLAPSettingsMap());
		settings.put("LINKING_MAX_DISTANCE", linkingMaxDistance);
		settings.put("GAP_CLOSING_MAX_DISTANCE", gapClosingMaxDistance);
		settings.put("MAX_FRAME_GAP", maxFrameGap);
		settings.put("ALLOW_GAP_CLOSING", true);

		SpotTracker tracker = factory.create(spots, settings);

		// A number of thread of more than 1 leads to inconsistent tracking
		// results...
		tracker.setNumThreads(Runtime.getRuntime().availableProcessors());
		tracker.setNumThreads(1);

		SimpleWeightedGraph<Spot, DefaultWeightedEdge> results = null;
		if (tracker.checkInput() && tracker.process()) {
			results = tracker.getResult();
		}
		else {
			log.error("Tracking failed : " + tracker.getErrorMessage());
			return;
		}

		// Process tracking results and edit the new table. Reconstruct all the
		// possible tracks from the graph object. This method do not take into
		// account merging and splitting (only gap closing).

		Map<Integer, List<Spot>> trackedSpots = new HashMap<>();
		int id1;
		int id2;
		int nextID;

		for (Spot spot1 : results.vertexSet()) {
			for (Spot spot2 : results.vertexSet()) {
				if (results.getEdge(spot1, spot2) != null) {

					id1 = this.contains(spot1, trackedSpots);
					id2 = this.contains(spot2, trackedSpots);

					if (id1 >= 0 && id2 >= 0) {
						// Do nothing
					}
					else if (id1 >= 0) {
						trackedSpots.get(id1).add(spot2);
					}
					else if (id2 >= 0) {
						trackedSpots.get(id2).add(spot1);
					}
					else {
						nextID = trackedSpots.size();
						trackedSpots.put(nextID, new ArrayList<>());
						trackedSpots.get(nextID).add(spot1);
						trackedSpots.get(nextID).add(spot2);
					}
				}
			}
		}

		// Now we had the spots that are linked to nothing.
		for (Spot spot : spots.iterable(true)) {
			if (contains(spot, trackedSpots) == -1) {
				nextID = trackedSpots.size();
				trackedSpots.put(nextID, new ArrayList<>());
				trackedSpots.get(nextID).add(spot);
			}
		}

		// Now we iterate over each tracker spots and add a column to table to set
		// its object id.

		table.appendColumn("object_id");

		int objectID;
		List<Spot> spotList;
		for (Map.Entry<Integer, List<Spot>> entry : trackedSpots.entrySet()) {
			objectID = entry.getKey();
			spotList = entry.getValue();
			for (Spot spot : spotList) {
				table.set("object_id", spot.getFeature("ID").intValue(), String.valueOf(objectID));
			}
		}

		resultTable = table;

		if (fillROIManager) {
			URL lutURL = luts.findLUTs().get("Spectrum.lut");
			try {
				ColorTable colorTable = luts.loadLUT(lutURL);
				ROIUtils.fillROIManager(resultTable, colorTable);
			}
			catch (IOException exc) {
				log.error("Can't load the LUT table to fill the ROI Manager.");
			}
		}

	}

	private SpotCollection buildSpotsFromMasks() {

		SpotCollection spots = new SpotCollection();
		Spot spot;
		int frameID;

		// Convert the mask to integer
		Img<IntegerType> integerMasks = ops.convert().uint8((IterableInterval) this.masks);

		IntervalView frame;
		ImgLabeling imgLabeling;
		LabelRegions<?> labelRegions;
		LabelRegion labelRegion;
		LabelRegion biggestRegion;
		double x;
		double y;
		double z;
		double quality;
		double radius;

		// Build the object list where the location of each object (Spot) are set to
		// the centroid of the biggest element of the mask.

		for (int i = 0; i < this.masks.dimension(2); i++) {

			// Get the frame of this mask
			frameID = Integer.valueOf((String) table.get("frame").get(i));

			// TODO: make sure the frame is from IntegerType or convert it.
			frame = ops.transform().hyperSliceView(integerMasks, 2, i);

			imgLabeling = ops.labeling().cca(frame, StructuringElement.EIGHT_CONNECTED);
			labelRegions = new LabelRegions(imgLabeling);

			biggestRegion = null;
			for (Object label : labelRegions) {
				labelRegion = (LabelRegion) label;
				if (biggestRegion == null || labelRegion.size() > biggestRegion.size()) {
					biggestRegion = labelRegion;
				}
			}

			x = biggestRegion.getCenterOfMass().getDoublePosition(0);
			y = biggestRegion.getCenterOfMass().getDoublePosition(1);
			z = 0;
			quality = 1;
			radius = 1;

			spot = new Spot(x, y, z, quality, radius);
			spot.getFeatures().put("ID", (double) i);
			spot.getFeatures().put("SIZE", (double) biggestRegion.size());
			spots.add(spot, frameID);
		}

		return spots;
	}

	private int contains(Spot spot, Map<Integer, List<Spot>> trackedSpots) {
		for (Map.Entry<Integer, List<Spot>> entry : trackedSpots.entrySet()) {
			if (entry.getValue().contains(spot)) {
				return entry.getKey();
			}
		}
		return -1;
	}

	private SpotCollection getFakeSpots() {
		SpotCollection spots = new SpotCollection();
		Spot fakeSpot;

		fakeSpot = new Spot(52, 50, 0, 2, 1);
		fakeSpot.getFeatures().put("ID", 0.0);
		spots.add(fakeSpot, 0);

		fakeSpot = new Spot(52, 50, 0, 2, 1);
		fakeSpot.getFeatures().put("ID", 1.0);
		spots.add(fakeSpot, 1);

		fakeSpot = new Spot(52, 50, 0, 2, 1);
		fakeSpot.getFeatures().put("ID", 2.0);
		spots.add(fakeSpot, 2);

		fakeSpot = new Spot(12, 10, 0, 2, 1);
		fakeSpot.getFeatures().put("ID", 3.0);
		spots.add(fakeSpot, 0);

		fakeSpot = new Spot(12, 10, 0, 2, 1);
		fakeSpot.getFeatures().put("ID", 4.0);
		spots.add(fakeSpot, 1);

		fakeSpot = new Spot(12, 10, 0, 2, 1);
		fakeSpot.getFeatures().put("ID", 5.0);
		spots.add(fakeSpot, 2);

		return spots;
	}

}
