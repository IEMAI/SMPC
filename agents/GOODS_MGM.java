package ext.sim.agents;

import java.util.Hashtable;
import bgu.dcr.az.api.agt.SimpleAgent;
import bgu.dcr.az.api.ano.Algorithm;
import bgu.dcr.az.api.ano.Variable;
import bgu.dcr.az.api.ano.WhenReceived;
import bgu.dcr.az.api.tools.Assignment;
import utils.Request;
import java.util.HashSet;
import java.util.Random;

@Algorithm(name = "GOODS_MGM", useIdleDetector = false)
public class GOODS_MGM extends SimpleAgent {

	@Variable(name = "timeFrame", defaultValue = "1000 ", description = "The number of iterations in a single run of the algorithm")
	int timeFrame = 1000;
	@Variable(name = "lambda_0", defaultValue = "1", description = "Initial cooperation parameter (i.e., lambda_{t=0})")
	double lambda_0 = 1;
	@Variable(name = "agentType", defaultValue = "1", description = "Represents agent's behavioral traits and willingness to cooperate")
	int agentType = 1;

	Hashtable<Integer, Request> valueAssignments = new Hashtable<Integer, Request>();
	Hashtable<Integer, Request> requests = new Hashtable<Integer, Request>();

	Assignment localView;
	Assignment baselineLocalView;
	Assignment prevLocalViewAfterNogoods;
	Assignment prevLocalViewBeforeNogoods;
	Assignment realStateLocalView;
	Assignment prevRealStateLocalView;

	boolean canImprove;
	boolean gotNegative;
	int phase;
	int myCurrentRequest;
	double myLr;

	int nPhases;
	double mu_t_minus_1;
	double c_St_minus_1;
	double Phi_t_minus_1;

	Hashtable<Integer, Hashtable<Integer, Boolean>> NG_store = new Hashtable<Integer, Hashtable<Integer, Boolean>>();
	Hashtable<Integer, Integer> elim = new Hashtable<Integer, Integer>();
	Hashtable<Integer, Double> neighborsLrs = new Hashtable<Integer, Double>();
	boolean gotBaseLine;
	boolean sendBaseLine;

	@Override
	public void start() {
		initializeVariables();
	}

	private void initializeVariables() {
		nPhases = 5;
		phase = 4;
		gotNegative = false;
		Phi_t_minus_1 = 1;
		prevRealStateLocalView = null;
		prevLocalViewBeforeNogoods = null;
		prevLocalViewAfterNogoods = null;
		initializeNG_Store();
		initializeElim();

		gotBaseLine = false;
		sendBaseLine = false;
		myLr = 0;
		canImprove = false;

		myCurrentRequest = random(this.getDomain());
		submitCurrentAssignment(myCurrentRequest);
		baselineLocalView = new Assignment(getId(), myCurrentRequest);
		localView = new Assignment(getId(), myCurrentRequest);
		realStateLocalView = new Assignment(getId(), myCurrentRequest);
		send("valueAssignment", this.getId(), myCurrentRequest, myCurrentRequest).toNeighbores();
	}

	// handling messages functions 	
	@WhenReceived("valueAssignment")
	public void handleValueAssignment(int i, int vi, int ri) {
		valueAssignments.put(valueAssignments.size(), new Request(i, vi, ri, -1, getSystemTimeInTicks()));
		localView.assign(i, ri);
		realStateLocalView.assign(i, ri);
	}

	@WhenReceived("good")
	public void handleGoodMessage(int i, int vi) {
		int currentNoGoodsForVal = elim.get(vi);
		elim.put(vi, currentNoGoodsForVal - 1);
	}

	@WhenReceived("noGood")
	public void handleNoGoodMessage(int i, int vi) {
		int currentNoGoodsForVal = elim.get(vi);
		elim.put(vi, currentNoGoodsForVal + 1);
	}

