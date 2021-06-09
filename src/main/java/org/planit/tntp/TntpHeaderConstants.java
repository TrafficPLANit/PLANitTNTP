package org.planit.tntp;

/**
 * Constants used in the header of TnTp files
 * 
 * @author gman, markr
 *
 */
public class TntpHeaderConstants {

  public static final String NUMBER_OF_ZONES_INDICATOR = "<NUMBER OF ZONES>";
  public static final String NUMBER_OF_NODES_INDICATOR = "<NUMBER OF NODES>";
  public static final String NUMBER_OF_LINKS_INDICATOR = "<NUMBER OF LINKS>";
  public static final String END_OF_METADATA_INDICATOR = "<END OF METADATA>";
  
  /**
   * Parse a value from one of the headers in the network file
   *
   * @param line the current line in the network file
   * @param header the header to be parsed
   * @return the integer value contained within the specified header section
   * @throws Exception thrown if the contents of the header cannot be parsed into an integer
   */
  public static int parseFromHeader(final String line, final String header) throws Exception {
    final String subLine = line.substring(header.length()).trim();
    return Integer.parseInt(subLine);
  }  
}
