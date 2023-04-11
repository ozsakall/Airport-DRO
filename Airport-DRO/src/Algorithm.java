
import data.data;
import optimizer.Optimizer;
import ilog.concert.*;

public class Algorithm {
	
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		Optimizer algorithm = new Optimizer();
		long startTime = System.nanoTime();   
		algorithm.runAlgorithm();
		long estimatedTime = System.nanoTime() - startTime;
		System.out.println("Cpu: "+estimatedTime/1000000000.00);
		
	}

}
