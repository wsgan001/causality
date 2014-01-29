package org.cbio.causality.binintanalysis;

import org.biopax.paxtools.pattern.miner.SIFEnum;
import org.cbio.causality.analysis.*;
import org.cbio.causality.data.drug.DrugData;
import org.cbio.causality.data.go.GO;
import org.cbio.causality.data.portal.BroadAccessor;
import org.cbio.causality.data.portal.CBioPortalAccessor;
import org.cbio.causality.data.portal.ExpDataManager;
import org.cbio.causality.model.Alteration;
import org.cbio.causality.model.AlterationPack;
import org.cbio.causality.model.Change;
import org.cbio.causality.network.MSigDBTFT;
import org.cbio.causality.network.PathwayCommons;
import org.cbio.causality.network.SPIKE;
import org.cbio.causality.util.*;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * @author Ozgun Babur
 */
public class ExpressionAffectedTargetFinder
{
	private static final String dir = "binint/ExpressionAffectedTargetFinder/";
	private Graph travSt;
	private Graph travExp;
	private Dataset1 dataset;
	CBioPortalAccessor portalAcc;
	ExpDataManager expMan;
	Map<String, Set<String>> mutatedUpstr;
	Map<String, Boolean> targetChange;
	Set<String> mutsig;
	double mutsigThr;
	int depth;

	private static final int MIN_GROUP_SIZE = 1;

	public static void main(String[] args) throws IOException
	{
		Graph graphSt = SPIKE.getGraph();
		Graph graphExp = PathwayCommons.getGraph(SIFEnum.CONTROLS_EXPRESSION_OF);
		graphExp.merge(MSigDBTFT.getGraph());

		double fdrThr = 0.05;
		Dataset1 dataset = Dataset1.GBM;
		double mutsigThr = 0.05;
		int depth = 2;

//		ExpressionAffectedTargetFinder finder = new ExpressionAffectedTargetFinder(
//			dataset, graphSt, graphExp, mutsigThr, depth);
//		List<String> res1 = finder.find(fdrThr);

//		Graph graphSt = PathwayCommons.getGraph(SIFEnum.CONTROLS_STATE_CHANGE_OF);

		ExpressionAffectedTargetFinder finder = new ExpressionAffectedTargetFinder(dataset, graphSt, graphExp, mutsigThr, depth);
		List<String> res2 = finder.find(fdrThr);

//		graphSt.merge(SPIKE.getGraph());
//
//		finder = new ExpressionAffectedTargetFinder(dataset, graphSt, graphExp, mutsigThr, depth);
//		List<String> res3 = finder.find(fdrThr);
//
//		int[] venn = CollectionUtil.getVennCounts(res1, res2, res3);
//		System.out.println(Arrays.toString(venn));
//		System.out.println(Arrays.toString(CollectionUtil.getSetNamesArray(3)));
	}

	public List<String> find(double fdrThr) throws IOException
	{
		Map<String, Double> result = calcInfluencePvals();

		System.out.println("pvals.size() = " + result.size());

		List<String> list = FDR.select(result, null, fdrThr);
		System.out.println("result size = " + list.size());

		generateResultDetails(result, list);

		return list;
	}

	private void generateResultDetails(Map<String, Double> result, List<String> list)
	{
		removeNonAffectors(list);
		System.out.println("non-effectors removed");

		generateInfluenceGraphs(list);
		System.out.println("influence graphs generated");

		Set<String> druggable = new HashSet<String>();

		for (String s : list)
		{
			if (targetChange.get(s) && !DrugData.getDrugs(s).isEmpty()) druggable.add(s);

			System.out.print(s + "\t" + (targetChange.get(s) ? "up" : "dw") + "\t" +
				FormatUtil.roundToSignificantDigits(result.get(s), 2) +
				"\t" + DrugData.getDrugs(s) + "\t");

			System.out.print(mutatedUpstr.get(s));
//			for (String up : finder.mutatedUpstr.get(s)) System.out.print(finder.getPath(up, s, finder.depth) + ", ");

			System.out.println();
		}

		for (GO.Namespace ns : GO.Namespace.values())
		{
			System.out.println("\n\n---------------------Go terms - " + ns.name());

			Map<String, Double> goMap = GO.getEnrichedTerms(
				new HashSet<String>(list), result.keySet(), ns);

			List<String> enrichedGO = FDR.select(goMap, null, 0.05);
			for (String go : enrichedGO)
			{
				System.out.println(go + "\t" +
					FormatUtil.roundToSignificantDigits(goMap.get(go), 2) + "\t" +
					GO.getMembers(go, ns));
			}
		}


		System.out.println("\n\n---------------------Drugs");
		Map<String, Set<String>> drugs = DrugData.getDrugs(druggable);
		for (String drug : DrugData.sortDrugs(drugs))
		{
			if (drugs.get(drug).size() > 1)
				System.out.println(drug + "\t" + drugs.get(drug) + "\tfdaAppr = " + DrugData.isFDAApproved(drug) + "\tcancer-drug = " + DrugData.isCancerDrug(drug));
		}
	}

