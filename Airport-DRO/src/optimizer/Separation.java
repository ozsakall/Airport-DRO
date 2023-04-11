package optimizer;

import data.data;
import ilog.concert.*;
import ilog.cplex.*;

public class Separation {
	public static double[] objValue;
	public static IloCplex sepCplex;
	private static IloObjective total_cost;
	private static IloNumVar[][] v;

	public static void GenModel() {
		try {
			sepCplex = new IloCplex();
			sepCplex.setOut(null);
			
			total_cost = sepCplex.addMaximize();
			objValue = new double[data.numScenarios];
			v = new IloNumVar[data.numScenarios][];
			
			/* Decision Variables */
			for(data.Scenario sc : data.all_scenarios) {
						IloColumn column = sepCplex.column(total_cost, 0);
						sc.probVar = sepCplex.numVar(column, 0, Double.MAX_VALUE, "prob."+sc.id);	

						v[sc.id] = new IloNumVar[data.numScenarios];
						for(data.Scenario sc2 : data.all_scenarios) {
							v[sc.id][sc2.id] = sepCplex.numVar(0, Double.MAX_VALUE, "v."+sc.id+"."+sc2.id);
						}
				}
			
			/* Constraints */
			/* distance for pv factor */
			IloLinearNumExpr num_expr = sepCplex.linearNumExpr();
			for(data.Scenario i : data.all_scenarios) {
				for(data.Scenario j : data.all_scenarios) {
					double dist = 0;
					for(int t = 0; t < data.TimePeriod; t++)
						dist += Math.abs(i.Ref_pvFactor[t]- j.pvFactor[t]);
					num_expr.addTerm(dist, v[i.id][j.id]);
				}
			}
			sepCplex.addRange(-Double.MAX_VALUE, num_expr, data.rds, "dist");	
			num_expr.clear();

			/* distance for demand */
			data.Demand d1 = data.all_demands.get(0);
			num_expr = sepCplex.linearNumExpr();
			for(data.Scenario i : data.all_scenarios) {
				for(data.Scenario j : data.all_scenarios) {
					double dist = 0;
					for(int t = 0; t < data.TimePeriod; t++)
						dist += Math.abs(i.Ref_demand.get(d1)[t]- j.Ref_demand.get(d1)[t]);
					num_expr.addTerm(dist, v[i.id][j.id]);
				}
			}
			sepCplex.addRange(-Double.MAX_VALUE, num_expr, data.rds, "dist");	
			num_expr.clear();
			
			/* v_i_j = prob_i */
			for(data.Scenario i : data.all_scenarios) {
				for(data.Scenario j : data.all_scenarios) {
					num_expr.addTerm(-1.0, v[i.id][j.id]);
				}
				num_expr.addTerm(1.0, i.probVar);
				sepCplex.addRange(0, num_expr, 0, "prob1."+i.id);
				num_expr.clear();
			}
			
			/* v_i_j = prob_j */
			for(data.Scenario j : data.all_scenarios) {
				for(data.Scenario i : data.all_scenarios) {
					num_expr.addTerm(1.0, v[i.id][j.id]);
				}
				sepCplex.addRange(j.probability, num_expr, j.probability, "prob2."+j.id);
				num_expr.clear();
			}
			
			/*prob = 1*/
			for(data.Scenario i : data.all_scenarios) {
				num_expr.addTerm(1.0, i.probVar);
			}
			sepCplex.addRange(1, num_expr, 1, "equal to 1" );
			num_expr.clear();
		}
		catch(IloException e){
			System.err.println("Concert exception caught: " + e);
		}
	}
	public static void changeObj() {
		try {
			IloLinearNumExpr num_expr = sepCplex.linearNumExpr();
			for(data.Scenario i : data.all_scenarios) {
				num_expr.addTerm(Sub.objValue[i.id], i.probVar);
				}
			total_cost.setExpr(num_expr);	
		}
		catch(IloException e){
			System.err.println("Concert exception caught: " + e);
		}				
	}
	public static double[] solve() {
		try {
			double[] prob = new double[data.numScenarios];
			changeObj();
			if(sepCplex.solve()) {
				
				for(data.Scenario sc : data.all_scenarios) {
					prob[sc.id] = sepCplex.getValue(sc.probVar);	
				}
				return prob;
			}
			else return null;
		}
		catch(IloException e){
			System.err.println("Concert exception caught: " + e);
			return null;
		}
	}
}
