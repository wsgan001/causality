package org.cbio.causality.data.portal;

import org.cbio.causality.idmapping.HGNC;
import org.cbio.causality.util.Download;
import org.cbio.causality.util.FileUtil;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

/**
 * @author Ozgun Babur
 */
public class BroadAccessor
{
	private static final String BROAD_DIR = "broad-data/";
	private static final String CACHED_STUDIES_FILE = "studies.txt";
	private static String cacheDir;
	private static String broadDataURL = "http://gdac.broadinstitute.org/runs/analyses__latest/";
	private static List<String> studyCodes;
	private static final String MUTSIG_ANALYSIS_SUBSTR = "MutSigNozzleReportMerged.Level_4";
	private static final String GISTIC_ANALYSIS_SUBSTR = "Gistic2.Level_4";
	public static final String[] mutsigPartialFileNames = new String[]{".sig_genes.", "sig_genes."};
	public static final String gisticAmpPartialName = "amp_genes";
	public static final String gisticDelPartialName = "del_genes";

	public static void setCacheDir(String dir)
	{
		cacheDir = dir;
	}

	public static void setBroadDataURL(String url)
	{
		broadDataURL = url;
		studyCodes = null;
	}

	public static String getBroadDataURL()
	{
		if (broadDataURL.endsWith("latest/"))
		{
			try
			{
				URL url = new URL(broadDataURL);
				URLConnection con = url.openConnection();
				BufferedReader reader = new BufferedReader(
					new InputStreamReader(con.getInputStream()));

				for (String line = reader.readLine(); line != null; line = reader.readLine())
				{
					if (line.startsWith("<h3>") && line.endsWith("analyses  Run</h3>"))
					{
						String date = line.substring(line.indexOf(">") + 1, line.indexOf(" "));
						System.out.println("date = " + date);
						broadDataURL = broadDataURL.substring(0, broadDataURL.lastIndexOf("l")) +
							date + "/";

						break;
					}
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		return broadDataURL;
	}

	public static List<String> getStudyCodes()
	{
		if (studyCodes == null)
		{
			studyCodes = readStudiesFromCache();

			if (studyCodes == null)
			{
				studyCodes = new ArrayList<String>(30);
				try
				{
					URL url = new URL(getBroadDataURL() + "ingested_data.tsv");

					URLConnection con = url.openConnection();

					BufferedReader reader = new BufferedReader(
						new InputStreamReader(con.getInputStream()));

					for (String line = reader.readLine(); line != null; line = reader.readLine())
					{
						if (line.isEmpty() || line.startsWith("#")
							|| line.startsWith("Tumor") || line.startsWith("Totals")) continue;

						String study = line.substring(0, line.indexOf("\t"));
						if (bothMutsigAndGisticAvailable(study)) studyCodes.add(study);
					}
					reader.close();

					// Keep only the ones that are available in cBioPortal

					Set<String> available = new HashSet<String>();
					CBioPortalAccessor acc = new CBioPortalAccessor();
					for (CancerStudy cancerStudy : acc.getCancerStudies())
					{
						if (cancerStudy.getStudyId().endsWith("_tcga"))
						{
							available.add(cancerStudy.getStudyId().substring(0,
								cancerStudy.getStudyId().indexOf("_")).toUpperCase());
						}
					}
					studyCodes.retainAll(available);
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}

				if (!studyCodes.isEmpty())
				{
					cacheStudies(studyCodes);
				}
			}
		}
		return studyCodes;
	}

	private static List<String> readStudiesFromCache()
	{
		try
		{
			if (!new File(getStudiesFileName()).exists()) return null;

			List<String> studies = new ArrayList<String>();
			BufferedReader reader = new BufferedReader(new FileReader(getStudiesFileName()));

			// Read date

			String date = reader.readLine() + "/";
			if (!broadDataURL.endsWith(date)) broadDataURL = broadDataURL.substring(0,
				broadDataURL.lastIndexOf("_") + 1) + date;

			for (String line = reader.readLine(); line != null; line = reader.readLine())
			{
				if (!line.isEmpty()) studies.add(line);
			}

			reader.close();
			return studies;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	private static void cacheStudies(List<String> studies)
	{
		try
		{
			BufferedWriter writer = new BufferedWriter(
				new FileWriter(getStudiesFileName()));

			// write analysis date
			String s = getBroadDataURL();
			s = s.substring(s.indexOf("__") + 2, s.lastIndexOf("/"));
			writer.write(s + "\n");

			for (String study : studies)
			{
				writer.write(study + "\n");
			}

			writer.close();

		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private static String getStudiesFileName()
	{
		return getBroadCacheDir() + CACHED_STUDIES_FILE;
	}

	private static String getBroadDateString()
	{
		String s = getBroadDataURL();
		s = s.substring(s.indexOf("__") + 2, s.lastIndexOf("/"));
		s = s.replaceAll("_", "");
		return s;
	}

	private static String getBroadDataURL(String study)
	{
		return getBroadDataURL() + "data/" + study + "/" + getBroadDateString() + "/";
	}

	private static String getBroadCacheDir()
	{
		if (cacheDir == null)
		{
			String s = CBioPortalManager.getCacheDir() + File.separator + BROAD_DIR;
			s = s.replaceAll("//", "/");
			File f = new File(s);
			if (!f.exists()) f.mkdirs();
			return s;
		}
		return cacheDir;
	}

	private static List<String> getBroadAnalysisFileNames(String study)
	{
		List<String> list = new ArrayList<String>(30);
		try
		{
			URL url = new URL(getBroadDataURL(study));

			URLConnection con = url.openConnection();
			BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
			for (String line = reader.readLine(); line != null; line = reader.readLine())
			{
				String start = "<li><a href=\"";
				if (line.startsWith(start))
				{
					String file = line.substring(start.length(), line.indexOf("\">"));
					list.add(file);
				}
			}
			reader.close();
		}
		catch (IOException e)
		{
			System.out.println(e);
		}
		return list;
	}

	private static String getGisticFileName(List<String> list)
	{
		for (String s : list)
		{
			if (s.contains(GISTIC_ANALYSIS_SUBSTR)) return s;
		}
		return null;
	}

	private static String getMutsigFileName(List<String> list)
	{
		for (String s : list)
		{
			if (s.contains(MUTSIG_ANALYSIS_SUBSTR)) return s;
		}
		return null;
	}

	private static boolean bothMutsigAndGisticAvailable(String study)
	{
		List<String> analysisFiles = getBroadAnalysisFileNames(study);
		return !analysisFiles.isEmpty() &&
			getGisticFileName(analysisFiles) != null &&
			getMutsigFileName(analysisFiles) != null;
	}

	private static String getCachedGisticAmpFileName(String study)
	{
		return getBroadCacheDir() + study + "-gistic-amp.txt";
	}
	private static String getCachedGisticDelFileName(String study)
	{
		return getBroadCacheDir() + study + "-gistic-del.txt";
	}

	private static String getCachedMutsigFileName(String study)
	{
		return getBroadCacheDir() + study + "-mutsig.txt";
	}

	private static String getTempFileName()
	{
		return getBroadCacheDir() + "temp.tar.gz";
	}

	private static void deleteTempFile()
	{
		new File(getBroadCacheDir() + "temp.tar.gz").delete();
	}

	private static boolean downloadGistic(String study, List<String> analysisFileNames)
	{
		String s = getGisticFileName(analysisFileNames);
		if (s != null)
		{
			if (Download.downloadAsIs(getBroadDataURL(study) + s, getTempFileName()))
			{
				if (FileUtil.extractEntryContainingNameInTARGZFile(getTempFileName(),
					gisticAmpPartialName, getCachedGisticAmpFileName(study)) &&
					FileUtil.extractEntryContainingNameInTARGZFile(getTempFileName(),
						gisticDelPartialName, getCachedGisticDelFileName(study)))
				{
					deleteTempFile();
					return true;
				}
			}
		}
		return false;
	}

	private static boolean downloadMutsig(String study, List<String> analysisFileNames)
	{
		String s = getMutsigFileName(analysisFileNames);
		if (s != null)
		{
			if (Download.downloadAsIs(getBroadDataURL(study) + s, getTempFileName()))
			{
				for (String name : mutsigPartialFileNames)
				{
					if (FileUtil.extractEntryContainingNameInTARGZFile(getTempFileName(), name,
						getCachedMutsigFileName(study)))
					{
						deleteTempFile();
						return true;
					}
				}
			}
		}
		return false;
	}

	public static Set<String> getMutsigGenes(String study, double qvalThr)
	{
		if (!getStudyCodes().contains(study))
		{
			System.out.println("Study " + study + " is unknown.");
			return Collections.emptySet();
		}

		Set<String> genes = new HashSet<String>();
		String file = getCachedMutsigFileName(study);
		if (!new File(file).exists())
		{
			downloadMutsig(study, getBroadAnalysisFileNames(study));
		}
		if (new File(file).exists())
		{
			genes.addAll(readGenesFromMutsig(study, qvalThr));
		}

		return genes;
	}

	public static Set<String> getGisticGenes(String study, double qvalThr)
	{
		if (!getStudyCodes().contains(study))
		{
			System.out.println("Study " + study + " is unknown.");
			return Collections.emptySet();
		}

		Set<String> genes = new HashSet<String>();
		List<Set<String>> sets = getGisticGeneSets(study, qvalThr);
		for (Set<String> set : sets)
		{
			genes.addAll(set);
		}
		return genes;
	}

	public static List<Set<String>> getGisticGeneSets(String study, double qvalThr)
	{
		if (!getStudyCodes().contains(study))
		{
			System.out.println("Study " + study + " is unknown.");
			return Collections.emptyList();
		}

		String file = getCachedGisticAmpFileName(study);
		if (!new File(file).exists())
		{
			downloadGistic(study, getBroadAnalysisFileNames(study));
		}
		if (new File(file).exists())
		{
			return readGenesFromGistic(study, qvalThr);
		}
		return null;
	}

	private static List<Set<String>> readGenesFromGistic(String study, double qvalThr)
	{
		List<Set<String>> list = new ArrayList<Set<String>>();

		String s = FileUtil.getFileContent(getCachedGisticAmpFileName(study));
		readGisticData(list, s, qvalThr);

		s = FileUtil.getFileContent(getCachedGisticDelFileName(study));
		readGisticData(list, s, qvalThr);
		return list;
	}

	private static Set<String> readGenesFromMutsig(String study, double qvalThr)
	{
		Set<String> set = new HashSet<String>();
		String s = FileUtil.getFileContent(getCachedMutsigFileName(study));

		for (String line : s.split("\n"))
		{
			if (line.startsWith("rank")) continue;

			String[] token = line.split("\t");

			double qval = token[token.length - 1].startsWith("<") ? 0:
				Double.parseDouble(token[token.length - 1]);

			if (qval < qvalThr)
			{
				String symbol = HGNC.getSymbol(token[1]);
				if (symbol != null) set.add(symbol);
			}
		}

		System.out.println("mutsig set = " + set.size());
		return set;
	}

	private static void readGisticData(List<Set<String>> list, String s, double qvalThr)
	{
		String[] line = s.split("\n");

		String[] qvalStr = line[2].split("\t");
		double[] qvals = new double[qvalStr.length - 1];
		for (int i = 0; i < qvals.length; i++)
		{
			qvals[i] = Double.parseDouble(qvalStr[i + 1]);
		}

		Set<String>[] set = new Set[qvals.length];
		for (int i = 0; i < set.length; i++)
		{
			set[i] = new HashSet<String>();
		}

		for (int i = 4; i < line.length; i++)
		{
			String[] gene = line[i].split("\t");

			for (int j = 1; j < gene.length; j++)
			{
				if (qvals[j - 1] >= qvalThr) continue;

				String symbol = HGNC.getSymbol(gene[j]);
				if (symbol != null) set[j - 1].add(symbol);
			}
		}
		for (Set<String> mem : set)
		{
			if (!mem.isEmpty()) list.add(mem);
		}
	}

	public static void main(String[] args)
	{
		List<String> codes = getStudyCodes();
		getGisticGenes("COADREAD", 0.05);
		getMutsigGenes("COADREAD", 0.05);
	}
}