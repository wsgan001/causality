package org.cbio.causality.analysis;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.NormalDistribution;
import org.apache.commons.math.distribution.NormalDistributionImpl;
import org.cbio.causality.model.Alteration;
import org.cbio.causality.model.AlterationPack;
import org.cbio.causality.util.ArrayUtil;
import org.cbio.causality.util.Histogram;
import org.cbio.causality.util.Summary;

import java.util.*;

/**
 * @author Ozgun Babur
 */
public class Outlier
{
	/**
	 * Gets the boolean array marking hyper-altered or hypo-altered samples, i.e. total alteration
	 * count is at top ratio.
	 * @param genes
	 * @return
	 */
	public static boolean[] getOutlierAltered(Collection<AlterationPack> genes, Alteration type)
	{
		try
		{
			final Map<Integer, Integer> sample2Cnt = new HashMap<Integer, Integer>();
			int size = genes.iterator().next().getSize();

//			Histogram h = new Histogram(50);

			for (int i = 0; i < size; i++)
			{
				int cnt = 0;
				for (AlterationPack gene : genes)
				{
					if (gene.get(type)[i].isAltered()) cnt++;
				}
//				h.count(cnt);
				sample2Cnt.put(i, cnt);
			}

	//		h.print();

			List<Integer> samples = new ArrayList<Integer>(sample2Cnt.keySet());
			Collections.sort(samples, new Comparator<Integer>()
			{
				@Override
				public int compare(Integer o1, Integer o2)
				{
					return sample2Cnt.get(o1).compareTo(sample2Cnt.get(o2));
				}
			});

			boolean[] outlier = new boolean[samples.size()];

			int i;
			for (i = samples.size(); i > 0; i--)
			{
				if (isRightOutlier(samples, sample2Cnt, i)) outlier[samples.get(i-1)] = true;
				else break;
			}

			System.out.println("original sample size: " + outlier.length);
			int ro = ArrayUtil.countValue(outlier, true);
			System.out.println("Right outlier: " + ro);

			int rightBorder = i-1;
			while(i > 0) outlier[samples.get(--i)] = false;

			for (i = 0; i < rightBorder; i++)
			{
				if (isLeftOutlier(samples, sample2Cnt, i, rightBorder)) outlier[samples.get(i)] = true;
				else break;
			}

			System.out.println("Left outliers: " + (ArrayUtil.countValue(outlier, true) - ro));

			System.out.println("Outlier ratio: " +
				(ArrayUtil.countValue(outlier, true) / (double) samples.size()));

			return outlier;
		}
		catch (MathException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	private static boolean isRightOutlier(List<Integer> samples, Map<Integer, Integer> cnts, int test) throws MathException
	{
		double[] v = new double[test];
		for (int i = 0; i < test; i++)
		{
			v[i] = cnts.get(samples.get(i));
		}

		return lastIsOutlier(v);
	}

	private static boolean isLeftOutlier(List<Integer> samples, Map<Integer, Integer> cnts,
		int test, int rightBorder) throws MathException
	{
		double[] v = new double[rightBorder - test + 1];
		for (int i = 0; i < v.length; i++)
		{
			v[i] = cnts.get(samples.get(i + test));
		}

		return firstIsOutlier(v);
	}

	private static boolean lastIsOutlier(double[] v) throws MathException
	{
		double mean = Summary.mean(v);
		double sd = Summary.stdev(v);

		if (sd == 0) return true;

		NormalDistribution dist = new NormalDistributionImpl(mean, sd);
		double p = 1 - dist.cumulativeProbability(v[v.length - 1]);

		double exp = p * v.length;

		return exp < 0.5;
	}

	private static boolean firstIsOutlier(double[] v) throws MathException
	{
		double mean = Summary.mean(v);
		double sd = Summary.stdev(v);

		NormalDistribution dist = new NormalDistributionImpl(mean, sd);
		double p = dist.cumulativeProbability(v[0]);

		double exp = p * v.length;

		return exp < 0.5;
	}
}
