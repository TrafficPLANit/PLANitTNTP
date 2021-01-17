package org.planit.tntp.input;

import java.io.File;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import org.djutils.event.EventInterface;
import org.planit.assignment.TrafficAssignmentComponentFactory;
import org.planit.cost.physical.BPRLinkTravelTimeCost;
import org.planit.cost.physical.AbstractPhysicalCost;
import org.planit.demands.Demands;
import org.planit.geo.PlanitJtsUtils;
import org.planit.input.InputBuilderListener;
import org.planit.network.physical.PhysicalNetwork;
import org.planit.network.physical.macroscopic.MacroscopicModePropertiesFactory;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.network.virtual.Zoning;
import org.planit.od.odmatrix.demand.ODDemandMatrix;
import org.planit.time.TimePeriod;
import org.planit.tntp.enums.CapacityPeriod;
import org.planit.tntp.enums.LengthUnits;
import org.planit.tntp.enums.NetworkFileColumns;
import org.planit.tntp.enums.SpeedUnits;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.math.Precision;
import org.planit.utils.misc.LoggingUtils;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.PredefinedModeType;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.LinkSegment;
import org.planit.utils.network.physical.LinkSegments;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;
import org.planit.utils.network.physical.macroscopic.MacroscopicModeProperties;
import org.planit.utils.network.virtual.Centroid;
import org.planit.utils.network.virtual.Connectoid;
import org.planit.utils.network.virtual.Zone;

import org.locationtech.jts.geom.Point;

/**
 * Class which reads input from TNTP files
 *
 * @author gman6028
 *
 */
public class TntpInputBuilder extends InputBuilderListener {

  /** generated UID */
  private static final long serialVersionUID = 1L;

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(TntpInputBuilder.class.getCanonicalName());

  /**
   * network data file
   */
  private File networkFile;

  /**
   * demand data file
   */
  private File demandFile;

  /**
   * node coordinate data file
   */
  private File nodeCoordinateFile;

  /**
   * Map specifying which columns in the network file contain which values
   */
  private Map<NetworkFileColumns, Integer> networkFileColumns;

  /**
   * Units of speed used in network input file
   */
  private SpeedUnits speedUnits;

  /**
   * Units of length used in network input file
   */
  private LengthUnits lengthUnits;

  /**
   * Time period for link capacity
   */
  private CapacityPeriod capacityPeriod;

  /**
   * TNTP only has one mode
   */
  private Mode mode;

  /**
   * TNTP only has one time period
   */
  private TimePeriod timePeriod;

  /**
   * The number of zones in the network
   */
  private int noZones;

  /**
   * The number of nodes in the network according to TNTP
   */
  private int noPhysicalNodes;

  /**
   * The number of links in the network according to TNTP
   */
  private int noLinks;

  /**
   * List of link segments in the network
   */
  private LinkSegments<? extends MacroscopicLinkSegment> linkSegments;

 /**
   * Map containing the BPR parameters for each link segment, if these are specified in the
   * network file (null if default values are being used)
   */
  private Map<LinkSegment, Pair<Double, Double>> bprParametersForLinkSegmentAndMode;

  /**
   * Default maximum speed across links
   */
  private double defaultMaximumSpeed;

  public static final int ONE_WAY_AB = 1;
  public static final int ONE_WAY_BA = 2;
  public static final int TWO_WAY = 3;

  public static final String NUMBER_OF_ZONES_INDICATOR = "<NUMBER OF ZONES>";
  public static final String NUMBER_OF_NODES_INDICATOR = "<NUMBER OF NODES>";
  public static final String NUMBER_OF_LINKS_INDICATOR = "<NUMBER OF LINKS>";
  public static final String END_OF_METADATA_INDICATOR = "<END OF METADATA>";

