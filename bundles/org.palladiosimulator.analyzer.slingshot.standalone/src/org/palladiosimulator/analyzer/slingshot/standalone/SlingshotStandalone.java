package org.palladiosimulator.analyzer.slingshot.standalone;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.palladiosimulator.analyzer.slingshot.core.Slingshot;
import org.palladiosimulator.analyzer.slingshot.workflow.jobs.SimulationJob;
import org.palladiosimulator.analyzer.workflow.core.ConstantsContainer;
import org.palladiosimulator.analyzer.workflow.core.jobs.LoadModelIntoBlackboardJob;
import org.palladiosimulator.analyzer.workflow.core.jobs.PreparePCMBlackboardPartitionJob;
import org.palladiosimulator.analyzer.workflow.core.jobs.ResolveAllModelsOfPartitionJob;

import de.uka.ipd.sdq.simucomframework.core.SimuComConfig;
import de.uka.ipd.sdq.workflow.jobs.IBlackboardInteractingJob;
import de.uka.ipd.sdq.workflow.jobs.SequentialBlackboardInteractingJob;
import de.uka.ipd.sdq.workflow.mdsd.blackboard.MDSDBlackboard;

/**
 * Runs a Slingshot simulation without Eclipse and without OSGi.
 *
 * <p>
 * The steps mirror the Eclipse launch ({@code SimulationLauncher} and
 * {@code SimulationRootJob}): extension registry from the class path, EMF
 * setup, Slingshot start, the PCM partition with the model files, then
 * Slingshot's own {@link SimulationJob}. Launch attributes are the same keys
 * and value types as in a {@code .launch} file ({@code String} for string
 * attributes, {@code Boolean} for boolean attributes).
 *
 * <pre>
 * java -cp &lt;jars&gt; org.palladiosimulator.analyzer.slingshot.standalone.SlingshotStandalone \
 *   --model default.allocation --model fallstudie.usagemodel --model mlps.monitorrepository \
 *   --simTime 10000 --seed 0 --out results
 * </pre>
 */
public final class SlingshotStandalone {

	private static final Logger LOGGER = Logger.getLogger(SlingshotStandalone.class);

	private SlingshotStandalone() {
	}

	/** Registry and EMF once per JVM; returns the registry. */
	public static IExtensionRegistry prepare() throws Exception {
		final ClassLoader loader = SlingshotStandalone.class.getClassLoader();
		final IExtensionRegistry registry = ClasspathExtensionRegistry.install(loader);
		EmfStandalone.setup(registry);
		return registry;
	}

	/** Loads the models into a fresh blackboard (PCM partition, shared libraries, all proxies resolved). */
	public static MDSDBlackboard loadModels(final List<URI> models, final IProgressMonitor monitor) throws Exception {
		final MDSDBlackboard blackboard = new MDSDBlackboard();
		final SequentialBlackboardInteractingJob<MDSDBlackboard> load = new SequentialBlackboardInteractingJob<>(
				"Load PCM models", false);
		load.add(new PreparePCMBlackboardPartitionJob());
		for (final URI model : models) {
			load.add(new LoadModelIntoBlackboardJob(model, ConstantsContainer.DEFAULT_PCM_INSTANCE_PARTITION_ID));
		}
		load.add(new ResolveAllModelsOfPartitionJob(ConstantsContainer.DEFAULT_PCM_INSTANCE_PARTITION_ID));
		load.setBlackboard(blackboard);
		load.execute(monitor);
		return blackboard;
	}

	/**
	 * One simulation run with Slingshot's default simulation driver.
	 *
	 * @param models     model files (allocation, usage model, monitor repository, ...)
	 * @param attributes launch attributes
	 */
	public static void run(final List<URI> models, final Map<String, Object> attributes, final IProgressMonitor monitor)
			throws Exception {
		prepare();
		Slingshot.startStandalone();
		try {
			final SimuComConfig config = new SimuComConfig(attributes, false);
			final MDSDBlackboard blackboard = loadModels(models, monitor);
			final IBlackboardInteractingJob<MDSDBlackboard> job = new SimulationJob(config);
			job.setBlackboard(blackboard);
			job.execute(monitor);
			config.getRecorderConfigurationFactory().finalizeRecorderConfigurationFactory();
		} finally {
			Slingshot.stopStandalone();
		}
	}

