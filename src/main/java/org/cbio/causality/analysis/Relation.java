package org.cbio.causality.analysis;

import org.cbio.causality.rppa.RPPAData;
import org.cbio.causality.signednetwork.SignedType;
import org.cbio.causality.util.CollectionUtil;

import java.util.Set;

/**
 * @author Ozgun Babur
 */
public class Relation
{
	public String source;
	public String target;
	public SignedType edgeType;
	public RPPAData sourceData;
	public RPPAData targetData;
	public int corrSign;
	public String mediators;
	public Set<String> sites;

	public Relation(String source, String target, SignedType edgeType,
		RPPAData sourceData, RPPAData targetData, int corrSign, String mediators,
		Set<String> sites)
	{
		this.source = source;
		this.target = target;
		this.edgeType = edgeType;
		this.sourceData = sourceData;
		this.targetData = targetData;
		this.corrSign = corrSign;
		this.mediators = mediators;
		this.sites = sites;
	}

	public String getEdgeData()
	{
		return source + "\t" + edgeType.getTag() + "\t" + target + "\t" + mediators +
			(sites == null ? "" : "\t" + CollectionUtil.merge(sites, ";"));
	}

	public String getEdgeDataAntibodyCentric()
	{
		return sourceData.id + "\t" + edgeType.getTag() + "\t" + targetData.id + "\t" + mediators +
			(sites == null ? "" : "\t" + CollectionUtil.merge(sites, ";"));
	}

	public boolean siteMatching()
	{
		if (sites == null) return false;
		if (targetData.sites == null) return false;
		for (String site : targetData.sites.get(target))
		{
			if (sites.contains(site)) return true;
		}
		return false;
	}

	public boolean phosphoEdge()
	{
		return edgeType.isPhospho();
	}

	public boolean dataChangesAsExpected()
	{
		return sourceData.getChangeSign() * targetData.getChangeSign() * corrSign == 1;
	}

	public boolean dataChangesAsUnxpected()
	{
		return sourceData.getChangeSign() * targetData.getChangeSign() * corrSign == -1;
	}

	public boolean dataChangesInsignificant()
	{
		return sourceData.getChangeSign() * targetData.getChangeSign() == 0;
	}

	public boolean siteMatches()
	{
		if (!targetData.isPhospho()) return true;
		if (sites == null) return false;
		for (String site : sites)
		{
			if (targetData.sites.get(target).contains(site)) return true;
		}
		return false;
	}
}
