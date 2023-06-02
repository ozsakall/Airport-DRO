package optimizer;

import ilog.cplex.*;
import ilog.concert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.crypto.Data;

import data.data;

public class Sub {
	public static double[] objValue;
	public static IloCplex SubCplex;
	private static IloObjective total_cost;
	public static double ch_time = 0;

	private static Map<data.Node, Map<Integer, IloNumVar[]>> f;
	
	private static Map<data.Technology, IloRange[]> row_capacity = new HashMap<data.Technology, IloRange[]>();
	private static Map<data.Demand, IloRange[]> row_demand = new HashMap<data.Demand, IloRange[]>();
	private static IloRange[] row_solar_factor = new IloRange[data.TimePeriod];
	private static IloRange[] row_electrolyzer = new IloRange[data.TimePeriod];
	private static IloRange[] row_bss = new IloRange[data.TimePeriod];
	private static IloRange[] row_hss = new IloRange[data.TimePeriod];
	private static IloRange[] row_fc = new IloRange[data.TimePeriod];
	private static IloRange[] row_initials = new IloRange[2]; 

	public static double[] alpha = new double[data.numScenarios];
	
	public Sub() {
		GenModel();
	}
	public static void GenModel() {
		try {
			SubCplex = new IloCplex();
			SubCplex.setOut(null);
			total_cost = SubCplex.addMinimize();
			objValue = new double[data.numScenarios];
			
			SubCplex.setParam(IloCplex.IntParam.RootAlg,IloCplex.Algorithm.Dual);
			//SubCplex.setParam(IloCplex.BooleanParam.PreInd, false);
			
			/* Decision Variables */
			f = new HashMap<data.Node, Map<Integer, IloNumVar[]>>();
			for(data.Node in : data.all_nodes) {
				if(in.outgoing.size() > 0) {
					f.put(in, new HashMap<Integer, IloNumVar[]>());
					for(Integer out : in.outgoing) {
						f.get(in).put(out, new IloNumVar[data.TimePeriod]);
						for(int t = 0; t < data.TimePeriod; t++) {
							IloColumn column = SubCplex.column(total_cost, 0);
							f.get(in).get(out)[t] = SubCplex.numVar(column, 0, Double.MAX_VALUE, "f."+in.name+"."+data.all_nodes.get(out).name+"."+t);	
							}
						}
					}
				}
			/* Constraints */
			/* capacity */
			cons_capacity();
			/* demand */
			cons_demand();
			/* solar output*/
			cons_solar();
			/* electrolyzer output*/
			cons_electrolyzer();
			/* batery storage system*/
			cons_bss();
			/* h2 storage system*/
			cons_hss();
			/* fuel cell output */
			cons_fc(); 
			/* initialization */
			cons_initials();
		}
		catch(IloException e){
			System.err.println("Concert exception caught: " + e);
		}
	}
	/* capacity constraint */
	private static void cons_capacity() {
		try {
			IloLinearNumExpr num_expr = SubCplex.linearNumExpr();
			for(data.Technology tec : data.all_technologies) {
				row_capacity.put(tec, new IloRange[data.TimePeriod]);
				for(int t = 0; t < data.TimePeriod; t++) {
					for(Integer out : tec.outgoing) num_expr.addTerm(1.0, f.get(tec).get(out)[t]);
					double cap = 0;
					
					int period = (int) Math.floor(1.0 * t / data.investmentPeriod);

					for(int i = 0; i <= period; i++) 
						cap += Master.sol_c.get(tec)[i];
					row_capacity.get(tec)[t] = SubCplex.addRange(-Double.MAX_VALUE, num_expr, cap, "tech_cap."+tec.id);	
					num_expr.clear();
				}
			}
		}
		catch(IloException e) {
			System.err.println("Concert exception caught: " + e);
		}
	}
	/* demand */
	private static void cons_demand() {
		try {
			IloLinearNumExpr num_expr = SubCplex.linearNumExpr();
			for(data.Demand demnd : data.all_demands) {
				row_demand.put(demnd, new IloRange[data.TimePeriod]);
				for(int t = 0; t < data.TimePeriod; t++) {
					for(Integer in : demnd.incoming) {
						data.Node node = data.all_nodes.get(in);
						num_expr.addTerm(1.0, f.get(node).get(demnd.id)[t]);
					}
					row_demand.get(demnd)[t] = SubCplex.addRange(0, num_expr, Double.MAX_VALUE, "demand."+demnd.id);
					num_expr.clear();
				}
			}
		}
		catch(IloException e) {
			System.err.println("Concert exception caught: " + e);
		}
	}
	/*solar output*/
	private static void cons_solar() {
		try {
			IloLinearNumExpr num_expr = SubCplex.linearNumExpr();
			data.Node solar = data.all_nodes.get(1);
			data.Node bss = data.all_nodes.get(2);
			
			for(int t = 0; t < data.TimePeriod; t++) {
				for(Integer out : solar.outgoing)
					num_expr.addTerm(1.0, f.get(solar).get(out)[t]);
				
				double cap = 0;
				int period = (int) Math.floor(1.0 * t / data.investmentPeriod);

				for(int i = 0; i <= period; i++) 
					cap += Master.sol_c.get(solar)[i];
				
				row_solar_factor[t] = SubCplex.addRange(-Double.MAX_VALUE, num_expr, cap, "solar_output."+t);
				//row_solar_factor[t] = SubCplex.addEq(num_expr,  Master.sol_c.get(solar), "solar_output."+t);
				num_expr.clear();
			}
		}
		catch(IloException e) {
			System.err.println("Concert exception caught: " + e);
		}
	}
	/*battery storage system*/
	private static void cons_bss() {
		try {
			IloLinearNumExpr num_expr = SubCplex.linearNumExpr();
			data.Node bss = data.all_nodes.get(2);
			
			for(int t = 0; t < data.TimePeriod - 1; t++) {
				num_expr.addTerm(1.0, f.get(bss).get(bss.id)[t + 1]);
				num_expr.addTerm(-1.0, f.get(bss).get(bss.id)[t]);
				
				for(Integer in : bss.incoming) {
					data.Node _node = data.all_nodes.get(in);
					num_expr.addTerm(-1.0, f.get(_node).get(bss.id)[t]);
				}
				for(Integer out : bss.outgoing) {
					if(data.all_nodes.get(out).name != "bss")
						num_expr.addTerm(1.0, f.get(bss).get(out)[t]);
				}
				row_bss[t] = SubCplex.addEq(num_expr, 0.0, "bss."+(t+1));
				num_expr.clear();
			}
		}
		catch(IloException e) {
			System.err.println("Concert exception caught: " + e);
		}
	}
	/*electrolyzer output*/
	private static void cons_electrolyzer() {
		try {
			IloLinearNumExpr num_expr = SubCplex.linearNumExpr();
			data.Node elect = data.all_nodes.get(3);
			data.Node hss = data.all_nodes.get(4);
			
			for(int t = 0; t < data.TimePeriod; t++) {
				num_expr.addTerm(1.0, f.get(elect).get(hss.id)[t]);
				
				for(Integer in : elect.incoming) {
					data.Node _node = data.all_nodes.get(in);
					num_expr.addTerm(-data.effc, f.get(_node).get(elect.id)[t]);
				}
				row_electrolyzer[t] = SubCplex.addEq(num_expr, 0.0, "electrolyzer_output."+t);
				num_expr.clear();
			}
		}
		catch(IloException e) {
			System.err.println("Concert exception caught: " + e);
		}
	}
	/*h2 storage system*/
	private static void cons_hss() {
		try {
			IloLinearNumExpr num_expr = SubCplex.linearNumExpr();
			data.Node hss = data.all_nodes.get(4);
			
			for(int t = 0; t < data.TimePeriod - 1; t++) {
				num_expr.addTerm(1.0, f.get(hss).get(hss.id)[t + 1]);
				num_expr.addTerm(-1.0, f.get(hss).get(hss.id)[t]);
				
				for(Integer in : hss.incoming) {
					data.Node _node = data.all_nodes.get(in);
					num_expr.addTerm(-1.0, f.get(_node).get(hss.id)[t]);
				}
				for(Integer out : hss.outgoing) {
					if(data.all_nodes.get(out).name != "hss")
						num_expr.addTerm(1.0, f.get(hss).get(out)[t]);
				}
				row_hss[t] = SubCplex.addEq(num_expr, 0.0, "hss."+(t+1));
				num_expr.clear();
			}
		}
		catch(IloException e) {
			System.err.println("Concert exception caught: " + e);
		}
	}
	/* fuel cell flow*/
	private static void cons_fc() {
		try {
			IloLinearNumExpr num_expr = SubCplex.linearNumExpr();
			data.Node hss = data.all_nodes.get(4);
			data.Node fc = data.all_nodes.get(5);
			
			for(int t = 0; t < data.TimePeriod ; t++) {
				for(Integer out : fc.outgoing)
					num_expr.addTerm(1.0, f.get(fc).get(out)[t]);
				
				num_expr.addTerm(-data.beta, f.get(hss).get(fc.id)[t]);
				
				row_fc[t] = SubCplex.addEq(num_expr, data.alpha, "fc."+(t));
				num_expr.clear();
			}
		}
		catch(IloException e) {
			System.err.println("Concert exception caught: " + e);
		}
	}
	/* bss and hss initialization */

