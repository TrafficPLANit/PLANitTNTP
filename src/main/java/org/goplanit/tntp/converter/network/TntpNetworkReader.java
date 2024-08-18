package org.goplanit.tntp.converter.network;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.goplanit.converter.BaseReaderImpl;
import org.goplanit.converter.ConverterReaderUtils;
import org.goplanit.converter.network.NetworkReader;
import org.goplanit.cost.physical.BprLinkTravelTimeCost;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.LayeredNetwork;
import org.goplanit.network.layer.macroscopic.AccessGroupPropertiesFactory;
import org.goplanit.tntp.TntpHeaderConstants;
import org.goplanit.tntp.enums.LengthUnits;
import org.goplanit.tntp.enums.NetworkFileColumnType;
import org.goplanit.tntp.enums.SpeedUnits;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitCrsUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.directed.EdgeSegmentUtils;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.utils.macroscopic.MacroscopicConstants;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.misc.FileUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.AccessGroupProperties;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
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
  
  /** the network to populate */
  private MacroscopicNetwork networkToPopulate;  
      
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
  
  /** Create an estimate for the number of lanes given a certain capacity using {@link #DEFAULT_LANE_CAPACITY_PCUH} and rounding upward 
   * 
   * @param capacityPcuH to use
   * @return number of lanes estimate
   */
  private static int getNumLaneEstimate(double capacityPcuH) {
    if(capacityPcuH > DEFAULT_LANE_CAPACITY_PCUH) {
      return (int) Math.ceil(capacityPcuH/(double)DEFAULT_LANE_CAPACITY_PCUH);
    }
    return 1;
  }
  
  /** Create mode access properties based on provided information
   * 
   * @param link to extract length from
   * @param mode to use
   * @param maxSpeedKmH to use in case length is not present
   * @return created properties
   */
  private AccessGroupProperties createAccessGroupProperties(Link link, Mode mode, double maxSpeedKmH) {

    final AccessGroupProperties modeAccessProperties = AccessGroupPropertiesFactory.create(maxSpeedKmH, maxSpeedKmH, mode);
    modeAccessProperties.setMaximumSpeedKmH(maxSpeedKmH);
    return modeAccessProperties;
  }

  /** create a new link segment type 
   * 
   * @param networkLayer to register on
   * @param capacityPerLane to use
   * @param modeAccessProperties to use
   * @param externalId externalId to set
   * @return created link segment type
   */
  private MacroscopicLinkSegmentType createAndRegisterLinkSegmentType(
      final MacroscopicNetworkLayer networkLayer, double capacityPerLane, final AccessGroupProperties modeAccessProperties, String externalId) {
    MacroscopicLinkSegmentType linkSegmentType;
    linkSegmentType = networkLayer.getLinkSegmentTypes().getFactory().registerNew(externalId, capacityPerLane, MacroscopicConstants.DEFAULT_MAX_DENSITY_PCU_KM_LANE);
    linkSegmentType.setAccessGroupProperties(modeAccessProperties);
    
    /* XML id */
    linkSegmentType.setXmlId(Long.toString(linkSegmentType.getId()));
    /* external id */
    linkSegmentType.setExternalId(externalId);
    return linkSegmentType;
  }

  /**
   * initialise the source id trackers, so we can lay indices on the source id as well for quick lookups
   */
  private void initialiseSourceIdTrackers() {
    initialiseSourceIdMap(Mode.class, Mode::getXmlId); // no source id for TNTP modes, uses XML id of predefined mode instead
    initialiseSourceIdMap(Link.class, Link::getExternalId);
    initialiseSourceIdMap(MacroscopicLinkSegment.class, MacroscopicLinkSegment::getExternalId);
    initialiseSourceIdMap(MacroscopicLinkSegmentType.class, MacroscopicLinkSegmentType::getExternalId);
    initialiseSourceIdMap(Node.class, Node::getExternalId);
  }

  /**
   * Create and register a new node if it does not already exist
   *
   * @param network the current physical network
   * @param nodeSourceId node source id to use
   * @return the node corresponding to this external ID
   */
  private Node collectOrCreatePlanitNode(final MacroscopicNetworkLayer network, final String nodeSourceId) {

    Node node = null;
    if (getBySourceId(Node.class, nodeSourceId) == null) {
      node = network.getNodes().getFactory().registerNew();
      /* XML id */
      node.setXmlId(nodeSourceId);
      /* external id */
      node.setExternalId(nodeSourceId);
      registerBySourceId(Node.class, node);
    } else {
      node = getBySourceId(Node.class, nodeSourceId);
    }
    return node;
  }
  
  /**
   * Create and register a new node if it does not already exist
   *
   * @param network the current physical network
   * @param cols the columns in the network input file
   * @param networkFileColumn the column in the network file which contains the node external Id
   * @return the node corresponding to this external ID
   */
  private Node collectOrCreatePlanitNode(
      final MacroscopicNetworkLayer network, final String[] cols, final NetworkFileColumnType networkFileColumn){
        
    final String nodeSourceId = cols[getSettings().getNetworkFileColumns().get(networkFileColumn)];
    if ( Long.parseLong(nodeSourceId) > noPhysicalNodes) {
      throw new PlanItRunTimeException("Number of nodes is specified as " + noPhysicalNodes + " " +
          "but found a reference to node " + nodeSourceId);
    }
    return collectOrCreatePlanitNode(network, nodeSourceId);
  }

  /**
   * Create and register a new link segment
   *
   * February 2020: We do not understand how the flow times for link types 1 and 2 are calculated.
   * Link type 3 is the only one for which our results match the published results.
   *
   * @param networkLayer the current macroscopic networkLayer
   * @param link the current link
   * @param directionAb direction to register link segment in
   * @param tntpLinkSegmentRowId the external Id of the type of this link segment (row index in file)
   * @param cols with information to parse
   * @return the macroscopic link segment which has been created
   */
  private MacroscopicLinkSegment createAndRegisterLinkSegment(
      final MacroscopicNetworkLayer networkLayer, final MacroscopicLink link, final long tntpLinkSegmentRowId, boolean directionAb, final String[] cols) {
    
    Map<NetworkFileColumnType, Integer> supportedColumns = getSettings().getNetworkFileColumns();
    SpeedUnits speedUnits = getSettings().getSpeedUnits();
    Mode mode = networkLayer.getFirstSupportedMode();
       
    /* LINK SEGMENT*/
    final MacroscopicLinkSegment linkSegment = networkLayer.getLinkSegments().getFactory().registerNew(link, directionAb, true);
    /* XML id */
    linkSegment.setXmlId(
        EdgeSegmentUtils.createParentLinkDerivedId(linkSegment, IdMapperType.EXTERNAL_ID));
    /* external id */    
    linkSegment.setExternalId(String.valueOf(tntpLinkSegmentRowId));
    if (linkSegment.getExternalId() != null) {
      registerBySourceId(MacroscopicLinkSegment.class, linkSegment);      
    }
    
    /* LINK SEGMENT TYPE + number of lanes */
    {
      /* max speed km/h */
      double defaultMaximumSpeed = getSettings().getDefaultMaximumSpeed();
      double planitMaxSpeedKmH = defaultMaximumSpeed *  speedUnits.getMultiplier();
      final double tntpSpeedLimit = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.MAXIMUM_SPEED)]);

      /* free flow travel time */
      double freeFlowTravelTimeH = link.getLengthKm()/planitMaxSpeedKmH;
      if(supportedColumns.containsKey(NetworkFileColumnType.FREE_FLOW_TRAVEL_TIME)) {
        double tntpFftt = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.FREE_FLOW_TRAVEL_TIME)]) * settings.getFreeFlowTravelTimeUnits().getMultiplier();
        freeFlowTravelTimeH = (tntpFftt > Precision.EPSILON_6 && tntpFftt < Double.POSITIVE_INFINITY) ? tntpFftt : freeFlowTravelTimeH;
      }

      if (tntpSpeedLimit > Precision.EPSILON_6 && tntpSpeedLimit < Double.POSITIVE_INFINITY) {
        planitMaxSpeedKmH = tntpSpeedLimit * speedUnits.getMultiplier();
      }else{
        // no speed limit, derive from free flow travel time instead
        planitMaxSpeedKmH = link.getLengthKm()/freeFlowTravelTimeH;
      }
      planitMaxSpeedKmH = (int) Math.round(planitMaxSpeedKmH); // round to whole number

      final AccessGroupProperties modeAccessProperties = createAccessGroupProperties(
          link, mode, planitMaxSpeedKmH);

      /* only when capacity is not combined with number of lanes we need to scale it to capacity per lane, otherwise not */
      boolean numLanesShouldScaleCapacity = !supportedColumns.containsKey(NetworkFileColumnType.NUMBER_OF_LANES);

      /* capacity pcu/h/lane */
      double capacityPerHourMultiplier =
          getSettings().getCapacityPeriodUnits().getMultiplier()/getSettings().getCapacityPeriodDuration();
      double capacityPerLane =
          Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.CAPACITY_PER_LANE)]) * capacityPerHourMultiplier;

      /* create per lane capacity estimate */
      int numLanes = -1;
      if(numLanesShouldScaleCapacity) {
        numLanes = getNumLaneEstimate(capacityPerLane);
        capacityPerLane = capacityPerLane / numLanes;
      }else{
        numLanes = Integer.parseInt(cols[supportedColumns.get(NetworkFileColumnType.NUMBER_OF_LANES)]);
      }
      
      /* Link segment type */
      MacroscopicLinkSegmentType linkSegmentType = null;
      String linkSegmentTypeSourceId = String.format("c_%.1f:s_%d", capacityPerLane, (int) planitMaxSpeedKmH);
      String linkSegmentTypeExternalId = "";
      if(supportedColumns.containsKey(NetworkFileColumnType.LINK_TYPE)) {
        linkSegmentTypeExternalId = cols[supportedColumns.get(NetworkFileColumnType.LINK_TYPE)];
      }
      linkSegmentType = getBySourceId(MacroscopicLinkSegmentType.class, linkSegmentTypeSourceId);



      if (linkSegmentType != null) {
        /* make sure that the referenced link type in TNTP definition is compatible with the definition of a link type in PLANit
         * by verifying the converted capacity per lane equality */
        /* determine if link type is compatible with link segment type as we require capacity per lane to be the
         * same across all usages of a particular type. If not we must create a new type or use an existing compatible
         * type */
        boolean typeAndLinkCapacityIncompatible = Precision.nonZero(capacityPerLane % linkSegmentType.getExplicitCapacityPerLane());
        if(typeAndLinkCapacityIncompatible|| !linkSegmentType.getAccessProperties(mode).isEqualExceptForModes(modeAccessProperties)) {
          /* cannot be matched to existing (referenced) TNTP link segment type search other candidates */
          linkSegmentType = null;

          /* find first match with equal capacity and mode properties */
          final var finalCapPerLane = capacityPerLane;
          MacroscopicLinkSegmentType match = networkLayer.getLinkSegmentTypes().toCollection().stream().filter(
              ls -> Precision.equal(ls.getExplicitCapacityPerLane(), finalCapPerLane)).filter(
              ls -> ls.getAccessProperties(mode).isEqualExceptForModes(modeAccessProperties)).findFirst().orElse(null);
          if(match != null) {
            linkSegmentType = match;
            if (LOGGER.getLevel() == Level.FINE) {
              LOGGER.fine(String.format("TNTP Link %s (nodes %s,%s) with capacity %.2f assigned to alternative type (%s) " +
                      "[%.2f capacity per lane, %.2f speed limit (km/h)] because TNTP type properties vary across links, this is not allowed in PLANit",
                  link.getExternalId(), link.getVertexA().getExternalId(), link.getVertexB().getExternalId(), capacityPerLane,
                  match.getXmlId(), match.getExplicitCapacityPerLane(), match.getMaximumSpeedKmH(mode)));
            }
          }
        }
      }

      if (linkSegmentType == null) {
        // we do not register by external id because it is not unique but we want to retain the external id
        // so we temporarily use our custom external id and then replace it after registration, not pretty but hack fix
        linkSegmentType = createAndRegisterLinkSegmentType(
            networkLayer, capacityPerLane, modeAccessProperties, linkSegmentTypeSourceId);
        registerBySourceId(MacroscopicLinkSegmentType.class, linkSegmentType);
        linkSegmentType.setExternalId(linkSegmentTypeExternalId);
      }
      
      linkSegment.setNumberOfLanes(numLanes);
      linkSegment.setLinkSegmentType(linkSegmentType);     
    }
  
    return linkSegment;
  }

  /**
   * parse the network file using the scanner provided
   *
   * @param scanner to use
   * @param networkLayer the physical network object to be populated from the input data
   */
  private void parseTntNetworkFile(Scanner scanner, MacroscopicNetworkLayer networkLayer) {
    boolean readingMetadata = true;
    boolean readingLinkData = false;
    long tntpLinkSegmentRowId = 0;

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
          tntpLinkSegmentRowId++;
          readLinkData(networkLayer, line, tntpLinkSegmentRowId);
        }
      }
    }

    if (tntpLinkSegmentRowId != noLinks) {
      final String errorMessage = "Header says " + noLinks + " links but " + tntpLinkSegmentRowId+ " were actually defined.";
      LOGGER.severe(errorMessage);
      throw new PlanItRunTimeException(errorMessage);
    }
  }

  /**
   * Update the node coordinates from the node coordinate file
   *
   * @param networkLayer the physical network object to be populated from the input data
   * @param nodeCoordinateFile file used
   */
  private void parseNodeCoordinatesFromFile(final MacroscopicNetworkLayer networkLayer, File nodeCoordinateFile) {
    boolean swapNodeCoords = getSettings().isSwapNodeCoordinates();
    try (Scanner scanner = new Scanner(nodeCoordinateFile)) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine().trim();
        line = line.replaceAll(";", "");
        final char firstChar = line.charAt(0);
        if (Character.isDigit(firstChar)) {
          final String[] cols = line.split("\\s+");
          final String nodeSourceId = cols[0];
  
          Node node = getBySourceId(Node.class, nodeSourceId);
          if(node == null) {
            LOGGER.warning(String.format("Node %s in TNTP node file not used by TNTP links, likely dangling",nodeSourceId));
            node = collectOrCreatePlanitNode(networkLayer, nodeSourceId);
          }
          double xCoord = swapNodeCoords ? Double.parseDouble(cols[2]) : Double.parseDouble(cols[1]);
          double yCoord = swapNodeCoords ? Double.parseDouble(cols[1]) : Double.parseDouble(cols[2]);
          Point nodePosition = PlanitJtsUtils.createPoint(xCoord, yCoord);
          node.setPosition(nodePosition);
        }
      }
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error when parsing node coordinates from file in TNTP",e);
    }
  }

  /**
   * Read network metadata from the top of the network input file
   *
   * @param line the current line in the network input file
   */
  private void readNetworkMetadata(final String line) {
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
   * @param tntpLinkSegmentRowId the row Id for the current Tntp link segment (used as external id)
   */
  private void readLinkData(final MacroscopicNetworkLayer networkLayer, final String line, final long tntpLinkSegmentRowId) {
    final String[] cols = line.split("\\s+");
    
    Map<NetworkFileColumnType, Integer> supportedColumns = getSettings().getNetworkFileColumns();
    LengthUnits lengthUnits = getSettings().getLengthUnits();
   
    final Node upstreamNode = collectOrCreatePlanitNode(networkLayer, cols, NetworkFileColumnType.UPSTREAM_NODE_ID);
    final Node downstreamNode = collectOrCreatePlanitNode(networkLayer, cols, NetworkFileColumnType.DOWNSTREAM_NODE_ID);    
    final double length = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.LENGTH)]) * lengthUnits.getMultiplier();
    
    /* LINK */
    MacroscopicLink link = null;
    var oppositeDirectionSegment = (MacroscopicLinkSegment) downstreamNode.getEdgeSegment(upstreamNode);
    boolean directionAb = true;
    if(oppositeDirectionSegment != null) {
      /* link already exists */
      if(!Precision.equal(oppositeDirectionSegment.getParentLink().getLengthKm(),length)){
        LOGGER.warning(String.format("Identified bi-directional TNTP link (node %s,node %s) with unequal lengths depending on direction, split in separate PLANit links",upstreamNode.getExternalId(), downstreamNode.getExternalId()));
      }else {
        link = oppositeDirectionSegment.getParentLink();
        directionAb = false;
      }
    }
    
    if(link==null) {
      link = networkLayer.getLinks().getFactory().registerNew(upstreamNode, downstreamNode, length, true /* register on node */);
      /* XML id */
      link.setXmlId(link.getId());
      /* External id */
      link.setExternalId(link.getXmlId());
    }
    
    /* LINK SEGMENT + TYPE */
    final MacroscopicLinkSegment linkSegment = createAndRegisterLinkSegment(
        networkLayer, link, tntpLinkSegmentRowId, directionAb, cols);
  
    /* MODE PARAMETERS */
    double alpha = BprLinkTravelTimeCost.DEFAULT_ALPHA;
    double beta = BprLinkTravelTimeCost.DEFAULT_BETA;
    boolean settingAlpha = false;
    if (supportedColumns.containsKey(NetworkFileColumnType.B)) {
      alpha = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.B)]);
      settingAlpha = true;
    }
    boolean settingBeta = false;
    if (supportedColumns.containsKey(NetworkFileColumnType.POWER)) {
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
      bprParametersForLinkSegmentAndMode = new HashMap<>();
    }
    final Pair<Double, Double> alphaBeta = Pair.of(alpha, beta);
    bprParametersForLinkSegmentAndMode.put(linkSegment, alphaBeta);
  }

  /**
   * Initialise CRS based on user settings
   */
  private void prepareCoordinateReferenceSystem() {
    var sourceCrs = ConverterReaderUtils.createCoordinateReferenceSystemCartesianIfFail(
            settings.getCoordinateReferenceSystem(), null);
    networkToPopulate.setCoordinateReferenceSystem(sourceCrs);
    LOGGER.info(String.format("Source CRS set to %s : %s", sourceCrs.getName(), networkToPopulate.getCoordinateReferenceSystem().getName()));
  }
  
  /**
   * Constructor
   * 
   * @param networkSettings to use
   * @param idToken to use for to be created network
   */
  protected TntpNetworkReader(TntpNetworkReaderSettings networkSettings, final IdGroupingToken idToken){
    this(networkSettings, new MacroscopicNetwork(idToken));    
  }

  /**
   * Constructor
   * 
   * @param settings to use
   * @param network to use 
   */  
  protected TntpNetworkReader(TntpNetworkReaderSettings settings, LayeredNetwork<?, ?> network) {
    this.settings = settings;
    this.networkToPopulate = (MacroscopicNetwork) network;
    if(!networkToPopulate.hasXmlId()){
      networkToPopulate.setXmlId(networkToPopulate.getId());
    }
  }

  public static final int DEFAULT_LANE_CAPACITY_PCUH = 2000;

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
  public MacroscopicNetwork read(){
    if(!networkToPopulate.getTransportLayers().isEmpty()) {
      throw new PlanItRunTimeException("Error cannot populate non-empty network");
    }

    /* crs */
    prepareCoordinateReferenceSystem();

    getSettings().logSettings();

    LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"populating Physical Network");
    
    
    initialiseSourceIdTrackers();
    
    File networkFile = null;
    File nodeCoordinateFile = null;
    try {
      networkFile = new File(settings.getNetworkFile()).getCanonicalFile();
      nodeCoordinateFile = (settings.getNodeCoordinateFile() == null) ? null : new File(settings.getNodeCoordinateFile()).getCanonicalFile();            
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error in constructing files from network and node file location settings of TNTP",e);
    }    
    
    /* TNTP only has one mode, define it here */
    Mode mode = networkToPopulate.getModes().getFactory().registerNew(PredefinedModeType.CAR);
    registerBySourceId(Mode.class, mode);
    
    /* TNTP only compatible with parsing a single network layer, so create it */
    final MacroscopicNetworkLayer networkLayer = networkToPopulate.getTransportLayers().getFactory().registerNew();
    networkLayer.registerSupportedMode(mode);
    if(!networkLayer.hasXmlId()){
      networkLayer.setXmlId(networkLayer.getId());
    }

    // parse network file
    FileUtils.wrapFileScanner(networkFile, scanner -> parseTntNetworkFile(scanner, networkLayer));

    if (nodeCoordinateFile != null) {
      parseNodeCoordinatesFromFile(networkLayer, nodeCoordinateFile);
    }
    
    return networkToPopulate;
  }


  /**
   * {@inheritDoc}
   */  
  @Override
  public void reset() {
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
