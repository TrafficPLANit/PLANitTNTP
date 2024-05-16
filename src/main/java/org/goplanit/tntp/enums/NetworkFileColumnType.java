package org.goplanit.tntp.enums;

/**
 * Enumeration giving roles of columns in TNTP network input file
 * 
 * @author gman6028
 *
 */
public enum NetworkFileColumnType {
	
	UPSTREAM_NODE_ID,
	DOWNSTREAM_NODE_ID,
	CAPACITY_PER_LANE, //TODO: should be called capacity, only when number of lanes is activated we interpret as capacity per lane otherwise not
	LENGTH,
	MAXIMUM_SPEED,
	LINK_TYPE,
	B,
	POWER,
	TOLL,
	FREE_FLOW_TRAVEL_TIME,
	CRITICAL_SPEED,
	NUMBER_OF_LANES;

}