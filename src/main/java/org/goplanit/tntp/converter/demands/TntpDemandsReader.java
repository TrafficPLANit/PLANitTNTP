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
import org.goplanit.utils.exceptions.PlanItRunTimeException;
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
    if(!settings.validateSettings()){
      LOGGER.severe("PLANit demand reader settings not valid, unable to create demands");
      return false;
    }
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
   */
  private TimePeriod creatAndRegistereDefaultTimePeriod() {
    // TNTP only has one time period, define it here
    long timePeriodDurationSeconds = Math.round(settings.getTimePeriodDuration() * settings.getTimePeriodDurationUnit().getMultiplier() * 3600);
    long startAtMidNightSeconds = Math.round(settings.getStartTimeSinceMidNight() * settings.getStartTimeSinceMidNightUnit().getMultiplier() * 3600);
    var timePeriod = demandsToPopulate.timePeriods.getFactory().registerNew("TNTP-period", startAtMidNightSeconds, timePeriodDurationSeconds);
    
    /* XML id */
    timePeriod.setXmlId(timePeriod.getDescription());
    /* external id */
    timePeriod.setExternalId("1"); //TODO wrong because no external id is available, but tests use it --> refactor    
    registerBySourceId(TimePeriod.class, timePeriod);
    
    return timePeriod;
  }

  /** Create TNTP default (single) user class
   * 
   * @param travellerType to use for user class
   * @return created user class
   */  
  private UserClass creatAndRegistereDefaultUserClass(TravellerType travellerType) {
    if(referenceNetwork.getModes().size()>1) {
      throw new PlanItRunTimeException("TNTP demands only support single mode, found more than one on reference network");
    }
    var mode = referenceNetwork.getModes().getFirst();
    var userClass = demandsToPopulate.userClasses.getFactory().registerNew("TNTP - user class", mode, travellerType);
    userClass .setXmlId(String.valueOf(userClass .getId()));
    return userClass; 
  }

  /** Create TNTP default (single) traveller type
   * 
   * @return created traveller
   */
  private TravellerType creatAndRegisterDefaultTravellerType() {
    var travellerType =  demandsToPopulate.travelerTypes.getFactory().registerNew("TNTP-traveller type");
    travellerType.setXmlId(String.valueOf(travellerType.getId()));
    return travellerType;
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
   * @param originZone the origin zone for all the demand values
   * @param odDemandMatrix the ODDemandMatrix object to be updated
   * @param mode to use
   * @param timePeriod to use
   * @return trips for this origin in PcuH
   */
  private double updateOdDemandMatrix(final Map<String, Double> demandToDestination, final Zone originZone, final OdDemandMatrix odDemandMatrix, Mode mode, final TimePeriod timePeriod ) {
    
    double originProductionVehH = 0;
    for (final String destinationZoneSourceId : demandToDestination.keySet()) {
      final Zone destinationZone = getBySourceId(Zone.class, destinationZoneSourceId);
      Double destinationDemandVeh = demandToDestination.get(destinationZoneSourceId);
      double destinationDemandVehH = destinationDemandVeh>0 ? (destinationDemandVeh*mode.getPcu())/timePeriod.getDurationHours() : 0; 
      odDemandMatrix.setValue(originZone, destinationZone, destinationDemandVehH);
      originProductionVehH += destinationDemandVehH; 
    }
    return originProductionVehH;
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
                totalTripsPcuH += updateOdDemandMatrix(demandToDestination, originZone, odDemandMatrix, mode, timePeriod);
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
      totalTripsPcuH += updateOdDemandMatrix(demandToDestination, originZone, odDemandMatrix, mode, timePeriod);
      demandsToPopulate.registerOdDemandPcuHour(timePeriod, mode, odDemandMatrix);
    } catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when populating demands in TNTP",e);
    }
    LOGGER.info(String.format("TNTP total OD Demand: %.2f (Pcu/h), %.2f (veh/h), %.2f (veh)",totalTripsPcuH, totalTripsPcuH/mode.getPcu(), (totalTripsPcuH*timePeriod.getDurationHours())/mode.getPcu()));
    
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
