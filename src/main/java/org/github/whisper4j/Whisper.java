package org.github.whisper4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This module is an implementation of the Whisper database API
 * 
 * Here is the basic layout of a whisper data file
 * 
 * File = Header,Data Header = Metadata,ArchiveInfo+ Metadata =
 * aggregationType,maxRetention,xFilesFactor,archiveCount ArchiveInfo =
 * Offset,SecondsPerPoint,Points Data = Archive+ Archive = Point+ Point =
 * timestamp,value
 * 
 * @author leen toelen
 * 
 */
public class Whisper {
	private boolean LOCK = false;
	private boolean CACHE_HEADERS = false;
	private boolean AUTOFLUSH = false;
	private Map<String, Header> __headerCache = new HashMap<String, Header>();
	private static int METADATA_BYTE_SIZE = 16;
	private static int ARCHIVEINFO_BYTE_SIZE = 12;

	private MetaData readMetaData(String path, RandomAccessFile fh)
			throws CorruptWhisperFile {
		try {
			// MetaData is 16 bytes
			MetaData metaData = new MetaData();
			metaData.aggregationType = fh.readInt();
			metaData.maxRetention = fh.readInt();
			metaData.xFileFactor = fh.readFloat();
			metaData.archiveCount = fh.readInt();
			return metaData;
			// (aggregationType,maxRetention,xff,archiveCount) =
			// struct.unpack(metadataFormat,packedMetadata);
		} catch (Exception e) {
			throw new CorruptWhisperFile("Unable to read header " + path, e);
		}

	}

	private int writeHeader(RandomAccessFile fh, Header header)
			throws IOException {
		fh.seek(0);
		fh.writeInt(header.metadata.aggregationType);
		fh.writeInt(header.metadata.maxRetention);
		fh.writeFloat(header.metadata.xFileFactor);
		fh.writeInt(header.metadata.archiveCount);

		// Now the archiveinfo
		for (ArchiveInfo info : header.archiveInfo) {
			fh.writeInt(info.offset);
			fh.writeInt(info.secondsPerPoint);
			fh.writeInt(info.points);
		}
		return METADATA_BYTE_SIZE
				+ (header.archiveInfo.size() * ARCHIVEINFO_BYTE_SIZE);
	}

	/**
	 * Reads the header part of a whisper file
	 * 
	 * @param fh
	 * @return
	 * @throws CorruptWhisperFile
	 * @throws IOException
	 */
	private Header readHeader(String path, RandomAccessFile fh)
			throws CorruptWhisperFile, IOException {
		Header info = __headerCache.get(path);
		if (info != null) {
			return info;
		}

		long originalOffset = fh.getFilePointer();
		fh.seek(0);

		MetaData metaData = readMetaData(path, fh);

		List<ArchiveInfo> archiveInfos = new ArrayList<ArchiveInfo>();

		for (long i = 0; i < metaData.archiveCount; i++) {
			// byte[] packedArchiveInfo = fh.read(archiveInfoSize);
			try {
				ArchiveInfo archiveInfo = readArchiveInfo(fh);

				// (offset,secondsPerPoint,points) =
				// struct.unpack(archiveInfoFormat,packedArchiveInfo);
				archiveInfos.add(archiveInfo);
			} catch (Exception e) {
				throw new CorruptWhisperFile("Unable to read archive metadata "
						+ i + " of " + metaData.archiveCount + ", at position "
						+ fh.getFilePointer(), e);
			}

		}

		// Go back to where we were
		fh.seek(originalOffset);

		info = new Header();
		info.metadata = metaData;
		info.archiveInfo = archiveInfos;

		if (CACHE_HEADERS) {
			__headerCache.put(path, info);
		}

		return info;
	}

	private ArchiveInfo readArchiveInfo(RandomAccessFile fh) throws IOException {
		ArchiveInfo archiveInfo = new ArchiveInfo();
		archiveInfo.offset = fh.readInt();
		archiveInfo.secondsPerPoint = fh.readInt();
		archiveInfo.points = fh.readInt();
		archiveInfo.retention = archiveInfo.secondsPerPoint
				* archiveInfo.points;
		archiveInfo.size = archiveInfo.points * Point.sizeof();
		return archiveInfo;
	}

