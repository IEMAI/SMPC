/* 
 * The MIT License
 *
 * Copyright 2016 Benny Lutati.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ext.sim.modules;

import bgu.dcr.az.api.Agt0DSL;
import bgu.dcr.az.api.prob.ProblemType;
import bgu.dcr.az.api.ano.Register;
import bgu.dcr.az.api.ano.Variable;
import bgu.dcr.az.api.ds.ImmutableSet;
import bgu.dcr.az.api.prob.Problem;
import java.util.Random;
import bgu.dcr.az.exen.pgen.UnstructuredDCOPGen;


@Register(name = "k_regular_graphs_adcop")
public class KRegularGraphsADCOPGen extends UnstructuredDCOPGen {

    @Variable(name = "n", description = "number of variables", defaultValue = "2")
    int n = 2;
    @Variable(name = "d", description = "domain size", defaultValue = "2")
    int d = 2;
    @Variable(name = "max-cost", description = "maximal cost of constraint", defaultValue = "10")
    int maxCost = 10;

    @Variable(name = "p2", description = "probablity of constraint between two values", defaultValue = "0.5")
    float p2 = 0.5f;
    @Variable(name = "r", description = "number of neighbors for each agent", defaultValue = "5")
    int r = 5;

    @Override
    public void generate(Problem p, Random rand) {
        p.initialize(ProblemType.ADCOP, n, new ImmutableSet<Integer>(Agt0DSL.range(0, d - 1)));
        
        for (int k = 0;  k < p.getNumberOfVariables()/(r+1); k++) {
              for (int i = k * (r+1);  i< (r+1)*(k+1); i++) {
                for (int j = i  ;  j< (r+1)*(k+1); j++) {
                        buildConstraint(i, j, p, false, rand); // ADCOP
                     // buildConstraint(i, j, p, true, rand);   // DCOP
                }
            }
        }

    }
    @Override
        protected void buildConstraint(int i, int j, Problem p, boolean sym, Random rand) {
        
            for (int vi = 0; vi < p.getDomain().size(); vi++) {
            for (int vj = 0; vj < p.getDomain().size(); vj++) {
                if (i == j) {
                    continue;
                }
                
                int cost1 = rand.nextInt(maxCost+ 1) ;
                int cost2 = rand.nextInt(maxCost+ 1) ;
                
                if (rand.nextDouble() < p2) {
                    cost1 = 0; 
                }
                if (rand.nextDouble() < p2) {
                    cost2 = 0; 
                }
                                   
                    p.setConstraintCost(i, vi, j, vj, cost1);
                    if (sym) {
                        p.setConstraintCost(j, vj, i, vi, cost1);
                    } else {
                        p.setConstraintCost(j, vj, i, vi, cost2);
                    }
                
                
            }
        }
    }
}
