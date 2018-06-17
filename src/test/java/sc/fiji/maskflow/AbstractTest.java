
package sc.fiji.maskflow;

import net.imagej.ImageJ;

import org.junit.After;
import org.junit.Before;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

public class AbstractTest {

	@Parameter
	protected Context context;

	@Parameter
	protected LogService log;

	@Parameter
	protected IOService io;

	@Parameter
	protected CommandService command;

	/** Subclasses can override to create a context with different services. */
	protected Context createContext() {
		ImageJ ij = new ImageJ();
		return ij.context();
	}

	@Before
	public void setUp() {
		createContext().inject(this);
	}

	@After
	public synchronized void cleanUp() {
		if (context != null) {
			context.dispose();
			context = null;
			log = null;
			io = null;
			command = null;
		}
	}

}
