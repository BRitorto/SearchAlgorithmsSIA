package ar.edu.itba.sia.gps;

import java.util.*;
import ar.edu.itba.sia.gps.GPSNode;
import ar.edu.itba.sia.gps.api.Heuristic;
import ar.edu.itba.sia.gps.api.Problem;
import ar.edu.itba.sia.gps.api.Rule;
import ar.edu.itba.sia.gps.api.State;

import static ar.edu.itba.sia.gps.SearchStrategy.IDDFS;

public class GPSEngine {

	private Queue<GPSNode> open;
	private Map<State, Integer> bestCosts;
	private Problem problem;
	private long explosionCounter;
	private boolean finished;
	private boolean failed;
	private GPSNode solutionNode;
	private Optional<Heuristic> heuristic;
	private long currentDepthLimit;
	private final static long TIMELIMIT = 9000;

	// Use this variable in open set order.
	protected SearchStrategy strategy;

	public GPSEngine(Problem problem, SearchStrategy strategy, Heuristic heuristic) {
		bestCosts = new HashMap<>();
		this.problem = problem;
		this.strategy = strategy;
		if(heuristic == null){
			this.heuristic = Optional.empty();
		}
		else{
			this.heuristic = Optional.of(heuristic);
		}
		explosionCounter = 0;
		finished = false;
		failed = false;
		currentDepthLimit = strategy == IDDFS ? 1 : Integer.MAX_VALUE;
		switch (strategy){
			case BFS:
			case DFS:
			case IDDFS:
				open = new LinkedList<>();
				break;
			case GREEDY:
				open = new PriorityQueue<>(new Comparator<GPSNode>() {
					@Override
					public int compare(GPSNode node1, GPSNode node2) {
						return heuristic.getValue(node1.getState()).compareTo(heuristic.getValue(node2.getState()));
					}
				});
				break;
			case ASTAR:
				open = new PriorityQueue<>(new Comparator<GPSNode>() {
					@Override
					public int compare(GPSNode node1, GPSNode node2) {
						return (new Integer(heuristic.getValue(node1.getState())+node1.getCost())).compareTo(
								new Integer(heuristic.getValue(node2.getState())+node2.getCost()));
					}
				});
				break;
		}
	}

	public void findSolution() {
		long iddfsTotalExplosionCounter = 0;
		long lastExplosionCounter = explosionCounter;
		long initTime = System.currentTimeMillis();
		long endTime;
		do {
			GPSNode rootNode = new GPSNode(problem.getInitState(), 0, null);
			//System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<current depth limit =" + currentDepthLimit);
			open.add(rootNode);
			explosionCounter = 0;
			while (open.size() > 0) {
				GPSNode currentNode = open.remove();
				// estos comentarios son importantes
//				//System.out.println(" depth = " +currentNode.getDepth());
//				System.out.println("-----------SACO DE OPEN---------------");
//				System.out.println(currentNode.getState().getRepresentation());
//				System.out.println("--------------------------");
//				System.out.println("cost = "+ currentNode.getCost());
//				System.out.println("depth = "+ currentNode.getDepth());
				if (problem.isGoal(currentNode.getState())) {
//					System.out.println("Entro isGoal de engine");
					finished = true;
					solutionNode = currentNode;
					if(strategy == IDDFS)
						explosionCounter = iddfsTotalExplosionCounter;
					System.out.println("GANAMOS!");
					System.out.println("exploded nodes = "+explosionCounter);
					System.out.println("cost = "+currentNode.getCost());
					return;
				} else {
					explode(currentNode);
				}
			}
			if(strategy == IDDFS){
				endTime = System.currentTimeMillis();
				if(lastExplosionCounter == explosionCounter || endTime-initTime >= TIMELIMIT){
					failed = true;
					finished = true;
					explosionCounter = iddfsTotalExplosionCounter;
					System.out.println("exploded nodes = "+explosionCounter);
				}else{
					open.clear();
					bestCosts.clear();
					currentDepthLimit++;
					lastExplosionCounter = explosionCounter;
					iddfsTotalExplosionCounter += explosionCounter;
				}
			}
			else{
				failed = true;
				finished = true;
				System.out.println("exploded nodes = "+explosionCounter);
			}
		}while(strategy == IDDFS && !finished);
	}

	private void explode(GPSNode node) {
		Collection<GPSNode> newCandidates;
		Heuristic myHeuristic;
		Stack<GPSNode> auxStack;
		switch (strategy) {
		case BFS:
			if (bestCosts.containsKey(node.getState())) {
				return;
			}
			newCandidates = new ArrayList<>();
			addCandidates(node, newCandidates);
			open.addAll(newCandidates);
			break;
		case DFS:
			if (bestCosts.containsKey(node.getState())) {
				return;
			}
			newCandidates = new ArrayDeque<>();
			addCandidates(node, newCandidates);
			while(!newCandidates.isEmpty()){
				((LinkedList<GPSNode>)open).push(((ArrayDeque<GPSNode>) newCandidates).removeLast());
			}

			break;
		case IDDFS:
			if (bestCosts.containsKey(node.getState()) && node.getCost() >= bestCosts.get(node.getState())) {
				return;
			}
			if(node.getDepth() == currentDepthLimit){
				return;
			}
			newCandidates = new ArrayDeque<>();
			addCandidates(node, newCandidates);
			while(!newCandidates.isEmpty()){
				((LinkedList<GPSNode>)open).push(((ArrayDeque<GPSNode>) newCandidates).removeLast());
			}
			break;
		case GREEDY:
			if (!isBest(node.getState(), node.getCost())) {
				return;
			}
			addCandidates(node, open);
			break;
		case ASTAR:
			if (!isBest(node.getState(), node.getCost())) {
				return;
			}
			addCandidates(node, open);
			break;
		}
	}


	private void addCandidates(GPSNode node, Collection<GPSNode> candidates) {
		explosionCounter++;
		updateBest(node);
		//Heuristic myHeuristic;
		for (Rule rule : (List<Rule>)problem.getRules()) {
			Optional<State> newState = rule.apply(node.getState());
			if (newState.isPresent()) {
				GPSNode newNode = new GPSNode(newState.get(), node.getCost() + rule.getCost(), node.getDepth()+1, rule);
				newNode.setParent(node);
				 //estos comentarios son importantes
//				System.out.println("******NEW NODE CANDIDATE******");
//				System.out.println(newNode.getState().getRepresentation());
//				System.out.println( ">>>>>> depth = "+newNode.getDepth() );
//				System.out.println("******************************");
				candidates.add(newNode);

			}
		}
	}

	private boolean isBest(State state, Integer cost) {
		return !bestCosts.containsKey(state) || cost < bestCosts.get(state);
	}

	private void updateBest(GPSNode node) {
		bestCosts.put(node.getState(), node.getCost());
	}

	// GETTERS FOR THE PEOPLE!

	public Queue<GPSNode> getOpen() {
		return open;
	}

	public Map<State, Integer> getBestCosts() {
		return bestCosts;
	}

	public Problem getProblem() {
		return problem;
	}

	public long getExplosionCounter() {
		return explosionCounter;
	}

	public boolean isFinished() {
		return finished;
	}

	public boolean isFailed() {
		return failed;
	}

	public GPSNode getSolutionNode() {
		return solutionNode;
	}

	public SearchStrategy getStrategy() {
		return strategy;
	}

}