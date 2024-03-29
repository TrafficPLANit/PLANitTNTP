package org.goplanit.tntp;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.assignment.TrafficAssignment;
import org.goplanit.assignment.traditionalstatic.TraditionalStaticAssignmentConfigurator;
import org.goplanit.cost.physical.BprLinkTravelTimeCost;
import org.goplanit.cost.virtual.FixedConnectoidTravelTimeCost;
import org.goplanit.demands.Demands;
import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.output.configuration.LinkOutputTypeConfiguration;
import org.goplanit.output.configuration.OdOutputTypeConfiguration;
import org.goplanit.output.configuration.OutputConfiguration;
import org.goplanit.output.configuration.PathOutputTypeConfiguration;
import org.goplanit.output.enums.OutputType;
import org.goplanit.output.enums.PathOutputIdentificationType;
import org.goplanit.output.property.OutputPropertyType;
import org.goplanit.sdinteraction.smoothing.MSASmoothing;
import org.goplanit.tntp.enums.LengthUnits;
import org.goplanit.tntp.enums.NetworkFileColumnType;
import org.goplanit.tntp.enums.SpeedUnits;
import org.goplanit.tntp.enums.TimeUnits;
import org.goplanit.tntp.output.formatter.CSVOutputFormatter;
import org.goplanit.tntp.project.TntpProject;
import org.goplanit.utils.args.ArgumentParser;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.unit.Unit;
import org.goplanit.zoning.Zoning;

/**
 * Main class for running TNTP models
 *
 * @author gman6028
 *
 */
public class TntpMain {

  /** the logger */
  private static Logger LOGGER;

  public static final int DEFAULT_MAX_ITERATIONS = 1;
  public static final double DEFAULT_CONVERGENCE_EPSILON = 0.01;

  //Todo: this is bad, it should be moved to reader settings and it should not be so low nor based on a specific example
  // network
  public static final double DEFAULT_MAXIMUM_SPEED = 25.0; // this is the default for Chicago Sketch
                                                           // Type 3 links

