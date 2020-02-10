package org.planit.tntp;

import java.util.HashMap;
import java.util.Map;

import org.planit.cost.physical.BPRLinkTravelTimeCost;
import org.planit.cost.virtual.FixedConnectoidTravelTimeCost;
import org.planit.demands.Demands;
import org.planit.exceptions.PlanItException;
import org.planit.logging.PlanItLogger;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.utils.ArgumentParser;
import org.planit.utils.IdGenerator;
import org.planit.zoning.Zoning;
import org.planit.output.configuration.LinkOutputTypeConfiguration;
import org.planit.output.configuration.OriginDestinationOutputTypeConfiguration;
import org.planit.output.configuration.OutputConfiguration;
import org.planit.output.configuration.PathOutputTypeConfiguration;
import org.planit.output.enums.OutputTimeUnit;
import org.planit.output.enums.OutputType;
import org.planit.output.enums.PathIdType;
import org.planit.output.property.OutputProperty;
import org.planit.sdinteraction.smoothing.MSASmoothing;
import org.planit.tntp.enums.CapacityPeriod;
import org.planit.tntp.enums.LengthUnits;
import org.planit.tntp.enums.NetworkFileColumns;
import org.planit.tntp.enums.SpeedUnits;
import org.planit.tntp.enums.TimeUnits;
import org.planit.tntp.project.TntpProject;
import org.planit.trafficassignment.TraditionalStaticAssignment;
import org.planit.trafficassignment.builder.CapacityRestrainedTrafficAssignmentBuilder;
import org.planit.tntp.output.formatter.CSVOutputFormatter;

public class TntpMain {

  private static final int DEFAULT_MAX_ITERATIONS = 1;
  private static final double DEFAULT_CONVERGENCE_EPSILON = 0.01;

