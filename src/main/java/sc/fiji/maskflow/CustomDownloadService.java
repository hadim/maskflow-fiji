
package sc.fiji.maskflow;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.imagej.ImageJService;

import org.scijava.app.AppService;
import org.scijava.app.StatusService;
import org.scijava.download.DiskLocationCache;
import org.scijava.download.DownloadService;
import org.scijava.event.EventHandler;
import org.scijava.io.location.BytesLocation;
import org.scijava.io.location.Location;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;
import org.scijava.task.Task;
import org.scijava.task.event.TaskEvent;
import org.scijava.util.ByteArray;

/*
 * Ideally that class should live elsewhere. It comes from 
 * https://github.com/imagej/imagej-tensorflow/blob/2f6a924c50f79c919c97856a7b5905403c07d009/src/main/java/net/imagej/tensorflow/DefaultTensorFlowService.java
 */
@Plugin(type = Service.class)
public class CustomDownloadService extends AbstractService implements ImageJService {

	/** Disk cache defining where compressed models are stored locally. */
	private DiskLocationCache modelCache;

	@Parameter
	private DownloadService downloadService;

	@Parameter
	private AppService appService;

	public File loadFile(final Location source, final String modelName, final String filePath)
		throws IOException
	{
		final String key = modelName + "/" + filePath;

		// Get a local directory with unpacked model data.
		final File modelDir = modelDir(source, modelName);

		return new File(modelDir, filePath);
	}

	// -- Helper methods --

	private DiskLocationCache modelCache() {
		if (modelCache == null) initModelCache();
		return modelCache;
	}

	private synchronized void initModelCache() {
		final DiskLocationCache cache = new DiskLocationCache();

		// Cache the models into $IMAGEJ_DIR/models.
		final File baseDir = appService.getApp().getBaseDirectory();
		final File cacheBase = new File(baseDir, "models");
		if (!cacheBase.exists()) cacheBase.mkdirs();
		cache.setBaseDirectory(cacheBase);

		modelCache = cache;
	}

	// TODO - Migrate unpacking logic into the DownloadService proper.
	// And consider whether/how to avoid using so much temporary space.

	private File modelDir(final Location source, final String modelName) throws IOException {
		final File modelDir = new File(modelCache().getBaseDirectory(), modelName);
		if (!modelDir.exists()) try {
			downloadAndUnpackResource(source, modelDir);
		}
		catch (final InterruptedException | ExecutionException exc) {
			throw new IOException(exc);
		}
		return modelDir;
	}

	/** Downloads and unpacks a zipped resource. */
	private void downloadAndUnpackResource(final Location source, final File destDir)
		throws InterruptedException, ExecutionException, IOException
	{
		// Allocate a dynamic byte array.
		final ByteArray byteArray = new ByteArray(1024 * 1024);

		// Download the compressed model into the byte array.
		final BytesLocation bytes = new BytesLocation(byteArray);
		final Task task = //
			downloadService.download(source, bytes, modelCache()).task();
		final StatusUpdater statusUpdater = new StatusUpdater(task);
		context().inject(statusUpdater);
		task.waitFor();

		// Extract the contents of the compressed data to the model cache.
		final byte[] buf = new byte[64 * 1024];
		final ByteArrayInputStream bais = new ByteArrayInputStream(//
			byteArray.getArray(), 0, byteArray.size());
		destDir.mkdirs();
		try (final ZipInputStream zis = new ZipInputStream(bais)) {
			while (true) {
				final ZipEntry entry = zis.getNextEntry();
				if (entry == null) break; // All done!
				final String name = entry.getName();
				statusUpdater.update("Unpacking " + name);
				final File outFile = new File(destDir, name);
				if (entry.isDirectory()) {
					outFile.mkdirs();
				}
				else {
					final int size = (int) entry.getSize();
					int len = 0;
					try (final FileOutputStream out = new FileOutputStream(outFile)) {
						while (true) {
							statusUpdater.update(len, size, "Unpacking " + name);
							final int r = zis.read(buf);
							if (r < 0) break; // end of entry
							len += r;
							out.write(buf, 0, r);
						}
					}
				}
			}
		}
		statusUpdater.clear();
	}

	/**
	 * A dumb class which passes task events on to the {@link StatusService}.
	 * Eventually, this sort of logic will be built in to SciJava Common. But for
	 * the moment, we do it ourselves.
	 */
	private class StatusUpdater {

		private final DecimalFormat formatter = new DecimalFormat("##.##");
		private final Task task;

		private long lastUpdate;

		@Parameter
		private StatusService statusService;

		private StatusUpdater(final Task task) {
			this.task = task;
		}

		public void update(final String message) {
			statusService.showStatus(message);
		}

		public void update(final int value, final int max, final String message) {
			final long timestamp = System.currentTimeMillis();
			if (timestamp < lastUpdate + 100) return; // Avoid excessive updates.
			lastUpdate = timestamp;

			final double percent = 100.0 * value / max;
			statusService.showStatus(value, max, message + ": " + //
				formatter.format(percent) + "%");
		}

		public void clear() {
			statusService.clearStatus();
		}

		@EventHandler
		private void onEvent(final TaskEvent evt) {
			if (task == evt.getTask()) {
				final int value = (int) task.getProgressValue();
				final int max = (int) task.getProgressMaximum();
				final String message = task.getStatusMessage();
				update(value, max, message);
			}
		}
	}

}
