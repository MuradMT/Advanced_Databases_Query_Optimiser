package sjdb;

import java.util.*;

public class Estimator implements PlanVisitor {
	private Relation output;

	public Relation getOutput() {
		return output;
	}

	public Estimator() {
	}

	@Override
	public void visit(Scan op) {
		Relation input = op.getRelation();
		Relation output = new Relation(input.getTupleCount());
		for (Attribute attribute : input.getAttributes()) {
			output.addAttribute(new Attribute(attribute));
		}
		op.setOutput(output);
		this.output = output;
	}

	@Override
	public void visit(Project op) {
		Relation input = op.getInput().getOutput();
		int tupleCount = input.getTupleCount();
		Relation output = new Relation(tupleCount);
		for (Attribute keep : op.getAttributes()) {
			Attribute inputAttr = input.getAttribute(keep);
			int distinct = Math.min(inputAttr.getValueCount(), tupleCount);
			output.addAttribute(new Attribute(inputAttr.getName(), distinct));
		}
		op.setOutput(output);
		this.output = output;
	}

	@Override
	public void visit(Select op) {
		Relation input = op.getInput().getOutput();
		int tupleCount = input.getTupleCount();
		Predicate predicate = op.getPredicate();

		Relation output;
		if (predicate.equalsValue()) {
			Attribute attr = predicate.getLeftAttribute();
			int valueCount = input.getAttribute(attr).getValueCount();
			int newCount = tupleCount / Math.max(1, valueCount);

			output = new Relation(newCount);
			for (Attribute attribute : input.getAttributes()) {
				int distinct = attribute.equals(attr) ? 1 : Math.min(attribute.getValueCount(), newCount);
				output.addAttribute(new Attribute(attribute.getName(), distinct));
			}
		} else {
			Attribute leftAttr = predicate.getLeftAttribute();
			Attribute rightAttr = predicate.getRightAttribute();
			int leftCount = input.getAttribute(leftAttr).getValueCount();
			int rightCount = input.getAttribute(rightAttr).getValueCount();
			int maxCount = Math.max(leftCount, rightCount);
			int newCount = tupleCount / Math.max(1, maxCount);
			int minValueCount = Math.min(leftCount, rightCount);

			output = new Relation(newCount);
			for (Attribute attribute : input.getAttributes()) {
				int distinct = (attribute.equals(leftAttr) || attribute.equals(rightAttr))
						? minValueCount
						: Math.min(attribute.getValueCount(), newCount);
				output.addAttribute(new Attribute(attribute.getName(), distinct));
			}
		}

		op.setOutput(output);
		this.output = output;
	}

	@Override
	public void visit(Product op) {
		Relation left = op.getLeft().getOutput();
		Relation right = op.getRight().getOutput();

		int newCount = left.getTupleCount() * right.getTupleCount();
		Relation output = new Relation(newCount);

		for (Attribute attribute : left.getAttributes()) {
			output.addAttribute(new Attribute(attribute));
		}
		for (Attribute attribute : right.getAttributes()) {
			output.addAttribute(new Attribute(attribute));
		}

		op.setOutput(output);
		this.output = output;
	}

	@Override
	public void visit(Join op) {
		op.getLeft().accept(this);
		Relation left = this.output;
		op.getRight().accept(this);
		Relation right = this.output;

		Attribute leftAttr = op.getPredicate().getLeftAttribute();
		Attribute rightAttr = op.getPredicate().getRightAttribute();
		int leftCount = left.getAttribute(leftAttr).getValueCount();
		int rightCount = right.getAttribute(rightAttr).getValueCount();

		long tupleCountLeft = left.getTupleCount();
		long tupleCountRight = right.getTupleCount();
		long denominator = Math.max(leftCount, rightCount);
		long newCount = denominator == 0 ? 0 : (tupleCountLeft * tupleCountRight) / denominator;

		Relation output = new Relation((int)newCount);

		for (Attribute attribute : left.getAttributes()) {
			int distinct = attribute.equals(leftAttr) ? Math.min(leftCount, rightCount) : attribute.getValueCount();
			output.addAttribute(new Attribute(attribute.getName(), distinct));
		}
		for (Attribute attribute : right.getAttributes()) {
			int distinct = attribute.equals(rightAttr) ? Math.min(leftCount, rightCount) : attribute.getValueCount();
			output.addAttribute(new Attribute(attribute.getName(), distinct));
		}

		op.setOutput(output);
		this.output = output;
	}
}