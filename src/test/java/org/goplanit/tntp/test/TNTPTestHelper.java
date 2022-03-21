package org.goplanit.tntp.test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import org.goplanit.assignment.TrafficAssignment;
import org.goplanit.assignment.traditionalstatic.TraditionalStaticAssignmentConfigurator;
import org.goplanit.cost.physical.BPRLinkTravelTimeCost;
import org.goplanit.cost.virtual.FixedConnectoidTravelTimeCost;
import org.goplanit.demands.Demands;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.output.configuration.LinkOutputTypeConfiguration;
import org.goplanit.output.configuration.OutputConfiguration;
import org.goplanit.output.enums.OutputType;
import org.goplanit.output.formatter.MemoryOutputFormatter;
import org.goplanit.output.formatter.OutputFormatter;
import org.goplanit.output.property.OutputPropertyType;
import org.goplanit.sdinteraction.smoothing.MSASmoothing;
import org.goplanit.tntp.input.TntpInputBuilder;
import org.goplanit.tntp.project.TntpProject;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.zoning.Zoning;

/**
 * Helper class for TNTP unit tests
 *
 * @author gman6028
 *
 */
public class TntpTestHelper {

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(TntpTestHelper.class.getCanonicalName());


  public static final int DEFAULT_MAX_ITERATIONS = 1;
  public static final double DEFAULT_CONVERGENCE_EPSILON = 0.01;
  public static final double DEFAULT_MAXIMUM_SPEED = 25.0; // this is the default for Chicago Sketch
                                                           // Type 3 links

  /**
   * Collect the standard results given for testing
   *
   * @param standardResultsFileLocation location of file containing standard results
   * @return Map of containing flow and cost values for each upstream and downstream node
   * @throws PlanItException thrown if there is an error
   */
  public static Map<String, Map<String, double[]>> parseStandardResultsFile(final String standardResultsFileLocation)
      throws PlanItException {
    final Map<String, Map<String, double[]>> resultsMap = new HashMap<String, Map<String, double[]>>();
    try (Scanner scanner = new Scanner(new File(standardResultsFileLocation).getCanonicalFile())) {
      String line = scanner.nextLine();
      while (scanner.hasNextLine()) {
        line = scanner.nextLine().trim();
        final String[] cols = line.split("\\s+");
        final String upstreamNodeExternalId = cols[0];
        if (!resultsMap.containsKey(upstreamNodeExternalId)) {
          resultsMap.put(upstreamNodeExternalId, new HashMap<String, double[]>());
        }
        final String downstreamNodeExternalId = cols[1];
        final double[] flowCost = {Double.parseDouble(cols[2]), Double.parseDouble(cols[3])};
        resultsMap.get(upstreamNodeExternalId).put(downstreamNodeExternalId, flowCost);
      }
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when parsing standard results file",e);
    }
    return resultsMap;
  }

  /**
   * Top-level method which runs PLANit for TNTP format input using a traditional static assignment
   *
   * @param inputBuilder to use which is assumed to be fully configured
   * @param maxIterations to apply
   * @param gapEpsilong to apply
   * @return project that the assignment was run on and the memory output formatter registered on the assignment
   * @throws PlanItException thrown if there is an error
   */
  public static Pair<TntpProject, MemoryOutputFormatter> execute(final TntpInputBuilder inputBuilder, int maxIterations, double gapEpsilon) throws PlanItException {
    
    final TntpProject project = new TntpProject(inputBuilder);

    // RAW INPUT START --------------------------------
    final MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork) project.createAndRegisterInfrastructureNetwork(MacroscopicNetwork.class.getCanonicalName());
    final Zoning zoning = project.createAndRegisterZoning(macroscopicNetwork);
    final Demands demands = project.createAndRegisterDemands(zoning, macroscopicNetwork);

    // RAW INPUT END -----------------------------------

    // TRAFFIC ASSIGNMENT START------------------------
    final TraditionalStaticAssignmentConfigurator ta =
        (TraditionalStaticAssignmentConfigurator) project.createAndRegisterTrafficAssignment(
            TrafficAssignment.TRADITIONAL_STATIC_ASSIGNMENT, demands, zoning, macroscopicNetwork);

    // SUPPLY-DEMAND INTERACTIONS
    ta.createAndRegisterPhysicalCost(BPRLinkTravelTimeCost.class.getCanonicalName());
    ta.createAndRegisterVirtualCost(FixedConnectoidTravelTimeCost.class.getCanonicalName());
    ta.createAndRegisterSmoothing(MSASmoothing.class.getCanonicalName());
          
    // DATA OUTPUT CONFIGURATION
    ta.activateOutput(OutputType.LINK);
    final OutputConfiguration outputConfiguration = ta.getOutputConfiguration();
    outputConfiguration.setPersistOnlyFinalIteration(true); // option to only persist the final
                                                            // iteration

    // OUTPUT FORMAT CONFIGURATION - LINKS
    final LinkOutputTypeConfiguration linkOutputTypeConfiguration =
        (LinkOutputTypeConfiguration) outputConfiguration.getOutputTypeConfiguration(OutputType.LINK);
    linkOutputTypeConfiguration.addProperty(OutputPropertyType.LINK_SEGMENT_TYPE_NAME);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.LINK_SEGMENT_ID);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.LINK_SEGMENT_XML_ID);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.DOWNSTREAM_NODE_LOCATION);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.DOWNSTREAM_NODE_ID);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.DOWNSTREAM_NODE_XML_ID);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.UPSTREAM_NODE_LOCATION);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.UPSTREAM_NODE_ID);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.UPSTREAM_NODE_XML_ID);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.MODE_EXTERNAL_ID);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.MODE_ID);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.TIME_PERIOD_EXTERNAL_ID);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.TIME_PERIOD_ID);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.LINK_SEGMENT_ID);
    linkOutputTypeConfiguration.removeProperty(OutputPropertyType.MAXIMUM_SPEED);
    
    linkOutputTypeConfiguration.addProperty(OutputPropertyType.DOWNSTREAM_NODE_EXTERNAL_ID);
    linkOutputTypeConfiguration.addProperty(OutputPropertyType.UPSTREAM_NODE_EXTERNAL_ID);   
    
    // MemoryOutputFormatter - Links
    final MemoryOutputFormatter memoryOutputFormatter = (MemoryOutputFormatter)
        project.createAndRegisterOutputFormatter(OutputFormatter.MEMORY_OUTPUT_FORMATTER);

    // taBuilder.registerOutputFormatter(csvOutputFormatter);
    ta.registerOutputFormatter(memoryOutputFormatter);

    // "USER" configuration
    ta.getGapFunction().getStopCriterion().setMaxIterations(maxIterations);
    ta.getGapFunction().getStopCriterion().setEpsilon(gapEpsilon);

    project.executeAllTrafficAssignments();
    return Pair.of(project,memoryOutputFormatter);
  }
}