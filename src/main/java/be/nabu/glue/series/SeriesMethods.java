package be.nabu.glue.series;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.api.EnclosedLambda;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.glue.series.api.SeriesGenerator;
import be.nabu.glue.series.impl.LambdaSeriesGenerator;
import be.nabu.glue.series.impl.LongGenerator;
import be.nabu.glue.series.impl.StringGenerator;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "series")
public class SeriesMethods {

	@SuppressWarnings("rawtypes")
	public static Iterable<?> offset(@GlueParam(name = "limit") final long offset, @GlueParam(name = "series") final Iterable<?> iterable) {
		return new Iterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {
					private Iterator parent = iterable.iterator(); {
						for (long i = 0; i < offset; i++) {
							if (parent.hasNext()) {
								parent.next();
							}
							else {
								throw new IllegalArgumentException("Can not skip to offset " + offset);
							}
						}
					}
					@Override
					public boolean hasNext() {
						return parent.hasNext();
					}
					@Override
					public Object next() {
						return parent.next();
					}
				};
			}
		};
	}
	
	@SuppressWarnings("rawtypes")
	public static Iterable<?> limit(@GlueParam(name = "limit") final long limit, @GlueParam(name = "series") final Iterable<?> iterable) {
		return new Iterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {
					private Iterator parent = iterable.iterator();
					private long index;
					@Override
					public boolean hasNext() {
						return index < limit && parent.hasNext();
					}
					@Override
					public Object next() {
						return index++ < limit ? parent.next() : null;
					}
				};
			}
		};
	}
	
	public static Iterable<?> unfold(@GlueParam(name = "series") Object series) {
		SeriesGenerator<?> generator;
		if (series instanceof Number) {
			generator = new LongGenerator(((Number) series).longValue());
		}
		else if (series instanceof String) {
			generator = new StringGenerator((String) series);
		}
		else if (series instanceof Lambda) {
			generator = new LambdaSeriesGenerator((Lambda) series);
		}
		else {
			throw new IllegalArgumentException("Can not unfold into a series");
		}
		return generator.newSeries();
	}
	
	@SuppressWarnings("rawtypes")
	public static Iterable<?> fold(@GlueParam(name = "lambda") final Lambda lambda, @GlueParam(name = "series") Object...objects) {
		if (objects == null || objects.length == 0) {
			return null;
		}
		final List<Iterable<?>> iterables = new ArrayList<Iterable<?>>();
		for (Object object : objects) {
			if (object instanceof Iterable) {
				iterables.add((Iterable<?>) object);
			}
			else {
				iterables.add(unfold(object));
			}
		}
		if (lambda.getDescription().getParameters().size() != iterables.size()) {
			throw new IllegalArgumentException("The lambda does not have enough parameters to process the series");
		}
		return new Iterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {
					
					private List<Iterator> iterators = new ArrayList<Iterator>(); {
						for (Object iterable : iterables) {
							iterators.add(toIterable(iterable).iterator());
						}
					}
					@Override
					public boolean hasNext() {
						for (Iterator iterator : iterators) {
							if (!iterator.hasNext()) {
								return false;
							}
						}
						return true;
					}

					@SuppressWarnings({ "unchecked" })
					@Override
					public Object next() {
						if (hasNext()) {
							LambdaExecutionOperation lambdaOperation = new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
								lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
							List parameters = new ArrayList();
							for (Iterator iterator : iterators) {
								parameters.add(iterator.next());
							}
							try {
								return lambdaOperation.evaluateWithParameters(ScriptRuntime.getRuntime().getExecutionContext(), parameters.toArray());
							}
							catch (EvaluationException e) {
								throw new RuntimeException(e);
							}
						}
						else {
							return null;
						}
					}
					
				};
			}
		};
	}
	
	private static Iterable<?> toIterable(Object object) {
		if (object instanceof Object[]) {
			return Arrays.asList((Object[]) object);
		}
		else if (object instanceof Iterable) {
			return (Iterable<?>) object;
		}
		else {
			throw new ClassCastException("Can not cast '" + object + "' to Iterable");
		}
	}
}
