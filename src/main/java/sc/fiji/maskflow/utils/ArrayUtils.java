
package sc.fiji.maskflow.utils;

import java.util.List;

public class ArrayUtils {

	static public int[] listDoubleToIntArray(List<Double> list) {
		int[] array = new int[list.size()];
		for (int i = 0; i < list.size(); i++) {
			array[i] = list.get(i).intValue();
		}
		return array;
	}

	static public int[] listIntegerToIntArray(List<Integer> list) {
		int[] array = new int[list.size()];
		for (int i = 0; i < list.size(); i++) {
			array[i] = list.get(i).intValue();
		}
		return array;
	}

	static public float[] listDoubleToFloatArray(List<Object> list) {
		float[] array = new float[list.size()];
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof Integer) {
				array[i] = ((Integer) list.get(i)).floatValue();
			}
			else if (list.get(i) instanceof Double) {
				array[i] = ((Double) list.get(i)).floatValue();
			}
			else {
				// Should not happen.
				array[i] = (float) list.get(i);
			}
		}
		return array;
	}

}
