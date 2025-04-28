package sjdb;


public class Estimator implements PlanVisitor {

	private Relation outputRelation;

	public Relation getOutput() {
		return outputRelation;
	}

	public Estimator() {

	}

	@Override
	public void visit(Scan scan) {
		Relation inputRelation = scan.getRelation();
		Relation output = new Relation(inputRelation.getTupleCount());

		for (Attribute attribute : inputRelation.getAttributes()) {
			output.addAttribute(new Attribute(attribute));
		}

		scan.setOutput(output);
		this.outputRelation = output;
	}

	@Override
	public void visit(Project project) {
		Relation inputRelation = project.getInput().getOutput();
		Relation output = new Relation(inputRelation.getTupleCount());

		for (Attribute projectedAttribute : project.getAttributes()) {
			Attribute inputAttribute = inputRelation.getAttribute(projectedAttribute);
			int valueCount = Math.min(inputAttribute.getValueCount(), inputRelation.getTupleCount());
			output.addAttribute(new Attribute(inputAttribute.getName(), valueCount));
		}

		project.setOutput(output);
		this.outputRelation = output;
	}

	@Override
	public void visit(Select select) {
		Relation inputRelation = select.getInput().getOutput();
		Predicate predicate = select.getPredicate();
		int tupleCount = inputRelation.getTupleCount();

		Relation output;

		if (predicate.equalsValue()) {
			Attribute attribute = predicate.getLeftAttribute();
			int distinctValueCount = inputRelation.getAttribute(attribute).getValueCount();
			int newTupleCount = tupleCount / Math.max(1, distinctValueCount);

			output = new Relation(newTupleCount);
			for (Attribute attributeInRelation : inputRelation.getAttributes()) {
				int valueCount = attributeInRelation.equals(attribute) ? 1 : Math.min(attributeInRelation.getValueCount(), newTupleCount);
				output.addAttribute(new Attribute(attributeInRelation.getName(), valueCount));
			}

		} else {
			Attribute leftAttribute = predicate.getLeftAttribute();
			Attribute rightAttribute = predicate.getRightAttribute();
			int leftDistinct = inputRelation.getAttribute(leftAttribute).getValueCount();
			int rightDistinct = inputRelation.getAttribute(rightAttribute).getValueCount();
			int maxDistinct = Math.max(leftDistinct, rightDistinct);
			int newTupleCount = tupleCount / Math.max(1, maxDistinct);
			int joinValueCount = Math.min(leftDistinct, rightDistinct);

			output = new Relation(newTupleCount);
			for (Attribute attributeInRelation : inputRelation.getAttributes()) {
				int valueCount = (attributeInRelation.equals(leftAttribute) || attributeInRelation.equals(rightAttribute))
						? joinValueCount
						: Math.min(attributeInRelation.getValueCount(), newTupleCount);
				output.addAttribute(new Attribute(attributeInRelation.getName(), valueCount));
			}
		}

		select.setOutput(output);
		this.outputRelation = output;
	}

	@Override
	public void visit(Product product) {
		Relation leftInput = product.getLeft().getOutput();
		Relation rightInput = product.getRight().getOutput();

		int newTupleCount = leftInput.getTupleCount() * rightInput.getTupleCount();
		Relation output = new Relation(newTupleCount);

		for (Attribute attribute : leftInput.getAttributes()) {
			output.addAttribute(new Attribute(attribute));
		}
		for (Attribute attribute : rightInput.getAttributes()) {
			output.addAttribute(new Attribute(attribute));
		}

		product.setOutput(output);
		this.outputRelation = output;
	}

	@Override
	public void visit(Join join) {
		join.getLeft().accept(this);
		Relation leftRelation = this.outputRelation;
		join.getRight().accept(this);
		Relation rightRelation = this.outputRelation;

		Attribute leftAttribute = join.getPredicate().getLeftAttribute();
		Attribute rightAttribute = join.getPredicate().getRightAttribute();
		int leftDistinct = leftRelation.getAttribute(leftAttribute).getValueCount();
		int rightDistinct = rightRelation.getAttribute(rightAttribute).getValueCount();

		long leftTuples = leftRelation.getTupleCount();
		long rightTuples = rightRelation.getTupleCount();
		long denominator = Math.max(leftDistinct, rightDistinct);
		long newTupleCount = (denominator == 0) ? 0 : (leftTuples * rightTuples) / denominator;

		Relation output = new Relation((int) newTupleCount);

		for (Attribute attribute : leftRelation.getAttributes()) {
			int valueCount = attribute.equals(leftAttribute) ? Math.min(leftDistinct, rightDistinct) : attribute.getValueCount();
			output.addAttribute(new Attribute(attribute.getName(), valueCount));
		}
		for (Attribute attribute : rightRelation.getAttributes()) {
			int valueCount = attribute.equals(rightAttribute) ? Math.min(leftDistinct, rightDistinct) : attribute.getValueCount();
			output.addAttribute(new Attribute(attribute.getName(), valueCount));
		}

		join.setOutput(output);
		this.outputRelation = output;
	}
}