  public static void main(String[] args) {
    String networkFileLocation = null;
    String demandFileLocation = null;
    String nodeCoordinateFileLocation = null;
    String linkOutputFilename = null;
    String logfileLocation = null;
    String outputTimeUnitValue = null;
    OutputTimeUnit outputTimeUnit = null;
    String odOutputFilename = null;
    String odPathOutputFilename = null;
    int maxIterations = DEFAULT_MAX_ITERATIONS;
    double epsilon = DEFAULT_CONVERGENCE_EPSILON;
    try {
      TntpMain tntpMain = new TntpMain();
      Map<String, String> argsMap = ArgumentParser.convertArgsToMap(args);
      if (!argsMap.keySet().contains("NETWORK")) {
        throw new PlanItException("No Network input file defined");
      }
      if (!argsMap.keySet().contains("DEMANDS")) {
        throw new PlanItException("No Demands input file defined");
      }
      for (String inputFileType : argsMap.keySet()) {
        String argValue = argsMap.get(inputFileType);
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
        }
      }
      if (logfileLocation == null) {
        PlanItLogger.setLoggingToConsoleOnly(TntpMain.class);
      } else {
        PlanItLogger.setLogging(logfileLocation, TntpMain.class);
      }
      if (outputTimeUnitValue != null) {
        String outputSelection = outputTimeUnitValue.substring(0, 1).toUpperCase();
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
      tntpMain.execute(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, linkOutputFilename, odOutputFilename,
          odPathOutputFilename, maxIterations, epsilon, outputTimeUnit);
      PlanItLogger.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void execute(String networkFileLocation, String demandFileLocation, String nodeCoordinateFileLocation, String linkOutputFilename,
      String odOutputFilename, String odPathOutputFilename, int maxIterations, double epsilon,
      OutputTimeUnit outputTimeUnit) throws PlanItException {

    boolean isLinkOutputActive = (linkOutputFilename != null);
    boolean isOdOutputActive = (odOutputFilename != null);
    boolean isOdPathOutputActive = (odPathOutputFilename != null);

    // SET UP INPUT SCANNER AND PROJECT
    IdGenerator.reset();

    Map<NetworkFileColumns, Integer> networkFileColumns = new HashMap<NetworkFileColumns, Integer>();
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

    SpeedUnits speedUnits = SpeedUnits.MILES_H;
    LengthUnits lengthUnits = LengthUnits.MILES;
    TimeUnits timeUnits = TimeUnits.MINUTES;
    CapacityPeriod capacityPeriod = CapacityPeriod.DAY;

    TntpProject project = new TntpProject(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, networkFileColumns, speedUnits,
        lengthUnits, timeUnits, capacityPeriod);

    // RAW INPUT START --------------------------------
    MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork) project
        .createAndRegisterPhysicalNetwork(MacroscopicNetwork.class.getCanonicalName());
    Zoning zoning = project.createAndRegisterZoning(macroscopicNetwork);
    Demands demands = project.createAndRegisterDemands(zoning);

    // RAW INPUT END -----------------------------------

    // TRAFFIC ASSIGNMENT START------------------------
    CapacityRestrainedTrafficAssignmentBuilder taBuilder = (CapacityRestrainedTrafficAssignmentBuilder) project
        .createAndRegisterDeterministicAssignment(TraditionalStaticAssignment.class.getCanonicalName());

    // SUPPLY SIDE
    taBuilder.registerPhysicalNetwork(macroscopicNetwork);
    // SUPPLY-DEMAND INTERACTIONS
    taBuilder.createAndRegisterPhysicalCost(BPRLinkTravelTimeCost.class.getCanonicalName());
    taBuilder
        .createAndRegisterVirtualTravelTimeCostFunction(FixedConnectoidTravelTimeCost.class.getCanonicalName());
    taBuilder.createAndRegisterSmoothing(MSASmoothing.class.getCanonicalName());

    // SUPPLY-DEMAND INTERFACE
    taBuilder.registerDemandsAndZoning(demands, zoning);

    // DATA OUTPUT CONFIGURATION
    taBuilder.activateOutput(OutputType.LINK);
    OutputConfiguration outputConfiguration = taBuilder.getOutputConfiguration();
    outputConfiguration.setPersistOnlyFinalIteration(true); // option to only persist the final
                                                            // iteration

    // OUTPUT FORMAT CONFIGURATION - LINKS
    if (isLinkOutputActive) {
      LinkOutputTypeConfiguration linkOutputTypeConfiguration = (LinkOutputTypeConfiguration) taBuilder
          .activateOutput(OutputType.LINK);
      linkOutputTypeConfiguration.addProperty(OutputProperty.RUN_ID);
      linkOutputTypeConfiguration.addProperty(OutputProperty.VC_RATIO);
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
    }
    // OUTPUT FORMAT CONFIGURATION - ORIGIN-DESTINATION
    if (isOdOutputActive) {
      OriginDestinationOutputTypeConfiguration originDestinationOutputTypeConfiguration =
          (OriginDestinationOutputTypeConfiguration) taBuilder
              .activateOutput(OutputType.OD);
      originDestinationOutputTypeConfiguration.removeProperty(OutputProperty.RUN_ID);
      originDestinationOutputTypeConfiguration.addProperty(OutputProperty.ORIGIN_ZONE_ID);
      originDestinationOutputTypeConfiguration.addProperty(OutputProperty.DESTINATION_ZONE_ID);
    }
    // OUTPUT FORMAT CONFIGURATION - PATH
    if (isOdPathOutputActive) {
      PathOutputTypeConfiguration pathOutputTypeConfiguration = (PathOutputTypeConfiguration) taBuilder
          .activateOutput(OutputType.PATH);
      pathOutputTypeConfiguration.removeProperty(OutputProperty.RUN_ID);
      pathOutputTypeConfiguration.addProperty(OutputProperty.ORIGIN_ZONE_ID);
      pathOutputTypeConfiguration.addProperty(OutputProperty.DESTINATION_ZONE_ID);
      pathOutputTypeConfiguration.setPathIdType(PathIdType.LINK_SEGMENT_EXTERNAL_ID);
    }

    // CSVOutputFormatter - Links
    CSVOutputFormatter csvOutputFormatter = (CSVOutputFormatter) project.createAndRegisterOutputFormatter(
        CSVOutputFormatter.class.getCanonicalName());
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

    Map<Long, PlanItException> exceptionMap = project.executeAllTrafficAssignments();
    if (!exceptionMap.keySet().isEmpty()) {
      for (long id : exceptionMap.keySet()) {
        throw exceptionMap.get(id);
      }
    }
  }
}
