package ext.sim.modules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import bgu.dcr.az.api.ano.Register;
import bgu.dcr.az.api.ano.Variable;
import bgu.dcr.az.api.prob.Problem;
import bgu.dcr.az.api.prob.ProblemType;

/**
 * Problem generator for meeting scheduling DCOP problems.  Uses the "events as variables" (EAV) DCOP
 * representation (Maheswaran, et al., "Taking DCOP to the Real World: Efficient Complete Solutions for
 * Distributed Multi-Event Scheduling." AAMAS-04, 2004) where there is a DCOP variable for each meeting and
 * domain-level agents are implicitly represented by costs between meetings that share participants and
 * costs reflecting time preferences.
 * <br>
 * Meeting scheduling instances have agents (aka "resources"), meetings that the agents must participate in, and 
 * time slots when the meetings must be scheduled.  Every meeting has a duration and a preference vector of costs 
 * over time slots; this can be viewed as the sum of individual preference vectors of the participants of that 
 * meeting.  While this is a unary constraint, it is implemented as a binary constraint by summing the unary 
 * costs for every pair of meetings and splitting this on the binary cost between them.  Because this uses 
 * integer division, there are discretization effects.   For every pair of meetings there are symmetric travel 
 * times between the meetings.  
 * <br>
 * There are two ways to choose the participants to each meeting: either choosing the number of participants 
 * required for a meeting and then selecting these randomly from the agents, or choosing the number of meetings 
 * an agent is required to participate in and then selecting these randomly from the meetings.
 * <br> 
 * There are four ways to choose binary constraint costs for scheduling conflicts: counting number of conflicts,
 * counting number of agents participating in conflicting meetings, counting number of conflicting (i.e., 
 * overscheduled) agents, and counting number of agents participating in the smaller of two conflicting meetings.
 * <br>
 * Most integer values are chosen uniformly at randomly from a range of consecutive integers [min..min+range-1].  
 * These ranges can be set using two Agent Zero parameter variables, one for the minimum value and one for the 
 * range (i.e., number of possible values), which should be strictly positive.
 * 
 * @author Steven
 *
 */
@Register(name="dcop-meeting-scheduling-noise")
public class MeetingSchedulingDCOPNoiseGen extends AbstractSeedableProbGen {
	
	/**
	 * Enumeration of ways to generate participants for meetings.
	 * <list>
	 * <li><code>ATTENDANCE</code> The number of agents attending each meeting is first determined, then 
	 * the specific agents for each meeting are chosen randomly to meet the attendance requirements.
	 * <li><code>LOAD</code> The number of meetings each agent attends is first determined, then the
	 * specific meetings for each agent are chosen randomly to meet the load requirements.
	 * </list>
	 * @author Steven
	 *
	 */
	public enum ParticipantGeneration { ATTENDANCE, LOAD };
	/**
	 * Enumeration of ways to map scheduling conflicts between pairs of meetings to DCOP costs. 
	 * <list>
	 * <li><code>BINARY</code> 1 for each conflicting pair of meetings.
	 * <li><code>SUM_TOTAL</code> The total number of agents participating in at least one of the two 
	 * conflicting meetings.
	 * <li><code>SUM_CONFLICT</code> The number of agents who participate in both conflicting meetings.
	 * <li><code>MIN_TOTAL</code> The total number of agents participating in the smaller of the two
	 * conflicting meetings. 
	 * @author Steven
	 *
	 */
	public enum ConflictCost { BINARY, SUM_TOTAL, SUM_CONFLICT, MIN_TOTAL };
	
