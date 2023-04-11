package full_model;

import data.data;
import java.util.Map;
import ilog.concert.*;
import ilog.cplex.*;

public class fmodel {

	public static double[] objValue;
	public static IloCplex cplex;
	private static IloObjective total_cost;

	private static Map<data.Node, Map<Integer, IloNumVar[]>> f;
	private static IloNumVar[] c;
	

	public static void full_model() {
		create_model();
		solve();
	}
	public static void create_model() {
		
	}
	public static void solve() {
		
	}
}
