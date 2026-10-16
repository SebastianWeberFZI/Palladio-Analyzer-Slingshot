package org.palladiosimulator.analyzer.slingshot.standalone;

import java.io.File;
import java.util.Map;

import org.palladiosimulator.edp2.models.measuringpoint.MeasuringPoint;
import org.palladiosimulator.recorderframework.core.config.AbstractRecorderConfiguration;
import org.palladiosimulator.recorderframework.core.config.IRecorderConfiguration;
import org.palladiosimulator.recorderframework.core.config.IRecorderConfigurationFactory;

/**
 * Configuration of the {@link CsvRecorder}. The output directory comes from the
 * launch attribute {@value #OUTPUT_DIRECTORY} (default: {@code results} in the
 * working directory).
 */
public class CsvRecorderConfigurationFactory implements IRecorderConfigurationFactory {

	/** Launch attribute: directory for the CSV files. */
	public static final String OUTPUT_DIRECTORY = "slingshot.standalone.outputDirectory";

	private File directory = new File("results");

	@Override
	public void initialize(final Map<String, Object> configuration) {
		final Object dir = configuration.get(OUTPUT_DIRECTORY);
		if (dir != null && !dir.toString().isBlank()) {
			this.directory = new File(dir.toString());
		}
	}

	@Override
	public IRecorderConfiguration createRecorderConfiguration(final Map<String, Object> configuration) {
		// read from the map, as the EDP2 recorder does (getMeasuringPoint() is deprecated)
		final Configuration result = new Configuration(this.directory,
				(MeasuringPoint) configuration.get(AbstractRecorderConfiguration.MEASURING_POINT));
		result.setConfiguration(configuration);
		return result;
	}

	@Override
	public void finalizeRecorderConfigurationFactory() {
		CsvRecorder.closeAll();
	}

	/** Measuring point and metric (from the recorder framework) plus the output directory. */
	public static final class Configuration extends AbstractRecorderConfiguration {

		private final File directory;
		private final MeasuringPoint point;

		Configuration(final File directory, final MeasuringPoint point) {
			this.directory = directory;
			this.point = point;
		}

		public File getDirectory() {
			return this.directory;
		}

		/** String representation of the measuring point, or {@code null} if there is none. */
		public String getMeasuringPointName() {
			return this.point == null ? null : this.point.getStringRepresentation();
		}
	}
}
