package microsim.statistics.weighted;

/**
 * Used by statistical object to access array of long values.
 */
public interface WeightedLongArraySource {
	/**
	 * Return the currently cached array of long values.
	 * @return An array of double or a null pointer if the source is empty.
	 */
	long[] getLongArray();

	double[] getWeights();
}
