package org.goplanit.tntp.converter.zoning;

import java.io.File;
import java.util.Scanner;
import java.util.logging.Logger;

import org.goplanit.converter.BaseReaderImpl;
import org.goplanit.converter.zoning.ZoningReader;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.tntp.TntpHeaderConstants;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.Connectoid;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.zoning.Zoning;

/**
 * Zoning reader component for TNTP data format
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
  private int noZones;

  /** reference network to use while populating zoning */  
  private MacroscopicNetwork referenceNetwork;
  
  /** the zoning to populate */
  private Zoning zoningToPopulate;  
    
  /** Constructor
   * @param zoningSettings
   * @param referenceNetwork
   * @param zoningToPopulate2
   */
  protected TntpZoningReader(TntpZoningReaderSettings zoningSettings, MacroscopicNetwork referenceNetwork, Zoning zoningToPopulate) {
    this.settings = zoningSettings;
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
      LOGGER.severe("TNTP network is not provided or empty, unable to create zoning");
      return false;
    }    
    if(referenceNetwork==null || referenceNetwork.getTransportLayers().isEmpty()) {
      LOGGER.severe("PLANit zoning instance is not available to populate with TNTP zoning information, unable to create zoning");
      return false;
    }     
    return true;
  }

  /**
   * initialise the source id trackers and populate them for the network references, 
   * so we can lay indices on the source id as well for quick lookups
   * 
   * @param network
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
      noZones = TntpHeaderConstants.parseFromHeader(line, TntpHeaderConstants.NUMBER_OF_ZONES_INDICATOR);
    } 
  }  

  /** Read meta data in order to now how many zones are to be expected
   * 
   * @throws PlanItException thrown if error
   */
  private void readMetaData() throws PlanItException {
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
    } catch (final PlanItException e) {
      throw e;
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when populating physical network in TNTP",e);
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
  public Zoning read() throws PlanItException {
    if(!validateSettings()) {
      return null;
    }
    
    initialiseSourceIdTrackers();
    initialiseParentNetworkSourceIdTrackers();
    
    /** read meta data to obtain number of zones in network */
    readMetaData();
    
    LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"populating zoning");
    for (long zoneSourceId = 1; zoneSourceId <= noZones; zoneSourceId++) {
      /* ZONE */
      final Zone zone = zoningToPopulate.odZones.getFactory().registerNew();
      /* XML id */
      zone.setXmlId(Long.toString(zone.getId()));      
      /* external id */
      zone.setExternalId(String.valueOf(zoneSourceId));
      registerBySourceId(Zone.class, zone);
      
      /* CONNECTOID */
      final Node node = getBySourceId(Node.class, zone.getExternalId());
      // TODO - calculate connectoid length
      final double connectoidLength = 1.0;
      Connectoid connectoid = zoningToPopulate.getOdConnectoids().getFactory().registerNew(node, zone, connectoidLength);
      /* XML id */
      connectoid.setXmlId(Long.toString(connectoid.getId()));
      /* external id */
      connectoid.setExternalId(zone.getExternalId());
      registerBySourceId(Connectoid.class, connectoid);
    }
    
    return zoningToPopulate;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void reset() {
    settings.reset();    
  }

}
