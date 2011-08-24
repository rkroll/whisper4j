package org.github.whisper4j;

import java.util.List;

public class Header {
	public MetaData metadata;
	public List<ArchiveInfo> archiveInfo;
	
	public String toString(){
		StringBuilder b = new StringBuilder();
		if(metadata != null){
			b.append("aggregationType"+metadata.aggregationType+"\n");
			b.append("archiveCount"+metadata.archiveCount+"\n");
			b.append("maxRetention"+metadata.maxRetention+"\n");
			b.append("xFileFactor="+metadata.xFileFactor+"\n");
		}
		if(archiveInfo != null){
			for(ArchiveInfo currentArchiveInfo:archiveInfo){
				b.append("offset="+currentArchiveInfo.offset+"\n");
				b.append("points="+currentArchiveInfo.points+"\n");
				b.append("retention="+currentArchiveInfo.retention+"\n");
				b.append("secondsPerPoint="+currentArchiveInfo.secondsPerPoint+"\n");
				b.append("size="+currentArchiveInfo.size+"\n");
			}
		}
		return b.toString();
	}
}
