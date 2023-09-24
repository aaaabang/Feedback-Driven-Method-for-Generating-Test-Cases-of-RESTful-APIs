package org.restct.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.formula.functions.T;
import org.restct.RESTCT;
import org.restct.dto.Operation;

import java.util.*;

public class Helper {
    public static Logger logger = LogManager.getLogger(RESTCT.class);

    public static <T> List<List<T>> getCombinations(Collection<T> collection, int strength){
        List<List<T>> result = new ArrayList<>();
        if (strength == 0) {
            result.add(new ArrayList<>());
            return result;
        }
        List<T> list = new ArrayList<>(collection);
        int n = list.size();
        for (int i = 0; i < n; i++) {
            T element = list.get(i);
            List<List<T>> subcombinations = getCombinations(list.subList(i + 1, n), strength - 1);
            for (List<T> subcombination : subcombinations) {
                subcombination.add(0, element);
                result.add(subcombination);
            }
        }
        return result;
    }

    public static int computeCombinations(Collection<? extends Collection<?>> collections, int strength){
        Set<List<?>> coveredSet = new HashSet<>();

        for (Collection<?> c : collections) {
            if (c.size() < strength) {
                continue;
            }
            for (List<?> comb : getCombinations(c, strength)) {
                coveredSet.add(comb);
            }
        }

        logger.debug("computeCombinations {}", coveredSet);
        return coveredSet.size();
    }



    public static <T> Set<List<T>> getPermutations(Collection<T> input, int strength) {
        Set<List<T>> result = new HashSet<>();
        if (strength == 0) {
            result.add(new ArrayList<>());
            return result;
        }
        List<T> list = new ArrayList<>(input);
        int n = list.size();
        for (int i = 0; i < n; i++) {
            T element = list.remove(i);
            Set<List<T>> subpermutations = getPermutations(list, strength - 1);
            for (List<T> subpermutation : subpermutations) {
                subpermutation.add(0, element);
                result.add(subpermutation);
            }
            list.add(i, element);
        }
        return result;
    }

    public static boolean opSetRemove(Set<Operation[]> set, Operation[] element) {
        Iterator<Operation[]> iterator = set.iterator();

        while (iterator.hasNext()) {
            Operation[] op = iterator.next();
            if (Arrays.deepEquals(op, element)) {
                iterator.remove();
                return true;
            }
        }
        return false;

    }
}