	/**
	 * 
	 * @param path
	 * @param aggregationMethod
	 *            specifies the method to use when propogating data (see
	 *            ``whisper.aggregationMethods``)
	 * @throws CorruptWhisperFile
	 * @throws IOException
	 */
	public void setAggregationMethod(String path,
			AggregationMethod aggregationMethod)
			throws InvalidAggregationMethodException, CorruptWhisperFile,
			IOException {
		RandomAccessFile fh = new RandomAccessFile(path, "rw");// "r+b"
		if (LOCK) {
			// fcntl.flock( fh.fileno(), fcntl.LOCK_EX );
		}

		if (aggregationMethod == null) {
			throw new InvalidAggregationMethodException(
					"Unrecognized aggregation method");
		}
		MetaData metaData = readMetaData(path, fh);
		metaData.aggregationType = aggregationMethod.getIntValue();

		fh.seek(0);
		fh.writeLong(metaData.aggregationType);

		if (AUTOFLUSH) {
			// fh.flush();
			// os.fsync(fh.fileno());
		}
		if (CACHE_HEADERS && __headerCache.containsKey(path)) {
			__headerCache.remove(path);
		}

		fh.close();

		// return
		// aggregationMethod;//aggregationTypeToMethod.get(aggregationType,
		// 'average');
	}

	/**
	 * 
	 * @param path
	 * @param archiveList
	 *            is a list of archives, each of which is of the form
	 *            (secondsPerPoint,numberOfPoints)
	 * @param xFilesFactor
	 *            =0.5 specifies the fraction of data points in a propagation
	 *            interval that must have known values for a propagation to
	 *            occur
	 * @param aggregationMethod
	 *            ='average' specifies the function to use when propogating data
	 *            (see ``whisper.aggregationMethods``)
	 * @throws IOException
	 * @throws InvalidConfigurationException
	 */
	public void create(String path, RetentionDef[] archiveList,
			float xFilesFactor, AggregationMethod aggregationMethod)
			throws IOException, InvalidConfigurationException {
		// Validate archive configurations...
		if (archiveList == null) {
			throw new InvalidConfigurationException(
					"You must specify at least one archive configuration!");
		}

		// TODO: sort
		// archiveList.sort(key=lambda a: a[0]) //sort by precision
		// (secondsPerPoint)

		for (int i = 0; i < archiveList.length; i++) {
			if (i == (archiveList.length - 1)) {
				break;
			}
			RetentionDef archive = archiveList[i];
			RetentionDef next = archiveList[i + 1];
			if ((archive.secondsPerPoint < next.secondsPerPoint) == false) {
				throw new InvalidConfigurationException(
						"You cannot configure two archives with the same precision "
								+ archive + "," + next);
			}
			if ((archive.secondsPerPoint % archive.secondsPerPoint) != 0) {
				throw new InvalidConfigurationException(
						"Higher precision archives' precision must evenly divide all lower precision archives' precision "
								+ archive.secondsPerPoint
								+ ","
								+ next.secondsPerPoint);
			}
			long retention = archive.secondsPerPoint * archive.point;
			long nextRetention = next.secondsPerPoint * next.point;

			if ((nextRetention > retention) == false) {
				throw new InvalidConfigurationException(
						"Lower precision archives must cover larger time intervals than higher precision archives "
								+ archive + "," + next);
			}
		}

		// Looks good, now we create the file and write the header
		File f = new File(path);
		if (f.exists()) {
			throw new InvalidConfigurationException("File " + path
					+ " already exists!");
		}

		RandomAccessFile fh = new RandomAccessFile(path, "rw");
		if (LOCK) {
			// fcntl.flock( fh.fileno(), fcntl.LOCK_EX )
		}

		Header header = new Header();
		header.metadata = new MetaData();
		header.metadata.aggregationType = aggregationMethod.getIntValue();
		header.metadata.archiveCount = archiveList.length;
		header.metadata.xFileFactor = xFilesFactor;
		// TODO
		int oldest = getOldest(archiveList);// sorted([secondsPerPoint * points
											// for
		// secondsPerPoint,points in archiveList])[-1]
		header.metadata.maxRetention = oldest;
		header.archiveInfo = new ArrayList<ArchiveInfo>();

		int headerSize = METADATA_BYTE_SIZE
				+ (archiveList.length * ARCHIVEINFO_BYTE_SIZE);
		// headerSize = metadataSize + (archiveInfoSize * len(archiveList))
		int archiveOffsetPointer = headerSize;

		for (RetentionDef retentionDef : archiveList) {
			ArchiveInfo info = new ArchiveInfo();
			info.secondsPerPoint = retentionDef.secondsPerPoint;
			info.points = retentionDef.point;
			info.offset = archiveOffsetPointer;
			header.archiveInfo.add(info);

			archiveOffsetPointer += (retentionDef.point * Point.sizeof());
		}

		writeHeader(fh, header);
		// zeroes = '\x00' * (archiveOffsetPointer - headerSize)
		byte[] zeroes = new byte[(archiveOffsetPointer - headerSize)];
		fh.write(zeroes);

		if (AUTOFLUSH) {
			fh.getFD().sync();
			// fh.flush()
			// os.fsync(fh.fileno())
		}

		fh.close();
	}

