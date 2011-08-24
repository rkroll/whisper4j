package org.github.whisper4j;

public enum AggregationMethod {
	Average(1),

	Sum(2),

	Last(3),

	Max(4),

	Min(5);

	private final int i;

	private AggregationMethod(int i) {
		this.i = i;
	}
	
	public int getIntValue(){
		return i;
	}
	
	public static AggregationMethod fromInt(int j){
		for(AggregationMethod aggregationMethod:AggregationMethod.values()){
			if(aggregationMethod.i == j){
				return aggregationMethod;
			}
		}
		return null;
	}
	// aggregationTypeToMethod = dict({
	// 1: 'average',
	// 2: 'sum',
	// 3: 'last',
	// 4: 'max',
	// 5: 'min'
	// })
	// aggregationMethodToType = dict([[v,k] for k,v in
	// aggregationTypeToMethod.items()])
	// aggregationMethods = aggregationTypeToMethod.values()
}