	@Variable(name="n", description="number of agents", defaultValue="10")
	protected int n = 10;
	@Variable(name="m", description="number of meetings", defaultValue="2")
	protected int m = 2;
	@Variable(name="t", description="number of time slots", defaultValue="5")
	protected int t = 5;
	@Variable(name="min-duration", description="minimum duration of a meeting", defaultValue="1")
	protected int minDuration = 1;
	@Variable(name="duration-range", description="number of possible values for duration", defaultValue="1")
	protected int durationRange = 1;
	@Variable(name="min-attendance", description="minimum number of agents in a meeting (with ATTENDANCE generation)", defaultValue="3")
	protected int minAttendance = 3;
	@Variable(name="attendance-range", description="number of possible values for required attendance (with ATTENDANCE generation)", defaultValue="2")
	protected int attendanceRange = 2;
	@Variable(name="min-travel-time", description="minimum travel time between meetings", defaultValue="1")
	protected int minTravelTime = 1;
	@Variable(name="travel-time-range", description="number of possible values for travel time", defaultValue="3")
	protected int travelTimeRange = 3;
	@Variable(name="min-load", description="minimum number of meetings per agent (with LOAD ageneration)", defaultValue="2")
	protected int minLoad = 2;
	@Variable(name="load-range", description="number of possible values for agent loads (with LOAD generation)", defaultValue="1")
	protected int loadRange = 1;
	@Variable(name="min-time-cost", description="minimum unary cost for meetings at different times", defaultValue="0")
	protected int minTimeCost = 0;
	@Variable(name="time-cost-range", description="number of possible values for unary meeting costs for times", defaultValue="1")
	protected int timeCostRange=1;
	
	/**
	 * How to generate participants for meetings:
	 * <ol><li>"ATTENDANCE": The number of participants for each meeting is randomly chosen from [minAttendance..maxAttendance]
	 *         and a random set of participants of that size is chosen.  The number of meetings a single person participates in
	 *         is uncontrolled.</li>
	 *     <li>"LOAD": The number of meetings for each agent is randomly chosen from [minLoad..maxLoad] and a
	 *         random set of meetings of that size is chosen.  The number of participants in a single meeting is uncontrolled.</li>
	 * </ol>
	 */
	@Variable(name="part-gen-type", description="Way to generate participants for meetings: either ATTENDANCE or LOAD", defaultValue="ATTENDANCE")
	protected String participantGenerationString = "ATTENDANCE";
	/**
	 * How to compute the cost of a scheduling conflict between two meetings:
	 * <ol><li>"BINARY": 1 if conflict, 0 otherwise</li>
	 *     <li>"SUM_TOTAL": The total number of unique participants in either meeting</li> 
	 *     <li>"SUM_CONFLICT": The total number of participants in both meetings</li>
	 *     <li>"MIN_TOTAL": The minimum of the number of participants in each meeting</li> 
	 * </ol>
	 */
	@Variable(name="conflict-cost-type", description="Cost of a scheduling conflict: BINARY, SUM_TOTAL, SUM_CONFLICT, or MIN_TOTAL", defaultValue="SUM_TOTAL")
	protected String conflictCostString = "SUM_TOTAL";

	public ParticipantGeneration participantGeneration;
	public ConflictCost conflictCost;
	
	
	/**
	 * Array of agent IDs to be shuffled to make random selections in the ATTENDANCE participant generation model.
	 */
	private int [] agentsForShuffle;
	/**
	 * Array of meeting IDs to be shuffled to make random selections in the LOAD participant generation model.
	 */
	private int [] meetingsForShuffle;
	
