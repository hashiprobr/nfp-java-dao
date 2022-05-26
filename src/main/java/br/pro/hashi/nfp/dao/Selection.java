package br.pro.hashi.nfp.dao;

import java.util.concurrent.ExecutionException;

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
