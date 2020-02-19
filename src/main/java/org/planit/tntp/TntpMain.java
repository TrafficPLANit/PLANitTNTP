package org.planit.tntp;

import java.util.HashMap;
import java.util.Map;

import org.planit.cost.physical.BPRLinkTravelTimeCost;
import org.planit.cost.virtual.FixedConnectoidTravelTimeCost;
import org.planit.demands.Demands;
import org.planit.exceptions.PlanItException;
import org.planit.logging.PlanItLogger;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.network.virtual.Zoning;
import org.planit.output.configuration.LinkOutputTypeConfiguration;
import org.planit.output.configuration.OriginDestinationOutputTypeConfiguration;
import org.planit.output.configuration.OutputConfiguration;
import org.planit.output.configuration.PathOutputTypeConfiguration;
import org.planit.output.enums.OutputTimeUnit;
import org.planit.output.enums.OutputType;
import org.planit.output.enums.RouteIdType;
import org.planit.output.property.OutputProperty;
import org.planit.sdinteraction.smoothing.MSASmoothing;
import org.planit.tntp.enums.CapacityPeriod;
import org.planit.tntp.enums.LengthUnits;
import org.planit.tntp.enums.NetworkFileColumns;
import org.planit.tntp.enums.SpeedUnits;
import org.planit.tntp.output.formatter.CSVOutputFormatter;
import org.planit.tntp.project.TntpProject;
import org.planit.trafficassignment.TraditionalStaticAssignment;
import org.planit.trafficassignment.builder.TraditionalStaticAssignmentBuilder;
import org.planit.utils.ArgumentParser;
import org.planit.utils.misc.IdGenerator;

public class TntpMain {

  public static final int DEFAULT_MAX_ITERATIONS = 1;
  public static final double DEFAULT_CONVERGENCE_EPSILON = 0.01;
  public static final double DEFAULT_MAXIMUM_SPEED = 25.0; // this is the default for Chicago Sketch
                                                           // Type 3 links

