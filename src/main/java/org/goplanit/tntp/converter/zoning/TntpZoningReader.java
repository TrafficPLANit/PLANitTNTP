package org.goplanit.tntp.converter.zoning;

import java.io.File;
import java.util.Scanner;
import java.util.logging.Logger;

import org.goplanit.converter.BaseReaderImpl;
import org.goplanit.converter.network.NetworkReader;
import org.goplanit.converter.zoning.ZoningReader;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.tntp.TntpHeaderConstants;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.Connectoid;
import org.goplanit.utils.zoning.OdZone;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.zoning.Zoning;

/**
 * Zoning reader component for TNTP data format. Note that zones are implicitly defined with a single connector in this format, meaning
 * that the first X entries of the links in the network file represent the connector links to X zones (in metadata). to still accomodate
 * this in PLANit, we treat the connector links as physical links (as often they are given a physical capacity in TNTP), and in front of it
 * create a connectoid with a zero length connectoid segment. This also requires one to use a fixed cost connectoid setup since the connectoid
 * has no length in TNTP.
 * 
 * @author gman, markr
 *
 */
public class TntpZoningReader extends BaseReaderImpl<Zoning> implements ZoningReader{
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(TntpZoningReader.class.getCanonicalName());
  
  /** settings for configuration purposes */
  private TntpZoningReaderSettings settings;
  
  /** track number of expected zones based on metadata */
  private int numZones;

  /** reference network to use while populating zoning */  
  private MacroscopicNetwork referenceNetwork;

  /** reference network reader to use to create the reference network, in case no reference network is provided as a
   * starting point */
  private final NetworkReader referenceNetworkReader;
  
  /** the zoning to populate */
  private Zoning zoningToPopulate;  
    
  /** Constructor
   * @param zoningSettings to use
   * @param referenceNetworkReader to use
   */
  protected TntpZoningReader(
      TntpZoningReaderSettings zoningSettings, NetworkReader referenceNetworkReader) {
    this.settings = zoningSettings;
    this.referenceNetworkReader = referenceNetworkReader;

    this.referenceNetwork = null;
    this.zoningToPopulate = null;
  }

  /** Constructor
   * @param zoningSettings to use
   * @param referenceNetwork to use
   * @param zoningToPopulate to use
   */
  protected TntpZoningReader(TntpZoningReaderSettings zoningSettings, MacroscopicNetwork referenceNetwork, Zoning zoningToPopulate) {
    this.settings = zoningSettings;
    this.referenceNetworkReader = null;

    this.referenceNetwork = referenceNetwork;
    this.zoningToPopulate = zoningToPopulate;
  }

  /** Validate settings and log found issue
   * 
   * @return true when ok, false otherwise
   */
  private boolean validateSettings() {
    if(settings.getNetworkFileLocation()==null) {
      LOGGER.severe("TNTP network file location is not provided, unable to create zoning");
      return false;
    }
    if(referenceNetwork==null || referenceNetwork.getTransportLayers().isEmpty()) {
      LOGGER.severe("PLANit network is not provided or empty, unable to create zoning");
      return false;
    }    
    if(zoningToPopulate==null) {
      LOGGER.severe("PLANit zoning instance is not available to populate with TNTP zoning information, unable to create zoning");
      return false;
    }
    if(zoningToPopulate.getCoordinateReferenceSystem() == null){
      var networkCrs = referenceNetwork.getCoordinateReferenceSystem();
      if(networkCrs == null){
        LOGGER.severe("Zoning instance to populate and related network are expected to be initialised with a valid coordinate reference system");
        return false;
      }
      LOGGER.info(String.format("Zoning instance's expected coordinate reference system synced to network coordinate reference system: %s",networkCrs.getName()));
      zoningToPopulate.setCoordinateReferenceSystem(networkCrs);
    }
    return true;
  }

  /**
   * initialise the source id trackers and populate them for the network references, 
   * so we can lay indices on the source id as well for quick lookups
   * 
   */
  private void initialiseParentNetworkSourceIdTrackers() {    
    initialiseSourceIdMap(Node.class, Node::getExternalId);
    referenceNetwork.getTransportLayers().forEach( layer -> getSourceIdContainer(Node.class).addAll(layer.getNodes()));    
    initialiseSourceIdMap(MacroscopicLinkSegment.class, MacroscopicLinkSegment::getExternalId);
    referenceNetwork.getTransportLayers().forEach( layer -> getSourceIdContainer(MacroscopicLinkSegment.class).addAll(layer.getLinkSegments()));
  }  
  
