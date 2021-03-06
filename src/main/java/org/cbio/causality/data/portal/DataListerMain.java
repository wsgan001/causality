package org.cbio.causality.data.portal;

import org.cbio.causality.model.Alteration;
import org.cbio.causality.model.AlterationPack;
import org.cbio.causality.model.Change;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author Arman Aksoy
 */

public class DataListerMain {
	private static Random random = new Random();
	private static String[] genes = {"POU5F1B"};
//	private static String[] genes = {"TP53", "EGFR", "MDM2", "BRCA1", "POLE", "GAPDH", "ACTB", "AR", "AKT1", "AKT2", "AKT3", "KLK3", "XXXX"};

	public static void main(String[] args) throws IOException
	{
		if (false){printRPPACases();return;}

		CBioPortalAccessor cBioPortalAccessor = new CBioPortalAccessor();

		int studyIndex = 120;
		int caseListIndex = 0;
		int[] profileIndex = new int[]{0};
		boolean testDataRetrieval = true;

		int i = 0;
		
		System.out.println("**");
		System.out.println("All studies: ");

		// List all available cancer studies
		List<CancerStudy> cancerStudies = cBioPortalAccessor.getCancerStudies();
		for (CancerStudy cancerStudy : cancerStudies) {
			System.out.println((i++) + "\tcancerStudy = " + cancerStudy.getName() + " [" + cancerStudy.getStudyId() + "]");
		}

		System.out.println("**");

		// Select a random cancer study and then list case lists associated with this study
//		CancerStudy cancerStudy = cancerStudies.get(random.nextInt(cancerStudies.size()));
		CancerStudy cancerStudy = cancerStudies.get(studyIndex);
		cBioPortalAccessor.setCurrentCancerStudy(cancerStudy);
		System.out.println("Current cancerStudy = " + cBioPortalAccessor.getCurrentCancerStudy().getName() + "\tid = " + cBioPortalAccessor.getCurrentCancerStudy().getStudyId());

		System.out.println("**");

		i = 0;

		System.out.println("Case lists:");
		List<CaseList> caseListsForCurrentStudy = cBioPortalAccessor.getCaseListsForCurrentStudy();
		for (CaseList caseList : caseListsForCurrentStudy) {
			System.out.println((i++) + "\tcaseList = " + caseList.getDescription() + " [" + caseList.getCases().length + "]"  + " [" + caseList.getId() + "]");
		}

		// Now use the first one on the list
//		cBioPortalAccessor.setCurrentCaseList(caseListsForCurrentStudy.get(random.nextInt(caseListsForCurrentStudy.size())));
		cBioPortalAccessor.setCurrentCaseList(caseListsForCurrentStudy.get(caseListIndex));
		System.out.println("**");
		System.out.println("Current case list: " + cBioPortalAccessor.getCurrentCaseList().getDescription() + "\tid = " + cBioPortalAccessor.getCurrentCaseList().getId());

		i = 0;

		// Now list all geneticProfiles available for current study
		System.out.println("**");
		System.out.println("Genetic Profiles for the study:");
		List<GeneticProfile> geneticProfilesForCurrentStudy = cBioPortalAccessor.getGeneticProfilesForCurrentStudy();
		for (GeneticProfile geneticProfile : geneticProfilesForCurrentStudy) {
			System.out.println((i++) + "\tgeneticProfile = " + geneticProfile.getName()
				+ " (" + geneticProfile.getType() + ")"  + " [" + geneticProfile.getId() + "]");
		}

		// Pick a random genetic profile from the list and set it.
		List<GeneticProfile> geneticProfiles = new ArrayList<GeneticProfile>();
//		geneticProfiles.add(geneticProfilesForCurrentStudy.get(random.nextInt(geneticProfilesForCurrentStudy.size())));
		for (int pi : profileIndex)
		{
			geneticProfiles.add(geneticProfilesForCurrentStudy.get(pi));
		}

		cBioPortalAccessor.setCurrentGeneticProfiles(geneticProfiles);
		System.out.println("**");
		System.out.println("Current genetic profile: ");

		for (GeneticProfile geneticProfile : cBioPortalAccessor.getCurrentGeneticProfiles()) {
			System.out.println("\tgeneticProfile = " + geneticProfile.getName()
				+ " (" + geneticProfile.getType() + ")\tid = " + geneticProfile.getId());
		}


		// Save alteration type and numOfCases for minimalistic oncoprints
		GeneticProfile geneticProfile = cBioPortalAccessor.getCurrentGeneticProfiles().iterator().next();
		Alteration alteration = ProfileType.convertToAlteration(geneticProfile.getType());
		Integer numOfCases = cBioPortalAccessor.getCurrentCaseList().getCases().length;

		if (!testDataRetrieval) return;

		// Headers
		System.out.println("**");
		System.out.println("Inferred alterations:\n");
		System.out.println("Gene Name\tStatus\t\tOncoPrint(" + numOfCases + " cases)");

		for (String gene : genes) {
			AlterationPack alterations = cBioPortalAccessor.getAlterations(gene);

			if (alterations == null) continue;
			if (alterations.get(Alteration.ANY) == null) alterations.complete(Alteration.ANY);

			StringBuilder oncoPrint = new StringBuilder();
//			Change[] changes = alterations.get(alteration);
			Change[] changes = alterations.get(Alteration.ANY);
			assert numOfCases == alterations.getSize();

			for (Change change : changes) {
				if(change.isAltered())
					oncoPrint.append("*");
				else if (!change.isAbsent())
					oncoPrint.append(".");
				else
					oncoPrint.append(" ");
			}

			System.out.println("\t" + gene + (gene.length() < 3 ? "  " : "") + "\t"
				+ (alterations.isAltered() ? "altered\t" : "not-altered")
				+ "\t" + oncoPrint);
		}
	}

	private static void printRPPACases() throws IOException
	{
		CBioPortalAccessor acc = new CBioPortalAccessor();
		List<CancerStudy> cancerStudies = acc.getCancerStudies();

		for (CancerStudy study : cancerStudies)
		{
			acc.setCurrentCancerStudy(study);

			for (CaseList cl : acc.getCaseListsForCurrentStudy())
			{
				if (cl.getId().contains("rppa"))
				{
					System.out.println(cl + " [" + cl.getCases().length + "]");
				}
			}
		}
	}
}