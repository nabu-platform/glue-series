# Lazy data series

This package introduces haskell-esque lazy data series that can be manipulated. It makes extensive use of lambdas so they must be enabled.

## Methods

### series.unfold

This creates a new series, you can pass it a number, a string or a lambda, for example:

```python
fibonacci = lambda(t2: 0, t1: 1, t2 + t1)
for (unfold(fibonacci))
	echo($value)
```

This will loop indefinitely and generate fibonacci sequence numbers (as a long so they can overflow).

A few things to note:

- The amount of parameters for the lambda determines how many of the past values you get back to calculate the new value
	- The order is from oldest to newest, so "...t-5, t-4, t-3,t-2,t-1"
- The default values of said parameters (if set) are **included** in the series, so this will print:

```
0
1
1
2
3
5
8
13
21
34
...
```

### series.limit

This limits a series to a certain number of items (most series are infinite):

```python
fibonacci = lambda(t2: 0, t1: 1, t2 + t1)
for (limit(10, unfold(fibonacci)))
	echo($value)
```

This will print out the 10 first numbers of the fibonacci sequence.

### series.offset

This skips a certain amount of items in a list, for example if we wanted the next 10 numbers from the fibonacci sequence we could do:

```python
fibonacci = lambda(t2: 0, t1: 1, t2 + t1)
for (limit(10, offset(10, unfold(fibonacci))))
        echo($value)
```

This would print:

```
55
89
144
233
377
610
987
1597
2584
4181
```

### series.fold

You can take multiple series and combine them using a lambda function by calling the fold method:

```python
fibonacci = lambda(t2: 0, t1: 1, t2 + t1)
lucas = lambda(t2: 1, t1: 2, t2 + t1)

for (limit(10, fold(lambda(x, y, x * y), fibonacci, lucas)))
        echo($value)
```

This would print:

```
0
2
3
10
24
65
168
442
1155
3026
```

Note that the lambda that is given **must** have as many input parameters as the amount of series passed in.
