package utils;

/**
 *
 * @author talz
 */
public class Request {
	public int    neighborId;
	public int    currentAssignment;
	public int    request;
	public double gain;
	public long   sendingTime;
	
	public Request(int neighborId, int currentAssignment, int request, double gain,long sendingTime  ){
		this.neighborId  = neighborId;
		this.currentAssignment = currentAssignment;
		this.request = request;
		this.gain = gain;
		this.sendingTime = sendingTime;
	}
	public int getNeighborId(){
		return this.neighborId;
	}
	
	public int getRequest(){
		return this.request;
	}
	
	public double getRequestGain(){
		return this.gain;
	}
	
	public int getCurrentAssignment(){
		return this.currentAssignment;
	}
}
