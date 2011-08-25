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

	public synchronized static void create(String path,
			String timePerPoint_timeToStore) {
		path = path.replace('\\', '/');
		String[] options = new String[] {
				"./src/test/python/whisper-create.py", path,
				timePerPoint_timeToStore };
		try {

//			File f = new File(path);
//			PythonInterpreter python = new PythonInterpreter();
//
//			python.exec("import sys");
//			python.exec("import os");
//			// python.exec("os.chdir('src/test/python/')");
//			python.exec("from whisper.py import whisper");
//			// python.exec("import whisper.py");
//			// python.execfile( "./src/test/python/whisper.py");
//			// python.e("sys.argv = ['', 'my', 'args', 'here']");
//			python.execfile(f.getAbsolutePath());
//			// python.exec(options[0]+" "+options[1]+" "+options[2]);
//			python.cleanup();
			// System.out.println(options);
			// jython.run(options);

			// PySystemState.initialize();
			jython.main(options);

			// System.gc();
			// jython.shutdownInterpreter();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