	@Override
	public void __generate(Problem prob, Random rand) {
		// first we create the meeting scheduling problem, then we will create the EAV DCOP

		// parse the participant generation string and conflict cost string so we know how 
		// to choose participants for meetings and set costs for conflicting meeting times
		participantGeneration = ParticipantGeneration.valueOf(participantGenerationString.toUpperCase());
		conflictCost = ConflictCost.valueOf(conflictCostString.toUpperCase());

		// create the array of meetings
		Meeting [] meetings = new Meeting[m];
		// symmetric array of travel times between meetings
		int [][] travelTimes = new int[m][m];

		// we create the meetings, making sure that the shortest duration meeting is at index 0, so that
		// the first variable has the largest domain.
		// this is necessary because Agent Zero seems to use the the first domain it sees to calculate a
		// backing array for the constraint tables.
		int shortestMeetingIndex = 0;
		for (int i = 0; i < meetings.length; i++) {
			meetings[i] = new Meeting(rand);
			if (meetings[i].duration < meetings[shortestMeetingIndex].duration) {
				shortestMeetingIndex = i;
			}
			for (int j = i + 1; j < meetings.length; j++) {
				travelTimes[i][j] = travelTimes[j][i] = valueInRange(rand, minTravelTime, travelTimeRange);
			}
		}
		// now we swap the shortest meeting into the 0th index 
		if (shortestMeetingIndex != 0) {
			Meeting shortestMeeting = meetings[shortestMeetingIndex];
			meetings[shortestMeetingIndex] = meetings[0];
			meetings[0] = shortestMeeting;
		}
		// compute the meeting participants
		switch (participantGeneration) {
		case ATTENDANCE:
			// nothing to do; the meeting constructors created the participants
			break;
		case LOAD:
			initMeetingsByLoad(meetings, rand);
			break;
		default:
			throw new AssertionError("Unsupported participant generation type " + participantGeneration);
		}
		
		// then we create the DCOP representation as an EAV
		// there is a variable for each meeting, with domain [0..t-duration] so that meetings cannot be scheduled
		// at a time when they cannot finish before the last time slot
		ArrayList<Set<Integer>> domains = new ArrayList<Set<Integer>>(m);
		for (Meeting meeting : meetings) {
			LinkedHashSet<Integer> domain = new LinkedHashSet<Integer>();
			for (int i = 0; i < t - meeting.duration + 1; i++) {
				domain.add(i);
			}
			domains.add(domain);
		}
		prob.initialize(ProblemType.DCOP, domains);

		// now we add constraints.  there are binary constraints between meetings 
		for (int i = 0; i < meetings.length; i++) {
			Meeting meeting1 = meetings[i];
			for (int j = i + 1; j < meetings.length; j++) {
				Meeting meeting2 = meetings[j];
				if (meeting1.getNumOverlap(meeting2) > 0) {
					for (int time1 :domains.get(i)) {
						// conflict window begins at earliest starting time for the meeting2 when 
						// participants in both meetings cannot make it to the meeting1 before the scheduled
						// start of meeting1
						int conflictWindowStart = Math.max(0, time1 - (meeting2.duration - 1 + travelTimes[j][i]));
						// conflict window ends when participants in both meeting can make it from meeting1 to
						// meeting2 before the scheduled start of meeting2.  this is the last (i.e., inclusive)
						// time slot with a conflict
						int conflictWindowEnd = Math.min(time1 + meeting1.duration + travelTimes[i][j] - 1, t-1);
						// compute cost of a conflict
						int cost = computeConflictCost(meeting1, meeting2);
						for (int time2 : domains.get(j)) {
							// add in the unary cost; as in Hilla's code we take the integer average so that
							// the total cost is spread over the two constraints
							int unaryCost = (meeting1.getTimeCost(time1) + meeting2.getTimeCost(time2)) / 2;
							// we only add the conflict cost if the meeting times conflict
							int totalCost = unaryCost + (time2 >= conflictWindowStart && time2 <= conflictWindowEnd ? cost : 0);
							if (totalCost > 0) {
								prob.setConstraintCost(i, time1, j, time2, 1000*totalCost); //NOISE ADDITION
								prob.setConstraintCost(j, time2, i, time1, 1000*totalCost); //NOISE ADDITION
							}
						}
					}
				}
			}
		}
	}

	public int getNumAgents() {
		return n;
	}
	
	public int getNumMeetings() {
		return m;
	}
	
	public int getNumTimeSlots() {
		return t;
	}
	
	public int getMinDuration() {
		return minDuration;
	}
	
	public int getDurationRange() {
		return durationRange;
	}
	
	public int getMinAttendance() {
		return minAttendance;
	}
	
	public int getAttendanceRange() {
		return attendanceRange;
	}
	