	private int getOldest(RetentionDef[] archiveList) {
		// sorted([secondsPerPoint * points for
		// secondsPerPoint,points in archiveList])[-1]

		int oldest = Integer.MIN_VALUE;
		for (int i = 0; i < archiveList.length; i++) {
			// secondsPerPoint * points
			int temp = archiveList[i].secondsPerPoint * archiveList[i].point;

			if (temp > oldest) {
				oldest = temp;
			}
		}
		return oldest;
	}

	public void update(String path, float value, long timestamp)
			throws InvalidAggregationMethodException, CorruptWhisperFile, TimestampNotCoveredException, IOException {
		RandomAccessFile fh = new RandomAccessFile(path, "rw");
		file_update(path,fh, value, timestamp);
	}

	public void file_update(String path,RandomAccessFile fh, float value, long timestamp) throws InvalidAggregationMethodException, IOException, CorruptWhisperFile, TimestampNotCoveredException {
		if (LOCK) {
			// fcntl.flock( fh.fileno(), fcntl.LOCK_EX )
		}

		Header header = readHeader(path,fh);
		long now = System.currentTimeMillis();
		if (timestamp == Long.MAX_VALUE || timestamp == Long.MIN_VALUE || timestamp == Integer.MIN_VALUE || timestamp == Integer.MAX_VALUE) {
			timestamp = now;
		}

		// timestamp = int(timestamp)
		long diff = now - timestamp;
		if (((diff < header.metadata.maxRetention) ==false && diff >= 0)) {
			throw new TimestampNotCoveredException(
					"Timestamp not covered by any archives in this database.");
		}

		ArchiveInfo archive=null;
		List<ArchiveInfo> lowerArchives = null;

		for (int i = 0; i < header.archiveInfo.size(); i++) {// i,archive in
																// enumerate(header['archives']){
			archive = header.archiveInfo.get(0);
			// Find the highest-precision archive that covers timestamp
			if (archive.retention < diff) {
				continue;
			}

			lowerArchives = subList(header.archiveInfo, i + 1);

			break;
		}

		// First we update the highest-precision archive
		int myInterval = (int) (timestamp - (timestamp % archive.secondsPerPoint));
		// myPackedPoint = struct.pack(pointFormat,myInterval,value)
		// fh.seek(archive['offset']);
		// packedPoint = fh.read(pointSize);
		// (baseInterval,baseValue) = struct.unpack(pointFormat,packedPoint);
		Point basePoint = readPoint(fh, archive.offset);

		if (basePoint.timestamp == 0) { // This file's first update
			// fh.seek(archive['offset'])
			// fh.write(myPackedPoint)
			writePoint(fh, archive.offset, basePoint);
			// baseInterval,baseValue = myInterval,value;
			basePoint.timestamp = myInterval;
			basePoint.value = value;
		} else { // Not our first update
			int timeDistance = (int) (myInterval - basePoint.timestamp);
			int pointDistance = timeDistance / archive.secondsPerPoint;
			int byteDistance = pointDistance * Point.sizeof();
			int myOffset = archive.offset + (byteDistance % archive.size);
			// fh.seek(myOffset);
			// fh.write(myPackedPoint);
			writePoint(fh, myOffset, basePoint);
		}
		// Now we propagate the update to lower-precision archives
		ArchiveInfo higher = archive;
		for (ArchiveInfo lowerArchive : lowerArchives) {
			boolean proagate = Propagation.__propagate(fh, header, myInterval,
					higher, lowerArchive);
			if (proagate == false) {
				break;
			} else {
				higher = lowerArchive;
			}
		}
		// for lower in lowerArchives:
		// if not __propagate(fh, header, myInterval, higher, lower):
		// break
		// higher = lower

		if (AUTOFLUSH) {
			// fh.flush()
			// os.fsync(fh.fileno())
		}
		fh.close();
	}

