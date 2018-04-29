package ext.sim.modules;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import bgu.dcr.az.api.ano.Register;
import bgu.dcr.az.api.ano.Variable;
import bgu.dcr.az.api.prob.Problem;
import bgu.dcr.az.exen.pgen.AbstractProblemGenerator;

//**********
//NOTE: this problem generator creates an abstract CONNECTED (or unstructured) DCOP problem, with no costs between
//variables (only constraints). We will use it to create a connected (or unstructured) DCOP/ADCOP problem later.
//**********

@Register(name="general-dcop")
public abstract class GeneralDCOPGen extends AbstractProblemGenerator {

@Variable(name = "n", description = "number of variables", defaultValue = "10")
protected int n = 10;
@Variable(name = "d", description = "domain size", defaultValue = "5")
protected int d = 5;
@Variable(name = "p1", description = "p(constraint) between variables", defaultValue = "0.6")
protected double p1 = 0.6;
@Variable(name = "p2", description = "the probability for a positive cost between values values of 2 variables", defaultValue="0.6")
protected double p2 = 0.6;
@Variable(name = "max-cost", description = "cost of breaking a constraint", defaultValue = "10")
protected int maxCost = 10;


//Build a new constraint between two variables
protected void addConstraints(Problem p, Random rand) {
for (int i = 0; i < p.getNumberOfVariables(); i++) {
for (int j = 0; j < p.getNumberOfVariables(); j++) {
if (rand.nextDouble() < p1 && i!=j) {
buildConstraint(i, j, p);
buildConstraint(j, i, p);
}
}
}
}
//Connect all variables to make the problem connected
protected void addConnectivity(Problem p, Random rand) {
Vector<Integer> connected = new Vector<Integer>();
Vector<Integer> unconnected = new Vector<Integer>();

for (int i = 0; i < p.getNumberOfVariables(); i++) {
unconnected.add(i);
}
connected.addAll(findConnections(p, 0)); //get all variables that are somehow connected to X0 (have a path that leads to it)
unconnected.removeAll(connected);	//get all variables that don't have a path from X0

//now we connect all the unconnected variable, making the problem CONNECTED 
while (!unconnected.isEmpty()) { //for each of the unconnected variables
int var1 = unconnected.get(rand.nextInt(unconnected.size()));; //randomly choose one unconnected variable
int var2 = connected.get(rand.nextInt(connected.size())); //randomly choose a variable to connect to
connected.add(var1);
unconnected.removeElement(var1);
buildConstraint(Math.min(var1, var2), Math.max(var1, var2), p); //build a new constraint between them
buildConstraint(Math.max(var1, var2), Math.min(var1, var2), p); //build a new constraint between them	
}
}
//BFS algorithm for finding all variables that have a path to X0
protected Vector<Integer> findConnections(Problem p, int var){ 
Vector<Integer> myQueue = new Vector<Integer>();
Set<Integer> mySet = new HashSet<Integer>();
myQueue.add(myQueue.size(), var);
mySet.add(var);
while (!myQueue.isEmpty()) {
int currentVar = myQueue.remove(0);
for (Integer i : p.getNeighbors(currentVar)) {
if (!mySet.contains(i)) {
mySet.add(i);
myQueue.add(myQueue.size(), i);	
}
}
}
myQueue.addAll(mySet);
return myQueue;
}//the method returns all the variables that have a path leading to X0
//Initially, set all costs to 0
protected void buildConstraint(int var1, int var2, Problem p) {
for (int i = 0; i < p.getDomainSize(var1); i++) {
for (int j = i; j < p.getDomainSize(var2); j++) {
p.setConstraintCost(var1, i, var2, j, 0);
}
}
}
//Split each original costs randomly between the asymmetric costs (in asymmetric problems)
//The costs here must be NOT-MULTIPLIED by 1000!!
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
p.setConstraintCost(var1, val1, var2, val2, costVal1 * 1000);
p.setConstraintCost(var2, val2, var1, val1, costVal2 * 1000);
}
}
}
}