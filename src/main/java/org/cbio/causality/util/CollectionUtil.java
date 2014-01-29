package org.cbio.causality.util;

import java.util.*;

/**
 * @author Ozgun Babur
 */
public class CollectionUtil
{
	public static List<String> getSortedList(final Map<String, Double> map)
	{
		List<String> list = new ArrayList<String>(map.keySet());
		Collections.sort(list, new Comparator<String>()
		{
			@Override
			public int compare(String o1, String o2)
			{
				Double v1 = map.get(o1);
				Double v2 = map.get(o2);

				if (v1.equals(v2)) return o1.compareTo(o2);
				return v1.compareTo(v2);
			}
		});

		return list;
	}

	public static int[] getVennCounts(Collection<?> col1, Collection<?> col2)
	{
		Set<?> set1 = new HashSet<Object>(col1);
		Set<?> set2 = new HashSet<Object>(col2);
		Set<?> inter = new HashSet<Object>(set1);
		inter.retainAll(set2);
		return new int[]{set1.size() - inter.size(), set2.size() - inter.size(), inter.size()};
	}

//	public static int[] getVennCounts(Collection<?> col1, Collection<?> col2, Collection<?> col3)
//	{
//		Set<?> set1 = new HashSet<Object>(col1);
//		Set<?> set2 = new HashSet<Object>(col2);
//		Set<?> set3 = new HashSet<Object>(col3);
//
//		Set<?> inter = new HashSet<Object>(set1);
//		inter.retainAll(set2);
//		inter.retainAll(set3);
//
//		Set<?> inter12 = new HashSet<Object>(set1);
//		inter12.retainAll(set2);
//
//		Set<?> inter13 = new HashSet<Object>(set1);
//		inter13.retainAll(set3);
//
//		Set<?> inter23 = new HashSet<Object>(set2);
//		inter23.retainAll(set3);
//
//		return new int[]{
//			set1.size() - inter12.size() - inter13.size() + inter.size(),
//			set2.size() - inter12.size() - inter23.size() + inter.size(),
//			set3.size() - inter13.size() - inter23.size() + inter.size(),
//			inter12.size() - inter.size(),
//			inter13.size() - inter.size(),
//			inter23.size() - inter.size(),
//			inter.size()};
//	}

	public static int[] getVennCounts(Collection<?>... col)
	{
		int size = col.length;
		Set<?>[] set = new Set[size];

		for (int i = 0; i < size; i++)
		{
			set[i] = new HashSet<Object>(col[i]);
		}

		int[] cnt = new int[(int) (Math.pow(2, size) - 1)];
		String[] bs = generateBinaryStrings(size);

		int x = 0;

		for (String s : bs)
		{
			Set<Set<?>> intersectSets = new HashSet<Set<?>>();
			Set<Set<?>> subtractSets = new HashSet<Set<?>>();

			for (int k = 0; k < size; k++)
			{
				if (s.length() < k + 1 || s.charAt(s.length() - 1 - k) == '0')
				{
					subtractSets.add(set[k]);
				}
				else intersectSets.add(set[k]);
			}

			boolean first = true;
			Set<Object> select = new HashSet<Object>();
			for (Set<?> inset : intersectSets)
			{
				if (first)
				{
					select.addAll(inset);
					first = false;
				}
				else select.retainAll(inset);
			}
			for (Set<?> subset : subtractSets)
			{
				select.removeAll(subset);
			}

			cnt[x++] = select.size();
		}
		return cnt;
	}

	private static String[] generateBinaryStrings(int n)
	{
		List<String> list = new ArrayList<String>();
		for (int i = 1; i < Math.pow(2, n); i++)
		{
			list.add(Integer.toBinaryString(i).intern());
		}

		Collections.sort(list, new Comparator<String>()
		{
			@Override
			public int compare(String o1, String o2)
			{
				int c1 = count1inBinaryString(o1);
				int c2 = count1inBinaryString(o2);

				if (c1 != c2) return new Integer(c1).compareTo(c2);

				for (int i = Math.min(o1.length(), o2.length()) - 1; i >= 0 ; i--)
				{
					boolean b1 = o1.charAt(i) == '1';
					boolean b2 = o2.charAt(i) == '1';

					if (b1 != b2)
					{
						return b1 ? -1 : 1;
					}
				}
				return 0;
			}
		});

		return list.toArray(new String[list.size()]);
	}

	private static int count1inBinaryString(String s)
	{
		int cnt = 0;
		for (int i = 0; i < s.length(); i++)
		{
			if (s.charAt(i) == '1') cnt++;
		}
		return cnt;
	}

	public static String[] getSetNamesArray(int n)
	{
		String[] bin = generateBinaryStrings(n);
		String[] names = new String[bin.length];

		int x = 0;
		for (String s : bin)
		{
			String name = "";
			for (int i = 0; i < s.length(); i++)
			{
				if (s.charAt(s.length() - 1 - i) == '1') name += (char) (i + 65);
			}
			names[x++] = name;
		}

		return names;
	}
}
