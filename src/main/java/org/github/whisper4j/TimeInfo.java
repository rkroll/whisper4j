package org.github.whisper4j;

public class TimeInfo {
	public long fromInterval;
	public long untilInterval;
	public long step;
	public Point[] points;

	public TimeInfo(long fromInterval, long untilInterval, long step) {
		this.fromInterval = fromInterval;
		this.untilInterval = untilInterval;
		this.step = step;
	}
}
