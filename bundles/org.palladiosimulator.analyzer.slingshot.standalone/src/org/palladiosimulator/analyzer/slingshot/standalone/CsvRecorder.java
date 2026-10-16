package org.palladiosimulator.analyzer.slingshot.standalone;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.measure.Measure;

import org.palladiosimulator.measurementframework.MeasuringValue;
import org.palladiosimulator.metricspec.MetricDescription;
import org.palladiosimulator.metricspec.MetricSetDescription;
import org.palladiosimulator.recorderframework.core.IRecorder;
import org.palladiosimulator.recorderframework.core.config.IRecorderConfiguration;

/**
 * Writes every measurement of one measuring point and metric as a CSV row.
 *
 * <p>
 * File name: {@code <measuring point>__<metric>.csv}, separator {@code ;},
 * one column per base metric of the (tuple) metric, values in the unit of the
 * measurement, written with {@link Double#toString(double)} so that every
 * value survives a round trip exactly.
 */
public class CsvRecorder implements IRecorder {

	private static final List<CsvRecorder> OPEN = new CopyOnWriteArrayList<>();

	private BufferedWriter out;
	private List<MetricDescription> columns;
	private File file;

	@Override
	public void initialize(final IRecorderConfiguration configuration) {
		final CsvRecorderConfigurationFactory.Configuration config = (CsvRecorderConfigurationFactory.Configuration) configuration;
		final MetricDescription metric = config.getRecorderAcceptedMetric();
		this.columns = baseMetrics(metric);
		final String name = sanitize(config.getMeasuringPointName()) + "__" + sanitize(metric.getName())
				+ ".csv";
		this.file = new File(config.getDirectory(), name);
		try {
			Files.createDirectories(config.getDirectory().toPath());
			this.out = Files.newBufferedWriter(this.file.toPath(), StandardCharsets.UTF_8);
			final List<String> header = new ArrayList<>();
			for (final MetricDescription c : this.columns) {
				header.add(c.getName());
			}
			this.out.write(String.join(";", header));
			this.out.newLine();
		} catch (final IOException e) {
			throw new UncheckedIOException("Cannot open " + this.file, e);
		}
		OPEN.add(this);
	}

	@Override
	public void newMeasurementAvailable(final MeasuringValue measurement) {
		writeData(measurement);
	}

	@Override
	public synchronized void writeData(final MeasuringValue measurement) {
		if (this.out == null) {
			return;
		}
		final StringBuilder line = new StringBuilder();
		for (int i = 0; i < this.columns.size(); i++) {
			if (i > 0) {
				line.append(';');
			}
			final Measure<?, ?> m = measurement.getMeasureForMetric(this.columns.get(i));
			final Object v = m == null ? null : m.getValue();
			line.append(v instanceof Number n ? Double.toString(n.doubleValue()) : String.valueOf(v));
		}
		try {
			this.out.write(line.toString());
			this.out.newLine();
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public synchronized void flush() {
		if (this.out != null) {
			try {
				this.out.flush();
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	@Override
	public void preUnregister() {
		close();
	}

	synchronized void close() {
		if (this.out != null) {
			try {
				this.out.close();
			} catch (final IOException ignored) {
				// nothing left to do
			}
			this.out = null;
		}
		OPEN.remove(this);
	}

	/** Closes all recorders that are still open (end of a run). */
	public static void closeAll() {
		for (final CsvRecorder r : OPEN) {
			r.close();
		}
	}

	public File getFile() {
		return this.file;
	}

	private static List<MetricDescription> baseMetrics(final MetricDescription metric) {
		final List<MetricDescription> out = new ArrayList<>();
		if (metric instanceof MetricSetDescription set) {
			for (final MetricDescription sub : set.getSubsumedMetrics()) {
				out.addAll(baseMetrics(sub));
			}
		} else {
			out.add(metric);
		}
		return out;
	}

	private static String sanitize(final String s) {
		return (s == null ? "unbenannt" : s).replaceAll("[^A-Za-z0-9._-]+", "_").toLowerCase(Locale.ROOT);
	}
}