	public ExpressionAffectedTargetFinder(Dataset1 dataset, Graph graphSt, Graph graphExp,
		double mutsigThr, int depth)
		throws IOException
	{
		this.dataset = dataset;
		this.mutsigThr = mutsigThr;
		this.depth = depth;
		this.travSt = graphSt;
		this.travExp = graphExp;
		loadData();
	}

	private void loadData() throws IOException
	{
		portalAcc = new CBioPortalAccessor(dataset.mutCnCallExpZ);
		expMan = new ExpDataManager(portalAcc.getGeneticProfileById(dataset.exp.profileID[0]),
			portalAcc.getCaseListById(dataset.exp.caseListID));

		mutsig = BroadAccessor.getMutsigGenes(dataset.code(), mutsigThr);
		Set<String> symbols = travSt.getSymbols();
		symbols.addAll(travExp.getSymbols());
		mutsig.retainAll(symbols);
		removeMutsigWithMissingData();
		System.out.println("mutsig in network = " + mutsig.size());
	}

	private void removeMutsigWithMissingData()
	{
		Iterator<String> iter = mutsig.iterator();
		while (iter.hasNext())
		{
			String ms = iter.next();

			AlterationPack alts = portalAcc.getAlterations(ms);

			if (alts == null) iter.remove();
			else if (alts.get(Alteration.MUTATION) == null ||
				alts.get(Alteration.COPY_NUMBER) == null ||
				alts.get(Alteration.EXPRESSION) == null) iter.remove();
		}
	}

	/**
	 * Gets the samples that the gene is either mutated or intact. In both cases it is expressed,
	 * and not copy number lost.
	 */
	private boolean[] selectSubset(Set<String> symbols, boolean mutated)
	{
		AlterationPack[] alts = new AlterationPack[symbols.size()];

		int i = 0;
		for (String symbol : symbols)
		{
			alts[i++] = portalAcc.getAlterations(symbol);
		}

		boolean[] x = new boolean[alts[0].getSize()];

		for (i = 0; i < x.length; i++)
		{
			x[i] = (mutated ? atLeastOneIsMutated(alts, i) : nothingMutated(alts, i)) &&
				copyNumberNotLost(alts, i) && expressionNotDown(alts, i);
		}

		return x;
	}

	private boolean atLeastOneIsMutated(AlterationPack[] alts, int index)
	{
		for (AlterationPack alt : alts)
		{
			if (alt.getChange(Alteration.MUTATION, index).isAltered()) return true;
		}
		return false;
	}

	private boolean nothingMutated(AlterationPack[] alts, int index)
	{
		for (AlterationPack alt : alts)
		{
			if (alt.getChange(Alteration.MUTATION, index) != Change.NO_CHANGE) return false;
		}
		return true;
	}

	private boolean copyNumberNotLost(AlterationPack[] alts, int index)
	{
		for (AlterationPack alt : alts)
		{
			Change ch = alt.getChange(Alteration.COPY_NUMBER, index);
//			if (ch == Change.INHIBITING || ch == Change.NO_DATA) return false;
			if (ch != Change.NO_CHANGE) return false;
		}
		return true;
	}

	private boolean expressionNotDown(AlterationPack[] alts, int index)
	{
		for (AlterationPack alt : alts)
		{
			Change ch = alt.getChange(Alteration.EXPRESSION, index);
//			if (ch == Change.INHIBITING || ch == Change.NO_DATA) return false;
			if (ch != Change.NO_CHANGE) return false;
		}
		return true;
	}

	private double calcDiffPval(String symbol, boolean[] set1, boolean[] set2)
	{
		double[] exp = expMan.get(symbol);

		if (exp == null) return Double.NaN;

		boolean[] cnUnchanged = getCopyNumberUnchanged(symbol);
		if (cnUnchanged == null) return Double.NaN;

		double[] vals1 = getSubset(exp, set1, cnUnchanged);
		double[] vals2 = getSubset(exp, set2, cnUnchanged);

		if (vals1.length < MIN_GROUP_SIZE || vals2.length < MIN_GROUP_SIZE) return Double.NaN;

		return StudentsT.getPValOfMeanDifference(vals1, vals2);
	}

