package org.github.whisper4j.test;

import java.io.File;
import java.net.URL;
import java.util.Properties;

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
import org.python.core.Py;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;
import org.python.util.jython;

public class Util {
	public static void delete(String path) {
		File f = new File(path);
		if (f.exists() == false) {
			return;
		} else {
			f.delete();
		}
	}

	/**
	 * Calls whisper-create.py with the given arguments
	 * 
	 * @param path
	 * @param timePerPoint_timeToStore
	 */
	public synchronized static void create(String path,
			String timePerPoint_timeToStore) {
		path = path.replace('\\', '/');
		try {
			PythonInterpreter python = new PythonInterpreter();
			python.exec("import sys");
			python.exec("sys.path.append(\"./src/test/python/\")");
			python.exec("sys.argv = ['whisper-create.py', '" + path + "', '"
					+ timePerPoint_timeToStore + "']");
			python.execfile("./src/test/python/whisper-create.py");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Calls whisper-create.py with the given arguments
	 * 
	 * @param path
	 * @param timePerPoint_timeToStore
	 */
	public synchronized static void update(String path,
			String timePerPoint_timeToStore) {
		path = path.replace('\\', '/');
		try {
			PythonInterpreter python = new PythonInterpreter();
			python.exec("import sys");
			python.exec("sys.path.append(\"./src/test/python/\")");
			python.exec("sys.argv = ['whisper-update.py', '" + path + "', '"
					+ timePerPoint_timeToStore + "']");
			python.execfile("./src/test/python/whisper-update.py");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
