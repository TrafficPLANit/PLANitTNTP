package org.goplanit.tntp.input;

import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.goplanit.component.event.PlanitComponentEvent;
import org.goplanit.component.event.PopulateDemandsEvent;
import org.goplanit.component.event.PopulateNetworkEvent;
import org.goplanit.component.event.PopulatePhysicalCostEvent;
import org.goplanit.component.event.PopulateZoningEvent;
import org.goplanit.cost.physical.AbstractPhysicalCost;
import org.goplanit.cost.physical.BprLinkTravelTimeCost;
import org.goplanit.demands.Demands;
import org.goplanit.input.InputBuilderListener;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.tntp.converter.demands.TntpDemandsReader;
import org.goplanit.tntp.converter.demands.TntpDemandsReaderFactory;
import org.goplanit.tntp.converter.demands.TntpDemandsReaderSettings;
import org.goplanit.tntp.converter.network.TntpNetworkReader;
import org.goplanit.tntp.converter.network.TntpNetworkReaderFactory;
import org.goplanit.tntp.converter.network.TntpNetworkReaderSettings;
import org.goplanit.tntp.converter.zoning.TntpZoningReader;
import org.goplanit.tntp.converter.zoning.TntpZoningReaderFactory;
import org.goplanit.tntp.converter.zoning.TntpZoningReaderSettings;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.zoning.Zoning;

/**
 * Class which reads input from TNTP files
 *
 * @author gman6028, markr
 *
 */
public class TntpInputBuilder extends InputBuilderListener {

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(TntpInputBuilder.class.getCanonicalName());
    
  private final TntpNetworkReaderSettings networkSettings;
    
  private final TntpDemandsReaderSettings demandsReaderSettings;
  
  private final TntpZoningReaderSettings zoningReaderSettings;
  
  /** track parsed BPR parameters from network parsing and make available to cost initialisation */
  private Map<LinkSegment, Pair<Double, Double>> bprParametersPerLinkSegment;

  
  /**
   * Creates the physical network object from the data in the input file
   *
   * @param macroscopicNetwork the network object to be populated from the input data
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateMacroscopicNetwork( final MacroscopicNetwork macroscopicNetwork) throws PlanItException {
       
    TntpNetworkReader networkReader = TntpNetworkReaderFactory.create(networkSettings, macroscopicNetwork);

    /* parse */
    networkReader.read();
    this.bprParametersPerLinkSegment = networkReader.getParsedBprParameters();
    