	// public void update_many(String path,List<Point> points){
	// // """update_many(path,points)
	// //
	// // path is a string
	// // points is a list of (timestamp,value) points
	// // """
	// if (points == null){
	// return;
	// }
	// //points = [ (int(t),float(v)) for (t,v) in points]
	// //TODO: sort
	// //points.sort(key=lambda p: p[0],reverse=True) //order points by
	// timestamp, newest first
	// RandomAccesFile fh = new RandomAccessFile(path,'r+b');
	// file_update_many(fh, points);
	// }
	
	
	
	
	// public void file_update_many(RandomAccessFile fh, List<Point> points){
	// if( LOCK){
	// //fcntl.flock( fh.fileno(), fcntl.LOCK_EX )
	// }
	//
	// Header header = __readHeader(fh);
	// long now = System.currentTimeMillis();
	// archives = header.archiveInfo;iter( header['archives'] );
	// currentArchive = archives.next();
	// currentPoints = [];
	//
	// for(Point point : points){
	// age = now - point[0];
	//
	// while currentArchive['retention'] < age: #we can't fit any more points in
	// this archive
	// if currentPoints: #commit all the points we've found that it can fit
	// currentPoints.reverse() #put points in chronological order
	// __archive_update_many(fh,header,currentArchive,currentPoints)
	// currentPoints = []
	// try:
	// currentArchive = archives.next()
	// except StopIteration:
	// currentArchive = None
	// break
	//
	// if not currentArchive:
	// break //drop remaining points that don't fit in the database
	//
	// currentPoints.append(point);
	// }
	//
	// if( currentArchive != null && currentPoints != null){ //don't forget to
	// commit after we've checked all the archives
	// currentPoints.reverse();
	// __archive_update_many(fh,header,currentArchive,currentPoints);
	// }
	//
	// if( AUTOFLUSH){
	// //fh.flush()
	// //os.fsync(fh.fileno())
	// }
	// fh.close();
	// }

	public Point[] getAlignedPoints(Point[] points,int step){
		//alignedPoints = [ (timestamp - (timestamp % step), value)  for (timestamp,value) in points ]
		Point[] alignedPoints = new Point[points.length];
		int i=0;
		for(Point point:points){
			Point aligned = new Point();
			aligned.timestamp = point.timestamp - mod(point.timestamp,step);
			aligned.value = point.value;
			alignedPoints[i] = aligned;
					i++;
		}
		return alignedPoints;
	}
	


	/**
	 * Like a subString for Lists
	 * 
	 * @param archiveInfo
	 * @param i
	 * @return
	 */
	private List<ArchiveInfo> subList(List<ArchiveInfo> archiveInfo, int i) {
		List<ArchiveInfo> result = new ArrayList<ArchiveInfo>();
		for (int j = i; j < archiveInfo.size(); j++) {
			result.add(archiveInfo.get(j));
		}
		return result;
	}

	public Header info(String path) throws Exception {
		// """info(path)
		//
		// path is a string
		// """
		RandomAccessFile fh = new RandomAccessFile(path, "rw");
		Header info = readHeader(path, fh);
		fh.close();
		return info;
	}

	/**
	 * 
	 * @param path
	 * @param fromTime
	 *            epoch time in seconds
	 * @param untilTime
	 *            epoch time in seconds, but defaults to now
	 * @return
	 * @throws Exception
	 */
	public TimeInfo fetch(String path, int fromTime, int untilTime)
			throws Exception {
		RandomAccessFile fh = new RandomAccessFile(path, "r");
		return file_fetch(path, fh, fromTime, untilTime);
	}

	/**
	 * Seconds since the epoch
	 * 
	 * @return
	 */
	public static int time() {
		long fromTime = System.currentTimeMillis();
		int i = (int) (fromTime / 1000l);
		return i;
	}

