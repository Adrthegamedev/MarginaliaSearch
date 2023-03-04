# Array Library

The array library offers easy allocation of large [memory mapped files](https://en.wikipedia.org/wiki/Memory-mapped_file) 
with less performance overhead than the traditional `buffers[i].get(j)`-style constructions
java often leads to given its suffocating 2 Gb ByteBuffer size limitation. 

It accomplishes this by delegating block oerations down to the appropriate page. If the operation
crosses a page boundary, it is not delegated and a bit slower.

It's a very C++-style library that does unidiomatic things with interface default 
functions to get diamond inheritance.

# Quick demo:
```java
var array = LongArray.mmapForWriting(Path.of("/tmp/test"), 1<<16);

array.transformEach(50, 1000, (pos, val) -> Long.hashCode(pos));
array.quickSort(50, 1000);
if (array.binarySearch(array.get(100), 50, 1000) >= 0) {
    System.out.println("Nevermind, I found it!");
}

array.range(50, 1000).fill(0, 950, 1);
array.forEach(0, 100, (pos, val) -> {
    System.out.println(pos + ":" + val);
});

```