	@WhenReceived("baseLine")
	public void handleBaseLineMessage(int i, int vi) {
		this.localView.assign(i, vi);
		this.realStateLocalView.assign(i, vi);
		gotBaseLine = true;
	}

	@WhenReceived("Lr")
	public void handleLrMessage(int i, double Lr) {
		neighborsLrs.put(i, Lr);

	}

	// algorithm functions
	@SuppressWarnings("deprecation")
	@Override
	
	public void onMailBoxEmpty() {
		if (getSystemTimeInTicks() <= timeFrame * nPhases) { // should continue
			switch (phase) {

			case 1:
				checkNgStoreAndSendGoodsMessages();
				phase = 2;
				return;
			case 2:
				checkChangesAndSendNoGoods();
				phase = 3;
				return;
			case 3:
				if (isDomainEmpty() == true)
					returnToBaseLine();
				phase = 4;
				return;
			case 4:
				if (getSystemTimeInTicks() == 1) {
					prevRealStateLocalView = realStateLocalView.copy();
					baselineLocalView = localView.copy();
				}
				if (!gotBaseLine && !sendBaseLine) {
					findBestAssignmnetAndSendToNeighbors();
				}
				phase = 5;
				return;
			case 5:
				mu_t_minus_1 = (getCurrentBudget(this.localView) + this.costOf(localView)) / (1 + lambda_0);
				c_St_minus_1 = this.costOf(localView);
				Phi_t_minus_1 = 0;

				if (canImprove && isTheBestLrIsMine()) {
					Phi_t_minus_1 = 1;
					int currentAssignment = this.getSubmitedCurrentAssignment();
					submitCurrentAssignment(myCurrentRequest);
					localView.assign(this.getId(), myCurrentRequest);
					realStateLocalView.assign(this.getId(), myCurrentRequest);
					send("valueAssignment", getId(), currentAssignment, myCurrentRequest).toNeighbores();
				}
				if (gotBaseLine) {
					int currentAssignment = this.getSubmitedCurrentAssignment();
					int baseLineAssignment = this.baselineLocalView.getAssignment(this.getId());
					submitCurrentAssignment(baseLineAssignment);
					localView.assign(this.getId(), baseLineAssignment);
					realStateLocalView.assign(this.getId(), baseLineAssignment);
					send("valueAssignment", getId(), currentAssignment, baseLineAssignment).toNeighbores();
				}
				reInitializeVariables();
				return;
			}
		} else {
			finish();
		}
	}

	// utility functions
	private void initializeNG_Store() {

		for (int neighborId : this.getNeighbors()) {
			Hashtable<Integer, Boolean> neighborNG_store = new Hashtable<Integer, Boolean>();
			for (int val = 0; val < this.getDomainSize(); val++) {
				neighborNG_store.put(val, false);
			}
			NG_store.put(neighborId, neighborNG_store);
		}
	}

	private void initializeElim() {
		for (int val : this.getDomain())
			elim.put(val, 0);
	}

	@SuppressWarnings("deprecation")
	private void reInitializeVariables() {
		myCurrentRequest = -1;
		myLr = 0;
		canImprove = false;
		gotBaseLine = false;
		sendBaseLine = false;
		prevLocalViewAfterNogoods = this.localView.copy();
		phase = 1;
		prevRealStateLocalView = new Assignment();
		prevRealStateLocalView = realStateLocalView.copy();
		neighborsLrs = new Hashtable<Integer, Double>();
	}

