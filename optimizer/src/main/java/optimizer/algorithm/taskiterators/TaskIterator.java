package optimizer.algorithm.taskiterators;

import core.structs.Tuple;
import optimizer.algorithm.graph.Graph;

import java.util.Iterator;
import java.util.concurrent.Callable;

public interface TaskIterator extends Iterator<Callable<Tuple<Graph, Integer>>> {

}
