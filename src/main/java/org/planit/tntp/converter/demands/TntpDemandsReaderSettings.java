package org.planit.tntp.converter.demands;

import java.util.Map;

import org.planit.converter.ConverterReaderSettings;
import org.planit.demands.Demands;
import org.planit.network.InfrastructureNetwork;
import org.planit.utils.mode.Mode;
import org.planit.utils.zoning.Zone;
import org.planit.zoning.Zoning;

/**
 * Settings for the TNTP demands reader
 * 
 * @author markr
 *
 */
public class TntpDemandsReaderSettings implements ConverterReaderSettings {
  
  /** the network these demands relates to */
  protected InfrastructureNetwork<?,?> referenceNetwork;  
  
  /** the zoning these demands relate to*/
  protected Zoning referenceZoning;
  
  /** the demands to populate */
  protected Demands demandsToPopulate;  
  
  /**
   * Map which stores references to modes by TNTP Id
   */  
  protected Map<String, Mode> sourceIdModeMap;
  
  /**
   * Map which stores references to zones by TNTP Id
   */  
   protected Map<String, Zone> sourceIdZoneMap;  
      
  /** Map to zones by TNTP id when parsing
   * 
   * @return externalIdZoneMap
   */
  protected Map<String, Zone> getMapToIndexZoneBySourceIds() {
    return this.sourceIdZoneMap;
  }  
  
  /** Map to modes by TNTP id when parsing
   * 
   * @return externalIdModeMap
   */
  protected Map<String, Mode> getMapToIndexModeBySourceIds() {
    return this.sourceIdModeMap;
  }   
      

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    sourceIdModeMap = null;
    sourceIdZoneMap = null;
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
  public InfrastructureNetwork<?, ?> getReferenceNetwork() {
    return referenceNetwork;
  }

  /** Reference network for the demands
   * 
   * @param referenceNetwork to use
   */
  public void setReferenceNetwork(final InfrastructureNetwork<?, ?> referenceNetwork) {
    this.referenceNetwork = referenceNetwork;
  }  
  
  public Demands getDemandsToPopulate() {
    return demandsToPopulate;
  }

  public void setDemandsToPopulate(final Demands demandsToPopulate) {
    this.demandsToPopulate = demandsToPopulate;
  }  
  
  /** Use provided map to index zones by metroscan id when parsing
   * 
   * @param sourceIdZoneMap to use
   */
  public void setMapToIndexZoneBySourceIds(final Map<String, Zone> sourceIdZoneMap) {
    this.sourceIdZoneMap = sourceIdZoneMap;
  }
  
  /** Use provided map to index modes by metroscan id when parsing
   * 
   * @param sourceIdModeMap to use
   */
  public void setMapToIndexModeBySourceIds(final Map<String, Mode> sourceIdModeMap) {
    this.sourceIdModeMap = sourceIdModeMap;
  }    
   
}
