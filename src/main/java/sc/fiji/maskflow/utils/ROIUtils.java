
package sc.fiji.maskflow.utils;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import org.scijava.table.GenericColumn;
import org.scijava.table.GenericTable;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import net.imglib2.display.ColorTable;

public class ROIUtils {

	static public void fillROIManager(GenericTable table) {
		fillROIManager(table, true, null);
	}

	static public void fillROIManager(GenericTable table, ColorTable colorTable) {
		fillROIManager(table, true, colorTable);
	}

	static public void fillROIManager(GenericTable table, boolean resetROIManager,
		ColorTable colorTable)
	{

		RoiManager rm = RoiManager.getRoiManager();

		if (resetROIManager) {
			rm.reset();
		}

		int x;
		int y;
		int width;
		int height;
		int frame;
		int id;
		int classID;
		double score;
		Roi roi;
		int objectID;
		int r;
		int g;
		int b;

		int totalObjects = 0;

		try {
			GenericColumn col = (GenericColumn) table.get("object_id");
			totalObjects = (int) col.stream().map(v -> Integer.valueOf((String) v)).distinct().count();
		}
		catch (IllegalArgumentException e) {
			// Do nothing
		}

		Map<Integer, Color> colors = new HashMap<>();

		for (int row = 0; row < table.getRowCount(); row++) {

			id = Integer.valueOf((String) table.get("id", row));
			x = Integer.valueOf((String) table.get("x", row));
			y = Integer.valueOf((String) table.get("y", row));
			width = Integer.valueOf((String) table.get("width", row));
			height = Integer.valueOf((String) table.get("height", row));
			frame = Integer.valueOf((String) table.get("frame", row));
			classID = Integer.valueOf((String) table.get("class_id", row));
			score = Double.valueOf((String) table.get("score", row));

			roi = new Roi(x, y, width, height);
			roi.setPosition(frame + 1);
			roi.setName("BBox-" + id + "-Score-" + score + "-ClassID-" + classID + "-Frame-" + frame);

			try {
				objectID = Integer.valueOf((String) table.get("object_id", row));
				if (!colors.keySet().contains(objectID)) {
					r = colorTable.getResampled(0, totalObjects, objectID);
					g = colorTable.getResampled(1, totalObjects, objectID);
					b = colorTable.getResampled(2, totalObjects, objectID);
					colors.put(objectID, new Color(r, g, b));
				}
				roi.setStrokeColor(colors.get(objectID));
			}
			catch (IllegalArgumentException e) {
				// Do nothing
			}

			rm.add((ImagePlus) null, roi, frame + 1);
		}

	}

}
