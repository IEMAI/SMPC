package ext.sim.agents;

import bgu.dcr.az.api.agt.SimpleAgent;
import bgu.dcr.az.api.ano.Algorithm;
import bgu.dcr.az.api.ano.Variable;
import bgu.dcr.az.api.ano.WhenReceived;
import bgu.dcr.az.api.tools.Assignment;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Random;

@Algorithm(name = "SM_AGC", useIdleDetector = false)
public class SM_AGC extends SimpleAgent {
	// Declare AGC Global Variables
	@Variable(name = "timeFrame", defaultValue = "1000 ", description = "The number of iterations in a single run of the algorithm")
	int timeFrame = 1000;
	@Variable(name = "lambda_0", defaultValue = "1", description = "Initial cooperation parameter (i.e., lambda_{t=0})")
	double lambda_0 = 1;
	@Variable(name = "agentType", defaultValue = "1", description = "Represents agent's behavioral traits and willingness to cooperate")
	int agentType = 1;

	boolean canImprove;
	boolean gotNegative;
	int nPhases;
	int phase;
	int myCurrentRequest;
	double myBestCostResuction;
	double mu_t_minus_1;
	double c_St_minus_1;
	double Phi_t_minus_1;
	Assignment baselineLocalView;
	Assignment localView;

	// Declare SM_AGC Global Variables
	@Variable(name = "vote", defaultValue = "cost", description = "")
	String vote = "cost";
	@Variable(name = "altruismFactor", defaultValue = "1", description = "")
	int altruismFactor = 1;
	@Variable(name = "taboo", defaultValue = "1", description = "")
	int taboo = 1;

	Random randNum = new Random();
	Hashtable<Integer, Boolean> taboos;
	Hashtable<Integer, Double> neighborsRequestsCostReduction;
	Hashtable<Integer, Integer> neighborsRequests;
	double[] cumulativeVotes;
	double[] selfCumulativeVotes;

	@Override
	public void start() {
		initializeVariables();
	}

	private void initializeVariables() {
		nPhases = 4;
		phase = 0;
		localView = null;
		baselineLocalView = null;
		gotNegative = false;
		Phi_t_minus_1 = 1;
		myBestCostResuction = 0;
		canImprove = false;
		randNum.setSeed(this.getId());
		taboos = new Hashtable<Integer, Boolean>();
		cumulativeVotes = new double[this.getDomainSize()];
		selfCumulativeVotes = new double[this.getDomainSize()];
		neighborsRequestsCostReduction = new Hashtable<Integer, Double>();
		neighborsRequests = new Hashtable<Integer, Integer>();
		for (int val = 0; val < this.getDomainSize(); val++)
			taboos.put(val, false);
		myCurrentRequest = random(this.getDomain());
		localView = new Assignment(getId(), myCurrentRequest);
		baselineLocalView = new Assignment(getId(), myCurrentRequest);
		this.submitCurrentAssignment(myCurrentRequest);
		send("valueAssignment", getId(), myCurrentRequest, myCurrentRequest).toNeighbores();
	}

	// handling messages functions 	
	@WhenReceived("valueAssignment")
	public void handleValueAssignment(int i, int vi, int ri) {
		localView.assign(i, ri);
	}

	@WhenReceived("taboos")
	public void handletaboos(int neighborId, boolean[] taboosList) {
		if (taboo == 1) {
			for (int val = 0; val < this.getDomainSize(); val++) {
				if (taboosList[val] == true) {
					taboos.put(val, true);
				}
			}
		}
	}

	@WhenReceived("changeRequest")
	public void handleChangeRequest(int neighborId, int changeRequest, double costReduction) {
		if (vote.equals("cost"))
			cumulativeVotes[changeRequest] = cumulativeVotes[changeRequest] + costReduction;
		if (vote.equals("binary")) {
			if (costReduction >= 0)
				cumulativeVotes[changeRequest] = cumulativeVotes[changeRequest] + 1;
			else
				cumulativeVotes[changeRequest] = cumulativeVotes[changeRequest] - 1;
		}
	}

	@WhenReceived("costReduction")
	public void handleCostReductionMessage(int i, int request, double cr) {
		neighborsRequestsCostReduction.put(i, cr);
		neighborsRequests.put(i, request);
	}

	@WhenReceived("Neg")
	public void handleNeg(int i) {
			gotNegative = true;
		}

