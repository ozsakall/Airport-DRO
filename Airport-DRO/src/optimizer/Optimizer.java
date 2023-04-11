package optimizer;

import data.data;
import ilog.cplex.IloCplex;

public class Optimizer {

	
	public void runAlgorithm() throws Exception {
		int iter = 0;
		double lBound = 0, tUbound = 0;
		double uBound = Double.MAX_VALUE;
		double[] prob = new double[data.numScenarios];
		for(int i = 0; i < data.numScenarios; i++)
			prob[i] = data.all_scenarios.get(i).probability;
		

		long mTime = 0;
		long sTime = 0;

		long startTime = System.nanoTime();  
		Master.solve(iter);
		mTime += System.nanoTime() - startTime;
		
		Sub.GenModel();
		Separation.GenModel();
		
		Master.addColumn();
		
		do {
	 	    tUbound = Master.getFSOj(iter);
			
			//solve subproblems
	 	   startTime = System.nanoTime(); 
			for(data.Scenario scenario : data.all_scenarios) {
				long sa = System.nanoTime();  
				Sub.solve(scenario);
				//Sub.write_solution();
				System.out.println("cpu: "+ cpu(System.nanoTime() - sa));
				tUbound += Sub.objValue[scenario.id] * scenario.probability;
			}
			System.out.println("change model cpu: "+cpu(Sub.ch_time));
			sTime += System.nanoTime() - startTime;

			//solve separation problem
			prob = Separation.solve();
			
	 	    //addCut
			Master.addOptCut(prob);
			iter++;
			
			//solve master
			startTime = System.nanoTime();  
			Master.solve(iter);
			mTime += System.nanoTime() - startTime;
			lBound = Master.objValue;
			
	 	    if(tUbound < uBound)
	 	    	uBound = tUbound;

	 	    System.err.println("Iteration: "+iter +" Gap: "+Math.round(Math.abs((uBound - lBound)/uBound)*100.00)/100.00+ " cpu: "+cpu(mTime + sTime));
		}while(Math.abs((uBound - lBound)/uBound) > data.eps);
		Master.write_solution(iter);
		System.err.println("Iteration: " +iter+ " Total cost: "+Math.round(uBound*100.00)/100.00);
		System.err.println("master cpu: "+cpu(mTime)+" sub cpu: "+cpu(sTime));
		System.err.println();
	}
	public Optimizer() {
		data.DataRead();
		//writeData();
		Master.GenModel();
	}
	public double cpu(double time) {
		
		return Math.round((time/1000000000.00)*100.00)/100.00;
	}
}