  /**
   * Create and register a new node if it does not already exist
   *
   * @param network the current physical network
   * @param cols the columns in the network input file
   * @param networkFileColumn the column in the network file which contains the node external Id
   * @return the node corresponding to this external ID
   * @throws PlanItException thrown if there is an error registering the node
   */
  private Node createAndRegisterNode(final PhysicalNetwork<?,?,?> network, final String[] cols, final NetworkFileColumns networkFileColumn)
      throws PlanItException {
    
    final String nodeSourceId = cols[networkFileColumns.get(networkFileColumn)];
    if ( Long.parseLong(nodeSourceId) > noPhysicalNodes) {
      throw new PlanItException("Number of nodes is specified as " + noPhysicalNodes + " but found a reference to node " + nodeSourceId);
    }
    Node node = null;
    if (getNodeBySourceId(nodeSourceId) == null) {      
      node = network.nodes.registerNew();
      /* XML id */
      node.setXmlId(Long.toString(node.getId()));    
      /* external id */
      node.setExternalId(nodeSourceId);
      final boolean duplicateNodeExternalId = addNodeToSourceIdMap(nodeSourceId, node);
      if (duplicateNodeExternalId && isErrorIfDuplicateSourceId()) {
        throw new PlanItException("Duplicate node external id " + nodeSourceId + " found in network file");
      }
    } else {
      node = getNodeBySourceId(nodeSourceId);
    }
    return node;
  }

  /**
   * Parse a value from one of the headers in the network file
   *
   * @param line the current line in the network file
   * @param header the header to be parsed
   * @return the integer value contained within the specified header section
   * @throws Exception thrown if the contents of the header cannot be parsed into an integer
   */
  private int parseFromHeader(final String line, final String header) throws Exception {
    final String subLine = line.substring(header.length()).trim();
    return Integer.parseInt(subLine);
  }

  /**
   * Update the OD demand matrix with demands from a specified origin zone
   *
   * @param demandToDestination Map of demands (value) from the current origin to specified
   *          destination zones (key)
   * @param zoning the current zoning object
   * @param originZone the origin zone for all the demand values
   * @param odDemandMatrix the ODDemandMatrix object to be updated
   */
  private void updateOdDemandMatrix(final Map<String, Double> demandToDestination, final Zoning zoning,
      final Zone originZone, final ODDemandMatrix odDemandMatrix) {
    
    for (final String destinationZoneSourceId : demandToDestination.keySet()) {
      final Zone destinationZone = getZoneBySourceId(destinationZoneSourceId);
      odDemandMatrix.setValue(originZone, destinationZone, demandToDestination.get(destinationZoneSourceId));
    }
    
  }

