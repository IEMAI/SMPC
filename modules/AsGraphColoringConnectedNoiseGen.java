package ext.sim.modules;

import java.util.Random;

import bgu.dcr.az.api.Agt0DSL;
import bgu.dcr.az.api.ano.Register;
import bgu.dcr.az.api.ano.Variable;
import bgu.dcr.az.api.ds.ImmutableSet;
import bgu.dcr.az.api.prob.Problem;
import bgu.dcr.az.api.prob.ProblemType;

/* //**********
//NOTE: this problem generator creates a connected ASYMMETRIC Graph Coloring problem, with NOISE addition
//********** */

@Register(name="as-graphcoloring-connected-noise")
public class AsGraphColoringConnectedNoiseGen extends GraphColoringConnectedNoiseGen {

public void generate(Problem p, Random rand) {
p.initialize(ProblemType.ADCOP, n, new ImmutableSet<Integer>(Agt0DSL.range(0, d - 1)));
addConstraints(p, rand);
//addConnectivity(p, rand);
for (int i = 0; i < p.getNumberOfVariables(); i++) {
for (int j = i+1; j < p.getNumberOfVariables(); j++) {
if (p.isConstrained(i, j)) {
turnDCOPToGraphColoring(i, j, p, rand, (breakCost),true);
}
}
}
}

}