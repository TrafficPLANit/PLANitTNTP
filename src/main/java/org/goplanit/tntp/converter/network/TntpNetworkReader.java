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
import org.goplanit.utils.geo.PlanitCrsUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdGroupingToken;
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
   * @param freeFlowTravelTime known free flow travel time of link
   * @return
   */
  private AccessGroupProperties createAccessGroupProperties(Link link, Mode mode, double maxSpeedKmH, double freeFlowTravelTime) {
    
    /* mode properties */
    double freeflowSpeedKmH = maxSpeedKmH;
    if(Precision.positive(link.getLengthKm()) && Precision.positive(freeFlowTravelTime)){
      freeflowSpeedKmH = (link.getLengthKm() / freeFlowTravelTime);
    }       
    
    //TODO: Make this configurable -> now we round to the nearest whole number regarding the free flow speed in order to minimise 
    //      the number of link segment types needed (due to inaccuracy in tntp files, we often get very close free flow speeds but still slightly different
    //      causing a large number of types to be created.
    freeflowSpeedKmH = Math.round(freeflowSpeedKmH);
    
    final AccessGroupProperties modeAccessProperties = AccessGroupPropertiesFactory.create(freeflowSpeedKmH, freeflowSpeedKmH, mode);
    modeAccessProperties.setMaximumSpeedKmH(freeflowSpeedKmH);
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
  private MacroscopicLinkSegmentType createAndRegisterLinkSegmentType(final MacroscopicNetworkLayer networkLayer, double capacityPerLane,
      final AccessGroupProperties modeAccessProperties, String externalId) {
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
  private Node collectOrCreatePlanitNode(final MacroscopicNetworkLayer network, final String[] cols, final NetworkFileColumnType networkFileColumn)
      throws PlanItException {
        
    final String nodeSourceId = cols[getSettings().getNetworkFileColumns().get(networkFileColumn)];
    if ( Long.parseLong(nodeSourceId) > noPhysicalNodes) {
      throw new PlanItException("Number of nodes is specified as " + noPhysicalNodes + " but found a reference to node " + nodeSourceId);
    }
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
   * Create and register a new link segment
   *
   * February 2020: We do not understand how the flow times for link types 1 and 2 are calculated.
   * Link type 3 is the only one for which our results match the published results.
   *
   * @param networkLayer the current macroscopic networkLayer
   * @param link the current link
   * @param directionAb direction to register link segment in
   * @param maxSpeed the maximum speed for this link
   * @param capacityPerLane the capacity per lane for this link
   * @param tntpLinkSegmentRowId the external Id of the type of this link segment (row index in file)
   * @param length the length of this link segment
   * @param freeFlowTravelTime the free flow travel time for this link segment
   * @return the macroscopic link segment which has been created
   * @throws PlanItException thrown if there is an error
   */
  private MacroscopicLinkSegment createAndRegisterLinkSegment(
      final MacroscopicNetworkLayer networkLayer, final Link link, final long tntpLinkSegmentRowId, boolean directionAb, final String[] cols) throws PlanItException {
    
    Map<NetworkFileColumnType, Integer> supportedColumns = getSettings().getNetworkFileColumns();
    SpeedUnits speedUnits = getSettings().getSpeedUnits();
    Mode mode = networkLayer.getFirstSupportedMode();
       
    /* LINK SEGMENT*/
    final int linkSegmentTypeSourceId = Integer.parseInt(cols[supportedColumns.get(NetworkFileColumnType.LINK_TYPE)]);         
    final MacroscopicLinkSegment linkSegment = networkLayer.getLinkSegments().getFactory().registerNew(link, directionAb, true);
    /* XML id */
    linkSegment.setXmlId(link.getExternalId() + "-" + (directionAb ? "AB" : "BA"));
    /* external id */    
    linkSegment.setExternalId(String.valueOf(tntpLinkSegmentRowId));
    if (linkSegment.getExternalId() != null) {
      registerBySourceId(MacroscopicLinkSegment.class, linkSegment);      
    }
    
    /* LINK SEGMENT TYPE + number of lanes */
    {    
      /* max speed km/h */
      double defaultMaximumSpeed = getSettings().getDefaultMaximumSpeed();
      double maxSpeedKmH = defaultMaximumSpeed *  speedUnits.getMultiplier();
      final double speedLimit = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.MAXIMUM_SPEED)]);
      if (speedLimit > Precision.EPSILON_6 && speedLimit < Double.POSITIVE_INFINITY) {
        maxSpeedKmH = speedLimit * speedUnits.getMultiplier();
      }
      
      /* free flow travel time */
      final double freeFlowTravelTimeH = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.FREE_FLOW_TRAVEL_TIME)]) * settings.getFreeFlowTravelTimeUnits().getMultiplier();    
      
      /* capacity pcu/h/lane */      
      double capacityPerHourMultiplier = getSettings().getCapacityPeriodUnits().getMultiplier()/getSettings().getCapacityPeriodDuration();      
      double capacityPerLane = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.CAPACITY_PER_LANE)]) * capacityPerHourMultiplier;       
          
      int numLanes = -1;
      final AccessGroupProperties modeAccessProperties = createAccessGroupProperties(link, mode, maxSpeedKmH, freeFlowTravelTimeH);    
      
      /** Link segment type **/
      String linkSegmentTypeSourceIdString = String.valueOf(linkSegmentTypeSourceId);
      MacroscopicLinkSegmentType linkSegmentType = getBySourceId(MacroscopicLinkSegmentType.class, linkSegmentTypeSourceIdString);
      if (linkSegmentType == null) {
        
        /* create per lane capacity estimate */
        numLanes = getNumLaneEstimate(capacityPerLane);
        capacityPerLane = capacityPerLane/numLanes;
              
        linkSegmentType = createAndRegisterLinkSegmentType(networkLayer, capacityPerLane, modeAccessProperties, linkSegmentTypeSourceIdString);
        registerBySourceId(MacroscopicLinkSegmentType.class, linkSegmentType);     
              
      }else {
        /* determine if link type is compatible with link segment type as we require capacity per lane to be the same across all usages of a particular type. IF not we must create a new type or use an existing comaptible type */
        double numLaneEstimateMod = capacityPerLane % linkSegmentType.getExplicitCapacityPerLane();  
        if(Precision.nonZero(numLaneEstimateMod) || !linkSegmentType.getAccessProperties(mode).isEqualExceptForModes(modeAccessProperties)) {
          /* cannot be matched to existing (referenced) TNTP link segment type */
          numLanes = getNumLaneEstimate(capacityPerLane);  
          double expectedCapacityPerLane = capacityPerLane / numLanes;
  
          /* find first match with equal capacity and mode properties */
          MacroscopicLinkSegmentType match = networkLayer.getLinkSegmentTypes().toCollection().stream().filter(
              ls -> Precision.equal(ls.getExplicitCapacityPerLane(), expectedCapacityPerLane)).filter(
                  ls -> ls.getAccessProperties(mode).isEqualExceptForModes(modeAccessProperties)).findFirst().orElse(null);       
          if(match != null) {
            linkSegmentType = match;
            LOGGER.warning(String.format("TNTP Link %s (nodes %s,%s) with capacity %.2f assigned to alternative type (%s) [%.2f capacity per lane, %.2f speed limit (km/h)] because TNTP type properties vary across links, this is not allowed in PLANit",
                link.getExternalId(), link.getVertexA().getExternalId(), link.getVertexB().getExternalId(), capacityPerLane, match.getXmlId(), match.getExplicitCapacityPerLane(), match.getMaximumSpeedKmH(mode)));
          }else {
            /* no match exists, create new type */
            linkSegmentType = createAndRegisterLinkSegmentType(networkLayer, expectedCapacityPerLane, modeAccessProperties, linkSegmentTypeSourceIdString);
            LOGGER.warning(String.format("TNTP Link %s (nodes %s,%s) with capacity %.2f remains unmatched, created new type %s [%.2f capacity per lane, %.2f speed limit (km/h)]",
                link.getExternalId(), link.getVertexA().getExternalId(), link.getVertexB().getExternalId(), capacityPerLane, linkSegmentType.getXmlId(), linkSegmentType.getExplicitCapacityPerLane(), linkSegmentType.getMaximumSpeedKmH(mode)));          
          }        
        }else {
          /* capacity per lane can be matched, determine number of lanes */
          numLanes = (int) Math.round(capacityPerLane/linkSegmentType.getExplicitCapacityPerLane());
        }
      }    
      
      linkSegment.setNumberOfLanes(numLanes);
      linkSegment.setLinkSegmentType(linkSegmentType);     
    }
  
    return linkSegment;
  }

  /**
   * Update the node coordinates from the node coordinate file
   *
   * @param network the physical network object to be populated from the input data
   * @param nodeCoordinateFile file used
   * @throws PlanItException thrown if there is an error reading the input file
   */
  private void parseNodeCoordinatesFromFile(final MacroscopicNetworkLayer network, File nodeCoordinateFile) throws PlanItException {
    try (Scanner scanner = new Scanner(nodeCoordinateFile)) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine().trim();
        line = line.replaceAll(";", "");
        final char firstChar = line.charAt(0);
        if (Character.isDigit(firstChar)) {
          final String[] cols = line.split("\\s+");
          final String nodeSourceId = cols[0];
  
          final Node node = getBySourceId(Node.class, nodeSourceId);
          if(node == null) {
            LOGGER.severe(String.format("Referenced node %s in TNTP node file not available in PLANit memory model",nodeSourceId));
            continue;
          }
          Point nodePosition = PlanitJtsUtils.createPoint(Double.parseDouble(cols[1]), Double.parseDouble(cols[2]));          
          node.setPosition(nodePosition);
        }
      }
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when parsing node coordinates from file in TNTP",e);
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
   * @param tntpLinkSegmentRowId the row Id for the current Tntp link segment (used as external id)
   * @throws PlanItException thrown if there is an error
   */
  private void readLinkData(final MacroscopicNetworkLayer networkLayer, final String line, final long tntpLinkSegmentRowId)
      throws PlanItException {
    final String[] cols = line.split("\\s+");
    
    Map<NetworkFileColumnType, Integer> supportedColumns = getSettings().getNetworkFileColumns();
    LengthUnits lengthUnits = getSettings().getLengthUnits();
   
    final Node upstreamNode = collectOrCreatePlanitNode(networkLayer, cols, NetworkFileColumnType.UPSTREAM_NODE_ID);
    final Node downstreamNode = collectOrCreatePlanitNode(networkLayer, cols, NetworkFileColumnType.DOWNSTREAM_NODE_ID);    
    final double length = Double.parseDouble(cols[supportedColumns.get(NetworkFileColumnType.LENGTH)]) * lengthUnits.getMultiplier();
    
    /** LINK **/
    Link link = null;
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
    
    /** LINK SEGMENT + TYPE **/    
    final MacroscopicLinkSegment linkSegment = createAndRegisterLinkSegment(networkLayer, link, tntpLinkSegmentRowId, directionAb, cols);
  
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
  }

  public static final int ONE_WAY_AB = 1;

  public static final int ONE_WAY_BA = 2;

  public static final int TWO_WAY = 3;

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
  public LayeredNetwork<?, ?> read() throws PlanItException {
    LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"populating Physical Network");
    
    if(!networkToPopulate.getTransportLayers().isEmpty()) {
      throw new PlanItException("Error cannot populate non-empty network");
    }
    
    if(getSettings().getCoordinateReferenceSystem()!=null) {
      var sourceCrs = PlanitCrsUtils.createCoordinateReferenceSystem(settings.getCoordinateReferenceSystem());
      networkToPopulate.setCoordinateReferenceSystem(sourceCrs);
    }else {
      LOGGER.info(String.format("Source CRS not set, assuming cartesiam coordinates"));
      networkToPopulate.setCoordinateReferenceSystem(PlanitJtsCrsUtils.CARTESIANCRS);
    }
    LOGGER.info(String.format("Source CRS set to %s : %s", settings.getCoordinateReferenceSystem(), networkToPopulate.getCoordinateReferenceSystem().getName()));    
    
    
    initialiseSourceIdTrackers();
    
    File networkFile = null;
    File nodeCoordinateFile = null;
    try {
      networkFile = new File(settings.getNetworkFile()).getCanonicalFile();
      nodeCoordinateFile = (settings.getNodeCoordinateFile() == null) ? null : new File(settings.getNodeCoordinateFile()).getCanonicalFile();            
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error in constructing files from network and node file location settings of TNTP",e);
    }    
    
    /* TNTP only has one mode, define it here */
    Mode mode = networkToPopulate.getModes().getFactory().registerNew(PredefinedModeType.CAR);
    mode.setXmlId("1"); 
    registerBySourceId(Mode.class, mode);    
    
    /* TNTP only compatible with parsing a single network layer, so create it */
    final MacroscopicNetworkLayer networkLayer = networkToPopulate.getTransportLayers().getFactory().registerNew();
    networkLayer.registerSupportedMode(mode);
   
    try (Scanner scanner = new Scanner(networkFile)) {
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
        throw new PlanItException(errorMessage);
      }
    } catch (final PlanItException e) {
      throw e;
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when populating physical network in TNTP",e);
    }

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