  /**
   * Create and register a new link segment
   *
   * February 2020: We do not understand how the flow times for link types 1 and 2 are calculated.
   * Link type 3 is the only one for which our results match the published results.
   *
   * @param network the current macroscopic network
   * @param link the current link
   * @param maxSpeed the maximum speed for this link
   * @param capacityPerLane the capacity per lane for this link
   * @param linkSegmentTypeExternalId the external Id of the type of this link segment
   * @param length the length of this link segment
   * @param freeFlowTravelTime the free flow travel time for this link segment
   * @return the macroscopic link segment which has been created
   * @throws PlanItException thrown if there is an error
   */
  private MacroscopicLinkSegment createAndRegisterLinkSegment(
      final MacroscopicNetwork network, final Link link, final long tntpLinkSegmentSourceId, final String[] cols) throws PlanItException {
    
    /* max speed */
    double maxSpeed = defaultMaximumSpeed;
    final double speed = Double.parseDouble(cols[networkFileColumns.get(NetworkFileColumns.MAXIMUM_SPEED)]);
    if (speed > Precision.EPSILON_6 && speed < Double.POSITIVE_INFINITY) {
      maxSpeed = speed * speedUnits.getMultiplier();
    }
    
    /* free flow travel time */
    final double freeFlowTravelTime = Double.parseDouble(cols[networkFileColumns.get(NetworkFileColumns.FREE_FLOW_TRAVEL_TIME)]);    
    
    /* capacity */
    final double capacityPerLane = Integer.parseInt(cols[networkFileColumns.get(NetworkFileColumns.CAPACITY_PER_LANE)]) * capacityPeriod.getMultiplier();
    
    /* link segment type */
    final int linkSegmentTypeSourceId = Integer.parseInt(cols[networkFileColumns.get(NetworkFileColumns.LINK_TYPE)]);    
    
    /* mode properties */
    MacroscopicModeProperties macroscopicModeProperties = null;
    double freeflowSpeed = defaultMaximumSpeed * speedUnits.getMultiplier();
    switch (linkSegmentTypeSourceId) {
      case 1:
        freeflowSpeed = (link.getLengthKm() / freeFlowTravelTime) * speedUnits.getMultiplier();
        break;
      case 2:
        freeflowSpeed = (link.getLengthKm() / freeFlowTravelTime) * speedUnits.getMultiplier();
        break;
      case 3:
        // already correct fall through
        break;
      default:
        throw new PlanItException("incorrect external id type encountered");
    }
    final Map<Mode, MacroscopicModeProperties> modePropertiesMap = new HashMap<Mode, MacroscopicModeProperties>();    
    macroscopicModeProperties = MacroscopicModePropertiesFactory.create(freeflowSpeed, freeflowSpeed);
    modePropertiesMap.put(mode, macroscopicModeProperties);

    final MacroscopicLinkSegment linkSegment = network.linkSegments.registerNew(link, true, true);
    /* XML id */
    linkSegment.setXmlId(Long.toString(linkSegment.getId()));
    /* external id */    
    linkSegment.setExternalId(String.valueOf(tntpLinkSegmentSourceId));
    if (linkSegment.getExternalId() != null) {
      final boolean duplicateLinkSegmentExternalId = addLinkSegmentToSourceIdMap(linkSegment.getExternalId(),linkSegment);
      if (duplicateLinkSegmentExternalId && isErrorIfDuplicateSourceId()) {
        throw new PlanItException("Duplicate link segment external id " + linkSegment.getExternalId()+ " found in network file");
      }
    }    

    /** Link segment type **/
    String linkSegmentTypeSourceIdString = String.valueOf(linkSegmentTypeSourceId);
    MacroscopicLinkSegmentType linkSegmentType = getLinkSegmentTypeBySourceId(linkSegmentTypeSourceIdString);
    if (linkSegmentType == null) {
      linkSegmentType = network.linkSegmentTypes.createAndRegisterNew( 
          linkSegmentTypeSourceIdString, capacityPerLane, MacroscopicLinkSegmentType.DEFAULT_MAX_DENSITY_LANE, modePropertiesMap);
      /* XML id */
      linkSegmentType.setXmlId(Long.toString(linkSegmentType.getId()));
      /* external id */
      linkSegmentType.setExternalId(linkSegmentTypeSourceIdString);
      addLinkSegmentTypeToSourceIdMap(linkSegmentType.getExternalId(), linkSegmentType);
    }
    linkSegment.setLinkSegmentType(linkSegmentType);    
    linkSegment.getLinkSegmentType().getModeProperties(mode).setMaximumSpeedKmH(maxSpeed);
    

    return linkSegment;
  }


  /**
   * Update the node coordinates from the node coordinate file
   *
   * @param network the physical network object to be populated from the input data
   * @throws PlanItException thrown if there is an error reading the input file
   */
  private void updateNodeCoordinatesFromFile(final PhysicalNetwork<?,?,?> network) throws PlanItException {
    try (Scanner scanner = new Scanner(nodeCoordinateFile)) {
      while (scanner.hasNextLine()) {
        final String line = scanner.nextLine().trim();
        line.replaceAll(";", "");
        final char firstChar = line.charAt(0);
        if (Character.isDigit(firstChar)) {
          final String[] cols = line.split("\\s+");
          final String nodeSourceId = cols[0];

          final Node node = getNodeBySourceId(nodeSourceId);
          Point nodePosition = PlanitJtsUtils.createPoint(Double.parseDouble(cols[1]), Double.parseDouble(cols[2]));          
          node.setPosition(nodePosition);
        }
      }
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when updating node coordinates from file in TNTP",e);
    }
  }

