package cz.cuni.mff.d3s.spl;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import static org.apache.maven.plugins.annotations.ResolutionScope.COMPILE_PLUS_RUNTIME;


@Mojo(name = "spl_annotation", requiresProject = true, requiresDependencyResolution = COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class SPLFormulaGeneration extends AbstractMojo {

	@Parameter(defaultValue = "${project}", required = true)
	private MavenProject project;

	@Parameter(defaultValue = "${project.build.directory}/generated-sources/spl_annotations", required = true)
	private File outputDirectory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("Reading resource 'SPLFormula.java'");
		InputStream SPLFormulaStream = this.getClass().getResourceAsStream("/SPLFormula.java");

		try {
			File finalDir = new File(outputDirectory, "cz/cuni/mff/d3s/spl");

			getLog().info("Creating directory structure '" + finalDir.toString() + "'");
			finalDir.mkdirs();

			getLog().info("Copying 'SPLFormula.java' to '" + finalDir.toString() + "' directory");
			Files.copy(SPLFormulaStream, new File(finalDir, "SPLFormula.java").toPath());
		} catch (IOException e) {
			getLog().error(e.getMessage());
		}

		getLog().info("Adding output directory to CLASSPATH");
		project.addCompileSourceRoot(outputDirectory.getPath());
	}
}