	private boolean isDomainEmpty() {
		for (int val : this.getDomain()) {
			if (elim.get(val) == 0) {
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings("deprecation")
	private void returnToBaseLine() {
		int blAssignment = baselineLocalView.getAssignment(this.getId());
		this.submitCurrentAssignment(blAssignment);
		this.localView = this.baselineLocalView.copy();
		send("baseLine", this.getId(), blAssignment).toNeighbores();
		sendBaseLine = true;
		initializeElim();
	}

	@SuppressWarnings("deprecation")
	private void checkChangesAndSendNoGoods() {
		prevLocalViewBeforeNogoods = new Assignment();
		prevLocalViewBeforeNogoods = this.localView.copy();

		double maximalCostThreshold = prevRealStateLocalView.calcCost(this.getProblem())
				+ this.getCurrentBudget(prevRealStateLocalView);
		double currentCost = this.localView.calcCost(this.getProblem());
		while (currentCost > maximalCostThreshold) {
			Assignment onlyNeighborsLv = new Assignment();
			onlyNeighborsLv = this.localView.copy();
			onlyNeighborsLv.unassign(this.getId());

			Object assignedNeighbors[] = onlyNeighborsLv.assignedVariables().toArray();
			int rnd = new Random().nextInt(assignedNeighbors.length);
			Object rndNeighbor = assignedNeighbors[rnd];
			int rndNeighborVal = this.localView.getAssignment((Integer) rndNeighbor);
			this.localView.unassign((Integer) rndNeighbor);
			send("noGood", this.getId(), rndNeighborVal).to((Integer) rndNeighbor);

			// updateing NG-Store
			Hashtable<Integer, Boolean> neighborNG_store = new Hashtable<Integer, Boolean>();
			neighborNG_store = NG_store.get(rndNeighbor);
			neighborNG_store.put(rndNeighborVal, true);
			NG_store.put((Integer) rndNeighbor, neighborNG_store);

			currentCost = this.localView.calcCost(this.getProblem());
		}
	}

	private boolean isTheBestLrIsMine() {
		for (int neighborId : neighborsLrs.keySet()) {
			double neighborLr = neighborsLrs.get(neighborId);
			// if(this.getId()==0 || this.getId()==63 || this.getId()==36)
			// System.out.println(getSystemTimeInTicks()+" "+this.getId()+" "+myLr+" "
			// +neighborsLrs);
			if (myLr < neighborLr || ((myLr == neighborLr) && (this.getId() < neighborId))) {
				// if (myLr < neighborLr ) {
				return false;
			}
		}
		// System.out.println(this.getId()+" "+ myLr);
		return true;
	}

	private void findBestAssignmnetAndSendToNeighbors() {
		myCurrentRequest = (int) (realStateLocalView.findMinimalCostValue(getId(), getCurrentDomain(), getProblem()));

		myLr = realStateLocalView.calcCost(this.getProblem())
				- realStateLocalView.calcAddedCost(this.getId(), myCurrentRequest, this.getProblem());

		if (myCurrentRequest != this.getSubmitedCurrentAssignment()) {
			canImprove = true;
			send("Lr", this.getId(), myLr).toNeighbores();
		}
	}

	private HashSet getCurrentDomain() {
		int domainSize = this.getDomainSize();
		HashSet currentDomain = new HashSet(getDomain());

		for (int val = 0; val < domainSize; val++) {
			if (elim.get(val) > 0) {
				currentDomain.remove(val);
			}
		}

		return currentDomain;
	}

	@SuppressWarnings("deprecation")
	private void checkNgStoreAndSendGoodsMessages() {
		for (int neighborId : this.getNeighbors()) {
			for (int val : this.getDomain()) {
				if (NG_store.get(neighborId).get(val) == true) {
					Assignment possibleLocalView = new Assignment();
					possibleLocalView = this.realStateLocalView.copy();
					possibleLocalView.assign(neighborId, val);
					double possibleCost = possibleLocalView.calcCost(this.getProblem());
					if (possibleCost < this.getCurrentBudget(this.prevRealStateLocalView)) {

						send("good", this.getId(), val).to(neighborId);

						Hashtable<Integer, Boolean> neighborNG_store = new Hashtable<Integer, Boolean>();
						neighborNG_store = NG_store.get(neighborId);
						neighborNG_store.put(neighborId, false);
						NG_store.put((Integer) neighborId, neighborNG_store);

					}
				}

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

}