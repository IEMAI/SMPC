/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ext.sim.modules;

//package ext.sim.tools;

import java.sql.ResultSet;
import java.sql.SQLException;

import bgu.dcr.az.api.Agent;
import bgu.dcr.az.api.Hooks;
import bgu.dcr.az.api.ano.Register;
import bgu.dcr.az.api.ano.Variable;
import bgu.dcr.az.api.exen.Execution;
import bgu.dcr.az.api.exen.SystemClock;
import bgu.dcr.az.api.exen.Test;
import bgu.dcr.az.api.exen.stat.DBRecord;
import bgu.dcr.az.api.exen.stat.Database;
import bgu.dcr.az.api.exen.stat.VisualModel;
import bgu.dcr.az.api.exen.stat.vmod.LineVisualModel;
import bgu.dcr.az.api.prob.Problem;
import bgu.dcr.az.api.tools.Assignment;
import bgu.dcr.az.exen.stat.AbstractStatisticCollector;

@Register(name="anytime-pt-sc")
public class AnytimePerTickSC extends AbstractStatisticCollector<AnytimePerTickSC.AnytimeDBRecord> {

	private long currBestCost;
	private long numChanges;
	private long timeSinceLastChange;
	private int numNonZeroConstraints;

	public static enum Type { COST, ANYTIME_COST, NUM_CHANGES, TIME_SINCE_CHANGE, TIME_OF_CHANGE, NUM_NZ, AVG_NZ_COST }

	@Variable(name="type", description="Type of statistic to show [COST | ANYTIME_COST | NUM_CHANGES | TIME_SINCE_CHANGE | TIME_OF_CHANGE | NUM_NZ | AVG_NZ_COST]", defaultValue="ANYTIME_COST")
	Type type = Type.ANYTIME_COST;
	
	public static class AnytimeDBRecord extends DBRecord {
		final int probNum;
		final long tick;
		final long currCost;
		final long bestCost;
		final long numChanges;
		final long timeSinceLastChange;
		final int numNonZeroConstraints;
		
		AnytimeDBRecord(int probNum, long tick, long currCost, long bestCost, long numChanges, long timeSinceLastChange, int numNonZeroConstraints) {
			this.probNum = probNum;
			this.tick = tick;
			this.currCost = currCost;
			this.bestCost = bestCost;
			this.numChanges = numChanges;
			this.timeSinceLastChange = timeSinceLastChange;
			this.numNonZeroConstraints = numNonZeroConstraints;
		}
		
		@Override
		public String provideTableName() {
			return "ANYTIME_COST";
		}
		
	}
		

