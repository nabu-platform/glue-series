package be.nabu.glue.series.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.EnclosedLambda;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.impl.GlueUtils;
import be.nabu.glue.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.glue.series.api.SeriesGenerator;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.impl.NativeOperation;

/**
 * To generate series with lambdas, we have the ability to set default values
 * These default values however are also part of the series! they are the initialization
 * 
 * The parameters in a lambda function are seen as the past values for the list, so for example if you have
 * lambda(x, y, x + y)
 * 
 * the x is actually t-2, the y is t-1 and "x+y" calculates t based on them
 * 
 * If for example you have no default value for t-2 but you do have one for t-1, it is part of the series (the beginning actually)
 * If you have a value for t-2 but not for t-1, this will generate an error as it is missing something in the list
 */
public class LambdaSeriesGenerator implements SeriesGenerator<Object> {

	private Lambda lambda;
	private Iterable<?> iterable;

	public LambdaSeriesGenerator(Lambda lambda) {
		this.lambda = lambda;
	}
	
	public LambdaSeriesGenerator(Lambda lambda, Iterable<?> iterable) {
		this.lambda = lambda;
		this.iterable = iterable;
	}
	
	@Override
	public Iterable<Object> newSeries() {
		return new Iterable<Object>() {
			@Override
			public Iterator<Object> iterator() {
				return new Iterator<Object>() {
					private Iterator<?> iterator = iterable == null ? null : iterable.iterator();
					
					private List<Object> history = new ArrayList<Object>();
					
					private Queue<Object> prefill = new ArrayDeque<Object>(); {
						if (iterator == null) {
							boolean hasValue = false;
							List<ParameterDescription> parameters = lambda.getDescription().getParameters();
							for (int i = 0; i < parameters.size() - (iterator == null ? 0 : 1); i++) {
								ParameterDescription description = parameters.get(i);
								Object defaultValue = description.getDefaultValue();
								// no default value
								// but we encountered one before so throw exception (t-2 has value, t-1 doesn't)
								if (defaultValue == null && hasValue) {
									throw new IllegalArgumentException("Missing default value for: " + description.getName());
								}
								else if (!hasValue) {
									hasValue = true;
								}
								prefill.add(defaultValue);
							}
						}
					}
					
					@Override
					public boolean hasNext() {
						return iterator == null || iterator.hasNext();
					}
					
					@SuppressWarnings("rawtypes")
					@Override
					public Object next() {
						// can not return this as a callable because the next element in the series is dependent on the previous
						// this means it has to happen in the correct sequence, returning a callable could trigger parallel execution
						Object response;
						if (!prefill.isEmpty()) {
							response = prefill.poll();
						}
						else {
							LambdaExecutionOperation lambdaOperation = new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
								lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
							lambdaOperation.add(new QueryPart(Type.STRING, "anonymous"));
							for (int i = 0; i < lambda.getDescription().getParameters().size() - (iterator == null ? 0 : 1); i++) {
								NativeOperation<?> operation = new NativeOperation();
								operation.add(new QueryPart(Type.UNKNOWN, i < history.size() ? history.get(i) : null));
								lambdaOperation.getParts().add(new QueryPart(Type.OPERATION, operation));
							}
							if (iterator != null) {
								NativeOperation<?> operation = new NativeOperation();
								Object resolveSingle = GlueUtils.resolveSingle(iterator.next());
								operation.add(new QueryPart(Type.UNKNOWN, resolveSingle));
								lambdaOperation.getParts().add(new QueryPart(Type.OPERATION, operation));
							}
							try {
								response = lambdaOperation.evaluate(ScriptRuntime.getRuntime().getExecutionContext());
							}
							catch (EvaluationException e) {
								throw new RuntimeException(e);
							}
						}
						history.add(response);
						if (history.size() > lambda.getDescription().getParameters().size() - (iterator == null ? 0 : 1)) {
							history.remove(0);
						}
						return response;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	@Override
	public Class<Object> getSeriesClass() {
		return Object.class;
	}

}
