package microsim.alignment.multiple;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import microsim.agent.Weight;
import microsim.alignment.AlignmentUtils;
import org.apache.commons.collections4.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.InputMismatchException;
import java.util.List;

import static jamjam.Sum.sum;
import static jamjam.probability.StatisticalDistance.KullbackLeiblerDivergence;
import static java.lang.Double.isInfinite;
import static java.lang.Double.isNaN;
import static java.lang.String.format;

/**
 * Multinomial alignment methods, where there is in general a set 'A' (>=2) of possible outcomes/states to align.
 * This algorithm is called *Logit Scaling* and is based on the minimization of the information loss (relative entropy).
 * This is an abstract class which can be applied to both weighted and non-weighted cases. Additionally, it can be used
 * in conventional binary alignment problems.
 *
 * @implSpec Getters are introduced with the purpose of documenting corresponding variables via {@code lombok}. This
 *           way, their meaning is not hidden in commented lines, but accessible with the help of Javadoc. Do *NOT*
 *           remove them.
 *
 * @implNote The design of this class suboptimal at the moment to make all class implementations share as much code as
 *           possible. In particular, when agents have no weight, they all automatically get one that is equal to 1.
 *           This code extends the original algorithm by adding the case of weighted samples. It also does add a filter
 *           for the whole population to align only the relevant subpopulation.
 *
 *           In the degenerate case when all probabilities are given as open-point distributions this algorithm fails to
 *           converge. This happens due to the fact it conserves 0s & 1s, thus, the entire array of probabilities does
 *           not change.
 *
 * @param <T> The Type parameter usually representing the agent class.
 *
 * @see <a href="https://ideas.repec.org/a/ijm/journl/v9y2016i3p89-102.html">Peter Stephensen, A General Method
 *      for Alignment in Microsimulation models, International Journal of Microsimulation (2016) 9(3) 89-102</a>
 */
public class AbstractLogitScalingAlignment<T> implements AlignmentUtils<T> {
    /**
     * @return totalChoiceNumber The number of possible outcomes of a given event that is the length of
     *                           the {@code targetShare} parameter.
     */
    @Getter(AccessLevel.PACKAGE) private int totalChoiceNumber;

    /**
     * @return filteredAgentList A list of agents after applying {@link #extractAgentList(Collection, Predicate)} and
     *                           {@code filter}.
     */
    @Getter(AccessLevel.PACKAGE) private List<T> filteredAgentList;

    /**
     * @return totalAgentNumber The total number of agents after passing the {@code filter}, obtained as the size of
     *                     {@link #getFilteredAgentList()}.
     */
    @Getter(AccessLevel.PACKAGE) private int totalAgentNumber;

    /**
     * @return weights An array of agent weights, that has the same size as the array returned by
     *                 {@link #getFilteredAgentList()}. {@code null} when there is no weights at all.
     */
    @Getter(AccessLevel.PACKAGE) private double[] weights;

    /**
     * @return probabilities A 2D array of probabilities, every value represents an agent/outcome probability. The size
     *                       is defined as {@link #getTotalAgentNumber()}x{@link #getTotalChoiceNumber()}. All values
     *                       are updated during simulation.
     */
    @Getter(AccessLevel.PACKAGE) private double[][] probabilities;

    /**
     * @return total The sum of all weights of all agents, retrieved by passing {@link #getWeights()} to {@link
     *               jamjam.Sum#sum(double...)}.
     */
    @Getter(AccessLevel.PACKAGE) private double total;

    /**
     * @return target The target share of the relevant subpopulation. Sums of the aligned probabilities must be equal
     *                to these values.
     */
    @Getter(AccessLevel.PACKAGE) private double[] targetShare;

    /**
     * @return probSumOverAgents An array of size {@link #getTotalChoiceNumber()}, containing sums of probabilities
     *                           over {@code agents} per choice at current iteration.
     */
    @Getter(AccessLevel.PACKAGE) private double[] probSumOverAgents;

    /**
     * @return probSumOverAgents An array of size {@link #getTotalAgentNumber()}, containing sums of probabilities over
     *                           {@code choices} per agent at current iteration.
     */
    @Getter(AccessLevel.PACKAGE) private double[] probSumOverChoices;

