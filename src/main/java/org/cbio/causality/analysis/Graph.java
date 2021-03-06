package org.cbio.causality.analysis;

import org.apache.commons.collections15.SetUtils;
import org.biopax.paxtools.pattern.miner.SIFEnum;
import org.biopax.paxtools.pattern.miner.SIFInteraction;
import org.biopax.paxtools.pattern.miner.SIFType;
import org.cbio.causality.network.MSigDBTFT;
import org.cbio.causality.network.PathwayCommons;
import org.cbio.causality.network.SPIKE;
import org.cbio.causality.util.CollectionUtil;
import org.cbio.causality.util.FDR;
import org.cbio.causality.util.FishersExactTest;
import org.cbio.causality.util.Histogram;

import java.io.*;
import java.util.*;

/**
 * This is a simple graph, built using external maps. All nodes are identified with a unique String.
 * Relations can be either directed or undirected, and can be mixed.
 *
 * @author Ozgun Babur
 */
public class Graph implements Serializable
{
	private String edgeType;
	private String name;

	protected Map<String, Set<String>> dwMap;
	protected Map<String, Set<String>> upMap;
	protected Map<String, Set<String>> ppMap;

	protected Map<String, Map<String, Set<String>>> mediators;

	protected boolean allowSelfEdges = false;

	public Graph(String name, String edgeType)
	{
		this();
		this.name = name;
		this.edgeType = edgeType;
	}

	public Graph()
	{
		dwMap = new HashMap<String, Set<String>>();
		upMap = new HashMap<String, Set<String>>();
		ppMap = new HashMap<String, Set<String>>();
		mediators = new HashMap<String, Map<String, Set<String>>>();
	}

	public boolean load(String filename, Set<String> ppiTypes, Set<String> signalTypes)
	{
		try
		{
			return load(new FileInputStream(filename), ppiTypes, signalTypes);
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
			return false;
		}
	}

