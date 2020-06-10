package org.planit.tntp.test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import org.planit.cost.physical.BPRLinkTravelTimeCost;
import org.planit.cost.virtual.FixedConnectoidTravelTimeCost;
import org.planit.demands.Demands;
import org.planit.exceptions.PlanItException;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.network.virtual.Zoning;
import org.planit.output.configuration.LinkOutputTypeConfiguration;
import org.planit.output.configuration.OutputConfiguration;
import org.planit.output.enums.OutputTimeUnit;
import org.planit.output.enums.OutputType;
import org.planit.output.formatter.MemoryOutputFormatter;
import org.planit.output.property.OutputProperty;
import org.planit.sdinteraction.smoothing.MSASmoothing;
import org.planit.tntp.enums.*;
import org.planit.tntp.input.Tntp;
import org.planit.tntp.project.TntpProject;
import org.planit.trafficassignment.TraditionalStaticAssignment;
import org.planit.trafficassignment.builder.TraditionalStaticAssignmentBuilder;
import org.planit.utils.misc.IdGenerator;
import org.planit.utils.misc.Pair;

/**
 * Helper class for TNTP unit tests
 *
 * @author gman6028
 *
 */
public class TNTPTestHelper {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(TNTPTestHelper.class.getCanonicalName());


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
  public static Map<Long, Map<Long, double[]>> parseStandardResultsFile(final String standardResultsFileLocation)
      throws PlanItException {
    final Map<Long, Map<Long, double[]>> resultsMap = new HashMap<Long, Map<Long, double[]>>();
    try (Scanner scanner = new Scanner(new File(standardResultsFileLocation).getCanonicalFile())) {
      String line = scanner.nextLine();
      while (scanner.hasNextLine()) {
        line = scanner.nextLine().trim();
        final String[] cols = line.split("\\s+");
        final long upstreamNodeExternalId = Long.parseLong(cols[0]);
        if (!resultsMap.containsKey(upstreamNodeExternalId)) {
          resultsMap.put(upstreamNodeExternalId, new HashMap<Long, double[]>());
        }
        final long downstreamNodeExternalId = Long.parseLong(cols[1]);
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
   * @param outputTimeUnit the output time units
   * @param defaultMaximumSpeed the default maximum speed along links
   * @throws PlanItException thrown if there is an error
   */
  public static Pair<MemoryOutputFormatter, Tntp> execute(final String networkFileLocation,
      final String demandFileLocation,
      final int maxIterations,
      final double epsilon, final OutputTimeUnit outputTimeUnit, final double defaultMaximumSpeed) throws PlanItException {

    // SET UP INPUT SCANNER AND PROJECT
    IdGenerator.reset();

    // TODO - The following arrangement of columns is correct for Chicago Sketch and Philadelphia.
    // For some other cities the arrangement is different.
    final Map<NetworkFileColumns, Integer> networkFileColumns = new HashMap<NetworkFileColumns, Integer>();
    networkFileColumns.put(NetworkFileColumns.UPSTREAM_NODE_ID, 0);
    networkFileColumns.put(NetworkFileColumns.DOWNSTREAM_NODE_ID, 1);
    networkFileColumns.put(NetworkFileColumns.CAPACITY_PER_LANE, 2);
    networkFileColumns.put(NetworkFileColumns.LENGTH, 3);
    networkFileColumns.put(NetworkFileColumns.FREE_FLOW_TRAVEL_TIME, 4);
    networkFileColumns.put(NetworkFileColumns.B, 5);
    networkFileColumns.put(NetworkFileColumns.POWER, 6);
    networkFileColumns.put(NetworkFileColumns.MAXIMUM_SPEED, 7);
    networkFileColumns.put(NetworkFileColumns.TOLL, 8);
    networkFileColumns.put(NetworkFileColumns.LINK_TYPE, 9);

    final SpeedUnits speedUnits = SpeedUnits.MILES_H;
    final LengthUnits lengthUnits = LengthUnits.MILES; // Both Chicago-Sketch and Philadelphia use miles
    final CapacityPeriod capacityPeriod = CapacityPeriod.HOUR; // Chicago-Sketch only - for Philadelphia use days

    final Tntp tntp = new Tntp(networkFileLocation, demandFileLocation, null,
        networkFileColumns, speedUnits, lengthUnits, capacityPeriod, defaultMaximumSpeed);
    final TntpProject project = new TntpProject(tntp);

    // RAW INPUT START --------------------------------
    final MacroscopicNetwork macroscopicNetwork =
        (MacroscopicNetwork) project.createAndRegisterPhysicalNetwork(MacroscopicNetwork.class.getCanonicalName());
    final Zoning zoning = project.createAndRegisterZoning(macroscopicNetwork);
    final Demands demands = project.createAndRegisterDemands(zoning, macroscopicNetwork);

    // RAW INPUT END -----------------------------------

    // TRAFFIC ASSIGNMENT START------------------------
    final TraditionalStaticAssignmentBuilder taBuilder =
        (TraditionalStaticAssignmentBuilder) project.createAndRegisterTrafficAssignment(
            TraditionalStaticAssignment.class.getCanonicalName(), demands, zoning, macroscopicNetwork);

    // SUPPLY-DEMAND INTERACTIONS
    taBuilder.createAndRegisterPhysicalCost(BPRLinkTravelTimeCost.class.getCanonicalName());
    taBuilder.createAndRegisterVirtualCost(FixedConnectoidTravelTimeCost.class.getCanonicalName());
    taBuilder.createAndRegisterSmoothing(MSASmoothing.class.getCanonicalName());

    // DATA OUTPUT CONFIGURATION
    taBuilder.activateOutput(OutputType.LINK);
    final OutputConfiguration outputConfiguration = taBuilder.getOutputConfiguration();
    outputConfiguration.setPersistOnlyFinalIteration(true); // option to only persist the final
                                                            // iteration

    // OUTPUT FORMAT CONFIGURATION - LINKS
    final LinkOutputTypeConfiguration linkOutputTypeConfiguration =
        (LinkOutputTypeConfiguration) taBuilder.activateOutput(OutputType.LINK);
    linkOutputTypeConfiguration.addProperty(OutputProperty.LINK_TYPE);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.LINK_SEGMENT_ID);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.LINK_SEGMENT_EXTERNAL_ID);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.DOWNSTREAM_NODE_LOCATION);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.DOWNSTREAM_NODE_ID);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.UPSTREAM_NODE_LOCATION);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.UPSTREAM_NODE_ID);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.MODE_EXTERNAL_ID);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.MODE_ID);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.TIME_PERIOD_EXTERNAL_ID);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.TIME_PERIOD_ID);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.LINK_SEGMENT_ID);
    linkOutputTypeConfiguration.removeProperty(OutputProperty.MAXIMUM_SPEED);

    // MemoryOutputFormatter - Links
    final MemoryOutputFormatter memoryOutputFormatter = (MemoryOutputFormatter)
        project.createAndRegisterOutputFormatter(MemoryOutputFormatter.class.getCanonicalName());
    if (outputTimeUnit != null) {
      memoryOutputFormatter.setOutputTimeUnit(outputTimeUnit);
    }

    // taBuilder.registerOutputFormatter(csvOutputFormatter);
    taBuilder.registerOutputFormatter(memoryOutputFormatter);

    // "USER" configuration
    taBuilder.getGapFunction().getStopCriterion().setMaxIterations(maxIterations);
    taBuilder.getGapFunction().getStopCriterion().setEpsilon(epsilon);

    final Map<Long, PlanItException> exceptionMap = project.executeAllTrafficAssignments();
    if (!exceptionMap.keySet().isEmpty()) {
      for (final long id : exceptionMap.keySet()) {
        throw exceptionMap.get(id);
      }
    }
    return new Pair<MemoryOutputFormatter, Tntp>(memoryOutputFormatter, tntp);
  }
}