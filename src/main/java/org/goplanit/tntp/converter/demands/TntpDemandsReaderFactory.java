package org.goplanit.tntp.converter.demands;

import org.goplanit.demands.Demands;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.tntp.converter.zoning.TntpZoningReader;
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
   * @param idToken to use when generating demands instance
   * @return created TNTP demands reader
   */
  public static TntpDemandsReader create(final MacroscopicNetwork network, final Zoning zoning, final IdGroupingToken idToken){
    return create(new TntpDemandsReaderSettings(),network, zoning, new Demands(idToken));
  }    
  
  /** Factory method using global id token to create instance of demands, requires user to set input file
   * 
   * @param network to extract references from (if any)
   * @param zoning to extract references from (if any)
   * @return created TNTP demands reader
   */
  public static TntpDemandsReader create(final MacroscopicNetwork network, final Zoning zoning){
    return create(network, zoning, IdGroupingToken.collectGlobalToken());
  }  
    
  /** Factory method using global id token to create instance of demands
   * 
   * @param demandInputFile to use (should contain the trips)
   * @param network to extract references from (if any)
   * @param zoning to extract references from (if any)
   * @return created TNTP demands reader
   */
  public static TntpDemandsReader create(
      final String demandInputFile, final MacroscopicNetwork network, final Zoning zoning){
    return create(demandInputFile, network, zoning, IdGroupingToken.collectGlobalToken());
  }
  
  /** Factory method
   * 
   * @param demandInputFile to use (should contain the trips)
   * @param network to extract references from (if any)
   * @param zoning to extract references from (if any)
   * @param idToken to use for the demands instance
   * @return created TNTP demands reader
   */  
  public static TntpDemandsReader create(String demandInputFile, final MacroscopicNetwork network, final Zoning zoning, final IdGroupingToken idToken) {
    return create(new TntpDemandsReaderSettings(demandInputFile), network, zoning, new Demands(idToken));
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

  /** Factory method
   *
   * @param referenceZoningReader to use
   * @return created PLANit zoning reader
   */
  public static TntpDemandsReader create(final TntpZoningReader referenceZoningReader) {
    return create(new TntpDemandsReaderSettings(), referenceZoningReader);
  }

    /** Factory method
     *
     * @param demandsSettings to use
     * @param referenceZoningReader to use
     * @return created PLANit zoning reader
     */
    public static TntpDemandsReader create(
        final TntpDemandsReaderSettings demandsSettings, final TntpZoningReader referenceZoningReader) {
      return new TntpDemandsReader(demandsSettings, referenceZoningReader);
    }
  

}
