package org.github.whisper4j;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Propagation {
	private static float sum(Point[] knownValues) {
		float result = 0;
		for (Point f : knownValues) {
			result += f.value;
		}
		return result;
	}

	private static float max(Point[] knownValues) {
		float result = Float.MIN_VALUE;
		for (Point f : knownValues) {
			if (f.value > result) {
				result = f.value;
			}
		}
		return result;
	}

	private static float min(Point[] knownValues) {
		float result = Float.MAX_VALUE;
		for (Point f : knownValues) {
			if (f.value < result) {
				result = f.value;
			}
		}
		return result;
	}

	private static float last(Point[] knownValues) {
		Point last = knownValues[knownValues.length - 1];
		return last.value;
	}

	public static float __aggregate(AggregationMethod aggregationMethod,
			Point[] knownValues) throws InvalidAggregationMethodException {
		switch (aggregationMethod) {
		case Average:
			return sum(knownValues) / (float) knownValues.length;
		case Sum:
			return sum(knownValues);
		case Last:
			return last(knownValues);
		case Max:
			return max(knownValues);
		case Min:
			return min(knownValues);
		default:
			throw new InvalidAggregationMethodException(
					"Unrecognized aggregation method " + aggregationMethod);
		}
	}

	public static boolean __propagate(RandomAccessFile fh, Header header,
			long timestamp, ArchiveInfo higher, ArchiveInfo lower)
			throws IOException, InvalidAggregationMethodException {
		AggregationMethod aggregationMethod = AggregationMethod
				.fromInt(header.metadata.aggregationType);
		float xff = header.metadata.xFileFactor;

		int lowerIntervalStart = (int) (timestamp - (Whisper.mod(timestamp,
				lower.secondsPerPoint)));
		long lowerIntervalEnd = lowerIntervalStart + lower.secondsPerPoint;

		fh.seek(higher.offset);
		long higherBaseInterval = fh.readLong();
		long higherBaseValue = fh.readLong();
		// packedPoint = fh.read(pointSize)
		// (higherBaseInterval,higherBaseValue) =
		// struct.unpack(pointFormat,packedPoint)

		long higherFirstOffset;

		if (higherBaseInterval == 0) {
			higherFirstOffset = higher.offset;
		} else {
			long timeDistance = lowerIntervalStart - higherBaseInterval;
			long pointDistance = timeDistance / higher.secondsPerPoint;
			long byteDistance = pointDistance * Point.sizeof();
			higherFirstOffset = higher.offset + (byteDistance % higher.size);
		}

		int higherPoints = lower.secondsPerPoint / higher.secondsPerPoint;
		int higherSize = higherPoints * Point.sizeof();
		int relativeFirstOffset = (int) (higherFirstOffset - higher.offset);
		int relativeLastOffset = (relativeFirstOffset + higherSize)
				% higher.size;
		int higherLastOffset = relativeLastOffset + higher.offset;
		fh.seek(higherFirstOffset);

		byte[] seriesString;
		if (higherFirstOffset < higherLastOffset) { // we don't wrap the archive

			seriesString = new byte[(int) (higherLastOffset - higherFirstOffset)];
			fh.read(seriesString);
		} else { // We do wrap the archive
					// TODO: create ony one array
			long higherEnd = higher.offset + higher.size;
			seriesString = new byte[(int) (higherEnd - higherFirstOffset)];
			fh.read(seriesString);
			fh.seek(higher.offset);
			byte[] secondpart = new byte[higherLastOffset - higher.offset];
			fh.read(secondpart);

			seriesString = Whisper.concat(seriesString, secondpart);
		}

		// Now we unpack the series data we just read
		// byteOrder,pointTypes = pointFormat[0],pointFormat[1:];
		// points = len(seriesString) / pointSize;
		// seriesFormat = byteOrder + (pointTypes * points);
		// unpackedSeries = struct.unpack(seriesFormat, seriesString);

		// And finally we construct a list of values
		// neighborValues = [None] * points;
		int currentInterval = (int) lowerIntervalStart;
		int step = higher.secondsPerPoint;
		//
		// for i in xrange(0,len(unpackedSeries),2){
		// pointTime = unpackedSeries[i];
		// if (pointTime == currentInterval){
		// neighborValues[i/2] = unpackedSeries[i+1];
		// }
		// currentInterval += step;
		// }

		Point[] neighborValues = Whisper.unpackPoints(seriesString,
				currentInterval, step);

		// Propagate aggregateValue to propagate from neighborValues if we have
		// enough known points
		Point[] knownValues = removeNullValues(neighborValues);
		if (knownValues == null) {
			return false;
		}

		float knownPercent = (float) knownValues.length
				/ (float) neighborValues.length;

		if (knownPercent >= xff) {
			// we have enough data to propagate a value!
			float aggregateValue = __aggregate(aggregationMethod, knownValues);
			// myPackedPoint =
			// struct.pack(pointFormat,lowerIntervalStart,aggregateValue);
			// fh.seek(lower.offset);
			// byte[] packedPoint = new byte[Point.sizeof()];
			// fh.read(packedPoint);
			Point lowerBasePoint = Whisper.readPoint(fh, lower.offset);
			// (lowerBaseInterval,lowerBaseValue) =
			// struct.unpack(pointFormat,packedPoint);

			if (lowerBasePoint.timestamp == 0) { // First propagated update to
													// this lower archive
				// fh.seek(lower.offset);
				// fh.write(myPackedPoint);
				Whisper.writePoint(fh, lower.offset, lowerBasePoint);
			} else {
				// Not our first propagated update to this lower archive
				int timeDistance = (int) (lowerIntervalStart - lowerBasePoint.timestamp);
				int pointDistance = timeDistance / lower.secondsPerPoint;
				int byteDistance = pointDistance * Point.sizeof();
				int lowerOffset = lower.offset + (byteDistance % lower.size);
				Whisper.writePoint(fh, lowerOffset, lowerBasePoint);
				// fh.seek(lowerOffset);
				// fh.write(myPackedPoint);
			}
			return true;
		} else {
			return false;
		}
	}

	public static Point[] removeNullValues(Point[] original) {
		List<Point> knownValues = new ArrayList<Point>();
		for (Point p : original) {
			if (p != null && p.timestamp != 0) {
				knownValues.add(p);
			}
		}
		return knownValues.toArray(new Point[0]);
	}
}
