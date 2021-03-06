package org.cbio.causality.analysis;

import org.biopax.paxtools.pattern.miner.SIFEnum;
import org.biopax.paxtools.pattern.miner.SIFInteraction;
import org.biopax.paxtools.pattern.miner.SIFType;
import org.cbio.causality.network.MSigDBTFT;
import org.cbio.causality.network.PathwayCommons;
import org.cbio.causality.network.SPIKE;
import org.cbio.causality.util.CollectionUtil;

import java.io.*;
import java.util.*;

/**
 * This is a set graphs.
 *
 * @author Ozgun Babur
 */
public class GraphList extends Graph
{
	protected List<Graph> graphs;

	protected Map<String, Graph> type2graph;

	public GraphList(String name)
	{
		super(name, null);
		graphs = new ArrayList<Graph>();
		type2graph = new HashMap<String, Graph>();
		super.ppMap = null;
		super.dwMap = null;
		super.upMap = null;
		super.mediators = null;
	}

	public void addGraph(Graph graph)
	{
		this.graphs.add(graph);
		type2graph.put(graph.getEdgeType(), graph);
	}

	public List<Graph> getGraphs()
	{
		return graphs;
	}

	public Graph getGraph(String edgeType)
	{
		return type2graph.get(edgeType);
	}

	public void write(Writer writer)
	{
		for (Graph graph : graphs)
		{
			graph.write(writer);
		}
	}

	public boolean isDirected()
	{
		for (Graph graph : graphs)
		{
			if (graph.isDirected()) return true;
		}
		return false;
	}

	public boolean isUndirected()
	{
		for (Graph graph : graphs)
		{
			if (graph.isUndirected()) return true;
		}
		return false;
	}

	public Set<String> goBFS(String seed, boolean downstream)
	{
		Set<String> result = new HashSet<String>();
		for (Graph graph : graphs)
		{
			result.addAll(graph.goBFS(seed, downstream ? graph.dwMap : graph.upMap));
		}
		return result;
	}

	public Set<String> goBFS(Set<String> seed, Set<String> visited, boolean downstream)
	{
		Set<String> result = new HashSet<String>();
		for (Graph graph : graphs)
		{
			result.addAll(graph.goBFS(seed, visited, downstream ? graph.dwMap : graph.upMap));
		}
		return result;
	}

	public Set<String> goBFS(Set<String> seed, Set<String> visited)
	{
		Set<String> result = new HashSet<String>();
		for (Graph graph : graphs)
		{
			result.addAll(graph.goBFS(seed, visited, graph.ppMap));
		}
		return result;
	}

	public Set<String> getUpstream(String gene)
	{
		Set<String> result = new HashSet<String>();
		for (Graph graph : graphs)
		{
			result.addAll(graph.getUpstream(gene));
		}
		return result;
	}

	public Set<String> getDownstream(String gene)
	{
		Set<String> result = new HashSet<String>();
		for (Graph graph : graphs)
		{
			result.addAll(graph.getDownstream(gene));
		}
		return result;
	}

	public Set<String> getNeighbors(String gene)
	{
		Set<String> result = new HashSet<String>();
		for (Graph graph : graphs)
		{
			result.addAll(graph.getNeighbors(gene));
		}
		return result;
	}

	public Set<String> getSymbols(boolean directed)
	{
		Set<String> syms = new HashSet<String>();

		for (Graph graph : graphs)
		{
			syms.addAll(graph.getSymbols(directed));
		}
		return syms;
	}

	public void printStats()
	{
		System.out.println("Graph list: " + getName());
		System.out.println("--------");
		for (Graph graph : graphs)
		{
			graph.printStats();
			System.out.println();
		}
		System.out.println("--------");
	}

	public Graph copy()
	{
		GraphList copy = new GraphList(getName());
		for (Graph graph : graphs)
		{
			copy.addGraph(graph.copy());
		}
		return copy;
	}

	public GraphList changeTo(boolean directed)
	{
		for (Graph graph : graphs)
		{
			graph.changeTo(directed);
		}
		return this;
	}

	public void crop(Collection<String> symbols)
	{
		for (Graph graph : graphs)
		{
			graph.crop(symbols);
		}
	}

	protected Set<String> getRelationStrings(boolean directed)
	{
		Set<String> set = new HashSet<String>();

		for (Graph graph : graphs)
		{
			set.addAll(graph.getRelationStrings(directed));
		}
		return set;
	}

	public Set<String> toString(Set<String> from, Set<String> to)
	{
		Set<String> result = new HashSet<String>();
		for (Graph graph : graphs)
		{
			result.addAll(graph.toString(from, to));
		}
		return result;
	}

	public static void main(String[] args)
	{

	}
}
