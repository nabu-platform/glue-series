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

	public LambdaSeriesGenerator(Lambda lambda) {
		this.lambda = lambda;
	}
	
	@Override
	public Iterable<Object> newSeries() {
		return new Iterable<Object>() {
			@Override
			public Iterator<Object> iterator() {
				return new Iterator<Object>() {
					
					private List<Object> history = new ArrayList<Object>();
					
					private Queue<Object> prefill = new ArrayDeque<Object>(); {
						boolean hasValue = false;
						for (ParameterDescription description : lambda.getDescription().getParameters()) {
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
					
					@Override
					public boolean hasNext() {
						return true;
					}
					
					@SuppressWarnings("rawtypes")
					@Override
					public Object next() {
						Object response;
						if (!prefill.isEmpty()) {
							response = prefill.poll();
						}
						else {
							LambdaExecutionOperation lambdaOperation = new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
								lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
							lambdaOperation.add(new QueryPart(Type.STRING, "anonymous"));
							for (int i = 0; i < lambda.getDescription().getParameters().size(); i++) {
								NativeOperation<?> operation = new NativeOperation();
								operation.add(new QueryPart(Type.UNKNOWN, i < history.size() ? history.get(i) : null));
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
						if (history.size() > lambda.getDescription().getParameters().size()) {
							history.remove(0);
						}
						return response;
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
