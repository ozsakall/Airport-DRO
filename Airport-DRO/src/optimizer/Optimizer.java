package optimizer;

import java.io.PrintWriter;

import data.data;
import full_model.fmodel;
import ilog.concert.IloException;

public class Optimizer {

	
	public void runAlgorithm() throws Exception {
		int iter = 0;
		double lBound = 0, tUbound = 0;
		double uBound = Double.MAX_VALUE;
		double[] prob = new double[data.numScenarios];
		for(data.Scenario s : data.all_scenarios)
			prob[s.id] = s.probability;
		

		long mTime = 0;
		long sTime = 0;

		PrintWriter writer = new PrintWriter("airport_sol.txt", "utf-8");
		
		  //fmodel.GenModel(); fmodel.solve();
		 
		long startTime = System.nanoTime();  
		Master.solve(iter);
		//Master.write_solution(iter);
		mTime += System.nanoTime() - startTime;
		System.out.println("Master cpu: "+cpu(mTime));
		Sub.GenModel();
		Separation.GenModel();
		
		Master.addColumn();

		//add_sol();
		do {
	 	    tUbound = Master.getFSOj(iter);
			
			//solve subproblems
	 	   startTime = System.nanoTime(); 
			for(data.Scenario scenario : data.all_scenarios) {
				long sa = System.nanoTime();
 				Sub.solve(scenario);
				//Sub.write_solution(scenario.id);
				//System.out.println("cpu: "+ cpu(System.nanoTime() - sa));
				
			}
			//System.out.println("change model cpu: "+cpu(Sub.ch_time));
			sTime += System.nanoTime() - startTime;

			//solve separation problem
			//prob = Separation.solve();
			for(data.Scenario scenario : data.all_scenarios) {
				tUbound += Sub.objValue[scenario.id] * prob[scenario.id];
			}
	 	    //addCut
			Master.addOptCut(prob);
			iter++;
			
			//solve master
			startTime = System.nanoTime();  
			Master.solve(iter);
			//Master.write_solution(iter);
			
			mTime += System.nanoTime() - startTime;
			lBound = Master.objValue;
			
	 	    if(tUbound < uBound)
	 	    	uBound = tUbound;

	 	    writer.println("Iteration: "+iter +" UpperBound: "+uBound+" LowerBound: "+lBound+" Gap: "+Math.round(Math.abs((uBound - lBound)/uBound)*1000.00)/1000.00+ " cpu: "+cpu(mTime + sTime));
			//Master.write_solution(iter);
	 	    System.err.println("Iteration: "+iter +" UpperBound: "+uBound+" LowerBound: "+lBound+" Gap: "+Math.round(Math.abs((uBound - lBound)/uBound)*1000.00)/1000.00+ " cpu: "+cpu(mTime + sTime));
		}while(Math.abs((uBound - lBound)/uBound) >= data.eps);
		Master.write_solution(iter);
		System.err.println("Iteration: " +iter+ " Total cost: "+Math.round(uBound*100.00)/100.00);
		System.err.println("master cpu: "+cpu(mTime)+" sub cpu: "+cpu(sTime));
		System.err.println("fmodel: "+fmodel.objValue+" ");
		writer.println("Iteration: " +iter+ " Total cost: "+Math.round(uBound*100.00)/100.00);
		writer.println("master cpu: "+cpu(mTime)+" sub cpu: "+cpu(sTime));
		writer.println("fmodel: "+fmodel.objValue+" ");
		writer.close();
	}
	public Optimizer() {
		data.DataRead();
		//writeData();
		Master.GenModel();
	}
	public void add_sol() throws IloException {
		for(data.Technology tec : data.all_technologies) {
			for(int t = 0; t < data.totalInvestment; t++) 
				Master.sol_c.get(tec)[t] = fmodel.sol_c.get(tec)[t];
		}
	}
	public double cpu(double time) {
		
		return Math.round((time/1000000000.00)*100.00)/100.00;
	}
}
