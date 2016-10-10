package be.nabu.glue.series;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.glue.series.api.SeriesGenerator;
import be.nabu.glue.series.impl.LambdaSeriesGenerator;
import be.nabu.glue.series.impl.LongGenerator;
import be.nabu.glue.series.impl.StringGenerator;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "series")
public class SeriesMethods {

	@GlueMethod(version = 1)
	public static List<?> list(Iterable<?> iterable) {
		List<Object> objects = new ArrayList<Object>();
		for (Object single : iterable) {
			objects.add(single);
		}
		return objects;
	}
	
	@GlueMethod(version = 1)
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
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	@GlueMethod(version = 1)
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
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	@GlueMethod(version = 1)
	public static Object aggregate(final Lambda lambda, Object...objects) {
		final Iterable<?> series = GlueUtils.toSeries(objects);
		LambdaSeriesGenerator generator = new LambdaSeriesGenerator(lambda, series);
		return generator.newSeries();
	}
	
	@GlueMethod(version = 1)
	public static Iterable<?> generate(@GlueParam(name = "series") Object series) {
		return unfold(series);
	}
	
	@GlueMethod(version = 1)
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
		else if (series instanceof Object[]) {
			return Arrays.asList((Object[]) series);
		}
		else {
			throw new IllegalArgumentException("Can not unfold into a series");
		}
		return generator.newSeries();
	}
	
	@GlueMethod(version = 1)
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
			else if (object instanceof Object[]) {
				iterables.add(Arrays.asList((Object[]) object));
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

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
					
				};
			}
		};
	}

	@GlueMethod(version = 1)
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Map hash(Lambda lambda, Object...objects) throws EvaluationException {
		if (objects == null || objects.length == 0) {
			return null;
		}
		Map map = new HashMap();
		if (lambda.getDescription().getParameters().size() != 1) {
			throw new IllegalArgumentException("The lambda does not have enough parameters to process the hash value");
		}
		for (Object object : objects) {
			LambdaExecutionOperation lambdaOperation = new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
				lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
			Object key = lambdaOperation.evaluateWithParameters(ScriptRuntime.getRuntime().getExecutionContext(), object);
			for (Object single : key instanceof Object[] ? (Object[]) key : new Object[] { key }) {
				Object current = map.get(single);
				if (current instanceof List) {
					((List) current).add(object);
				}
				else if (current != null) {
					List list = new ArrayList();
					list.add(current);
					list.add(object);
					map.put(single, list);
				}
				else {
					map.put(single, object);
				}
			}
		}
		return map;
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
