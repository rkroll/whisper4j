package org.github.whisper4j.test;

import java.io.File;
import java.net.URL;

import org.github.whisper4j.AggregationMethod;
import org.github.whisper4j.ArchiveInfo;
import org.github.whisper4j.Header;
import org.github.whisper4j.Whisper;
import org.github.whisper4j.Point;
import org.github.whisper4j.TimeInfo;
import org.junit.Assert;
import org.junit.Test;

public class TestReadHeader {
	public static String getWhistperFile(Class anyTestClass, String fileName) {
		final String clsUri = anyTestClass.getName().replace('.', '/')
				+ ".class";
		final URL url = anyTestClass.getClassLoader().getResource(clsUri);
		String clsPath = url.getPath();
		clsPath = clsPath.replace("%20", " ");
		final File root = new File(clsPath.substring(0, clsPath.length()
				- clsUri.length()));
		String str = root.getAbsolutePath() + File.separator + fileName;
		return str;
	}

	@Test
	public void test_60_1440() throws Exception {
		String testFile = getWhistperFile(getClass(), "60_1440.wsp");
		Whisper jisper = new Whisper();
		Header header = jisper.info(testFile);
		Assert.assertNotNull(header);
		Assert.assertNotNull(header.metadata);
		Assert.assertEquals(AggregationMethod.Average.getIntValue(),
				header.metadata.aggregationType);
		Assert.assertEquals("archiveCount", 1, header.metadata.archiveCount);
		Assert.assertEquals("maxRetention", 86400, header.metadata.maxRetention);
		Assert.assertEquals("xFileFactor", 0.5f, header.metadata.xFileFactor,
				0.001);

		Assert.assertNotNull(header.archiveInfo);
		Assert.assertEquals(1, header.archiveInfo.size());

		ArchiveInfo info = header.archiveInfo.get(0);
		Assert.assertEquals("Points", 1440, info.points);
		Assert.assertEquals("secondsPerPoint", 60, info.secondsPerPoint);
		Assert.assertEquals("offset", 28, info.offset);
		Assert.assertEquals("size", 17280, info.size);
		Assert.assertEquals("retention", 86400, info.retention);
	}

	@Test
	public void test_12h_2y() throws Exception {
		String testFile = getWhistperFile(getClass(), "12h_2y.wsp");
		Whisper jisper = new Whisper();
		Header header = jisper.info(testFile);
		Assert.assertNotNull(header);
		Assert.assertNotNull(header.metadata);
		Assert.assertEquals(AggregationMethod.Average.getIntValue(),
				header.metadata.aggregationType);
		Assert.assertEquals("archiveCount", 1, header.metadata.archiveCount);
		Assert.assertEquals("maxRetention", 63072000,
				header.metadata.maxRetention);
		Assert.assertEquals("xFileFactor", 0.5f, header.metadata.xFileFactor,
				0.001);

		Assert.assertNotNull(header.archiveInfo);
		Assert.assertEquals(1, header.archiveInfo.size());

		ArchiveInfo info = header.archiveInfo.get(0);
		Assert.assertEquals("Points", 1460, info.points);
		Assert.assertEquals("secondsPerPoint", 43200, info.secondsPerPoint);
		Assert.assertEquals("offset", 28, info.offset);
		Assert.assertEquals("size", 17520, info.size);
		Assert.assertEquals("retention", 63072000, info.retention);
	}

	@Test
	public void test_15m_8() throws Exception {
		String testFile = getWhistperFile(getClass(), "15m_8.wsp");

		Whisper jisper = new Whisper();
		Header header = jisper.info(testFile);
		Assert.assertNotNull(header);
		Assert.assertNotNull(header.metadata);
		Assert.assertEquals(AggregationMethod.Average.getIntValue(),
				header.metadata.aggregationType);
		Assert.assertEquals("archiveCount", 1, header.metadata.archiveCount);
		Assert.assertEquals("maxRetention", 7200, header.metadata.maxRetention);
		Assert.assertEquals("xFileFactor", 0.5f, header.metadata.xFileFactor,
				0.001);

		Assert.assertNotNull(header.archiveInfo);
		Assert.assertEquals(1, header.archiveInfo.size());

		ArchiveInfo info = header.archiveInfo.get(0);
		Assert.assertEquals("Points", 8, info.points);
		Assert.assertEquals("secondsPerPoint", 900, info.secondsPerPoint);
		Assert.assertEquals("offset", 28, info.offset);
		Assert.assertEquals("size", 96, info.size);
		Assert.assertEquals("retention", 7200, info.retention);
	}

	@Test
	public void testFetch() throws Exception {
		String testFile = getWhistperFile(getClass(), "out.wsp");

		Whisper jisper = new Whisper();
		Header header = jisper.info(testFile);
		Assert.assertNotNull(header);
		System.out.println(header.toString());
		Assert.assertNotNull(header.metadata);
		Assert.assertEquals(AggregationMethod.Average.getIntValue(),
				header.metadata.aggregationType);
		Assert.assertEquals("archiveCount", 1, header.metadata.archiveCount);
		Assert.assertEquals("maxRetention", 86400, header.metadata.maxRetention);
		Assert.assertEquals("xFileFactor", 0.5f, header.metadata.xFileFactor,
				0.001);

		Assert.assertNotNull(header.archiveInfo);
		Assert.assertEquals(1, header.archiveInfo.size());

		ArchiveInfo info = header.archiveInfo.get(0);
		Assert.assertEquals("Points", 1440, info.points);
		Assert.assertEquals("secondsPerPoint", 60, info.secondsPerPoint);
		Assert.assertEquals("offset", 28, info.offset);
		Assert.assertEquals("size", 17280, info.size);
		Assert.assertEquals("retention", 86400, info.retention);

		// TimeInfo result = jisper.fetch(testFile, (int)1313585674,
		// 1313672074);
		TimeInfo result = jisper.fetch(testFile, Integer.MIN_VALUE,
				Integer.MAX_VALUE);

		// 1313659560 1.000000
		// 1313659620 None
		// ...
		// 1313660400 None
		// 1313660460 None
		// 1313660520 None
		// 1313660580 2.000000
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.points);
		Assert.assertEquals(
				"points read is not the same as what archiveinfo says",
				info.points, result.points.length);

		int validPoints = 0;
		for (Point point : result.points) {
			if (point != null && point.timestamp == 1313659560) {
				Assert.assertEquals(1.0f, point.value, 0.00001);
				validPoints++;
			}
			if (point != null && point.timestamp == 1313660580) {
				Assert.assertEquals(2.0f, point.value, 0.00001);
				validPoints++;
			}
		}
		// Assert.assertEquals(2,validPoints);
	}

}
