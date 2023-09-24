package org.restct;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import javafx.util.Pair;
import org.apache.poi.ss.formula.functions.T;
import org.restct.dto.Operation;
import org.restct.dto.keywords.Method;
import org.restct.utils.Helper;

import java.util.*;
import java.util.stream.Collectors;

class Node {
    public List<Operation> operations;
    public Resource state;
    public List<Node> successors;
    public Node predecessor;

    public Node(List<Operation> operations) {
        this.operations = operations;

        // resource states
        if (this.methodSet().contains(Method.POST)) {
            this.state = Resource.NoExist;
        } else {
            this.state = Resource.Exist;
        }

        this.successors = new ArrayList<Node>();
        this.predecessor = null;
    }

    public Set<Method> methodSet() {
        Set<Method> methodSet = new HashSet<>();
        for (Operation op : this.operations) {
            methodSet.add(op.method);
        }
        return methodSet;
    }

    public boolean haveMethod(Method method) {
        return methodSet().contains(method);
    }

    public Operation findOperation(Method method) {
        for (Operation operation : this.operations) {
            if (operation.method == method) {
                return operation;
            }
        }
        return null;
    }

    public boolean isRoot() {
        return methodSet().size() == 0;
    }

    public boolean havePredecessor() {
        return this.predecessor != null;
    }

    public boolean haveSuccessor() {
        return this.successors.size() > 0;
    }

    public String getUrl() {
        return this.operations.get(0).url;
    }

    public Set<Pair<String, Integer>> getSplittedUrl() {
        return this.operations.get(0).getSplittedUrl();
    }
}

enum Resource {
    NoExist(0),
    Exist(1),
    Destroy(-1);

    private int value;