	public boolean load(InputStream is, Set<String> undirectedTypes, Set<String> directedTypes)
	{
		try
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));

			for (String line = reader.readLine(); line != null; line = reader.readLine())
			{
				String[] token = line.split("\t");

				if (token.length < 3) continue;

				Boolean directed = null;
				if (undirectedTypes.contains(token[1])) directed = false;
				else if (directedTypes.contains(token[1])) directed = true;

				if (directed != null)
				{
					if (token.length > 3)
					{
						putRelation(token[0], token[2], token[3], directed);
					}
					else
					{
						putRelation(token[0], token[2], directed);
					}
				}
			}

			reader.close();
		}
		catch (IOException e) { e.printStackTrace(); return false; } return true;
	}

	public void load(Collection<SIFInteraction> sifs, SIFType... typeArray)
	{
		Set<SIFType> types = typeArray.length == 0 ? null :
			new HashSet<SIFType>(Arrays.asList(typeArray));

		for (SIFInteraction sif : sifs)
		{
			if (types != null && !types.contains(sif.type)) continue;

			putRelation(sif.sourceID, sif.targetID, sif.type.isDirected());
		}
	}

	public void write(String filename)
	{
		try
		{
			BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
			write(writer);
			writer.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void write(Writer writer)
	{
		try
		{
			for (String g1 : dwMap.keySet())
			{
				for (String g2 : dwMap.get(g1))
				{
					writer.write(g1 + "\t" + edgeType + "\t" + g2);

					if (mediators.containsKey(g1) && mediators.get(g1).containsKey(g2))
					{
						writer.write("\t" + convertMediatorsToString(mediators.get(g1).get(g2)));
					}

					writer.write("\n");
				}
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public String getMediatorsInString(String source, String target)
	{
		String s = "";
		if (mediators.containsKey(source) && mediators.get(source).containsKey(target))
		{
			s += convertMediatorsToString(mediators.get(source).get(target));
		}
		if (this.isUndirected())
		{
			if (mediators.containsKey(target) && mediators.get(target).containsKey(source))
			{
				s += " " + convertMediatorsToString(mediators.get(target).get(source));
			}
		}
		return s.trim();
	}

	private String convertMediatorsToString(Set<String> set)
	{
		StringBuilder sb = new StringBuilder();
		for (String s : set)
		{
			sb.append(s).append(" ");
		}

		return sb.toString().trim();
	}

	public void clear()
	{
		upMap.clear();
		dwMap.clear();
		ppMap.clear();
	}

	public void putRelation(String source, String target, String mediatorsStr, boolean directed)
	{
		putRelation(source, target, directed);

		if (!mediators.containsKey(source))
			mediators.put(source, new HashMap<String, Set<String>>());
		if (!mediators.get(source).containsKey(target))
			mediators.get(source).put(target, new HashSet<String>());

		mediators.get(source).get(target).addAll(Arrays.asList(mediatorsStr.split(" |;")));
	}

	public void putRelation(String source, String target, boolean directed)
	{
		if (!allowSelfEdges && source.equals(target)) return;

		if (directed)
		{
			if (!upMap.containsKey(target)) upMap.put(target, new HashSet<String>());
			if (!dwMap.containsKey(source)) dwMap.put(source, new HashSet<String>());
			upMap.get(target).add(source);
			dwMap.get(source).add(target);
		}
		else
		{
			if (!ppMap.containsKey(source)) ppMap.put(source, new HashSet<String>());
			if (!ppMap.containsKey(target)) ppMap.put(target, new HashSet<String>());
			ppMap.get(source).add(target);
			ppMap.get(target).add(source);
		}
	}

	public boolean isDirected()
	{
		return !upMap.isEmpty();
	}

	public boolean isUndirected()
	{
		return !ppMap.isEmpty();
	}

	public String getEdgeType()
	{
		return edgeType;
	}

	public void setEdgeType(String edgeType)
	{
		this.edgeType = edgeType;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public Set<String> goBFS(String seed, boolean downstream)
	{
		return goBFS(seed, downstream ? dwMap : upMap);
	}

	public Set<String> goBFS(Set<String> seed, Set<String> visited, boolean downstream)
	{
		return goBFS(seed, visited, downstream ? dwMap : upMap);
	}

	public Set<String> goBFS(Set<String> seed, Set<String> visited)
	{
		return goBFS(seed, visited, ppMap);
	}

	protected Set<String> goBFS(String seed, Map<String, Set<String>> map)
	{
		return goBFS(Collections.singleton(seed), null, map);
	}

	protected Set<String> goBFS(Set<String> seed, Set<String> visited, Map<String, Set<String>> map)
	{
		Set<String> neigh = new HashSet<String>();
		for (String s : seed)
		{
			if (map.containsKey(s))
			{
				for (String n : map.get(s))
				{
					if (visited == null || !visited.contains(n))
					{
						neigh.add(n);
					}
				}
			}
		}
		return neigh;
	}

	public Set<String> getUpstream(Collection<String> genes)
	{
		return getStream(genes, true);
	}

	public Set<String> getDownstream(Collection<String> genes)
	{
		return getStream(genes, false);
	}

	protected Set<String> getStream(Collection<String> genes, boolean upstream)
	{
		Set<String> result = new HashSet<String>();
		for (String gene : genes)
		{
			result.addAll(upstream ? getUpstream(gene) : getDownstream(gene));
		}
		return result;
	}

	public Set<String> getUpstream(String gene)
	{
		if (upMap.containsKey(gene)) return upMap.get(gene);
		else return Collections.emptySet();
	}

	public Set<String> getUpstream(String gene, int depth)
	{
		return getUpstream(Collections.singleton(gene), depth);
	}

	public Set<String> getUpstream(Set<String> genes, int depth)
	{
		return getStream(genes, true, depth);
	}

	public Set<String> getUpstream(Set<String> genes)
	{
		Set<String> result = new HashSet<String>();
		for (String gene : genes)
		{
			result.addAll(getUpstream(gene));
		}
		return result;
	}

	public Set<String> getDownstream(String gene, int depth)
	{
		return getDownstream(Collections.singleton(gene), depth);
	}

	public Set<String> getDownstream(Set<String> genes, int depth)
	{
		return getStream(genes, false, depth);
	}

	public Set<String> getBothstream(String gene, int depth)
	{
		return getBothstream(Collections.singleton(gene), depth);
	}

	public Set<String> getBothstream(Set<String> genes, int depth)
	{
		Set<String> stream = getStream(genes, true, depth);
		stream.addAll(getStream(genes, false, depth));
		return stream;
	}


	private Set<String> getStream(Set<String> genes, boolean upstream, int depth)
	{
		if (depth < 1) return new HashSet<String>();

		Set<String> newSet = new HashSet<String>(genes);
		Set<String> result = new HashSet<String>();

		for (int i = 0; i < depth; i++)
		{
			newSet = upstream ? getUpstream(newSet) : getDownstream(newSet);
			newSet.removeAll(result);
			result.addAll(newSet);
		}

		return result;
	}

	public Set<String> getDownstream(String gene)
	{
		if (dwMap.containsKey(gene)) return dwMap.get(gene);
		else return Collections.emptySet();
	}

	public Set<String> getNeighbors(String gene)
	{
		Set<String> n = new HashSet<String>();
		if (ppMap.get(gene) != null) n.addAll(ppMap.get(gene));
		if (upMap.get(gene) != null) n.addAll(upMap.get(gene));
		if (dwMap.get(gene) != null) n.addAll(dwMap.get(gene));
		return n;
	}
	
	public Set<String> getNeighbors(Set<String> genes)
	{
		Set<String> n = new HashSet<String>();
		for (String gene : genes)
		{
			n.addAll(getNeighbors(gene));
		}
		return n;
	}

	public List<Set<String>> getNeighborsTiered(Set<String> genes, int depth, boolean upstream)
	{
		List<Set<String>> list = new ArrayList<Set<String>>();

		for (int i = 1; i <= depth; i++)
		{
			Set<String> stream = getStream(genes, upstream, i);

			for (Set<String> set : list)
			{
				stream.removeAll(set);
			}

			list.add(stream);
		}
		return list;
	}

	public Set<String> getConnectedComponent(String node)
	{
		Set<String> comp = new HashSet<String>();
		Set<String> newGenes = getNeighbors(node);
		do
		{
			Set<String> n2 = new HashSet<String>();
			for (String gene : newGenes)
			{
				n2.addAll(getNeighbors(gene));
			}
			comp.addAll(newGenes);
			n2.removeAll(comp);
			newGenes = n2;
		}
		while (!newGenes.isEmpty());
		return comp;
	}

	public Set<String> getGenesWithCommonDownstream(String gene)
	{
		Set<String> up = getUpstream(gene);
		Set<String> dw = getDownstream(gene);
		Set<String> ot = getUpstream(dw);

		Set<String> result = new HashSet<String>(up);
		result.addAll(dw);
		result.addAll(ot);
		return result;
	}

	public Set<String> getGenesWithCommonDownstream(Set<String> genes)
	{
		Set<String> up = getUpstream(genes);
		Set<String> dw = getDownstream(genes);
		Set<String> ot = getUpstream(dw);

		Set<String> result = new HashSet<String>(up);
		result.addAll(dw);
		result.addAll(ot);
		return result;
	}

	public int getDegree(String gene)
	{
		return getNeighbors(gene).size();
	}
	
	public Set<String> getPathElements(String from, Set<String> to, int limit)
	{
		Set<String> result = new HashSet<String>();
		getPathElements(from, to, limit, 0, result);
		return result;
	}

	private void getPathElements(String from, Set<String> to, int limit, int i, Set<String> result)
	{
		Set<String> set = Collections.singleton(from);
		Set<String> neigh = goBFS(set, set, true);
		for (String n : neigh)
		{
			if (to.contains(n)) result.add(n);
			else if (i < limit)
			{
				int prevSize = result.size();
				getPathElements(n, to, limit, i+1, result);
				if (result.size() > prevSize) result.add(n);
			}
		}
	}
	
	public List<CommPoint> getCommonDownstream(Set<String> seed, int limit)
	{
		Map<String, Set<String>> reachMap = new HashMap<String, Set<String>>();
		Map<String, Set<String>> breadthMap = new HashMap<String, Set<String>>();
		Map<String, Set<String>> visitedMap = new HashMap<String, Set<String>>();

		Set<CommPoint> points = new HashSet<CommPoint>();
		
		for (String s : seed)
		{
			reachMap.put(s, new HashSet<String>(Arrays.asList(s)));
			breadthMap.put(s, new HashSet<String>(Arrays.asList(s)));
			visitedMap.put(s, new HashSet<String>(Arrays.asList(s)));
		}

		for (int i = 1; i < limit; i++)
		{
			for (String s : seed)
			{
				Set<String> neigh = goBFS(breadthMap.get(s), visitedMap.get(s), true);
				for (String n : neigh)
				{
					if (!reachMap.containsKey(n))
						reachMap.put(n, new HashSet<String>(Arrays.asList(s)));
					else reachMap.get(n).add(s);
				}
				breadthMap.put(s, neigh);
				visitedMap.get(s).addAll(neigh);
			}

			for (String r : reachMap.keySet())
			{
				if (reachMap.get(r).size() > 1)
				{
					CommPoint p = new CommPoint(r, reachMap.get(r), i);
					if (!containsBetter(points, p)) points.add(p);
				}
			}
		}

		List<CommPoint> list = new ArrayList<CommPoint>(points);
		Collections.sort(list);
		return list;
	}
	
	private boolean containsBetter(Set<CommPoint> set, CommPoint p)
	{
		if (set.contains(p)) return true;
		for (CommPoint cp : set)
		{
			if (cp.dist < p.dist && cp.upstr.containsAll(p.upstr)) return true;
		}
		return false;
	}

	public Set<String> getOneSideSymbols(boolean source)
	{
		Set<String> syms = new HashSet<String>();
		syms.addAll(source ? dwMap.keySet() : upMap.keySet());
		return syms;
	}

	public Set<String> getSymbols()
	{
		Set<String> symbols = getSymbols(true);
		symbols.addAll(getSymbols(false));
		return symbols;
	}

	public Set<String> getSymbols(boolean directed)
	{
		Set<String> syms = new HashSet<String>();

		if (directed)
		{
			syms.addAll(upMap.keySet());
			syms.addAll(dwMap.keySet());
		}
		else
		{
			syms.addAll(ppMap.keySet());
		}

		return syms;
	}
	
	class CommPoint implements Comparable
	{
		String s;
		Set<String> upstr;
		int dist;

		CommPoint(String s, Set<String> upstr, int dist)
		{
			this.s = s;
			this.upstr = upstr;
			this.dist = dist;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o instanceof CommPoint)
			{
				CommPoint p = (CommPoint) o;
				if (p.s.equals(s) && p.upstr.containsAll(upstr) && upstr.containsAll(p.upstr) && 
					p.dist == dist) return true;
			}
			return false;
		}

		@Override
		public int hashCode()
		{
			return s.hashCode();
		}

		@Override
		public int compareTo(Object o)
		{
			if (o instanceof CommPoint)
			{
				CommPoint p = (CommPoint) o;
				new Integer(p.upstr.size()).compareTo(upstr.size());
			}
			return 0;
		}
	}

	/**
	 * Gets the common downstream of length 1, but allows more length if path to downstream is also
	 * in the seed set.
	 */
	public Set<String> getLinkedCommonDownstream(Set<String> seed)
	{
		Map<String, Set<String>> map = new HashMap<String, Set<String>>();

		for (String s : seed)
		{
			map.put(s, new HashSet<String>(getDownstream(s)));
			map.get(s).add(s);
		}

		boolean loop = true;

		while(loop)
		{
			loop = false;

			for (String s1 : seed)
			{
				for (String s2 : seed)
				{
					if (s1.equals(s2)) continue;

					if (map.get(s2).contains(s1))
					{
						boolean changed = map.get(s2).addAll(map.get(s1));
						loop = changed || loop;
					}
				}
			}
		}

		HashSet[] sets = map.values().toArray(new HashSet[map.values().size()]);
		Set<String> result = new HashSet<String>(sets[0]);

		for (int i = 1; i < sets.length; i++)
		{
			result.retainAll(sets[i]);
		}

		return result;
	}

	public Map<Integer, Integer> getDegreeDistibution(boolean indegree)
	{
		Map<Integer, Integer> dist = new HashMap<Integer, Integer>();
		collectDegrees(dist, indegree ? upMap : dwMap);
		return dist;
	}

	public Map<Integer, Integer> getDegreeDistibution()
	{
		Map<Integer, Integer> dist = new HashMap<Integer, Integer>();
		collectDegrees(dist, upMap);
		collectDegrees(dist, dwMap);
		collectDegrees(dist, ppMap);
		return dist;
	}

	private void collectDegrees(Map<Integer, Integer> dist, Map<String, Set<String>> map)
	{
		for (Set<String> set : map.values())
		{
			int degree = set.size();

			if (!dist.containsKey(degree)) dist.put(degree, 1);
			else dist.put(degree, dist.get(degree) + 1);
		}
	}

	public int getEdgeCount(boolean directed)
	{
		int edgeCnt = 0;

		if (directed)
		{
			for (Set<String> set : upMap.values()) edgeCnt += set.size();
			return edgeCnt;
		}
		else
		{
			for (Set<String> set : ppMap.values()) edgeCnt += set.size();
			edgeCnt /= 2;
		}

		return edgeCnt;
	}

	public void printStats()
	{
		System.out.println(name + " [" + edgeType + "]");
		if (!ppMap.isEmpty())
		{
			Set<String> syms = getSymbols(false);
			int edgeCnt = getEdgeCount(false);

			System.out.println("Undirected graph: " + syms.size() + " genes and " + edgeCnt + " edges");
		}

		if (!upMap.isEmpty() || !dwMap.isEmpty())
		{
			Set<String> syms = getSymbols(true);
			int edgeCnt = getEdgeCount(true);

			System.out.println("Directed graph: " + syms.size() + " genes (source: " +
				dwMap.keySet().size() + ", target: " + upMap.keySet().size() + ") and " + edgeCnt + " edges");
		}
	}

	public void merge(Graph graph)
	{
		merge(this.ppMap, graph.ppMap);
		merge(this.upMap, graph.upMap);
		merge(this.dwMap, graph.dwMap);
		for (String gene : graph.mediators.keySet())
		{
			if (!mediators.containsKey(gene))
				mediators.put(gene, new HashMap<String, Set<String>>());

			merge(mediators.get(gene), graph.mediators.get(gene));
		}
	}

	private void merge(Map<String, Set<String>> m1, Map<String, Set<String>> m2)
	{
		for (String s : m2.keySet())
		{
			if (!m1.containsKey(s)) m1.put(s, new HashSet<String>());
			m1.get(s).addAll(m2.get(s));
		}
	}

	public Graph copy()
	{
		Graph copy = new Graph(name, edgeType);
		for (String s : ppMap.keySet()) copy.ppMap.put(s, new HashSet<String>(ppMap.get(s)));
		for (String s : upMap.keySet()) copy.upMap.put(s, new HashSet<String>(upMap.get(s)));
		for (String s : dwMap.keySet()) copy.dwMap.put(s, new HashSet<String>(dwMap.get(s)));
		return copy;
	}

	public Graph changeTo(boolean directed)
	{
		Map<String, Set<String>> map = directed ? upMap : ppMap;

		for (String g1 : map.keySet())
		{
			for (String g2 : map.get(g1))
			{
				putRelation(g1, g2, directed);
			}
		}
		map.clear();

		if (!directed) dwMap.clear();
		return this;
	}

	public void crop(Collection<String> symbols)
	{
		crop(ppMap, symbols);
		crop(upMap, symbols);
		crop(dwMap, symbols);
	}

	private void crop(Map<String, Set<String>> map, Collection<String> symbols)
	{
		Set<String> remKeys = new HashSet<String>();
		for (String s : map.keySet())
		{
			if (!symbols.contains(s)) remKeys.add(s);
			else
			{
				map.get(s).retainAll(symbols);

				if (map.get(s).isEmpty()) remKeys.add(s);
			}
		}
		for (String key : remKeys)
		{
			map.remove(key);
		}
	}

	public void printVennIntersections(boolean directed, Graph... gArray)
	{
		System.out.println("directed = " + directed);
		List<Graph> graphs = new ArrayList<Graph>(gArray.length + 1);
		graphs.add(this);
		Collections.addAll(graphs, gArray);

		List<Set<String>> relList = new ArrayList<Set<String>>();
		for (Graph graph : graphs)
		{
			relList.add(graph.getRelationStrings(directed));
		}

		int i = 65;
		for (Graph graph : graphs)
		{
			System.out.println((char) (i++) + "\t" + graph.getName());
		}

		CollectionUtil.printVennCounts(relList.toArray(new Collection[relList.size()]));
	}

	protected Set<String> getRelationStrings(boolean directed)
	{
		Set<String> set = new HashSet<String>();

		if (directed)
		{
			for (String targ : upMap.keySet())
			{
				for (String sour : upMap.get(targ))
				{
					set.add(sour + " " + targ);
				}
			}
		}
		else
		{
			for (String g1 : ppMap.keySet())
			{
				for (String g2 : ppMap.get(g1))
				{
					if (g1.compareTo(g2) < 0 ) set.add(g1 + " " + g2);
				}
			}
		}
		return set;
	}

	public void printVennIntersections(Graph... graph)
	{
		if (isDirected()) printVennIntersections(true, graph);
		if (isUndirected()) printVennIntersections(false, graph);
	}

	public Set<String> toString(Set<String> from, Set<String> to)
	{
		Set<String> result = new HashSet<String>();
		for (String f : from)
		{
			for (String t : getDownstream(f))
			{
				if (to.contains(t)) result.add(f + "\t" + edgeType + "\t" + t);
			}
		}
		return result;
	}

	public List<String> getEnrichedGenes(Set<String> query, Set<String> background, double fdrThr,
		NeighborType type, int distance)
	{
		Graph graph = this;

		if (background != null)
		{
			graph = copy();
			graph.crop(background);
		}

		background = graph.getSymbols();
		int n = background.size();

		query = new HashSet<String>(query);
		query.retainAll(background);

		int qSize = query.size();

		Map<String, Double> pvals = new HashMap<String, Double>();
		Map<String, Double> limit = new HashMap<String, Double>();

		for (String gene : background)
		{
			Set<String> neighbors = type == NeighborType.UPSTREAM ? graph.getUpstream(gene, distance) :
				type == NeighborType.DOWNSTREAM ? graph.getDownstream(gene, distance) :
					graph.getBothstream(gene, distance);
			neighbors = new HashSet<String>(neighbors);
			neighbors.add(gene);
			int nSize = neighbors.size();

			neighbors.retainAll(query);
			int o = neighbors.size();

			pvals.put(gene, FishersExactTest.calcEnrichmentPval(n, qSize, nSize, o));
			limit.put(gene, FishersExactTest.calcEnrichmentPval(n, qSize, nSize,
				Math.min(qSize, nSize)));
		}

		if (fdrThr < 0)
		{
			fdrThr = FDR.decideBestFDR_BH(pvals, limit);
			System.out.println("fdrThr = " + fdrThr);
		}
		return FDR.select(pvals, limit, fdrThr);
	}

	public enum NeighborType
	{
		UPSTREAM,
		DOWNSTREAM,
		BOTHSTREAM
	}

	public void printDegreeDistribution(int bins)
	{
		List<Integer> list = new ArrayList<Integer>();
		for (String node : getSymbols())
		{
			Set<String> neighbors = getNeighbors(node);
			list.add(neighbors.size());
		}

		int max = CollectionUtil.maxIntInList(list);

		Histogram h = new Histogram(max / (double) bins);
		h.setBorderAtZero(true);
		for (Integer v : list)
		{
			h.count(v);
		}
		h.print();
	}

	public Graph cropToDegree(int minDegree)
	{
		Set<String> keep = new HashSet<String>();
		for (String node : getSymbols())
		{
			Set<String> neighbors = getNeighbors(node);
			if (neighbors.size() >= minDegree) keep.add(node);
		}
		Graph g = copy();
		g.crop(keep);
		return g;
	}

	public static void main(String[] args) throws FileNotFoundException
	{
		Graph g = new Graph();
		g.load(new FileInputStream("../biopax-pattern/DeltaFeatures.txt"), Collections.<String>emptySet(), Collections.singleton(SIFEnum.CONTROLS_STATE_CHANGE_OF.getTag()));
		g.printStats();

		if (true) return;


		System.out.println("PC controls-expression-of");
		Graph pcExp = PathwayCommons.getGraph(SIFEnum.CONTROLS_EXPRESSION_OF);
		pcExp.printStats();
		System.out.println("TRANSFAC");
		Graph transfac = MSigDBTFT.getGraph();
		transfac.printStats();
		System.out.println("PC exp versus transfac");
		pcExp.printVennIntersections(transfac);
		pcExp.merge(transfac);
		pcExp.printStats();

		System.out.println("\nPC controls-state-change-of");
		Graph pcSt = PathwayCommons.getGraph(SIFEnum.CONTROLS_STATE_CHANGE_OF);
		pcSt.printStats();
		System.out.println("SPIKE");
		Graph spike = SPIKE.getGraphPostTl();
		spike.printStats();
		System.out.println("PC ST-CH versus SPIKE");
		pcSt.printVennIntersections(spike);
		pcSt.merge(spike);
		pcSt.printStats();
	}
}
