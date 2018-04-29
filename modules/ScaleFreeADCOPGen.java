package ext.sim.modules;


import bgu.dcr.az.api.Agt0DSL;

import bgu.dcr.az.api.ano.Register;

import bgu.dcr.az.api.ano.Variable;

import bgu.dcr.az.api.ds.ImmutableSet;

import bgu.dcr.az.api.exp.InvalidValueException;

import bgu.dcr.az.api.prob.Problem;

import bgu.dcr.az.api.prob.ProblemType;


import java.util.ArrayList;

import java.util.Collections;

import java.util.Random;

import bgu.dcr.az.exen.pgen.AbstractProblemGenerator;


/**

 * Changed with Steven's code modification ["while (!v.isEmpty())"] 03/10/14

 * Scale free ADCOP

 * @author alongrub

 *

 * Used the Barabasi-Albert model

 * Needs a large network to get the scale free effect

 */


@Register(name = "adcop-scale-free")

public class ScaleFreeADCOPGen extends AbstractProblemGenerator {

@Variable(name = "n", description = "number of variables", defaultValue="10")

    int n = 10;

    @Variable(name = "d", description = "domain size", defaultValue="2")

    int d = 2;

    @Variable(name = "max-cost", description = "maximal cost of constraint", defaultValue="10")

    int maxCost = 10;

    @Variable(name = "core-size", description = "The number of vertices in the core of the graph", defaultValue="5")

    int core = 5; 	

    @Variable(name = "growth", description = "The number of vertices any new edge will be connected to (smaller than the core-size)", defaultValue="2")

    int growth = 2;

    

    public void generate(Problem p, Random rand) {

    p.initialize(ProblemType.ADCOP, n, new ImmutableSet<Integer>(Agt0DSL.range(0, d - 1)));

    double[] degree = new double[n];

    double totalDeg = 0;

    if ((core < 2) || (core >= n) || (growth>core))

    throw new InvalidValueException("BA model - bad parameters: n="+n+", core="+core+" and growth="+growth);

    ArrayList<Integer> v = new ArrayList<Integer>();

    ArrayList<Integer> coreSet = new ArrayList<Integer>();

for (int i=0; i<n; i++)

v.add(i);

for (int i=0; i<core; i++){

Integer vcs = v.get(rand.nextInt(v.size()));

coreSet.add(vcs);

v.remove(vcs);

}

    	

/* first make sure that each one of the nodes is connected with at least two edges by making it a ring */

Collections.shuffle(coreSet,rand);

for (int i=0; i<core; i++){

Integer v1 = coreSet.get(i);

Integer v2 = coreSet.get((i+1) % coreSet.size());

buildConstraint(v1, v2, p, rand);

degree[v1]++;

degree[v2]++;

}

/* now we add more edges to the ring */

for (int i=0; i<core; i++){

Integer v1 = coreSet.get(0);

coreSet.remove(v1);

for (int j=0; j<growth-degree[v1]; j++){

Integer v2 = coreSet.get(rand.nextInt(coreSet.size()));

/* not very elegant or efficient but should work (will work better as core becomes larger than growth) */

if (p.isConstrained(v1, v2)){

j--;

continue;

}

buildConstraint(v1, v2, p, rand);

degree[v1]++;

degree[v2]++;

totalDeg+=2;

}

coreSet.add(v1);

}

/* finally we add the remaining vertices to the network */

Collections.shuffle(v,rand);

while (!v.isEmpty()) {

Integer v1 = v.get(0);

for (int j=0; j<growth; j++){

Integer v2 = coreSet.get(rand.nextInt(coreSet.size()));

if (!p.isConstrained(v1, v2) && rand.nextDouble() < degree[v2]/totalDeg){

buildConstraint(v1, v2, p, rand);

degree[v1]++;

degree[v2]++;

totalDeg+=2;

} else {

j--;

continue;

}

}

coreSet.add(v1);

v.remove(v1);

}

for (int i = 0; i < p.getNumberOfVariables(); i++) {

for (int j = i+1; j < p.getNumberOfVariables(); j++) {

if (p.isConstrained(i, j)) {

splitCost(i, j, p, rand);

}

}

}

    }

    

    protected void buildConstraint(int i, int j, Problem p, Random rand) {

        for (int vi = 0; vi < p.getDomain().size(); vi++) {

            for (int vj = 0; vj < p.getDomain().size(); vj++) {

                final int cost1 = rand.nextInt(maxCost);

                final int cost2 = rand.nextInt(maxCost);

                p.setConstraintCost(i, vi, j, vj, cost1);

                p.setConstraintCost(j, vj, i, vi, cost2);

                

            }

        }

    }

    

    

//Split each original costs randomly between the asymmetric costs (in asymmetric problems)

protected void splitCost(int var1, int var2, Problem p, Random rand) {

int originalCost, costVal1, costVal2;

for (int val1 = 0; val1 < p.getDomainSize(var1); val1++) {

for (int val2 = 0; val2 < p.getDomainSize(var2); val2++) {

originalCost = p.getConstraintCost(var1, val1, var2, val2);

if (originalCost > 0) 

costVal1 = (rand.nextInt(originalCost) + 1);

else 

costVal1 = 0;

costVal2 = originalCost - costVal1;

p.setConstraintCost(var1, val1, var2, val2, costVal1);

p.setConstraintCost(var2, val2, var1, val1, costVal2);

}

}

}

    

    

    @Override

    public String toString() {

        StringBuilder sb = new StringBuilder();

        sb.append("Generating : ").append("n = ").append(n).append("\nd = ").append(d).append("\nmaxCost = ").append(maxCost);

        return sb.toString();

    }

}