	private static void cons_initials() {
		try {
			IloLinearNumExpr num_expr = SubCplex.linearNumExpr();
			data.Node bss = data.all_nodes.get(2);
			data.Node hss = data.all_nodes.get(4);

			//bss;
			num_expr.addTerm(1.0, f.get(bss).get(bss.id)[0]);
			row_initials[0] = SubCplex.addEq(num_expr, 0.0, "bss initial");
			num_expr.clear();
			
			//hss;
			num_expr.addTerm(1.0, f.get(hss).get(hss.id)[0]);
			row_initials[1] = SubCplex.addEq(num_expr, 0.0, "hss initial");
			num_expr.clear();
		}
		catch(IloException e) {
			System.err.println("Concert exception caught: " + e);
		}
	}
	public static void solve(data.Scenario sc) {
		try {
			double a = System.nanoTime();
			change_scenarios(sc);
			ch_time += System.nanoTime() - a;
			if(SubCplex.solve()) {

				objValue[sc.id] = SubCplex.getObjValue();
				//write_solution();
				calculateAlpha(sc);
				calculateBeta(sc);
			}
			else
				System.err.println("infeasible solution");
			
		}
		catch(IloException e) {
			System.err.println("Concert exception caught: " + e);
		}	
	}
	public static void change_scenarios(data.Scenario scenario) {
		try {
			IloLinearNumExpr num_expr = SubCplex.linearNumExpr();
			data.Node solar = data.all_nodes.get(data.key_id.get("solar"));
			
			data.Node grid = data.all_nodes.get(data.key_id.get("grid"));
			data.Node sell_elec = data.all_nodes.get(data.key_id.get("sell"));
			int ind = sell_elec.incoming.get(0);
			data.Node sell_node = data.all_nodes.get(ind);
			
			data.Node h2 = data.all_nodes.get(data.key_id.get("h2"));
			
			for(int t = 0; t < data.TimePeriod; t++) {
				//cost
				num_expr.addTerm(data.buy_h2, f.get(h2).get(h2.outgoing.get(0))[t]);
				for(Integer out : grid.outgoing)
					num_expr.addTerm(scenario.electrictyCost, f.get(grid).get(out)[t]);
				
				num_expr.addTerm(-data.sell_elec, f.get(sell_node).get(sell_elec.id)[t]);
				//demand constraint
				for(data.Demand demnd : data.all_demands) {
					row_demand.get(demnd)[t].setLB(scenario.demand.get(demnd)[t]);
				}
				/* solar output */
				double solar_inv = 0;
				int period = (int) Math.floor(1.0 * t / data.investmentPeriod);

				for(int i = 0; i <= period; i++) { 
					solar_inv += Master.sol_c.get(solar)[i];
					double bound = solar_inv*scenario.pvFactor[t];
					row_solar_factor[t].setBounds(-Double.MAX_VALUE, bound);
				}
			}
			total_cost.setExpr(num_expr);
		}
		catch(IloException e){
			System.err.println("Concert exception caught: " + e);
		}
	}

