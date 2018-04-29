package ext.sim.agents;

import bgu.dcr.az.api.agt.SimpleAgent;
import bgu.dcr.az.api.ano.Algorithm;
import bgu.dcr.az.api.ano.Variable;
import bgu.dcr.az.api.ano.WhenReceived;
import bgu.dcr.az.api.tools.Assignment;

import java.util.Hashtable;
import java.util.HashSet;
import java.util.Random;

import utils.Request;

@Algorithm(name = "AGC", useIdleDetector = false)
public class AGC extends SimpleAgent {
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
	double bestCostReduction;
	double mu_t_minus_1;
	double c_St_minus_1;
	double Phi_t_minus_1;
	Assignment baselineLocalView;
	Assignment localView;
	Hashtable<Integer, Request> valueAssignments = new Hashtable<Integer, Request>();
	Hashtable<Integer, Request> requests = new Hashtable<Integer, Request>();

	@Override
	public void start() {
		initializeVariables();
		chooseNewValue();
	}

	private void initializeVariables() {
		nPhases = 3;
		phase = 1;
		gotNegative = false;
		Phi_t_minus_1 = 1;
		bestCostReduction = 0;
		myCurrentRequest = random(this.getDomain());
		baselineLocalView = new Assignment(getId(), myCurrentRequest);
		localView = new Assignment(getId(), myCurrentRequest);
		submitCurrentAssignment(myCurrentRequest);
		send("valueAssignment", getId(), myCurrentRequest, myCurrentRequest, bestCostReduction).toNeighbores();
	}

	// handling messages functions
	@WhenReceived("valueAssignment")
	public void handleValueAssignment(int i, int vi, int ri, double gain) {
		valueAssignments.put(valueAssignments.size(), new Request(i, vi, ri, gain, getSystemTimeInTicks()));
		localView.assign(i, ri);
	}

	@WhenReceived("Request")
	public void handleRequest(int i, int vi, int ri, double gain) {
		requests.put(requests.size(), new Request(i, vi, ri, gain, getSystemTimeInTicks()));
	}

	@WhenReceived("Neg")
	public void handleNegMessage(int i) {
		gotNegative = true;
	}

	// algorithm functions
	@SuppressWarnings("deprecation")
	@Override
	public void onMailBoxEmpty() {

		if (getSystemTimeInTicks() <= timeFrame * nPhases) {
			switch (phase) {
			case 1:
				if (getSystemTimeInTicks() == 1) {
					baselineLocalView = localView.copy();
					mu_t_minus_1 = this.costOf(baselineLocalView);
					c_St_minus_1 = this.costOf(baselineLocalView);
				}
				chooseNewValue();
				phase = 2;
				return;

			case 2:
				findBestCostReductionAndSendNegMessages();
				requests.clear();
				phase = 3;
				return;

			case 3:
				mu_t_minus_1 = (getCurrentBudget(this.localView) + this.costOf(localView)) / (1 + lambda_0);
				c_St_minus_1 = this.costOf(localView);
				Phi_t_minus_1 = 0;
				if (gotNegative == false && canImprove == true) {
					Phi_t_minus_1 = 1;
					int currentAssignment = this.getSubmitedCurrentAssignment();
					submitCurrentAssignment(myCurrentRequest);
					localView.assign(this.getId(), myCurrentRequest);
					send("valueAssignment", getId(), currentAssignment, myCurrentRequest, bestCostReduction)
							.toNeighbores();
				}
				gotNegative = false;
				phase = 1;
			}

		} else {
			finish();
		}
	}

	// utility functions
	private void chooseNewValue() {
		myCurrentRequest = (Integer) findImprovingAssignment(this.localView);
		canImprove = true;

		if ((myCurrentRequest == -1) || (myCurrentRequest == this.getSubmitedCurrentAssignment())) {
			canImprove = false;
			bestCostReduction = 0;
		} else {
			bestCostReduction = localView.calcCost(this.getProblem())
					- localView.calcAddedCost(this.getId(), myCurrentRequest, this.getProblem());
			send("Request", getId(), this.getSubmitedCurrentAssignment(), myCurrentRequest, bestCostReduction)
					.toNeighbores();
		}
	}

	private void findBestCostReductionAndSendNegMessages() {
		double bestCostReduction = this.bestCostReduction;
		int bestCostReductionAgentId = this.getId();
		double budget_t = getCurrentBudget(localView);

		for (int neighbor : requests.keySet()) {
			int neighborId = requests.get(neighbor).neighborId;
			int neighborOriginalAssignment = requests.get(neighbor).currentAssignment;
			int neighborRequest = requests.get(neighbor).request;
			double neighborCostReduction = requests.get(neighbor).gain;
			double requestCost = this.getConstraintCost(neighborId, neighborRequest, this.getId(),
					this.getSubmitedCurrentAssignment())
					- this.getConstraintCost(neighborId, neighborOriginalAssignment, this.getId(),
							this.getSubmitedCurrentAssignment());

			if ((neighborCostReduction > bestCostReduction && budget_t >= requestCost)
					|| (neighborCostReduction == bestCostReduction && neighborId > bestCostReductionAgentId
							&& budget_t >= requestCost)) {
				if (bestCostReductionAgentId != this.getId()) {
					send("Neg", getId()).to(bestCostReductionAgentId);
				}
				bestCostReduction = neighborCostReduction;
				bestCostReductionAgentId = neighborId;
			} else {
				send("Neg", getId()).to(neighborId);
			}
		}
	}

	private double getCurrentBudget(Assignment localView) {
		double budget_t;
		double cost_St = this.costOf(localView);
		double lambda_t = lambda_0;
		double mu_t = this.costOf(baselineLocalView);

		if (agentType == 1)
			mu_t = this.costOf(baselineLocalView);
		if (agentType == 2)
			mu_t = mu_t_minus_1 + Math.min(0, (cost_St - c_St_minus_1) / (1 + lambda_t));
		if (agentType == 3)
			mu_t = mu_t_minus_1 + Math.min(0, Phi_t_minus_1 * (cost_St - c_St_minus_1) / (1 + lambda_t));

		budget_t = mu_t * (1 + lambda_t) - cost_St;
		return budget_t;
	}

	private Object findImprovingAssignment(Assignment localView) {
		Object improvingAssignment = -1;
		HashSet<Object> currentDomain = new HashSet<Object>(getDomain());

		while (currentDomain.isEmpty() == false) {
			Object[] domainValues = currentDomain.toArray();
			int rndVal = new Random().nextInt(domainValues.length);
			Object val = domainValues[rndVal];
			currentDomain.remove(val);

			double valAssignmentCost = localView.calcCost(this.getProblem())
					- localView.calcAddedCost(this.getId(), (Integer) val, this.getProblem());
			if (valAssignmentCost > 0) {
				improvingAssignment = val;
				break;
			}
		}
		return improvingAssignment;
	}

}