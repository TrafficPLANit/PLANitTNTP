package org.goplanit.tntp.converter.network;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import org.goplanit.converter.BaseReaderImpl;
import org.goplanit.converter.network.NetworkReader;
import org.goplanit.cost.physical.BPRLinkTravelTimeCost;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.LayeredNetwork;
import org.goplanit.network.layer.macroscopic.AccessGroupPropertiesFactory;
import org.goplanit.tntp.TntpHeaderConstants;
import org.goplanit.tntp.enums.LengthUnits;
import org.goplanit.tntp.enums.NetworkFileColumnType;
import org.goplanit.tntp.enums.SpeedUnits;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.macroscopic.MacroscopicConstants;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.AccessGroupProperties;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.utils.network.layer.physical.Node;
import org.locationtech.jts.geom.Point;

/**
 * Network reader component for TNTP data format
 * 
 * @author gman, markr
 *
 */
public class TntpNetworkReader extends BaseReaderImpl<LayeredNetwork<?,?>> implements NetworkReader {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(TntpNetworkReader.class.getCanonicalName());
  
  /**
   * network data file
   */
  private final File networkFile;
  
  /**
   * node coordinate data file
   */
  private final File nodeCoordinateFile;  
  
  /** settings to use */
  private final TntpNetworkReaderSettings settings;
    
  /**
   * The number of nodes in the network according to TNTP
   */
  private int noPhysicalNodes;

  /**
   * The number of links in the network according to TNTP
   */
  private int noLinks;
  
  /**
   * Map containing the BPR parameters for each link segment, if these are specified in the
   * network file (null if default values are being used)
   */
  private Map<LinkSegment, Pair<Double, Double>> bprParametersForLinkSegmentAndMode;  

  
  public static final int ONE_WAY_AB = 1;
  public static final int ONE_WAY_BA = 2;
  public static final int TWO_WAY = 3;  
  
  /**
   * initialise the source id trackers, so we can lay indices on the source id as well for quick lookups
   */
  private void initialiseSourceIdTrackers() {
    initialiseSourceIdMap(Mode.class, Mode::getExternalId);
    initialiseSourceIdMap(Link.class, Link::getExternalId);
    initialiseSourceIdMap(MacroscopicLinkSegment.class, MacroscopicLinkSegment::getExternalId);
    initialiseSourceIdMap(MacroscopicLinkSegmentType.class, MacroscopicLinkSegmentType::getExternalId);
    initialiseSourceIdMap(Node.class, Node::getExternalId);
  }  
  
  /**
   * Create and register a new node if it does not already exist
   *
   * @param network the current physical network
   * @param cols the columns in the network input file
   * @param networkFileColumn the column in the network file which contains the node external Id
   * @return the node corresponding to this external ID
   * @throws PlanItException thrown if there is an error registering the node
   */
  private Node createAndRegisterNode(final MacroscopicNetworkLayer network, final String[] cols, final NetworkFileColumnType networkFileColumn)
      throws PlanItException {
        
    final String nodeSourceId = cols[getSettings().getNetworkFileColumns().get(networkFileColumn)];
    if ( Long.parseLong(nodeSourceId) > noPhysicalNodes) {
      throw new PlanItException("Number of nodes is specified as " + noPhysicalNodes + " but found a reference to node " + nodeSourceId);
    }
    Node node = null;
    if (getBySourceId(Node.class, nodeSourceId) == null) {      
      node = network.getNodes().getFactory().registerNew();
      /* XML id */
      node.setXmlId(Long.toString(node.getId()));    
      /* external id */
      node.setExternalId(nodeSourceId);
      registerBySourceId(Node.class, node);      
    } else {
      node = getBySourceId(Node.class, nodeSourceId);
    }
    return node;
  }