	public int getMinTravelTime() {
		return minTravelTime;
	}
	
	public int getTravelTimeRange() {
		return travelTimeRange;
	}
	
	public int getMinLoad() {
		return minLoad;
	}
	
	public int getLoadRange() {
		return loadRange;
	}
	
	public int getMinTimeCost() {
		return minTimeCost;
	}
	
	public int getTimeCostRange() {
		return timeCostRange;
	}
	
	
	/**
	 * Compute the cost that would be incurred if two meetings are scheduled at a conflicting time.
	 * The cost function depends on the <code>conflictCost</code> parameter and the participants of
	 * the two meetings.
	 * @param m1 The first meeting.
	 * @param m2 The second meeting.
	 * @return The cost of conflict.
	 */
	protected int computeConflictCost(Meeting m1, Meeting m2) {
		int cost = 0;
		switch (conflictCost) {
		case BINARY:
			// unit cost
			cost = 1;
			break;
		case SUM_TOTAL:
			// the total number of participants in the two meetings
			cost = m1.getNumParticipants() + m2.getNumParticipants() - m1.getNumOverlap(m2);
			break;
		case SUM_CONFLICT:
			// the number of participants who are participating in both meetings
			cost = m1.getNumOverlap(m2);
			break;
		case MIN_TOTAL:
			// the total number of participants in the smaller meeting
			cost = m1.getNumParticipants() <= m2.getNumParticipants() ? m1.getNumParticipants() : m2.getNumParticipants(); 
			break;
		default:
			throw new UnsupportedOperationException("Unknown conflict cost type " + conflictCost + ".");
		}
		return cost;
	}
	
	/**
	 * Initializes the participants to meetings by when the <code>ParticipantGeneration.LOAD</code> 
	 * generation type is being used.  Chooses meetings for each participant uniformly at random in
	 * order to meet each agent's desired load.
	 * @param meetings The array of meetings.  The meetings should already be instantiated.
	 * @param rand The pseudorandom number generator.
	 */
	private void initMeetingsByLoad(Meeting [] meetings, Random rand) {
		// loop through all agents and choose the meetings each participates in
		for (int i = 0; i < n; i++) {
			int load = minLoad + rand.nextInt(loadRange);
			// choose meetings using Fisher-Yates shuffling
			int [] meetingIds = getMeetingsForShuffle();
			for (int j = 0; j < load; j++) {
				int idx = j + rand.nextInt(meetingIds.length - j);
				int temp = meetingIds[idx];
				meetingIds[idx] = meetingIds[j];
				meetingIds[j] = temp;
				meetings[temp].addParticipant(i);
			}
		}
	}

	/**
	 * Gets a random integer from within a range [min..min+range-1].
	 * @param rand The pseudorandom number generator.
	 * @param min The minimum allowed value.
	 * @param range The number of sequential integers to be chosen from.
	 * @return A value chosen uniformly at random from [min..min+range-1] using <code>rand</code>.
	 */
	private int valueInRange(Random rand, int min, int range) {
		return min + (range > 0 ? rand.nextInt(range) : 0);
	}

	/**
	 * Gets the array of agent IDs to be used for shuffling to randomly choose sets of agents.
	 * @return An array of agent IDs.
	 */
	private int [] getAgentsForShuffle() {
		// use lazy initialization
		if (agentsForShuffle == null) {
			agentsForShuffle = new int[n];
			for (int i = 0; i < agentsForShuffle.length; i++) {
				agentsForShuffle[i] = i;
			}
		}
		return agentsForShuffle;
	}