 /**
  * Top-level class for running TNTP models
  *
  * @param args Command-line arguments for TNTP model
  */
  public static void main(final String[] args) {
    
    String networkFileLocation = null;
    String demandFileLocation = null;
    String nodeCoordinateFileLocation = null;
    String linkOutputFilename = null;
    String loggingPropertiesFileLocation = null;
    String outputTimeUnitValue = null;
    Unit outputTimeUnit = null;
    String odOutputFilename = null;
    String odPathOutputFilename = null;
    int maxIterations = DEFAULT_MAX_ITERATIONS;
    double epsilon = DEFAULT_CONVERGENCE_EPSILON;
    double defaultMaximumSpeed = DEFAULT_MAXIMUM_SPEED;
    boolean persistZeroFlow = false;
    
    try {
      
      final TntpMain tntpMain = new TntpMain();
      final Map<String, String> argsMap = ArgumentParser.convertArgsToMap(args);
      
      if (!argsMap.keySet().contains("NETWORK")) {
        final String errorMessage = "No Network input file defined";
        LOGGER.severe(errorMessage);
        throw new PlanItException(errorMessage);
      }
      
      if (!argsMap.keySet().contains("DEMANDS")) {
        final String errorMessage = "No Demands input file defined";
        LOGGER.severe(errorMessage);
        throw new PlanItException(errorMessage);
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
            loggingPropertiesFileLocation = argValue;
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
          case "DEFAULTMAXIMUMSPEED":
            defaultMaximumSpeed = Double.parseDouble(argValue);
            break;
          case "PERSISTZEROFLOW":
            persistZeroFlow = Boolean.parseBoolean(argValue);
        }
      }

      //If the user has specified a logging properties file use that, otherwise use the default
      if (loggingPropertiesFileLocation != null) {
        LOGGER = Logging.createLogger(TntpMain.class, loggingPropertiesFileLocation).orElseThrow(() -> new PlanItException("Unable to create TNTP logger"));
      } else {
        LOGGER = Logging.createLogger(TntpMain.class);
      }

      if (outputTimeUnitValue != null) {
        final String outputSelection = outputTimeUnitValue.substring(0, 1).toUpperCase();
        switch (outputSelection) {
          case "H":
            outputTimeUnit = Unit.HOUR;
            break;
          case "M":
            outputTimeUnit = Unit.MINUTE;
            break;
          case "S":
            outputTimeUnit = Unit.SECOND;
            break;
          default:
            final String errorMessage = "Argument OutputTimeUnit included but does not start with h, m or s.";
            LOGGER.severe(errorMessage);
            throw new PlanItException(errorMessage);
        }
      }
      
      tntpMain.execute(networkFileLocation, 
          demandFileLocation, 
          nodeCoordinateFileLocation,
          linkOutputFilename, 
          odOutputFilename,
          odPathOutputFilename, 
          persistZeroFlow, 
          maxIterations, 
          epsilon, 
          outputTimeUnit, 
          defaultMaximumSpeed);
      
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
    } finally {
      Logging.closeLogger(LOGGER);
    }
  }

  /**
   * Top-level method which runs PLANit for TNTP format input
   *
   * @param networkFileLocation the input network file (required)
   * @param demandFileLocation the input trips file (required)
   * @param nodeCoordinateFileLocation the node coordinate file (null if not included)
   * @param linkOutputFilename the link output CSV file
   * @param odOutputFilename the OD output CSV file
   * @param odPathOutputFilename the OD path output CSV file
   * @param persistZeroFlow if true record
   * @param maxIterations the maximum number of iterations
   * @param epsilon the epsilon used for convergence
   * @param outputCostTimeUnit the output time units
   * @param defaultMaximumSpeed the default maximum speed along links
   * @throws PlanItException thrown if there is an error
   */
  public void execute(
      final String networkFileLocation, 
      final String demandFileLocation,
      final String nodeCoordinateFileLocation, 
      final String linkOutputFilename,
      final String odOutputFilename, 
      final String odPathOutputFilename,
      final boolean persistZeroFlow, 
      final int maxIterations, 
      final double epsilon,
      final Unit outputCostTimeUnit, 
      final double defaultMaximumSpeed) throws PlanItException {

    final boolean isLinkOutputActive = (linkOutputFilename != null);
    final boolean isOdOutputActive = (odOutputFilename != null);
    final boolean isOdPathOutputActive = (odPathOutputFilename != null);

    //TODO - The following arrangement of columns is correct for Chicago Sketch and Philadelphia.  For some other cities the arrangement is different.
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

    final LengthUnits lengthUnits = LengthUnits.MILES; // Both Chicago-Sketch and Philadelphia use miles

    final TntpProject project = new TntpProject(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation);
    project.getNetworkReaderSettings().setNetworkFileColumns(networkFileColumns);
    project.getNetworkReaderSettings().setSpeedUnits(SpeedUnits.MILES_H);
    project.getNetworkReaderSettings().setCapacityPeriod(1, TimeUnits.HOURS);   // Chicago-Sketch only - for Philadelphia use days
    project.getNetworkReaderSettings().setDefaultMaximumSpeed(defaultMaximumSpeed);
    project.getNetworkReaderSettings().setFreeFlowTravelTimeUnits(TimeUnits.MINUTES);
    project.getNetworkReaderSettings().setLengthUnits(lengthUnits); 
    
    project.getZoningReaderSettings().setNetworkFileLocation(networkFileLocation);
    project.getDemandsReaderSettings().setTimePeriodDuration(1, TimeUnits.HOURS);
    project.getDemandsReaderSettings().setStartTimeSinceMidnight(8, TimeUnits.HOURS);
    
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
    ta.createAndRegisterPhysicalCost(BprLinkTravelTimeCost.class.getCanonicalName());
    ta.createAndRegisterVirtualCost(FixedConnectoidTravelTimeCost.class.getCanonicalName());
    ta.createAndRegisterSmoothing(MSASmoothing.class.getCanonicalName());
    
    boolean adjustCostOutputTimeUnit = false;
    if (outputCostTimeUnit != null) {
      adjustCostOutputTimeUnit = true;
    }     

    // DATA OUTPUT CONFIGURATION
    ta.activateOutput(OutputType.LINK);
    final OutputConfiguration outputConfiguration = ta.getOutputConfiguration();
    outputConfiguration.setPersistOnlyFinalIteration(true); // option to only persist the final iteration
    outputConfiguration.setPersistZeroFlow(persistZeroFlow);

    // OUTPUT FORMAT CONFIGURATION - LINKS
    if (isLinkOutputActive) {
      final LinkOutputTypeConfiguration linkOutputTypeConfiguration = (LinkOutputTypeConfiguration) outputConfiguration.getOutputTypeConfiguration(OutputType.LINK);
      linkOutputTypeConfiguration.addProperty(OutputPropertyType.LINK_SEGMENT_TYPE_NAME);
      linkOutputTypeConfiguration.addProperty(OutputPropertyType.VC_RATIO);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.LINK_SEGMENT_ID);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.LINK_SEGMENT_EXTERNAL_ID);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.DOWNSTREAM_NODE_LOCATION);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.DOWNSTREAM_NODE_ID);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.UPSTREAM_NODE_LOCATION);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.UPSTREAM_NODE_ID);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.MODE_EXTERNAL_ID);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.MODE_ID);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.TIME_PERIOD_EXTERNAL_ID);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.TIME_PERIOD_ID);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.LINK_SEGMENT_ID);
      linkOutputTypeConfiguration.removeProperty(OutputPropertyType.MAXIMUM_SPEED);
      
      if(adjustCostOutputTimeUnit == true) {
        linkOutputTypeConfiguration.overrideOutputPropertyUnits(OutputPropertyType.LINK_SEGMENT_COST, outputCostTimeUnit);
      }      
    }
    // OUTPUT FORMAT CONFIGURATION - ORIGIN-DESTINATION
    if (isOdOutputActive) {
      final OdOutputTypeConfiguration originDestinationOutputTypeConfiguration =
          (OdOutputTypeConfiguration) outputConfiguration.getOutputTypeConfiguration(OutputType.OD);
      originDestinationOutputTypeConfiguration.removeProperty(OutputPropertyType.RUN_ID);
      originDestinationOutputTypeConfiguration.addProperty(OutputPropertyType.ORIGIN_ZONE_ID);
      originDestinationOutputTypeConfiguration.addProperty(OutputPropertyType.DESTINATION_ZONE_ID);
      
      if(adjustCostOutputTimeUnit == true) {
        originDestinationOutputTypeConfiguration.overrideOutputPropertyUnits(OutputPropertyType.OD_COST, outputCostTimeUnit);
      }       
    }
    // OUTPUT FORMAT CONFIGURATION - PATH
    if (isOdPathOutputActive) {
      final PathOutputTypeConfiguration pathOutputTypeConfiguration =
          (PathOutputTypeConfiguration) outputConfiguration.getOutputTypeConfiguration(OutputType.PATH);
      pathOutputTypeConfiguration.removeProperty(OutputPropertyType.RUN_ID);
      pathOutputTypeConfiguration.addProperty(OutputPropertyType.ORIGIN_ZONE_ID);
      pathOutputTypeConfiguration.addProperty(OutputPropertyType.DESTINATION_ZONE_ID);
      pathOutputTypeConfiguration.setPathIdentificationType(PathOutputIdentificationType.LINK_SEGMENT_EXTERNAL_ID);
    }

    // CSVOutputFormatter - Links
    final CSVOutputFormatter csvOutputFormatter =
        (CSVOutputFormatter) project.createAndRegisterOutputFormatter(CSVOutputFormatter.class.getCanonicalName());
    if (isLinkOutputActive) {
      csvOutputFormatter.addCsvFileNamePerOutputType(OutputType.LINK, linkOutputFilename);
    }
    if (isOdOutputActive) {
      csvOutputFormatter.addCsvFileNamePerOutputType(OutputType.OD, odOutputFilename);
    }
    if (isOdPathOutputActive) {
      csvOutputFormatter.addCsvFileNamePerOutputType(OutputType.PATH, odPathOutputFilename);
    }
    ta.registerOutputFormatter(csvOutputFormatter);

    // "USER" configuration
    ta.getGapFunction().getStopCriterion().setMaxIterations(maxIterations);
    ta.getGapFunction().getStopCriterion().setEpsilon(epsilon);

    project.executeAllTrafficAssignments();
  }
}