  /**
   * Read network metadata from the top of the network input file
   *
   * @param line the current line in the network input file
   * @throws Exception thrown if the contents of the header cannot be parsed into an integer
   */
  private void readNetworkMetadata(final String line) throws Exception {
    if (line.startsWith(NUMBER_OF_ZONES_INDICATOR)) {
      noZones = parseFromHeader(line, NUMBER_OF_ZONES_INDICATOR);
    } else if (line.startsWith(NUMBER_OF_NODES_INDICATOR)) {
      noPhysicalNodes = parseFromHeader(line, NUMBER_OF_NODES_INDICATOR);
    } else if (line.startsWith(NUMBER_OF_LINKS_INDICATOR)) {
      noLinks = parseFromHeader(line, NUMBER_OF_LINKS_INDICATOR);
    }
  }

  /**
   * Create and register the nodes, links and link segments from the current line in the network
   * input file
   *
   * @param network the macroscopic network object to be populated from the input data
   * @param line the current line in the network input file
   * @param tntpLinkSegmentSourceId the external Id for the current line segment
   * @throws PlanItException thrown if there is an error
   */
  private void readLinkData(final MacroscopicNetwork network, final String line, final long tntpLinkSegmentSourceId)
      throws PlanItException {
    final String[] cols = line.split("\\s+");

    final Node upstreamNode = createAndRegisterNode(network, cols, NetworkFileColumns.UPSTREAM_NODE_ID);
    final Node downstreamNode = createAndRegisterNode(network, cols, NetworkFileColumns.DOWNSTREAM_NODE_ID);

    /** LINK **/
    final double length = Double.parseDouble(cols[networkFileColumns.get(NetworkFileColumns.LENGTH)]) * lengthUnits.getMultiplier();
    final Link link = network.links.registerNew(upstreamNode, downstreamNode, length);
    /* XML id */
    link.setXmlId(Long.toString(link.getId()));
    /* link external id */
    link.setExternalId(String.valueOf(tntpLinkSegmentSourceId));
    
    /** LINK SEGMENT/TYPE **/    
    final MacroscopicLinkSegment linkSegment = createAndRegisterLinkSegment(network, link, tntpLinkSegmentSourceId, cols);

    /** MODE PARAMETERS **/
    double alpha = BPRLinkTravelTimeCost.DEFAULT_ALPHA;
    double beta = BPRLinkTravelTimeCost.DEFAULT_BETA;
    boolean settingAlpha = false;
    if (networkFileColumns.keySet().contains(NetworkFileColumns.B)) {
      alpha = Double.parseDouble(cols[networkFileColumns.get(NetworkFileColumns.B)]);
      settingAlpha = true;
    }
    boolean settingBeta = false;
    if (networkFileColumns.keySet().contains(NetworkFileColumns.POWER)) {
      beta = Double.parseDouble(cols[networkFileColumns.get(NetworkFileColumns.POWER)]);
      settingBeta = true;
    }
    if (settingAlpha || settingBeta) {
      addBprParametersForLinkSegmentAndMode(linkSegment, alpha, beta);
    }
  }

  /**
   * Add BPR parameters for a specified link segmen
   *
   * @param linkSegment the specified link segment
   * @param alpha the BPR alpha parameter
   * @param beta the BPR beta parameter
   */
  private void addBprParametersForLinkSegmentAndMode(final LinkSegment linkSegment, final double alpha,
      final double beta) {
    if (bprParametersForLinkSegmentAndMode == null) {
      bprParametersForLinkSegmentAndMode = new HashMap<LinkSegment, Pair<Double, Double>>();
    }
    final Pair<Double, Double> alphaBeta = Pair.create(alpha, beta);
    bprParametersForLinkSegmentAndMode.put(linkSegment, alphaBeta);
  }