	/**
	 * Negative-safe modulus
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public static long mod(long x, long y) {
		long result = x % y;
		if (result < 0) {
			result += y;
		}
		return result;
	}

	public TimeInfo file_fetch(String path, RandomAccessFile fh, int fromTime,
			int untilTime) throws IOException, CorruptWhisperFile,
			InvalidTimeIntervalException {
		Header header = readHeader(path, fh);
		int now = time();
		if (untilTime == Long.MAX_VALUE || untilTime == Long.MIN_VALUE) {
			untilTime = now;
		}
		// long fromTime = int(fromTime)
		// untilTime = int(untilTime)

		int oldestTime = now - header.metadata.maxRetention;
		if (fromTime == Integer.MIN_VALUE || fromTime < oldestTime) {
			fromTime = oldestTime;
		}

		if ((fromTime < untilTime) == false) {
			throw new InvalidTimeIntervalException("Invalid time interval "
					+ fromTime + " " + untilTime);
		}
		if (untilTime > now) {
			untilTime = now;
		}
		if (untilTime < fromTime) {
			untilTime = now;
		}

		int diff = now - fromTime;
		ArchiveInfo archive = null;
		for (ArchiveInfo currentArchive : header.archiveInfo) {
			if (currentArchive.retention >= diff) {
				archive = currentArchive;
				break;
			}
		}

		int fromInterval = (fromTime - (fromTime % archive.secondsPerPoint))
				+ archive.secondsPerPoint;
		int untilInterval = (untilTime - (untilTime % archive.secondsPerPoint))
				+ archive.secondsPerPoint;
		fh.seek(archive.offset);

		// long pointer = fh.getFilePointer();
		// packedPoint = fh.read(pointSize)
		// (baseInterval,baseValue) = struct.unpack(pointFormat,packedPoint)

		// See http://jwinblad.com/resources/unsignedtojava.html

		long baseInterval = fh.readInt() & 0xffffffffL;// Remove the sign
		// int baseInterval = fh.readInt() & 0xffff;//Remove the sign
		// double baseValue =
		// fh.readFloat();//Float.intBitsToFloat(fh.readInt());
		// double d = fh.readDouble();
		float baseValue = (float) fh.readDouble();

		if (baseInterval == 0) {
			long step = archive.secondsPerPoint;
			int points = (int) ((untilInterval - fromInterval) / step);
			TimeInfo timeInfo = new TimeInfo(fromInterval, untilInterval, step);
			Point[] valueList = new Point[points];
			timeInfo.points = valueList;
			return timeInfo;
		}

		// Determine fromOffset
		long timeDistance = ((long) fromInterval) - baseInterval;
		long pointDistance = timeDistance / archive.secondsPerPoint;
		long byteDistance = pointDistance * Point.sizeof();

		long temp = mod(byteDistance, archive.size);
		long fromOffset = archive.offset + temp;

		// Determine untilOffset
		timeDistance = untilInterval - baseInterval;
		pointDistance = timeDistance / archive.secondsPerPoint;
		byteDistance = pointDistance * Point.sizeof();
		int untilOffset = (int) (archive.offset + mod(byteDistance,
				archive.size));

		// Read all the points in the interval
		fh.seek(fromOffset);
		byte[] seriesString;
		if (fromOffset < untilOffset) {
			// If we don't wrap around the archive
			seriesString = new byte[((int) untilOffset - (int) fromOffset)];
			int read = fh.read(seriesString);
			if (read != seriesString.length) {
				throw new CorruptWhisperFile("read " + read + " != "
						+ seriesString.length);
			}
		} else {
			// We do wrap around the archive, so we need two reads
			long archiveEnd = archive.offset + archive.size;
			int firstpart = ((int) archiveEnd - (int) fromOffset);
			int secondpart = untilOffset - archive.offset;
			seriesString = new byte[firstpart];
			fh.read(seriesString, 0, firstpart);
			fh.seek(archive.offset);
			byte[] newb = new byte[secondpart];
			fh.read(newb);
			// TODO: is there a way to read directly in the existing array with
			// an offset?
			seriesString = concat(seriesString, newb);
		}
		// System.out.println("Points size: " + seriesString.length);
		// System.out.println("Points count: " + seriesString.length
		// / Point.sizeof());
		// System.out.println("# Points: " + archive.points);

		// Now we unpack the series data we just read (anything faster than
		// unpack?)
		// byteOrder,pointTypes = pointFormat[0],pointFormat[1:]
		// points = len(seriesString) / pointSize
		// seriesFormat = byteOrder + (pointTypes * points)
		// unpackedSeries = struct.unpack(seriesFormat, seriesString)

		// //And finally we construct a list of values (optimize this!)
		// valueList = [None] * points //pre-allocate entire list for speed
		int currentInterval = fromInterval;
		int step = archive.secondsPerPoint;
		Point[] points = unpackPoints(seriesString, currentInterval, step);

		//
		// for( i in xrange(0,len(unpackedSeries),2)) {
		// pointTime = unpackedSeries[i]
		// if pointTime == currentInterval:
		// pointValue = unpackedSeries[i+1]
		// valueList[i/2] = pointValue #in-place reassignment is faster than
		// append()
		// currentInterval += step
		// }
		// fh.close();
		TimeInfo timeInfo = new TimeInfo(fromInterval, untilInterval, step);
		// return (timeInfo,valueList);
		timeInfo.points = points;
		return timeInfo;
	}

	public static byte[] concat(byte[] A, byte[] B) {
		byte[] C = new byte[A.length + B.length];
		System.arraycopy(A, 0, C, 0, A.length);
		System.arraycopy(B, 0, C, A.length, B.length);

		return C;
	}

	public static Point[] unpackPoints(byte[] series, int currentInterval,
			int step) {
		int pointsize = Point.sizeof();
		int count = series.length / pointsize;

		Point[] points = new Point[count];

		ByteBuffer buf = ByteBuffer.wrap(series);
		// System.out.println(new String(series));
		for (int i = 0; i < count; i += 1) {
			currentInterval += step;

			// byte[] dst = new byte[8];
			// buf.get(dst, i, 2);
			// System.out.println(buf );
			long timestamp = buf.getInt(i * pointsize) & 0xffffffffL;// Remove
																		// the
																		// sign

			// long timestamp = buf.getInt();
			double value = buf.getDouble((i * pointsize) + 4);

			if (timestamp == currentInterval) {
				//System.out.println(i + " " + currentInterval + " --- -" + i
				//		+ " - " + timestamp + " , " + value);
				// This is a timestamp
				Point p = new Point();
				p.timestamp = timestamp;
				p.value = (float) value;

				points[i] = p;
//			} else if (timestamp == 0) {
//				System.err.println(i + " " + currentInterval + " 000 -" + i
//						+ " - " + timestamp + " , " + value);

			} else {
				//System.out.println(i + " " + currentInterval + " ??? -" + i
				//		+ " - " + timestamp + " , " + value);
				Point p = new Point();
				p.timestamp = 0;
				p.value = 0;
				points[i] = p;
			}
		}
		return points;
	}

	/**
	 * @see java.nio.Bits#makeInt()
	 * @param b3
	 * @param b2
	 * @param b1
	 * @param b0
	 * @return
	 */
	static private int makeInt(byte b3, byte b2, byte b1, byte b0) {
		return (int) ((((b3 & 0xff) << 24) | ((b2 & 0xff) << 16)
				| ((b1 & 0xff) << 8) | ((b0 & 0xff) << 0)));
	}

