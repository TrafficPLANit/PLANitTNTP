package org.planit.tntp.input;

import java.io.File;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.annotation.Nonnull;

import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.coordinate.Position;
import org.planit.demands.Demands;
import org.planit.event.CreatedProjectComponentEvent;
import org.planit.exceptions.PlanItException;
import org.planit.geo.PlanitGeoUtils;
import org.planit.input.InputBuilderListener;
import org.planit.logging.PlanItLogger;
import org.planit.network.physical.Link;
import org.planit.network.physical.Node;
import org.planit.network.physical.PhysicalNetwork;
import org.planit.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.network.physical.macroscopic.MacroscopicLinkSegmentType;
import org.planit.network.physical.macroscopic.MacroscopicLinkSegmentTypeModeProperties;
import org.planit.network.physical.macroscopic.MacroscopicModeProperties;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.network.virtual.Centroid;
import org.planit.od.odmatrix.demand.ODDemandMatrix;
import org.planit.time.TimePeriod;
import org.planit.tntp.enums.CapacityPeriod;
import org.planit.tntp.enums.LengthUnits;
import org.planit.tntp.enums.NetworkFileColumns;
import org.planit.tntp.enums.SpeedUnits;
import org.planit.tntp.enums.TimeUnits;
import org.planit.userclass.Mode;
import org.planit.zoning.Zone;
import org.planit.zoning.Zoning;

import com.vividsolutions.jts.geom.Coordinate;

/**
 * Class which reads input from TNTP files
 *
 * @author gman6028
 *
 */
public class Tntp extends InputBuilderListener {

  private PlanitGeoUtils planitGeoUtils;

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
   * Units of time used for free flow travel time in network input file
   */
  private TimeUnits timeUnits;

  /**
   * Time period for link capacity
   */
  private CapacityPeriod capacityPeriod;

  /**
   * TNTP only has one mode
   */
  private final Mode mode;

  /**
   * TNTP only has one time period
   */
  private final TimePeriod timePeriod;

  /**
   * The number of zones in the network
   */
  private int noZones;

  /**
   * The number of nodes in the network
   */
  private int noPhysicalNodes;

  /**
   * The number of links in the network
   */
  private int noLinks;

  public static final int ONE_WAY_AB = 1;
  public static final int ONE_WAY_BA = 2;
  public static final int TWO_WAY = 3;
  public static final double bprAlpha = 0.87;
  public static final double bprBeta = 4.0;