	private double calcMeanChange(String symbol, boolean[] set1, boolean[] set2)
	{
		double[] exp = expMan.get(symbol);

		if (exp == null) return Double.NaN;

		boolean[] cnUnchanged = getCopyNumberUnchanged(symbol);
		if (cnUnchanged == null) return Double.NaN;

		double[] vals1 = getSubset(exp, set1, cnUnchanged);
		double[] vals2 = getSubset(exp, set2, cnUnchanged);

		if (vals1.length < MIN_GROUP_SIZE || vals2.length < MIN_GROUP_SIZE) return Double.NaN;

		return Summary.calcChangeOfMean(vals1, vals2);
	}

	private double[] getSubset(double[] vals, boolean[] inds, boolean[] cnUnchanged)
	{
		List<Double> list = new ArrayList<Double>(vals.length);

		for (int i = 0; i < vals.length; i++)
		{
			if (inds[i] && cnUnchanged[i] && !Double.isNaN(vals[i])) list.add(vals[i]);
		}
		double[] sub = new double[list.size()];
		for (int i = 0; i < sub.length; i++)
		{
			sub[i] = list.get(i);
		}
		return sub;
	}

	private boolean[] getCopyNumberUnchanged(String symbol)
	{
		AlterationPack alts = portalAcc.getAlterations(symbol);

		if (alts == null) return null;

		Change[] cnc = alts.get(Alteration.COPY_NUMBER);

		if (cnc == null) return null;

		boolean[] b = new boolean[cnc.length];
		for (int i = 0; i < b.length; i++)
		{
			b[i] = !cnc[i].isAltered() && !cnc[i].isAbsent();
		}
		return b;
	}

	private Set<String> getUpstream(String symbol, int depth)
	{
		assert depth >= 0;

		Set<String> expUp = new HashSet<String>(travExp.getUpstream(symbol));

		if (depth == 0) return expUp;

		return travSt.getUpstream(expUp, depth);
	}

	private String getPath(String from, String to, int depth)
	{
		Set<String> expUp = new HashSet<String>(travExp.getUpstream(to));

		if (expUp.contains(from)) return from + " -.> " + to;
		else if (depth > 0)
		{
			for (String s : expUp)
			{
				String path = getStChPath(from, s, depth);

				if (path != null) return path + " -.> " + to;
			}
		}
		return null;
	}

	private String getStChPath(String from, String to, int depth)
	{
		Set<String> stUp = new HashSet<String>(travSt.getUpstream(to));

		if (stUp.contains(from)) return from + " --> " + to;
		else if (depth > 1)
		{
			for (String s : stUp)
			{
				String path = getStChPath(from, s, depth-1);

				if (path != null) return path + " --> " + to;
			}
		}
		return null;
	}

	public Map<String, Double> calcInfluencePvals()
	{
		mutatedUpstr = new HashMap<String, Set<String>>();
		targetChange = new HashMap<String, Boolean>();
		Map<String, Double> pvals = new HashMap<String, Double>();

		for (String sym : travExp.getSymbols())
		{
			Set<String> up = getUpstream(sym, depth);

			up.retainAll(mutsig);

			if (up.isEmpty()) continue;

			boolean[] normal = selectSubset(up, false);
			boolean[] mutated = selectSubset(up, true);

			double pval = calcDiffPval(sym, normal, mutated);

			if (Double.isNaN(pval)) continue;

			mutatedUpstr.put(sym, up);

			pvals.put(sym, pval);

			targetChange.put(sym, Math.signum(calcMeanChange(sym, normal, mutated)) > 0);
		}
		return pvals;
	}

	// Section : Visualization --------------------------------------------------------------------|

	private void removeNonAffectors(List<String> list)
	{
		for (String target : list)
		{
			Set<String> up = mutatedUpstr.get(target);

			// Remove based on individual effect
			Set<String> remove = new HashSet<String>();

			for (String u : up)
			{
				if (calcPVal(target, Collections.singleton(u)) > 0.99) remove.add(u);
			}
			up.removeAll(remove);

			// Remove based on negative contribution

			boolean loop = up.size() > 1;

			int i = 0;
			while(loop)
			{
				boolean[] normal = selectSubset(up, false);
				boolean[] mutated = selectSubset(up, true);

				double pval = calcDiffPval(target, normal, mutated);

				double minPval = 1;
				String rem = null;

				for (String s : up)
				{
					Set<String> reduced = new HashSet<String>(up);
					reduced.remove(s);
					boolean[] nor = selectSubset(reduced, false);
					boolean[] mut = selectSubset(reduced, true);

					double p = calcDiffPval(target, nor, mut);

					if (p < minPval)
					{
						minPval = p;
						rem = s;
					}
				}

				if (minPval < pval)
				{
					up.remove(rem);
					if (up.size() < 2) loop = false;
				}
				else loop = false;

				if (i++ > 100)
				{
					System.out.println("loop of 100");
				}
			}
		}
	}

