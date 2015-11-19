package be.nabu.glue.series.api;

public interface SeriesGenerator<T> {
	public Iterable<T> newSeries();
	public Class<T> getSeriesClass();
}
