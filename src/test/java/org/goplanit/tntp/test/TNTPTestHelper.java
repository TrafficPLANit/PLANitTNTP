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
import org.goplanit.tntp.enums.CapacityPeriod;
import org.goplanit.tntp.enums.LengthUnits;
import org.goplanit.tntp.enums.NetworkFileColumnType;
import org.goplanit.tntp.enums.SpeedUnits;
import org.goplanit.tntp.enums.TimeUnits;
import org.goplanit.tntp.project.TntpProject;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.unit.Unit;
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
   * Store the standard results given for testing
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
        if (!cols[2].trim().equals("0")) {
          final double[] flowCost = {Double.parseDouble(cols[2]), Double.parseDouble(cols[3])};
          resultsMap.get(upstreamNodeExternalId).put(downstreamNodeExternalId, flowCost);
        }
      }
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when parsing standard results file",e);
    }
    return resultsMap;
  }

  /**
   * Top-level method which runs PLANit for TNTP format input
   *
   * @param networkFileLocation the input network file (required)
   * @param demandFileLocation the input trips file (required)
   * @param maxIterations the maximum number of iterations
   * @param epsilon the epsilon used for convergence
   * @param outputCostTimeUnit the output time units
   * @param defaultMaximumSpeed the default maximum speed along links
   * @return a Pair containing the MemoryOutputFormatter and the Tntp object
   * @throws PlanItException thrown if there is an error
   */
  public static Pair<MemoryOutputFormatter, TntpInputBuilder4Testing> execute(final String networkFileLocation,
      final String demandFileLocation,
      final int maxIterations,
      final double epsilon, final Unit outputCostTimeUnit, final double defaultMaximumSpeed) throws PlanItException {

    // TODO - The following arrangement of columns is correct for Chicago Sketch and Philadelphia.
    // For some other cities the arrangement is different.
    final Map<NetworkFileColumnType, Integer> networkFileColumns = new HashMap<NetworkFileColumnType, Integer>();
    networkFileColumns.put(NetworkFileColumnType.UPSTREAM_NODE_ID, 0);
    networkFileColumns.put(NetworkFileColumnType.DOWNSTREAM_NODE_ID, 1);
    networkFileColumns.put(NetworkFileColumnType.CAPACITY_PER_LANE, 2);
    networkFileColumns.put(NetworkFileColumnType.LENGTH, 3);
    networkFileColumns.put(NetworkFileColumnType.FREE_FLOW_TRAVEL_TIME, 4);
    networkFileColumns.put(NetworkFileColumnType.B, 5);
    networkFileColumns.put(NetworkFileColumnType.POWER, 6);
    networkFileColumns.put(NetworkFileColumnType.MAXIMUM_SPEED, 7);
    networkFileColumns.put(NetworkFileColumnType.TOLL, 8);
    networkFileColumns.put(NetworkFileColumnType.LINK_TYPE, 9);

    final SpeedUnits speedUnits = SpeedUnits.MILES_H;
    final LengthUnits lengthUnits = LengthUnits.MILES; // Both Chicago-Sketch and Philadelphia use miles
    final CapacityPeriod capacityPeriod = CapacityPeriod.HOUR; // Chicago-Sketch only - for Philadelphia use days
    
    final TimeUnits timeUnits = TimeUnits.MINUTES;

    final TntpInputBuilder4Testing tntp = new TntpInputBuilder4Testing(
        networkFileLocation, 
        demandFileLocation, 
        null,
        networkFileColumns, 
        speedUnits, 
        lengthUnits, 
        timeUnits,
        capacityPeriod, 
        defaultMaximumSpeed);
    
    final TntpProject project = new TntpProject(tntp);

    // RAW INPUT START --------------------------------
    final MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork) project.createAndRegisterInfrastructureNetwork(MacroscopicNetwork.class.getCanonicalName());
    final Zoning zoning = project.createAndRegisterZoning(macroscopicNetwork);
    final Demands demands = project.createAndRegisterDemands(zoning, macroscopicNetwork);
    tntp.setCreatedInputs(macroscopicNetwork, zoning, demands);

    // RAW INPUT END -----------------------------------

    // TRAFFIC ASSIGNMENT START------------------------
    final TraditionalStaticAssignmentConfigurator ta =
        (TraditionalStaticAssignmentConfigurator) project.createAndRegisterTrafficAssignment(
            TrafficAssignment.TRADITIONAL_STATIC_ASSIGNMENT, demands, zoning, macroscopicNetwork);

    // SUPPLY-DEMAND INTERACTIONS
    ta.createAndRegisterPhysicalCost(BPRLinkTravelTimeCost.class.getCanonicalName());
    ta.createAndRegisterVirtualCost(FixedConnectoidTravelTimeCost.class.getCanonicalName());
    ta.createAndRegisterSmoothing(MSASmoothing.class.getCanonicalName());
    
    boolean adjustCostOutputTimeUnit = false;
    if (outputCostTimeUnit != null) {
      adjustCostOutputTimeUnit = true;
    }        

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
    
    if(adjustCostOutputTimeUnit == true) {
      linkOutputTypeConfiguration.overrideOutputPropertyUnits(OutputPropertyType.LINK_SEGMENT_COST, outputCostTimeUnit);
    }     
    
    // MemoryOutputFormatter - Links
    final MemoryOutputFormatter memoryOutputFormatter = (MemoryOutputFormatter)
        project.createAndRegisterOutputFormatter(OutputFormatter.MEMORY_OUTPUT_FORMATTER);

    // taBuilder.registerOutputFormatter(csvOutputFormatter);
    ta.registerOutputFormatter(memoryOutputFormatter);

    // "USER" configuration
    ta.getGapFunction().getStopCriterion().setMaxIterations(maxIterations);
    ta.getGapFunction().getStopCriterion().setEpsilon(epsilon);

    project.executeAllTrafficAssignments();
    return Pair.of(memoryOutputFormatter, tntp);
  }
}