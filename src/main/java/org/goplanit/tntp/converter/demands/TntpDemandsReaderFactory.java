package org.goplanit.tntp.converter.demands;

import org.goplanit.demands.Demands;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.zoning.Zoning;

/**
 * Factory class for creating demands reader in the TNTP format. 
 * 
 * @author markr
 *
 */
public class TntpDemandsReaderFactory {
  
  /** Factory method, will create Demands based on idToken provided and use it to populate when reading in TNTP demand, , requires user to set input file
   * 
   * @param network to extract references from (if any)
   * @param zoning to extract references from (if any)
   * @param odToken to use when generating demands instance
   * @return created TNTP demands reader
   */
  public static TntpDemandsReader create(final MacroscopicNetwork network, final Zoning zoning, final IdGroupingToken idtoken){
    return create(new TntpDemandsReaderSettings(),network, zoning, new Demands(idtoken));
  }    
  
  /** Factory method using global id token to create instance of demands, requires user to set input file
   * 
   * @param network to extract references from (if any)
   * @param zoning to extract references from (if any)
   * @return created TNTP demands reader
   */
  public static TntpDemandsReader create(final MacroscopicNetwork network, final Zoning zoningToPopulate){
    return create(network, zoningToPopulate, IdGroupingToken.collectGlobalToken());
  }  
    
  /** Factory method using global id token to create instance of demands
   * 
   * @param demandInputFile to use (should contain the trips)
   * @param network to extract references from (if any)
   * @param zoning to extract references from (if any)
   * @return created TNTP demands reader
   */
  public static TntpDemandsReader create(
      final String demandInputFile, final MacroscopicNetwork network, final Zoning zoningToPopulate){
    return create(demandInputFile, network, zoningToPopulate, IdGroupingToken.collectGlobalToken());
  }
  
  /** Factory method
   * 
   * @param demandInputFile to use (should contain the trips)
   * @param network to extract references from (if any)
   * @param zoning to extract references from (if any)
   * @param idToken to use for the demands instance
   * @return created TNTP demands reader
   */  
  public static TntpDemandsReader create(String networkInputFile, final MacroscopicNetwork network, final Zoning zoningToPopulate, final IdGroupingToken idToken) {
    return create(new TntpDemandsReaderSettings(networkInputFile), network, zoningToPopulate, new Demands(idToken));
  }

  /** Factory method
   * 
   * @param demandsSettings to use
   * @param referenceNetwork to use
   * @param referenceZoning to use
   * @param demandsToPopulate to use
   * @return created PLANit zoning reader
   */
  public static TntpDemandsReader create(
      final TntpDemandsReaderSettings demandsSettings, final MacroscopicNetwork referenceNetwork, final Zoning referenceZoning, final Demands demandsToPopulate) {
    return new TntpDemandsReader(demandsSettings, referenceNetwork, referenceZoning, demandsToPopulate);
  }  
  

}
