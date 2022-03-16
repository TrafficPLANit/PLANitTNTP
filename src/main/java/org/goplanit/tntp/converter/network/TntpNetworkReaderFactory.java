package org.goplanit.tntp.converter.network;

import java.util.logging.Logger;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.LayeredNetwork;
import org.goplanit.utils.id.IdGroupingToken;


/**
 * Factory for creating TNTPNetworkReaders
 * 
 * @author markr
 *
 */
public class TntpNetworkReaderFactory {
  
  /** the logger */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(TntpNetworkReaderFactory.class.getCanonicalName());
  
  /** Create a TNTPNetworkReader which will create its own macroscopic network and non-locale specific defaults for any right hand driving country
   * 
   * @return created TNTP network reader
   */
  public static TntpNetworkReader create() {
    return create(IdGroupingToken.collectGlobalToken());
  }   
  
  /** Create a TNTPNetworkReader which will create its own macroscopic network and non-locale specific defaults for any right hand driving country
   * 
   * @param idToken to use for network
   * @return created TNTP network reader
   */
  public static TntpNetworkReader create(final IdGroupingToken idToken) {
    return create(new TntpNetworkReaderSettings(), new MacroscopicNetwork(idToken));    
  }
  
  /** Create a TNTPNetworkReader which will create its own macroscopic network and non-locale specific 
   *  defaults for any right hand driving country
   * 
   * @param inputDirectory to use (directory only, find first compatible file)
   * @return created TNTP network reader
   */
  public static TntpNetworkReader create(final String networkInputFile, final String nodeInputFile) {
    TntpNetworkReader networkReader = create();
    networkReader.getSettings().setNetworkFile(networkInputFile);
    networkReader.getSettings().setNodeCoordinateFile(nodeInputFile);
    return networkReader;
  }  
  
  /** Create a TNTPNetworkReader which will create its own macroscopic network and non-locale specific defaults for any right hand driving country
   * 
   * @param settings to use
   * @return created TNTP network reader
   */
  public static TntpNetworkReader create(final TntpNetworkReaderSettings settings) {
    return create(settings, new MacroscopicNetwork(IdGroupingToken.collectGlobalToken()));
  }   
  
  /** Create a TNTPNetworkReader which will create its own macroscopic network and non-locale specific defaults for any right hand driving country
   * 
   * @param settings to use
   * @param network to use
   * @return created TNTP network reader
   */
  public static TntpNetworkReader create(final TntpNetworkReaderSettings settings, final LayeredNetwork<?,?> network) {
    return new TntpNetworkReader(settings, network);    
  }        
     
}