  /**
   * Creates the physical network object from the data in the input file
   *
   * @param physicalNetwork the physical network object to be populated from the input data
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populatePhysicalNetwork( final PhysicalNetwork<?,?,?> physicalNetwork) throws PlanItException {
    LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"populating Physical Network");

    final MacroscopicNetwork network = (MacroscopicNetwork) physicalNetwork;
    // TNTP only has one mode, define it here
    mode = network.modes.registerNew(PredefinedModeType.CAR);
    /* external id */
    mode.setExternalId("1"); //TODO wrong because no external id is available, but tests use it --> refactor
    addModeToSourceIdMap(mode.getExternalId(), mode);

    try (Scanner scanner = new Scanner(networkFile)) {
      boolean readingMetadata = true;
      boolean readingLinkData = false;
      long tntpLinkSegmentSourceId = 0;
      while (scanner.hasNextLine()) {
        final String line = scanner.nextLine().trim();
        final char firstChar = (line.isEmpty()) ? 'x' : line.charAt(0);
        final boolean atEndOfMetadata = line.equals(END_OF_METADATA_INDICATOR);
        if (atEndOfMetadata) {
          readingMetadata = false;
        }
        if (readingMetadata) {
          readNetworkMetadata(line);
        } else if (!atEndOfMetadata) {
          if (firstChar == '~') {
            readingLinkData = true;
          } else if (readingLinkData) {
            tntpLinkSegmentSourceId++;
            readLinkData(network, line, tntpLinkSegmentSourceId);
          }
        }
      }

      if (tntpLinkSegmentSourceId != noLinks) {
        final String errorMessage = "Header says " + noLinks + " links but " + tntpLinkSegmentSourceId+ " were actually defined.";
        LOGGER.severe(errorMessage);
        throw new PlanItException(errorMessage);
      }
    } catch (final PlanItException e) {
      throw e;
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when populating physical network in TNTP",e);
    }

    if (nodeCoordinateFile != null) {
      updateNodeCoordinatesFromFile(network);
    }

