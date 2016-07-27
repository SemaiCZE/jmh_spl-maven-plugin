package cz.cuni.mff.d3s.spl;


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.BenchmarkListEntry;
import org.openjdk.jmh.runner.format.OutputFormat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

@Mojo(name = "formula_extractor", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class FormulaExtractor extends AbstractMojo {
	private String outputPath = "META-INF/SPLFormulas";
	private Map<String, String> formulas;
	/**
	 * Cache - for fully qualified class name is cached instance of Class class
	 * and it's SPL formula (null if not present).
	 */
	private Map<String, Map.Entry<Class<?>, String>> classCache;

	public FormulaExtractor() {
		formulas = new HashMap<String, String>();
		classCache = new HashMap<String, Map.Entry<Class<?>, String>>();
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			getSPLFormulas();
			saveSPLFormulas();
		} catch (Throwable e) {
			getLog().error("Error during getting SPL formulas: " + e.getMessage());
		}
	}

	private void getSPLFormulas() throws ClassNotFoundException, NoSuchMethodException {
		Set<BenchmarkListEntry> benchmarks =
				//BenchmarkList.defaultList().getAll(new NullOutputFormat(), new LinkedList<String>());
				BenchmarkList.fromFile("target/classes/META-INF/BenchmarkList").getAll(new NullOutputFormat(), new LinkedList<String>());

		for (BenchmarkListEntry benchmark : benchmarks) {
			// lookup to cache for class of this name
			Class benchmarkClass;
			String classFormula;
			if (classCache.containsKey(benchmark.getUserClassQName())) {
				Map.Entry<Class<?>, String> classInfo = classCache.get(benchmark.getUserClassQName());
				benchmarkClass = classInfo.getKey();
				classFormula = classInfo.getValue();
			} else {
				benchmarkClass = Class.forName(benchmark.getUserClassQName());
				classFormula = null;
				if (benchmarkClass.isAnnotationPresent(SPLFormula.class)) {
					Annotation SPLAnnotation = benchmarkClass.getAnnotation(SPLFormula.class);
					SPLFormula formula = (SPLFormula) SPLAnnotation;
					classFormula = formula.value();
				}
				classCache.put(benchmark.getUserClassQName(),
						new AbstractMap.SimpleEntry<Class<?>, String>(benchmarkClass, classFormula));
			}

			String methodName = benchmark.getUsername().substring(benchmark.getUserClassQName().length() + 1);
			// It's ok to call just getMethod() without arguments, because JMH testing methods can't
			// be overloaded by different argument number or types.
			Method benchmarkMethod = benchmarkClass.getMethod(methodName);

			String methodFormula = null;
			if (benchmarkMethod.isAnnotationPresent(SPLFormula.class)) {
				Annotation SPLAnnotation = benchmarkMethod.getAnnotation(SPLFormula.class);
				SPLFormula formula = (SPLFormula) SPLAnnotation;
				methodFormula = formula.value();
			}

			if (methodFormula != null) {
				formulas.put(benchmarkClass.getCanonicalName() + "." + benchmarkMethod.getName(), methodFormula);
			} else if (classFormula != null) {
				formulas.put(benchmarkClass.getCanonicalName() + "." + benchmarkMethod.getName(), classFormula);
			} else {
				// no formula for this method -> will not be processed by SPL framework
			}
		}
	}

	private void saveSPLFormulas() throws FileNotFoundException, UnsupportedEncodingException {
		PrintWriter writer = new PrintWriter(outputPath, "UTF-8");
		for (Map.Entry<String, String> methodEntry : formulas.entrySet()) {
			writer.write(methodEntry.getKey());
			writer.write(":");
			writer.write(methodEntry.getValue());
		}
		writer.close();
	}
}

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