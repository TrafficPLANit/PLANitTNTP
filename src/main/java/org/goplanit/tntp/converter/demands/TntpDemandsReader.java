package org.goplanit.tntp.converter.demands;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import org.goplanit.converter.BaseReaderImpl;
import org.goplanit.converter.demands.DemandsReader;
import org.goplanit.demands.Demands;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.od.demand.OdDemandMatrix;
import org.goplanit.tntp.TntpHeaderConstants;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.time.TimePeriod;
import org.goplanit.utils.zoning.Zone;
import org.goplanit.zoning.Zoning;

/**
 * Demands reader component for TNTP data format
 * 
 * @author gman, markr
 *
 */
public class TntpDemandsReader extends BaseReaderImpl<Demands> implements DemandsReader{
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(TntpDemandsReader.class.getCanonicalName());
  
  /**
   * settings to use
   */
  private final TntpDemandsReaderSettings settings = new TntpDemandsReaderSettings();  
  
  /**
   * demand data file
   */
  private File demandFile;

  /**
   * TNTP only has one time period
   */
  private TimePeriod timePeriod;
  
  /**
   * initialise the source id trackers and populate them for the network and or zoning references, 
   * so we can lay indices on the source id as well for quick lookups
   * 
   * @param network to use
   * @param zoning to use
   */
  private void initialiseParentSourceIdTrackers(MacroscopicNetwork network, Zoning zoning) {    
    initialiseSourceIdMap(Mode.class, Mode::getXmlId, network.getModes());
    
    initialiseSourceIdMap(Zone.class, Zone::getExternalId);
    getSourceIdContainer(Zone.class).addAll(zoning.odZones);
  } 
  
  /**
   * initialise the source id trackers of generated PLANit entity types so we can lay indices on the source id as well for quick lookups
   * 
   */
  private void initialiseSourceIdTrackers() {    
    initialiseSourceIdMap(TimePeriod.class, TimePeriod::getExternalId);
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
      final Zone originZone, final OdDemandMatrix odDemandMatrix) {
    
    for (final String destinationZoneSourceId : demandToDestination.keySet()) {
      final Zone destinationZone = getBySourceId(Zone.class, destinationZoneSourceId);
      odDemandMatrix.setValue(originZone, destinationZone, demandToDestination.get(destinationZoneSourceId));
    }
    
  }  

  /** Constructor 
   * 
   * @param demandFileLocation location of demands file to parse
   * @throws PlanItException thrown if error
   */
  public TntpDemandsReader(String demandFileLocation) throws PlanItException {

    try {      
      this.demandFile = new File(demandFileLocation).getCanonicalFile();      
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error in construction of TNTP",e);
    }
    
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TntpDemandsReaderSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public Demands read() throws PlanItException {
    
    LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"populating Demands");
    final Zoning zoning = getSettings().getReferenceZoning();
    final Demands demands = getSettings().getDemandsToPopulate();
    final MacroscopicNetwork network = getSettings().getReferenceNetwork();
    
    initialiseSourceIdTrackers();
    initialiseParentSourceIdTrackers(network, zoning);
    
    // TNTP only has one time period, define it here
    int wholeDaydurationSeconds = 24*3600;
    int startAtMidNightSeconds = 0;
    timePeriod = demands.timePeriods.createAndRegisterNewTimePeriod("All Day", startAtMidNightSeconds, wholeDaydurationSeconds);
    /* XML id */
    timePeriod.setXmlId(Long.toString(timePeriod.getId()));
    /* external id */
    timePeriod.setExternalId("1"); //TODO wrong because no external id is available, but tests use it --> refactor    
    registerBySourceId(TimePeriod.class, timePeriod);    
    
    try (Scanner scanner = new Scanner(demandFile)) {
      boolean readingMetadata = true;
      Zone originZone = null;
      Map<String, Double> demandToDestination = null;
      final OdDemandMatrix odDemandMatrix = new OdDemandMatrix(zoning.odZones);
      while (scanner.hasNextLine()) {
        final String line = scanner.nextLine().trim();
        final char firstChar = (line.isEmpty()) ? 'x' : line.charAt(0);
        final boolean atEndOfMetadata = line.equals(TntpHeaderConstants.END_OF_METADATA_INDICATOR);
        if (atEndOfMetadata) {
          readingMetadata = false;
        }
        if (readingMetadata) {
          if (line.startsWith(TntpHeaderConstants.NUMBER_OF_ZONES_INDICATOR)) {
            final String subLine = line.substring(TntpHeaderConstants.NUMBER_OF_ZONES_INDICATOR.length()).trim();
            if (zoning.odZones.size() != Integer.parseInt(subLine)) {
              throw new PlanItException("Network file contained %d but demand file indicates %s zones", zoning.odZones.size(), subLine);
            }
          }
        } else if (!atEndOfMetadata) {
          if ((!line.isEmpty()) && (firstChar != '~')) {
            if (line.startsWith("Origin")) {
              if (demandToDestination != null) {
                updateOdDemandMatrix(demandToDestination, zoning, originZone, odDemandMatrix);
              }
              final String[] cols = line.split("\\s+");
              originZone = getBySourceId(Zone.class, cols[1]);
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
      demands.registerOdDemandPcuHour(timePeriod, network.getTransportLayers().getFirst().getFirstSupportedMode(), odDemandMatrix);
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when populating demands in TNTP",e);
    }
    
    return demands;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void reset() {
    settings.reset();
  }

}