  /**
   * initialise the source id trackers for the to be populated zoning entities, so we can lay indices on the XML id as well for quick lookups
   */
  private void initialiseSourceIdTrackers() {
    initialiseSourceIdMap(Zone.class, Zone::getExternalId);
    initialiseSourceIdMap(Connectoid.class, Connectoid::getExternalId);
  }   
  
  /**
   * Read network metadata from the top of the network input file
   *
   * @param line the current line in the network input file
   * @throws Exception thrown if the contents of the header cannot be parsed into an integer
   */
  private void readMetadataEntry(final String line) throws Exception {
    if (line.startsWith(TntpHeaderConstants.NUMBER_OF_ZONES_INDICATOR)) {
      numZones = TntpHeaderConstants.parseFromHeader(line, TntpHeaderConstants.NUMBER_OF_ZONES_INDICATOR);
    } 
  }  

  /** Read meta data in order to now how many zones are to be expected
   */
  private void readMetaData() {
    try (Scanner scanner = new Scanner(new File(settings.getNetworkFileLocation()).getCanonicalFile())) {
      while (scanner.hasNextLine()) {
        final String line = scanner.nextLine().trim();
        final boolean atEndOfMetadata = line.equals(TntpHeaderConstants.END_OF_METADATA_INDICATOR);
        if (atEndOfMetadata) {
          break;
        }else {
          readMetadataEntry(line);
        }
      }      
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error when populating physical network in TNTP",e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TntpZoningReaderSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public Zoning read() {

    /* prep reference network and zoning to populate based on network reader if that is what we use */
    if(referenceNetworkReader != null){
      var readNetwork = referenceNetworkReader.read();
      if( readNetwork == null || !(readNetwork instanceof MacroscopicNetwork)){
        throw new PlanItRunTimeException("Unable to read network, or network not an instance of MacroscopicNetwork for " +
            "use in conjunction with TnTP zoning reader");
      }
      referenceNetwork = (MacroscopicNetwork) readNetwork;
      zoningToPopulate = new Zoning(referenceNetwork.getIdGroupingToken(), referenceNetwork.getNetworkGroupingTokenId());
      /*TNTP zoning is always 1:1 to its network, so we may assume that the CRS of the zoning is 1:1 to the network as well */
      if(zoningToPopulate.getCoordinateReferenceSystem()==null){
        zoningToPopulate.setCoordinateReferenceSystem(referenceNetwork.getCoordinateReferenceSystem());
      }
    }

    if(!validateSettings()) {
      return null;
    }
    
    initialiseSourceIdTrackers();
    initialiseParentNetworkSourceIdTrackers();
    
    /** read meta data to obtain number of zones in network */
    readMetaData();
    
    LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"populating zoning");
    for (long zoneSourceId = 1; zoneSourceId <= numZones; zoneSourceId++) {
      /* ZONE */
      final OdZone zone = zoningToPopulate.getOdZones().getFactory().registerNew();
      /* XML id */
      zone.setXmlId(Long.toString(zone.getId()));      
      /* external id */
      zone.setExternalId(String.valueOf(zoneSourceId));
      registerBySourceId(Zone.class, zone);
      
      /* CONNECTOID */
      final Node node = getBySourceId(Node.class, zone.getExternalId());      
      
      /*
       *  connectoid length set to zero as connectors are parsed as physical links in network due to limit flexibility in TNTP format itself,
       *  Zone/centroid is placed on top of connectoid which in turn is placed on top of the node in the network
       */
      final double connectoidLength = 0.0;
      Connectoid connectoid = zoningToPopulate.getOdConnectoids().getFactory().registerNew(node, zone, connectoidLength);
      zone.getCentroid().setPosition(node.getPosition());
      
      /* XML id */
      connectoid.setXmlId(Long.toString(connectoid.getId()));
      /* external id */
      connectoid.setExternalId(zone.getExternalId());
      registerBySourceId(Connectoid.class, connectoid);
    }
    
    return zoningToPopulate;
  }

  public MacroscopicNetwork getReferenceNetwork(){
    return referenceNetwork;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void reset() {
  }

}
