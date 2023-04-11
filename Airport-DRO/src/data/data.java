package data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ilog.concert.*;

public class data {
	public static List<Node> all_nodes = new ArrayList<Node>();
	public static List<Technology> all_technologies = new ArrayList<Technology>();
	public static List<Scenario> all_scenarios = new ArrayList<Scenario>();
	public static List<Demand> all_demands = new ArrayList<Demand>();
	public static int numTech;
	public static int numDemad;
	public static int TimePeriod = 8640*5;
	public static int numScenarios = 25;
	public static double rds = 0.1;
	final public static double alpha = 0;
	final public static double beta = 1;
	final public static double effc = 1;
	final public static double eps =  0.01;
	final public static double sell_elec =  0.01;

	public static class Node{
		public int id;
		public String name;
		public ArrayList<Integer> outgoing = new ArrayList<Integer>();
		public ArrayList<Integer> incoming = new ArrayList<Integer>();
		public Node(ArrayList<Integer> out, ArrayList<Integer> inc, String s) {
			this.id = all_nodes.size();
			this.name= s; 
			this.outgoing = out;
			this.incoming = inc;
			all_nodes.add(this);
		}
	}
	public static class Technology extends Node{
		public IloNumVar c;
		public double invCost; //investment cost
		public double oprCost; //operation costz
		public double lb;
		public double ub;
		public Map<Scenario, Double> beta = new HashMap<Scenario, Double>();
		public Technology(ArrayList<Integer> out, ArrayList<Integer> inc, String name, double invcost, double oprcost, double lb, double ub) {
			super(out, inc, name);
			this.invCost =  invcost;
			this.oprCost =  oprcost;
			this.lb = lb;
			this.ub = ub;
			all_technologies.add(this);
		}
	}
	public static class Demand extends Node{
		public String name;
		public ArrayList<Integer> incoming = new ArrayList<Integer>();
		public double[] dmnd;
		
		public Demand(ArrayList<Integer> inc, String name, double[] dmd) {
			super(new ArrayList<Integer>(), inc, name);
			this.name= name; 
			this.incoming = inc;
			this.dmnd = dmd;
			all_demands.add(this);
		}
	}
	public static class Scenario{
		public int id;
		public double probability;
		public IloNumVar probVar; 
		public Map<Demand, double[]> demand;
		public Map<Demand, double[]> Ref_demand;
		public double[] pvFactor;
		public double[] Ref_pvFactor;
		public double electrictyCost;
		public double Ref_electrictyCost;
		public Scenario(double prob, Map<Demand, double[]> demd, Map<Demand, double[]> ref_demd, double pvFactor[], double[] Ref_pvFactor, double cost, double refCost) {
			this.id = all_scenarios.size();
			this.probability = prob;
			this.demand = demd;
			this.Ref_demand = ref_demd;
			this.pvFactor = pvFactor;
			this.Ref_pvFactor = Ref_pvFactor;
			this.electrictyCost = cost;
			this.Ref_electrictyCost = refCost;
			all_scenarios.add(this);
		}
	}
	public static void DataRead() {
		create_network();
		create_scenarios();
	}
	private static void create_network() {
		Node grid = new Node(new ArrayList<Integer>(), new ArrayList<Integer>(), "grid");
		grid.outgoing.add(3);//elect
		grid.outgoing.add(6);//d1
		
		Technology solar = new Technology(new ArrayList<Integer>(), new ArrayList<Integer>(), "solar", 100, 1, 100, 1400);
		solar.outgoing.add(2);//bss
		
		Technology bss = new Technology(new ArrayList<Integer>(), new ArrayList<Integer>(), "bss", 10, 1, 10, 1400); 
		bss.incoming.add(1);//solar
		bss.outgoing.add(2);//bss
		bss.outgoing.add(3);//elect
		bss.outgoing.add(6);//d1
		bss.outgoing.add(8);//sell
		
		Technology elec = new Technology(new ArrayList<Integer>(), new ArrayList<Integer>(), "electrolyzer", 100, 1, 300, 400);
		elec.incoming.add(0);//grid
		elec.incoming.add(2);//bss
		elec.outgoing.add(4);//hss
		
		Technology hss = new Technology(new ArrayList<Integer>(), new ArrayList<Integer>(), "hss", 100, 1, 300, 400);
		hss.incoming.add(3);//elect
		hss.outgoing.add(4);//hss
		hss.outgoing.add(5);//fc
		
		Technology fc = new Technology(new ArrayList<Integer>(), new ArrayList<Integer>(), "fc", 100, 1, 300, 400);
		fc.incoming.add(4);//hss
		fc.outgoing.add(7);//d2

		Demand d1 = new Demand(new ArrayList<Integer>(), "d1", new double[numScenarios]);
		d1.incoming.add(0);//grid
		d1.incoming.add(2);//bss
		
		Demand d2 = new Demand(new ArrayList<Integer>(), "d2", new double[numScenarios]);
		d2.incoming.add(5);//fc

		Node sell = new Node(new ArrayList<Integer>(), new ArrayList<Integer>(), "sell");
		sell.incoming.add(2);//bss
		
		Node o2 = new Node(new ArrayList<Integer>(), new ArrayList<Integer>(), "sell");
		o2.incoming.add(2);//bss
		
		numDemad = all_demands.size();
		numTech = all_technologies.size();
	}
	private static void create_scenarios() {
		
		Random rand = new Random(3);
		double prob = 1.0/numScenarios;
				
		for(int s = 0; s<numScenarios; s++) {
			Map<Demand, double[]> dem = new HashMap<Demand, double[]>();
			Map<Demand, double[]> ref_dem = new HashMap<Demand, double[]>();
			double[] pv = new double[TimePeriod];
			
			for(Demand d : all_demands) {
				double[] dm = new double[TimePeriod];
				double[] ref_dm = new double[TimePeriod];
				for(int t = 0; t < TimePeriod; t++) {
					double rng = rand.nextDouble()*50+100;
					dm[t] = rng;
					
					rng = rand.nextDouble()*100+200; //rand.nextGaussian()*50 + 100; 
					ref_dm[t] = rng;
					pv[t] = rand.nextDouble();
					if(pv[t] == 0) pv[t] = 0.2;
				}
				dem.put(d, (dm));
				ref_dem.put(d, (dm));
			}
			Scenario sc = new Scenario(prob, dem, ref_dem, pv, pv, 1, 1);
		}
	}
	public static List<Node> getNodes(){
		return all_nodes;
	}
	public static List<Technology> getTecnhology(){
		return all_technologies;
	}
	public static List<Scenario> getScenarios(){
		return all_scenarios;
	}
}
