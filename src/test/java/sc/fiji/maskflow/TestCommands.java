
package sc.fiji.maskflow;

public class TestCommands extends AbstractTest {

/*	@Test
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

		CommandModule module = command.run(PseudoFlatFieldCorrectionCommand.class, true, inputs).get();
		Dataset output = (Dataset) module.getOutput("output");

		assertEquals(output.numDimensions(), dataset.numDimensions());
	}*/

}