    /**
     * @return modelIsWeighted A {@code boolean} showing if the model has to deal with weighted data.
     */
    @Getter(AccessLevel.PACKAGE) private boolean weightedModel;

    /**
     * @return  alphaValues An array containing alpha values.
     */
    @Getter(AccessLevel.PACKAGE) private double[] alphaValues;

    /**
     * @return  gammaValues An array containing gamma values.
     */
    @Getter(AccessLevel.PACKAGE) private double[] gammaValues;

    /**
     * @return  tempAgents A temporary array containing a certain property of all agents.
     */
    @Getter(AccessLevel.PACKAGE) private double[] tempAgents;

    /**
     * General alignment procedure, it adjusts probabilities using all the provided parameters until the algorithm
     * reaches the target precision/number of iterations.
     * @param agents A collection of agents of a given type. Most commonly, every agent is a person,
     *               however, this class does not limit its usage to humans only.
     * @param filter A filter to select a subpopulation from a given collection of {@code agents}.
     * @param closure An object that specifies how to get the (unaligned) probability of the agent and how to set the
     *                result of the aligned probability.
     * @param targetProbabilityDistribution The target discrete probability distribution. Means of the
     *                                      aligned probabilities must be equal to these values.
     * @implNote The total number of iterations defaults to 50. The error threshold of the numerical scheme is 1e-15.
     */
    final public void align(@NonNull Collection<T> agents, @Nullable Predicate<T> filter,
                            @NonNull AlignmentMultiProbabilityClosure<T> closure,
                            double @NonNull [] targetProbabilityDistribution){
        weightedModel = isWeighted(agents);

        totalChoiceNumber = targetProbabilityDistribution.length;

        filteredAgentList = extractAgentList(agents, filter);
        totalAgentNumber = filteredAgentList.size();

        tempAgents = new double[totalAgentNumber];

        weights = extractWeights();
        total = weights == null ? 1.0d * totalAgentNumber : sum(weights);

        probabilities = new double[totalAgentNumber][totalChoiceNumber];

        for (var agentId = 0; agentId < totalAgentNumber; agentId++)
            probabilities[agentId] = closure.getProbability(filteredAgentList.get(agentId));

        if (weightedModel)// todo does changing this array changes in probs changes the original one?
            for (var agentId = 0; agentId < totalAgentNumber; agentId++)
                for (int choice = 0; choice < totalChoiceNumber; choice++)
                    probabilities[agentId][choice] *= weights[agentId];

        validateInputData(targetProbabilityDistribution, probabilities, weights);// assume it works for now

        targetShare = new double[totalChoiceNumber]; // expected number of agent in every state, can be > 1

        gammaValues = new double[totalChoiceNumber];
        alphaValues = new double[totalAgentNumber];

        for(var choice = 0; choice < totalChoiceNumber; choice++)
            targetShare[choice] = targetProbabilityDistribution[choice] * total;

        probSumOverAgents = new double[totalChoiceNumber];

        double error;
        short iteration;
        for (iteration = 0; iteration < 50 ; iteration++) {// todo check that this is enough
            probabilityAdjustmentCycle(probSumOverAgents, targetShare, weights, tempAgents, probabilities);
            error = KullbackLeiblerDivergence(targetProbabilityDistribution, probSumOverAgents); // fixme renormalize probsum back to probdistribution
            if (error <= 1e-15)
                break;
        }
        throwDivergenceError(error, errorThreshold, total, totalAgentNumber, count);
        correctProbabilities(closure, filteredAgentList, weights, probabilities);
    }

    /**
     * Checks if all agents have corresponding weights, i.e., {@link Weight} implemented.
     * @param agentCollection A collection of objects representing agents.
     * @return {@code true} when all of them have weights, {@code false} if none of them have.
     * @throws InputMismatchException When the input collection contains both weighted and non-weighted samples.
     * @implNote Introduced to avoid all the hassle with multiple classes.
     */
    boolean isWeighted(@NotNull Collection<T> agentCollection) {
        if (agentCollection.stream().allMatch((o) -> o instanceof Weight)) return true;
        else if (agentCollection.stream().noneMatch((o) -> o instanceof Weight)) return false;
        else throw new InputMismatchException("Provided collection of agents contains " +
                    "both weighted and non-weighted samples.");
    }

