package sjdb;

import java.util.*;

public class Optimiser {
    public Optimiser(Catalogue catalogue) {
    }

    public Operator optimise(Operator plan) {
        List<Attribute> projectAttributes = null;
        if (plan instanceof Project) {
            Project project = (Project) plan;
            projectAttributes = project.getAttributes();
            plan = project.getInput();
        }

        List<Predicate> allPredicates = new ArrayList<>();
        while (plan instanceof Select) {
            Select select = (Select) plan;
            allPredicates.add(select.getPredicate());
            plan = select.getInput();
        }

        List<Scan> scanOperators = new ArrayList<>();
        collectScans(plan, scanOperators);

        Map<String, List<Predicate>> pushdownPredicates = new HashMap<>();
        Map<RelationPair, Predicate> joinPredicates = new HashMap<>();

        for (Predicate predicate : allPredicates) {
            if (predicate.equalsValue()) {
                String attributeName = predicate.getLeftAttribute().getName();
                pushdownPredicates.computeIfAbsent(attributeName, key -> new ArrayList<>()).add(predicate);
            } else {
                String leftAttribute = predicate.getLeftAttribute().getName();
                String rightAttribute = predicate.getRightAttribute().getName();
                joinPredicates.put(new RelationPair(leftAttribute, rightAttribute), predicate);
            }
        }

        List<Operator> subPlans = new ArrayList<>();
        for (Scan originalScan : scanOperators) {
            Relation relation = originalScan.getRelation();
            NamedRelation namedRelation = (NamedRelation) relation;
            Operator currentOperator = new Scan(namedRelation);

            for (Attribute attribute : currentOperator.getOutput().getAttributes()) {
                List<Predicate> predicatesOnAttribute = pushdownPredicates.getOrDefault(attribute.getName(), Collections.emptyList());
                for (Predicate predicate : predicatesOnAttribute) {
                    currentOperator = new Select(currentOperator, predicate);
                }
            }
            subPlans.add(currentOperator);
        }

        Estimator estimator = new Estimator();
        Operator currentPlan = null;
        long bestTupleCount = Long.MAX_VALUE;
        for (Operator candidate : subPlans) {
            candidate.accept(estimator);
            long candidateTupleCount = estimator.getOutput().getTupleCount();
            if (candidateTupleCount < bestTupleCount) {
                bestTupleCount = candidateTupleCount;
                currentPlan = candidate;
            }
        }
        subPlans.remove(currentPlan);

        while (!subPlans.isEmpty()) {
            Operator bestNextPlan = null;
            Operator bestCandidate = null;
            bestTupleCount = Long.MAX_VALUE;

            for (Operator candidate : subPlans) {
                Predicate joinPredicate = findJoinPredicate(currentPlan, candidate, joinPredicates);
                Operator trialPlan = (joinPredicate != null)
                        ? new Join(currentPlan, candidate, joinPredicate)
                        : new Product(currentPlan, candidate);
                trialPlan.accept(estimator);
                long trialTupleCount = estimator.getOutput().getTupleCount();
                if (trialTupleCount < bestTupleCount) {
                    bestTupleCount = trialTupleCount;
                    bestNextPlan = trialPlan;
                    bestCandidate = candidate;
                }
            }

            currentPlan = bestNextPlan;
            subPlans.remove(bestCandidate);
        }

        if (projectAttributes != null) {
            currentPlan = new Project(currentPlan, projectAttributes);
        }
        return currentPlan;
    }

    private void collectScans(Operator operator, List<Scan> scanList) {
        if (operator instanceof Scan) {
            scanList.add((Scan) operator);
        } else {
            for (Operator childOperator : operator.getInputs()) {
                collectScans(childOperator, scanList);
            }
        }
    }

    private Predicate findJoinPredicate(
            Operator leftOperator,
            Operator rightOperator,
            Map<RelationPair, Predicate> joinPredicateMap
    ) {
        Set<String> leftAttributes = attributeNames(leftOperator.getOutput());
        Set<String> rightAttributes = attributeNames(rightOperator.getOutput());
        for (String leftAttribute : leftAttributes) {
            for (String rightAttribute : rightAttributes) {
                Predicate predicate = joinPredicateMap.get(new RelationPair(leftAttribute, rightAttribute));
                if (predicate != null) {
                    return predicate;
                }
            }
        }
        return null;
    }

    private Set<String> attributeNames(Relation relation) {
        Set<String> names = new HashSet<>();
        for (Attribute attribute : relation.getAttributes()) {
            names.add(attribute.getName());
        }
        return names;
    }

    private static class RelationPair {
        private final String attributeOne;
        private final String attributeTwo;

        RelationPair(String attributeA, String attributeB) {
            if (attributeA.compareTo(attributeB) <= 0) {
                this.attributeOne = attributeA;
                this.attributeTwo = attributeB;
            } else {
                this.attributeOne = attributeB;
                this.attributeTwo = attributeA;
            }
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof RelationPair)) return false;
            RelationPair other = (RelationPair) object;
            return attributeOne.equals(other.attributeOne) && attributeTwo.equals(other.attributeTwo);
        }

        @Override
        public int hashCode() {
            return attributeOne.hashCode() * 31 + attributeTwo.hashCode();
        }
    }
}