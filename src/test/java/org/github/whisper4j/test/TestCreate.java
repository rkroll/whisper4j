package org.github.whisper4j.test;

import java.io.File;
import java.net.URL;
import javax.swing.JTable.PrintMode;

import org.github.whisper4j.AggregationMethod;
import org.github.whisper4j.ArchiveInfo;
import org.github.whisper4j.Header;
import org.github.whisper4j.Whisper;
import org.github.whisper4j.Point;
import org.github.whisper4j.RetentionDef;
import org.github.whisper4j.TimeInfo;
import org.github.whisper4j.UnitMultipliers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCreate {
	@Test
	public void test_12h_2y() throws Exception {
		Util.delete("./temp.wsp");
		// maxRetention: 63072000
		// fileSize: 17548
		// aggregationMethod: average
		// xFilesFactor: 0.5
		//
		// Archive 0
		// points: 1460
		// secondsPerPoint: 43200
		// offset: 28
		// size: 17520
		// retention: 63072000

		Whisper jisper = new Whisper();
		RetentionDef[] defs = new RetentionDef[1];
		defs[0] = RetentionDef
				.calc(12, UnitMultipliers.h, 2, UnitMultipliers.y);
		Assert.assertEquals(1460, defs[0].point);
		Assert.assertEquals(43200, defs[0].secondsPerPoint);

		jisper.create("./temp.wsp", defs, 0.5f, AggregationMethod.Average);
		Header header = jisper.info("./temp.wsp");
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
	public void testUpdate() throws Exception {
		String testFile = TestReadHeader.getWhistperFile(getClass(), "15m_8.wsp");

		Util.delete(testFile);
		Util.create(testFile, "15m:8");
		
		int oneMinuteFromNow = Whisper.time() + 60;
		long value = 12345;
		Util.update(testFile, oneMinuteFromNow+":"+value);

		Whisper jisper = new Whisper();

		TimeInfo timeInfo = jisper.fetch(testFile, oneMinuteFromNow - 120, oneMinuteFromNow + 120);
		Assert.assertNotNull(timeInfo);
		
		for(Point p : timeInfo.points){
			if(p != null && p.timestamp != 0){
				System.out.println(p);
			}
		}
	}

}