    networkReader.reset();
  }

  /**
   * Populates the Demands object from the input file
   *
   * @param demands the Demands object to be populated from the input data
   * @param zoning Zoning object previously defined
   * @param network Network to use
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateDemands( final Demands demands, final Zoning zoning, final MacroscopicNetwork network) throws PlanItException {
    
    TntpDemandsReader demandsReader = TntpDemandsReaderFactory.create(demandsReaderSettings, network, zoning, demands);
            
    /* parse */
    demandsReader.read();
    demandsReader.reset();
  }

  /**
   * Creates the Zoning object and connectoids from the data in the network file
   *
   * In TNTP this method does not need to read a TAZ file. It simply uses the number of zones, which
   * has already been read in from the network file.
   *
   * @param zoning the Zoning object to be populated from the input data
   * @param parameter1 the physical network object previously created
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateZoning(final Zoning zoning, final Object parameter1) throws PlanItException {
    
    if (!(parameter1 instanceof MacroscopicNetwork)) {
      throw new PlanItException("TNTP reader currently only supports writing macroscopic networks");
    }
    final MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork) parameter1;
    
    TntpZoningReader zoningReader = TntpZoningReaderFactory.create(zoningReaderSettings, macroscopicNetwork, zoning);
        
    /* parse */
    zoningReader.read();
    zoningReader.reset();
  }

  /**
   * Populate the BPR parameters
   *
   * @param costComponent the BPRLinkTravelTimeCost to be populated
   * @param parentNetwork top use for costs
   * @throws PlanItException thrown if there is an error
   */
  protected void populatePhysicalCost( final AbstractPhysicalCost costComponent, final MacroscopicNetwork parentNetwork) throws PlanItException {
    LOGGER.info(LoggingUtils.getClassNameWithBrackets(this)+"populating BPR link costs");
    
    Mode mode = parentNetwork.getModes().getFirst();
    if (bprParametersPerLinkSegment == null) {
      LOGGER.warning("BPR parameters not available upon populating physical costs in TNTP input builder, ignore");
      return;
    }
    if(!(costComponent instanceof BprLinkTravelTimeCost)) {
      LOGGER.warning(String.format("Expected BPR cost to be populated by found %s, ignore", costComponent.getClass().getCanonicalName()));
      return;
    }
    
    final BprLinkTravelTimeCost bprLinkTravelTimeCost = (BprLinkTravelTimeCost) costComponent;
    for (final Entry<LinkSegment, Pair<Double, Double>> entry : bprParametersPerLinkSegment.entrySet()) {
      final Pair<Double, Double> alphaBeta = entry.getValue();
      final MacroscopicLinkSegment macroscopicLinkSegment = (MacroscopicLinkSegment) entry.getKey();
      bprLinkTravelTimeCost.setParameters(macroscopicLinkSegment, mode, alphaBeta.first(), alphaBeta.second());
    }
  }
  
  /**
   * Constructor. Most barebones constructor, requires user to change underlying settings regarding configuration of network, zoning etc. before continuing.
   *
   * @param networkFileLocation network file location
   * @param nodeCoordinateFileLocation optional provision of node coordinates (may be null)
   * @param demandFileLocation demand file location optional provision of demand file location (may be null)
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String nodeCoordinateFileLocation, final String demandFileLocation)
      throws PlanItException {
    super();
    
    this.networkSettings = new TntpNetworkReaderSettings();
    this.networkSettings.setNetworkFile(networkFileLocation);
    this.networkSettings.setNodeCoordinateFile(nodeCoordinateFileLocation);
    
    this.zoningReaderSettings = new TntpZoningReaderSettings();    
    this.zoningReaderSettings.setNetworkFileLocation(networkFileLocation);
    
    this.demandsReaderSettings = new TntpDemandsReaderSettings();
    this.demandsReaderSettings.setDemandFileLocation(demandFileLocation);
  }  

  /**
   * Whenever a project component is created this method will be invoked
   *
   * @param event event containing the created (and empty) project component
   * @throws PlanItException thrown if there is an error
   */
  @Override
  public void onPlanitComponentEvent(final PlanitComponentEvent event) throws PlanItException {
    // registered for create notifications
    if (event.getType().equals(PopulateNetworkEvent.EVENT_TYPE)) {
      populateMacroscopicNetwork(((PopulateNetworkEvent)event).getNetworkToPopulate());
    }else if(event.getType().equals(PopulateZoningEvent.EVENT_TYPE)){
      PopulateZoningEvent zoningEvent = ((PopulateZoningEvent) event);
      populateZoning(zoningEvent.getZoningToPopulate(), zoningEvent.getParentNetwork());
    }else if(event.getType().equals(PopulateDemandsEvent.EVENT_TYPE)){
      PopulateDemandsEvent demandsEvent = ((PopulateDemandsEvent) event);
      populateDemands(demandsEvent.getDemandsToPopulate(), demandsEvent.getParentZoning(), demandsEvent.getParentNetwork());
    }else if(event.getType().equals(PopulatePhysicalCostEvent.EVENT_TYPE)){
      PopulatePhysicalCostEvent physicalCostEvent = ((PopulatePhysicalCostEvent) event);
      populatePhysicalCost(physicalCostEvent.getPhysicalCostToPopulate(), physicalCostEvent.getParentNetwork());
    } else {      
      /* generic case */
      LOGGER.fine("Event component " + event.getClass().getCanonicalName() + " ignored by TNTP InputBuilder");
    }
  }

  public TntpDemandsReaderSettings getDemandsReaderSettings() {
    return demandsReaderSettings;
  }
  
  public TntpNetworkReaderSettings getNetworkReaderSettings() {
    return networkSettings;
  }
  
  public TntpZoningReaderSettings getZoningReaderSettings() {
    return zoningReaderSettings;
  }    

}
