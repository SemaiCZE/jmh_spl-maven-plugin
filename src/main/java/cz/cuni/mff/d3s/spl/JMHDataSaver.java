package cz.cuni.mff.d3s.spl;


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Mojo(name = "data_saver", defaultPhase = LifecyclePhase.VERIFY)
public class JMHDataSaver extends AbstractMojo {

    @Parameter(property = "data_saver.revision_id", defaultValue = "last")
    private String revisionID = "last";

	@Parameter(property = "data_saver.benchmarks_jar", required = true)
	private String jmhJar;

	@Parameter(property = "data_saver.result_path", required = true)
	private String resultPath;

	@Parameter(property = "data_saver.additional_options", defaultValue = "")
	private String additionalOpts = "";

    public void execute() throws MojoExecutionException {
	    try {
		    getLog().info("Collecting data from '" + jmhJar + "' to '" + resultPath +
				    "' with revision '" + revisionID + "' ...");
		    prepareDir(resultPath);
		    runJar(jmhJar, resultPath, revisionID, additionalOpts);
		    getLog().info("Data successfully saved");
	    } catch (IOException e) {
		    getLog().error(e.getMessage());
	    }
    }

	private int runJar(String argJar, String argPath, String argId, String argOpts) throws IOException {
		List<String> args = new ArrayList<String>();
		args.add("java");
		args.add("-jar");
		args.add(argJar);
		args.add("-rf");
		args.add("json");
		args.add("-rff");
		args.add(Paths.get(argPath, argId).toString());

		// push back additional options
		Collections.addAll(args, argOpts.split(" "));

		Process p =
				new ProcessBuilder(args)
						.inheritIO()
						.directory(new File(System.getProperty("user.dir"))) // current working directory
						.start();

		int returnCode = 0;
		while (true) {
			try {
				returnCode = p.waitFor();
				break;
			} catch (InterruptedException e) {
				continue;
			}
		}

		return returnCode;
	}

	private void prepareDir(String argPath) throws IOException {
		File dir = new File(argPath);
		boolean mkdirOK = dir.mkdirs();
		if (!mkdirOK) {
			if (!dir.exists()) {
				throw new IOException("Creation of directory " + argPath + " failed.");
			}
		}
	}
}