    /**
     * Extracts weights - if any - from individual agents and stores them in a single array.
     * @return An array with weights or {@code null}.
     */
    double @Nullable [] extractWeights() {
        int l = filteredAgentList.size();
        val w = weightedModel ? new double[l] : null;

        if (weightedModel) for (int i = 0; i < l; i++) w[i] = ((Weight) filteredAgentList.get(i)).getWeight();
        return w;
    }

    /**
     * Does a basic input data validation. Sees that probabilities are in range in [0,1], their sum is not greater than
     * 1; also checks that weights and precision are strictly positive.
     * @param targetShare Probabilities of outcomes of an event.
     * @param maxNumberIterations The number of iterations the method goes through.
     * @param precision The precision of the method.
     * @param weights Weights of agents.
     * @throws AssertionError When any of the checks fail.
     * @throws NullPointerException When {@code targetShare} or {@code weights} or both are null.
     * @throws IllegalArgumentException When sanity checks fail: {@code targetShare} values are out of range [0,1],
     *                                  {@code sum(targetShare) > 1}, {@code maxNumberIterations == 0},
     *                                  {@code precision <= 0}, NaN, or Inf, any element of {@code weights} is
     *                                  {@code <= 0}, NaN, or Inf.
     */
    final void validateInputData(final double [] targetShare, final int maxNumberIterations, final double [][] probs,
                                 final double precision, final double[] weights){
        if (targetShare.length < 2)
            throw new IllegalArgumentException("The number of outcomes must be at least 2.");
//fixme targetProbabilityDistribution is an actual probabilities distribution!!!! all elements are between zero and one

        for (var v : targetShare)
            if (v < 0. || v > 1.)
                throw new IllegalArgumentException("Each probability value must lie in [0,1].");
        if (sum(targetShare) > 1.)
            throw new IllegalArgumentException("Sum of all outcomes must be 1 by definition.");
        if (maxNumberIterations == 0)
            throw new IllegalArgumentException("The method has to go at least through one iteration.");
        if (precision <= 0. || isNaN(precision) || isInfinite(precision))
            throw new IllegalArgumentException("The precision of the scheme has be greater than 0, but finite");

        for (var weight : weights)
            if (weight <= 0. || isNaN(weight) || isInfinite(weight))
                throw new IllegalArgumentException("Agent's weight cannot be <= 0, NaN, or Inf.");
    }

    /**
     * Throws an error message when the method doesn't converge to the expected solution of the problem within a given
     * range of iterations. This message is optional (conditional), however, additional sanity checks are introduced
     * by default. If at least one of the input parameters is NaN of Inf, something went wrong earlier as these values
     * make no numerical sense and should not be here in any case.
     * @param precisionValue The accuracy of the method.
     * @param warningsOn A boolean flag that switches on/off the warning message.
     * @param errorValue Actual error.
     * @param errorLevel Expected error.
     * @param sampleSize Number of agents.
     * @param iterations Total number of passed iterations at the moment of termination.
     * @param totalAgentWeight The sum of all weights in a given subpopulation.
     * @throws ArithmeticException When the main iterative condition is not satisfied; when double values are NaN, Inf.
     * @throws IllegalArgumentException When parameters are out of the acceptable range.
     */
    final void throwDivergenceError(final double precisionValue, final boolean warningsOn,
                                    final double errorValue, final double errorLevel, final double totalAgentWeight,
                                    final int sampleSize, final int iterations){
        val pv = isNaN(precisionValue) || isInfinite(precisionValue);
        val ev = isNaN(errorValue) || isInfinite(errorValue);
        val el = isNaN(errorLevel) || isInfinite(errorLevel);
        val aw = isNaN(totalAgentWeight) || isInfinite(totalAgentWeight);
        if (pv || ev || el || aw)
            throw new ArithmeticException("Sanity check fails.");
        if (precisionValue <= 0. || errorValue <= 0. || errorLevel <= 0. || totalAgentWeight <= 0. ||
                sampleSize < 1 || iterations < 0)
            throw new IllegalArgumentException("Values of parameters are out of range.");

        if(errorValue >= errorLevel && warningsOn) {
            String className = this.getClass().getCanonicalName();
            throw new ArithmeticException(format("""
                WARNING: The %s align() method terminated with an error of %f, which has a greater magnitude than the
                 precision bounds of +/-%f. The size of the filtered agent collection is %d and the number of iterations
                 was %d. Check the results of the %s alignment to ensure that alignment is good enough for the purpose
                 in question, or consider increasing the maximum number of iterations or the precision!
                """, className, errorValue/totalAgentWeight, precisionValue, sampleSize, iterations, className));
        }
    }

