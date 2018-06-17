
package sc.fiji.maskflow.utils;

import net.imagej.tensorflow.GraphBuilder;

import org.tensorflow.Graph;
import org.tensorflow.Output;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

public class TensorUtils {

	public static Tensor<?> expandDimension(Tensor<?> tensor, int dimension) {
		try (Graph g = new Graph()) {
			final GraphBuilder b = new GraphBuilder(g);

			final Output input = b.constant("input", tensor);
			final Output output = g.opBuilder("ExpandDims", "ExpandDims").addInput(input).addInput(b
				.constant("dimension", dimension)).build().output(0);

			try (Session s = new Session(g)) {
				Tensor<?> result = (Tensor<?>) s.runner().fetch(output.op().name()).run().get(0);
				return result;
			}
		}
	}

}
