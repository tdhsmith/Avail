/*
 * Statistic.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.avail.performance;

import com.avail.AvailRuntimeConfiguration;
import com.avail.interpreter.Interpreter;

import javax.annotation.Nullable;

import static java.lang.Math.sqrt;
import static java.lang.String.format;

/**
 * A {@code PerInterpreterStatistic} is an incremental, summarized recording of
 * a set of integral values and times.  It is synchronized, although the typical
 * usage is that it will only be written by a single {@link Thread} at a time,
 * and read by another {@link Thread} only rarely.
 *
 * <p>If you want to record samples from multiple processes, use a Statistic,
 * which holds a PerInterpreterStatistic for up to {@link
 * AvailRuntimeConfiguration#maxInterpreters} separate Threads to access,
 * without any locks.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class PerInterpreterStatistic
implements Comparable<PerInterpreterStatistic>
{
	/** The number of samples recorded so far. */
	private long count;

	/** The smallest sample yet encountered. */
	private double min;

	/** The largest sample yet encountered. */
	private double max;

	/** The average of all samples recorded so far. */
	private double mean;

	/**
	 * The sum of the squares of differences from the current mean.  This is
	 * more numerically stable in calculating the variance than the sum of
	 * squares of the samples.  See <cite> Donald E. Knuth (1998). The Art
	 * of Computer Programming, volume 2: Seminumerical Algorithms, 3rd
	 * edn., p. 232. Boston: Addison-Wesley</cite>.  That cites a 1962 paper
	 * by <cite>B. P. Welford</cite>.
	 */
	private double sumOfDeltaSquares;

	/**
	 * Construct a new statistic with the given values.
	 *
	 * @param count The number of samples.
	 * @param min The minimum sample.
	 * @param max The maximum sample.
	 * @param mean The mean of the samples.
	 * @param sumOfDeltaSquares
	 *        The sum of squares of differences of the samples from the mean.
	 */
	PerInterpreterStatistic (
		final long count,
		final double min,
		final double max,
		final double mean,
		final double sumOfDeltaSquares)
	{
		this.count = count;
		this.min = min;
		this.max = max;
		this.mean = mean;
		this.sumOfDeltaSquares = sumOfDeltaSquares;
	}

	/**
	 * Create an empty statistic.
	 */
	PerInterpreterStatistic ()
	{
		this(0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, 0.0);
	}

	/** Default sort is descending by sum. */
	@Override
	public int compareTo (final @Nullable PerInterpreterStatistic otherStat)
	{
		// Compare by descending sums.
		assert otherStat != null;
		return Double.compare(otherStat.sum(), this.sum());
	}

	/**
	 * Return the number of samples that have been recorded.
	 *
	 * @return The sample count.
	 */
	public synchronized long count ()
	{
		return count;
	}

	/**
	 * Return the sum of the samples.  This is thread-safe, but may block if
	 * an update (or other read) is in progress.
	 *
	 * @return The sum of the samples.
	 */
	public synchronized double sum ()
	{
		return mean * count;
	}

	/**
	 * Answer the corrected variance of the samples.  This is the sum of squares
	 * of differences from the mean, divided by one less than the number of
	 * samples.  Fudge it for less than two samples, pretending the variance is
	 * zero rather than undefined.
	 *
	 * @return The Bessel-corrected variance of the samples.
	 */
	public synchronized double variance ()
	{
		return computeVariance(count, sumOfDeltaSquares);
	}

	/**
	 * Given a count of samples and the sum of squares of their differences from
	 * the mean, compute the variance.
	 *
	 * @param theCount
	 *        The number of samples.
	 * @param theSumOfDeltaSquares
	 *        The sum of the squares of distances from the mean of the samples.
	 * @return The statistical variance.
	 */
	private static double computeVariance (
		final long theCount,
		final double theSumOfDeltaSquares)
	{
		return theCount <= 1L ? 0.0 : theSumOfDeltaSquares / (theCount - 1L);
	}

	/**
	 * Answer the Bessel-corrected ("unbiased") standard deviation of these
	 * samples.  This assumes the samples are not the entire population, and
	 * therefore the distances of the samples from the mean are really the
	 * distances from the sample mean, not the actual population mean.
	 *
	 * @return The Bessel-corrected standard deviation of the samples.
	 */
	public double standardDeviation ()
	{
		return sqrt(variance());
	}

	/**
	 * Describe this statistic as though its samples are durations in
	 * nanoseconds.
	 *
	 * @param builder
	 *        Where to describe this statistic.
	 * @param unit
	 *        The {@link ReportingUnit units} to use to report the statistic.
	 */
	public void describeOn (
		final StringBuilder builder,
		final ReportingUnit unit)
	{
		final long capturedCount;
		final double capturedMean;
		final double capturedSumOfDeltaSquares;
		// Read multiple fields coherently.
		synchronized (this)
		{
			capturedCount = count;
			capturedMean = mean;
			capturedSumOfDeltaSquares = sumOfDeltaSquares;
		}
		final double standardDeviation =
			sqrt(computeVariance(capturedCount, capturedSumOfDeltaSquares));
		builder.append(
			unit.describe(
				capturedCount, capturedMean, 0.0, false));
		builder.append(format(" [N=%,10d] ", capturedCount));
		// We could use a chi (x) with a line over it, "\u0304x", but this makes
		// the text area REALLY SLOW.  Like, over ten seconds to insert a report
		// from running Avail for five seconds.  So we spell out "mean".
		builder.append("(mean=");
		builder.append(unit.describe(1, capturedMean, standardDeviation, true));
		builder.append(")");
	}

	/**
	 * Record a new sample, updating any cumulative statistical values.  This is
	 * thread-safe.  However, the locking cost should be exceedingly low if a
	 * {@link Statistic} is used to partition an array of {@link
	 * PerInterpreterStatistic}s by {@link Interpreter}.
	 *
	 * @param sample The sample value to record.
	 */
	public synchronized void record (final double sample)
	{
		count++;
		min = Math.min(sample, min);
		max = Math.max(sample, max);
		final double delta = sample - mean;
		mean += delta / count;
		sumOfDeltaSquares += delta * (sample - mean);
	}

	/**
	 * Add my information to another {@code PerInterpreterStatistic}.  This is
	 * thread-safe for the receiver, and assumes the argument does not need to
	 * be treated thread-safely.
	 *
	 * @param target The statistic to add the receiver to.
	 */
	synchronized void addTo (final PerInterpreterStatistic target)
	{
		final long newCount = target.count + count;
		if (newCount > 0)
		{
			final double delta = mean - target.mean;
			final double newMean =
				(target.count * target.mean + count * mean) / newCount;
			final double newSumOfDeltas =
				target.sumOfDeltaSquares + sumOfDeltaSquares
					+ (delta * delta / newCount) * target.count * count;
			// Now overwrite the target.
			target.count = newCount;
			target.min = Math.min(target.min, min);
			target.max = Math.max(target.max, max);
			target.mean = newMean;
			target.sumOfDeltaSquares = newSumOfDeltas;
		}
	}

	/**
	 * Reset this statistic as though no samples had ever been recorded.
	 */
	public synchronized void clear ()
	{
		count = 0;
		min = Double.POSITIVE_INFINITY;
		max = Double.NEGATIVE_INFINITY;
		mean = 0.0;
		sumOfDeltaSquares = 0.0;
	}
}
