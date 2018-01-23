package cz.cuni.mff.d3s.spl;


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Mojo(name = "data_saver", defaultPhase = LifecyclePhase.VERIFY)
public class JMHDataSaver extends AbstractMojo {

	@Parameter(property = "data_saver.revision_id", defaultValue = "")
	private String revisionID = "";

	@Parameter(property = "data_saver.benchmarks_jar", defaultValue = "${project.build.directory}/${uberjar.name}.jar", required = true)
	private String jmhJar;

	@Parameter(property = "data_saver.result_path", defaultValue = "${project.basedir}/jmh_results", required = true)
	private String resultPath;

	@Parameter(property = "data_saver.additional_options", defaultValue = "")
	private String additionalOpts = "";

	@Parameter(property = "data_saver.skip", defaultValue = "false")
	private boolean skip = false;

	@Override
	public void execute() throws MojoExecutionException {
		if (skip) {
			getLog().info("Collecting data skipped by user configuration");
			return;
		}

		try {
			fillRevisionId();
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
		List<String> args = new ArrayList<>();
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

		return waitForExitCode(p);
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

	private void fillRevisionId() {
		if (revisionID.isEmpty()) {
			revisionID = "last";
			try {
				Process commitProc = new ProcessBuilder("git", "rev-parse", "HEAD")
						.directory(new File(System.getProperty("user.dir"))).start();
				String commit = runProcessWithOutput(commitProc);
				String timestamp = Long.toString(System.currentTimeMillis() / 1000L);
				if (commit != null && !commit.isEmpty()) {
					revisionID = String.format("%1$s-%2$s", timestamp, commit);
				}
			} catch (Exception ignored) {
			}
		}
	}

	private String runProcessWithOutput(Process p) throws IOException {
		String outputLine = "";
		BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream()));
		outputLine = is.readLine();

		waitForExitCode(p);
		return outputLine;
	}

	private int waitForExitCode(Process p) {
		int returnCode;
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
}
