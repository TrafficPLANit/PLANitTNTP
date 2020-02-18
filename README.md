# PLANitTNTP

PLANit Interface for TNTP format files

The input files for this format can be found at https://github.com/bstabler/TransportationNetworks

February 2020: So far we have got the standard files for Philadelphia and Chicago-Sketch to run.  The test case for Philadelphia runs using this code but we have not validated the outputs.

# Data Files 

Comment lines in files begin with the tilda symbol '~' and are ignored.

Data files use columns which are separated by one or more spaces.  The number of spaces used to separate columns varies from row to row, so the columns in a file do not always line up in a manner which is neat to humans.  

Network File (required, name ends _net.tntp)

Metadata Information at top of file:

<NUMBER OF ZONES>										
<NUMBER OF NODES>										
<FIRST THRU NODE>										
<NUMBER OF LINKS>										
<END OF METADATA>			

Row information, columns for each link (values required unless stated):-

* Upstream Node Number ('tail node')
* Downstream Node Number ('head node')
* Capacity (vehicles/hr)
* Length - units may be given in the header or the project notes, assume kilometres otherwise
* Free Flow Travel Time - Not currently used
* B - alpha value used in BPR function
* Power - beta value used in BPR function
* Speed Limit - units may be given in the header or the project notes, assume kilometres otherwise.  ChicagoSketch uses default of 25 mph instead of this value for Type 3 links
* Toll - Not currently used
* Link Type

Each row defines a link which is one-way and single-lane.  In many cases one line defines a link in one direction and a later line defines another link between the same two nodes but in the other direction, but TNTP does not use a directionality property.

Trips File (required, name ends _trips.tntp)

This is the equivalent of the Demands file used in other input formats.

Metadata Information at top of file:

<NUMBER OF ZONES>  (Should match value in Network File)
<TOTAL OD FLOW>
<END OF METADATA>

For every origin node number, contains a list of destinations in format <destination node number>: <demand>;

For example, the start of the file for Chicago Sketch (covering the demands from node 1 to the first 20 nodes) is as follows:- 

Origin         1
    1 :     273.18;      2 :     347.31;      3 :     390.81;      4 :     204.51;      5 :     373.73;  
    6 :     199.70;      7 :     119.69;      8 :      96.23;      9 :      39.53;     10 :      75.72;  
   11 :      24.02;     12 :      27.71;     13 :      32.98;     14 :     124.31;     15 :      72.66;  
   16 :     124.83;     17 :     311.17;     18 :     157.00;     19 :      32.43;     20 :      13.38;  
   
Sometimes a destination-demand pair is not defined for an origin, in this case the demand is assumed to be zero.

The default units are trips/hr, but Philadelphia uses trips/day.

Node Locations File (optional, name ends _node.tntp)

After the first line which is a header, all lines have three columns:-

* Node number ('node')
* X coordinate ('X')
* Y coordinate ('Y')

If this file is present, it is read and used to create coordinate pairs for each node.  But link lengths are not derived from these coordinates, the lengths given in the network file are used instead.

Standard Results File (optional, name ends _flow.tntp);

After the first line which is a header, all lines have four columns:-

* Upstream Node External Id ("From')
* Downstream Node External Id ('To')
* Flow ('Volume') - vehicles/hr
* Cost ('Cost') - hr

At present only Chicago Sketch has a file of this type.  It is used in the PLANit unit testing.

# Single Mode and Time Period

TNTP data does not use modes or time periods.  One dummy mode and one time dummy period are created.  These are internally used where required. 

# Running the TntpMain Class

The Tntp class, which extends InputBuilderListener, contains the notify(), populatePhysicalNetwork(), populateZoning() and populateDemands() methods for TNTP format.  This called from TntpProject, which extends CustomPlanItProject.

The TntpMain class contains a main() method and can be run as a standalone program, with the locations of the input files used a run-time arguments.  But it also contains a public method execute() which calls the TntpProject, populates the components and executes the traffic assignment.
The TntpMain class can be instantiated and its execute() method run from another class.  The unit test TNTPTest does this.

if a standard results file has been specifed, TntpMain.execute() returns a Map with the standard flow and cost for the upstream and downstream nodes for each link.  TntpProject has a method createStandardResultsFile() which returns this Map.  
If the null is given for the parameter value of the standardResultsFileLocation argument to TntpMain.execute(), meaning there is no standard results file, the execute() method returns a null.  This is convenient for unit testing.  

# Test Cases

February 2020:  We use ChicagoSketch for the unit tests.  We compare our results against the ChicagoSketch_flow.tntp file.  This file was created on 13 March 2016 (https://github.com/bstabler/TransportationNetworks/blob/master/Chicago-Sketch/ChicagoSketch_flow.tntp)

We have been able to replicate the link cost modelling for Type 3 links in the Chicago Sketch data files.  These use a BPR cost function with a standard maximum speed of 25 miles per hour.  We have not been able to determine how the costs for links of types 1 and 2 are calculated.