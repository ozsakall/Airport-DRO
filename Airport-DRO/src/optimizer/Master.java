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
	public static Map<data.Technology, Double> sol_c = new HashMap<data.Technology, Double>();
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
				IloColumn column = mastcplex.column(total_cost, tec.invCost);
				tec.c = mastcplex.numVar(column, 0, Double.MAX_VALUE, "c."+tec.name);
			}
			/* Capacity Constraints */
			for(data.Technology tec : data.all_technologies) {
				row_capacity.put(tec, mastcplex.addRange(tec.lb, tec.c, tec.ub, "tec."+tec.name));
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

			for(data.Technology tec : data.all_technologies)
				num_expr.addTerm((prob[s.id] * tec.beta.get(s)), tec.c);
		}
		num_expr.addTerm(1.0, theta);
		row_cut.add(mastcplex.addRange(lhs, num_expr, Double.MAX_VALUE, "opt."+row_cut.size()));
	}
	
	public static void solve(int iter) {
		try {
			if(mastcplex.solve()) {
				objValue = mastcplex.getObjValue();
				for(data.Technology tec : data.all_technologies) sol_c.put(tec, mastcplex.getValue(tec.c)); 
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
		for (data.Technology tech : data.all_technologies)
			System.out.println(tech.name+", Cost: "+ Math.round(tech.invCost*sol_c.get(tech)*100.00)/100.00+", Cap: "+ Math.round(sol_c.get(tech)*100.00)/100.00);
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