	public VisualModel analyze(Database db, Test r) {
		final String query;
		final LineVisualModel line;
		switch (type) {
		case COST:
			query="select ALGORITHM_INSTANCE, TICK, AVG(CAST(CURRCOST as DOUBLE)) as AVG_COST from ANYTIME_COST group by ALGORITHM_INSTANCE,TICK order by TICK";
			line = new LineVisualModel("time", "Solution Cost", "Current Solution Cost");
			try {
				ResultSet rs = db.query(query);
				while (rs.next()) {
					line.setPoint(rs.getString("ALGORITHM_INSTANCE"), rs.getInt("TICK"), rs.getFloat("AVG_COST"));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return line;
		case ANYTIME_COST: 
			query="select ALGORITHM_INSTANCE, TICK, AVG(CAST(BESTCOST as DOUBLE)) as AVG_BEST_COST from ANYTIME_COST group by ALGORITHM_INSTANCE,TICK order by TICK";
			line = new LineVisualModel("time", "Anytime Solution Cost", "Anytime Solution Cost");
			try {
				ResultSet rs = db.query(query);
				while (rs.next()) {
					line.setPoint(rs.getString("ALGORITHM_INSTANCE"), rs.getInt("TICK"), rs.getFloat("AVG_BEST_COST"));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return line;
		case NUM_CHANGES:
			query="select ALGORITHM_INSTANCE, TICK, AVG(CAST(NUMCHANGES as DOUBLE)) as AVG_NUM_CHANGES from ANYTIME_COST group by ALGORITHM_INSTANCE,TICK order by TICK";
			line = new LineVisualModel("time", "Number of Changes", "Number of Changes to Anytime Solution");
			try {
				ResultSet rs = db.query(query);
				while (rs.next()) {
					line.setPoint(rs.getString("ALGORITHM_INSTANCE"), rs.getInt("TICK"), rs.getFloat("AVG_NUM_CHANGES"));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return line;
		case TIME_SINCE_CHANGE:
			query="select ALGORITHM_INSTANCE, TICK, AVG(CAST(TIMESINCELASTCHANGE as DOUBLE)) as AVG_TIME_SINCE_CHANGE from ANYTIME_COST group by ALGORITHM_INSTANCE,TICK order by TICK";
			line = new LineVisualModel("time", "Time Since Last Change", "Time Since Last Change to Anytime Solution");
			try {
				ResultSet rs = db.query(query);
				while (rs.next()) {
					line.setPoint(rs.getString("ALGORITHM_INSTANCE"), rs.getInt("TICK"), rs.getFloat("AVG_TIME_SINCE_CHANGE"));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return line;
		case TIME_OF_CHANGE:
			query="select ALGORITHM_INSTANCE, TICK, AVG(TICK - CAST(TIMESINCELASTCHANGE as DOUBLE)) as AVG_TIME_OF_CHANGE from ANYTIME_COST group by ALGORITHM_INSTANCE,TICK order by TICK";
			line = new LineVisualModel("time", "Time of Last Change", "Time of Last Change to Anytime Solution");
			try {
				ResultSet rs = db.query(query);
				while (rs.next()) {
					line.setPoint(rs.getString("ALGORITHM_INSTANCE"), rs.getInt("TICK"), rs.getFloat("AVG_TIME_OF_CHANGE"));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return line;
		case NUM_NZ:
			query="select ALGORITHM_INSTANCE, TICK, AVG(CAST(NUMNONZEROCONSTRAINTS as DOUBLE)) as AVG_NUM_NZ from ANYTIME_COST group by ALGORITHM_INSTANCE,TICK order by TICK";
			line = new LineVisualModel("time", "Number of NZ Constraints", "Number of Non-Zero Constraints");
			try {
				ResultSet rs = db.query(query);
				while (rs.next()) {
					line.setPoint(rs.getString("ALGORITHM_INSTANCE"), rs.getInt("TICK"), rs.getFloat("AVG_NUM_NZ"));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return line;
		case AVG_NZ_COST:
			query="select ALGORITHM_INSTANCE, TICK, AVG(CASE NumNonZeroConstraints WHEN 0 THEN 0.0 ELSE BESTCOST / CAST(NUMNONZEROCONSTRAINTS as DOUBLE) END) as AVG_NZ_COST from ANYTIME_COST group by ALGORITHM_INSTANCE,TICK order by TICK";
			line = new LineVisualModel("time", "Non-Zero Constraint Cost", "Cost of Non-Zero Constraints");
			try {
				ResultSet rs = db.query(query);
				while (rs.next()) {
					line.setPoint(rs.getString("ALGORITHM_INSTANCE"), rs.getInt("TICK"), rs.getFloat("AVG_NZ_COST"));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return line;			
		default:
			throw new AssertionError("Unknown type \"" + type + "\"!");
		}
	}


	public void hookIn(final Agent[] agents, final Execution ex) {
		currBestCost = Long.MAX_VALUE;
		numChanges = 0;
		timeSinceLastChange = 0;
		numNonZeroConstraints = 0;
		new Hooks.TickHook() {
			@Override
			public void hook(SystemClock clock) {
				long time = clock.time();
				Problem prob = ex.getGlobalProblem();
				Assignment ass = ex.getResult().getAssignment();
				long cost = ass.calcCost(prob);
				boolean change = false;
				if (cost < currBestCost) {
					currBestCost = cost;
					numChanges++;
					numNonZeroConstraints = 0;
					for (int i = 0; i < agents.length; i++) {
						for (int j = i + 1; j < agents.length; j++) {
							if (prob.isConstrained(i, j) && prob.getConstraintCost(i, ass.getAssignment(i), j, ass.getAssignment(j)) > 0) {
								numNonZeroConstraints++;
							}
						}
					}
					
					change = true;
				}
				// always increment this so the record is how long it was BEFORE this step
				timeSinceLastChange++;
				AnytimeDBRecord rec = new AnytimeDBRecord(ex.getTest().getCurrentProblemNumber(), time, cost, currBestCost, numChanges, timeSinceLastChange, numNonZeroConstraints);
				submit(rec);
				// and now if we changed, reset the counter
				if (change) {
					timeSinceLastChange = 0;
				}
			}
		}.hookInto(ex);
	}


	public String getName() {
		return "Anytime Cost per Tick";
	}

}
