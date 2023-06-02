package optimizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import data.data;
import ilog.cplex.*;
import ilog.concert.*;

public class Master {
	public static double objValue;
	public static IloCplex mastcplex;
	private static IloObjective total_cost;
	private static IloNumVar theta; 
	public static Map<data.Technology, double[]> sol_c = new HashMap<data.Technology, double[]>();
	public static double sol_theta;
	private static Map<data.Technology, IloRange> row_capacity = new HashMap<data.Technology, IloRange>();
	private static List<IloRange> row_cut = new ArrayList<IloRange>();
	
	
	public static void GenModel() {
		try {
			mastcplex = new IloCplex();
			mastcplex.setOut(null);
			total_cost = mastcplex.addMinimize();
			
			/*Decision variables*/
			for(data.Technology tec : data.all_technologies) {
				tec.c = new IloNumVar[data.totalInvestment];
				for(int t = 0; t < data.totalInvestment; t++) {
					IloColumn column = mastcplex.column(total_cost, tec.invCost * (t+10) /* (1/(t+1))*/);
					tec.c[t] = mastcplex.numVar(column, 0, Double.MAX_VALUE, "c."+tec.name+"."+t);
				}
			}
			/* Capacity Constraints */
			for(data.Technology tec : data.all_technologies) {
				IloLinearNumExpr num_expr = mastcplex.linearNumExpr();
				for(int t = 0; t < data.totalInvestment; t++) {
					num_expr.addTerm(1.0, tec.c[t]);
				}
				row_capacity.put(tec, mastcplex.addRange(tec.lb, num_expr, tec.ub, "tec."+tec.name));
				num_expr.clear();
			}
		}
		catch(IloException e){
			System.err.println("Concert exception caught: " + e);
		}
	}

	public static void addColumn() throws Exception
	{
		Master.total_cost =  mastcplex.getObjective();
        IloColumn col = mastcplex.column(Master.total_cost, 1.0);
        theta = mastcplex.numVar(col,-Double.MAX_VALUE, Double.MAX_VALUE,"theta");
	}
	
	public static void addOptCut(double[] prob) throws IloException {
		double lhs = 0;
		IloLinearNumExpr num_expr = mastcplex.linearNumExpr();
		
		for(data.Scenario s : data.all_scenarios) {
			lhs += prob[s.id] * Sub.alpha[s.id];

			for(data.Technology tec : data.all_technologies) {
				for(int t = 0; t < data.totalInvestment; t++)
					num_expr.addTerm((prob[s.id] * tec.beta.get(s)[t]), tec.c[t]);
			}
		}
		num_expr.addTerm(1.0, theta);
		row_cut.add(mastcplex.addRange(lhs, num_expr, Double.MAX_VALUE, "opt."+row_cut.size()));
	}
	
	public static void solve(int iter) {
		try {
			if(mastcplex.solve()) {
				objValue = mastcplex.getObjValue();
				for(data.Technology tec : data.all_technologies) {
					sol_c.put(tec, new double[data.totalInvestment]);
					
					for(int t = 0; t < data.totalInvestment; t++)
						sol_c.get(tec)[t] = mastcplex.getValue(tec.c[t]); 
				}
				if(theta != null)
					sol_theta = mastcplex.getValue(theta);
			}
			if(iter != 0)
				Sub.change_capacity();
		}
		catch(IloException e) {
			System.err.println("Concert exception caught: " + e);
		}
	}
	public static void write_solution(int iter) {
		System.out.println();
		System.out.println("Total Cost:" +getFSOj(iter)+ " theta: "+sol_theta);
		for (data.Technology tech : data.all_technologies) {
			for(int t = 0; t < data.totalInvestment; t++)
				System.out.println(tech.name+"."+t+", Cap: "+ Math.round(sol_c.get(tech)[t]*100.00)/100.00+", Cost: "+ Math.round(tech.invCost*sol_c.get(tech)[t]*100.00)/100.00);
		}
			
		System.out.println();
	}
	public static double getFSOj(int iter)
	{
		if(iter == 0)
			return objValue;
		else
			return objValue - sol_theta;	
	}
}