    linkSegments = network.linkSegments;

  }

  /**
   * Populates the Demands object from the input file
   *
   * @param demands the Demands object to be populated from the input data
   * @param parameter1 Zoning object previously defined
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateDemands( final Demands demands, final Object parameter1) throws PlanItException {
    LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"populating Demands");
    final Zoning zoning = (Zoning) parameter1;
    
    // TNTP only has one time period, define it here
    int wholeDaydurationSeconds = 24*3600;
    int startAtMidNightSeconds = 0;
    timePeriod = demands.timePeriods.createAndRegisterNewTimePeriod("All Day", startAtMidNightSeconds, wholeDaydurationSeconds);
    /* XML id */
    timePeriod.setXmlId(Long.toString(timePeriod.getId()));
    /* external id */
    timePeriod.setExternalId("1"); //TODO wrong because no external id is available, but tests use it --> refactor
    addTimePeriodToSourceIdMap(timePeriod.getExternalId(), timePeriod);    
    
    try (Scanner scanner = new Scanner(demandFile)) {
      boolean readingMetadata = true;
      Zone originZone = null;
      Map<String, Double> demandToDestination = null;
      final ODDemandMatrix odDemandMatrix = new ODDemandMatrix(zoning.zones);
      while (scanner.hasNextLine()) {
        final String line = scanner.nextLine().trim();
        final char firstChar = (line.isEmpty()) ? 'x' : line.charAt(0);
        final boolean atEndOfMetadata = line.equals("<END OF METADATA>");
        if (atEndOfMetadata) {
          readingMetadata = false;
        }
        if (readingMetadata) {
          if (line.startsWith(NUMBER_OF_ZONES_INDICATOR)) {
            final String subLine = line.substring(NUMBER_OF_ZONES_INDICATOR.length()).trim();
            if (noZones != Integer.parseInt(subLine)) {
              throw new PlanItException("Network file indicates there are " + noZones + " but demand file indicates there are " + Integer.parseInt(subLine) + " zones");
            }
          }
        } else if (!atEndOfMetadata) {
          if ((!line.isEmpty()) && (firstChar != '~')) {
            if (line.startsWith("Origin")) {
              if (demandToDestination != null) {
                updateOdDemandMatrix(demandToDestination, zoning, originZone, odDemandMatrix);
              }
              final String[] cols = line.split("\\s+");
              originZone = getZoneBySourceId(cols[1]);
              demandToDestination = new HashMap<String, Double>();
            } else {
              final String lineWithNoSpaces = line.replaceAll("\\s", "");
              final String[] destDemand = lineWithNoSpaces.split("[:;]");
              for (int i = 0; i < destDemand.length; i += 2) {
                demandToDestination.put(destDemand[i], Double.parseDouble(destDemand[i + 1]));
              }
            }
          }
        }
      }
      scanner.close();
      updateOdDemandMatrix(demandToDestination, zoning, originZone, odDemandMatrix);
      demands.registerODDemand(timePeriod, mode, odDemandMatrix);
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when populating demands in TNTP",e);
    }
  }

  /**
   * Creates the Zoning object and connectoids from the data in the network file
   *
   * In TNTP this method does not need to read a TAZ file. It simply uses the number of zones, which
   * has already been read in from the network file.
   *
   * @param zoning the Zoning object to be populated from the input data
   * @param parameter1 the physical network object previously created
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateZoning(final Zoning zoning, final Object parameter1) throws PlanItException {
    LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"populating zoning");
    for (long zoneSourceId = 1; zoneSourceId <= noZones; zoneSourceId++) {
      /* ZONE */
      final Zone zone = zoning.zones.createAndRegisterNewZone();
      /* XML id */
      zone.setXmlId(Long.toString(zone.getId()));      
      /* external id */
      zone.setExternalId(String.valueOf(zoneSourceId));
      addZoneToSourceIdMap(zone.getExternalId(), zone);
      final Centroid centroid = zone.getCentroid();
      
      /* CONNECTOID */
      final Node node = getNodeBySourceId(zone.getExternalId());
      // TODO - calculate connectoid length
      final double connectoidLength = 1.0;
      Connectoid connectoid = zoning.getVirtualNetwork().connectoids.registerNewConnectoid(centroid, node, connectoidLength);
      /* XML id */
      connectoid.setXmlId(Long.toString(connectoid.getId()));
      /* external id */
      connectoid.setExternalId(zone.getExternalId());
    }
  }

  /**
   * Populate the BPR parameters
   *
   * @param costComponent the BPRLinkTravelTimeCost to be populated
   * @throws PlanItException thrown if there is an error
   */
  protected void populatePhysicalCost( final AbstractPhysicalCost costComponent) throws PlanItException {
    LOGGER.info(LoggingUtils.getClassNameWithBrackets(this)+"populating BPR link costs");
    if (bprParametersForLinkSegmentAndMode != null) {
      final BPRLinkTravelTimeCost bprLinkTravelTimeCost = (BPRLinkTravelTimeCost) costComponent;
      for (final LinkSegment linkSegment : linkSegments) {
        if (bprParametersForLinkSegmentAndMode.containsKey(linkSegment)) {
          final Pair<Double, Double> alphaBeta = bprParametersForLinkSegmentAndMode.get(linkSegment);
          final MacroscopicLinkSegment macroscopicLinkSegment = (MacroscopicLinkSegment) linkSegment;
          bprLinkTravelTimeCost.setParameters(macroscopicLinkSegment, mode, alphaBeta.first(), alphaBeta.second());
        }
      }
    }
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param capacityPeriod time period for link capacity
   * @param defaultMaximumSpeed default maximum speed along a link
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
      final Map<NetworkFileColumns, Integer> networkFileColumns, final SpeedUnits speedUnits,
      final LengthUnits lengthUnits,
      final CapacityPeriod capacityPeriod, final double defaultMaximumSpeed)
      throws PlanItException {
    this(networkFileLocation, demandFileLocation, null, networkFileColumns, speedUnits, lengthUnits,
        capacityPeriod, defaultMaximumSpeed);
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param defaultMaximumSpeed default maximum speed along a link
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
      final Map<NetworkFileColumns, Integer> networkFileColumns, final SpeedUnits speedUnits,
      final LengthUnits lengthUnits,
      final double defaultMaximumSpeed)
      throws PlanItException {
    this(networkFileLocation, demandFileLocation, null, networkFileColumns, speedUnits, lengthUnits, null,
        defaultMaximumSpeed);
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param nodeCoordinateFileLocation node coordinate file location
   * @param standardResultsFileLocation the location of the standard results file
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param defaultMaximumSpeed default maximum speed along a link
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
      final String nodeCoordinateFileLocation, final String standardResultsFileLocation,
      final Map<NetworkFileColumns, Integer> networkFileColumns,
      final SpeedUnits speedUnits,
      final LengthUnits lengthUnits, final double defaultMaximumSpeed) throws PlanItException {
    this(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, networkFileColumns, speedUnits,
        lengthUnits, null, defaultMaximumSpeed);
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param nodeCoordinateFileLocation node coordinate file location
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param capacityPeriod time period for link capacity
   * @param defaultMaximumSpeed default maximum speed along links
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
      final String nodeCoordinateFileLocation, final Map<NetworkFileColumns, Integer> networkFileColumns,
      final SpeedUnits speedUnits, final LengthUnits lengthUnits, final CapacityPeriod capacityPeriod,
      final double defaultMaximumSpeed) throws PlanItException {

    super();

    try {
      networkFile = new File(networkFileLocation).getCanonicalFile();
      demandFile = new File(demandFileLocation).getCanonicalFile();
      nodeCoordinateFile = (nodeCoordinateFileLocation == null) ? null : new File(nodeCoordinateFileLocation)
          .getCanonicalFile();
      this.networkFileColumns = networkFileColumns;
      this.speedUnits = speedUnits;
      this.lengthUnits = lengthUnits;
      this.capacityPeriod = (capacityPeriod == null) ? CapacityPeriod.HOUR : capacityPeriod;
      this.defaultMaximumSpeed = defaultMaximumSpeed;
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error in construction of TNTP",e);
    }
  }

  /**
   * Whenever a project component is created this method will be invoked
   *
   * @param event event containing the created (and empty) project component
   * @throws RemoteException thrown if there is an error
   */
  @Override
  public void notify(final EventInterface event) throws RemoteException {
    // registered for create notifications
    if (event.getType() == TrafficAssignmentComponentFactory.TRAFFICCOMPONENT_CREATE) {
      final Object[] content = (Object[]) event.getContent();
      final Object projectComponent = content[0];
      // the content consists of the actual traffic assignment component and an array of object
      // parameters (second parameter)
      final Object[] parameters = (Object[]) content[1];
      try {
        if (projectComponent instanceof PhysicalNetwork<?,?,?>) {
          populatePhysicalNetwork((PhysicalNetwork<?,?,?>) projectComponent);
        } else if (projectComponent instanceof Zoning) {
          populateZoning((Zoning) projectComponent, parameters[0]);
        } else if (projectComponent instanceof Demands) {
          populateDemands((Demands) projectComponent, parameters[0]);
        } else if (projectComponent instanceof AbstractPhysicalCost) {
          populatePhysicalCost((AbstractPhysicalCost) projectComponent);
        } else {
          LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"event component is " + projectComponent.getClass().getCanonicalName()+ " which is not handled by PlanItInputBuilder.");
        }
      } catch (final PlanItException e) {
        LOGGER.severe(e.getMessage());
        throw new RemoteException("error rethrown as RemoteException in notify of TNTP",e);
      }
    }
  }

}