    /**
     * Scales down {@link #getProbabilities()} to 're-normalise', as it was previously scaled up by weight. Replaces individual
     * probabilities with the aligned probabilities {@link #getProbabilities()}.
     * @param closure An instance of implementation of
     *                {@link microsim.alignment.multiple.AlignmentMultiProbabilityClosure}.
     * @param fa A list of filtered agents.
     * @param w Weights of agents.
     * @param probabilities Probabilities of the subpopulation.
     */
    final void correctProbabilities(final @NotNull AlignmentMultiProbabilityClosure<T> closure,
                                    final @NotNull List<T> fa, final double @Nullable [] w,
                                    final double @NotNull [] @NotNull [] probabilities){
        if (w != null) for (var i = 0; i < totalAgentNumber; i++)
                for(var choice = 0; choice < totalChoiceNumber; choice++) probabilities[i][choice] /= w[i];// zero weights are filtered out?

        for (var i = 0; i < totalAgentNumber; i++) closure.align(fa.get(i), probabilities[i]);
    }

    /**
     * Adjusts the probabilities via consecutive use of {@link #executeGammaTransform} and
     * {@link #executeAlphaTransform}.
     */
    final void probabilityAdjustmentCycle(){
        generateGammaValues();
        executeGammaTransform();

        generateAlphaValues();
        executeAlphaTransform();
    }

    /**
     * Generates an array with gamma values.
     * @implSpec {@code agentSizeArray} is introduced to avoid extra memory handling as this method is called during
     *           every iteration.
     */
    final void generateGammaValues(){
        for(var choice = 0; choice < totalChoiceNumber; choice++) {
            for (var agentId = 0; agentId < totalAgentNumber; agentId++)
                agentSizeArray[agentId] = probabilities[agentId][choice];
            gamma[choice] = targetSum[choice] / sum(agentSizeArray);// division by zero
        }
    }

    /**
     * Generates corresponding alpha values.
     */
    final void generateAlphaValues() {
        if (weights == null) Arrays.fill(alphaValues, 1.0);
        else System.arraycopy(weights, 0, alphaValues, 0, totalAgentNumber);
        for (var agentId = 0; agentId < totalAgentNumber; agentId++) alphaValues[agentId] /= probSumOverChoices[agentId];
        // division by zero
    }

    /**
     * Gamma transform of the probabilities. It scales all columns by corresponding elements of {@code gammaValues} in
     * such a way that the total sum of every column matches the target value.
     */
    final void executeGammaTransform(){
        for (var g : gammaValues) assert g > 0 : "Gamma values must be non-negative.";
        // does it have to be > or >=
        // fixme how does the scheme behave when we have at least one 0 in the target distribution?// check for nan int subnormals?

        for(var agentId = 0; agentId < totalAgentNumber; agentId++) {
            for (var choice = 0; choice < totalChoiceNumber; choice++)
                probabilities[agentId][choice] *= gammaValues[choice];
            probSumOverChoices[agentId] = sum(probabilities[agentId]);
        }
    }

    /**
     * Alpha transform of the probabilities. Each row gets multiplied by the corresponding coefficient {@code alpha} so
     * that the sum of all elements in the row adds up exactly to 1 when there is no weights or corresponding weight
     * otherwise.
     * In addition, calculates the sum of probabilities over agents.
     */
    final void executeAlphaTransform(){
        for(var agentId = 0; agentId < totalAgentNumber; agentId++)
            for (var choice = 0; choice < totalChoiceNumber; choice++)
                probabilities[agentId][choice] *= alphaValues[agentId]; // scale it first

        for (var choice = 0; choice < totalChoiceNumber; choice++) {
            probSumOverAgents[choice] = 0.;
            for (var agentId = 0; agentId < totalAgentNumber; agentId++) // fixme old sum was extremely odd, check it again
                probSumOverAgents[choice] = sum(probSumOverAgents[choice], probabilities[agentId][choice]);// fixme not the most accurate sum
        }
    }
}
