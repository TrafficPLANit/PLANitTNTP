package org.planit.tntp.converter.zoning;

import java.io.File;
import java.util.Scanner;
import java.util.logging.Logger;

import org.planit.converter.BaseReaderImpl;
import org.planit.converter.zoning.ZoningReader;
import org.planit.network.MacroscopicNetwork;
import org.planit.tntp.TntpHeaderConstants;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.LoggingUtils;
import org.planit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.network.layer.physical.Node;
import org.planit.utils.zoning.Connectoid;
import org.planit.utils.zoning.Zone;
import org.planit.zoning.Zoning;

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
  private final TntpZoningReaderSettings settings = new TntpZoningReaderSettings();
  
  /**
   * network data file to extract zones from
   */
  private final File networkFile;
  
  /** track number of expected zones based on metadata */
  private int noZones;
  
  /**
   * initialise the source id trackers and populate them for the network references, 
   * so we can lay indices on the source id as well for quick lookups
   * 
   * @param network
   */
  private void initialiseParentNetworkSourceIdTrackers(MacroscopicNetwork network) {    
    initialiseSourceIdMap(Node.class, Node::getExternalId);
    network.getTransportLayers().forEach( layer -> getSourceIdContainer(Node.class).addAll(layer.getNodes()));    
    initialiseSourceIdMap(MacroscopicLinkSegment.class, MacroscopicLinkSegment::getExternalId);
    network.getTransportLayers().forEach( layer -> getSourceIdContainer(MacroscopicLinkSegment.class).addAll(layer.getLinkSegments()));
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
    try (Scanner scanner = new Scanner(networkFile)) {
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

  /** Constructor
   * @param networkFileLocation to parse zoning from (included in network for TNTP)
   * @throws PlanItException thrown if error
   * 
   */
  public TntpZoningReader(String networkFileLocation) throws PlanItException {
    try {
      networkFile = new File(networkFileLocation).getCanonicalFile();
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error in construction of TNTP",e);
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
    
    initialiseSourceIdTrackers();
    initialiseParentNetworkSourceIdTrackers(settings.getReferenceNetwork());
    
    /** read meta data to obtain number of zones in network */
    readMetaData();
    
    Zoning zoning = getSettings().getZoningToPopulate();
    LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"populating zoning");
    for (long zoneSourceId = 1; zoneSourceId <= noZones; zoneSourceId++) {
      /* ZONE */
      final Zone zone = zoning.odZones.getFactory().registerNew();
      /* XML id */
      zone.setXmlId(Long.toString(zone.getId()));      
      /* external id */
      zone.setExternalId(String.valueOf(zoneSourceId));
      registerBySourceId(Zone.class, zone);
      
      /* CONNECTOID */
      final Node node = getBySourceId(Node.class, zone.getExternalId());
      // TODO - calculate connectoid length
      final double connectoidLength = 1.0;
      Connectoid connectoid = zoning.odConnectoids.getFactory().registerNew(node, zone, connectoidLength);
      /* XML id */
      connectoid.setXmlId(Long.toString(connectoid.getId()));
      /* external id */
      connectoid.setExternalId(zone.getExternalId());
    }
    
    return zoning;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void reset() {
    settings.reset();    
  }

}
