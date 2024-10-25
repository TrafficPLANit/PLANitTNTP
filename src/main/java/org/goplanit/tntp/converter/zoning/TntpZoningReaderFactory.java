package org.goplanit.tntp.converter.zoning;

import org.goplanit.converter.network.NetworkReader;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.tntp.converter.network.TntpNetworkReader;
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
   * @param idToken to use
   * @return created TNTP zoning reader
   */
  public static TntpZoningReader create(final MacroscopicNetwork network, final IdGroupingToken idToken){
    var zoning = new Zoning(idToken, network.getNetworkGroupingTokenId());
    zoning.setCoordinateReferenceSystem(network.getCoordinateReferenceSystem());
    return create(new TntpZoningReaderSettings(),network, zoning);
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
    var zoning = new Zoning(idToken, network.getNetworkGroupingTokenId());
    zoning.setCoordinateReferenceSystem(network.getCoordinateReferenceSystem());
    return create(networkInputFile, network, zoning);
  }


  /** Factory method
   * 
   * @param zoningSettings to use
   * @param referenceNetwork to use
   * @param zoningToPopulate to use
   * @return created TNTP zoning reader
   */
  public static TntpZoningReader create(
      final TntpZoningReaderSettings zoningSettings, final MacroscopicNetwork referenceNetwork, final Zoning zoningToPopulate) {
    return new TntpZoningReader(zoningSettings, referenceNetwork, zoningToPopulate);
  }

  /** Factory method to create
   *
   * @param referenceNetworkReader to use
   * @return created TNTP zoning reader
   */
  public static TntpZoningReader create(final NetworkReader referenceNetworkReader) {
    return create(new TntpZoningReaderSettings(), referenceNetworkReader);
  }

  /** Factory method to create a TNTP Zoning reader using a TNTP network reader information to minimise configuration
   * overhead
   *
   * @param tntpNetworkReader to use
   * @return created TNTP zoning reader
   */
  public static TntpZoningReader createFromTntpNetworkReader(final TntpNetworkReader tntpNetworkReader) {
    var tntpZoningReader =  create(new TntpZoningReaderSettings(), tntpNetworkReader);
    tntpZoningReader.getSettings().setNetworkFileLocation(tntpNetworkReader.getSettings().getNetworkFile());
    return tntpZoningReader;
  }

  /** Factory method allowing use of any network reader compatible with this TNTP zoning reader
   *
   * @param zoningSettings to use (assumes explicit configuration of TNTP file location on these settings)
   * @param referenceNetworkReader to use
   * @return created TNTP zoning reader
   */
  public static TntpZoningReader create(
      final TntpZoningReaderSettings zoningSettings, final NetworkReader referenceNetworkReader) {
    return new TntpZoningReader(zoningSettings, referenceNetworkReader);
  }


}
