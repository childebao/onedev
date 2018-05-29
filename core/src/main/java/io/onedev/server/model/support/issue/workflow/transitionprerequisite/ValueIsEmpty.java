package io.onedev.server.model.support.issue.workflow.transitionprerequisite;

import io.onedev.server.util.editable.annotation.Editable;

@Editable(order=400, name="is empty")
public class ValueIsEmpty implements ValueMatcher {

	private static final long serialVersionUID = 1L;

	@Override
	public boolean matches(String value) {
		return value == null;
	}
	
}