	public static void change_capacity() {
		try {
			/*master solution tech cap*/
			for(data.Technology tec : data.all_technologies) {
				for(int t = 0; t < data.TimePeriod; t++) {
					double cap = 0;
					int period = (int) Math.floor(1.0 * t / data.investmentPeriod);

					for(int i = 0; i <= period; i++) 
						cap += Master.sol_c.get(tec)[i];
					
					row_capacity.get(tec)[t].setUB(cap); 
				}
			}
		}
		catch(IloException e){
			System.err.println("Concert exception caught: " + e);
		}
	}
	private static void calculateAlpha(data.Scenario scenario) throws IloException {

		double _alphaValue = 0;
		for(int t = 0; t < data.TimePeriod; t++) {
			for(data.Demand demnd : data.all_demands) {
				double dmnd = row_demand.get(demnd)[t].getLB();
				double dual = SubCplex.getDual(row_demand.get(demnd)[t]);
				_alphaValue += dmnd * dual;
			}
			_alphaValue += data.alpha * SubCplex.getDual(row_fc[t]);
		}
		alpha[scenario.id] = _alphaValue;
	}
	private static void calculateBeta(data.Scenario scenario) {
		try {
			for(data.Technology tec : data.all_technologies) {
				tec.beta.put(scenario, new double[data.totalInvestment]);
				for(int i = 0; i < data.totalInvestment; i++) {
					double _beta = 0;
					int lb = i * data.investmentPeriod;
					int ub = (i + 1) * data.investmentPeriod;
					ub = (ub > data.TimePeriod) ? data.TimePeriod : ub;

					for(int t = lb; t < ub; t++) {
						_beta += SubCplex.getDual(row_capacity.get(tec)[t]);
						if(tec.name == "solar")
							_beta += scenario.pvFactor[t] * SubCplex.getDual(row_solar_factor[t]);
					}
					tec.beta.get(scenario)[i] = -_beta; 
					
				}
				
			}
			/*Iterator it = SubCplex.rangeIterator();
			double obj = 0;
			while (it.hasNext()) {
				  IloRange r = (IloRange) it.next();
				  double rhs = r.getUB() < Double.MAX_VALUE ? r.getUB() : r.getLB();
				  System.out.println(r.getName()+" Rhs: "+rhs+ " Dual: "+SubCplex.getDual(r) );
				  obj += rhs * SubCplex.getDual(r);
			}
			System.out.println("Obj: "+obj);*/
		}
		catch(IloException e){
			System.err.println("Concert exception caught: " + e);
		}
	}
	public static void write_solution() {
		try {

			for(int t = 0; t < data.TimePeriod; t++) {
				for(data.Node i : data.all_nodes) {
				for(Integer ind : i.outgoing) {
					data.Node j = data.all_nodes.get(ind);
						if(f.get(i).get(j.id) != null) {
							if(SubCplex.getValue(f.get(i).get(j.id)[t])>0) 
								System.out.println(i.name+" to "+j.name+" "+t+" = "+Math.round(SubCplex.getValue(f.get(i).get(j.id)[t])*100.0)/ 100.0);
						}
					}
					
				}
			}
			System.out.println();
		}
		catch(IloException e){
			System.err.println("Concert exception caught: " + e);
		}
	}
	public static void check() {
		try {
			data.Node sell_elec = data.all_nodes.get(data.key_id.get("sell"));
			int ind = sell_elec.incoming.get(0);
			data.Node sell_node = data.all_nodes.get(ind );
			data.Technology bss = data.all_technologies.get(1);
			
			for(int t = 0; t < data.TimePeriod; t++) {
				if(SubCplex.getValue(f.get(sell_node).get(ind)[t])>0 && SubCplex.getValue(f.get(sell_node).get(ind)[t]) > Master.sol_c.get(bss)[t]) 
					System.out.println(Math.round(SubCplex.getValue(f.get(sell_node).get(ind)[t])*100.0)/ 100.0);
			}
			System.out.println();
		}
		catch(IloException e){
			System.err.println("Concert exception caught: " + e);
		}
	}
}