	/**
	 * Gets the array of meeting IDs to be used for shuffling to randomly choose sets of meetings. 
	 * @return an array of meeting IDs.
	 */
	private int [] getMeetingsForShuffle() {
		// use lazy initialization
		if (meetingsForShuffle == null) {
			meetingsForShuffle = new int[m];
			for (int i = 0; i < meetingsForShuffle.length; i++) {
				meetingsForShuffle[i] = i;
			}
		}
		return meetingsForShuffle;
	}
	

	
	/**
	 * Helper class representing a meeting.
	 * @author Steven
	 *
	 */
	private class Meeting {
		/**
		 * List of the IDs of agents required to participate in the meeting.
		 */
		List<Integer> participants;
		/**
		 * Unary costs indexed by time indicating preferences for the meeting to be scheduled at different times
		 */
		int [] costs;
		/**
		 * Duration of the meeting, as measured by time slots.
		 */
		int duration;

		/**
		 * Constructs a new Meeting instance.  For ATTENDANCE this includes generating the participants; for LOAD,
		 * participants must be generated later.
		 * @param rand
		 */
		public Meeting(Random rand) {
			switch (participantGeneration) {
			case ATTENDANCE:
				int numParticipants = valueInRange(rand, minAttendance, attendanceRange);
				selectParticipants(rand, numParticipants);
				break;
			case LOAD:
				if (participants == null) {
					participants = new LinkedList<Integer>();
				} else {
					participants.clear();
				}
				break;
			default:
				throw new UnsupportedOperationException("Unknown participant generation type " + participantGeneration + ".");
			}

			duration = valueInRange(rand, minDuration, durationRange);
			costs = new int[t];
			if (minTimeCost > 0 || timeCostRange > 1) {
				for (int time = 0; time < t; time++) {
					costs[time] = minTimeCost + rand.nextInt(timeCostRange);
				}
			}
		}
		
		/**
		 * Adds a agent to the meeting.  The agent should not already be a participant. 
		 * @param agent The agent to be added as a participant.
		 * @throws IllegalArgumentException If the agent is already a participant.
		 */
		public void addParticipant(int agent) throws IllegalArgumentException {
			if (participants.contains(agent)) {
				throw new IllegalArgumentException("Adding agent " + agent + " which is already a participants!");
			}
			participants.add(agent);
		}
		
		/**
		 * Gets the number of participants in the meeting.
		 * @return The number of participants.
		 */
		public int getNumParticipants() {
			return participants.size();
		}
		
		/**
		 * Gets the number of agents who are participating both in this meeting and another meeting.
		 * @param meeting The other meeting.
		 * @return The intersection of the participant sets of the two meetings.
		 */
		public int getNumOverlap(Meeting meeting) {
			int count = 0;
			for (int participant : meeting.participants) {
				if (participants.contains(participant)) {
					count++;
				}
			}
			return count;
		}
		
		/**
		 * Gets the preference of scheduling a meeting as a cost.  
		 * @param time The time.
		 * @return The cost.
		 */
		public int getTimeCost(int time) {
			return costs[time];
		}
		
		@Override
		public String toString() {
			boolean previousParticipant = false;
			StringBuffer sb = new StringBuffer("M(" + getNumParticipants() + "={");
			for (Integer participant : participants) {
				if (previousParticipant) {
					sb.append(", ");
				}
				sb.append(participant);
				previousParticipant = true;
			}
			sb.append("}, " + duration + ")");
			return sb.toString();
		}
		

		/**
		 * Chooses participants uniformly at random; should be used with the ATTENDANCE participant generation
		 * type.
		 * @param rand The pseudorandom number generator.
		 * @param numParticipants The number of participants for the meeting.
		 */
		private void selectParticipants(Random rand, int numParticipants) {
			if (participants == null) {
				participants = new ArrayList<Integer>(numParticipants);
			} else {
				participants.clear();
			}
			// use a partial Fisher-Yates shuffle to swap the elements into the first n places in the array
			// and to move them into the participant list (swapping is still needed so the array is valid for
			// later calls)
			int [] agents = getAgentsForShuffle();
			for (int i = 0; i < numParticipants; i++) {
				int idx = i + rand.nextInt(n - i);
				int temp = agents[i];
				agents[i] = agents[idx];
				agents[idx] = temp;
				participants.add(agents[i]);
			}

		}
	}
}
