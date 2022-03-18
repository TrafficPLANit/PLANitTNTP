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
import org.goplanit.userclass.TravellerType;
import org.goplanit.userclass.UserClass;
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
  private TntpDemandsReaderSettings settings;
  
  /** the network these demands relates to */
  private MacroscopicNetwork referenceNetwork;  
  
  /** the zoning these demands relate to*/
  private Zoning referenceZoning;  
  
  /** the demands to populate */
  private Demands demandsToPopulate;               
    
  /** Validate settings and log found issue
   * 
   * @return true when ok, false otherwise
   */
  private boolean validateSettings() {
    if(!settings.validateSettings())
    if(referenceNetwork==null || referenceNetwork.getTransportLayers().isEmpty()) {
      LOGGER.severe("PLANit reference network is not provided or empty, unable to create demands");
      return false;
    }    
    if(referenceZoning==null || referenceZoning.getOdZones().isEmpty()) {
      LOGGER.severe("PLANit reference zoning is not provided or empty, unable to create demands");
      return false;
    }     
    if(demandsToPopulate==null) {
      LOGGER.severe("PLANit demands is not provided to populate, unable to create demands");
      return false;
    }      
    return true;
  }

  /** Create TNTP default (single) time period
   * 
   * @return created time period
   * @throws PlanItException thrown if error
   */
  private TimePeriod creatAndRegistereDefaultTimePeriod() throws PlanItException {
    // TNTP only has one time period, define it here
    long timePeriodDurationSeconds = Math.round(settings.getTimePeriodDuration() * settings.getTimePeriodDurationUnit().getMultiplier() * 3600);
    long startAtMidNightSeconds = Math.round(settings.getStartTimeSinceMidNight() * settings.getStartTimeSinceMidNightUnit().getMultiplier() * 3600);
    return demandsToPopulate.timePeriods.createAndRegisterNewTimePeriod("TNTP-period", startAtMidNightSeconds, timePeriodDurationSeconds);
  }

  /** Create TNTP default (single) user class
   * 
   * @param travellerType to use for user class
   * @return created user class
   * @throws PlanItException thrown if error
   */  
  private UserClass creatAndRegistereDefaultUserClass(TravellerType travellerType) throws PlanItException {
    if(referenceNetwork.getModes().size()>1) {
      throw new PlanItException("TNTP demands only support single mode, found more than one on reference network");
    }
    var mode = referenceNetwork.getModes().getFirst();
    return demandsToPopulate.userClasses.createAndRegister("TNTP - user class", mode, travellerType);
  }

  /** Create TNTP default (single) traveller type
   * 
   * @return created traveller
   * @throws PlanItException thrown if error
   */    
  private TravellerType creatAndRegisterDefaultTravellerType() {
    return demandsToPopulate.travelerTypes.createAndRegisterNew("TNTP-traveller type");
  }

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
    getSourceIdContainer(Zone.class).addAll(zoning.getOdZones());
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
   * @return trips for this origin in PcuH
   */
  private double updateOdDemandMatrix(final Map<String, Double> demandToDestination, final Zoning zoning,
      final Zone originZone, final OdDemandMatrix odDemandMatrix) {
    
    double originProductionPcuH = 0;
    for (final String destinationZoneSourceId : demandToDestination.keySet()) {
      final Zone destinationZone = getBySourceId(Zone.class, destinationZoneSourceId);
      Double destinationDemandPcuH = demandToDestination.get(destinationZoneSourceId);
      odDemandMatrix.setValue(originZone, destinationZone, demandToDestination.get(destinationZoneSourceId));
      originProductionPcuH += destinationDemandPcuH>0 ? destinationDemandPcuH : 0; 
    }
    return originProductionPcuH;
  }  

  /** Constructor 
   * 
   * @param settings to use
   * @param referenceNetwork to use
   * @param referenceZoning to use
   * @param demandsToPopulate to use
   */
  protected TntpDemandsReader(final TntpDemandsReaderSettings settings, final MacroscopicNetwork referenceNetwork, final Zoning referenceZoning, final Demands demandsToPopulate) {
    this.settings = settings;
    this.referenceNetwork = referenceNetwork;
    this.referenceZoning = referenceZoning;
    this.demandsToPopulate = demandsToPopulate;      
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
    if(!validateSettings()) {
      return null;
    }       
    LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"populating Demands");
    getSettings().logSettings();    
    
    initialiseSourceIdTrackers();
    initialiseParentSourceIdTrackers(referenceNetwork, referenceZoning);

    /* time period */
    var timePeriod = creatAndRegistereDefaultTimePeriod();

    /* traveller type */
    var travellerType = creatAndRegisterDefaultTravellerType();
    LOGGER.info("TNTP traveller type: "+ travellerType.toString());
    
    /* user class */
    var userClass = creatAndRegistereDefaultUserClass(travellerType);
    LOGGER.info("TNTP traveller type: "+ userClass.toString());
    
    var mode = referenceNetwork.getTransportLayers().getFirst().getFirstSupportedMode();
    
    /* XML id */
    timePeriod.setXmlId(timePeriod.getDescription());
    /* external id */
    timePeriod.setExternalId("1"); //TODO wrong because no external id is available, but tests use it --> refactor    
    registerBySourceId(TimePeriod.class, timePeriod);    
    
    double totalTripsPcuH = 0;
    try (Scanner scanner = new Scanner(new File(settings.getDemandFileLocation()).getCanonicalFile())) {
      boolean readingMetadata = true;
      Zone originZone = null;
      Map<String, Double> demandToDestination = null;
      final OdDemandMatrix odDemandMatrix = new OdDemandMatrix(referenceZoning.getOdZones());
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
            if (referenceZoning.getOdZones().size() != Integer.parseInt(subLine)) {
              throw new PlanItException("Network file contained %d but demand file indicates %s zones", referenceZoning.getOdZones().size(), subLine);
            }
          }
        } else if (!atEndOfMetadata) {
          if ((!line.isEmpty()) && (firstChar != '~')) {
            if (line.startsWith("Origin")) {
              if (demandToDestination != null) {
                totalTripsPcuH += updateOdDemandMatrix(demandToDestination, referenceZoning, originZone, odDemandMatrix) * mode.getPcu();
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
      totalTripsPcuH += updateOdDemandMatrix(demandToDestination, referenceZoning, originZone, odDemandMatrix) * mode.getPcu();
      demandsToPopulate.registerOdDemandPcuHour(timePeriod, mode, odDemandMatrix);
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when populating demands in TNTP",e);
    }
    LOGGER.info(String.format("TNTP total OD Demand (Pcu/h): %.2f",totalTripsPcuH));
    
    return demandsToPopulate;
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void reset() {
    settings.reset();
  }

}
