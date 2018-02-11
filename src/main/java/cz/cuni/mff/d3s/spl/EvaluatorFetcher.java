package cz.cuni.mff.d3s.spl;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.URLResolver;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;


@Mojo(name = "evaluator_fetcher", defaultPhase = LifecyclePhase.VERIFY)
public class EvaluatorFetcher extends AbstractMojo {

	@Parameter(property = "evaluator_fetcher.save_dir", defaultValue = "${project.basedir}/jmh_results", required = true)
	private String evaluatorSaveDir;

	@Parameter(property = "evaluator_fetcher.version", defaultValue = "1.0.2", required = true)
	private String evaluatorVersion;

	private String artifactId = "spl-evaluation-java";

	private String groupId = "cz.cuni.mff.d3s.spl";

	@Override
	public void execute() {
		String jarName = String.format("%1$s/%2$s-%3$s.jar", evaluatorSaveDir, artifactId, evaluatorVersion);
		File outputFile = new File(jarName);
		if(outputFile.exists() && !outputFile.isDirectory()) {
			getLog().info("File " + jarName + " already exists.");
			return;
		}

		//creates clear ivy settings
		IvySettings ivySettings = new IvySettings();
		//url resolver for configuration of maven repo
		URLResolver resolver = new URLResolver();
		resolver.setM2compatible(true);
		resolver.setName("central");
		//you can specify the url resolution pattern strategy
		resolver.addArtifactPattern(
				"http://repo1.maven.org/maven2/[organisation]/[module]/[revision]/[artifact](-[revision]).[ext]");
		//adding maven repo resolver
		ivySettings.addResolver(resolver);
		//set to the default resolver
		ivySettings.setDefaultResolver(resolver.getName());
		//creates an Ivy instance with settings
		Ivy ivy = Ivy.newInstance(ivySettings);

		try {
			File ivyfile = File.createTempFile("ivy", ".xml");
			ivyfile.deleteOnExit();

			String[] dep = new String[] {groupId, artifactId, evaluatorVersion};

			DefaultModuleDescriptor md =
					DefaultModuleDescriptor.newDefaultInstance(ModuleRevisionId.newInstance(dep[0],
							dep[1] + "-caller", "working"));

			DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(md,
					ModuleRevisionId.newInstance(dep[0], dep[1], dep[2]), false, false, true);
			md.addDependency(dd);

			//creates an ivy configuration file
			XmlModuleDescriptorWriter.write(md, ivyfile);

			String[] confs = new String[] {"default"};
			ResolveOptions resolveOptions = new ResolveOptions().setConfs(confs);

			//init resolve report
			ResolveReport report = ivy.resolve(ivyfile.toURI().toURL(), resolveOptions);

			//so you can get the jar library
			File jarArtifactFile = report.getAllArtifactsReports()[0].getLocalFile();

			FileChannel src = new FileInputStream(jarArtifactFile).getChannel();
			FileChannel dest = new FileOutputStream(outputFile).getChannel();
			dest.transferFrom(src, 0, src.size());

		} catch (Exception e) {
			getLog().error("Error downloading " + jarName + ", message: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