  /**
   * Create and register a new link segment
   *
   * February 2020: We do not understand how the flow times for link types 1 and 2 are calculated.
   * Link type 3 is the only one for which our results match the published results.
   *
   * @param networkLayer the current macroscopic networkLayer
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
      final MacroscopicNetworkLayer networkLayer, final Link link, final long tntpLinkSegmentSourceId, final String[] cols) throws PlanItException {
    
    Map<NetworkFileColumnType, Integer> supportedColumns = getSettings().getNetworkFileColumns();
    SpeedUnits speedUnits = getSettings().getSpeedUnits();
    double defaultMaximumSpeed = getSettings().getDefaultMaximumSpeed();
    
    /* max speed */
    double maxSpeed = defaultMaximumSpeed *  speedUnits.getMultiplier();
    final double speed = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.MAXIMUM_SPEED)]);
    if (speed > Precision.EPSILON_6 && speed < Double.POSITIVE_INFINITY) {
      maxSpeed = speed * speedUnits.getMultiplier();
    }
    
    /* free flow travel time */
    final double freeFlowTravelTime = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.FREE_FLOW_TRAVEL_TIME)]);    
    
    /* capacity */
    final double capacityPerLane = Integer.parseInt(cols[supportedColumns.get(NetworkFileColumnType.CAPACITY_PER_LANE)]) * getSettings().getCapacityPeriod().getMultiplier();
    
    /* link segment type */
    final int linkSegmentTypeSourceId = Integer.parseInt(cols[supportedColumns.get(NetworkFileColumnType.LINK_TYPE)]);    
    
    /* mode properties */
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
    final AccessGroupProperties modeAccessProperties = AccessGroupPropertiesFactory.create(freeflowSpeed, freeflowSpeed, networkLayer.getFirstSupportedMode());
  
    final MacroscopicLinkSegment linkSegment = networkLayer.getLinkSegments().getFactory().registerNew(link, true, true);
    /* XML id */
    linkSegment.setXmlId(Long.toString(linkSegment.getId()));
    /* external id */    
    linkSegment.setExternalId(String.valueOf(tntpLinkSegmentSourceId));
    if (linkSegment.getExternalId() != null) {
      registerBySourceId(MacroscopicLinkSegment.class, linkSegment);      
    }    
  
    /** Link segment type **/
    String linkSegmentTypeSourceIdString = String.valueOf(linkSegmentTypeSourceId);
    MacroscopicLinkSegmentType linkSegmentType = getBySourceId(MacroscopicLinkSegmentType.class, linkSegmentTypeSourceIdString);
    if (linkSegmentType == null) {
      linkSegmentType = networkLayer.getLinkSegmentTypes().getFactory().registerNew(linkSegmentTypeSourceIdString, capacityPerLane, MacroscopicConstants.DEFAULT_MAX_DENSITY_PCU_KM_LANE);
      linkSegmentType.setAccessGroupProperties(modeAccessProperties);
      
      /* XML id */
      linkSegmentType.setXmlId(Long.toString(linkSegmentType.getId()));
      /* external id */
      linkSegmentType.setExternalId(linkSegmentTypeSourceIdString);
      registerBySourceId(MacroscopicLinkSegmentType.class, linkSegmentType);
    }
    linkSegment.setLinkSegmentType(linkSegmentType);    
    linkSegment.getLinkSegmentType().getAccessProperties(networkLayer.getFirstSupportedMode()).setMaximumSpeedKmH(maxSpeed);
    
  
    return linkSegment;
  }

  /**
   * Update the node coordinates from the node coordinate file
   *
   * @param network the physical network object to be populated from the input data
   * @throws PlanItException thrown if there is an error reading the input file
   */
  private void updateNodeCoordinatesFromFile(final MacroscopicNetworkLayer network) throws PlanItException {
    try (Scanner scanner = new Scanner(nodeCoordinateFile)) {
      while (scanner.hasNextLine()) {
        final String line = scanner.nextLine().trim();
        line.replaceAll(";", "");
        final char firstChar = line.charAt(0);
        if (Character.isDigit(firstChar)) {
          final String[] cols = line.split("\\s+");
          final String nodeSourceId = cols[0];
  
          final Node node = getBySourceId(Node.class, nodeSourceId);
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
   if (line.startsWith(TntpHeaderConstants.NUMBER_OF_NODES_INDICATOR)) {
      noPhysicalNodes = TntpHeaderConstants.parseFromHeader(line, TntpHeaderConstants.NUMBER_OF_NODES_INDICATOR);
    } else if (line.startsWith(TntpHeaderConstants.NUMBER_OF_LINKS_INDICATOR)) {
      noLinks = TntpHeaderConstants.parseFromHeader(line, TntpHeaderConstants.NUMBER_OF_LINKS_INDICATOR);
    }
  }

  /**
   * Create and register the nodes, links and link segments from the current line in the network
   * input file
   *
   * @param networkLayer the macroscopic networkLayer object to be populated from the input data
   * @param line the current line in the network input file
   * @param tntpLinkSegmentSourceId the external Id for the current line segment
   * @throws PlanItException thrown if there is an error
   */
  private void readLinkData(final MacroscopicNetworkLayer networkLayer, final String line, final long tntpLinkSegmentSourceId)
      throws PlanItException {
    final String[] cols = line.split("\\s+");
    
    Map<NetworkFileColumnType, Integer> supportedColumns = getSettings().getNetworkFileColumns();
    LengthUnits lengthUnits = getSettings().getLengthUnits();
   
    final Node upstreamNode = createAndRegisterNode(networkLayer, cols, NetworkFileColumnType.UPSTREAM_NODE_ID);
    final Node downstreamNode = createAndRegisterNode(networkLayer, cols, NetworkFileColumnType.DOWNSTREAM_NODE_ID);
  
    /** LINK **/
    final double length = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.LENGTH)]) * lengthUnits.getMultiplier();
    final Link link = networkLayer.getLinks().getFactory().registerNew(upstreamNode, downstreamNode, length);
    /* XML id */
    link.setXmlId(Long.toString(link.getId()));
    /* link external id */
    link.setExternalId(String.valueOf(tntpLinkSegmentSourceId));
    
    /** LINK SEGMENT/TYPE **/    
    final MacroscopicLinkSegment linkSegment = createAndRegisterLinkSegment(networkLayer, link, tntpLinkSegmentSourceId, cols);
  
    /** MODE PARAMETERS **/
    double alpha = BPRLinkTravelTimeCost.DEFAULT_ALPHA;
    double beta = BPRLinkTravelTimeCost.DEFAULT_BETA;
    boolean settingAlpha = false;
    if (supportedColumns.keySet().contains(NetworkFileColumnType.B)) {
      alpha = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.B)]);
      settingAlpha = true;
    }
    boolean settingBeta = false;
    if (supportedColumns.keySet().contains(NetworkFileColumnType.POWER)) {
      beta = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.POWER)]);
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
    final Pair<Double, Double> alphaBeta = Pair.of(alpha, beta);
    bprParametersForLinkSegmentAndMode.put(linkSegment, alphaBeta);
  }  

  /**
   * Constructor
   * 
   * @param networkFileLocation to use
   * @param nodeCoordinateFileLocation to use 
   * @throws PlanItException thrown if error
   */
  public TntpNetworkReader(String networkFileLocation, String nodeCoordinateFileLocation) throws PlanItException {    
    this(networkFileLocation, nodeCoordinateFileLocation, null);
  }
  
  /**
   * Constructor
   * 
   * @param networkFileLocation to use
   * @param nodeCoordinateFileLocation to use 
   * @param networkSettings to use, when null new instance is created
   * @throws PlanItException thrown if error
   */
  public TntpNetworkReader(String networkFileLocation, String nodeCoordinateFileLocation, TntpNetworkReaderSettings networkSettings) throws PlanItException {
    if(networkSettings==null) {
      this.settings = new TntpNetworkReaderSettings();
    }else {
      this.settings = networkSettings;
    }
    
    try {
      networkFile = new File(networkFileLocation).getCanonicalFile();
      nodeCoordinateFile = (nodeCoordinateFileLocation == null) ? null : new File(nodeCoordinateFileLocation).getCanonicalFile();            
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error in construction of TNTP",e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TntpNetworkReaderSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public LayeredNetwork<?, ?> read() throws PlanItException {
    LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"populating Physical Network");

    final MacroscopicNetwork network = getSettings().getNetworkToPopulate();
    initialiseSourceIdTrackers();
    
    /* TNTP only has one mode, define it here */
    Mode mode = network.getModes().getFactory().registerNew(PredefinedModeType.CAR);
    /* external id */
    mode.setExternalId("1"); //TODO wrong because no external id is available, but tests use it --> refactor
    registerBySourceId(Mode.class, mode);    
    
    /* TNTP only compatible with parsing a single network layer, so create it */
    final MacroscopicNetworkLayer networkLayer = network.getTransportLayers().getFactory().registerNew();
    networkLayer.registerSupportedMode(mode);
   
    try (Scanner scanner = new Scanner(networkFile)) {
      boolean readingMetadata = true;
      boolean readingLinkData = false;
      long tntpLinkSegmentSourceId = 0;
      while (scanner.hasNextLine()) {
        final String line = scanner.nextLine().trim();
        final char firstChar = (line.isEmpty()) ? 'x' : line.charAt(0);
        final boolean atEndOfMetadata = line.equals(TntpHeaderConstants.END_OF_METADATA_INDICATOR);
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
            readLinkData(networkLayer, line, tntpLinkSegmentSourceId);
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
      updateNodeCoordinatesFromFile(networkLayer);
    }
    
    return network;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void reset() {
    settings.reset();
    bprParametersForLinkSegmentAndMode = null;
  }
  
  /** Provide read access to parsed bpr parameters of links (only available after parsing)
   * 
   * @return parsed bpr parameters
   */
  public Map<LinkSegment, Pair<Double, Double>> getParsedBprParameters() {
    return Collections.unmodifiableMap(this.bprParametersForLinkSegmentAndMode);
  }

}
