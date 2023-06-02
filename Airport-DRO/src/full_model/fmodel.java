package full_model;

import data.data;

import java.util.HashMap;
import java.util.Map;
import ilog.concert.*;
import ilog.cplex.*;

public class fmodel {

	public static double objValue;
	public static IloCplex cplex;
	private static IloObjective total_cost;

	private static int sell_ind = 8;
	private static int h2_ind = 9;
	private static Map<data.Technology, IloNumVar[]> c = new HashMap<data.Technology, IloNumVar[]>();
	private static Map<data.Node, Map<Integer, IloNumVar[][]>> f;

	public static Map<data.Technology, double[]> sol_c = new HashMap<data.Technology, double[]>();
	

	public static void full_model() {
		GenModel();
		solve();
	}
	public static void GenModel() {
		try {
			cplex = new IloCplex();
			cplex.setOut(null);
			total_cost = cplex.addMinimize();
			
			/* Decision Variables */
			c = new HashMap<data.Technology, IloNumVar[]>();
			for(data.Technology tech : data.all_technologies) {
				c.put(tech, new IloNumVar[data.totalInvestment]);
				for(int i = 0; i < data.totalInvestment; i++) {
					IloColumn column = cplex.column(total_cost, tech.invCost * (i+10));
					c.get(tech)[i] = cplex.numVar(column, 0, Double.MAX_VALUE, "c."+tech.name+"."+i);
				}
			}
			f = new HashMap<data.Node, Map<Integer, IloNumVar[][]>>();
			for(data.Node in : data.all_nodes) {
				if(in.outgoing.size() > 0) {
					f.put(in, new HashMap<Integer, IloNumVar[][]>());
					for(Integer out : in.outgoing) {
						f.get(in).put(out, new IloNumVar[data.TimePeriod][]);
						for(int t = 0; t < data.TimePeriod; t++) {
							f.get(in).get(out)[t] = new IloNumVar[data.numScenarios];
							for(int s = 0; s < data.numScenarios; s++) {
								
								IloColumn column = cplex.column(total_cost, 0);
								f.get(in).get(out)[t][s] = cplex.numVar(column, 0, Double.MAX_VALUE, "f."+in.name+"."+data.all_nodes.get(out).name+"."+t+"."+s);	
							}
							}
						}
					}
				}

			IloLinearNumExpr num_expr = cplex.linearNumExpr();
			data.Technology solar = data.all_technologies.get(0);
			
			data.Node grid = data.all_nodes.get(0);
			data.Node sell_elec = data.all_nodes.get(sell_ind);
			int ind = sell_elec.incoming.get(0);
			data.Node sell_node = data.all_nodes.get(ind );
			
			data.Node h2 = data.all_nodes.get(h2_ind);
			num_expr= (IloLinearNumExpr) (total_cost.getExpr());
			for(int t = 0; t < data.TimePeriod; t++) {
				for(data.Scenario s : data.all_scenarios) {
					//cost
					num_expr.addTerm(data.buy_h2, f.get(h2).get(h2.outgoing.get(0))[t][s.id]);
					for(Integer out : grid.outgoing)
						num_expr.addTerm(s.electrictyCost, f.get(grid).get(out)[t][s.id]);
					
					num_expr.addTerm(-data.sell_elec, f.get(sell_node).get(sell_elec.id)[t][s.id]);
				}
			}
			total_cost.setExpr(num_expr);
			num_expr.clear();
			/* Constraints */
			/* capacity */
			for(data.Technology tec : data.all_technologies) {
				for(int t = 0; t < data.totalInvestment; t++) {
					num_expr.addTerm(1.0, c.get(tec)[t]);
				}
				cplex.addRange(tec.lb, num_expr, tec.ub, "tec."+tec.name);
				num_expr.clear();
			}

			for(data.Scenario s : data.all_scenarios) {
				for(data.Technology tec : data.all_technologies) {
					for(int t = 0; t < data.TimePeriod; t++) {
						for(Integer out : tec.outgoing) num_expr.addTerm(-1.0, f.get(tec).get(out)[t][s.id]);

						int period = (int) Math.floor(1.0 * t / data.investmentPeriod);
						for(int i = 0; i <= period; i++) 
							num_expr.addTerm(1.0, c.get(tec)[i]);
						
						cplex.addRange(0, num_expr, Double.MAX_VALUE, "tech_cap."+tec.id);	
						num_expr.clear();
					}
				}
			}
			/* demand */
			for(data.Scenario s : data.all_scenarios) {
				for(data.Demand demnd : data.all_demands) {
					for(int t = 0; t < data.TimePeriod; t++) {
						for(Integer in : demnd.incoming) {
							data.Node node = data.all_nodes.get(in);
							num_expr.addTerm(1.0, f.get(node).get(demnd.id)[t][s.id]);
						}
						cplex.addRange(s.demand.get(demnd)[t], num_expr, Double.MAX_VALUE, "demand."+demnd.id);
						num_expr.clear();
					}
				}
			}
			/* solar output*/
			data.Node bss = data.all_nodes.get(2);
			for(data.Scenario s : data.all_scenarios) {
				for(int t = 0; t < data.TimePeriod; t++) {
					for(Integer out : solar.outgoing)
						num_expr.addTerm(-1.0, f.get(solar).get(out)[t][s.id]);
					
					int period = (int) Math.floor(1.0 * t / data.investmentPeriod);
					for(int i = 0; i <= period; i++) 
						num_expr.addTerm(s.pvFactor[t], c.get(solar)[i]);
					
					cplex.addRange(0, num_expr,  Double.MAX_VALUE, "solar_output."+t);
					num_expr.clear();
				}
			}
			/* batery storage system*/
			for(data.Scenario s : data.all_scenarios) {
				for(int t = 0; t < data.TimePeriod - 1; t++) {
					num_expr.addTerm(1.0, f.get(bss).get(bss.id)[t + 1][s.id]);
					num_expr.addTerm(-1.0, f.get(bss).get(bss.id)[t][s.id]);
					
					for(Integer in : bss.incoming) {
						data.Node _node = data.all_nodes.get(in);
						num_expr.addTerm(-1.0, f.get(_node).get(bss.id)[t][s.id]);
					}
					for(Integer out : bss.outgoing) {
						if(data.all_nodes.get(out).name != "bss")
							num_expr.addTerm(1.0, f.get(bss).get(out)[t][s.id]);
					}
					cplex.addEq(num_expr, 0.0, "bss."+(t+1));
					num_expr.clear();
				}
			}
			/* electrolyzer output*/
			data.Node elect = data.all_nodes.get(3);
			data.Node hss = data.all_nodes.get(4);
			for(data.Scenario s : data.all_scenarios) {
				for(int t = 0; t < data.TimePeriod; t++) {
					num_expr.addTerm(1.0, f.get(elect).get(hss.id)[t][s.id]);
					
					for(Integer in : elect.incoming) {
						data.Node _node = data.all_nodes.get(in);
						num_expr.addTerm(-data.effc, f.get(_node).get(elect.id)[t][s.id]);
					}
					cplex.addEq(num_expr, 0.0, "electrolyzer_output."+t);
					num_expr.clear();
				}
			}
			/* h2 storage system*/	

			for(data.Scenario s : data.all_scenarios) {
				for(int t = 0; t < data.TimePeriod - 1; t++) {
					num_expr.addTerm(1.0, f.get(hss).get(hss.id)[t + 1][s.id]);
					num_expr.addTerm(-1.0, f.get(hss).get(hss.id)[t][s.id]);
					
					for(Integer in : hss.incoming) {
						data.Node _node = data.all_nodes.get(in);
						num_expr.addTerm(-1.0, f.get(_node).get(hss.id)[t][s.id]);
					}
					for(Integer out : hss.outgoing) {
						if(data.all_nodes.get(out).name != "hss")
							num_expr.addTerm(1.0, f.get(hss).get(out)[t][s.id]);
					}
					cplex.addEq(num_expr, 0.0, "hss."+(t+1));
					num_expr.clear();
				}
			}
			/* fuel cell output */
			data.Node fc = data.all_nodes.get(5);
			for(data.Scenario s : data.all_scenarios) {
				for(int t = 0; t < data.TimePeriod ; t++) {
					for(Integer out : fc.outgoing)
						num_expr.addTerm(1.0, f.get(fc).get(out)[t][s.id]);
					
					num_expr.addTerm(-data.beta, f.get(hss).get(fc.id)[t][s.id]);
					
					cplex.addEq(num_expr, data.alpha, "fc."+(t));
					num_expr.clear();
				}
			}
			/* initialization */
			for(data.Scenario s : data.all_scenarios) {
				num_expr.addTerm(1.0, f.get(bss).get(bss.id)[0][s.id]);
				cplex.addEq(num_expr, 0.0, "bss initial");
				num_expr.clear();
			}
			
			//hss;
			for(data.Scenario s : data.all_scenarios) {
				num_expr.addTerm(1.0, f.get(hss).get(hss.id)[0][s.id]);
				cplex.addEq(num_expr, 0.0, "hss initial");
				num_expr.clear();
			}
		}
		catch(IloException e){
			System.err.println("Concert exception caught: " + e);
		}
	}
	public static void solve() {
		try {
			if(cplex.solve()) {

				objValue = cplex.getObjValue();
				for(data.Technology tec : data.all_technologies) {
					sol_c.put(tec, new double[data.totalInvestment]);
					
					for(int t = 0; t < data.totalInvestment; t++)
						sol_c.get(tec)[t] = cplex.getValue(tec.c[t]); 
				}
				

				write_solution();
			}
			else
				System.err.println("infeasible solution");
			
		}
		catch(IloException e) {
			System.err.println("Concert exception caught: " + e);
		}	
	}

	public static void write_solution() {
		try {

			for (data.Technology tech : data.all_technologies) {
				for(int t = 0; t < data.totalInvestment; t++)
					System.out.println(tech.name+"."+t+", Cap: "+ Math.round(sol_c.get(tech)[t]*100.00)/100.00+", Cost: "+ Math.round(tech.invCost*sol_c.get(tech)[t]*100.00)/100.00);
			}
				
			System.out.println();
			
			for(int s = 0; s < data.numScenarios; s++) {
				System.err.println("Scenario: "+s);
				for(int t = 0; t < data.TimePeriod; t++) {
					System.err.println();
					for(data.Node i : data.all_nodes) {
					for(Integer ind : i.outgoing) {
						data.Node j = data.all_nodes.get(ind);
							if(f.get(i).get(j.id) != null) {
								if(cplex.getValue(f.get(i).get(j.id)[t][s])>0) 
									System.out.println(i.name+" to "+j.name+" "+t+" = "+Math.round(cplex.getValue(f.get(i).get(j.id)[t][s])*100.0)/ 100.0);
							}
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
}
