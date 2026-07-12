package org.goplanit.tntp.converter.demands;

import java.util.logging.Logger;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.tntp.enums.TimeUnits;
import org.goplanit.utils.misc.LoggingUtils;
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
  public TntpDemandsReaderSettings(
      final String demandFileLocation, final double duration, final TimeUnits durationUnit) {
    this.demandFileLocation = demandFileLocation;
    setTimePeriodDuration(duration, durationUnit);
  }

  /**
   * Log the current settings
   */
  @Override
  public void logSettings() {
    LOGGER.info(LoggingUtils.settingsHeader("TNTP Demands Reader Settings"));
    LOGGER.info(LoggingUtils.settingsValue("Demand file", getDemandFileLocation(), 0));
    LOGGER.info(LoggingUtils.settingsValue(
        "Start time of period",
        String.format("%.2f (%s)", getStartTimeSinceMidNight(), getStartTimeSinceMidNightUnit().name()),
        0));
    LOGGER.info(LoggingUtils.settingsValue(
        "Duration of time period",
        String.format("%.2f (%s)", getTimePeriodDuration(), getTimePeriodDurationUnit().name()),
        0));
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

  /**
   * Gets the demand file location.
   *
   * @return the demand file location path
   */
  public String getDemandFileLocation() {
    return demandFileLocation;
  }

  /**
   * Sets the demand file location.
   *
   * @param demandFileLocation the file location to set
   */
  public void setDemandFileLocation(String demandFileLocation) {
    this.demandFileLocation = demandFileLocation;
  }

  /**
   * Sets the duration of the time period.
   *
   * @param duration the duration value
   * @param durationUnit the time unit for the duration
   */
  public void setTimePeriodDuration(final Number duration, final TimeUnits durationUnit) {
    this.timePeriodDuration = Pair.of(duration.doubleValue(), durationUnit);
  }

  /**
   * Gets the value of the time period duration.
   *
   * @return the duration value as a double
   */
  public double getTimePeriodDuration() {
    return this.timePeriodDuration.first();
  }

  /**
   * Gets the unit of the time period duration.
   *
   * @return the {@link TimeUnits} used for the duration
   */
  public TimeUnits getTimePeriodDurationUnit() {
    return this.timePeriodDuration.second();
  }

  /**
   * Sets the start time of the time period since midnight.
   *
   * @param startTime the start time value
   * @param startTimeUnit the time unit for the start time
   */
  public void setStartTimeSinceMidnight(final Number startTime, final TimeUnits startTimeUnit) {
    this.startTimeSinceMidNight = Pair.of(startTime.doubleValue(), startTimeUnit);
  }

  /**
   * Gets the start time value since midnight.
   *
   * @return the start time value as a double
   */
  public double getStartTimeSinceMidNight() {
    return this.startTimeSinceMidNight.first();
  }

  /**
   * Gets the unit for the start time since midnight.
   *
   * @return the {@link TimeUnits} used for the start time
   */
  public TimeUnits getStartTimeSinceMidNightUnit() {
    return this.startTimeSinceMidNight.second();
  }

}
