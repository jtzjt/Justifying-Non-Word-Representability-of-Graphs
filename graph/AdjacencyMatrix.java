package graph;

//IMPORTS------------------------------/
import path.PathMatrix;
import path.PathList;
import path.Path;

import graph.SemiTransitiveReason.Reason;

import util.Matrix;
import util.EdgeData;
import word.GraphFromWord;
import word.Word;

import java.util.Observable;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import java.awt.Point;

/***BEGIN CLASS AdjacencyMatrix.java********************************************
 * Main class representing a graph as an n*n boolean adjacency matrix. Has
 * methods for adding and removing nodes, for adding, removing and toggling
 * oriented and non-oriented edges, and for checking for the existence of an
 * edge, as well as checking if one is oriented in some way or not.
 * Implements algorithms for checking the word-representability of the graph,
 * and can load a graph from a word as well as verify whether a particular word
 * represents the current graph.
 * 
 * @author julia
 *****************/public class AdjacencyMatrix extends Observable/*************/
					 implements Graph, Matrix<Boolean> {

//FIELDS-------------------------------/
private boolean[][] matrix;
static final int MIN_SIZE = 2;
static final int MAX_SIZE = 100;
private List<String> labels;


//CONSTRUCTORS--------------------------/
/* param sz - the TOTAL size of the graph in number of nodes.
 */
public AdjacencyMatrix (int sz) {
	if (sz < MIN_SIZE) sz = MIN_SIZE;
	if (sz > MAX_SIZE) sz = MAX_SIZE;

	matrix = new boolean[sz][sz];
	labels = new ArrayList<String>(sz);
	//using default labels 1-sz
	for (int i = 1; i <= sz; i++)
		labels.add(i+"");
}


/* Constructor that sets matrix and labels directly. Used for deep-copying
 */
public AdjacencyMatrix (boolean[][] mx, List<String> ls) {
	matrix = new boolean[mx.length][mx.length];
	labels = new ArrayList<String>(ls);
	
	for (int i = 0; i < mx.length; i++)
		System.arraycopy(mx[i], 0, matrix[i], 0, mx.length);
}


//METHODS------------------------------/
public Set<Vertex> vertices()
{	Set<Vertex> res = new HashSet<Vertex>(length());
	for (String l : labels)
		res.add(new Vertex(l));
	return res;
}


public Set<Edge> edges()
{	Set<Edge> res = new HashSet<Edge>((length() * length()) / 2);
	for (int v = 0; v < length()-1; v++)
		for (int u = v+1; u < length(); u++) {
			Vertex v_v = new Vertex(labels.get(v)),
				   u_v = new Vertex(labels.get(u));
			if (hasEdge(v,u))
				if (hasUndirEdge(v,u))
					res.add(new Edge(v_v,u_v,false));
				else if (hasDirEdge(v,u))
					res.add(new Edge(v_v,u_v,true));
				else res.add(new Edge(u_v,v_v,true));
		}
	return res;
}


public boolean wordRepresents (Word w)
{	//get all alternations in word
	Set<Edge> word = new HashSet<Edge>();
	for (String a : w)
		for (String b : w)
			if (a.equals(b))
				break;	//a repetition, so no more alternations with a
			else if (GraphFromWord.isAlterning(a, b, w))
				word.add(new Edge(new Vertex(a),new Vertex(b), false));

	//comparing with edges in graph, ignoring orientations
	Set<Edge> edges = new HashSet<Edge>(edges());
	for (Edge e : edges)
		e.directed = false;
	
	return (edges.equals(word));
}


public SemiTransitiveReason semiTransitivelyOriented() throws Exception
{	if (!oriented())
		throw new Exception("Graph is not yet fully oriented!");

	List<Path> allCycles = new ArrayList<Path>();
	List<Path> allShortcuts = new ArrayList<Path>();

	//first, produce matrix A^2
	PathMatrix pm = square();
	
	//check it has no non-zero along main diagonal
	collectAllCycles(pm, allCycles);
	
	//for-each A^i where 3 <= i <= N
	for (int i=3; i <= length(); i++) {
		//get A^i
		pm = symbolicMultiply(pm);
		
		//check that A^i has no non-zero along the main diagonal
		collectAllCycles(pm, allCycles);
		
		//evaluate the non-zero elements for shortcuts.
		collectAllShortcuts(pm, allShortcuts);

	}
	
	//if cycles or shortcuts found, return them
	if (!allCycles.isEmpty() || !allShortcuts.isEmpty()) {
		Reason r = !allCycles.isEmpty() ? Reason.CYCLE : Reason.SHORTCUT;
		return new SemiTransitiveReason(r, allCycles, allShortcuts);
	}
	
	//if all checks pass, graph is semi-transitively oriented.
	return new SemiTransitiveReason(Reason.SEMI_TRANSITIVE, null);
}


/* return a fully oriented Graph which is an orientation of the current one
 * and is semi-transitive, if one exists, or null otherwise.
 */
public AdjacencyMatrix admitsSemiTransitiveOrientation() throws Exception
{   
    if (oriented())
        throw new Exception("Graph is already fully oriented!");

    // 创建当前图的副本
    AdjacencyMatrix copy = (AdjacencyMatrix) copy();

    // 找到具有最多边的顶点（最高度顶点）
    int maxDegree = -1;
    int sourceVertexIndex = -1;
    for (int v = 0; v < length(); v++) {
        int degree = 0;
        for (int u = 0; u < length(); u++) {
            if (hasEdge(v, u)) degree++;
        }
        if (degree > maxDegree) {
            maxDegree = degree;
            sourceVertexIndex = v;
        }
    }

    Vertex sourceV = new Vertex(labels.get(sourceVertexIndex));

    // ===关键修正：安全地强制 sourceVertex 为源顶点===
    for (int u = 0; u < length(); u++) {

        if (u == sourceVertexIndex) continue;

        // 如果边不存在就跳过
        if (!hasEdge(sourceVertexIndex, u)) continue;

        boolean v_to_u = copy.matrix[sourceVertexIndex][u];
        boolean u_to_v = copy.matrix[u][sourceVertexIndex];

        // 若已经是 u→v，强制 source 必须为 source → 冲突，提前返回 null
        if (u_to_v && !v_to_u) {
            return null;  // 图不可能让此顶点成 source
        }

        // 若无冲突，则设为 source → neighbor
        copy.matrix[sourceVertexIndex][u] = true;
        copy.matrix[u][sourceVertexIndex] = false;
    }

    // ===继续原始递归过程===
    return copy.recursiveOrient();
}



/* Recursively finds the next non-oriented edge, divides-and-conquers to
 * orient it one way and then the other, until all edges are oriented. Then it
 * queries whether it is semi-transitive.
 */
public AdjacencyMatrix recursiveOrient()
{	if (!optimize())        //局部启发式（heuristic）剪枝     
		return null;

	// take next non-oriented edge
	Edge edge = null;
	for (Edge e : edges())
		if (!e.directed) {
			edge = e;
			break;
		}

	//if there are no more non-oriented edges, check for semi-transitivity
	if (edge == null)
		try {
			if (semiTransitivelyOriented().result())
				return this;
			else return null;
		//all edges are assigned, so this should never throw an exception
		} catch (Exception e) { throw new IllegalStateException(e); }
	
	
	// left = do recursiveOrient on copy after assigning edge from->to
	AdjacencyMatrix left = (AdjacencyMatrix) copy();
	left.setDirEdge(edge.from, edge.to);
	left = left.recursiveOrient();
	
	// is it semi-transitive?
	if (left != null)
		return left;
	
	
	// right = do recursiveOrient on copy after assigning edge to->from
	AdjacencyMatrix right = (AdjacencyMatrix) copy();
	right.setDirEdge(edge.to, edge.from);
	right = right.recursiveOrient();
	
	// is it semi-transitive?
	if (right != null)
		return right;
	
	return null;
}


/* Perform a few optimizations to a partially oriented graph, checking for
 * edges that must be oriented in a certain way if the graph is to be
 * semi-transitively oriented. If a situation is found where the orientation
 * cannot possibly be semi-transitive, then this method returns false.
 * Otherwise, when it gets to the end it returns true.
 */
private boolean optimize() 
{	Set<Vertex> vertices = vertices();
search:	//<- will return here if an orientation for edge is found
	for (Edge edge : edges()) if (!edge.directed) {
	//for each unassigned edge, called edge...

		//for each node i connected with one of the nodes in edge...
		for (Vertex i : vertices) {
			if (edge(edge.from, i))
				; //this one is, so proceed
			else if (edge(edge.to, i)) {
				//as above, just switch around the names for convenience
				Vertex tmp = edge.from;
				edge.from = edge.to;
				edge.to = tmp;
			} else continue; //this isn't, go to next i


			//check for a triangle with i
			if (edge(i, edge.to) && !undirEdge(i,edge.to)) {
				//check for a path indicating a potential cycle
				
				//block path from edge.from -> i -> edge.to
				if (isPath(edge.from, i, edge.to)) {
					//block a cycle
					setDirEdge(edge.from, edge.to);
					continue search;	//go to next unassigned edge

				//block path from edge.to -> i -> edge.from
				} else if (isPath(edge.to, i, edge.from)) {
					//block a cycle
					setDirEdge(edge.to, edge.from);
					continue search;	//go to next unassigned edge
				}
			}

			//now, check for cycles and shortcuts on 4 nodes
			//for each node j...
			for (Vertex j : vertices) {
				//check for a square cycle edge.from -> i -> j -> edge.to
				if (edge(i, j) && edge(j, edge.to))
					;	//this is, so proceed
				else continue; //this isn't, so go to next j

				//call edge (i,j) "opposite" for convenience
				//the two edges connect at (edge.from, opposite.from)
				//and (edge.to, opposite.to)
				Edge opposite = new Edge(i, j, false);

				int count = 0; //keep a count of diagonals
				//look for diagonal from edge.from to opposite.to
				if (edge(edge.from, opposite.to)) 
					count++;
				//look for diagonal from edge.to to opposite.from
				if (edge(edge.to, opposite.from)) 
					count++;

				//if path opposite.from -> opposite.to -> edge.to
				if (isPath(opposite.from, opposite.to, edge.to)) {

					//if there's a path
					//edge.from -> opposite.from -> opposite.to -> edge.to,
					//there is a possibility of either a shortcut or a cycle
					if (dirEdge(edge.from, opposite.from)) {
						//if there is no possibility of shortcut,
						//(ie. no diagonals are missing) block the cycle
						if (count == 2) {
							setDirEdge(edge.from, edge.to);
							continue search;	//go to next unassigned edge
						}
						//if there is, then any orientation we add creates
						//either a shortcut or a cycle, so there is no
						//possibility of semi-transitivity.
						else return false;
					}

					//if not, block and follow
					if (count != 2) { //if there's a possibility of shortcut that is!
						setDirEdge(opposite.from, edge.from);
						setDirEdge(edge.from, edge.to);
					}
					continue search;	//go to next unassigned edge
				}

				//if path opposite.to -> opposite.from -> edge.from
				if (isPath(opposite.to, opposite.from, edge.from)) {

					//if there's a path
					//edge.to -> opposite.to -> opposite.from -> edge.from,
					//there is a possibility of either a shortcut or cycle
					if (dirEdge(edge.to, opposite.to)) {
						//if there is no possibility of shortcut,
						//(ie. no diagonals are missing), block the cycle
						if (count == 2) {
							setDirEdge(edge.to, edge.from);
							continue search;	//go to next unassigned edge
						}
						//if there is, then any orientation we add creates
						//either a shortcut or a cycle, so there is no
						//possibility of semi-transitivity.
						else return false;
					}

					//if not, block and follow
					if (count != 2) { //if there's a possibility of shortcut that is!
						setDirEdge(opposite.to, edge.to);
						setDirEdge(edge.to, edge.from);
					}
					continue search;	//go to next unassigned edge
				}


				//the rest only prevents shortcuts
				if (count != 2) {

					//if edges opposite.from -> edge.from
					//and edge.to -> opposite.to
					if (dirEdge(opposite.from, edge.from) &&
							dirEdge(edge.to, opposite.to)) {
				
						//block to prevent a path
						//opposite.from -> edge.from -> edge.to -> opposite.to
						//which would cause a shortcut.
						setDirEdge(edge.to, edge.from);
						continue search;	//go to next unassigned edge
					}

					//if edges opposite.to -> edge.to
					//and edge.from -> opposite.from
					if (dirEdge(opposite.to, edge.to) &&
							dirEdge(edge.from, opposite.from)) {

						//block to prevent a path
						//opposite.to -> edge.to -> edge.from -> opposite.from
						//which would cause a shortcut.
						setDirEdge(edge.from, edge.to);
						continue search;	//go to next unassigned edge
					}
					
				} //end if (count != 2)

			} //end for-each vertex j
		} //end for-each vertex i
	} //end for-each unassigned edge
	return true;
}


/* return true if there is an oriented path traversing all specified nodes
 * _in_order_, false otherwise
 */
private boolean isPath(Vertex... nodes)
{	if (!dirEdge(nodes[0], nodes[1]))
		return false;
	
	for (int i = 1; i < nodes.length; i++)
		if (!dirEdge(nodes[i-1], nodes[i]))
			return false;

	return true;
}


/* return the square of this matrix.
 */
private PathMatrix square()
{	PathMatrix res = new PathMatrix(length());
	
	//for-each row and column of matrix...
	for (int row = 0; row < length(); row++)
		for (int column = 0; column < length(); column++) {
			
			//for-each index of row and column, add paths
			PathList pl = res.get(row,column);
			
			//for-each i, check if (row,i) and (i,column) are set
			for (int i = 0; i < length(); i++)
				if (matrix[row][i] && matrix[i][column]) {
					Path path = new Path();	pl.add(path);
					path.add(new Vertex(labels.get(row)));	//start of path
					path.add(new Vertex(labels.get(i)));
					path.add(new Vertex(labels.get(column)));	//end of path
				}
		}
	return res;
}


/* return the result of symbolic multiplication with pm
 */
private PathMatrix symbolicMultiply (PathMatrix pm)
{	if (pm.length()!=length()) throw new IllegalArgumentException();

	PathMatrix res = new PathMatrix(length());
	
	//for-each row of pm and column of matrix...
	for (int row = 0; row < pm.length(); row++)
		for (int column = 0; column < length(); column++) {
			
			//for-each i in pm(row,i) and matrix(i,column),
			//if both are set, add to the new path-matrix entry in (row,column)
			PathList pl = res.get(row,column);
			for (int i = 0; i < pm.length(); i++)
				if ( matrix[i][column] && !pm.get(row,i).isEmpty() )
					pl.addAll(pm.get(row,i).addToPaths(
								new Vertex(labels.get(column))));
		}
	return res;
}


/* Evaluates each entry in the matrix along with a given path-matrix, pm. For
 * each index where both the matrix and path-matrix are non-zero, checks each
 * path in that entry for shortcuts.
 * return a Path describing a shortcut from one node to another, if a shortcut
 * exists; null otherwise. If the method returns non-null (and provided pm
 * correctly describes the original graph) then the graph cannot be
 * semi-transitively oriented.
 */
private Path evaluateNonZeroElements (PathMatrix pm)
{	if (pm.length()!=length()) throw new IllegalArgumentException();
	
	//for-each row and column of both matrices...
	for (int row = 0; row < length(); row++)
		for (int column = 0; column < length(); column++)
			
			//check if both (row,column) are non-empty
			if ( matrix[row][column] && !pm.get(row,column).isEmpty() )
				
				//if they both are, iterate through each path in pm...
				for (Path path : pm.get(row,column)) {
					
					//... checking that, for each i-th and j-th entry in the
					//path where 1<=i<j<=(length-1), matrix(i,j) is non-zero. 
					for (int i = 0; i < path.size()-1; i++)
						for (int j = i+1; j < path.size(); j++)

							//if it IS zero, graph is non-semi-transitively
							//oriented, so return shortcut
							if (!matrix
									[labels.indexOf(path.get(i).label)]
									[labels.indexOf(path.get(j).label)])
							  return path;
				}
	return null; //no shortcut was found
}


/* Collect all cycles from the diagonal of the path matrix
 */
private void collectAllCycles (PathMatrix pm, List<Path> cycles)
{	for (int i = 0; i < pm.length(); i++) {
		PathList pl = pm.get(i, i);
		if (!pl.isEmpty()) {
			for (Path path : pl) {
				// Check if this cycle is duplicate or contains/is contained by existing cycles
				if (!isDuplicateCycle(path, cycles)) {
					// Remove any existing cycles that are contained by this new cycle
					removeContainedCycles(cycles, path);
					// Add the new cycle
					cycles.add(path);
				}
			}
		}
	}
}


/* Collect all shortcuts from the path matrix
 * A shortcut must satisfy:
 * 1. At least 4 vertices
 * 2. Exactly one source (no incoming edges) and one sink (no outgoing edges)
 * 3. A directed path from source to sink going through every vertex
 * 4. A direct edge from source to sink (shortcutting edge)
 * 5. Not transitive (exists u->v and v->z but no u->z)
 */
private void collectAllShortcuts (PathMatrix pm, List<Path> shortcuts)
{	if (pm.length() != length()) throw new IllegalArgumentException();
	
	//for-each row and column of both matrices...
	for (int row = 0; row < length(); row++)
		for (int column = 0; column < length(); column++) {
			
			// Check if there's a direct edge from row to column (shortcutting edge)
			if (!matrix[row][column]) continue;
			
			// Check if there's a path from row to column in path matrix
			PathList pl = pm.get(row, column);
			if (pl.isEmpty()) continue;
			
			// Iterate through each path in pm...
			for (Path path : pl) {
				
				// Check if this path represents a valid shortcut
				if (isValidShortcut(path, row, column)) {
					// Check if not duplicate
					if (!isDuplicatePath(path, shortcuts))
						shortcuts.add(path);
				}
			}
		}
}


/* Check if a path represents a valid shortcut according to the strict definition
 * path: the path from source to sink
 * sourceIdx: index of source vertex (row)
 * sinkIdx: index of sink vertex (column)
 * 
 * A shortcut must satisfy:
 * 1. At least 4 vertices
 * 2. Exactly one source (no incoming edges from vertices in shortcut) and one sink (no outgoing edges to vertices in shortcut)
 * 3. A directed path from source to sink going through every vertex in the shortcut
 * 4. A direct edge from source to sink (shortcutting edge) - already checked before calling this
 * 5. Not transitive (exists u->v and v->z but no u->z)
 */
private boolean isValidShortcut(Path path, int sourceIdx, int sinkIdx) {
	// 1. At least 4 vertices
	if (path.size() < 4) return false;
	
	// Verify path starts with source and ends with sink
	Vertex source = new Vertex(labels.get(sourceIdx));
	Vertex sink = new Vertex(labels.get(sinkIdx));
	if (!path.get(0).equals(source) || !path.get(path.size()-1).equals(sink)) {
		return false;
	}

	// Reject non-simple paths (paths with repeated vertices). Paths that
	// revisit vertices (e.g. [1,2,3,1,4]) are not considered canonical
	// shortcuts for reporting purposes and produce duplicate/ambiguous
	// results in the current path-matrix construction.
	java.util.Set<String> seen = new java.util.HashSet<String>();
	for (Vertex v : path) {
		if (seen.contains(v.label)) {
			return false; // repeated vertex -> not a simple path
		}
		seen.add(v.label);
	}
	
	// Get all vertices in the path (these form the shortcut subgraph)
	Set<Vertex> shortcutVertices = new HashSet<Vertex>(path);
	
	// Verify path is a valid directed path (each consecutive edge exists)
	for (int i = 0; i < path.size() - 1; i++) {
		Vertex from = path.get(i);
		Vertex to = path.get(i + 1);
		int fromIdx = labels.indexOf(from.label);
		int toIdx = labels.indexOf(to.label);
		if (!matrix[fromIdx][toIdx]) {
			return false; // Path edge doesn't exist
		}
	}
	
	// NOTE: The previous implementation rejected shortcuts if the source had
	// incoming edges from other vertices in the shortcut, or the sink had
	// outgoing edges to other vertices in the shortcut. That was overly
	// restrictive and caused valid shortcuts to be missed (see bug report
	// for input "0101;0010;1001;0000"). We therefore do not enforce those
	// two checks here; we rely instead on path validity and non-transitivity
	// checks below to identify genuine shortcuts.
	
	// 4. Direct edge from source to sink (shortcutting edge) - already verified in collectAllShortcuts
	
	// 5. Check not transitive: exists u->v and v->z but no u->z
	// For a shortcut, we need to verify that the structure is non-transitive
	// Specifically, there should be at least one case where u->v and v->z exist but u->z doesn't
	boolean foundNonTransitive = false;
	for (Vertex u : shortcutVertices) {
		int uIdx = labels.indexOf(u.label);
		for (Vertex v : shortcutVertices) {
			if (u.equals(v)) continue;
			int vIdx = labels.indexOf(v.label);
			if (matrix[uIdx][vIdx]) { // u->v exists
				for (Vertex z : shortcutVertices) {
					if (u.equals(z) || v.equals(z)) continue;
					int zIdx = labels.indexOf(z.label);
					if (matrix[vIdx][zIdx] && !matrix[uIdx][zIdx]) {
						// Found u->v and v->z but no u->z - non-transitive
						foundNonTransitive = true;
						break;
					}
				}
				if (foundNonTransitive) break;
			}
		}
		if (foundNonTransitive) break;
	}
	
	// A shortcut must be non-transitive
	return foundNonTransitive;
}


/* Check if a cycle is duplicate (same set of vertices, possibly rotated or reversed)
 * or contains an existing cycle. Note: if new cycle is contained by existing cycle,
 * we don't return true here - we'll remove the larger existing cycle in removeContainedCycles
 */
private boolean isDuplicateCycle(Path path, List<Path> existing) {
    if (path == null || path.isEmpty()) return false;

    // 去掉闭环重复结尾（例如 [1,2,3,1] -> [1,2,3]）
    List<String> labelsPath = normalizeCycleLabels(path);

    for (Path existingPath : existing) {
        List<String> labelsExisting = normalizeCycleLabels(existingPath);
        
        // 检查是否是相同的cycle（旋转/反向等价）
        if (labelsExisting.size() == labelsPath.size()) {
            // 检查旋转等价
            if (isRotationEquivalent(labelsPath, labelsExisting)) return true;

            // 检查反向旋转等价
            List<String> reversed = new ArrayList<>(labelsPath);
            java.util.Collections.reverse(reversed);
            if (isRotationEquivalent(reversed, labelsExisting)) return true;
        }
        
        // 检查新cycle是否包含已存在的cycle（作为连续子序列）
        // 如果新cycle包含已存在的cycle，不添加新cycle（保留较小的已存在cycle）
        if (labelsPath.size() > labelsExisting.size()) {
            if (containsCycle(labelsPath, labelsExisting)) return true;
        }
        
        // 注意：如果新cycle被已存在的cycle包含，我们不在这里返回true
        // 而是在removeContainedCycles中移除较大的已存在cycle，保留较小的新cycle
    }
    return false;
}

/* 将 Path 转为 label 列表，并去掉闭环重复首尾 */
private List<String> normalizeCycleLabels(Path p) {
    List<String> labelsList = new ArrayList<>();
    for (Vertex v : p)
        labelsList.add(v.label);
    // 去掉重复的最后一个顶点（若首尾相同）
    if (labelsList.size() > 1 &&
        labelsList.get(0).equals(labelsList.get(labelsList.size() - 1)))
        labelsList.remove(labelsList.size() - 1);
    return labelsList;
}

/* 判断 list a 是否是 list b 的循环移位 */
private boolean isRotationEquivalent(List<String> a, List<String> b) {
    if (a.size() != b.size()) return false;
    int n = a.size();
    for (int offset = 0; offset < n; offset++) {
        boolean match = true;
        for (int i = 0; i < n; i++) {
            if (!a.get(i).equals(b.get((i + offset) % n))) {
                match = false;
                break;
            }
        }
        if (match) return true;
    }
    return false;
}


/* 检查 cycle（作为循环序列）是否包含另一个cycle作为连续子序列
 * largerCycle: 较大的cycle
 * smallerCycle: 较小的cycle（要检查是否被包含）
 */
private boolean containsCycle(List<String> largerCycle, List<String> smallerCycle) {
    if (largerCycle.size() < smallerCycle.size()) return false;
    if (smallerCycle.isEmpty()) return true;
    
    int n = largerCycle.size();
    int m = smallerCycle.size();
    
    // 由于cycle是循环的，需要检查所有可能的起始位置
    for (int start = 0; start < n; start++) {
        boolean match = true;
        for (int i = 0; i < m; i++) {
            int idx = (start + i) % n;
            if (!largerCycle.get(idx).equals(smallerCycle.get(i))) {
                match = false;
                break;
            }
        }
        if (match) return true;
    }
    return false;
}


/* 移除被新cycle包含的已存在cycles，或包含新cycle的已存在cycles
 * 这样可以确保只保留最小的cycle
 */
private void removeContainedCycles(List<Path> cycles, Path newCycle) {
    if (newCycle == null || newCycle.isEmpty()) return;
    
    List<String> labelsNew = normalizeCycleLabels(newCycle);
    
    // 使用迭代器安全地移除元素
    java.util.Iterator<Path> it = cycles.iterator();
    while (it.hasNext()) {
        Path existingCycle = it.next();
        List<String> labelsExisting = normalizeCycleLabels(existingCycle);
        
        // 如果新cycle包含已存在的cycle，移除已存在的cycle（保留较小的）
        if (labelsNew.size() > labelsExisting.size()) {
            if (containsCycle(labelsNew, labelsExisting)) {
                it.remove();
            }
        }
        // 如果已存在的cycle包含新cycle，移除已存在的cycle（保留较小的新cycle）
        else if (labelsExisting.size() > labelsNew.size()) {
            if (containsCycle(labelsExisting, labelsNew)) {
                it.remove();
            }
        }
    }
}



/* Check if a path is duplicate
 */
private boolean isDuplicatePath (Path path, List<Path> existing)
{	if (path == null || path.isEmpty()) return false;
	
	for (Path existingPath : existing) {
		if (existingPath.size() != path.size()) continue;
		
		// Check if paths are identical
		boolean match = true;
		for (int i = 0; i < path.size() && match; i++) {
			if (!path.get(i).equals(existingPath.get(i)))
				match = false;
		}
		if (match) return true;
	}
	return false;
}


public String toString()
{	StringBuilder res = new StringBuilder();
	
	for (int r = 0; r < length(); r++) {
		for (int c = 0; c < length(); c++)
			res.append(toString(r,c));
		res.append("\n");
	}
	return res.toString();
}


public String toString(int r, int c)
{	//if looking to the "top" or "left" of the matrix, get the col/row label
	if (r < 0)
		return labels.get(c);
	if (c < 0)
		return labels.get(r);
	
	return matrix[r][c] ? "1" : "0";
}


/* convenience method to let the observers know that there's a new graph.
 */
public void refresh()
{	setChanged();
	notifyObservers();
}


//Methods for checking the existence of connections between vertices
public boolean edge (Vertex v, Vertex u)
{	int v_i = labels.indexOf(v.label);
	int u_i = labels.indexOf(u.label);
	return hasEdge(v_i,u_i);
}


public boolean dirEdge (Vertex fr, Vertex to)
{	int fr_i = labels.indexOf(fr.label);
	int to_i = labels.indexOf(to.label);
	return hasDirEdge(fr_i,to_i);
}


public boolean undirEdge (Vertex v, Vertex u)
{	int v_i = labels.indexOf(v.label);
	int u_i = labels.indexOf(u.label);
	return hasUndirEdge(v_i,u_i);
}


public boolean dirPath (Vertex fr, Vertex to)
{	//First check if there's a path of length 1!
	int fr_i = labels.indexOf(fr.label);
	int to_i = labels.indexOf(to.label);
	if (hasDirEdge(fr_i,to_i)) return true;
	
	//Look thru adjacency matrices A^2 -> A^N

	PathMatrix pm = square();

	if (!pm.get(fr_i,to_i).isEmpty())
		return true;

	for (int n = 3; n <= length(); n++) {
		pm = symbolicMultiply(pm);
		if (!pm.get(fr_i,to_i).isEmpty())
			return true;
	}
	return false;
}


// private methods for getting edges from node indices
private boolean hasEdge (int v, int u)
{	return matrix[v][u] || matrix[u][v];
}


private boolean hasDirEdge (int fr, int to)
{	return matrix[fr][to] && !matrix[to][fr];
}


private boolean hasUndirEdge (int v, int u)
{	return matrix[v][u] && matrix[u][v];
}


/* Calculate the degree of a vertex (number of edges incident to it)
 */
private int getDegree (int v)
{	int degree = 0;
	for (int u = 0; u < length(); u++)
		if (u != v && hasEdge(v, u))
			degree++;
	return degree;
}
//


//Methods for adding vertices and edges. Note that adding a directed edge
//from x to y replaces an undirected edge x to y, and vice versa; and of course,
//adding a directed edge x->y replaces a directed edge y->x
public void setVertex()
{	if (length() >= MAX_SIZE) return;

	increaseByOne();
	
	labels.add(length()+"");

	refresh();
}


public void set (Vertex v)
{	if (length() >= MAX_SIZE) return;
	if (labels.contains(v.label)) return;

	increaseByOne();

	labels.add(v.label);

	refresh();
}


public void setEdge (Vertex v, Vertex u)
{	int v_i = labels.indexOf(v.label);
	int u_i = labels.indexOf(u.label);

	matrix[v_i][u_i] = true;
	matrix[u_i][v_i] = true;
	
	setChanged();
	notifyObservers(new EdgeData(new Edge(v, u, false), new Point(v_i,u_i)));
}


public void setDirEdge (Vertex fr, Vertex to)
{	int fr_i = labels.indexOf(fr.label);
	int to_i = labels.indexOf(to.label);

	matrix[fr_i][to_i] = true;
	matrix[to_i][fr_i] = false;
	
	setChanged();
	notifyObservers(new EdgeData(new Edge(fr, to, true), new Point(fr_i,to_i)));
}


//Methods for removing vertices and edges.
public void unset (Vertex v)
{	if (length() <= MIN_SIZE) return;

	int v_i = labels.indexOf(v.label);

	reduceByOne(v_i);

	labels.remove(v_i);

	refresh();
}


public void unsetEdge (Vertex v, Vertex u)
{	int v_i = labels.indexOf(v.label);
	int u_i = labels.indexOf(u.label);

	matrix[v_i][u_i] = matrix[u_i][v_i] = false;

	refresh();
}


public boolean oriented()
{	for (int v = 0; v < length()-1; v++)
		for (int u = v+1; u < length(); u++)
			if (matrix[v][u] && matrix[u][v])
				return false;
	return true;
}


public boolean nonOriented()
{	for (int v = 0; v < length()-1; v++)
		for (int u = v+1; u < length(); u++)
			if (matrix[v][u] != matrix[u][v])
				return false;
	return true;
}


/* return a deep copy of this graph
 */
public Graph copy()
{	return new AdjacencyMatrix(matrix, labels);
}


//METHODS INHERITED FROM Matrix--------/
public int length()
{	return matrix.length;
}


public Boolean get (int r, int c)
{	return matrix[r][c];
}


public void set (int r, int c, Boolean s)
{	matrix[r][c] = s;

	refresh();
}


/* Make this n*n matrix a n+1*n+1 matrix
 */
public void increase()
{	if (length() >= MAX_SIZE) return;

	increaseByOne();

	labels.add(length()+"");

	setChanged();
	notifyObservers();
}


/* Make this n*n matrix a n-1*n-1 matrix by removing the i-th row and column
 */
public void remove (int i)
{	if (length() <= MIN_SIZE) return;

	reduceByOne(i);
	
	labels.remove(i);

	setChanged();
	notifyObservers();
}


//Private methods for manipulating matrix
/* add one entry to the matrix
 */
private void increaseByOne()
{	boolean[][] copy = new boolean[length()+1][length()+1];
	for (int i = 0; i < length(); i++)
		copy[i] = Arrays.copyOfRange(matrix[i], 0, length()+1);
	matrix = copy;
}


/* remove one entry from the matrix
 */
private void reduceByOne (int i)
{	boolean[][] copy = new boolean[length()-1][length()-1];
	int r, c;
	for (r = 0; r <= i-1; r++) {
		//copy[r][0] = matrix[r][0];
		//copy[r][1] = matrix[r][1];
		//...
		//copy[r][i-1] = matrix[r][i-1];
		//copy[r][i] = matrix[r][i+1];
		//copy[r][i+1] = matrix[r][i+2];
		//...
		//copy[r][N-1] = matrix[r][N];
		//which would go like...
		for (c = 0; c < i; c++) 
			copy[r][c] = matrix[r][c];
		for (c = i; c < length()-1; c++) 
			copy[r][c] = matrix[r][c+1];
	}
	for (r = i; r < length()-1; r++) {
		for (c = 0; c < i; c++) 
			copy[r][c] = matrix[r][c];
		for (c = i; c < length()-1; c++) 
			copy[r][c] = matrix[r][c+1];
	}
	matrix = copy;
}

/*****************/}/**************************END CLASS AdjacencyMatrix.java***/