	/** Launch attributes with the defaults of the Eclipse launch dialog. */
	public static Map<String, Object> defaultAttributes() {
		final Map<String, Object> a = new HashMap<>();
		a.put(SimuComConfig.SIMULATION_TIME, "150000");
		a.put(SimuComConfig.MAXIMUM_MEASUREMENT_COUNT, "-1");
		a.put(SimuComConfig.EXPERIMENT_RUN, SimuComConfig.DEFAULT_EXPERIMENT_RUN);
		a.put(SimuComConfig.VARIATION_ID, SimuComConfig.DEFAULT_VARIATION_NAME);
		a.put(SimuComConfig.SIMULATOR_ID, SimuComConfig.DEFAULT_SIMULATOR_ID);
		a.put(SimuComConfig.PERSISTENCE_RECORDER_NAME, "Slingshot Standalone CSV");
		a.put(SimuComConfig.USE_FIXED_SEED, Boolean.FALSE);
		a.put(SimuComConfig.VERBOSE_LOGGING, Boolean.FALSE);
		a.put(SimuComConfig.SHOULD_THROW_EXCEPTION, Boolean.TRUE);
		a.put(SimuComConfig.SIMULATE_FAILURES, Boolean.FALSE);
		a.put(SimuComConfig.SIMULATE_LINKING_RESOURCES, Boolean.FALSE);
		a.put(SimuComConfig.USE_CONFIDENCE, Boolean.FALSE);
		a.put(SimuComConfig.CONFIDENCE_LEVEL, "95");
		a.put(SimuComConfig.CONFIDENCE_HALFWIDTH, "10");
		a.put(SimuComConfig.CONFIDENCE_MODELELEMENT_NAME, "");
		a.put(SimuComConfig.CONFIDENCE_MODELELEMENT_URI, "");
		a.put(SimuComConfig.CONFIDENCE_USE_AUTOMATIC_BATCHES, Boolean.TRUE);
		a.put(SimuComConfig.CONFIDENCE_BATCH_SIZE, "200");
		a.put(SimuComConfig.CONFIDENCE_MIN_NUMBER_OF_BATCHES, "60");
		return a;
	}

	public static void main(final String[] args) throws Exception {
		final List<URI> models = new ArrayList<>();
		final Map<String, Object> attributes = defaultAttributes();
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "--model" -> models.add(URI.createFileURI(new File(args[++i]).getAbsolutePath()));
			case "--simTime" -> attributes.put(SimuComConfig.SIMULATION_TIME, args[++i]);
			case "--maxMeasurements" -> attributes.put(SimuComConfig.MAXIMUM_MEASUREMENT_COUNT, args[++i]);
			case "--seed" -> {
				final long base = Long.parseLong(args[++i]);
				attributes.put(SimuComConfig.USE_FIXED_SEED, Boolean.TRUE);
				for (int s = 0; s < 6; s++) {
					attributes.put(SimuComConfig.FIXED_SEED_PREFIX + s, Long.toString(base + s));
				}
			}
			case "--out" -> attributes.put(CsvRecorderConfigurationFactory.OUTPUT_DIRECTORY, args[++i]);
			case "--project" -> {
				final String[] kv = args[++i].split("=", 2);
				EmfStandalone.mapProject(kv[0], new File(kv[1]));
			}
			case "--set" -> {
				final String[] kv = args[++i].split("=", 2);
				attributes.put(kv[0], "true".equals(kv[1]) || "false".equals(kv[1]) ? Boolean.valueOf(kv[1]) : kv[1]);
			}
			default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
			}
		}
		if (models.isEmpty()) {
			System.err.println("At least one --model is required.");
			System.exit(2);
		}
		final long t0 = System.nanoTime();
		int exit = 0;
		try {
			run(models, attributes, new NullProgressMonitor());
			LOGGER.info(String.format("Simulation finished in %.1f s", (System.nanoTime() - t0) / 1e9));
		} catch (final Exception e) {
			LOGGER.error("Simulation failed", e);
			exit = 1;
		}
		// Libraries started during the run leave non-daemon threads behind (as in Eclipse, where the
		// platform ends the JVM). A command line run ends here.
		System.exit(exit);
	}
}
