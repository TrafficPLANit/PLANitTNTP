package org.planit.tntp.converter.demands;

import org.planit.converter.ConverterReaderSettings;
import org.planit.demands.Demands;
import org.planit.network.MacroscopicNetwork;
import org.planit.zoning.Zoning;

/**
 * Settings for the TNTP demands reader
 * 
 * @author markr
 *
 */
public class TntpDemandsReaderSettings implements ConverterReaderSettings {
  
  /** the network these demands relates to */
  protected MacroscopicNetwork referenceNetwork;  
  
  /** the zoning these demands relate to*/
  protected Zoning referenceZoning;
  
  /** the demands to populate */
  protected Demands demandsToPopulate;               

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
  }
  
  // GETTERS/SETTERS

  public Zoning getReferenceZoning() {
    return referenceZoning;
  }

  public void setReferenceZoning(Zoning referenceZoning) {
    this.referenceZoning = referenceZoning;
  }

  /** Reference network for the zoning
   * 
   * @return reference network
   */
  public MacroscopicNetwork getReferenceNetwork() {
    return referenceNetwork;
  }

  /** Reference network for the demands
   * 
   * @param referenceNetwork to use
   */
  public void setReferenceNetwork(final MacroscopicNetwork referenceNetwork) {
    this.referenceNetwork = referenceNetwork;
  }  
  
  public Demands getDemandsToPopulate() {
    return demandsToPopulate;
  }

  public void setDemandsToPopulate(final Demands demandsToPopulate) {
    this.demandsToPopulate = demandsToPopulate;
  }  
     
}
