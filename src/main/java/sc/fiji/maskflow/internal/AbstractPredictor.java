
package sc.fiji.maskflow.internal;

import java.io.IOException;
import java.util.List;

import net.imagej.tensorflow.TensorFlowService;

import org.scijava.io.location.Location;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.tensorflow.Graph;
import org.tensorflow.Session;

public abstract class AbstractPredictor {

	@Parameter
	protected LogService log;

	@Parameter
	protected TensorFlowService tfService;

	protected Graph graph;
	protected Session session;

	protected void loadModel(Location modelLocation, String modelName, String modelFilename) {
		try {
			this.graph = tfService.loadGraph(modelLocation, modelName, modelFilename);
			this.session = new Session(this.graph);
		}
		catch (IOException e) {
			log.error(e);
		}
	}

	protected List<String> loadLabels(Location modelLocation, String modelName, String fname) {

		try {
			return tfService.loadLabels(modelLocation, modelName, fname);
		}
		catch (IOException e) {
			log.error(e);
		}
		return null;
	}

	protected void clear() {
		// Do some cleaning
		this.session.close();
		tfService.dispose();
	}

	public Graph getGraph() {
		return graph;
	}

	public Session getSession() {
		return session;
	}

}
