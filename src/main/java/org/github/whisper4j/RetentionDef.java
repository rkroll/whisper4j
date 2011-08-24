package org.github.whisper4j;

public class RetentionDef {
	public int secondsPerPoint;
	public int point;

	public static RetentionDef calc(int precision,
			UnitMultipliers precisionUnit, int point, UnitMultipliers pointUnit) {
		if (precisionUnit == null) {
			throw new IllegalArgumentException();
		}
		RetentionDef def = new RetentionDef();
		def.secondsPerPoint = precision * precisionUnit.getSeconds();

		if (pointUnit != null) {
			def.point = (point * pointUnit.getSeconds()) / def.secondsPerPoint;
		} else {
			def.point = point;
		}
		return def;
		// if pointsUnit:
		// points = points * UnitMultipliers[pointsUnit] / precision
	}

	// public static RetentionDef parseRetentionDef(String retentionDef) {
	// if (retentionDef == null) {
	// return null;
	// }
	// int colon = retentionDef.indexOf(':');
	// String precisionStr = retentionDef.substring(0, colon);
	// String pointsStr = retentionDef.substring(colon + 1);
	//
	// int precision;
	// UnitMultipliers precisionUnit;
	// int point;
	// UnitMultipliers pointUnit;
	//
	// throw new IllegalArgumentException("not implemented");

	// if precision.isdigit():
	// precisionUnit = 's'
	// precision = int(precision)

	// else:
	// precisionUnit = precision[-1]
	// precision = int( precision[:-1] )

	// if points.isdigit():
	// pointsUnit = None
	// points = int(points)

	// else:
	// pointsUnit = points[-1]
	// points = int( points[:-1] )
	//
	// if precisionUnit not in UnitMultipliers:
	// raise ValueError("Invalid unit: '%s'" % precisionUnit)
	//
	// if pointsUnit not in UnitMultipliers and pointsUnit is not None:
	// raise ValueError("Invalid unit: '%s'" % pointsUnit)
	//
	// precision = precision * UnitMultipliers[precisionUnit]
	//
	// if pointsUnit:
	// points = points * UnitMultipliers[pointsUnit] / precision

	// return (precision, points);
	// }

}
