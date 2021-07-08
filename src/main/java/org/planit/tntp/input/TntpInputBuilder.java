package org.planit.tntp.input;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.djutils.event.EventInterface;
import org.planit.assignment.TrafficAssignmentComponentFactory;
import org.planit.cost.physical.BPRLinkTravelTimeCost;
import org.planit.cost.physical.AbstractPhysicalCost;
import org.planit.demands.Demands;
import org.planit.input.InputBuilderListener;
import org.planit.network.MacroscopicNetwork;
import org.planit.network.TransportLayerNetwork;
import org.planit.tntp.converter.demands.TntpDemandsReader;
import org.planit.tntp.converter.network.TntpNetworkReader;
import org.planit.tntp.converter.zoning.TntpZoningReader;
import org.planit.tntp.enums.CapacityPeriod;
import org.planit.tntp.enums.LengthUnits;
import org.planit.tntp.enums.NetworkFileColumnType;
import org.planit.tntp.enums.SpeedUnits;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.LoggingUtils;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.network.layer.physical.LinkSegment;
import org.planit.zoning.Zoning;

/**
 * Class which reads input from TNTP files
 *
 * @author gman6028, markr
 *
 */
public class TntpInputBuilder extends InputBuilderListener {

  /** generated UID */
  private static final long serialVersionUID = 1L;

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(TntpInputBuilder.class.getCanonicalName());

  
  /**
   * Creates the physical network object from the data in the input file
   *
   * @param network the network object to be populated from the input data
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateInfrastructureNetwork( final TransportLayerNetwork<?,?> network) throws PlanItException {
    
    if (!(network instanceof MacroscopicNetwork)) {
      throw new PlanItException("TNTP reader currently only supports writing macroscopic networks");
    }
    final MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork) network;
    
    /* prep */
    getTntpNetworkReader().getSettings().setNetworkToPopulate(macroscopicNetwork);