  /**
   * Create and register a new node if it does not already exist
   *
   * @param network the current physical network
   * @param cols the columns in the network input file
   * @param networkFileColumn the column in the network file which contains the node external Id
   * @param nodeExternalId the external Id of the node to be created
   * @return the node corresponding to this external ID
   */
  private Node createAndRegisterNode(final PhysicalNetwork network, final String[] cols, final NetworkFileColumns networkFileColumn)
      throws PlanItException {
    final long nodeExternalId = Long.parseLong(cols[networkFileColumns.get(networkFileColumn)]);
    if (nodeExternalId > noPhysicalNodes) {
      throw new PlanItException("Number of nodes is specified as " + noPhysicalNodes
          + " but found a reference to node " + nodeExternalId);
    }
    Node node = network.nodes.findNodeByExternalIdentifier(nodeExternalId);
    if (node == null) {
      node = new Node();
      node.setExternalId(nodeExternalId);
      network.nodes.registerNode(node);
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
  private void updateOdDemandMatrix(final Map<Integer, Double> demandToDestination, final Zoning zoning, final Zone originZone,
      final ODDemandMatrix odDemandMatrix) {
    for (final Integer destinationZoneId : demandToDestination.keySet()) {
      final Zone destinationZone = zoning.zones.getZoneByExternalId(destinationZoneId);
      odDemandMatrix.setValue(originZone, destinationZone, demandToDestination.get(destinationZoneId));
    }
  }

  /**
   * Create and register a new link segment
   *
   * @param network the current physical network
   * @param link the current link
   * @param maxSpeed the maximum speed for this link
   * @param capacityPerLane the capacity per lane for this link
   * @param externalId external Id of this link segment
   * @throws PlanItException thrown if there is an error
   */
  private void createAndRegisterLinkSegment(@Nonnull final PhysicalNetwork network, @Nonnull final Link link, final double maxSpeed,
      final double capacityPerLane, final long externalId)
      throws PlanItException {
    final MacroscopicLinkSegment linkSegment = (MacroscopicLinkSegment) network.linkSegments.createDirectionalLinkSegment(
        link, true);
    linkSegment.setMaximumSpeed(mode, maxSpeed);
    linkSegment.setExternalId(externalId);
    final String capacityBasedName = String.valueOf(capacityPerLane);
    final MacroscopicModeProperties modeProperties = new MacroscopicModeProperties();
    final MacroscopicLinkSegmentTypeModeProperties macroscopicLinkSegmentTypeModeProperties =
        new MacroscopicLinkSegmentTypeModeProperties(mode, modeProperties);
    final MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork) network;
    final MacroscopicLinkSegmentType macroscopicLinkSegmentType = macroscopicNetwork.registerNewLinkSegmentType(
        capacityBasedName, capacityPerLane,
        Double.POSITIVE_INFINITY, externalId, macroscopicLinkSegmentTypeModeProperties).getFirst();
    linkSegment.setLinkSegmentType(macroscopicLinkSegmentType);
    network.linkSegments.registerLinkSegment(link, linkSegment, true);
  }

  /**
   * Update the node coordinates from the node coordinate file
   *
   * @param physicalNetwork the physical network object to be populated from the input data
   * @throws PlanItException thrown if there is an error reading the input file
   */
  private void updateNodeCoordinatesFromFile(final PhysicalNetwork network) throws PlanItException {
    try (Scanner scanner = new Scanner(nodeCoordinateFile)) {
       while (scanner.hasNextLine()) {
        final String line = scanner.nextLine().trim();
         line.replaceAll(";", "");
        final char firstChar = line.charAt(0);
        if (Character.isDigit(firstChar)) {
          final String[] cols = line.split("\\s+");
          final long nodeExternalId = Long.parseLong(cols[0]);

          final Coordinate coordinate = new Coordinate(Double.parseDouble(cols[1]), Double.parseDouble(cols[2]));
          final Coordinate[] coordinates = {coordinate};
          final List<Position> positions = planitGeoUtils.convertToDirectPositions(coordinates);
          final DirectPosition nodeGeometry = (DirectPosition) positions.get(0);
          final Node node = network.nodes.findNodeByExternalIdentifier(nodeExternalId);
          node.setCentrePointGeometry(nodeGeometry);
        }
      }
    } catch (final Exception ex) {
      throw new PlanItException(ex);
    }
  }

  /**
   * Read network metadata from the top of the network input file
   *
   * @param line the current line in the network input file
   * @throws Exception thrown if the contents of the header cannot be parsed into an integer
   */
  private void readNetworkMetadata(final String line) throws Exception {
    if (line.startsWith("<NUMBER OF ZONES>")) {
      noZones = parseFromHeader(line, "<NUMBER OF ZONES>");
    } else if (line.startsWith("<NUMBER OF NODES>")) {
      noPhysicalNodes = parseFromHeader(line, "<NUMBER OF NODES>");
    } else if (line.startsWith("<NUMBER OF LINKS>")) {
      noLinks = parseFromHeader(line, "<NUMBER OF LINKS>");
    }
  }

  /**
   * Create and register the nodes, links and link segments from the current line in the network
   * input file
   *
   * @param physicalNetwork the physical network object to be populated from the input data
   * @param line the current line in the network input file
   * @param linkSegmentExternalId the external Id for the current line segment
   * @throws PlanItException thrown if there is an error
   */
  private void readLinkData(final PhysicalNetwork network, final String line, final long linkSegmentExternalId) throws PlanItException {
    final String[] cols = line.split("\\s+");

    final Node upstreamNode = createAndRegisterNode(network, cols, NetworkFileColumns.UPSTREAM_NODE_ID);
    final Node downstreamNode = createAndRegisterNode(network, cols, NetworkFileColumns.DOWNSTREAM_NODE_ID);

    final double length = Double.parseDouble(cols[networkFileColumns.get(NetworkFileColumns.LENGTH)])
        * lengthUnits.getMultiplier();
    final Link link = network.links.registerNewLink(upstreamNode, downstreamNode, length);
    double maxSpeed = 0.0;
    final double speed = Double.parseDouble(cols[networkFileColumns.get(NetworkFileColumns.MAXIMUM_SPEED)]);
    if (speed == 0.0) {
      final double freeFlowTravelTime = Double.parseDouble(cols[networkFileColumns.get(
          NetworkFileColumns.FREE_FLOW_TRAVEL_TIME)]) * timeUnits.getMultiplier();
      if (freeFlowTravelTime > 0.0) {
        maxSpeed = length / freeFlowTravelTime;
      }
    } else {
      maxSpeed = speed * speedUnits.getMultiplier();
    }
    if (maxSpeed == 0.0) {
      maxSpeed = MacroscopicModeProperties.DEFAULT_MAXIMUM_SPEED;
    }
    final double capacityPerLane = Integer.parseInt(cols[networkFileColumns.get(NetworkFileColumns.CAPACITY_PER_LANE)]) + capacityPeriod.getMultiplier();
    createAndRegisterLinkSegment(network, link, maxSpeed, capacityPerLane, linkSegmentExternalId);
  }

  /**
   * Creates the physical network object from the data in the input file
   *
   * @param physicalNetwork the physical network object to be populated from the input data
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populatePhysicalNetwork(@Nonnull final PhysicalNetwork network) throws PlanItException {
    PlanItLogger.info("Populating Physical Network");
    try (Scanner scanner = new Scanner(networkFile)) {
      boolean readingMetadata = true;
      boolean readingLinkData = false;
      long linkSegmentExternalId = 0;
      while (scanner.hasNextLine()) {
        final String line = scanner.nextLine().trim();
        final char firstChar = (line.isEmpty()) ? 'x' : line.charAt(0);
        final boolean atEndOfMetadata = line.equals("<END OF METADATA>");
        if (atEndOfMetadata) {
          readingMetadata = false;
        }
        if (readingMetadata) {
          readNetworkMetadata(line);
        } else if (!atEndOfMetadata) {
          if (firstChar == '~') {
            readingLinkData = true;
          } else if (readingLinkData) {
            linkSegmentExternalId++;
            readLinkData(network, line, linkSegmentExternalId);
          }
        }
      }

      if (linkSegmentExternalId != noLinks) {
        throw new PlanItException("Header says " + noLinks + " links but " + linkSegmentExternalId
            + " were actually defined.");
      }
    } catch (final PlanItException pex) {
      throw pex;
    } catch (final Exception ex) {
      throw new PlanItException(ex);
    }

    if (nodeCoordinateFile != null) {
      updateNodeCoordinatesFromFile(network);
    }
  }

  /**
   * Populates the Demands object from the input file
   *
   * @param demands the Demands object to be populated from the input data
   * @param parameter1 Zoning object previously defined
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateDemands(@Nonnull final Demands demands, final Object parameter1) throws PlanItException {
    PlanItLogger.info("Populating Demands");
    final Zoning zoning = (Zoning) parameter1;
    try (Scanner scanner = new Scanner(demandFile)) {
      boolean readingMetadata = true;
      Zone originZone = null;
      Map<Integer, Double> demandToDestination = null;
      final ODDemandMatrix odDemandMatrix = new ODDemandMatrix(zoning.zones);
      while (scanner.hasNextLine()) {
        final String line = scanner.nextLine().trim();
        final char firstChar = (line.isEmpty()) ? 'x' : line.charAt(0);
        final boolean atEndOfMetadata = line.equals("<END OF METADATA>");
        if (atEndOfMetadata) {
          readingMetadata = false;
        }
        if (readingMetadata) {
          if (line.startsWith("<NUMBER OF ZONES>")) {
            final String subLine = line.substring("<NUMBER OF ZONES>".length()).trim();
            if (noZones != Integer.parseInt(subLine)) {
              throw new PlanItException("Network file indicates there are " + noZones
                  + " but demand file indicates there are " + Integer.parseInt(subLine) + " zones.");
            }
          }
        } else if (!atEndOfMetadata) {
          if ((!line.isEmpty()) && (firstChar != '~')) {
            if (line.startsWith("Origin")) {
              if (demandToDestination != null) {
                updateOdDemandMatrix(demandToDestination, zoning, originZone, odDemandMatrix);
              }
              final String[] cols = line.split("\\s+");
              final long originId = Long.parseLong(cols[1]);
              originZone = zoning.zones.getZoneByExternalId(originId);
              demandToDestination = new HashMap<Integer, Double>();
            } else {
              final String lineWithNoSpaces = line.replaceAll("\\s", "");
              final String[] destDemand = lineWithNoSpaces.split("[:;]");
              for (int i = 0; i < destDemand.length; i += 2) {
                demandToDestination.put(Integer.parseInt(destDemand[i]), Double.parseDouble(destDemand[i + 1]));
              }
            }
          }
        }
      }
      scanner.close();
      updateOdDemandMatrix(demandToDestination, zoning, originZone, odDemandMatrix);

      demands.registerODDemand(timePeriod, mode, odDemandMatrix);
    } catch (final Exception ex) {
      ex.printStackTrace();
      throw new PlanItException(ex);
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
    PlanItLogger.info("Populating zoning");
    final PhysicalNetwork physicalNetwork = (PhysicalNetwork) parameter1;
    for (long zoneExternalId = 1; zoneExternalId <= noZones; zoneExternalId++) {
      final Zone zone = zoning.zones.createAndRegisterNewZone(zoneExternalId);
      final Centroid centroid = zone.getCentroid();
      final Node node = physicalNetwork.nodes.findNodeByExternalIdentifier(zoneExternalId);
      // TODO - calculate connectoid length
      final double connectoidLength = 1.0;
      zoning.getVirtualNetwork().connectoids.registerNewConnectoid(centroid, node, connectoidLength, BigInteger.valueOf(
          zoneExternalId));
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
   * @throws PlanItException
   */
  public Tntp(final String networkFileLocation, final String demandFileLocation,
      final Map<NetworkFileColumns, Integer> networkFileColumns, final SpeedUnits speedUnits, final LengthUnits lengthUnits,
      final TimeUnits timeUnits, final CapacityPeriod capacityPeriod)
      throws PlanItException {
    this(networkFileLocation, demandFileLocation, null, networkFileColumns, speedUnits, lengthUnits, timeUnits, capacityPeriod);
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @throws PlanItException
   */
  public Tntp(final String networkFileLocation, final String demandFileLocation,
      final Map<NetworkFileColumns, Integer> networkFileColumns, final SpeedUnits speedUnits, final LengthUnits lengthUnits,
      final TimeUnits timeUnits)
      throws PlanItException {
    this(networkFileLocation, demandFileLocation, null, networkFileColumns, speedUnits, lengthUnits, timeUnits, null);
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
   * @param timeUnits time units for the free flow travel time
   * @throws PlanItException
   */
  public Tntp(final String networkFileLocation, final String demandFileLocation, final String nodeCoordinateFileLocation,
      final Map<NetworkFileColumns, Integer> networkFileColumns, final SpeedUnits speedUnits, final LengthUnits lengthUnits,
      final TimeUnits timeUnits)
      throws PlanItException {
    this(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, networkFileColumns, speedUnits, lengthUnits, timeUnits, null);
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
   * @param timeUnits time units for the free flow travel time
   * @param capacityPeriod time period for link capacity
   * @throws PlanItException
   */
  public Tntp(final String networkFileLocation, final String demandFileLocation, final String nodeCoordinateFileLocation,
      final Map<NetworkFileColumns, Integer> networkFileColumns, final SpeedUnits speedUnits, final LengthUnits lengthUnits,
      final TimeUnits timeUnits, final CapacityPeriod capacityPeriod)
      throws PlanItException {

    // TNTP only has one mode, define it here
    mode = new Mode(1, "Base Mode", 1.0);

    // TNTP only has one time period, define it here
    timePeriod = new TimePeriod(1, "All Day", "0000", 24.0);
    try {
      networkFile = new File(networkFileLocation).getCanonicalFile();
      demandFile = new File(demandFileLocation).getCanonicalFile();
      nodeCoordinateFile = (nodeCoordinateFileLocation == null) ? null : new File(nodeCoordinateFileLocation)
          .getCanonicalFile();
      this.networkFileColumns = networkFileColumns;
      this.speedUnits = speedUnits;
      this.lengthUnits = lengthUnits;
      this.timeUnits = timeUnits;
      this.capacityPeriod = (capacityPeriod == null) ? CapacityPeriod.HOUR : capacityPeriod;
      planitGeoUtils = new PlanitGeoUtils();
    } catch (final Exception ex) {
      throw new PlanItException(ex);
    }
  }

  /**
   * Whenever a project component is created this method will be invoked
   *
   * @param event event containing the created (and empty) project component
   * @throws PlanItException thrown if there is an error
   */
  @Override
  public void onCreateProjectComponent(final CreatedProjectComponentEvent<?> event) throws PlanItException {
    final Object projectComponent = event.getProjectComponent();
    if (projectComponent instanceof PhysicalNetwork) {
      populatePhysicalNetwork((PhysicalNetwork) projectComponent);
    } else if (projectComponent instanceof Zoning) {
      populateZoning((Zoning) projectComponent, event.getParameter1());
    } else if (projectComponent instanceof Demands) {
      populateDemands((Demands) projectComponent, event.getParameter1());
    } else {
      PlanItLogger.info("Event component is " + projectComponent.getClass().getCanonicalName()
          + " which is not handled by PlanIt.");
    }
  }

}
