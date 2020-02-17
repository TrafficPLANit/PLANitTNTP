# PLANitTNTP

PLANit Interface for TNTP format files

The input files for this format can be found at https://github.com/bstabler/TransportationNetworks

February 2020: So far we have got the standard files for Philadelphia and Chicago-Sketch to run.  The test case for Philadelphia runs using this code but we have not validated the outputs.

# Test Cases

February 2020:  We have been able to replicate the link cost modelling for Type 3 links.  These use a BPR cost function with a standard maximum speed of 25 miles per hour.  We have not been able to determine how the costs for links of types 1 and 2 are calculated.