  public static void main(final String[] args) {
    String networkFileLocation = null;
    String demandFileLocation = null;
    String nodeCoordinateFileLocation = null;
    String standardResultsFileLocation = null;
    String linkOutputFilename = null;
    String logfileLocation = null;
    String outputTimeUnitValue = null;
    OutputTimeUnit outputTimeUnit = null;
    String odOutputFilename = null;
    String odPathOutputFilename = null;
    int maxIterations = DEFAULT_MAX_ITERATIONS;
    double epsilon = DEFAULT_CONVERGENCE_EPSILON;
    double defaultMaximumSpeed = DEFAULT_MAXIMUM_SPEED;
    try {
      final TntpMain tntpMain = new TntpMain();
      final Map<String, String> argsMap = ArgumentParser.convertArgsToMap(args);
      if (!argsMap.keySet().contains("NETWORK")) {
        throw new PlanItException("No Network input file defined");
      }
      if (!argsMap.keySet().contains("DEMANDS")) {
        throw new PlanItException("No Demands input file defined");
      }
      for (final String inputFileType : argsMap.keySet()) {
        final String argValue = argsMap.get(inputFileType);
        switch (inputFileType) {
          case "NETWORK":
            networkFileLocation = argValue;
            break;
          case "DEMANDS":
            demandFileLocation = argValue;
            break;
          case "NODECOORDINATES":
            nodeCoordinateFileLocation = argValue;
            break;
          case "LINKOUTPUT":
            linkOutputFilename = argValue;
            break;
          case "MAXITERATIONS":
            maxIterations = Integer.parseInt(argValue);
            break;
          case "EPSILON":
            epsilon = Double.parseDouble(argValue);
            break;
          case "LOGFILE":
            logfileLocation = argValue;
            break;
          case "OUTPUTTIMEUNIT":
            outputTimeUnitValue = argValue;
            break;
          case "ODOUTPUT":
            odOutputFilename = argValue;
            break;
          case "ODPATHOUTPUT":
            odPathOutputFilename = argValue;
            break;
          case "STANDARDRESULTS":
            standardResultsFileLocation = argValue;
            break;
          case "DEFAULTMAXIMUMSPEED":
            defaultMaximumSpeed = Double.parseDouble(argValue);
        }
      }
      if (logfileLocation == null) {
        PlanItLogger.setLoggingToConsoleOnly(TntpMain.class);
      } else {
        PlanItLogger.setLogging(logfileLocation, TntpMain.class);
      }
      if (outputTimeUnitValue != null) {
        final String outputSelection = outputTimeUnitValue.substring(0, 1).toUpperCase();
        switch (outputSelection) {
          case "H":
            outputTimeUnit = OutputTimeUnit.HOURS;
            break;
          case "M":
            outputTimeUnit = OutputTimeUnit.MINUTES;
            break;
          case "S":
            outputTimeUnit = OutputTimeUnit.SECONDS;
            break;
          default:
            throw new PlanItException("Argument OutputTimeUnit included but does not start with h, m or s.");
        }
      }
      tntpMain.execute(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, standardResultsFileLocation,
          linkOutputFilename, odOutputFilename,
          odPathOutputFilename, maxIterations, epsilon, outputTimeUnit, defaultMaximumSpeed);
      PlanItLogger.close();
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Top-level method which runs PLANit for TNTP format input
   *
   * @param networkFileLocation the input network file (required)
   * @param demandFileLocation the input trips file (required)
   * @param nodeCoordinateFileLocation the node coordinate file (null if not included)
   * @param standardResultsFileLocation the standard results file used to check output results (null
   *          if not included)
   * @param linkOutputFilename the link output CSV file
   * @param odOutputFilename the OD output CSV file
   * @param odPathOutputFilename the OD path output CSV file
   * @param maxIterations the maximum number of iterations
   * @param epsilon the epsilon used for convergence
   * @param outputTimeUnit the output time units
   * @param defaultMaximumSpeed the default maximum speed along links
   * @return Map of standard results for each link (null if the standardResultsFileLocation is not
   *         included)
   * @throws PlanItException thrown if there is an error
   */
  public Map<Long, Map<Long, double[]>> execute(final String networkFileLocation, final String demandFileLocation,
      final String nodeCoordinateFileLocation, final String standardResultsFileLocation,
      final String linkOutputFilename,
      final String odOutputFilename, final String odPathOutputFilename, final int maxIterations, final double epsilon,
      final OutputTimeUnit outputTimeUnit, final double defaultMaximumSpeed) throws PlanItException {

    final boolean isLinkOutputActive = (linkOutputFilename != null);
    final boolean isOdOutputActive = (odOutputFilename != null);
    final boolean isOdPathOutputActive = (odPathOutputFilename != null);

    // SET UP INPUT SCANNER AND PROJECT
    IdGenerator.reset();

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
    final LengthUnits lengthUnits = LengthUnits.MILES; // Both Chicago-Sketch and Philadelphia use
                                                       // miles
    final CapacityPeriod capacityPeriod = CapacityPeriod.HOUR; // Chicago-Sketch only - for
                                                               // Philadelphia use days

    final TntpProject project = new TntpProject(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation,
        networkFileColumns, speedUnits,
        lengthUnits, capacityPeriod, defaultMaximumSpeed);

    // RAW INPUT START --------------------------------
    final MacroscopicNetwork macroscopicNetwork =
        (MacroscopicNetwork) project.createAndRegisterPhysicalNetwork(MacroscopicNetwork.class.getCanonicalName());
    final Zoning zoning = project.createAndRegisterZoning(macroscopicNetwork);
    final Demands demands = project.createAndRegisterDemands(zoning);
    final Map<Long, Map<Long, double[]>> standardResults =
        (standardResultsFileLocation == null) ? null : project.createStandardResultsFile(standardResultsFileLocation);

    // RAW INPUT END -----------------------------------

    // TRAFFIC ASSIGNMENT START------------------------
    final TraditionalStaticAssignmentBuilder taBuilder =
        (TraditionalStaticAssignmentBuilder) project.createAndRegisterTrafficAssignment(
            TraditionalStaticAssignment.class.getCanonicalName(), demands, zoning, macroscopicNetwork);

    // SUPPLY-DEMAND INTERACTIONS
    taBuilder.createAndRegisterPhysicalCost(BPRLinkTravelTimeCost.class.getCanonicalName());
    taBuilder.createAndRegisterVirtualTravelTimeCostFunction(FixedConnectoidTravelTimeCost.class.getCanonicalName());
    taBuilder.createAndRegisterSmoothing(MSASmoothing.class.getCanonicalName());

    // DATA OUTPUT CONFIGURATION
    taBuilder.activateOutput(OutputType.LINK);
    final OutputConfiguration outputConfiguration = taBuilder.getOutputConfiguration();
    outputConfiguration.setPersistOnlyFinalIteration(true); // option to only persist the final
                                                            // iteration

    // OUTPUT FORMAT CONFIGURATION - LINKS
    if (isLinkOutputActive) {
      final LinkOutputTypeConfiguration linkOutputTypeConfiguration = (LinkOutputTypeConfiguration) taBuilder
          .activateOutput(OutputType.LINK);
      linkOutputTypeConfiguration.addProperty(OutputProperty.LINK_TYPE);
      linkOutputTypeConfiguration.addProperty(OutputProperty.VC_RATIO);
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
      linkOutputTypeConfiguration.removeProperty(OutputProperty.DENSITY);
      linkOutputTypeConfiguration.removeProperty(OutputProperty.MAXIMUM_SPEED);
    }
    // OUTPUT FORMAT CONFIGURATION - ORIGIN-DESTINATION
    if (isOdOutputActive) {
      final OriginDestinationOutputTypeConfiguration originDestinationOutputTypeConfiguration =
          (OriginDestinationOutputTypeConfiguration) taBuilder.activateOutput(OutputType.OD);
      originDestinationOutputTypeConfiguration.removeProperty(OutputProperty.RUN_ID);
      originDestinationOutputTypeConfiguration.addProperty(OutputProperty.ORIGIN_ZONE_ID);
      originDestinationOutputTypeConfiguration.addProperty(OutputProperty.DESTINATION_ZONE_ID);
    }
    // OUTPUT FORMAT CONFIGURATION - PATH
    if (isOdPathOutputActive) {
      final PathOutputTypeConfiguration pathOutputTypeConfiguration =
          (PathOutputTypeConfiguration) taBuilder.activateOutput(OutputType.PATH);
      pathOutputTypeConfiguration.removeProperty(OutputProperty.RUN_ID);
      pathOutputTypeConfiguration.addProperty(OutputProperty.ORIGIN_ZONE_ID);
      pathOutputTypeConfiguration.addProperty(OutputProperty.DESTINATION_ZONE_ID);
      pathOutputTypeConfiguration.setPathIdType(RouteIdType.LINK_SEGMENT_EXTERNAL_ID);
    }

    // CSVOutputFormatter - Links
    final CSVOutputFormatter csvOutputFormatter =
        (CSVOutputFormatter) project.createAndRegisterOutputFormatter(CSVOutputFormatter.class.getCanonicalName());
    if (outputTimeUnit != null) {
      csvOutputFormatter.setOutputTimeUnit(outputTimeUnit);
    }
    if (isLinkOutputActive) {
      csvOutputFormatter.addCsvFileNamePerOutputType(OutputType.LINK, linkOutputFilename);
    }
    if (isOdOutputActive) {
      csvOutputFormatter.addCsvFileNamePerOutputType(OutputType.OD, odOutputFilename);
    }
    if (isOdPathOutputActive) {
      csvOutputFormatter.addCsvFileNamePerOutputType(OutputType.PATH, odPathOutputFilename);
    }
    taBuilder.registerOutputFormatter(csvOutputFormatter);

    // "USER" configuration
    taBuilder.getGapFunction().getStopCriterion().setMaxIterations(maxIterations);
    taBuilder.getGapFunction().getStopCriterion().setEpsilon(epsilon);

    final Map<Long, PlanItException> exceptionMap = project.executeAllTrafficAssignments();
    if (!exceptionMap.keySet().isEmpty()) {
      for (final long id : exceptionMap.keySet()) {
        throw exceptionMap.get(id);
      }
    }
    return standardResults;
  }
}
