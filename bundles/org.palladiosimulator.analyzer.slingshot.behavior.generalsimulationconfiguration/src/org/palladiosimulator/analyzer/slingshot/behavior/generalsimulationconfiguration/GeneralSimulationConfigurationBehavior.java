package org.palladiosimulator.analyzer.slingshot.behavior.generalsimulationconfiguration;

import jakarta.inject.Inject;

import org.apache.log4j.Logger;
import org.palladiosimulator.analyzer.slingshot.core.extension.SimulationBehaviorExtension;

import de.uka.ipd.sdq.probfunction.math.IProbabilityFunctionFactory;
import de.uka.ipd.sdq.probfunction.math.impl.ProbabilityFunctionFactoryImpl;
import de.uka.ipd.sdq.simucomframework.core.SimuComConfig;
import de.uka.ipd.sdq.simucomframework.variables.cache.StoExCache;

/**
 * This behavior is used in order to have a general implementation for configuraring
 * the simulator beforehand. For instance, this class initializes the {@link StoEx} cache
 * on the simulation start.
 * 
 * @author Julijan Katic
 * @version 1.0
 */
public class GeneralSimulationConfigurationBehavior implements SimulationBehaviorExtension {
	
	private final static Logger LOGGER = Logger.getLogger(GeneralSimulationConfigurationBehavior.class);
	
	@Inject
	public GeneralSimulationConfigurationBehavior(final SimuComConfig config) {
		this.initializeStoExCache(config);
	}
	
	/**
	 * Initializes the ProbFunction and StoExCache.
	 * 
	 * <p>
	 * The probability function factory gets the random generator of the
	 * simulation configuration, as in SimuCom. Only this generator honors the
	 * "fixed seed" setting of the launch configuration; without it, two runs
	 * with the same seed draw different numbers. It has to be set before the
	 * first stochastic expression is evaluated, which is why this happens here,
	 * in the first behavior to be created.
	 */
	private void initializeStoExCache(final SimuComConfig config) {
		final IProbabilityFunctionFactory probabilityFunctionFactory = ProbabilityFunctionFactoryImpl.getInstance();
		probabilityFunctionFactory.setRandomGenerator(config.getRandomGenerator());
		StoExCache.initialiseStoExCache(probabilityFunctionFactory);
		LOGGER.info("Initialized probability function");
	}
}
