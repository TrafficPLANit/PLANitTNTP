package org.goplanit.tntp.project;

import org.goplanit.project.CustomPlanItProject;
import org.goplanit.tntp.converter.demands.TntpDemandsReaderSettings;
import org.goplanit.tntp.converter.network.TntpNetworkReaderSettings;
import org.goplanit.tntp.converter.zoning.TntpZoningReaderSettings;
import org.goplanit.tntp.input.TntpInputBuilder;
import org.goplanit.utils.exceptions.PlanItException;

/**
 * TNTP implementation of CustomPlanItProject
 *
 * @author gman6028
 *
 */
public class TntpProject extends CustomPlanItProject {
  
  /**
   * Constructor
   *
   * @param networkFileLocation network input file location
   * @param demandFileLocation demand input file location
   * @param nodeCoordinateFileLocation node coordinate file location
   * @throws PlanItException thrown if there is an error
   */
   public TntpProject(final String networkFileLocation, final String demandFileLocation, final String nodeCoordinateFileLocation) throws PlanItException {
     super(new TntpInputBuilder(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation));
   }  

  /**
   * Constructor
   *
   * @param tntp Tntp object already instantiated
   */
  public TntpProject(final TntpInputBuilder tntp) {
    super(tntp);
  }
  
  public TntpDemandsReaderSettings getDemandsReaderSettings() {
    return ((TntpInputBuilder)this.inputBuilderListener).getDemandsReaderSettings();
  }
  
  public TntpNetworkReaderSettings getNetworkReaderSettings() {
    return ((TntpInputBuilder)this.inputBuilderListener).getNetworkReaderSettings();
  }
  
  public TntpZoningReaderSettings getZoningReaderSettings() {
    return ((TntpInputBuilder)this.inputBuilderListener).getZoningReaderSettings();
  }    

}