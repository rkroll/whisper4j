package org.github.whisper4j;

public class CorruptWhisperFile extends WhisperException{

	public CorruptWhisperFile(String msg,Exception cause) {
		super(msg,cause);
	}
//	class CorruptWhisperFile(WhisperException):
//		  def __init__(self, error, path):
//		    Exception.__init__(self, error)
//		    self.error = error
//		    self.path = path
//
//		  def __repr__(self):
//		    return "<CorruptWhisperFile[%s] %s>" % (self.path, self.error)
//
//		  def __str__(self):
//		    return "%s (%s)" % (self.error, self.path)

	public CorruptWhisperFile(String msg) {
		super(msg);
	}
}
