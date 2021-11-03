package org.goplanit.tntp.converter.zoning;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.zoning.Zoning;

/**
 * Settings for the Tntp zoning reader
 * 
 * @author markr
 *
 */
public class TntpZoningReaderSettings implements ConverterReaderSettings {
  
  /** the network this zoning relates to */
  protected MacroscopicNetwork referenceNetwork;  
  
  /** the zoning to populate */
  protected Zoning zoningToPopulate;
      
  public Zoning getZoningToPopulate() {
    return zoningToPopulate;
  }

  public void setZoningToPopulate(Zoning zoningToPopulate) {
    this.zoningToPopulate = zoningToPopulate;
  }

  /** Reference network for the zoning
   * 
   * @return reference network
   */
  public MacroscopicNetwork getReferenceNetwork() {
    return referenceNetwork;
  }

  /** Reference network for the zoning
   * 
   * @param referenceNetwork to use
   */
  public void setReferenceNetwork(MacroscopicNetwork referenceNetwork) {
    this.referenceNetwork = referenceNetwork;
  }  

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {  
  }
  
  // GETTERS/SETTERS    
   
}
