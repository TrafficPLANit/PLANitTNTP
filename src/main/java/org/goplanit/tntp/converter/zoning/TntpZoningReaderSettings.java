package org.goplanit.tntp.converter.zoning;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.network.MacroscopicNetwork;

/**
 * Settings for the Tntp zoning reader
 * 
 * @author markr
 *
 */
public class TntpZoningReaderSettings implements ConverterReaderSettings {
  
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

  public String getNetworkFileLocation() {
    return networkFileLocation;
  }

  public void setNetworkFileLocation(String networkFileLocation) {
    this.networkFileLocation = networkFileLocation;
  }
  
  // GETTERS/SETTERS    
   
}
