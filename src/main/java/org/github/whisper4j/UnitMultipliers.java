package org.github.whisper4j;

public enum UnitMultipliers {
//	UnitMultipliers = {
//			  's' : 1,
//			  'm' : 60,
//			  'h' : 60 * 60,
//			  'd' : 60 * 60 * 24,
//			  'y' : 60 * 60 * 24 * 365,
//			}
	
	s(1),
	
	m(60),
	
	h(60*60),
	
	d(60*60*24),
	
	y(60 * 60 * 24 * 365);
	
	private final int i;
	
	private UnitMultipliers(int i){
		this.i=i;
	}
	
	public int getSeconds(){
		return i;
	}
}