	// algorithm functions
	@SuppressWarnings("deprecation")
	@Override
	public void onMailBoxEmpty() {

		if (getSystemTimeInTicks() <= timeFrame * nPhases) {

			switch (phase) {
			case 0:
				if (getSystemTimeInTicks() == 1) {
					baselineLocalView = localView.copy();
					mu_t_minus_1 = this.costOf(baselineLocalView);
					c_St_minus_1 = this.costOf(baselineLocalView);
				}
				sendTaboosToAgents(this.localView);
			case 1:

				sendPreferencesToNeighbors(this.localView, new HashSet<Object>(getDomain()));
				phase = 2;
				return;
			case 2:
				calculateSelfCostsInvolvedInNeighborsPreferences(this.localView);
				sendSocialImprovingAssignment();
				phase = 3;
				return;
			case 3:
				collectSocialGainsAndSendNegs();
				phase = 4;
				return;
			case 4:
				mu_t_minus_1 = (getCurrentBudget(this.localView) + this.costOf(localView)) / (1 + lambda_0);
				c_St_minus_1 = this.costOf(localView);
				Phi_t_minus_1 = 0;
				
				if (isThebestSocialGainIsMine() && canImprove && !gotNegative) {
					Phi_t_minus_1 = 1;
					submitCurrentAssignmentAndUpdateNeighbors();
				}
				reInitializeVariables();
				phase = 0;
			}

		} else {

			finish();
		}
	}

	// utility functions
	private void submitCurrentAssignmentAndUpdateNeighbors() {

		int myCurrentAssignment = this.localView.getAssignment(this.getId());
		submitCurrentAssignment(myCurrentRequest);
		localView.assign(this.getId(), myCurrentRequest);

		send("valueAssignment", getId(), myCurrentAssignment, myCurrentRequest).toNeighbores();
	}

	private void sendPreferencesToNeighbors(Assignment lv, HashSet<Object> domain) {
		int myCurrentAssignment = this.getSubmitedCurrentAssignment();
		for (Map.Entry<Integer, Integer> neighborAssignment : lv.getAssignments()) {
			int neighborId = neighborAssignment.getKey();

			if (neighborId != this.getId()) {
				double currentCost = getConstraintCost(this.getId(), myCurrentAssignment, neighborId,
						lv.getAssignment(neighborId));
				HashSet currentDomain = new HashSet(domain);

				while (currentDomain.isEmpty() == false) {
					Object[] domainValues = currentDomain.toArray();
					int rndVal = new Random().nextInt(domainValues.length);
					Object val = domainValues[rndVal];
					double requestCost = getConstraintCost(this.getId(), myCurrentAssignment, neighborId, (int) val);
					double costReduction = currentCost - requestCost;

					if (costReduction > 0) {
						send("changeRequest", getId(), val, costReduction).to(neighborId);
						break;
					}
					currentDomain.remove(val);
				}
			}
		}
	}

	private void calculateSelfCostsInvolvedInNeighborsPreferences(Assignment lv) {
		for (int request = 0; request < cumulativeVotes.length; request++) {
			if ((taboo == 1 && vote.equals("none")) || cumulativeVotes[request] > 0) {
				double valSelfCostReduction = localView.calcCost(this.getProblem())
						- localView.calcAddedCost(this.getId(), (int) request, this.getProblem());
				selfCumulativeVotes[request] = valSelfCostReduction;
			}
		}
	}

	private int randomlySampleByFrequency(double[] freqTable) {
		for (int val = 0; val < freqTable.length; val++)
			if (freqTable[val] < 0)
				freqTable[val] = 0;
		double sumOfVals = 0;
		for (int val = 0; val < freqTable.length; val++)
			sumOfVals = sumOfVals + freqTable[val];
		double rndNum = randNum.nextDouble();
		double accumulatedDensity = 0;
		for (int val = 0; val < freqTable.length; val++) {
			if (freqTable[val] == 0)
				continue;
			double valDensity = freqTable[val] / sumOfVals;
			if ((rndNum >= accumulatedDensity) && (rndNum < accumulatedDensity + valDensity))
				return val;
			accumulatedDensity = accumulatedDensity + valDensity;
		}
		return -1;
	}

	private void sendSocialImprovingAssignment() {
		double[] freqTable = new double[this.getDomainSize()];
		int sampledVal;
		for (int val = 0; val < cumulativeVotes.length; val++) {
			if (taboos.get(val) == true) {
				freqTable[val] = 0;
				continue;
			}
			if (-selfCumulativeVotes[val] <= this.getCurrentBudget(this.localView)) {
				if (vote.equals("cost") || vote.equals("none"))
					freqTable[val] = cumulativeVotes[val] + selfCumulativeVotes[val] * altruismFactor;
				if (vote.equals("binary") && selfCumulativeVotes[val] > 0) {
					freqTable[val] = cumulativeVotes[val] + 1;
				}
			} else {
				freqTable[val] = 0;
			}
		}
		sampledVal = randomlySampleByFrequency(freqTable);

		if (((sampledVal != -1) && freqTable[sampledVal] > 0)) {
			send("costReduction", getId(), sampledVal, freqTable[sampledVal]).toNeighbores();
			canImprove = true;
			myBestCostResuction = freqTable[sampledVal];
			myCurrentRequest = sampledVal;
		} else {
			myBestCostResuction = 0;
		}
	}