	static private long makeLong(byte b7, byte b6, byte b5, byte b4, byte b3,
			byte b2, byte b1, byte b0) {
		return ((((long) b7 & 0xff) << 56) | (((long) b6 & 0xff) << 48)
				| (((long) b5 & 0xff) << 40) | (((long) b4 & 0xff) << 32)
				| (((long) b3 & 0xff) << 24) | (((long) b2 & 0xff) << 16)
				| (((long) b1 & 0xff) << 8) | (((long) b0 & 0xff) << 0));
	}

	public static Point readPoint(RandomAccessFile fh, int offset)
			throws IOException {
		// int pointsize = Point.sizeof();
		byte[] buf = new byte[Point.sizeof()];
		fh.read(buf);
		// ByteBuffer buf2 = ByteBuffer.wrap(buf);
		long timestamp = makeInt(buf[3], buf[2], buf[1], buf[0]) & 0xffffffffL;
		// Remove // the
		// sign

		// long timestamp = buf.getInt();
		double value = Double.longBitsToDouble(makeLong(buf[11], buf[10],
				buf[9], buf[8], buf[7], buf[6], buf[5], buf[4]));

		Point p = new Point();
		p.value = (float) value;
		p.timestamp = timestamp;
		return p;
	}

	public static void writePoint(RandomAccessFile fh, int offset, Point p)
			throws IOException {
		fh.seek(offset);
		fh.writeInt((int) p.timestamp);
		fh.writeDouble(p.value);
	}
}
