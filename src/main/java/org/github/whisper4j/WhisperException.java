package org.github.whisper4j;

public class WhisperException extends Exception {
	public WhisperException(String msg) {
		//Base class for whisper exceptions.
		super(msg);
	}

	public WhisperException(String msg, Exception cause) {
		super(msg,cause);
	}
}