	private boolean isThebestSocialGainIsMine() {
		double bestCostReduction = myBestCostResuction;
		boolean answer = true;
		if (canImprove == false)
			return false;
		for (int neighborId : neighborsRequestsCostReduction.keySet()) {
			double neighborCr = neighborsRequestsCostReduction.get(neighborId);

			if ((bestCostReduction < neighborCr)
					|| ((bestCostReduction == neighborCr) && (this.getId() < neighborId))) {
				answer = false;
			}
		}
		return answer;
	}

	private double getCurrentBudget(Assignment localView) {
		double budget_t;
		double cost_St = this.costOf(localView);

		double lambda_t = lambda_0;
		double mu_t = this.costOf(baselineLocalView);
		if (agentType == 1)
			mu_t = this.costOf(baselineLocalView);
		if (agentType == 2) {
			mu_t = mu_t_minus_1 + Math.min(0, (cost_St - c_St_minus_1) / (1 + lambda_t));
			//if (this.getId() == 0 && this.agentType == 2) System.out.println(this.getSystemTimeInTicks()+" : "+mu_t);		
	}
		if (agentType == 3)
			mu_t = mu_t_minus_1 + Math.min(0, Phi_t_minus_1 * (cost_St - c_St_minus_1) / (1 + lambda_t));

		budget_t = mu_t * (1 + lambda_t) - cost_St;
		
		return budget_t;
	}

	private boolean[] findtaboosforNeighbor(int neighborId, Assignment lv) {
		boolean allNegative = true;
		boolean[] neighborTaboos = new boolean[this.getDomainSize()];

		for (int neighborPssibleRequest = 0; neighborPssibleRequest < this.getDomainSize(); neighborPssibleRequest++) {
			int neighborOriginalAssignment = lv.getAssignment(neighborId);

			double requestCost = this.getConstraintCost(neighborId, neighborPssibleRequest, this.getId(),
					this.getSubmitedCurrentAssignment())
					- this.getConstraintCost(neighborId, neighborOriginalAssignment, this.getId(),
							this.getSubmitedCurrentAssignment());
			if (getCurrentBudget(lv) < requestCost) {
				neighborTaboos[neighborPssibleRequest] = true;
				allNegative = false;
			} else {
				neighborTaboos[neighborPssibleRequest] = false;

			}

		}

		return neighborTaboos;
	}

	private void sendTaboosToAgents(Assignment lv) {
		for (Map.Entry<Integer, Integer> neighborAssignment : lv.getAssignments()) {
			int neighborId = neighborAssignment.getKey();
			if (neighborId != this.getId()) {
				boolean[] neighborTaboos = new boolean[this.getDomainSize()];
				neighborTaboos = findtaboosforNeighbor(neighborId, lv);
				send("taboos", getId(), neighborTaboos).to(neighborId);
			}
		}
	}

	private void collectSocialGainsAndSendNegs() {
		double bestCostReduction = 0;
		int bestAgentCostReduction = -1;
		if (canImprove) {
			bestCostReduction = myBestCostResuction;
			bestAgentCostReduction = this.getId();
		}

		for (int neighborId : neighborsRequestsCostReduction.keySet()) {
			double neighborCr = neighborsRequestsCostReduction.get(neighborId);
			int neighborRequest = neighborsRequests.get(neighborId);
			int neighborOriginalAssignment = this.localView.getAssignment(neighborId);
			double requestCost = this.getConstraintCost(neighborId, neighborRequest, this.getId(),
					this.getSubmitedCurrentAssignment())
					- this.getConstraintCost(neighborId, neighborOriginalAssignment, this.getId(),
							this.getSubmitedCurrentAssignment());

			if (taboo == 0 && (getCurrentBudget(localView) < requestCost)) {
				send("Neg", getId()).to(neighborId);
				continue;
			}
			if (neighborCr >= bestCostReduction) {

				if (((neighborCr == bestCostReduction) && (neighborId > bestAgentCostReduction))
						|| (neighborCr > bestCostReduction)) {
					if ((bestAgentCostReduction != this.getId()) && (bestAgentCostReduction != -1)) {
						send("Neg", getId()).to(bestAgentCostReduction);
					}
					bestCostReduction = neighborCr;
					bestAgentCostReduction = neighborId;

				} else {
					send("Neg", getId()).to(neighborId);
				}

			} else {
				send("Neg", getId()).to(neighborId);
			}
		}
	}

	private void reInitializeVariables() {
		myBestCostResuction = 0;
		canImprove = false;
		neighborsRequestsCostReduction.clear();
		neighborsRequests.clear();
		cumulativeVotes = new double[this.getDomainSize()];
		selfCumulativeVotes = new double[this.getDomainSize()];
		for (int val = 0; val < this.getDomainSize(); val++)
			taboos.put(val, false);
		gotNegative = false;
	}

}
