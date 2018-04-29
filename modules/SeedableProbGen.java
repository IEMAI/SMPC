
package ext.sim.modules;
 
/**
 * Interface for problem generators that can specify the seed for pseudorandom number generation in both 
 * the generation of the problem as well as on algorithms that are run on the generated problems.  
 * @author Steven
 *
 */
public interface SeedableProbGen {
     
    /**
     * Gets the effective problem seed. This is the seed that is being used for generating the current
     * problem.
     * @return The effective problem seed.
     */
    public abstract long getEffProbSeed();
}