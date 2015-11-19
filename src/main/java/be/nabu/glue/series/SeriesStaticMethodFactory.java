package be.nabu.glue.series;

import java.util.ArrayList;
import java.util.List;

import be.nabu.glue.api.StaticMethodFactory;

public class SeriesStaticMethodFactory implements StaticMethodFactory {

	@Override
	public List<Class<?>> getStaticMethodClasses() {
		List<Class<?>> classes = new ArrayList<Class<?>>();
		classes.add(SeriesMethods.class);
		return classes;
	}
	
}
