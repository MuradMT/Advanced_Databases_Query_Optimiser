package sjdb;
public class TestJoin {
    public static void main(String[] args) throws Exception {
        Catalogue cat = new Catalogue();
        cat.createRelation("A", 100);
        cat.createAttribute("A", "a1", 100);
        cat.createAttribute("A", "a2", 15);
        cat.createRelation("B", 150);
        cat.createAttribute("B", "b1", 150);
        cat.createAttribute("B", "b2", 100);
        cat.createAttribute("B", "b3", 5);

        Scan scanA = new Scan(cat.getRelation("A"));
        Scan scanB = new Scan(cat.getRelation("B"));

        Predicate joinPred = new Predicate(
                new Attribute("a2"),
                new Attribute("b3")
        );
        Join joinPlan = new Join(scanA, scanB, joinPred);

        Estimator est = new Estimator();
        joinPlan.accept(est);

        Inspector insp = new Inspector();
        joinPlan.accept(insp);
    }
}