    /* parse */
    getTntpNetworkReader().read();
  }

  /**
   * Populates the Demands object from the input file
   *
   * @param demands the Demands object to be populated from the input data
   * @param parameter1 Zoning object previously defined
   * @param parameter2 Network to use
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateDemands( final Demands demands, final Object parameter1, Object parameter2) throws PlanItException {
    PlanItException.throwIf(!(parameter1 instanceof Zoning),"Parameter 1 of call to populateDemands() is not of class Zoning");
    PlanItException.throwIf(!(parameter2 instanceof MacroscopicNetwork),"Parameter 2 of call to populateDemands() is not of class MacroscopicNetwork");
    
    /* prep */
    getTntpDemandsReader().getSettings().setDemandsToPopulate(demands);
    getTntpDemandsReader().getSettings().setReferenceNetwork((MacroscopicNetwork)parameter2);
    getTntpDemandsReader().getSettings().setReferenceZoning((Zoning)parameter1);
    
    getTntpDemandsReader().getSettings().setMapToIndexModeBySourceIds(getTntpNetworkReader().getAllModesBySourceId());
    getTntpDemandsReader().getSettings().setMapToIndexZoneBySourceIds(getTntpZoningReader().getAllZonesBySourceId());
    
    /* parse */
    getTntpDemandsReader().read();
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
    
    /* prep */
    getTntpZoningReader().getSettings().setZoningToPopulate(zoning);
    getTntpZoningReader().getSettings().setReferenceNetwork(macroscopicNetwork);
    
    getTntpZoningReader().getSettings().setNodesBySourceId(getTntpNetworkReader().getAllNodesBySourceId());
    getTntpZoningReader().getSettings().setLinkSegmentsBySourceId(getTntpNetworkReader().getAllLinkSegmentsBySourceId());
    
    /* parse */
    getTntpZoningReader().read();
  }

  /**
   * Populate the BPR parameters
   *
   * @param costComponent the BPRLinkTravelTimeCost to be populated
   * @throws PlanItException thrown if there is an error
   */
  protected void populatePhysicalCost( final AbstractPhysicalCost costComponent) throws PlanItException {
    LOGGER.info(LoggingUtils.getClassNameWithBrackets(this)+"populating BPR link costs");
    
    Mode mode = getTntpZoningReader().getSettings().getReferenceNetwork().getTransportLayers().getFirst().getFirstSupportedMode();
    Map<LinkSegment, Pair<Double, Double>> bprParametersForLinkSegmentAndMode = getTntpNetworkReader().getParsedBprParameters();
    if (bprParametersForLinkSegmentAndMode != null) {
      final BPRLinkTravelTimeCost bprLinkTravelTimeCost = (BPRLinkTravelTimeCost) costComponent;
      for (final Entry<LinkSegment, Pair<Double, Double>> entry : bprParametersForLinkSegmentAndMode.entrySet()) {
        final Pair<Double, Double> alphaBeta = entry.getValue();
        final MacroscopicLinkSegment macroscopicLinkSegment = (MacroscopicLinkSegment) entry.getKey();
        bprLinkTravelTimeCost.setParameters(macroscopicLinkSegment, mode, alphaBeta.first(), alphaBeta.second());
      }
    }
  }
  
  /** get TNTP network reader instance
   * 
   * @return TNTP network reader
   */
  protected TntpNetworkReader getTntpNetworkReader() {
    return (TntpNetworkReader)getNetworkReader();
  }
  
  /** get TNTP zoning reader instance
   * 
   * @return TNTP zoning reader
   */
  protected TntpZoningReader getTntpZoningReader() {
    return (TntpZoningReader) getZoningReader();
  }  
  
  /** get TNTP demands reader instance
   * 
   * @return TNTP demands reader
   */
  protected TntpDemandsReader getTntpDemandsReader() {
    return (TntpDemandsReader) getDemandsReader();
  }  

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param capacityPeriod time period for link capacity
   * @param defaultMaximumSpeed default maximum speed along a link
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
      final Map<NetworkFileColumnType, Integer> networkFileColumns, final SpeedUnits speedUnits,
      final LengthUnits lengthUnits,
      final CapacityPeriod capacityPeriod, final double defaultMaximumSpeed)
      throws PlanItException {
    this(networkFileLocation, demandFileLocation, null, networkFileColumns, speedUnits, lengthUnits,
        capacityPeriod, defaultMaximumSpeed);
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param defaultMaximumSpeed default maximum speed along a link
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
      final Map<NetworkFileColumnType, Integer> networkFileColumns, final SpeedUnits speedUnits,
      final LengthUnits lengthUnits,
      final double defaultMaximumSpeed)
      throws PlanItException {
    this(networkFileLocation, demandFileLocation, null, networkFileColumns, speedUnits, lengthUnits, null,
        defaultMaximumSpeed);
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param nodeCoordinateFileLocation node coordinate file location
   * @param standardResultsFileLocation the location of the standard results file
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param defaultMaximumSpeed default maximum speed along a link
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
      final String nodeCoordinateFileLocation, final String standardResultsFileLocation,
      final Map<NetworkFileColumnType, Integer> networkFileColumns,
      final SpeedUnits speedUnits,
      final LengthUnits lengthUnits, final double defaultMaximumSpeed) throws PlanItException {
    this(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, networkFileColumns, speedUnits,
        lengthUnits, null, defaultMaximumSpeed);
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param nodeCoordinateFileLocation node coordinate file location
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param capacityPeriod time period for link capacity
   * @param defaultMaximumSpeed default maximum speed along links
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
      final String nodeCoordinateFileLocation, final Map<NetworkFileColumnType, Integer> networkFileColumns,
      final SpeedUnits speedUnits, final LengthUnits lengthUnits, final CapacityPeriod capacityPeriod,
      final double defaultMaximumSpeed) throws PlanItException {

    super();

    /* network reader */
    setNetworkReader(new TntpNetworkReader(networkFileLocation, nodeCoordinateFileLocation));
    getTntpNetworkReader().getSettings().setCapacityPeriod((capacityPeriod == null) ? CapacityPeriod.HOUR : capacityPeriod);
    getTntpNetworkReader().getSettings().setLengthUnits(lengthUnits);
    getTntpNetworkReader().getSettings().setNetworkFileColumns(networkFileColumns);
    getTntpNetworkReader().getSettings().setSpeedUnits(speedUnits);
    getTntpNetworkReader().getSettings().setDefaultMaximumSpeed(defaultMaximumSpeed);
    
    /* zoning reader */
    setZoningReader(new TntpZoningReader(networkFileLocation));
    
    /* demands reader */
    setDemandsReader(new TntpDemandsReader(demandFileLocation));
  }

  /**
   * Whenever a project component is created this method will be invoked
   *
   * @param event event containing the created (and empty) project component
   * @throws RemoteException thrown if there is an error
   */
  @Override
  public void notify(final EventInterface event) throws RemoteException {
    // registered for create notifications
    if (event.getType() == TrafficAssignmentComponentFactory.TRAFFICCOMPONENT_CREATE) {
      final Object[] content = (Object[]) event.getContent();
      final Object projectComponent = content[0];
      // the content consists of the actual traffic assignment component and an array of object
      // parameters (second parameter)
      final Object[] parameters = (Object[]) content[1];
      try {
        if (projectComponent instanceof TransportLayerNetwork) {
          populateInfrastructureNetwork((TransportLayerNetwork<?,?>) projectComponent);
        } else if (projectComponent instanceof Zoning) {
          populateZoning((Zoning) projectComponent, parameters[0]);
        } else if (projectComponent instanceof Demands) {
          populateDemands((Demands) projectComponent, parameters[0], parameters[1]);
        } else if (projectComponent instanceof AbstractPhysicalCost) {
          populatePhysicalCost((AbstractPhysicalCost) projectComponent);
        } else {
          LOGGER.fine(LoggingUtils.getClassNameWithBrackets(this)+"event component is " + projectComponent.getClass().getCanonicalName()+ " which is not handled by PlanItInputBuilder.");
        }
      } catch (final PlanItException e) {
        LOGGER.severe(e.getMessage());
        throw new RemoteException("error rethrown as RemoteException in notify of TNTP",e);
      }
    }
  }

}
