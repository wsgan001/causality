package org.cbio.causality.util;

/**
 * Common functions related to arrays.
 *
 * @author Ozgun Babur
 */
public class ArrayUtil
{
	public static boolean[] negate(boolean[] posit)
	{
		boolean[] neg = new boolean[posit.length];
		for (int i = 0; i < posit.length; i++)
		{
			neg[i] = !posit[i];
		}
		return neg;
	}

	public static int countValue(boolean[] b, boolean val)
	{
		int cnt = 0;
		for (boolean v : b)
		{
			if (v == val) cnt++;
		}
		return cnt;
	}
}
