package org.goplanit.tntp.converter.zoning;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.zoning.Zoning;

/**
 * Factory class for creating zoning reader in the TNTP format. Note that the zoning information of TNTP is captured within its network definition.
 * 
 * @author markr
 *
 */
public class TntpZoningReaderFactory {
  
  /** Factory method, will create zoning based on idToken provided and use it to populate when reading in TNTP zoning
   * 
   * @param network to extract references from (if any)
   * @return created TNTP zoning reader
   */
  public static TntpZoningReader create(final MacroscopicNetwork network, final IdGroupingToken idtoken){
    return create(new TntpZoningReaderSettings(),network, new Zoning(idtoken, network.getNetworkGroupingTokenId()));
  }    
  
  /** Factory method
   * 
   * @param network to extract references from (if any)
   * @param zoningToPopulate to populate
   * @return created TNTP zoning reader
   */
  public static TntpZoningReader create(final MacroscopicNetwork network, final Zoning zoningToPopulate){
    return create(new TntpZoningReaderSettings(),network, zoningToPopulate);
  }  
    
  /** Factory method
   * 
   * @param networkInputFile to use (zone definition is included in network definition)
   * @param network to extract references from (if any)
   * @param zoningToPopulate to populate
   * @return created TNTP zoning reader
   */
  public static TntpZoningReader create(
      final String networkInputFile, final MacroscopicNetwork network, final Zoning zoningToPopulate){
    return create(new TntpZoningReaderSettings(networkInputFile),network, zoningToPopulate);
  }
  
  /** Factory method
   * 
   * @param networkInputFile to use (zone definition is included in network definition)
   * @param network to extract references from (if any)
   * @param idToken to use for the zoning instance
   * @return created TNTP zoning reader
   */  
  public static TntpZoningReader create(String networkInputFile, MacroscopicNetwork network, IdGroupingToken idToken) {
    return create(networkInputFile, network, new Zoning(idToken, network.getNetworkGroupingTokenId()));
  }

  /** Factory method
   * 
   * @param zoningSettings to use
   * @param referenceNetwork to use
   * @param zoningToPopulate to use
   * @return created PLANit zoning reader
   */
  public static TntpZoningReader create(
      final TntpZoningReaderSettings zoningSettings, final MacroscopicNetwork referenceNetwork, final Zoning zoningToPopulate) {
    return new TntpZoningReader(zoningSettings, referenceNetwork, zoningToPopulate);
  }  
  

}
