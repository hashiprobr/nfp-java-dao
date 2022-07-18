package br.pro.hashi.nfp.dao;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.Query.Direction;
import com.google.cloud.firestore.QuerySnapshot;

import br.pro.hashi.nfp.dao.exception.ExecutionFirestoreException;
import br.pro.hashi.nfp.dao.exception.InterruptedFirestoreException;

public class Selection {
	private static final int NAME_LIMIT = 1500;
	private static final String NAME_ALLOWED = "[_a-zA-Z][_a-zA-Z0-9]*";

	static String clean(String name) {
		if (name == null) {
			throw new IllegalArgumentException("Field name cannot be null");
		}
		name = name.strip();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Field name cannot be blank");
		}
		if (name.getBytes().length > NAME_LIMIT) {
			throw new IllegalArgumentException("Field name cannot have more than %d bytes".formatted(NAME_LIMIT));
		}
		if (!name.matches(NAME_ALLOWED)) {
			throw new IllegalArgumentException("Field name must match the regular expression %s".formatted(NAME_ALLOWED));
		}
		return name;
	}

	static String clean(String name, List<?> values) {
		name = clean(name);
		if (values == null) {
			throw new IllegalArgumentException("List of values cannot be null");
		}
		if (values.isEmpty()) {
			throw new IllegalArgumentException("List of values cannot be empty");
		}
		return name;
	}

	private Query query;

	Selection(Query query) {
		this.query = query;
	}

	Firestore getFirestore() {
		return query.getFirestore();
	}

	QuerySnapshot getDocuments() {
		QuerySnapshot documents;
		try {
			documents = query.get().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		return documents;
	}

	public Selection whereEqualTo(String name, Object value) {
		name = clean(name);
		query = query.whereEqualTo(name, value);
		return this;
	}

	public Selection whereNotEqualTo(String name, Object value) {
		name = clean(name);
		query = query.whereNotEqualTo(name, value);
		return this;
	}

	public Selection whereLessThan(String name, Object value) {
		name = clean(name);
		query = query.whereLessThan(name, value);
		return this;
	}

	public Selection whereLessThanOrEqualTo(String name, Object value) {
		name = clean(name);
		query = query.whereLessThanOrEqualTo(name, value);
		return this;
	}

	public Selection whereGreaterThan(String name, Object value) {
		name = clean(name);
		query = query.whereGreaterThan(name, value);
		return this;
	}

	public Selection whereGreaterThanOrEqualTo(String name, Object value) {
		name = clean(name);
		query = query.whereGreaterThanOrEqualTo(name, value);
		return this;
	}

	public Selection whereContains(String name, Object value) {
		name = clean(name);
		query = query.whereArrayContains(name, value);
		return this;
	}

	public Selection whereContainsAny(String name, List<?> values) {
		name = clean(name, values);
		query = query.whereArrayContainsAny(name, values);
		return this;
	}

	public Selection whereIn(String name, List<?> values) {
		name = clean(name, values);
		query = query.whereIn(name, values);
		return this;
	}

	public Selection whereNotIn(String name, List<?> values) {
		name = clean(name, values);
		query = query.whereNotIn(name, values);
		return this;
	}

	public Selection orderBy(String name, boolean descending) {
		name = clean(name);
		if (descending) {
			query = query.orderBy(name, Direction.DESCENDING);
		} else {
			query = query.orderBy(name, Direction.ASCENDING);
		}
		return this;
	}

	public Selection orderBy(String name) {
		return orderBy(name, false);
	}

	public Selection offset(int offset) {
		if (offset < 1) {
			throw new IllegalArgumentException("Offset must be positive");
		}
		query = query.offset(offset);
		return this;
	}

	public Selection limit(int limit) {
		if (limit < 1) {
			throw new IllegalArgumentException("Limit must be positive");
		}
		query = query.limit(limit);
		return this;
	}

	public Selection limitToLast(int limit) {
		if (limit < 1) {
			throw new IllegalArgumentException("Limit to last must be positive");
		}
		query = query.limitToLast(limit);
		return this;
	}
}
