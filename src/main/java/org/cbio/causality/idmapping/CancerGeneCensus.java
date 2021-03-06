package org.cbio.causality.idmapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * @author Ozgun Babur
 */
public class CancerGeneCensus
{
	private static Map<String, String> sym2chr;

	public static Set<String> getAllSymbols()
	{
		return sym2chr.keySet();
	}

	public static boolean isCancerGene(String sym)
	{
		return sym2chr.containsKey(sym);
	}

	public static Set<String> getSymbolsOfChromosome(String chr)
	{
		Set<String> set = new HashSet<String>();
		for (String sym : getAllSymbols())
		{
			if (sym2chr.containsKey(sym))
			{
				String c = sym2chr.get(sym);
				String no = c.split("p")[0].split("q")[0];

				String desNo = chr.split("p")[0].split("q")[0];

				if (no.equals(desNo) && c.contains(chr))
				{
					set.add(sym);
				}
			}
		}
		return set;
	}

	static
	{
		sym2chr = new HashMap<String, String>();

		BufferedReader reader = new BufferedReader(new InputStreamReader(
			HGNC.class.getResourceAsStream("Census_allWed Feb 24 19-39-08 2016.tsv")));

		try
		{
			reader.readLine(); //skip header
			for (String line = reader.readLine(); line != null; line = reader.readLine())
			{
				String[] token = line.split("\t");
				String sym = token[0];
				String chr = token[4];
				sym2chr.put(sym, chr);
			}

			reader.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public static void main(String[] args)
	{
//		System.out.println(getAllSymbols().contains("LLP"));

//		Set<String> genes = HGNC.getSymbolsOfChromosome("1q");
		Set<String> genes = new HashSet<String>(Arrays.asList(
			"POU5F1B",
			"SBNO1" ,
			"CYLD" ,
			"TOX" ,
			"SAV1" ,
			"CLEC12A" ,
			"SMAD4" ,
			"BBX" ,
			"HCFC2" ,
			"ZC3H18" ,
			"ANKRD24" ,
			"FBXW7" ,
			"ITPR2" ,
			"CHD4" ,
			"OR2T35" ,
			"ABCD1"));
		genes.retainAll(getAllSymbols());
		System.out.println("genes = " + genes);
	}
}
