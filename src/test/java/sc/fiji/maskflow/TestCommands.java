
package sc.fiji.maskflow;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import net.imagej.Dataset;

import org.junit.Test;

public class TestCommands extends AbstractTest {

	@Test
	public void TestPseudoFlatFieldCorrectionCommand() throws IOException, InterruptedException,
		ExecutionException
	{
		final String sampleImage =
			"8bit-unsigned&pixelType=uint8&indexed=true&lengths=10,10,5&axes=X,Y,Time.fake";

		Dataset dataset = (Dataset) io.open(sampleImage);

		Map<String, Object> inputs = new HashMap<>();
		inputs.put("input", dataset);
		inputs.put("gaussianFilterSize", 50);
		inputs.put("normalizeIntensity", false);
		inputs.put("iteratePlane", true);
		inputs.put("saveImage", false);
		inputs.put("suffix", "");

		//CommandModule module = command.run(PseudoFlatFieldCorrectionCommand.class, true, inputs).get();
		//Dataset output = (Dataset) module.getOutput("output");

		//assertEquals(output.numDimensions(), dataset.numDimensions());
	}

	@Test
	public void TestDOGFilterCommand() throws IOException, InterruptedException, ExecutionException {
		final String sampleImage =
			"8bit-unsigned&pixelType=uint8&indexed=true&lengths=10,10,5&axes=X,Y,Time.fake";

		Dataset dataset = (Dataset) io.open(sampleImage);

		Map<String, Object> inputs = new HashMap<>();
		inputs.put("input", dataset);
		inputs.put("sigma1", 6);
		inputs.put("sigma2", 2);
		inputs.put("normalizeIntensity", true);
		inputs.put("saveImage", false);
		inputs.put("suffix", "");

		//CommandModule module = command.run(DOGFilterCommand.class, true, inputs).get();
		//Dataset output = (Dataset) module.getOutput("output");

		//assertEquals(output.numDimensions(), dataset.numDimensions());
	}

}
