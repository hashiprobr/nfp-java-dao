package br.pro.hashi.nfp.dao;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.Query.Direction;
import com.google.cloud.firestore.QuerySnapshot;

import br.pro.hashi.nfp.dao.exception.ExecutionFirestoreException;
import br.pro.hashi.nfp.dao.exception.FormatFirestoreException;
import br.pro.hashi.nfp.dao.exception.InterruptedFirestoreException;

public class Selection {
	private Query query;

	Selection(Query query) {
		this.query = query;
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

	public Selection select(List<?> rawKeys) {
		List<String> keys = DAO.convert(rawKeys);
		DAO.checkRead(keys);
		query = query.whereIn(FieldPath.documentId(), keys);
		return this;
	}

	public Selection selectExcept(List<?> rawKeys) {
		List<String> keys = DAO.convert(rawKeys);
		DAO.checkRead(keys);
		query = query.whereNotIn(FieldPath.documentId(), keys);
		return this;
	}

	public Selection selectWhereIn(String key, List<?> values) {
		DAO.checkRead(key);
		DAO.checkIn(values);
		query = query.whereIn(key, values);
		return this;
	}

	public Selection selectWhereNotIn(String key, List<?> values) {
		DAO.checkRead(key);
		DAO.checkIn(values);
		query = query.whereNotIn(key, values);
		return this;
	}

	public Selection selectWhereEqualTo(String key, Object value) {
		DAO.checkRead(key);
		query = query.whereEqualTo(key, value);
		return this;
	}

	public Selection selectWhereNotEqualTo(String key, Object value) {
		DAO.checkRead(key);
		query = query.whereNotEqualTo(key, value);
		return this;
	}

	public Selection selectWhereLessThan(String key, Object value) {
		DAO.checkRead(key);
		query = query.whereLessThan(key, value);
		return this;
	}

	public Selection selectWhereLessThanOrEqualTo(String key, Object value) {
		DAO.checkRead(key);
		query = query.whereLessThanOrEqualTo(key, value);
		return this;
	}

	public Selection selectWhereGreaterThan(String key, Object value) {
		DAO.checkRead(key);
		query = query.whereGreaterThan(key, value);
		return this;
	}

	public Selection selectWhereGreaterThanOrEqualTo(String key, Object value) {
		DAO.checkRead(key);
		query = query.whereGreaterThanOrEqualTo(key, value);
		return this;
	}

	public Selection selectWhereContains(String key, Object value) {
		DAO.checkRead(key);
		query = query.whereArrayContains(key, value);
		return this;
	}

	public Selection selectWhereContainsAny(String key, List<?> values) {
		DAO.checkRead(key);
		DAO.checkIn(values);
		query = query.whereArrayContainsAny(key, values);
		return this;
	}

	public Selection orderBy(String orderBy, boolean descending) {
		if (orderBy == null) {
			throw new FormatFirestoreException("Order by cannot be null");
		}
		if (orderBy.isBlank()) {
			throw new FormatFirestoreException("Order by cannot be blank");
		}
		if (descending) {
			query = query.orderBy(orderBy, Direction.DESCENDING);
		} else {
			query = query.orderBy(orderBy, Direction.ASCENDING);
		}
		return this;
	}

	public Selection orderBy(String key) {
		return orderBy(key, false);
	}

	public Selection offset(int offset) {
		if (offset < 1) {
			throw new FormatFirestoreException("Offset must be positive");
		}
		query = query.offset(offset);
		return this;
	}

	public Selection limit(int limit) {
		if (limit < 1) {
			throw new FormatFirestoreException("Limit must be positive");
		}
		query = query.limit(limit);
		return this;
	}

	public Selection limitToLast(int limit) {
		if (limit < 1) {
			throw new FormatFirestoreException("Limit to last must be positive");
		}
		query = query.limitToLast(limit);
		return this;
	}
}