	//--- Section: Graph generation----------------------------------------------------------------|

	private double calcPVal(String target, Set<String> ups)
	{
		boolean[] normal = selectSubset(ups, false);
		boolean[] mutated = selectSubset(ups, true);

		return calcDiffPval(target, normal, mutated);
	}

	public void generateInfluenceGraphs(List<String> result)
	{
		UpstreamTree tree = new UpstreamTree(travSt, travExp, new BranchDataProvider()
		{
			final Color TARG_UP_COLOR = new Color(220, 255, 220);
			final Color TARG_DW_COLOR = new Color(255, 255, 200);

			@Override
			public Color getColor(String gene, String root)
			{
				if (gene.equals(root))
				{
					return targetChange.get(gene) ? TARG_UP_COLOR : TARG_DW_COLOR;
				}

				double pval = calcPVal(root, Collections.singleton(gene));
				String color = val2Color(pval);
				String[] c = color.split(" ");
				return new Color(Integer.parseInt(c[0]), Integer.parseInt(c[1]),
					Integer.parseInt(c[2]));
			}

			@Override
			public double getThickness(GeneBranch branch, String root)
			{
				double pval = calcPVal(root, branch.getAllGenes());
				return -Math.log(pval);
			}
		});

		String dir = this.dir + dataset.name() + "/";
		File d = new File(dir);
		if (!d.exists()) d.mkdirs();
		assert d.isDirectory();

		for (String target : result)
		{
			GeneBranch g = tree.getTree(target, mutatedUpstr.get(target), depth + 1);
			g.trimToMajorPaths(mutatedUpstr.get(target));


			RadialInfluenceTree.write(g, true, dir + target + ".svg");

			writeTree(g, dir);
		}
	}

	private void writeTree(GeneBranch gwu, String dir)
	{
		try
		{
			BufferedWriter writer1 = new BufferedWriter(new FileWriter(dir + gwu.gene + ".sif"));
			BufferedWriter writer2 = new BufferedWriter(new FileWriter(dir + gwu.gene + ".format"));

			writer2.write("graph\tgrouping\ton\n");
			writer2.write("node\t" + gwu.gene + "\tcolor\t" +
				(targetChange.get(gwu.gene) ? "220 255 220" : "255 255 200") + "\n");

			for (GeneBranch up : gwu.branches)
			{
				String edgeTag = SIFEnum.CONTROLS_EXPRESSION_OF.getTag();
				writer1.write(up.gene + "\t" + edgeTag + "\t" + gwu.gene + "\n");

				writeWeights(gwu.gene, gwu.gene, up, edgeTag, writer2);

				writePart(gwu.gene, up, writer1, writer2);
			}
			writer1.close();
			writer2.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void writePart(String origTarget, GeneBranch gwu, Writer writer1,
		Writer writer2) throws IOException
	{
		for (GeneBranch up : gwu.branches)
		{
			String edgeTag = SIFEnum.CONTROLS_STATE_CHANGE_OF.getTag();
			writer1.write(up.gene + "\t" + edgeTag + "\t" + gwu.gene + "\n");

			writeWeights(origTarget, gwu.gene, up, edgeTag, writer2);

			writePart(origTarget, up, writer1, writer2);
		}
	}

	private void writeWeights(String orig, String to, GeneBranch gwu, String edgeType,
		Writer writer) throws IOException
	{
		Set<String> upstr = gwu.getAllGenes();
		upstr.retainAll(mutatedUpstr.get(orig));
		assert !upstr.isEmpty();
		double cumPval = calcPVal(orig, upstr);

		String key = gwu.gene + " " + edgeType + " " + to;
		writer.write("edge\t" + key + "\tcolor\t" + val2Color(cumPval) + "\n");

		if (mutatedUpstr.get(orig).contains(gwu.gene))
		{
			double pval = calcPVal(orig, Collections.singleton(gwu.gene));
			writer.write("node\t" + gwu.gene + "\tcolor\t" + val2Color(pval) + "\n");
		}
		else
		{
			writer.write("node\t" + gwu.gene + "\tcolor\t255 255 255\n");
		}
	}

	private String val2Color(double pval)
	{
		double score = Math.min(10, -Math.log(pval));

		int v = 255 - (int) Math.round((255D / 10D) * score);

		return "255 " + v + " " + v;
	}
}