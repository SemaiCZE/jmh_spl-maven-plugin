package cz.cuni.mff.d3s.spl;


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.BenchmarkListEntry;
import org.openjdk.jmh.runner.format.OutputFormat;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

@Mojo(name = "formula_extractor", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class FormulaExtractor extends AbstractMojo {

	@Parameter(property = "project.build.outputDirectory")
	private String buildDirectory;

	@Component
	private MavenProject project;

	private final String outputPathSuffix = "/META-INF/SPLFormulas";
	private Map<String, String> formulas;

	/**
	 * Cache - for fully qualified class name is cached instance of Class class
	 * and it's SPL formula (null if not present).
	 */
	private Map<String, Map.Entry<Class<?>, String>> classCache;

	public FormulaExtractor() {
		formulas = new HashMap<>();
		classCache = new HashMap<>();
	}

	@Override
	public void execute() {
		try {
			getSPLFormulas();
			saveSPLFormulas();
		} catch (Throwable e) {
			getLog().error("Error during getting SPL formulas: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Read SPL formulas from project sources (using some JMH API for getting
	 * benchmarks).
	 *
	 * @throws ClassNotFoundException
	 */
	private void getSPLFormulas() throws ClassNotFoundException {
		Set<BenchmarkListEntry> benchmarks = BenchmarkList
				.fromFile(buildDirectory + "/META-INF/BenchmarkList")
				.getAll(new NullOutputFormat(), new LinkedList<>());

		ClassLoader alteredClassLoader = getClassLoader();

		for (BenchmarkListEntry benchmark : benchmarks) {
			// lookup to cache for class of this name
			Class benchmarkClass;
			String classFormula;
			String methodName = benchmark.getUsername().substring(benchmark.getUserClassQName().length() + 1);

			if (classCache.containsKey(benchmark.getUserClassQName())) {
				Map.Entry<Class<?>, String> classInfo = classCache.get(benchmark.getUserClassQName());
				benchmarkClass = classInfo.getKey();
				classFormula = classInfo.getValue();
			} else {
				try {
					benchmarkClass = Class.forName(benchmark.getUserClassQName(), true, alteredClassLoader);
				} catch (NoClassDefFoundError ignored) {
					getLog().warn("Benchmark class " + benchmark.getUserClassQName() + " for method " +
							methodName + " could not be initialized. No SPL formulas are saved for this method.");
					continue;
				}
				classFormula = null;
				if (benchmarkClass.isAnnotationPresent(SPLFormula.class)) {
					Annotation SPLAnnotation = benchmarkClass.getAnnotation(SPLFormula.class);
					SPLFormula formula = (SPLFormula) SPLAnnotation;
					classFormula = formula.value();
				}
				classCache.put(benchmark.getUserClassQName(),
						new AbstractMap.SimpleEntry<>(benchmarkClass, classFormula));
			}

			// Can't call getMethod(), because arguments are not known. Method name is guarantied to
			// be unique by JMH, but it won't help us here. So loop over all methods and pick the
			// right one.
			// TODO: cache methods array (getMethods() can be slow operation)
			Method benchmarkMethod = null;
			for (Method benchmarkClassMethod : benchmarkClass.getMethods()) {
				if (benchmarkClassMethod.getName().equals(methodName)) {
					benchmarkMethod = benchmarkClassMethod;
					break;
				}
			}
			// make sure we found the required method
			if (benchmarkMethod == null) {
				getLog().warn("Benchmark method " + methodName + " not found. Skipping.");
				continue;
			}

			String methodFormula = null;
			if (benchmarkMethod.isAnnotationPresent(SPLFormula.class)) {
				Annotation SPLAnnotation = benchmarkMethod.getAnnotation(SPLFormula.class);
				SPLFormula formula = (SPLFormula) SPLAnnotation;
				methodFormula = formula.value();
			}

			String fullName = benchmarkClass.getCanonicalName() + "." + benchmarkMethod.getName();
			if (methodFormula != null) {
				formulas.put(fullName, methodFormula);
				getLog().info("Found method formula '" + methodFormula + "' for '" + fullName + "'");
			} else if (classFormula != null) {
				formulas.put(fullName, classFormula);
				getLog().info("Found class formula '" + classFormula + "' for '" + fullName + "'");
			} else {
				// no formula for this method0.
			}
		}

		getLog().info("Total SPL formulas found: " + formulas.size());
	}

	/**
	 * Get current classloader altered with path to compile path of project using this plugin.
	 *
	 * @return New classloader.
	 */
	private ClassLoader getClassLoader() {
		ClassLoader currentThreadClassLoader = Thread.currentThread().getContextClassLoader();

		// Add the dir with compiled classes to the classpath
		// Chain the current thread classloader
		ClassLoader urlClassLoader = currentThreadClassLoader;
		try {
			List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();
			List<URL> runtimeUrls = new ArrayList<>();
			for (String classpathElement : runtimeClasspathElements) {
				runtimeUrls.add(new File(classpathElement).toURI().toURL());
			}
			runtimeUrls.add(new File(buildDirectory).toURI().toURL());
			urlClassLoader = new URLClassLoader(runtimeUrls.toArray(new URL[] {}),
					currentThreadClassLoader);
		} catch (Exception e) {
			getLog().error("Error altering classpath: " + e.getMessage());
		}

		return urlClassLoader;
	}

	/**
	 * Save SPL formulas to file (specified by outputPath private variable).
	 * Format is line based, each line containing benchmark name, ':', SPL formula.
	 */
	private void saveSPLFormulas() throws FileNotFoundException, UnsupportedEncodingException {
		PrintWriter writer = new PrintWriter(buildDirectory + outputPathSuffix, "UTF-8");
		for (Map.Entry<String, String> methodEntry : formulas.entrySet()) {
			writer.write(methodEntry.getKey());
			writer.write(":");
			writer.write(methodEntry.getValue());
			writer.write("\n");
		}
		writer.close();
	}
}

/**
 * Output format. Not used here, but required by JMH API.
 */
class NullOutputFormat implements OutputFormat {
	@Override
	public void iteration(BenchmarkParams benchParams, IterationParams params, int iteration) {}
	@Override
	public void iterationResult(BenchmarkParams benchParams, IterationParams params, int iteration, IterationResult data) {}
	@Override
	public void startBenchmark(BenchmarkParams benchParams) {}
	@Override
	public void endBenchmark(BenchmarkResult result) {}
	@Override
	public void startRun() {}
	@Override
	public void endRun(Collection<RunResult> result) {}
	@Override
	public void print(String s) {}
	@Override
	public void println(String s) {}
	@Override
	public void flush() {}
	@Override
	public void close() {}
	@Override
	public void verbosePrintln(String s) {}
	@Override
	public void write(int b) {}
	@Override
	public void write(byte[] b) throws IOException {}
}