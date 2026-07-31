package org.goplanit.tntp.converter.zoning;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.misc.LoggingUtils;

import java.util.logging.Logger;

/**
 * Settings for the Tntp zoning reader
 * 
 * @author markr
 *
 */
public class TntpZoningReaderSettings implements ConverterReaderSettings {

  private static final Logger LOGGER = Logger.getLogger(TntpZoningReaderSettings.class.getCanonicalName());
  
  /** the network this zoning relates to */
  protected MacroscopicNetwork referenceNetwork;  
    
  /**
   * network data file to extract zones from
   */
  private String networkFileLocation;
  
  /**
   * Default constructor
   */
  public TntpZoningReaderSettings() {
    setNetworkFileLocation(null);
  }  
      
  /** Constructor
   * 
   * @param networkFileLocation to use
   */
  public TntpZoningReaderSettings(String networkFileLocation) {
    this();
    this.setNetworkFileLocation(networkFileLocation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() { 
    networkFileLocation = null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("TNTP Zoning Reader Settings"));
    LOGGER.info(LoggingUtils.settingsValue("Network file location", getNetworkFileLocation(), level));
  }

  /**
   * Get network file location
   * @return location
   */
  public String getNetworkFileLocation() {
    return networkFileLocation;
  }

  /**
   * Set location
   * @param networkFileLocation to set
   */
  public void setNetworkFileLocation(String networkFileLocation) {
    this.networkFileLocation = networkFileLocation;
  }
  
  // GETTERS/SETTERS    
   
}