    Resource(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

class Graph {
    private Node root;
    private Map<String, Node> nodes;

    public Graph() {
        this.root = new Node(new ArrayList<>());
        this.nodes = new HashMap<>();
    }

    public Node getRoot(){
        return this.root;
    }


    public void insert(Node newNode, Node predecessor) {
        if (predecessor == null) {
            predecessor = root;
        }

        boolean insertFlag = true;
        if (predecessor.isRoot() || newNode.getSplittedUrl().containsAll(predecessor.getSplittedUrl())) {
            for (Node successor : predecessor.successors) {
                if (newNode.getSplittedUrl().containsAll(successor.getSplittedUrl())) {
                    insert(newNode, successor);
                    insertFlag = false;
                }
            }
        }
        if (insertFlag) {
            if (predecessor.successors.size() > 0) {
                List<Node> nodesModified = new ArrayList<>();
                for (Node successor : predecessor.successors) {
                    if (newNode.getSplittedUrl().containsAll(successor.getSplittedUrl())) {
                        nodesModified.add(successor);
                    }
                }
                for (Node curNode : nodesModified) {
                    curNode.predecessor = newNode;
                    newNode.successors.add(curNode);
                    predecessor.successors.remove(curNode);
                }
            }
            predecessor.successors.add(newNode);
            newNode.predecessor = predecessor;
        }
    }

    //改变资源状态
    public void change(List<Operation> opsToAdd) {
        for (Operation operation : opsToAdd) {
            Node node = findNode(operation.url);
            if (operation.method == Method.POST) {
                assert node.state == Resource.NoExist;
                node.state = Resource.Exist;
            } else if (operation.method == Method.DELETE) {
                assert node.state == Resource.Exist;
                node.state = Resource.Destroy;
            }
        }
    }

    public Node findNode(String url) {
        assert nodes.containsKey(url);
        return nodes.get(url);
    }

    public static List<Node> findPredecessors(Node node) {
        List<Node> predecessors = new ArrayList<>();
        Node curNode = node;
        while (true) {
            Node preNode = curNode.predecessor;
            if (preNode.isRoot()) {
                return predecessors;
            } else {
                predecessors.add(0, preNode);
                curNode = preNode;
            }
        }
    }

    public static Graph buildGraph(List<Operation> operations) {
        Graph graph = new Graph();

        List<Operation> operationSorted = new ArrayList<>(operations);
        operationSorted.sort(Comparator.comparing(op -> op.url));
        Map<String, List<Operation>> operationByUrl = operationSorted.stream().collect(Collectors.groupingBy(Operation::getUrl));
        for (List<Operation> group : operationByUrl.values()) {
            Node node = new Node(group);
            graph.nodes.put(node.getUrl(), node);
            graph.insert(node, null);
        }
        return graph;
    }
}


public class SeqenceCoveringArray {
    static List<List<Operation>> members = new ArrayList<>();
    private int strength;
    public Set<Operation[]> uncoveredSet;
    private long time;

    public SeqenceCoveringArray() {
        strength = Math.min(Config.s_strength, Operation.members.size());
        uncoveredSet = collectUncoveredSet();
        time = System.currentTimeMillis();
    }

    private Set<Operation[]> collectUncoveredSet() {
        Set<Operation[]> uncoveredSet = new HashSet<>();
        Set<List<Operation>> operationLists = Helper.getPermutations(Operation.getMembers(), strength);//TODO: 生成排列办法
        for (List<Operation> operationList : operationLists) {
            Operation[] operationArray = operationList.toArray(new Operation[0]);
            if (isValidP(operationArray)) {
                uncoveredSet.add(operationArray);
            }
        }
        return uncoveredSet;
    }

    private static boolean isValidP(Operation[] operationList) {
        for (int i = 0; i < operationList.length; i++) {
            Operation operation = operationList[i];
            // POST Constraint
            if (operation.method == Method.POST) {
                for (int j = 0; j < i; j++) {
                    if (operationList[j].getSplittedUrl().containsAll(operation.getSplittedUrl())) {
                        return false;
                    }
                }
            }
            // DELETE Constraint
            if (operation.method == Method.DELETE) {
                for (int j = i + 1; j < operationList.length; j++) {
                    if (operationList[j].getSplittedUrl().containsAll(operation.getSplittedUrl())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private int countPWithOp(Operation op, List<Operation> seq, int length) {

        assert length <= seq.size();
        Set<Operation[]> combinationsWithOp = new HashSet<>();
        for (List<Operation> p : Helper.getCombinations(seq, length)) {//TODO: 生成有序列表的组合
            p.add(op);
            combinationsWithOp.add(p.toArray(new Operation[0]));
        }

        assert 0 <= length && length <= this.strength;
        if (length == this.strength - 1) {
            // 直接计算已覆盖的操作序列组合
            return containsSetofOpList(uncoveredSet, combinationsWithOp);//TODO: 待验证，计算两个set的交集
//            return (int) uncoveredSet.stream().filter(combinationsWithOp::contains).count();//TODO: 待验证，计算两个set的交集
        } else {
            int count = 0;

            Set<Operation[]> tmp = new HashSet<>();
            for (Operation[] uncovered : uncoveredSet) {
                // 计算每个未覆盖的元组的前length + 1 个元素是否存在于操作序列中
                Operation[] sublist = Arrays.copyOfRange(uncovered, 0, length + 1);
                tmp.add(sublist);
//                if (combinationsWithOp.contains(Arrays.asList(sublist))) {
//                    count += 1;
//                    break;
//                }
            }
            return containsSetofOpList(tmp, combinationsWithOp);
        }
    }

    private Pair<Integer, List<Operation>> setPriorities(Set<Operation> candidates, List<Operation> sequence, int length) {
        if (candidates.isEmpty()) {
            return new Pair<>(0, new ArrayList<>());
        }
        List<Pair<Operation, Integer>> candidatesWithCount = new ArrayList<>();
        for (Operation op : candidates) {
            int count = countPWithOp(op, sequence, length);
            candidatesWithCount.add(new Pair<>(op, count));
        }
        int maxCount = 0;
        List<Operation> targetOpList = new ArrayList<>();
        for (Pair<Operation, Integer> pair : candidatesWithCount) {
            Operation op = pair.getKey();
            int count = pair.getValue();
            if (count == maxCount) {
                targetOpList.add(op);
            } else if (count > maxCount) {
                targetOpList.clear();
                targetOpList.add(op);
                maxCount = count;
            }
        }
        return new Pair<>(maxCount, targetOpList);
    }

    public static Set<Operation> getCandidates(Graph graph) {
        Set<Operation> candidates = new HashSet<>();
        Stack<Node> stack = new Stack<>();
        stack.push(graph.getRoot());
        while (!stack.isEmpty()) {
            Node curNode = stack.pop();
            for (Node successor : curNode.successors) {
                if (successor.state != Resource.Destroy) {
                    candidates.addAll(successor.operations);
                    stack.push(successor);
                }
            }
        }
        return candidates;
    }



    public static List<Operation> genDependOps(Operation operation, Graph graph) {
        List<Operation> opsToAdd = new ArrayList<>();
        Node node = graph.findNode(operation.getUrl());
        while (true) {
            if (node.isRoot()) {
                break;
            }
            if (node.state == Resource.NoExist) {
                assert node.haveMethod(Method.POST);
                opsToAdd.add(0, node.findOperation(Method.POST));
            }
            assert node.state != Resource.Destroy;
            node = node.predecessor;
        }
        if (!opsToAdd.contains(operation)) {
            opsToAdd.add(operation);
        }
        return opsToAdd;
    }

    public Operation[] buildSequence() {
        List<Operation> sequence = new ArrayList<>();
        Graph graph = Graph.buildGraph(Operation.members);
        boolean loopFlag = true;
        while (loopFlag) {
            for (int childLength = this.strength - 1; childLength >= 0; childLength--) {
                if (childLength > sequence.size()) {
                    continue;
                }
                Set<Operation> candidates = SeqenceCoveringArray.getCandidates(graph);
                candidates.removeAll(sequence);
                Pair<Integer, List<Operation>> priority = this.setPriorities(candidates, sequence, childLength);
                int maxCount = priority.getKey();
                List<Operation> operationList = priority.getValue();
                if (maxCount > 0) {
                    Operation selectedOp = operationList.get((int) (Math.random() * operationList.size()));
                    List<Operation> opsToAdd = SeqenceCoveringArray.genDependOps(selectedOp, graph);
                    graph.change(opsToAdd);
                    sequence.addAll(opsToAdd);
                    break;
                } else {
                    if (childLength == 0) {
                        loopFlag = false;
                    }
                }
            }
        }
        // update uncovered set
        Set<Operation[]> newCovered = new HashSet<>();
        for (List<Operation> combo : Helper.getCombinations(sequence, this.strength)) {
            Operation[] arr = combo.toArray(new Operation[combo.size()]);
            newCovered.add(arr);
        }

        // 遍历Set A中的每个元素
        for (Operation[] element : newCovered) {

            // 检查Set B是否包含相同的元素
            if (Helper.opSetRemove(uncoveredSet, element)) {
                // 如果Set B中不包含该元素，则添加到临时HashSet中
                //tempSet.add(element);
                //logger.debug("uncoveredSet remove {}",element.length);
            }
        }

//        // 使用迭代器遍历Set A
//        Iterator<Operation[]> iterator = uncoveredSet.iterator();
//        while (iterator.hasNext()) {
//            Operation[] element = iterator.next();
//            // 检查Set B是否包含相同的元素
//            if (newCovered.contains(element)) {
//                // 如果Set B中包含该元素，则使用迭代器的remove方法删除该元素
//                iterator.remove();
//            }
//        }
//
//        this.uncoveredSet.removeAll(newCovered);
        SeqenceCoveringArray.members.add(sequence);
        return sequence.toArray(new Operation[0]);
    }


    private boolean opSetRemove(Set<Operation[]> set, Operation[] element) {
        for (Operation[] arr : set) {
            if (Arrays.deepEquals(arr, element)) {
                set.remove(arr);
                return true;
            }
        }
        return false;
    }

    //A长 B短
    private int containsSetofOpList(Set<Operation[]> setA, Set<Operation[]> setB) {
        int count = 0;
        // 创建临时HashSet

        // 遍历Set A中的每个元素
        for (Operation[] element : setA) {
            for (Operation[] b: setB) {
                // 检查Set B是否包含相同的元素
                if (Arrays.deepEquals(b, element)) {
                    count++;
                    break;
                }
            }
        }
        return count;

    }


}
