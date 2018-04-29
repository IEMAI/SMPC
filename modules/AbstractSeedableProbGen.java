package ext.sim.modules;

import java.util.Random;

import bgu.dcr.az.api.ano.Variable;
import bgu.dcr.az.api.prob.Problem;
import bgu.dcr.az.exen.pgen.AbstractProblemGenerator;

/**
 * Abstract base class for problem generators that can specify the seed for pseudorandom number generation in both the 
 * generation of the problem as well as on algorithms that are run on the generated problems.  
 * @author Steven
 *
 */
public abstract class AbstractSeedableProbGen extends AbstractProblemGenerator implements SeedableProbGen {
	
	/**
	 * The seed to be used for algorithms solving this problem.  Values are restricted to <code>int</code> 
	 * values (not <code>long</code> values) and cannot be one of the following two special seed values:
	 * <ol>
	 * 	<li><code>-1</code>  Indicates that the algorithm seed should be based on the problem's pseudorandom
	 * number generator; this is guaranteed to be the the same for every execution in an experiment and will
	 * usually be the same between experiments; however, it may not be consistent between different versions of
	 * the Agent Zero framework. 
	 *  <li><code>-2</code>  Indicates that the algorithm seed should not be used, so the algorithm
	 *  pseudorandom number generator is instantiated with a <code>int</code> seed generated from an unseeded 
	 *  pseudorandom number generator.
	 * </ol>
	 * In both cases the effective seed may be set to a special value which can be replicated by using 
	 * <code>algSeedType</code> of LITERAL.
	 * Note that individual agents may use different seeds in their algorithm pseudorandom number generators,
	 * but these agent-specific seeds used must be deterministically based on the algorithm seed in order
	 * to guarantee reproducibility.  
	 */
	@Variable(name="alg-seed", description="Seed for algorithm randomization", defaultValue="-1")
	int algSeed = -1;

	/**
	 * How special values in the algSeed parameter should be treated.
	 */
	@Variable(name="alg-seed-type", description="How the alg-seed parameter should be treated [INTERPRETED, LITERAL]", defaultValue="INTERPRETED")
	String algSeedType = "INTERPRETED";
	@Variable(name="prob-seed-type", description="How the seed parameter should be treated [INTERPRETED, LITERAL]", defaultValue="INTERPRETED")
	String probSeedType = "INTERPRETED";
	
	/**
	 * The seed to be used for problem generation.  Values are restricted to <code>int</code> 
	 * values (not <code>long</code> values).  The special value of -1 indicates that the seed should be based 
	 * on the problem's pseudorandom number generator as passed in; this is guaranteed to be the the same for 
	 * every execution in an experiment and will usually be the same between experiments; however, it may not be 
	 * consistent between different versions of the Agent Zero framework. 
	 * Statistics will record the effective seed used to generate the problem (i.e., <code>seed</code> if 
	 * <code>seed != -1</code>, and the generated seed if <code>seed == -1</code>), to facilitate replication.
	 * Effective seeds of <code>-1</code> are included and can be replicated when <code>seedType</code> is set
	 * to LITERAL. 
	 */
	@Variable(name="prob-seed", description="Seed for problem randomization.  Also affects algorithm randomization if alg-seed==-1.", defaultValue="-1")
	int probSeed = -1;
	/**
	 * The effective problem seed used for pseudorandom number generation of the problem.
	 */
	private long effProbSeed;

	@Variable(name="DEBUG", description="Debug flag", defaultValue="false")
	boolean DEBUG = false;

	@Override
	public final void generate(Problem prob, Random rand) {
		// initialize the problem seed
		DEBUG("Processing alg seed=" + algSeed);
		switch (probSeed) {
		case -1:
			effProbSeed = rand.nextInt();
			break;
		default:
			effProbSeed = probSeed;
		}
		System.err.println("Prob seed=" + probSeed + " with effSeed=" + effProbSeed);
		rand.setSeed(effProbSeed);
		// initialize the algorithm seed
		DEBUG("Processing alg seed=" + algSeed);
		int effAlgSeed = algSeed;	// the effective algorithm seed to be used for this problem
		// we must make sure that a value is extracted from rand no matter what, to ensure reproducibility
		int randAlgSeedVal = rand.nextInt();
		if (algSeedType.equals("INTERPRETED")) {
			switch (algSeed) {
				case -1:
					effAlgSeed = randAlgSeedVal;
					break;
				case -2: {
					System.err.println("Processing alg seed=" + algSeed);
					Random random = new Random();
					effAlgSeed = random.nextInt();
					break;
				}
			}
		}
		prob.getMetadata().put("alg-seed", (long) effAlgSeed);
		System.err.println("algSeed=" + algSeed + " with effAlgSeed=" + effAlgSeed);
		DEBUG("The effective alg seed is " + effAlgSeed + " and the alg seed is " + algSeed);
		
		// now call the implementation to actually generate the problem
		__generate(prob, rand);
	}
	
	/**
	 * The implementation for problem generation.
	 * @param prob The problem object.
	 * @param rand The pseudorandom number generator to be used for any randomization.  <code>rand.setSeed()</code>
	 * should not be called on this object!
	 */
	protected abstract void __generate(Problem prob, Random rand);

	@Override
	public final long getEffProbSeed() {
		return effProbSeed;
	}
	
	/**
	 * Prints debug statements when the <code>DEBUG</code> flag is set.
	 * @param str The debug statement to be printed.
	 */
	protected void DEBUG(String str) {
		if (DEBUG) {
			System.out.println(str);
		}
	}

}
