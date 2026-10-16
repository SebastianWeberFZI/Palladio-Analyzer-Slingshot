package org.palladiosimulator.analyzer.slingshot.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EList;
import org.palladiosimulator.pcm.core.PCMRandomVariable;
import org.palladiosimulator.pcm.seff.AbstractBranchTransition;
import org.palladiosimulator.pcm.seff.GuardedBranchTransition;
import org.palladiosimulator.pcm.seff.ProbabilisticBranchTransition;
import org.palladiosimulator.pcm.usagemodel.BranchTransition;

import com.google.common.base.Preconditions;

import de.uka.ipd.sdq.simucomframework.variables.StackContext;
import de.uka.ipd.sdq.simucomframework.variables.cache.StoExCache;
import de.uka.ipd.sdq.simucomframework.variables.stackframe.SimulatedStackframe;

/**
 * Util class to determine a transition based on probabilities.
 * 
 * @author Joachim Meyer
 * @author Julijan Katic
 */
public class TransitionDeterminer {

	private static final Logger LOGGER = Logger.getLogger(TransitionDeterminer.class);

	/** The stack frame in which the parameter lies. */
	private final SimulatedStackframe<Object> currentStackFrame;

	public TransitionDeterminer(final SimulatedStackframe<Object> currentStackFrame) {
		Preconditions.checkNotNull(currentStackFrame);
		this.currentStackFrame = currentStackFrame;
	}

	/**
	 * Checks whether the boolean expression in the condition holds or not.
	 * 
	 * @param condition the condition that must be a boolean expression
	 * @return true iff it holds.
	 */
	private boolean conditionHolds(final PCMRandomVariable condition) {
		return StackContext.evaluateStatic(condition.getSpecification(), Boolean.class, this.currentStackFrame);
	}

	/**
	 * Sums the probabilities of the list of probabilities. In a list of summed
	 * probabilities, each value of an element in the list has its own probability
	 * added by the previous probability. Means, if the first probabilites in the
	 * list of probabilities is 0.3, the value of the first element in the summed
	 * probability list is 0.3. If the second probability in the list is 0.4, the
	 * corresponding value in the summed probability list is 0.4+0.3 and so on.
	 * 
	 * @param branchProbabilities a list with branch probabilities.
	 * @return the summed probability list.
	 */
	protected List<Double> createSummedProbabilityList(final List<Double> branchProbabilities) {
		double currentSum = 0;
		final List<Double> summedProbabilityList = new ArrayList<>();
		for (final Double probability : branchProbabilities) {
			currentSum += probability;
			summedProbabilityList.add(currentSum);
		}
		return summedProbabilityList;
	}
	
	/**
	 * Determines a branch transition out of a list of branch transitions, with respect
	 * to their probabilities.
	 * 
	 * @param branchTransitions the list of branch transitions.
	 * @return a branch transition.
	 */
	public BranchTransition determineBranchTransition(final EList<BranchTransition> branchTransitions) {
		final List<Double> summedProbabilityList = this
		        .createSummedProbabilityList(this.extractProbabilitiesUsageModel(branchTransitions));
		final int transitionIndex = this.getRandomIndex(summedProbabilityList);

		return branchTransitions.get(transitionIndex);
	}
	
	/**
	 * Determines a guarded branch transition out of a list of guarded branch transitions.
	 * 
	 * @param guardedBranchTransitions the list of guarded branch transitions.
	 * @return a guarded branch transition. This is the branch transition whose condition holds first.
	 */
	private GuardedBranchTransition determineGuardedBranchTransition(final EList<AbstractBranchTransition> guardedBranchTransitions) {
		/*
		 * There is no predefined order in evaluating the guards attached to a BranchAction. So the first guard
		 * which evaluates to true will be chosen.
		 * 
		 * Further: As it is unclear of INNER variables in branch conditions if different or if the same collection
		 * element is meant by the component developer, the current PCM version forbids the use of INNER characterizarions
		 * in branch conditions. Thus, this problem has not to be addressed like in the collection iterator (EvaluationProxies
		 * and the same value for all occurences in one iteration).
		 */
		int i = 0;
		GuardedBranchTransition branchTransition = null;
		for (final AbstractBranchTransition abstractBranchTransition : guardedBranchTransitions) {
			final GuardedBranchTransition guardedBranchTransition = (GuardedBranchTransition) abstractBranchTransition;
			final PCMRandomVariable condition = guardedBranchTransition.getBranchCondition_GuardedBranchTransition();
			
			if (this.conditionHolds(condition)) {
				branchTransition = (GuardedBranchTransition) guardedBranchTransitions.get(i);
				break;
			}
			
			i++;
		}
		
		return branchTransition;
	}
	
	/**
	 * Determines a probabilistic branch transition out of a list of probabilistic branch
	 * transitions, with respect to their probabilities.
	 * 
	 * @param probabilisticBranchTransitions the list of probabilistic branch transition.
	 * @return a probabilistic branch transition.
	 */
	public ProbabilisticBranchTransition determineProbabilisticBranchTransition(final EList<AbstractBranchTransition> probabilisticBranchTransitions) {
		final List<Double> summedProbabilityList = this.createSummedProbabilityList(this.extractProbabilitiesRDSEFF(probabilisticBranchTransitions));
		
		final int transitionIndex = this.getRandomIndex(summedProbabilityList);
		
		final ProbabilisticBranchTransition branchTransition = (ProbabilisticBranchTransition) probabilisticBranchTransitions.get(transitionIndex);
		
		return branchTransition;
	}
	
	/**
	 * Determines a branch transition in the list of branch transitions. The list can only contain
	 * either probabilistic or guarded branch transitions.
	 * 
	 * @param abstractBranchTransitions the list with branch transitions.
	 * @return the determined AbstractBranchTransition.
	 */
	public AbstractBranchTransition determineTransition(final EList<AbstractBranchTransition> abstractBranchTransitions) {
		/*
		 * Mixed types with branch is not allowed, so the following is sufficient.
		 */
		final AbstractBranchTransition branchTransition;
		
		if (abstractBranchTransitions.get(0) instanceof ProbabilisticBranchTransition) {
			branchTransition = this.determineProbabilisticBranchTransition(abstractBranchTransitions);
		} else {
			branchTransition = this.determineGuardedBranchTransition(abstractBranchTransitions);
		}
		
		return branchTransition;
	}
	
	private int getRandomIndex(final List<Double> summedProbabilityList) {
		if (summedProbabilityList.size() == 0) {
			return -1;
		}

		final double lastSum = summedProbabilityList.get(summedProbabilityList.size() - 1);
		// The random generator of the stochastic expressions honors "fixed seed";
		// Math.random() made every branch choice irreproducible.
		final double randomNumber = StoExCache.singleton().getRandomGenerator().random();

		int i = 0;
		for (final Double sum : summedProbabilityList) {
			if (lastSum * randomNumber < sum) {
				return i;
			}
			i++;
		}
		return -1;
	}

	protected List<Double> extractProbabilitiesRDSEFF(final EList<AbstractBranchTransition> probabilisticBranchTransitions) {
		return probabilisticBranchTransitions.stream()
				.map(ProbabilisticBranchTransition.class::cast)
				.map(ProbabilisticBranchTransition::getBranchProbability)
				.collect(Collectors.toList());
	}
	
	protected List<Double> extractProbabilitiesUsageModel(final EList<BranchTransition> branchTransitions) {
		return branchTransitions.stream()
		        .map(BranchTransition::getBranchProbability)
		        .collect(Collectors.toList());
	}
}
