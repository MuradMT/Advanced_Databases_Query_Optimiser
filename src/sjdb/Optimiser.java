package sjdb;

import java.util.*;

public class Optimiser {
    public Optimiser(Catalogue cat) {
    }

    public Operator optimise(Operator plan) {
        List<Attribute> topProjectAttributes = null;
        if (plan instanceof Project) {
            Project project = (Project) plan;
            topProjectAttributes = project.getAttributes();
            plan = project.getInput();
        }

        List<Predicate> allPredicates = new ArrayList<>();
        while (plan instanceof Select) {
            Select select = (Select) plan;
            allPredicates.add(select.getPredicate());
            plan = select.getInput();
        }

        List<Scan> scans = new ArrayList<>();
        collectScans(plan, scans);

        Map<String, List<Predicate>> selectionMap = new HashMap<>();
        Map<RelationPair, Predicate> joinMap = new HashMap<>();

        for (Predicate predicate : allPredicates) {
            if (predicate.equalsValue()) {
                String attrName = predicate.getLeftAttribute().getName();
                selectionMap.computeIfAbsent(attrName, k -> new ArrayList<>()).add(predicate);
            } else {
                String leftName = predicate.getLeftAttribute().getName();
                String rightName = predicate.getRightAttribute().getName();
                joinMap.put(new RelationPair(leftName, rightName), predicate);
            }
        }

        List<Operator> subPlans = new ArrayList<>();
        for (Scan originalScan : scans) {
            Relation rel = originalScan.getRelation();
            NamedRelation namedRel = (NamedRelation) rel;
            Operator subPlan = new Scan(namedRel);

            for (Attribute attr : subPlan.getOutput().getAttributes()) {
                List<Predicate> selections = selectionMap.getOrDefault(attr.getName(), Collections.emptyList());
                for (Predicate sel : selections) {
                    subPlan = new Select(subPlan, sel);
                }
            }
            subPlans.add(subPlan);
        }

        Estimator estimator = new Estimator();
        Operator current = null;
        long bestSize = Long.MAX_VALUE;
        for (Operator candidate : subPlans) {
            candidate.accept(estimator);
            long size = estimator.getOutput().getTupleCount();
            if (size < bestSize) {
                bestSize = size;
                current = candidate;
            }
        }
        subPlans.remove(current);

        while (!subPlans.isEmpty()) {
            Operator bestPlan = null;
            Operator bestCandidate = null;
            bestSize = Long.MAX_VALUE;

            for (Operator candidate : subPlans) {
                Predicate joinPredicate = findJoinPredicate(current, candidate, joinMap);
                Operator trial = (joinPredicate != null) ? new Join(current, candidate, joinPredicate) : new Product(current, candidate);
                trial.accept(estimator);
                long size = estimator.getOutput().getTupleCount();
                if (size < bestSize) {
                    bestSize = size;
                    bestPlan = trial;
                    bestCandidate = candidate;
                }
            }

            current = bestPlan;
            subPlans.remove(bestCandidate);
        }

        if (topProjectAttributes != null) {
            current = new Project(current, topProjectAttributes);
        }
        return current;
    }

    private void collectScans(Operator operator, List<Scan> scans) {
        if (operator instanceof Scan) {
            scans.add((Scan) operator);
        } else {
            for (Operator child : operator.getInputs()) {
                collectScans(child, scans);
            }
        }
    }

    private Predicate findJoinPredicate(Operator left, Operator right, Map<RelationPair, Predicate> joinMap) {
        Set<String> leftAttrs = attributeNames(left.getOutput());
        Set<String> rightAttrs = attributeNames(right.getOutput());

        Predicate bestPredicate = null;
        long bestSize = Long.MAX_VALUE;

        for (String leftAttr : leftAttrs) {
            for (String rightAttr : rightAttrs) {
                Predicate predicate = joinMap.get(new RelationPair(leftAttr, rightAttr));
                if (predicate != null) {
                    Relation leftRel = left.getOutput();
                    Relation rightRel = right.getOutput();
                    int Vleft = leftRel.getAttribute(new Attribute(leftAttr)).getValueCount();
                    int Vright = rightRel.getAttribute(new Attribute(rightAttr)).getValueCount();
                    long estimatedSize = (long) leftRel.getTupleCount() * rightRel.getTupleCount() / Math.max(1, Math.max(Vleft, Vright));

                    if (estimatedSize < bestSize) {
                        bestSize = estimatedSize;
                        bestPredicate = predicate;
                    }
                }
            }
        }
        return bestPredicate;
    }

    private Set<String> attributeNames(Relation relation) {
        Set<String> names = new HashSet<>();
        for (Attribute attribute : relation.getAttributes()) {
            names.add(attribute.getName());
        }
        return names;
    }

    private static class RelationPair {
        private final String attr1, attr2;

        RelationPair(String a, String b) {
            if (a.compareTo(b) <= 0) {
                this.attr1 = a;
                this.attr2 = b;
            } else {
                this.attr1 = b;
                this.attr2 = a;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof RelationPair)) return false;
            RelationPair other = (RelationPair) obj;
            return attr1.equals(other.attr1) && attr2.equals(other.attr2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attr1, attr2);
        }
    }
}