package org.planit.tntp.converter.zoning;

import java.util.Map;

import org.planit.converter.ConverterReaderSettings;
import org.planit.network.TransportLayerNetwork;
import org.planit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.network.layer.physical.Node;
import org.planit.zoning.Zoning;

/**
 * Settings for the Tntp zoning reader
 * 
 * @author markr
 *
 */
public class TntpZoningReaderSettings implements ConverterReaderSettings {
  
  /** the network this zoning relates to */
  protected TransportLayerNetwork<?,?> referenceNetwork;  
  
  /** the zoning to populate */
  protected Zoning zoningToPopulate;
  
  /**
   * mapping of nodes by XML id for quick lookups
   */
  protected Map<String, Node> nodesBySourceId = null; 

  /**
   * mapping of link segments by XML id for quick lookups
   */  
  protected Map<String, MacroscopicLinkSegment> linkSegmentsBySourceId = null;   
    
  public Zoning getZoningToPopulate() {
    return zoningToPopulate;
  }

  public void setZoningToPopulate(Zoning zoningToPopulate) {
    this.zoningToPopulate = zoningToPopulate;
  }

  /** Reference network for the zoning
   * 
   * @return reference network
   */
  public TransportLayerNetwork<?, ?> getReferenceNetwork() {
    return referenceNetwork;
  }

  /** Reference network for the zoning
   * 
   * @param referenceNetwork to use
   */
  public void setReferenceNetwork(TransportLayerNetwork<?, ?> referenceNetwork) {
    this.referenceNetwork = referenceNetwork;
  }  

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    nodesBySourceId = null;
    linkSegmentsBySourceId = null;  
  }
  
  // GETTERS/SETTERS
  
  /** Collect node by source id 
   * 
   * @param nodesBySourceId to collect
   * @return node found
   */
  public Node getNodeBySourceId(String nodeSourceId) {
    return this.nodesBySourceId.get(nodeSourceId);
  }  
  
  /** Provide the map containing the TNTP id to node mapping. 
   * 
   * @param nodesBySourceId to use
   */
  public void setNodesBySourceId(final Map<String, Node> nodesBySourceId) {
    this.nodesBySourceId = nodesBySourceId;
  }

  /** Provide the map containing the TNTP id to link segment mapping. 
   * 
   * @param linkSegmentsBySourceId to use
   */  
  public void setLinkSegmentsBySourceId(final Map<String, MacroscopicLinkSegment> linkSegmentsBySourceId) {
    this.linkSegmentsBySourceId = linkSegmentsBySourceId;
  }   
   
}
