// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.bfg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * This algorithm takes as input a graph and uses Tarjan's algorithm to produce a collection of
 * strongly connected components.
 *
 * <p>Algorithm Description:
 * https://en.wikipedia.org/wiki/Tarjan's_strongly_connected_components_algorithm
 */
class StronglyConnectedComponents<N> {

  /** Resulting strongly connected components */
  private final ImmutableList.Builder<ImmutableSet<N>> connectedComponents;

  /** Original graph to be processed */
  private final Graph<N> graph;

  /** Stack of nodes that have not been assigned a component */
  private final Deque<N> unassignedNodeStack;

  /** Counter of the number of nodes DFS has discovered, used to set discovery times of nodes */
  private int numberOfNodesDiscovered;

  /** Map connecting each node to metadata relevant for Tarjan's algorithm. */
  private final HashMap<N, TarjanMetadata> tarjanMetadataMap;

  StronglyConnectedComponents(Graph<N> graph) {
    this.graph = checkNotNull(graph);
    this.unassignedNodeStack = new ArrayDeque<>();
    this.numberOfNodesDiscovered = 0;
    this.tarjanMetadataMap = new HashMap<>();
    this.connectedComponents = new ImmutableList.Builder<>();
  }

  /**
   * Returns set of strongly connected components for the passed in graph. The order of said
   * components is in reverse topological order.
   */
  Collection<ImmutableSet<N>> compute() {
    checkArgument(graph.isDirected());
    for (N node : graph.nodes()) {
      if (!isNodeDiscovered(node)) {
        stronglyConnectedDFS(node);
      }
    }
    return connectedComponents.build();
  }

  /**
   * Returns a directed acyclic graph of the strongly connected components, where each node in the
   * DAG is a strongly connected component in the original graph, and an edge from one vertex, C1,
   * to another vertex C2, iff there exist an edge (u,v) in the original graph for u ϵ C1, and v ϵ
   * C2.
   *
   * <p>Traversal on the resulting DAG using Graph's nodes() method is done in reverse topological
   * order.
   */
  ImmutableGraph<ImmutableSet<N>> computeComponentDAG() {
    Collection<ImmutableSet<N>> components = compute();
    Map<N, ImmutableSet<N>> nodeToComponentMap = new HashMap<>();
    MutableGraph<ImmutableSet<N>> metaGraph =
        GraphBuilder.directed().allowsSelfLoops(false).build();
    for (ImmutableSet<N> component : components) {
      metaGraph.addNode(component);
      component.stream().forEach(node -> nodeToComponentMap.put(node, component));

      for (N node : component) {
        for (N successor : graph.successors(node)) {
          if (!component.equals(nodeToComponentMap.get(successor))) {
            metaGraph.putEdge(component, nodeToComponentMap.get(successor));
          }
        }
      }
    }
    return ImmutableGraph.copyOf(metaGraph);
  }

  /**
   * DFS tailored for Tarjan's algorithm. Alongside the additional bookkeeping, the most notable
   * distinction is that nodes are only removed from the stack, whenever we detect a root in a DFS
   * subtree. Here we are defining a root to be any node without any back or cross edges.
   */
  private void stronglyConnectedDFS(N node) {
    discoverNode(node);
    for (N neighbor : graph.successors(node)) {
      if (!isNodeDiscovered(neighbor)) {
        stronglyConnectedDFS(neighbor);
        updateLowNumber(node, neighbor);
      } else if (isNodeBeingExplored(neighbor)) {
        updateLowNumber(node, neighbor);
      }
    }
    if (isComponentRoot(node)) {
      /* Pop elements from the stack until you reach the current node. */
      ImmutableSet.Builder<N> currentSCC = new ImmutableSet.Builder<N>();
      N poppedNode;
      do {
        poppedNode = unassignedNodeStack.pop();
        tarjanMetadataMap.get(poppedNode).isOnStack = false;
        currentSCC.add(poppedNode);
      } while (poppedNode != node);
      connectedComponents.add(currentSCC.build());
    }
  }

  /** Updates the lowest reachable node from the current node. */
  private void updateLowNumber(N rootNode, N childNode) {
    tarjanMetadataMap.get(rootNode).lowNumber =
        Math.min(
            tarjanMetadataMap.get(rootNode).lowNumber, tarjanMetadataMap.get(childNode).lowNumber);
  }

  private void discoverNode(N node) {
    tarjanMetadataMap.put(node, new TarjanMetadata(numberOfNodesDiscovered++));
    unassignedNodeStack.push(node);
  }

  private boolean isNodeBeingExplored(N node) {
    return tarjanMetadataMap.containsKey(node) && tarjanMetadataMap.get(node).isOnStack;
  }

  private boolean isNodeDiscovered(N node) {
    return tarjanMetadataMap.containsKey(node);
  }

  /**
   * Returns true iff 'node' is the first node we encountered in some strongly-connected component.
   */
  private boolean isComponentRoot(N node) {
    return tarjanMetadataMap.get(node).lowNumber == tarjanMetadataMap.get(node).discoveryNumber;
  }

  /** Metadata necessary for implementing Tarjan's algorithm. */
  private static class TarjanMetadata {
    /** The order a node is visited in the DFS traversal. */
    private final int discoveryNumber;

    /** This is the smallest discovery number reachable in the DFS tree from this node. */
    private int lowNumber;

    /** Returns true iff the node has yet to be assigned a strongly connected component. */
    private boolean isOnStack;

    private TarjanMetadata(int discoveryNumber) {
      this.discoveryNumber = discoveryNumber;
      this.lowNumber = discoveryNumber;
      this.isOnStack = true;
    }
  }
}
