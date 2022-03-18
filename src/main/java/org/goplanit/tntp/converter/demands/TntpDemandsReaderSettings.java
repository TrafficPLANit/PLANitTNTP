package org.goplanit.tntp.converter.demands;

import java.util.logging.Logger;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.tntp.enums.TimeUnits;
import org.goplanit.utils.misc.Pair;

/**
 * Settings for the TNTP demands reader
 * <ul>
 * <li>demand file location: mandatory, no default</li>
 * <li>timePeriodDuration: optional, default 1 hour</li>
 * </ul>
 * 
 * @author markr
 *
 */
public class TntpDemandsReaderSettings implements ConverterReaderSettings {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(TntpDemandsReaderSettings.class.getCanonicalName());
  
  /**
   * demand data file location
   */
  private String demandFileLocation;
  
  /** set the duration of the time period */
  private Pair<Double, TimeUnits> timePeriodDuration = DEFAULT_TIME_PERIOD_DURATION;
  
  /** set the start time of the time period */
  private Pair<Double, TimeUnits>  startTimeSinceMidNight = DEFAULT_START_TIME_PERIOD_8AM;
  
  /** default time period duration is set to 1 hour */
  public static Pair<Double, TimeUnits> DEFAULT_TIME_PERIOD_DURATION = Pair.of(1.0, TimeUnits.HOURS);
  
  /** default start time of time period since midnight is set to 8:00 hours */
  public static Pair<Double, TimeUnits> DEFAULT_START_TIME_PERIOD_8AM = Pair.of(8.0, TimeUnits.HOURS);  
    
  /** Validate settings
   * 
   * @return true when valid, false otherwise
   */
  protected boolean validateSettings() {
    if(getDemandFileLocation()==null) {
      LOGGER.severe("TNTP demand file location is not provided, unable to create demands");
      return false;
    }
    if(timePeriodDuration==null) {
      LOGGER.severe("TNTP time period duration not set, unable to create demands");
      return false;
    }
    if(startTimeSinceMidNight==null) {
      LOGGER.severe("TNTP start time of period not set, unable to create demands");
      return false;
    }        
    return true;
  }

  /**
   * Default constructor
   */
  public TntpDemandsReaderSettings() {
    reset();
  }
  
  /**
   * Constructor
   * 
   * @param demandFileLocation to use
   */
  public TntpDemandsReaderSettings(String demandFileLocation) {
    this.demandFileLocation = demandFileLocation;
  }  
  
  /**
   * Constructor
   * 
   * @param demandFileLocation to use
   * @param duration to use
   * @param durationUnit to use
   */
  public TntpDemandsReaderSettings(final String demandFileLocation, final double duration, final TimeUnits durationUnit) {
    this.demandFileLocation = demandFileLocation;
    setTimePeriodDuration(duration, durationUnit);
  }    
  
  /**
   * Log the current settings
   */
  public void logSettings() {
    LOGGER.info("TNTP demand file: " + demandFileLocation);
    LOGGER.info(String.format("TNTP start time of period set to: %.2f (%s)",this.getStartTimeSinceMidNight(), this.getStartTimeSinceMidNightUnit().name()));
    LOGGER.info(String.format("TNTP duration of time period set to: %.2f (%s)",this.getTimePeriodDuration(), this.getTimePeriodDurationUnit().name()));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    demandFileLocation = null;
    timePeriodDuration = DEFAULT_TIME_PERIOD_DURATION;
  }
  
  // GETTERS/SETTERS

  public String getDemandFileLocation() {
    return demandFileLocation;
  }

  public void setDemandFileLocation(String demandFileLocation) {
    this.demandFileLocation = demandFileLocation;
  }  
  
  public void setTimePeriodDuration(final double duration, final TimeUnits durationUnit) {
    this.timePeriodDuration = Pair.of(duration, durationUnit);
  }
  
  public double getTimePeriodDuration() {
    return this.timePeriodDuration.first();
  }
  
  public TimeUnits getTimePeriodDurationUnit() {
    return this.timePeriodDuration.second();
  }

  public void setStartTimeSinceMidNight(final double startTime, final TimeUnits startTimeUnit) {
    this.startTimeSinceMidNight = Pair.of(startTime, startTimeUnit);;
  }   
  
  public double getStartTimeSinceMidNight() {
    return this.startTimeSinceMidNight.first();
  }
  
  public TimeUnits getStartTimeSinceMidNightUnit() {
    return this.startTimeSinceMidNight.second();
  }  
     
}
